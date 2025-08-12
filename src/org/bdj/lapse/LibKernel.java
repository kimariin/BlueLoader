// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj.lapse;

import org.bdj.Console;
import org.bdj.api.API;
import org.bdj.api.Buffer;

/** Wrapper around libkernel and OS syscalls required by Lapse */

public class LibKernel extends Library {
	// NOTE: This class relies on a specific initialization order to work. Be careful when
	// reordering things around. There are no compiler warnings for screwing it up.

	public LibKernel() throws Exception {
		super(API.LIBKERNEL_MODULE_HANDLE);
		Console.log("Loaded LibKernel: handle " + handle + " base 0x" + Long.toHexString(module.address()));
		Console.log("Loaded LibKernel: __error = 0x" + Long.toHexString(Error.address));
		Console.log("Loaded LibKernel: SYS_exit = 0x" + Long.toHexString(SYS_exit.address));
		Console.log("Loaded LibKernel: SYS_aio_create = 0x" + Long.toHexString(SYS_aio_create.address));
	}

	// Need this around for strerror
	public LibC libc = new LibC();

	/***********************************************************************************************
	 * sceKernelGetModuleInfoFromAddr
	 **********************************************************************************************/

	// Offsets from https://github.com/shadps4-emu/shadPS4/blob/0bdd21b/src/core/module.h#L32
	public class ModuleInfo implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final FieldPtr initProcAddr = new FieldPtr(this, 0x128);
		public final FieldPtr firstSegment = new FieldPtr(this, 0x160);
		public final int SIZE = 0x300;
		public ModuleInfo() {
			buffer = new Buffer(0x300);
			buffer.fill((byte)0);
		}
	}

	/** Returns information about a loaded executable module, given an address that is inside it */
	public Function KernelGetModuleInfoFromAddr = new Function("sceKernelGetModuleInfoFromAddr");

	/** Returns information about a loaded executable module, given an address that is inside it */
	public ModuleInfo kernelGetModuleInfoFromAddr(long addr) {
		ModuleInfo info = new ModuleInfo();
		long r = KernelGetModuleInfoFromAddr.call(addr, 1, info.address());
		if (r != 0) {
			int e = errno();
			String error = libc.strerror(e);
			throw new SystemCallFailed(KernelGetModuleInfoFromAddr.name + "(" +
				"addr=0x" + Long.toHexString(addr) + ", " +
				"out=" + Long.toHexString(info.address()) + ")" +
				" => " + r + ", " + error, e, error);
		}
		return info;
	}

	public ModuleInfo moduleInfo = kernelGetModuleInfoFromAddr(KernelGetModuleInfoFromAddr.address);

	// Number taken from Lapse; is this always the size of libkernel?
	public Buffer module = new Buffer(moduleInfo.firstSegment.get(), 0x40000);

	/***********************************************************************************************
	 * Syscall infrastructure
	 **********************************************************************************************/

	/** Native callable syscall wrapper */
	public class Syscall extends Callable {
		public final int number;
		public Syscall(String name, int number) {
			super(name);
			this.number = number;
			this.address = findSyscallWrapper(module, name, number);
		}
	}

	/** Find a syscall wrapper inside a given module */
	public long findSyscallWrapper(Buffer module, String name, int number) {
		long address = 0;
		byte s0 = (byte)((number & 0x000000ff) >> 0);
		byte s1 = (byte)((number & 0x0000ff00) >> 8);
		byte s2 = (byte)((number & 0x00ff0000) >> 16);
		byte s3 = (byte)((number & 0xff000000) >> 24);
		byte[] pattern = {0x48, (byte)0xc7, (byte)0xc0, s0, s1, s2, s3, 0x49, (byte)0x89, (byte)0xca, 0x0f, 0x05};
		for (long p = module.address(); p < module.address() + module.size() - pattern.length; p++) {
			boolean found = true;
			for (int i = 0; i < pattern.length; i++) {
				if (api.read8(p + i) != pattern[i]) {
					found = false;
					break;
				}
			}
			if (found) {
				address = p;
				break;
			}
		}
		return address;
	}

	/***********************************************************************************************
	 * FreeBSD syscalls
	 * http://fxr.watson.org/fxr/source/sys/syscall.h?v=FREEBSD-9-1
	 **********************************************************************************************/

	public Syscall SYS_syscall                  = new Syscall("SYS_syscall",                  0);
	public Syscall SYS_exit                     = new Syscall("SYS_exit",                     1);
	public Syscall SYS_fork                     = new Syscall("SYS_fork",                     2);
	public Syscall SYS_read                     = new Syscall("SYS_read",                     3);
	public Syscall SYS_write                    = new Syscall("SYS_write",                    4);
	public Syscall SYS_open                     = new Syscall("SYS_open",                     5);
	public Syscall SYS_close                    = new Syscall("SYS_close",                    6);
	public Syscall SYS_wait4                    = new Syscall("SYS_wait4",                    7);
	public Syscall SYS_link                     = new Syscall("SYS_link",                     9);
	public Syscall SYS_unlink                   = new Syscall("SYS_unlink",                   10);
	public Syscall SYS_chdir                    = new Syscall("SYS_chdir",                    12);
	public Syscall SYS_fchdir                   = new Syscall("SYS_fchdir",                   13);
	public Syscall SYS_mknod                    = new Syscall("SYS_mknod",                    14);
	public Syscall SYS_chmod                    = new Syscall("SYS_chmod",                    15);
	public Syscall SYS_chown                    = new Syscall("SYS_chown",                    16);
	public Syscall SYS_break                    = new Syscall("SYS_break",                    17);
	public Syscall SYS_freebsd4_getfsstat       = new Syscall("SYS_freebsd4_getfsstat",       18);
	public Syscall SYS_getpid                   = new Syscall("SYS_getpid",                   20);
	public Syscall SYS_mount                    = new Syscall("SYS_mount",                    21);
	public Syscall SYS_unmount                  = new Syscall("SYS_unmount",                  22);
	public Syscall SYS_setuid                   = new Syscall("SYS_setuid",                   23);
	public Syscall SYS_getuid                   = new Syscall("SYS_getuid",                   24);
	public Syscall SYS_geteuid                  = new Syscall("SYS_geteuid",                  25);
	public Syscall SYS_ptrace                   = new Syscall("SYS_ptrace",                   26);
	public Syscall SYS_recvmsg                  = new Syscall("SYS_recvmsg",                  27);
	public Syscall SYS_sendmsg                  = new Syscall("SYS_sendmsg",                  28);
	public Syscall SYS_recvfrom                 = new Syscall("SYS_recvfrom",                 29);
	public Syscall SYS_accept                   = new Syscall("SYS_accept",                   30);
	public Syscall SYS_getpeername              = new Syscall("SYS_getpeername",              31);
	public Syscall SYS_getsockname              = new Syscall("SYS_getsockname",              32);
	public Syscall SYS_access                   = new Syscall("SYS_access",                   33);
	public Syscall SYS_chflags                  = new Syscall("SYS_chflags",                  34);
	public Syscall SYS_fchflags                 = new Syscall("SYS_fchflags",                 35);
	public Syscall SYS_sync                     = new Syscall("SYS_sync",                     36);
	public Syscall SYS_kill                     = new Syscall("SYS_kill",                     37);
	public Syscall SYS_getppid                  = new Syscall("SYS_getppid",                  39);
	public Syscall SYS_dup                      = new Syscall("SYS_dup",                      41);
	public Syscall SYS_pipe                     = new Syscall("SYS_pipe",                     42);
	public Syscall SYS_getegid                  = new Syscall("SYS_getegid",                  43);
	public Syscall SYS_profil                   = new Syscall("SYS_profil",                   44);
	public Syscall SYS_ktrace                   = new Syscall("SYS_ktrace",                   45);
	public Syscall SYS_getgid                   = new Syscall("SYS_getgid",                   47);
	public Syscall SYS_getlogin                 = new Syscall("SYS_getlogin",                 49);
	public Syscall SYS_setlogin                 = new Syscall("SYS_setlogin",                 50);
	public Syscall SYS_acct                     = new Syscall("SYS_acct",                     51);
	public Syscall SYS_sigaltstack              = new Syscall("SYS_sigaltstack",              53);
	public Syscall SYS_ioctl                    = new Syscall("SYS_ioctl",                    54);
	public Syscall SYS_reboot                   = new Syscall("SYS_reboot",                   55);
	public Syscall SYS_revoke                   = new Syscall("SYS_revoke",                   56);
	public Syscall SYS_symlink                  = new Syscall("SYS_symlink",                  57);
	public Syscall SYS_readlink                 = new Syscall("SYS_readlink",                 58);
	public Syscall SYS_execve                   = new Syscall("SYS_execve",                   59);
	public Syscall SYS_umask                    = new Syscall("SYS_umask",                    60);
	public Syscall SYS_chroot                   = new Syscall("SYS_chroot",                   61);
	public Syscall SYS_msync                    = new Syscall("SYS_msync",                    65);
	public Syscall SYS_vfork                    = new Syscall("SYS_vfork",                    66);
	public Syscall SYS_sbrk                     = new Syscall("SYS_sbrk",                     69);
	public Syscall SYS_sstk                     = new Syscall("SYS_sstk",                     70);
	public Syscall SYS_vadvise                  = new Syscall("SYS_vadvise",                  72);
	public Syscall SYS_munmap                   = new Syscall("SYS_munmap",                   73);
	public Syscall SYS_mprotect                 = new Syscall("SYS_mprotect",                 74);
	public Syscall SYS_madvise                  = new Syscall("SYS_madvise",                  75);
	public Syscall SYS_mincore                  = new Syscall("SYS_mincore",                  78);
	public Syscall SYS_getgroups                = new Syscall("SYS_getgroups",                79);
	public Syscall SYS_setgroups                = new Syscall("SYS_setgroups",                80);
	public Syscall SYS_getpgrp                  = new Syscall("SYS_getpgrp",                  81);
	public Syscall SYS_setpgid                  = new Syscall("SYS_setpgid",                  82);
	public Syscall SYS_setitimer                = new Syscall("SYS_setitimer",                83);
	public Syscall SYS_swapon                   = new Syscall("SYS_swapon",                   85);
	public Syscall SYS_getitimer                = new Syscall("SYS_getitimer",                86);
	public Syscall SYS_getdtablesize            = new Syscall("SYS_getdtablesize",            89);
	public Syscall SYS_dup2                     = new Syscall("SYS_dup2",                     90);
	public Syscall SYS_fcntl                    = new Syscall("SYS_fcntl",                    92);
	public Syscall SYS_select                   = new Syscall("SYS_select",                   93);
	public Syscall SYS_fsync                    = new Syscall("SYS_fsync",                    95);
	public Syscall SYS_setpriority              = new Syscall("SYS_setpriority",              96);
	public Syscall SYS_socket                   = new Syscall("SYS_socket",                   97);
	public Syscall SYS_connect                  = new Syscall("SYS_connect",                  98);
	public Syscall SYS_getpriority              = new Syscall("SYS_getpriority",              100);
	public Syscall SYS_bind                     = new Syscall("SYS_bind",                     104);
	public Syscall SYS_setsockopt               = new Syscall("SYS_setsockopt",               105);
	public Syscall SYS_listen                   = new Syscall("SYS_listen",                   106);
	public Syscall SYS_gettimeofday             = new Syscall("SYS_gettimeofday",             116);
	public Syscall SYS_getrusage                = new Syscall("SYS_getrusage",                117);
	public Syscall SYS_getsockopt               = new Syscall("SYS_getsockopt",               118);
	public Syscall SYS_readv                    = new Syscall("SYS_readv",                    120);
	public Syscall SYS_writev                   = new Syscall("SYS_writev",                   121);
	public Syscall SYS_settimeofday             = new Syscall("SYS_settimeofday",             122);
	public Syscall SYS_fchown                   = new Syscall("SYS_fchown",                   123);
	public Syscall SYS_fchmod                   = new Syscall("SYS_fchmod",                   124);
	public Syscall SYS_setreuid                 = new Syscall("SYS_setreuid",                 126);
	public Syscall SYS_setregid                 = new Syscall("SYS_setregid",                 127);
	public Syscall SYS_rename                   = new Syscall("SYS_rename",                   128);
	public Syscall SYS_flock                    = new Syscall("SYS_flock",                    131);
	public Syscall SYS_mkfifo                   = new Syscall("SYS_mkfifo",                   132);
	public Syscall SYS_sendto                   = new Syscall("SYS_sendto",                   133);
	public Syscall SYS_shutdown                 = new Syscall("SYS_shutdown",                 134);
	public Syscall SYS_socketpair               = new Syscall("SYS_socketpair",               135);
	public Syscall SYS_mkdir                    = new Syscall("SYS_mkdir",                    136);
	public Syscall SYS_rmdir                    = new Syscall("SYS_rmdir",                    137);
	public Syscall SYS_utimes                   = new Syscall("SYS_utimes",                   138);
	public Syscall SYS_adjtime                  = new Syscall("SYS_adjtime",                  140);
	public Syscall SYS_setsid                   = new Syscall("SYS_setsid",                   147);
	public Syscall SYS_quotactl                 = new Syscall("SYS_quotactl",                 148);
	public Syscall SYS_nlm_syscall              = new Syscall("SYS_nlm_syscall",              154);
	public Syscall SYS_nfssvc                   = new Syscall("SYS_nfssvc",                   155);
	public Syscall SYS_freebsd4_statfs          = new Syscall("SYS_freebsd4_statfs",          157);
	public Syscall SYS_freebsd4_fstatfs         = new Syscall("SYS_freebsd4_fstatfs",         158);
	public Syscall SYS_lgetfh                   = new Syscall("SYS_lgetfh",                   160);
	public Syscall SYS_getfh                    = new Syscall("SYS_getfh",                    161);
	public Syscall SYS_freebsd4_getdomainname   = new Syscall("SYS_freebsd4_getdomainname",   162);
	public Syscall SYS_freebsd4_setdomainname   = new Syscall("SYS_freebsd4_setdomainname",   163);
	public Syscall SYS_freebsd4_uname           = new Syscall("SYS_freebsd4_uname",           164);
	public Syscall SYS_sysarch                  = new Syscall("SYS_sysarch",                  165);
	public Syscall SYS_rtprio                   = new Syscall("SYS_rtprio",                   166);
	public Syscall SYS_semsys                   = new Syscall("SYS_semsys",                   169);
	public Syscall SYS_msgsys                   = new Syscall("SYS_msgsys",                   170);
	public Syscall SYS_shmsys                   = new Syscall("SYS_shmsys",                   171);
	public Syscall SYS_freebsd6_pread           = new Syscall("SYS_freebsd6_pread",           173);
	public Syscall SYS_freebsd6_pwrite          = new Syscall("SYS_freebsd6_pwrite",          174);
	public Syscall SYS_setfib                   = new Syscall("SYS_setfib",                   175);
	public Syscall SYS_ntp_adjtime              = new Syscall("SYS_ntp_adjtime",              176);
	public Syscall SYS_setgid                   = new Syscall("SYS_setgid",                   181);
	public Syscall SYS_setegid                  = new Syscall("SYS_setegid",                  182);
	public Syscall SYS_seteuid                  = new Syscall("SYS_seteuid",                  183);
	public Syscall SYS_stat                     = new Syscall("SYS_stat",                     188);
	public Syscall SYS_fstat                    = new Syscall("SYS_fstat",                    189);
	public Syscall SYS_lstat                    = new Syscall("SYS_lstat",                    190);
	public Syscall SYS_pathconf                 = new Syscall("SYS_pathconf",                 191);
	public Syscall SYS_fpathconf                = new Syscall("SYS_fpathconf",                192);
	public Syscall SYS_getrlimit                = new Syscall("SYS_getrlimit",                194);
	public Syscall SYS_setrlimit                = new Syscall("SYS_setrlimit",                195);
	public Syscall SYS_getdirentries            = new Syscall("SYS_getdirentries",            196);
	public Syscall SYS_freebsd6_mmap            = new Syscall("SYS_freebsd6_mmap",            197);
	public Syscall SYS___syscall                = new Syscall("SYS___syscall",                198);
	public Syscall SYS_freebsd6_lseek           = new Syscall("SYS_freebsd6_lseek",           199);
	public Syscall SYS_freebsd6_truncate        = new Syscall("SYS_freebsd6_truncate",        200);
	public Syscall SYS_freebsd6_ftruncate       = new Syscall("SYS_freebsd6_ftruncate",       201);
	public Syscall SYS___sysctl                 = new Syscall("SYS___sysctl",                 202);
	public Syscall SYS_mlock                    = new Syscall("SYS_mlock",                    203);
	public Syscall SYS_munlock                  = new Syscall("SYS_munlock",                  204);
	public Syscall SYS_undelete                 = new Syscall("SYS_undelete",                 205);
	public Syscall SYS_futimes                  = new Syscall("SYS_futimes",                  206);
	public Syscall SYS_getpgid                  = new Syscall("SYS_getpgid",                  207);
	public Syscall SYS_poll                     = new Syscall("SYS_poll",                     209);
	public Syscall SYS_freebsd7___semctl        = new Syscall("SYS_freebsd7___semctl",        220);
	public Syscall SYS_semget                   = new Syscall("SYS_semget",                   221);
	public Syscall SYS_semop                    = new Syscall("SYS_semop",                    222);
	public Syscall SYS_freebsd7_msgctl          = new Syscall("SYS_freebsd7_msgctl",          224);
	public Syscall SYS_msgget                   = new Syscall("SYS_msgget",                   225);
	public Syscall SYS_msgsnd                   = new Syscall("SYS_msgsnd",                   226);
	public Syscall SYS_msgrcv                   = new Syscall("SYS_msgrcv",                   227);
	public Syscall SYS_shmat                    = new Syscall("SYS_shmat",                    228);
	public Syscall SYS_freebsd7_shmctl          = new Syscall("SYS_freebsd7_shmctl",          229);
	public Syscall SYS_shmdt                    = new Syscall("SYS_shmdt",                    230);
	public Syscall SYS_shmget                   = new Syscall("SYS_shmget",                   231);
	public Syscall SYS_clock_gettime            = new Syscall("SYS_clock_gettime",            232);
	public Syscall SYS_clock_settime            = new Syscall("SYS_clock_settime",            233);
	public Syscall SYS_clock_getres             = new Syscall("SYS_clock_getres",             234);
	public Syscall SYS_ktimer_create            = new Syscall("SYS_ktimer_create",            235);
	public Syscall SYS_ktimer_delete            = new Syscall("SYS_ktimer_delete",            236);
	public Syscall SYS_ktimer_settime           = new Syscall("SYS_ktimer_settime",           237);
	public Syscall SYS_ktimer_gettime           = new Syscall("SYS_ktimer_gettime",           238);
	public Syscall SYS_ktimer_getoverrun        = new Syscall("SYS_ktimer_getoverrun",        239);
	public Syscall SYS_nanosleep                = new Syscall("SYS_nanosleep",                240);
	public Syscall SYS_ntp_gettime              = new Syscall("SYS_ntp_gettime",              248);
	public Syscall SYS_minherit                 = new Syscall("SYS_minherit",                 250);
	public Syscall SYS_rfork                    = new Syscall("SYS_rfork",                    251);
	public Syscall SYS_openbsd_poll             = new Syscall("SYS_openbsd_poll",             252);
	public Syscall SYS_issetugid                = new Syscall("SYS_issetugid",                253);
	public Syscall SYS_lchown                   = new Syscall("SYS_lchown",                   254);
	public Syscall SYS_aio_read                 = new Syscall("SYS_aio_read",                 255);
	public Syscall SYS_aio_write                = new Syscall("SYS_aio_write",                256);
	public Syscall SYS_lio_listio               = new Syscall("SYS_lio_listio",               257);
	public Syscall SYS_getdents                 = new Syscall("SYS_getdents",                 272);
	public Syscall SYS_lchmod                   = new Syscall("SYS_lchmod",                   274);
	public Syscall SYS_netbsd_lchown            = new Syscall("SYS_netbsd_lchown",            275);
	public Syscall SYS_lutimes                  = new Syscall("SYS_lutimes",                  276);
	public Syscall SYS_netbsd_msync             = new Syscall("SYS_netbsd_msync",             277);
	public Syscall SYS_nstat                    = new Syscall("SYS_nstat",                    278);
	public Syscall SYS_nfstat                   = new Syscall("SYS_nfstat",                   279);
	public Syscall SYS_nlstat                   = new Syscall("SYS_nlstat",                   280);
	public Syscall SYS_preadv                   = new Syscall("SYS_preadv",                   289);
	public Syscall SYS_pwritev                  = new Syscall("SYS_pwritev",                  290);
	public Syscall SYS_freebsd4_fhstatfs        = new Syscall("SYS_freebsd4_fhstatfs",        297);
	public Syscall SYS_fhopen                   = new Syscall("SYS_fhopen",                   298);
	public Syscall SYS_fhstat                   = new Syscall("SYS_fhstat",                   299);
	public Syscall SYS_modnext                  = new Syscall("SYS_modnext",                  300);
	public Syscall SYS_modstat                  = new Syscall("SYS_modstat",                  301);
	public Syscall SYS_modfnext                 = new Syscall("SYS_modfnext",                 302);
	public Syscall SYS_modfind                  = new Syscall("SYS_modfind",                  303);
	public Syscall SYS_kldload                  = new Syscall("SYS_kldload",                  304);
	public Syscall SYS_kldunload                = new Syscall("SYS_kldunload",                305);
	public Syscall SYS_kldfind                  = new Syscall("SYS_kldfind",                  306);
	public Syscall SYS_kldnext                  = new Syscall("SYS_kldnext",                  307);
	public Syscall SYS_kldstat                  = new Syscall("SYS_kldstat",                  308);
	public Syscall SYS_kldfirstmod              = new Syscall("SYS_kldfirstmod",              309);
	public Syscall SYS_getsid                   = new Syscall("SYS_getsid",                   310);
	public Syscall SYS_setresuid                = new Syscall("SYS_setresuid",                311);
	public Syscall SYS_setresgid                = new Syscall("SYS_setresgid",                312);
	public Syscall SYS_aio_return               = new Syscall("SYS_aio_return",               314);
	public Syscall SYS_aio_suspend              = new Syscall("SYS_aio_suspend",              315);
	public Syscall SYS_aio_cancel               = new Syscall("SYS_aio_cancel",               316);
	public Syscall SYS_aio_error                = new Syscall("SYS_aio_error",                317);
	public Syscall SYS_oaio_read                = new Syscall("SYS_oaio_read",                318);
	public Syscall SYS_oaio_write               = new Syscall("SYS_oaio_write",               319);
	public Syscall SYS_olio_listio              = new Syscall("SYS_olio_listio",              320);
	public Syscall SYS_yield                    = new Syscall("SYS_yield",                    321);
	public Syscall SYS_mlockall                 = new Syscall("SYS_mlockall",                 324);
	public Syscall SYS_munlockall               = new Syscall("SYS_munlockall",               325);
	public Syscall SYS___getcwd                 = new Syscall("SYS___getcwd",                 326);
	public Syscall SYS_sched_setparam           = new Syscall("SYS_sched_setparam",           327);
	public Syscall SYS_sched_getparam           = new Syscall("SYS_sched_getparam",           328);
	public Syscall SYS_sched_setscheduler       = new Syscall("SYS_sched_setscheduler",       329);
	public Syscall SYS_sched_getscheduler       = new Syscall("SYS_sched_getscheduler",       330);
	public Syscall SYS_sched_yield              = new Syscall("SYS_sched_yield",              331);
	public Syscall SYS_sched_get_priority_max   = new Syscall("SYS_sched_get_priority_max",   332);
	public Syscall SYS_sched_get_priority_min   = new Syscall("SYS_sched_get_priority_min",   333);
	public Syscall SYS_sched_rr_get_interval    = new Syscall("SYS_sched_rr_get_interval",    334);
	public Syscall SYS_utrace                   = new Syscall("SYS_utrace",                   335);
	public Syscall SYS_freebsd4_sendfile        = new Syscall("SYS_freebsd4_sendfile",        336);
	public Syscall SYS_kldsym                   = new Syscall("SYS_kldsym",                   337);
	public Syscall SYS_jail                     = new Syscall("SYS_jail",                     338);
	public Syscall SYS_nnpfs_syscall            = new Syscall("SYS_nnpfs_syscall",            339);
	public Syscall SYS_sigprocmask              = new Syscall("SYS_sigprocmask",              340);
	public Syscall SYS_sigsuspend               = new Syscall("SYS_sigsuspend",               341);
	public Syscall SYS_freebsd4_sigaction       = new Syscall("SYS_freebsd4_sigaction",       342);
	public Syscall SYS_sigpending               = new Syscall("SYS_sigpending",               343);
	public Syscall SYS_freebsd4_sigreturn       = new Syscall("SYS_freebsd4_sigreturn",       344);
	public Syscall SYS_sigtimedwait             = new Syscall("SYS_sigtimedwait",             345);
	public Syscall SYS_sigwaitinfo              = new Syscall("SYS_sigwaitinfo",              346);
	public Syscall SYS___acl_get_file           = new Syscall("SYS___acl_get_file",           347);
	public Syscall SYS___acl_set_file           = new Syscall("SYS___acl_set_file",           348);
	public Syscall SYS___acl_get_fd             = new Syscall("SYS___acl_get_fd",             349);
	public Syscall SYS___acl_set_fd             = new Syscall("SYS___acl_set_fd",             350);
	public Syscall SYS___acl_delete_file        = new Syscall("SYS___acl_delete_file",        351);
	public Syscall SYS___acl_delete_fd          = new Syscall("SYS___acl_delete_fd",          352);
	public Syscall SYS___acl_aclcheck_file      = new Syscall("SYS___acl_aclcheck_file",      353);
	public Syscall SYS___acl_aclcheck_fd        = new Syscall("SYS___acl_aclcheck_fd",        354);
	public Syscall SYS_extattrctl               = new Syscall("SYS_extattrctl",               355);
	public Syscall SYS_extattr_set_file         = new Syscall("SYS_extattr_set_file",         356);
	public Syscall SYS_extattr_get_file         = new Syscall("SYS_extattr_get_file",         357);
	public Syscall SYS_extattr_delete_file      = new Syscall("SYS_extattr_delete_file",      358);
	public Syscall SYS_aio_waitcomplete         = new Syscall("SYS_aio_waitcomplete",         359);
	public Syscall SYS_getresuid                = new Syscall("SYS_getresuid",                360);
	public Syscall SYS_getresgid                = new Syscall("SYS_getresgid",                361);
	public Syscall SYS_kqueue                   = new Syscall("SYS_kqueue",                   362);
	public Syscall SYS_kevent                   = new Syscall("SYS_kevent",                   363);
	public Syscall SYS_extattr_set_fd           = new Syscall("SYS_extattr_set_fd",           371);
	public Syscall SYS_extattr_get_fd           = new Syscall("SYS_extattr_get_fd",           372);
	public Syscall SYS_extattr_delete_fd        = new Syscall("SYS_extattr_delete_fd",        373);
	public Syscall SYS___setugid                = new Syscall("SYS___setugid",                374);
	public Syscall SYS_eaccess                  = new Syscall("SYS_eaccess",                  376);
	public Syscall SYS_afs3_syscall             = new Syscall("SYS_afs3_syscall",             377);
	public Syscall SYS_nmount                   = new Syscall("SYS_nmount",                   378);
	public Syscall SYS___mac_get_proc           = new Syscall("SYS___mac_get_proc",           384);
	public Syscall SYS___mac_set_proc           = new Syscall("SYS___mac_set_proc",           385);
	public Syscall SYS___mac_get_fd             = new Syscall("SYS___mac_get_fd",             386);
	public Syscall SYS___mac_get_file           = new Syscall("SYS___mac_get_file",           387);
	public Syscall SYS___mac_set_fd             = new Syscall("SYS___mac_set_fd",             388);
	public Syscall SYS___mac_set_file           = new Syscall("SYS___mac_set_file",           389);
	public Syscall SYS_kenv                     = new Syscall("SYS_kenv",                     390);
	public Syscall SYS_lchflags                 = new Syscall("SYS_lchflags",                 391);
	public Syscall SYS_uuidgen                  = new Syscall("SYS_uuidgen",                  392);
	public Syscall SYS_sendfile                 = new Syscall("SYS_sendfile",                 393);
	public Syscall SYS_mac_syscall              = new Syscall("SYS_mac_syscall",              394);
	public Syscall SYS_getfsstat                = new Syscall("SYS_getfsstat",                395);
	public Syscall SYS_statfs                   = new Syscall("SYS_statfs",                   396);
	public Syscall SYS_fstatfs                  = new Syscall("SYS_fstatfs",                  397);
	public Syscall SYS_fhstatfs                 = new Syscall("SYS_fhstatfs",                 398);
	public Syscall SYS_ksem_close               = new Syscall("SYS_ksem_close",               400);
	public Syscall SYS_ksem_post                = new Syscall("SYS_ksem_post",                401);
	public Syscall SYS_ksem_wait                = new Syscall("SYS_ksem_wait",                402);
	public Syscall SYS_ksem_trywait             = new Syscall("SYS_ksem_trywait",             403);
	public Syscall SYS_ksem_init                = new Syscall("SYS_ksem_init",                404);
	public Syscall SYS_ksem_open                = new Syscall("SYS_ksem_open",                405);
	public Syscall SYS_ksem_unlink              = new Syscall("SYS_ksem_unlink",              406);
	public Syscall SYS_ksem_getvalue            = new Syscall("SYS_ksem_getvalue",            407);
	public Syscall SYS_ksem_destroy             = new Syscall("SYS_ksem_destroy",             408);
	public Syscall SYS___mac_get_pid            = new Syscall("SYS___mac_get_pid",            409);
	public Syscall SYS___mac_get_link           = new Syscall("SYS___mac_get_link",           410);
	public Syscall SYS___mac_set_link           = new Syscall("SYS___mac_set_link",           411);
	public Syscall SYS_extattr_set_link         = new Syscall("SYS_extattr_set_link",         412);
	public Syscall SYS_extattr_get_link         = new Syscall("SYS_extattr_get_link",         413);
	public Syscall SYS_extattr_delete_link      = new Syscall("SYS_extattr_delete_link",      414);
	public Syscall SYS___mac_execve             = new Syscall("SYS___mac_execve",             415);
	public Syscall SYS_sigaction                = new Syscall("SYS_sigaction",                416);
	public Syscall SYS_sigreturn                = new Syscall("SYS_sigreturn",                417);
	public Syscall SYS_getcontext               = new Syscall("SYS_getcontext",               421);
	public Syscall SYS_setcontext               = new Syscall("SYS_setcontext",               422);
	public Syscall SYS_swapcontext              = new Syscall("SYS_swapcontext",              423);
	public Syscall SYS_swapoff                  = new Syscall("SYS_swapoff",                  424);
	public Syscall SYS___acl_get_link           = new Syscall("SYS___acl_get_link",           425);
	public Syscall SYS___acl_set_link           = new Syscall("SYS___acl_set_link",           426);
	public Syscall SYS___acl_delete_link        = new Syscall("SYS___acl_delete_link",        427);
	public Syscall SYS___acl_aclcheck_link      = new Syscall("SYS___acl_aclcheck_link",      428);
	public Syscall SYS_sigwait                  = new Syscall("SYS_sigwait",                  429);
	public Syscall SYS_thr_create               = new Syscall("SYS_thr_create",               430);
	public Syscall SYS_thr_exit                 = new Syscall("SYS_thr_exit",                 431);
	public Syscall SYS_thr_self                 = new Syscall("SYS_thr_self",                 432);
	public Syscall SYS_thr_kill                 = new Syscall("SYS_thr_kill",                 433);
	public Syscall SYS__umtx_lock               = new Syscall("SYS__umtx_lock",               434);
	public Syscall SYS__umtx_unlock             = new Syscall("SYS__umtx_unlock",             435);
	public Syscall SYS_jail_attach              = new Syscall("SYS_jail_attach",              436);
	public Syscall SYS_extattr_list_fd          = new Syscall("SYS_extattr_list_fd",          437);
	public Syscall SYS_extattr_list_file        = new Syscall("SYS_extattr_list_file",        438);
	public Syscall SYS_extattr_list_link        = new Syscall("SYS_extattr_list_link",        439);
	public Syscall SYS_ksem_timedwait           = new Syscall("SYS_ksem_timedwait",           441);
	public Syscall SYS_thr_suspend              = new Syscall("SYS_thr_suspend",              442);
	public Syscall SYS_thr_wake                 = new Syscall("SYS_thr_wake",                 443);
	public Syscall SYS_kldunloadf               = new Syscall("SYS_kldunloadf",               444);
	public Syscall SYS_audit                    = new Syscall("SYS_audit",                    445);
	public Syscall SYS_auditon                  = new Syscall("SYS_auditon",                  446);
	public Syscall SYS_getauid                  = new Syscall("SYS_getauid",                  447);
	public Syscall SYS_setauid                  = new Syscall("SYS_setauid",                  448);
	public Syscall SYS_getaudit                 = new Syscall("SYS_getaudit",                 449);
	public Syscall SYS_setaudit                 = new Syscall("SYS_setaudit",                 450);
	public Syscall SYS_getaudit_addr            = new Syscall("SYS_getaudit_addr",            451);
	public Syscall SYS_setaudit_addr            = new Syscall("SYS_setaudit_addr",            452);
	public Syscall SYS_auditctl                 = new Syscall("SYS_auditctl",                 453);
	public Syscall SYS__umtx_op                 = new Syscall("SYS__umtx_op",                 454);
	public Syscall SYS_thr_new                  = new Syscall("SYS_thr_new",                  455);
	public Syscall SYS_sigqueue                 = new Syscall("SYS_sigqueue",                 456);
	public Syscall SYS_kmq_open                 = new Syscall("SYS_kmq_open",                 457);
	public Syscall SYS_kmq_setattr              = new Syscall("SYS_kmq_setattr",              458);
	public Syscall SYS_kmq_timedreceive         = new Syscall("SYS_kmq_timedreceive",         459);
	public Syscall SYS_kmq_timedsend            = new Syscall("SYS_kmq_timedsend",            460);
	public Syscall SYS_kmq_notify               = new Syscall("SYS_kmq_notify",               461);
	public Syscall SYS_kmq_unlink               = new Syscall("SYS_kmq_unlink",               462);
	public Syscall SYS_abort2                   = new Syscall("SYS_abort2",                   463);
	public Syscall SYS_thr_set_name             = new Syscall("SYS_thr_set_name",             464);
	public Syscall SYS_aio_fsync                = new Syscall("SYS_aio_fsync",                465);
	public Syscall SYS_rtprio_thread            = new Syscall("SYS_rtprio_thread",            466);
	public Syscall SYS_sctp_peeloff             = new Syscall("SYS_sctp_peeloff",             471);
	public Syscall SYS_sctp_generic_sendmsg     = new Syscall("SYS_sctp_generic_sendmsg",     472);
	public Syscall SYS_sctp_generic_sendmsg_iov = new Syscall("SYS_sctp_generic_sendmsg_iov", 473);
	public Syscall SYS_sctp_generic_recvmsg     = new Syscall("SYS_sctp_generic_recvmsg",     474);
	public Syscall SYS_pread                    = new Syscall("SYS_pread",                    475);
	public Syscall SYS_pwrite                   = new Syscall("SYS_pwrite",                   476);
	public Syscall SYS_mmap                     = new Syscall("SYS_mmap",                     477);
	public Syscall SYS_lseek                    = new Syscall("SYS_lseek",                    478);
	public Syscall SYS_truncate                 = new Syscall("SYS_truncate",                 479);
	public Syscall SYS_ftruncate                = new Syscall("SYS_ftruncate",                480);
	public Syscall SYS_thr_kill2                = new Syscall("SYS_thr_kill2",                481);
	public Syscall SYS_shm_open                 = new Syscall("SYS_shm_open",                 482);
	public Syscall SYS_shm_unlink               = new Syscall("SYS_shm_unlink",               483);
	public Syscall SYS_cpuset                   = new Syscall("SYS_cpuset",                   484);
	public Syscall SYS_cpuset_setid             = new Syscall("SYS_cpuset_setid",             485);
	public Syscall SYS_cpuset_getid             = new Syscall("SYS_cpuset_getid",             486);
	public Syscall SYS_cpuset_getaffinity       = new Syscall("SYS_cpuset_getaffinity",       487);
	public Syscall SYS_cpuset_setaffinity       = new Syscall("SYS_cpuset_setaffinity",       488);
	public Syscall SYS_faccessat                = new Syscall("SYS_faccessat",                489);
	public Syscall SYS_fchmodat                 = new Syscall("SYS_fchmodat",                 490);
	public Syscall SYS_fchownat                 = new Syscall("SYS_fchownat",                 491);
	public Syscall SYS_fexecve                  = new Syscall("SYS_fexecve",                  492);
	public Syscall SYS_fstatat                  = new Syscall("SYS_fstatat",                  493);
	public Syscall SYS_futimesat                = new Syscall("SYS_futimesat",                494);
	public Syscall SYS_linkat                   = new Syscall("SYS_linkat",                   495);
	public Syscall SYS_mkdirat                  = new Syscall("SYS_mkdirat",                  496);
	public Syscall SYS_mkfifoat                 = new Syscall("SYS_mkfifoat",                 497);
	public Syscall SYS_mknodat                  = new Syscall("SYS_mknodat",                  498);
	public Syscall SYS_openat                   = new Syscall("SYS_openat",                   499);
	public Syscall SYS_readlinkat               = new Syscall("SYS_readlinkat",               500);
	public Syscall SYS_renameat                 = new Syscall("SYS_renameat",                 501);
	public Syscall SYS_symlinkat                = new Syscall("SYS_symlinkat",                502);
	public Syscall SYS_unlinkat                 = new Syscall("SYS_unlinkat",                 503);
	public Syscall SYS_posix_openpt             = new Syscall("SYS_posix_openpt",             504);
	public Syscall SYS_gssd_syscall             = new Syscall("SYS_gssd_syscall",             505);
	public Syscall SYS_jail_get                 = new Syscall("SYS_jail_get",                 506);
	public Syscall SYS_jail_set                 = new Syscall("SYS_jail_set",                 507);
	public Syscall SYS_jail_remove              = new Syscall("SYS_jail_remove",              508);
	public Syscall SYS_closefrom                = new Syscall("SYS_closefrom",                509);
	public Syscall SYS___semctl                 = new Syscall("SYS___semctl",                 510);
	public Syscall SYS_msgctl                   = new Syscall("SYS_msgctl",                   511);
	public Syscall SYS_shmctl                   = new Syscall("SYS_shmctl",                   512);
	public Syscall SYS_lpathconf                = new Syscall("SYS_lpathconf",                513);
	public Syscall SYS_cap_new                  = new Syscall("SYS_cap_new",                  514);
	public Syscall SYS_cap_getrights            = new Syscall("SYS_cap_getrights",            515);
	public Syscall SYS_cap_enter                = new Syscall("SYS_cap_enter",                516);
	public Syscall SYS_cap_getmode              = new Syscall("SYS_cap_getmode",              517);
	public Syscall SYS_pdfork                   = new Syscall("SYS_pdfork",                   518);
	public Syscall SYS_pdkill                   = new Syscall("SYS_pdkill",                   519);
	public Syscall SYS_pdgetpid                 = new Syscall("SYS_pdgetpid",                 520);
	public Syscall SYS_pselect                  = new Syscall("SYS_pselect",                  522);
	public Syscall SYS_getloginclass            = new Syscall("SYS_getloginclass",            523);
	public Syscall SYS_setloginclass            = new Syscall("SYS_setloginclass",            524);
	public Syscall SYS_rctl_get_racct           = new Syscall("SYS_rctl_get_racct",           525);
	public Syscall SYS_rctl_get_rules           = new Syscall("SYS_rctl_get_rules",           526);
	public Syscall SYS_rctl_get_limits          = new Syscall("SYS_rctl_get_limits",          527);
	public Syscall SYS_rctl_add_rule            = new Syscall("SYS_rctl_add_rule",            528);
	public Syscall SYS_rctl_remove_rule         = new Syscall("SYS_rctl_remove_rule",         529);
	public Syscall SYS_posix_fallocate          = new Syscall("SYS_posix_fallocate",          530);
	public Syscall SYS_posix_fadvise            = new Syscall("SYS_posix_fadvise",            531);

	/***********************************************************************************************
	 * Sony custom syscalls
	 * https://www.psdevwiki.com/ps4/Syscalls
	 **********************************************************************************************/

	public Syscall SYS_netcontrol                         = new Syscall("SYS_netcontrol",                         99);
	public Syscall SYS_netabort                           = new Syscall("SYS_netabort",                           101);
	public Syscall SYS_netgetsockinfo                     = new Syscall("SYS_netgetsockinfo",                     102);
	public Syscall SYS_socketex                           = new Syscall("SYS_socketex",                           113);
	public Syscall SYS_socketclose                        = new Syscall("SYS_socketclose",                        114);
	public Syscall SYS_netgetiflist                       = new Syscall("SYS_netgetiflist",                       125);
	public Syscall SYS_kqueueex                           = new Syscall("SYS_kqueueex",                           141);
	public Syscall SYS_mtypeprotect                       = new Syscall("SYS_mtypeprotect",                       379);
	public Syscall SYS_regmgr_call                        = new Syscall("SYS_regmgr_call",                        532);
	public Syscall SYS_jitshm_create                      = new Syscall("SYS_jitshm_create",                      533);
	public Syscall SYS_jitshm_alias                       = new Syscall("SYS_jitshm_alias",                       534);
	public Syscall SYS_dl_get_list                        = new Syscall("SYS_dl_get_list",                        535);
	public Syscall SYS_dl_get_info                        = new Syscall("SYS_dl_get_info",                        536);
	public Syscall SYS_dl_notify_event                    = new Syscall("SYS_dl_notify_event",                    537);
	public Syscall SYS_evf_create                         = new Syscall("SYS_evf_create",                         538);
	public Syscall SYS_evf_delete                         = new Syscall("SYS_evf_delete",                         539);
	public Syscall SYS_evf_open                           = new Syscall("SYS_evf_open",                           540);
	public Syscall SYS_evf_close                          = new Syscall("SYS_evf_close",                          541);
	public Syscall SYS_evf_wait                           = new Syscall("SYS_evf_wait",                           542);
	public Syscall SYS_evf_trywait                        = new Syscall("SYS_evf_trywait",                        543);
	public Syscall SYS_evf_set                            = new Syscall("SYS_evf_set",                            544);
	public Syscall SYS_evf_clear                          = new Syscall("SYS_evf_clear",                          545);
	public Syscall SYS_evf_cancel                         = new Syscall("SYS_evf_cancel",                         546);
	public Syscall SYS_query_memory_protection            = new Syscall("SYS_query_memory_protection",            547);
	public Syscall SYS_batch_map                          = new Syscall("SYS_batch_map",                          548);
	public Syscall SYS_osem_create                        = new Syscall("SYS_osem_create",                        549);
	public Syscall SYS_osem_delete                        = new Syscall("SYS_osem_delete",                        550);
	public Syscall SYS_osem_open                          = new Syscall("SYS_osem_open",                          551);
	public Syscall SYS_osem_close                         = new Syscall("SYS_osem_close",                         552);
	public Syscall SYS_osem_wait                          = new Syscall("SYS_osem_wait",                          553);
	public Syscall SYS_osem_trywait                       = new Syscall("SYS_osem_trywait",                       554);
	public Syscall SYS_osem_post                          = new Syscall("SYS_osem_post",                          555);
	public Syscall SYS_osem_cancel                        = new Syscall("SYS_osem_cancel",                        556);
	public Syscall SYS_namedobj_create                    = new Syscall("SYS_namedobj_create",                    557);
	public Syscall SYS_namedobj_delete                    = new Syscall("SYS_namedobj_delete",                    558);
	public Syscall SYS_set_vm_container                   = new Syscall("SYS_set_vm_container",                   559);
	public Syscall SYS_debug_init                         = new Syscall("SYS_debug_init",                         560);
	public Syscall SYS_suspend_process                    = new Syscall("SYS_suspend_process",                    561);
	public Syscall SYS_resume_process                     = new Syscall("SYS_resume_process",                     562);
	public Syscall SYS_opmc_enable                        = new Syscall("SYS_opmc_enable",                        563);
	public Syscall SYS_opmc_disable                       = new Syscall("SYS_opmc_disable",                       564);
	public Syscall SYS_opmc_set_ctl                       = new Syscall("SYS_opmc_set_ctl",                       565);
	public Syscall SYS_opmc_set_ctr                       = new Syscall("SYS_opmc_set_ctr",                       566);
	public Syscall SYS_opmc_get_ctr                       = new Syscall("SYS_opmc_get_ctr",                       567);
	public Syscall SYS_budget_create                      = new Syscall("SYS_budget_create",                      568);
	public Syscall SYS_budget_delete                      = new Syscall("SYS_budget_delete",                      569);
	public Syscall SYS_budget_get                         = new Syscall("SYS_budget_get",                         570);
	public Syscall SYS_budget_set                         = new Syscall("SYS_budget_set",                         571);
	public Syscall SYS_virtual_query                      = new Syscall("SYS_virtual_query",                      572);
	public Syscall SYS_mdbg_call                          = new Syscall("SYS_mdbg_call",                          573);
	public Syscall SYS_sblock_create                      = new Syscall("SYS_sblock_create",                      574);
	public Syscall SYS_sblock_delete                      = new Syscall("SYS_sblock_delete",                      575);
	public Syscall SYS_sblock_enter                       = new Syscall("SYS_sblock_enter",                       576);
	public Syscall SYS_sblock_exit                        = new Syscall("SYS_sblock_exit",                        577);
	public Syscall SYS_sblock_xenter                      = new Syscall("SYS_sblock_xenter",                      578);
	public Syscall SYS_sblock_xexit                       = new Syscall("SYS_sblock_xexit",                       579);
	public Syscall SYS_eport_create                       = new Syscall("SYS_eport_create",                       580);
	public Syscall SYS_eport_delete                       = new Syscall("SYS_eport_delete",                       581);
	public Syscall SYS_eport_trigger                      = new Syscall("SYS_eport_trigger",                      582);
	public Syscall SYS_eport_open                         = new Syscall("SYS_eport_open",                         583);
	public Syscall SYS_eport_close                        = new Syscall("SYS_eport_close",                        584);
	public Syscall SYS_is_in_sandbox                      = new Syscall("SYS_is_in_sandbox",                      585);
	public Syscall SYS_dmem_container                     = new Syscall("SYS_dmem_container",                     586);
	public Syscall SYS_get_authinfo                       = new Syscall("SYS_get_authinfo",                       587);
	public Syscall SYS_mname                              = new Syscall("SYS_mname",                              588);
	public Syscall SYS_dynlib_dlopen                      = new Syscall("SYS_dynlib_dlopen",                      589);
	public Syscall SYS_dynlib_dlclose                     = new Syscall("SYS_dynlib_dlclose",                     590);
	public Syscall SYS_dynlib_dlsym                       = new Syscall("SYS_dynlib_dlsym",                       591);
	public Syscall SYS_dynlib_get_list                    = new Syscall("SYS_dynlib_get_list",                    592);
	public Syscall SYS_dynlib_get_info                    = new Syscall("SYS_dynlib_get_info",                    593);
	public Syscall SYS_dynlib_load_prx                    = new Syscall("SYS_dynlib_load_prx",                    594);
	public Syscall SYS_dynlib_unload_prx                  = new Syscall("SYS_dynlib_unload_prx",                  595);
	public Syscall SYS_dynlib_do_copy_relocations         = new Syscall("SYS_dynlib_do_copy_relocations",         596);
	public Syscall SYS_dynlib_prepare_dlclose             = new Syscall("SYS_dynlib_prepare_dlclose",             597);
	public Syscall SYS_dynlib_get_proc_param              = new Syscall("SYS_dynlib_get_proc_param",              598);
	public Syscall SYS_dynlib_process_needed_and_relocate = new Syscall("SYS_dynlib_process_needed_and_relocate", 599);
	public Syscall SYS_sandbox_path                       = new Syscall("SYS_sandbox_path",                       600);
	public Syscall SYS_mdbg_service                       = new Syscall("SYS_mdbg_service",                       601);
	public Syscall SYS_randomized_path                    = new Syscall("SYS_randomized_path",                    602);
	public Syscall SYS_rdup                               = new Syscall("SYS_rdup",                               603);
	public Syscall SYS_dl_get_metadata                    = new Syscall("SYS_dl_get_metadata",                    604);
	public Syscall SYS_workaround8849                     = new Syscall("SYS_workaround8849",                     605);
	public Syscall SYS_is_development_mode                = new Syscall("SYS_is_development_mode",                606);
	public Syscall SYS_get_self_auth_info                 = new Syscall("SYS_get_self_auth_info",                 607);
	public Syscall SYS_dynlib_get_info_ex                 = new Syscall("SYS_dynlib_get_info_ex",                 608);
	public Syscall SYS_budget_getid                       = new Syscall("SYS_budget_getid",                       609);
	public Syscall SYS_budget_get_ptype                   = new Syscall("SYS_budget_get_ptype",                   610);
	public Syscall SYS_get_paging_stats_of_all_threads    = new Syscall("SYS_get_paging_stats_of_all_threads",    611);
	public Syscall SYS_get_proc_type_info                 = new Syscall("SYS_get_proc_type_info",                 612);
	public Syscall SYS_get_resident_count                 = new Syscall("SYS_get_resident_count",                 613);
	public Syscall SYS_prepare_to_suspend_process         = new Syscall("SYS_prepare_to_suspend_process",         614);
	public Syscall SYS_get_resident_fmem_count            = new Syscall("SYS_get_resident_fmem_count",            615);
	public Syscall SYS_thr_get_name                       = new Syscall("SYS_thr_get_name",                       616);
	public Syscall SYS_set_gpo                            = new Syscall("SYS_set_gpo",                            617);
	public Syscall SYS_get_paging_stats_of_all_objects    = new Syscall("SYS_get_paging_stats_of_all_objects",    618);
	public Syscall SYS_test_debug_rwmem                   = new Syscall("SYS_test_debug_rwmem",                   619);
	public Syscall SYS_free_stack                         = new Syscall("SYS_free_stack",                         620);
	public Syscall SYS_suspend_system                     = new Syscall("SYS_suspend_system",                     621);
	public Syscall SYS_ipmimgr_call                       = new Syscall("SYS_ipmimgr_call",                       622);
	public Syscall SYS_get_gpo                            = new Syscall("SYS_get_gpo",                            623);
	public Syscall SYS_get_vm_map_timestamp               = new Syscall("SYS_get_vm_map_timestamp",               624);
	public Syscall SYS_opmc_set_hw                        = new Syscall("SYS_opmc_set_hw",                        625);
	public Syscall SYS_opmc_get_hw                        = new Syscall("SYS_opmc_get_hw",                        626);
	public Syscall SYS_get_cpu_usage_all                  = new Syscall("SYS_get_cpu_usage_all",                  627);
	public Syscall SYS_mmap_dmem                          = new Syscall("SYS_mmap_dmem",                          628);
	public Syscall SYS_physhm_open                        = new Syscall("SYS_physhm_open",                        629);
	public Syscall SYS_physhm_unlink                      = new Syscall("SYS_physhm_unlink",                      630);
	public Syscall SYS_resume_internal_hdd                = new Syscall("SYS_resume_internal_hdd",                631);
	public Syscall SYS_thr_suspend_ucontext               = new Syscall("SYS_thr_suspend_ucontext",               632);
	public Syscall SYS_thr_resume_ucontext                = new Syscall("SYS_thr_resume_ucontext",                633);
	public Syscall SYS_thr_get_ucontext                   = new Syscall("SYS_thr_get_ucontext",                   634);
	public Syscall SYS_thr_set_ucontext                   = new Syscall("SYS_thr_set_ucontext",                   635);
	public Syscall SYS_set_timezone_info                  = new Syscall("SYS_set_timezone_info",                  636);
	public Syscall SYS_set_phys_fmem_limit                = new Syscall("SYS_set_phys_fmem_limit",                637);
	public Syscall SYS_utc_to_localtime                   = new Syscall("SYS_utc_to_localtime",                   638);
	public Syscall SYS_localtime_to_utc                   = new Syscall("SYS_localtime_to_utc",                   639);
	public Syscall SYS_set_uevt                           = new Syscall("SYS_set_uevt",                           640);
	public Syscall SYS_get_cpu_usage_proc                 = new Syscall("SYS_get_cpu_usage_proc",                 641);
	public Syscall SYS_get_map_statistics                 = new Syscall("SYS_get_map_statistics",                 642);
	public Syscall SYS_set_chicken_switches               = new Syscall("SYS_set_chicken_switches",               643);
	public Syscall SYS_extend_page_table_pool             = new Syscall("SYS_extend_page_table_pool",             644);
	public Syscall SYS_extend_page_table_pool2            = new Syscall("SYS_extend_page_table_pool2",            645);
	public Syscall SYS_get_kernel_mem_statistics          = new Syscall("SYS_get_kernel_mem_statistics",          646);
	public Syscall SYS_get_sdk_compiled_version           = new Syscall("SYS_get_sdk_compiled_version",           647);
	public Syscall SYS_app_state_change                   = new Syscall("SYS_app_state_change",                   648);
	public Syscall SYS_dynlib_get_obj_member              = new Syscall("SYS_dynlib_get_obj_member",              649);
	public Syscall SYS_budget_get_ptype_of_budget         = new Syscall("SYS_budget_get_ptype_of_budget",         650);
	public Syscall SYS_prepare_to_resume_process          = new Syscall("SYS_prepare_to_resume_process",          651);
	public Syscall SYS_process_terminate                  = new Syscall("SYS_process_terminate",                  652);
	public Syscall SYS_blockpool_open                     = new Syscall("SYS_blockpool_open",                     653);
	public Syscall SYS_blockpool_map                      = new Syscall("SYS_blockpool_map",                      654);
	public Syscall SYS_blockpool_unmap                    = new Syscall("SYS_blockpool_unmap",                    655);
	public Syscall SYS_dynlib_get_info_for_libdbg         = new Syscall("SYS_dynlib_get_info_for_libdbg",         656);
	public Syscall SYS_blockpool_batch                    = new Syscall("SYS_blockpool_batch",                    657);
	public Syscall SYS_fdatasync                          = new Syscall("SYS_fdatasync",                          658);
	public Syscall SYS_dynlib_get_list2                   = new Syscall("SYS_dynlib_get_list2",                   659);
	public Syscall SYS_dynlib_get_info2                   = new Syscall("SYS_dynlib_get_info2",                   660);
	public Syscall SYS_aio_submit                         = new Syscall("SYS_aio_submit",                         661);
	public Syscall SYS_aio_multi_delete                   = new Syscall("SYS_aio_multi_delete",                   662);
	public Syscall SYS_aio_multi_wait                     = new Syscall("SYS_aio_multi_wait",                     663);
	public Syscall SYS_aio_multi_poll                     = new Syscall("SYS_aio_multi_poll",                     664);
	public Syscall SYS_aio_get_data                       = new Syscall("SYS_aio_get_data",                       665);
	public Syscall SYS_aio_multi_cancel                   = new Syscall("SYS_aio_multi_cancel",                   666);
	public Syscall SYS_get_bio_usage_all                  = new Syscall("SYS_get_bio_usage_all",                  667);
	public Syscall SYS_aio_create                         = new Syscall("SYS_aio_create",                         668);
	public Syscall SYS_aio_submit_cmd                     = new Syscall("SYS_aio_submit_cmd",                     669);
	public Syscall SYS_aio_init                           = new Syscall("SYS_aio_init",                           670);
	public Syscall SYS_get_page_table_stats               = new Syscall("SYS_get_page_table_stats",               671);
	public Syscall SYS_dynlib_get_list_for_libdbg         = new Syscall("SYS_dynlib_get_list_for_libdbg",         672);
	public Syscall SYS_blockpool_move                     = new Syscall("SYS_blockpool_move",                     673);
	public Syscall SYS_virtual_query_all                  = new Syscall("SYS_virtual_query_all",                  674);
	public Syscall SYS_reserve_2mb_page                   = new Syscall("SYS_reserve_2mb_page",                   675);
	public Syscall SYS_cpumode_yield                      = new Syscall("SYS_cpumode_yield",                      676);
	public Syscall SYS_get_phys_page_size                 = new Syscall("SYS_get_phys_page_size",                 677);

	/***********************************************************************************************
	 * errno/__error
	 **********************************************************************************************/

	public Function Error = new Function("__error");

	public int errno() {
		long pe = Error.call();
		return api.read32(pe);
	}

	public String strerror(int errno) { return libc.strerror(errno); }
	public String strerror() { return libc.strerror(errno()); }

	public static final int EPERM           = 1;          /* Operation not permitted */
	public static final int ENOENT          = 2;          /* No such file or directory */
	public static final int ESRCH           = 3;          /* No such process */
	public static final int EINTR           = 4;          /* Interrupted system call */
	public static final int EIO             = 5;          /* Input/output error */
	public static final int ENXIO           = 6;          /* Device not configured */
	public static final int E2BIG           = 7;          /* Argument list too long */
	public static final int ENOEXEC         = 8;          /* Exec format error */
	public static final int EBADF           = 9;          /* Bad file descriptor */
	public static final int ECHILD          = 10;         /* No child processes */
	public static final int EDEADLK         = 11;         /* Resource deadlock avoided */
	public static final int ENOMEM          = 12;         /* Cannot allocate memory */
	public static final int EACCES          = 13;         /* Permission denied */
	public static final int EFAULT          = 14;         /* Bad address */
	public static final int ENOTBLK         = 15;         /* Block device required */
	public static final int EBUSY           = 16;         /* Device busy */
	public static final int EEXIST          = 17;         /* File exists */
	public static final int EXDEV           = 18;         /* Cross-device link */
	public static final int ENODEV          = 19;         /* Operation not supported by device */
	public static final int ENOTDIR         = 20;         /* Not a directory */
	public static final int EISDIR          = 21;         /* Is a directory */
	public static final int EINVAL          = 22;         /* Invalid argument */
	public static final int ENFILE          = 23;         /* Too many open files in system */
	public static final int EMFILE          = 24;         /* Too many open files */
	public static final int ENOTTY          = 25;         /* Inappropriate ioctl for device */
	public static final int ETXTBSY         = 26;         /* Text file busy */
	public static final int EFBIG           = 27;         /* File too large */
	public static final int ENOSPC          = 28;         /* No space left on device */
	public static final int ESPIPE          = 29;         /* Illegal seek */
	public static final int EROFS           = 30;         /* Read-only filesystem */
	public static final int EMLINK          = 31;         /* Too many links */
	public static final int EPIPE           = 32;         /* Broken pipe */
	public static final int EDOM            = 33;         /* Numerical argument out of domain */
	public static final int ERANGE          = 34;         /* Result too large */
	public static final int EAGAIN          = 35;         /* Resource temporarily unavailable */
	public static final int EWOULDBLOCK     = EAGAIN;     /* Operation would block */
	public static final int EINPROGRESS     = 36;         /* Operation now in progress */
	public static final int EALREADY        = 37;         /* Operation already in progress */
	public static final int ENOTSOCK        = 38;         /* Socket operation on non-socket */
	public static final int EDESTADDRREQ    = 39;         /* Destination address required */
	public static final int EMSGSIZE        = 40;         /* Message too long */
	public static final int EPROTOTYPE      = 41;         /* Protocol wrong type for socket */
	public static final int ENOPROTOOPT     = 42;         /* Protocol not available */
	public static final int EPROTONOSUPPORT = 43;         /* Protocol not supported */
	public static final int ESOCKTNOSUPPORT = 44;         /* Socket type not supported */
	public static final int EOPNOTSUPP      = 45;         /* Operation not supported */
	public static final int ENOTSUP         = EOPNOTSUPP; /* Operation not supported */
	public static final int EPFNOSUPPORT    = 46;         /* Protocol family not supported */
	public static final int EAFNOSUPPORT    = 47;         /* AF not supported by PF */
	public static final int EADDRINUSE      = 48;         /* Address already in use */
	public static final int EADDRNOTAVAIL   = 49;         /* Can't assign requested address */
	public static final int ENETDOWN        = 50;         /* Network is down */
	public static final int ENETUNREACH     = 51;         /* Network is unreachable */
	public static final int ENETRESET       = 52;         /* Network dropped connection on reset */
	public static final int ECONNABORTED    = 53;         /* Software caused connection abort */
	public static final int ECONNRESET      = 54;         /* Connection reset by peer */
	public static final int ENOBUFS         = 55;         /* No buffer space available */
	public static final int EISCONN         = 56;         /* Socket is already connected */
	public static final int ENOTCONN        = 57;         /* Socket is not connected */
	public static final int ESHUTDOWN       = 58;         /* Can't send after socket shutdown */
	public static final int ETOOMANYREFS    = 59;         /* Too many references: can't splice */
	public static final int ETIMEDOUT       = 60;         /* Operation timed out */
	public static final int ECONNREFUSED    = 61;         /* Connection refused */
	public static final int ELOOP           = 62;         /* Too many levels of symbolic links */
	public static final int ENAMETOOLONG    = 63;         /* File name too long */
	public static final int EHOSTDOWN       = 64;         /* Host is down */
	public static final int EHOSTUNREACH    = 65;         /* No route to host */
	public static final int ENOTEMPTY       = 66;         /* Directory not empty */
	public static final int EPROCLIM        = 67;         /* Too many processes */
	public static final int EUSERS          = 68;         /* Too many users */
	public static final int EDQUOT          = 69;         /* Disc quota exceeded */
	public static final int ESTALE          = 70;         /* Stale NFS file handle */
	public static final int EREMOTE         = 71;         /* Too many levels of remote in path */
	public static final int EBADRPC         = 72;         /* RPC struct is bad */
	public static final int ERPCMISMATCH    = 73;         /* RPC version wrong */
	public static final int EPROGUNAVAIL    = 74;         /* RPC prog. not avail */
	public static final int EPROGMISMATCH   = 75;         /* Program version wrong */
	public static final int EPROCUNAVAIL    = 76;         /* Bad procedure for program */
	public static final int ENOLCK          = 77;         /* No locks available */
	public static final int ENOSYS          = 78;         /* Function not implemented */
	public static final int EFTYPE          = 79;         /* Inappropriate file type or format */
	public static final int EAUTH           = 80;         /* Authentication error */
	public static final int ENEEDAUTH       = 81;         /* Need authenticator */
	public static final int EIDRM           = 82;         /* Identifier removed */
	public static final int ENOMSG          = 83;         /* No message of desired type */
	public static final int EOVERFLOW       = 84;         /* Value too large to be stored in type */
	public static final int ECANCELED       = 85;         /* Operation canceled */
	public static final int EILSEQ          = 86;         /* Illegal byte sequence */
	public static final int ENOATTR         = 87;         /* Attribute not found */
	public static final int EDOOFUS         = 88;         /* Programming error */
	public static final int EBADMSG         = 89;         /* Bad message */
	public static final int EMULTIHOP       = 90;         /* Multihop attempted */
	public static final int ENOLINK         = 91;         /* Link has been severed */
	public static final int EPROTO          = 92;         /* Protocol error */
	public static final int ENOTCAPABLE     = 93;         /* Capabilities insufficient */
	public static final int ECAPMODE        = 94;         /* Not permitted in capability mode */

	/***********************************************************************************************
	 * SceAIO subsystem calls
	 * https://www.psdevwiki.com/ps4/Vulnerabilities - PoC for PS4 5.00-12.02 ... by abc
	 **********************************************************************************************/

	public static final int AIO_CMD_READ  = 0x0001;
	public static final int AIO_CMD_WRITE = 0x0002;
	public static final int AIO_CMD_MASK  = 0x0fff;
	public static final int AIO_CMD_MULTI = 0x1000;

	public static final int AIO_CMD_MULTI_READ  = AIO_CMD_MULTI | AIO_CMD_READ;
	public static final int AIO_CMD_MULTI_WRITE = AIO_CMD_MULTI | AIO_CMD_WRITE;

	public static final int AIO_PRIORITY_LOW  = 1;
	public static final int AIO_PRIORITY_MID  = 2;
	public static final int AIO_PRIORITY_HIGH = 3;

	public static final int AIO_STATE_COMPLETED = 2;
	public static final int AIO_STATE_ABORTED   = 3;

	/** Wait for all requests */
	public static final int AIO_WAIT_AND = 1;
	/** Wait for one request */
	public static final int AIO_WAIT_OR = 2;

	/** Max number of requests that can be created/polled/canceled/deleted/waited */
	public static final int MAX_AIO_IDS = 128;

	public static final int SCE_KERNEL_ERROR_ESRCH = 0x80020003;

	/** The various SceAIO syscalls that copies out errors/states will not check if the address is
	 * NULL and will return EFAULT. this dummy buffer will serve as the default argument so users
	 * don't need to specify one */
	public AioErrorArray AIO_ERRORS = new AioErrorArray(MAX_AIO_IDS);

	/** Struct for the result of an AIO read/write request */
	public class AioResult implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final FieldInt64 retval = new FieldInt64(this, 0);
		public final FieldInt32 state  = new FieldInt32(this, retval.next);
		public final FieldInt32 pad    = new FieldInt32(this, state.next);
		public final int SIZE = pad.next;
		public AioResult(long address) {
			buffer = new Buffer(address, SIZE);
		}
		public AioResult() {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
		}
		public AioResult(long retval, int state) {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
			set(retval, state);
		}
		public void set(long retval, int state) {
			this.retval.set(retval);
			this.state .set(state);
		}
		public String toString() {
			return "AioResult{" + retval + "," + state + "}";
		}
	}

	/** Struct for an AIO read/write request */
	public class AioRWRequest implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final FieldOffT  offset = new FieldOffT (this, 0);
		public final FieldSizeT nbyte  = new FieldSizeT(this, offset.next);
		public final FieldPtr   buf    = new FieldPtr  (this, nbyte.next);
		public final FieldPtr   result = new FieldPtr  (this, buf.next);
		public final FieldInt32 fd     = new FieldInt32(this, result.next);
		public final FieldInt32 pad    = new FieldInt32(this, fd.next);
		public final int SIZE = pad.next;
		public AioRWRequest(long address) {
			buffer = new Buffer(address, SIZE);
		}
		public AioRWRequest() {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
		}
		public AioRWRequest(long offset, long nbyte, BufferLike buf, BufferLike result, int fd) {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
			set(offset, nbyte, buf, result, fd);
		}
		public void set(long offset, long nbyte, BufferLike buf, BufferLike result, int fd) {
			this.offset.set(offset);
			this.nbyte .set(nbyte);
			this.buf   .set(buf    == null ? 0 : buf   .address());
			this.result.set(result == null ? 0 : result.address());
			this.fd    .set(fd);
		}
		public AioResult result() {
			return new AioResult(result.get());
		}
		public String toString() {
			return "{" + offset.get() + "," + nbyte.get() + "," + buf.get() + "," + result.get() +
				"," + fd.get() + "}";
		}
	}

	/** Array of AIO read/write requests */
	public class AioRWRequestArray implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final int count, stride;
		public AioRWRequestArray(int count) {
			this.count = count;
			stride = (new AioRWRequest(0)).SIZE;
			buffer = new Buffer(count * stride);
			buffer.fill((byte)0);
		}
		public AioRWRequestArray(long address, int count) {
			this.count = count;
			stride = (new AioRWRequest(0)).SIZE;
			buffer = new Buffer(address, count * stride);
		}
		public AioRWRequest get(int i) {
			return new AioRWRequest(address() + i * stride);
		}
		public void set(int i, long offset, long nbyte, BufferLike buf, BufferLike result, int fd) {
			get(i).set(offset, nbyte, buf, result, fd);
		}
		public AioRWRequestArray slice(int offset, int count) {
			return new AioRWRequestArray(address() + offset * stride, count);
		}
		public String toString() {
			String s = "{";
			for (int i = 0; i < count; i++) {
				if (i == 0) {
					s += get(i).toString();
				} else {
					s += ", " + get(i).toString();
				}
			}
			return s + "}";
		}
		/** Initialize an aio request array with the bare minimum for aio_submit_cmd to accept it.
		 * Equivalent to make_reqs1 in lapse.mjs/lua */
		public void initMinimal() {
			for (int i = 0; i < count; i++) {
				set(i, 0, 0, null, null, -1);
			}
		}
	}

	/** AIO request ID assigned during submit */
	public class AioSubmitId implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final FieldInt32 id = new FieldInt32(this, 0);
		public final int SIZE = id.next;
		public AioSubmitId(long address) {
			buffer = new Buffer(address, SIZE);
		}
		public AioSubmitId() {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
		}
		public AioSubmitId(int id) {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
			set(id);
		}
		public int get() { return this.id.get(); }
		public void set(int id) { this.id.set(id); }
		public String toString() { return Integer.toString(id.get()); }
	}

	/** Array of AIO request IDs */
	public class AioSubmitIdArray implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final int count, stride;
		public AioSubmitIdArray(int count) {
			this.count = count;
			stride = (new AioSubmitId(0)).SIZE;
			buffer = new Buffer(count * stride);
			buffer.fill((byte)0);
		}
		public AioSubmitIdArray(long address, int count) {
			this.count = count;
			stride = (new AioSubmitId(0)).SIZE;
			buffer = new Buffer(address, count * stride);
		}
		public AioSubmitId get(int i) {
			return new AioSubmitId(address() + i * stride);
		}
		public void set(int i, int id) {
			get(i).set(id);
		}
		public AioSubmitIdArray slice(int offset, int count) {
			return new AioSubmitIdArray(address() + offset * stride, count);
		}
		public String toString() {
			String s = "{";
			for (int i = 0; i < count; i++) {
				if (i == 0) {
					s += get(i).toString();
				} else {
					s += ", " + get(i).toString();
				}
			}
			return s + "}";
		}
	}

	/** AIO error */
	public class AioError implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final FieldInt32 id = new FieldInt32(this, 0);
		public final int SIZE = id.next;
		public AioError(long address) {
			buffer = new Buffer(address, SIZE);
		}
		public AioError() {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
		}
		public AioError(int id) {
			buffer = new Buffer(SIZE);
			buffer.fill((byte)0);
			set(id);
		}
		public int get() { return this.id.get(); }
		public void set(int id) { this.id.set(id); }
		public String toString() { return "0x" + Integer.toHexString(id.get()); }
	}

	/** Array of AIO errors */
	public class AioErrorArray implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size();    }
		public final int count, stride;
		public AioErrorArray(int count) {
			this.count = count;
			stride = (new AioError(0)).SIZE;
			buffer = new Buffer(count * stride);
			buffer.fill((byte)-1);
		}
		public AioErrorArray(long address, int count) {
			this.count = count;
			stride = (new AioError(0)).SIZE;
			buffer = new Buffer(address, count * stride);
		}
		public AioError get(int i) {
			return new AioError(address() + i * stride);
		}
		public void set(int i, int id) {
			get(i).set(id);
		}
		public AioErrorArray slice(int offset, int count) {
			return new AioErrorArray(address() + offset * stride, count);
		}
		public String toString() {
			String s = "{";
			for (int i = 0; i < count; i++) {
				if (i == 0) {
					s += get(i).toString();
				} else {
					s += ", " + get(i).toString();
				}
			}
			return s + "}";
		}
	}

	/** Submit a batch of AIO read/write requests. Only up to MAX_AIO_IDS(128).
	 * @param cmd AIO_CMD_READ/AIO_CMD_WRITE | optional AIO_CMD_MULTI
	 * @param requests read/write requests (offset/nbyte/buf/result/fd)
	 * @param ids output: request submit IDs
	 */
	public void aioSubmitCmd(int cmd, AioRWRequestArray requests, AioSubmitIdArray ids) {
		int nreqs = requests.count, nids = ids.count;
		if (nreqs > nids) {
			throw new SystemCallInvalid(nreqs + " reqs > " + nids + " ids");
		}
		if (nreqs > MAX_AIO_IDS) {
			// Use aioSubmitCmdAutoBatch instead
			throw new SystemCallInvalid(nreqs + " reqs > " + MAX_AIO_IDS + " MAX_AIO_IDS");
		}
		long r = SYS_aio_submit_cmd.call(cmd, requests.address(), nreqs, AIO_PRIORITY_HIGH, ids.address());
		if (r != 0) {
			throw new SystemCallFailed("cmd=0x" + Long.toHexString(cmd) + ", " +
				// "reqs=" + requests + ", " + // takes too much space
				"prio=3, " + "ids=0x" + ids + " => " + r, errno(), libc);
		}
	}

	/** Submit a batch of AIO read/write requests. Will use multiple calls for large arrays.
	 * @param cmd AIO_CMD_READ/AIO_CMD_WRITE | optional AIO_CMD_MULTI
	 * @param requests read/write requests (offset/nbyte/buf/result/fd)
	 * @param ids output: request submit IDs
	 */
	public void aioSubmitCmdAutoBatch(int cmd, AioRWRequestArray requests, AioSubmitIdArray ids) {
		int nreqs = requests.count, nids = ids.count;
		if (nreqs > nids) {
			throw new SystemCallInvalid(nreqs + " reqs > " + nids + " ids");
		}
		int rem = nreqs % MAX_AIO_IDS, batches = (nids - rem) / MAX_AIO_IDS;
		for (int ibatch = 0; ibatch < batches; ibatch++) {
			int start = ibatch * MAX_AIO_IDS, count = MAX_AIO_IDS;
			aioSubmitCmd(cmd, requests.slice(start, count), ids.slice(start, count));
		}
		if (rem > 0) {
			int start = batches * MAX_AIO_IDS;
			aioSubmitCmd(cmd, requests.slice(start, rem), ids.slice(start, rem));
		}
	}

	/** Cancel one or more pending AIO requests. Only up to MAX_AIO_IDS(128).
	 * @param ids submit IDs of requests to cancel
	 * @param errors status codes returned from aio_multi_cancel
	 */
	public void aioMultiCancel(AioSubmitIdArray ids, AioErrorArray errors) {
		if (errors == null) {
			errors = AIO_ERRORS;
		}
		int nids = ids.count, nerrs = errors.count;
		if (nids > nerrs) {
			throw new SystemCallInvalid(nids + " ids > " + nerrs + " errors");
		}
		if (nids > MAX_AIO_IDS) {
			// Use aioUtilBatchCancel instead
			throw new SystemCallInvalid(nids + " ids > " + MAX_AIO_IDS + " MAX_AIO_IDS");
		}
		long r = SYS_aio_multi_cancel.call(ids.address(), nids, errors.address());
		if (r != 0) {
			throw new SystemCallFailed("ids=" + ids + " errors=" + errors + " => " + r, errno(), libc);
		}
	}

	/** Cancel one or more pending AIO requests. Will use multiple calls for large arrays.
	 * @param ids submit IDs of requests to cancel
	 * @param errors status codes returned from aio_multi_cancel
	 */
	public void aioUtilBatchCancel(AioSubmitIdArray ids, AioErrorArray errors) {
		int nids = ids.count;
		if (errors != null) {
			int nerrs = errors.count;
			if (nids > nerrs) {
				throw new SystemCallInvalid(nids + " ids > " + nerrs + " errors");
			}
		}
		int rem = nids % MAX_AIO_IDS, batches = (nids - rem) / MAX_AIO_IDS;
		for (int ibatch = 0; ibatch < batches; ibatch++) {
			int start = ibatch * MAX_AIO_IDS, count = MAX_AIO_IDS;
			aioMultiCancel(ids.slice(start, count), errors != null ? errors.slice(start, count) : null);
		}
		if (rem > 0) {
			int start = batches * MAX_AIO_IDS;
			aioMultiCancel(ids.slice(start, rem), errors != null ? errors.slice(start, rem) : null);
		}
	}

	/** Poll one or more pending AIO requests. Only up to MAX_AIO_IDS(128).
	 * @param ids submit IDs of requests to poll
	 * @param errors status codes returned from aio_multi_poll
	 */
	public void aioMultiPoll(AioSubmitIdArray ids, AioErrorArray errors) {
		if (errors == null) {
			errors = AIO_ERRORS;
		}
		int nids = ids.count, nerrs = errors.count;
		if (nids > nerrs) {
			throw new SystemCallInvalid(nids + " ids > " + nerrs + " errors");
		}
		if (nids > MAX_AIO_IDS) {
			// Use aioUtilBatchPoll instead
			throw new SystemCallInvalid(nids + " ids > " + MAX_AIO_IDS + " MAX_AIO_IDS");
		}
		long r = SYS_aio_multi_poll.call(ids.address(), nids, errors.address());
		if (r != 0) {
			throw new SystemCallFailed("ids=" + ids + " errors=" + errors + " => " + r, errno(), libc);
		}
	}

	/** Poll one or more pending AIO requests. Will use multiple calls for large arrays.
	 * @param ids submit IDs of requests to poll
	 * @param errors status codes returned from aio_multi_poll
	 */
	public void aioUtilBatchPoll(AioSubmitIdArray ids, AioErrorArray errors) {
		int nids = ids.count;
		if (errors != null) {
			int nerrs = errors.count;
			if (nids > nerrs) {
				throw new SystemCallInvalid(nids + " ids > " + nerrs + " errors");
			}
		}
		int rem = nids % MAX_AIO_IDS, batches = (nids - rem) / MAX_AIO_IDS;
		for (int ibatch = 0; ibatch < batches; ibatch++) {
			int start = ibatch * MAX_AIO_IDS, count = MAX_AIO_IDS;
			aioMultiPoll(ids.slice(start, count), errors != null ? errors.slice(start, count) : null);
		}
		if (rem > 0) {
			int start = batches * MAX_AIO_IDS;
			aioMultiPoll(ids.slice(start, rem), errors != null ? errors.slice(start, rem) : null);
		}
	}

	/** Wait for one or more pending AIO requests. Only up to MAX_AIO_IDS(128).
	 * @param ids submit IDs of requests to poll
	 * @param errors status codes returned from aio_multi_wait
	 * @param mode AIO_WAIT_AND (wait for all) or AIO_WAIT_OR (wait for one)
	 * @param timeout max microseconds(?) to wait (or 0 to wait forever)
	 * @return number of microseconds actually waited(??) or 0
	 */
	public int aioMultiWait(AioSubmitIdArray ids, AioErrorArray errors, int mode, int timeout) {
		if (errors == null) {
			errors = AIO_ERRORS;
		}
		int nids = ids.count, nerrs = errors.count;
		if (nids > nerrs) {
			throw new SystemCallInvalid(nids + " ids > " + nerrs + " errors");
		}
		if (nids > MAX_AIO_IDS) {
			throw new SystemCallInvalid(nids + " ids > " + MAX_AIO_IDS + " MAX_AIO_IDS");
		}
		Buffer usecBuf = null;
		long usec = 0;
		if (timeout != 0) {
			usecBuf = new Buffer(4);
			usecBuf.putInt(0, timeout);
			usec = usecBuf.address();
		}
		long r = SYS_aio_multi_wait.call(ids.address(), nids, errors.address(), mode, usec);
		if (r != 0) {
			int e = errno();
			if (e == ETIMEDOUT && timeout != 0) {
				return usecBuf.getInt(0);
			}
			throw new SystemCallFailed("ids=" + ids + " errors=" + errors + " mode=" + mode +
				" => " + r, errno(), libc);
		}
		if (timeout != 0) {
			return usecBuf.getInt(0);
		}
		return 0;
	}
	public void aioMultiWait(AioSubmitIdArray ids, AioErrorArray errors, int mode) {
		aioMultiWait(ids, errors, mode, 0);
	}

	/** Delete one or more pending AIO requests. Only up to MAX_AIO_IDS(128).
	 * @param ids submit IDs of requests to delete
	 * @param errors status codes returned from aio_multi_delete
	 */
	public void aioMultiDelete(AioSubmitIdArray ids, AioErrorArray errors) {
		if (errors == null) {
			errors = AIO_ERRORS;
		}
		int nids = ids.count, nerrs = errors.count;
		if (nids > nerrs) {
			throw new SystemCallInvalid(nids + " ids > " + nerrs + " errors");
		}
		if (nids > MAX_AIO_IDS) {
			// Use aioUtilBatchDelete instead
			throw new SystemCallInvalid(nids + " ids > " + MAX_AIO_IDS + " MAX_AIO_IDS");
		}
		long r = SYS_aio_multi_delete.call(ids.address(), nids, errors.address());
		if (r != 0) {
			throw new SystemCallFailed("ids=" + ids + " errors=" + errors + " => " + r, errno(), libc);
		}
	}

	/** Delete one or more pending AIO requests. Will use multiple calls for large arrays.
	 * @param ids submit IDs of requests to delete
	 * @param errors status codes returned from aio_multi_delete
	 */
	public void aioUtilBatchDelete(AioSubmitIdArray ids, AioErrorArray errors) {
		int nids = ids.count;
		if (errors != null) {
			int nerrs = errors.count;
			if (nids > nerrs) {
				throw new SystemCallInvalid(nids + " ids > " + nerrs + " errors");
			}
		}
		int rem = nids % MAX_AIO_IDS, batches = (nids - rem) / MAX_AIO_IDS;
		for (int ibatch = 0; ibatch < batches; ibatch++) {
			int start = ibatch * MAX_AIO_IDS, count = MAX_AIO_IDS;
			aioMultiDelete(ids.slice(start, count), errors != null ? errors.slice(start, count) : null);
		}
		if (rem > 0) {
			int start = batches * MAX_AIO_IDS;
			aioMultiDelete(ids.slice(start, rem), errors != null ? errors.slice(start, rem) : null);
		}
	}

	/** Poll and delete one or more pending AIO requests. Like free_aios2 in lapse.mjs.
	 * @param ids submit IDs of requests to delete
	 */
	public void aioUtilBatchPollDelete(AioSubmitIdArray ids) {
		aioUtilBatchPoll(ids, null);
		aioUtilBatchDelete(ids, null);
	}

	/** Cancel, poll and delete one or more pending AIO requests. Like free_aios in lapse.mjs.
	 * @param ids submit IDs of requests to delete
	 */
	public void aioUtilBatchCancelPollDelete(AioSubmitIdArray ids) {
		aioUtilBatchCancel(ids, null);
		aioUtilBatchPollDelete(ids);
	}

	/***********************************************************************************************
	 * Socket APIs
	 * http://fxr.watson.org/fxr/source/sys/socket.h?v=FREEBSD-9-1
	 * http://fxr.watson.org/fxr/source/netinet/tcp.h?v=FREEBSD-9-1
	 * http://fxr.watson.org/fxr/source/netinet/tcp_fsm.h?v=FREEBSD-9-1
	 * http://fxr.watson.org/fxr/source/netinet6/in6.h?v=FREEBSD-9-1
	 **********************************************************************************************/

	// sys/socket.h - Address families
	public static final byte AF_UNIX  = 1;  // local to host (pipes, portals)
	public static final byte AF_INET  = 2;  // internetwork: UDP, TCP, etc.
	public static final byte AF_INET6 = 28; // IPv6

	// sys/socket.h - Types
	public static final byte SOCK_STREAM = 1; // stream socket
	public static final byte SOCK_DGRAM  = 2; // datagram socket
	public static final byte SOCK_RAW    = 3; // raw-protocol socket

	// sys/socket.h - Level number for (get/set)sockopt() to apply to socket itself
	public static final int SOL_SOCKET = 0xffff; // options for socket level

	// sys/socket.h - Option flags per-socket
	public static final int SO_DEBUG        = 0x0001; // turn on debugging info recording
	public static final int SO_ACCEPTCONN   = 0x0002; // socket has had listen()
	public static final int SO_REUSEADDR    = 0x0004; // allow local address reuse
	public static final int SO_KEEPALIVE    = 0x0008; // keep connections alive
	public static final int SO_DONTROUTE    = 0x0010; // just use interface addresses
	public static final int SO_BROADCAST    = 0x0020; // permit sending of broadcast msgs
	public static final int SO_USELOOPBACK  = 0x0040; // bypass hardware when possible
	public static final int SO_LINGER       = 0x0080; // linger on close if data present
	public static final int SO_OOBINLINE    = 0x0100; // leave received OOB data in line
	public static final int SO_REUSEPORT    = 0x0200; // allow local address & port reuse
	public static final int SO_TIMESTAMP    = 0x0400; // timestamp received dgram traffic
	public static final int SO_NOSIGPIPE    = 0x0800; // no SIGPIPE from EPIPE
	public static final int SO_ACCEPTFILTER = 0x1000; // there is an accept filter
	public static final int SO_BINTIME      = 0x2000; // timestamp received dgram traffic
	public static final int SO_NO_OFFLOAD   = 0x4000; // socket cannot be offloaded
	public static final int SO_NO_DDP       = 0x8000; // disable direct data placement

	// in.h - Protocols common to RFC 1700, POSIX, and X/Open
	public static final int IPPROTO_IP   = 0;  // dummy for IP
	public static final int IPPROTO_ICMP = 1;  // control message protocol
	public static final int IPPROTO_TCP  = 6;  // tcp
	public static final int IPPROTO_UDP  = 17; // user datagram protocol

	// in.h - Protocols (RFC 1700)
	public static final int IPPROTO_IPV6 = 41; // IP6 header

	// netinet/tcp.h - User-settable options (used with setsockopt)
	public static final int TCP_NODELAY    = 0x01;  // don't delay send to coalesce packets
	public static final int TCP_MAXSEG     = 0x02;  // set maximum segment size
	public static final int TCP_NOPUSH     = 0x04;  // don't push last block of write
	public static final int TCP_NOOPT      = 0x08;  // don't use TCP options
	public static final int TCP_MD5SIG     = 0x10;  // use MD5 digests (RFC2385)
	public static final int TCP_INFO       = 0x20;  // retrieve tcp_info structure
	public static final int TCP_CONGESTION = 0x40;  // get/set congestion control algorithm
	public static final int TCP_KEEPINIT   = 0x80;  // N, time to establish connection
	public static final int TCP_KEEPIDLE   = 0x100; // L,N,X start keeplives after this period
	public static final int TCP_KEEPINTVL  = 0x200; // L,N interval between keepalives
	public static final int TCP_KEEPCNT    = 0x400; // L,N number of keepalives before close

	// netinet/tcp_fsm.h - TCP FSM state definitions
	public static final int TCPS_CLOSED       = 0;  // closed
	public static final int TCPS_LISTEN       = 1;  // listening for connection
	public static final int TCPS_SYN_SENT     = 2;  // active, have sent syn
	public static final int TCPS_SYN_RECEIVED = 3;  // have sent and received syn
	// states < TCPS_ESTABLISHED are those where connections not established
	public static final int TCPS_ESTABLISHED  = 4;  // established
	public static final int TCPS_CLOSE_WAIT   = 5;  // rcvd fin, waiting for close
	// states > TCPS_CLOSE_WAIT are those where user has closed
	public static final int TCPS_FIN_WAIT_1   = 6;  // have closed, sent fin
	public static final int TCPS_CLOSING      = 7;  // closed xchd FIN; await FIN ACK
	public static final int TCPS_LAST_ACK     = 8;  // had fin and close; await FIN ACK
	// states > TCPS_CLOSE_WAIT && < TCPS_FIN_WAIT_2 await ACK of FIN
	public static final int TCPS_FIN_WAIT_2   = 9;  // have closed, fin is acked
	public static final int TCPS_TIME_WAIT    = 10; // in 2*msl quiet wait after close

	// netinet6/in6.h - Options for use with [gs]etsockopt at the IPV6 level.
	public static final int IPV6_2292PKTOPTIONS = 25; // buf/cmsghdr; set/get IPv6 options
	public static final int IPV6_PKTINFO        = 46; // in6_pktinfo; send if, src addr
	public static final int IPV6_NEXTHOP        = 48; // sockaddr; next hop addr
	public static final int IPV6_RTHDR          = 51; // ip6_rthdr; send routing header
	public static final int IPV6_TCLASS         = 61; // int; send traffic class value

	public static short shortToNetworkByteOrder(int x) {
		return (short)(((x & 0xff) << 8) | ((x & 0xff00) >> 8));
	}

	public static int ipAddrToNetworkByteOrder(int a, int b, int c, int d) {
		return ((d & 0xff) << 24) | ((c & 0xff) << 16) | ((b & 0xff) << 8) | (a & 0xff);
	}

	public class SocketAddress extends Buffer {
		private String repr;
		/** @param family AF_UNIX, AF_INET, AF_INET6 */
		public SocketAddress(int family, int port, int ipA, int ipB, int ipC, int ipD) {
			super(16);
			fill((byte)0);
			putByte(1, (byte)family);
			putShort(2, shortToNetworkByteOrder(port));
			putInt(4, ipAddrToNetworkByteOrder(ipA, ipB, ipC, ipD));
			repr = family + ":" + ipA + "." + ipB + "." + ipC + "." + ipD + ":" + port;
		}
		public String toString() {
			return repr;
		}
	}

	public class FileDescriptor {
		public int fd;
		public boolean owner;
		/** @param fd File descriptor
		 *  @param owner Will this object be responsible for closing the fd? */
		public FileDescriptor(int fd, boolean owner) {
			this.fd = fd;
			this.owner = owner;
		}
		public void close() {
			if (owner) {
				SYS_close.call(fd);
				fd = -1;
				owner = false;
			}
		}
		// This is probably a terrible idea but just in case...
		public void finalize() throws Throwable {
			try {
				close();
			} finally {
				super.finalize();
			}
		}

		public Buffer read(int size) {
			Buffer buf = new Buffer(size);
			long r = SYS_read.call(fd, buf.address(), size);
			if (r != 0) throw new SystemCallFailed("fd=" + fd + ", size=" + size, errno(), libc);
			return buf;
		}

		public void write(Buffer buf) {
			long r = SYS_write.call(fd, buf.address(), buf.size());
			if (r != 0) throw new SystemCallFailed("fd=" + fd + ", size=" + buf.size(), errno(), libc);
		}
	}

	public FileDescriptor[] pipe() {
		Buffer buf = new Buffer(8);
		long r = SYS_pipe.call(buf.address());
		if (r != 0) throw new SystemCallFailed("", errno(), libc);
		return new FileDescriptor[]{
			new FileDescriptor(buf.getInt(0), true),
			new FileDescriptor(buf.getInt(1), true),
		};
	}

	public class Socket extends FileDescriptor {
		/** @param fd Socket file descriptor
		 *  @param owner Will this object be responsible for closing the socket? */
		public Socket(int fd, boolean owner) {
			super(fd, owner);
		}

		/** Creates an endpoint for communication.
		 * @param domain AF_UNIX, AF_INET, AF_INET6
		 * @param type SOCK_STREAM, SOCK_DGRAM, SOCK_RAW
		 * @param protocol IPPROTO_TCP, IPPROTO_UDP (optional)
		 */
		public Socket(int domain, int type, int protocol) {
			super(0, false);
			long r = SYS_socket.call(domain, type, protocol);
			if (r == -1) throw new SystemCallFailed("domain=" + domain + ", type=" + type, errno(), libc);
			this.fd = (int)r;
			this.owner = true;
		}

		/** Assign a local protocol address to a socket. */
		public void bind(SocketAddress addr) {
			long r = SYS_bind.call(fd, addr.address(), addr.size());
			if (r != 0) throw new SystemCallFailed("fd=" + fd + " addr=" + addr.toString(), errno(), libc);
		}

		/** Signal willingness to accept connections and specify a queue limit. */
		public void listen(int backlog) {
			long r = SYS_listen.call(fd, backlog);
			if (r != 0) throw new SystemCallFailed("fd=" + fd + " backlog=" + backlog, errno(), libc);
		}

		/** Accept a connection on a socket. If there are no pending connections, and the original
		 * socket is not marked as non-blocking, blocks the caller until a connection is present.
		 * If the socket is non-blocking, throws and sets errno to EWOULDBLOCK or EAGAIN.
		 * Returns a new socket for the particular connection that was accepted. */
		public Socket accept() {
			long r = SYS_accept.call(fd, /* out addr */ 0, /* out addrlen */ 0);
			if (r == -1) throw new SystemCallFailed("fd=" + fd, errno(), libc);
			return new Socket((int)r, true);
		}

		/** Initiate a connection on a socket. Behaviour depends on socket type.
		 * For SOCK_STREAM, this call attempts to connect to another socket as specified by name. */
		public void connect(SocketAddress name) {
			long r = SYS_connect.call(fd, name.address(), name.size());
			if (r != 0) throw new SystemCallFailed("fd=" + fd + " name=" + name.toString(), errno(), libc);
		}

		/** Get an arbitrary socket option, writing to a given buffer.
		 * @param level SOL_SOCKET for general options, IPPROTO_* for protocol specific options
		 * @param optname SO_* for SOL_SOCKET or IP_*, TCP_*, IPV6_*, etc. for IPPROTO_*
		 * @param optval option data buffer, depends on option
		 * @return number of bytes actually written to the buffer
		*/
		public int getOption(int level, int optname, Buffer optval) {
			Buffer size = new Buffer(4);
			size.putInt(0, optval.size());
			// NOTE: The lapse code does some weird stuff around make_aliased_rthdrs that makes me
			// not want to screw with this buffer beyond what getsockopt does to it
			// optval.fill((byte)0); // don't uncomment just yet
			long r = SYS_getsockopt.call(fd, level, optname, optval.address(), size.address());
			if (r != 0) {
				throw new SystemCallFailed(
					"level=" + level + " name=" + optname + " size=" + optval.size(),
					errno(), libc);
			}
			return size.getInt(0);
		}

		/** Get an arbitrary socket option, writing to a newly allocated buffer.
		 * @param level SOL_SOCKET for general options, IPPROTO_* for protocol specific options
		 * @param optname SO_* for SOL_SOCKET or IP_*, TCP_*, IPV6_*, etc. for IPPROTO_*
		 * @param optsize option data buffer size, depends on option, can't be auto-detected
		*/
		public Buffer getOption(int level, int optname, int optsize) {
			Buffer optval = new Buffer(optsize);
			getOption(level, optname, optval);
			return optval;
		}

		/** Set an arbitrary socket option.
		 * @param level SOL_SOCKET for general options, IPPROTO_* for protocol specific options
		 * @param optname SO_* for SOL_SOCKET or IP_*, TCP_*, IPV6_*, etc. for IPPROTO_*
		 * @param optval buffer to read option data from, format is option-specific
		*/
		public void setOption(int level, int optname, Buffer optval) {
			long r = SYS_setsockopt.call(fd, level, optname, optval.address(), optval.size());
			if (r != 0) {
				throw new SystemCallFailed(
					"level=" + level + " name=" + optname + " size=" + optval.size(),
					errno(), libc);
			}
		}

		/** Set the SO_REUSEADDR socket option. */
		public void setReuseAddr(boolean enable) {
			Buffer optval = new Buffer(4);
			optval.putInt(0, enable ? 1 : 0);
			setOption(SOL_SOCKET, SO_REUSEADDR, optval);
		}

		/** Set the SO_LINGER socket option. */
		public void setLinger(boolean onoff, int linger) {
			Buffer optval = new Buffer(8);
			optval.putInt(0, onoff ? 1 : 0);
			optval.putInt(4, linger);
			setOption(SOL_SOCKET, SO_REUSEADDR, optval);
		}

		/** Get the IPV6_RTHDR socket option. This writes a struct with a particular size.
		 * We deliberately don't check the size here because Lapse abuses the size field.
		 * @return number of bytes actually written to the buffer */
		public int getRthdr(Buffer dst) {
			return getOption(IPPROTO_IPV6, IPV6_RTHDR, dst);
		}
		public int getRthdr(Rthdr dst) {
			return getRthdr(dst.buffer);
		}

		/** Set the IPV6_RTHDR socket option. This takes a struct with a particular size.
		 * We deliberately don't check the size here because Lapse abuses the size field. */
		public void setRthdr(Buffer src) {
			setOption(IPPROTO_IPV6, IPV6_RTHDR, src);
		}
		public void setRthdr(Rthdr src) {
			setRthdr(src.buffer);
		}

		/** Get the TCP FSM state for this socket.
		 * @return TCPS_CLOSED, TCPS_LISTEN, etc. */
		public int getTCPState() {
			Buffer info = new Buffer(256);
			Buffer size = new Buffer(4);
			size.putInt(0, info.size());
			SYS_getsockopt.call(fd, IPPROTO_TCP, TCP_INFO, info.address(), size.address());
			return info.getByte(0);
		}
	}

	public class Rthdr implements BufferLike {
		public Buffer buffer;
		public long address() { return buffer.address(); }
		public long size()    { return buffer.size(); }
		public final FieldByte  next     = new FieldByte(this, 0);
		public final FieldByte  len      = new FieldByte(this, next.next);
		public final FieldByte  type     = new FieldByte(this, len.next);
		public final FieldByte  segleft  = new FieldByte(this, type.next);
		public final FieldInt32 reserved = new FieldInt32(this, segleft.next);
		// in6_addr (uint8 s6_addr[16]) ip6r_sigs[segleft] + padding
		public Rthdr(long address, int size) {
			buffer = new Buffer(address, size);
			len.set((byte)(((size >> 3) - 1) & ~1));
			segleft.set((byte)(len.get() >> 1));
		}
		public Rthdr(int size) {
			buffer = new Buffer(size);
			len.set((byte)(((size >> 3) - 1) & ~1));
			segleft.set((byte)(len.get() >> 1));
		}
	}

	/** Creates an unnamed pair of connected sockets.
	 * @param domain AF_UNIX, AF_INET, AF_INET6
	 * @param type SOCK_STREAM, SOCK_DGRAM, SOCK_RAW
	 */
	public Socket[] socketPair(int domain, int type) {
		Buffer buf = new Buffer(8);
		long r = SYS_socketpair.call(domain, type, /* protocol */ 0, buf.address());
		if (r != 0) throw new SystemCallFailed("domain=" + domain + ", type=" + type, errno(), libc);
		return new Socket[]{
			new Socket(buf.getInt(0), true),
			new Socket(buf.getInt(4), true),
		};
	}

	public class SocketUDP6 extends Socket {
		public SocketUDP6() {
			super(AF_INET6, SOCK_DGRAM, IPPROTO_UDP);
		}
	}

	/***********************************************************************************************
	 * Thread pinning & priority APIs
	 * http://fxr.watson.org/fxr/source/sys/cpuset.h?v=FREEBSD-9-1
	 * https://man.freebsd.org/cgi/man.cgi?query=cpuset&sektion=2&apropos=0&manpath=FreeBSD+9.1-RELEASE
	 * https://man.freebsd.org/cgi/man.cgi?query=rtprio_thread&sektion=2&n=1
	 **********************************************************************************************/

	public static final int CPU_LEVEL_ROOT   = 1; // all system CPUs
	public static final int CPU_LEVEL_CPUSET = 2; // available cpus for which
	public static final int CPU_LEVEL_WHICH  = 3; // actual mask/id for which

	public static final int CPU_WHICH_TID    = 1; // id is a thread id  (-1 means current)
	public static final int CPU_WHICH_PID    = 2; // id is a process id (-1 means current)
	public static final int CPU_WHICH_CPUSET = 3; // id is a cpuset id  (-1 means current)
	public static final int CPU_WHICH_IRQ    = 4; // id is an IRQ number

	public static final int CPU_SETSIZE = 16; // this is what Lapse does, I will assume it's correct

	public int cpuGetAffinityForCurrentThread() {
		Buffer cpuset = new Buffer(CPU_SETSIZE);
		cpuset.fill((byte)0);
		long r = SYS_cpuset_getaffinity.call(CPU_LEVEL_WHICH, CPU_WHICH_TID, -1, cpuset.size(), cpuset.address());
		if (r != 0) throw new SystemCallFailed("WHICH, TID, -1, " + cpuset.toString(), errno(), libc);
		return cpuset.getInt(0);
	}

	public void cpuSetAffinityForCurrentThread(int mask) {
		Buffer cpuset = new Buffer(CPU_SETSIZE);
		cpuset.fill((byte)0);
		cpuset.putInt(0, mask);
		long r = SYS_cpuset_setaffinity.call(CPU_LEVEL_WHICH, CPU_WHICH_TID, -1, cpuset.size(), cpuset.address());
		if (r != 0) throw new SystemCallFailed("WHICH, TID, -1, " + cpuset.toString(), errno(), libc);
	}

	public static final short RTP_LOOKUP = 0; // look up priority
	public static final short RTP_SET    = 1; // set priority

	public static final short PRI_REALTIME  = 2; // real time process
	public static final short PRI_TIMESHARE = 3; // time sharing process (default?)

	public static class ThreadPriority {
		public short type; // PRI_REALTIME, PRI_TIMESHARE
		public short prio; // 0 to 31 on BSDs, but apparently 0 is bad and 256 is good on PS4?
		public ThreadPriority(short type, int prio) {
			this.type = type;
			this.prio = (short)prio;
		}
		public String toString() {
			return "{" + this.type + "," + this.prio + "}";
		}
	}

	public ThreadPriority cpuGetPriorityForCurrentThread() {
		Buffer rtprio = new Buffer(4); // 2 uint16s
		long r = SYS_rtprio_thread.call(RTP_LOOKUP, 0, rtprio.address());
		if (r != 0) throw new SystemCallFailed("LOOKUP, 0", errno(), libc);
		return new ThreadPriority(rtprio.getShort(0), rtprio.getShort(2));
	}

	public void cpuSetPriorityForCurrentThread(ThreadPriority priority) {
		Buffer rtprio = new Buffer(4); // 2 uint16s
		rtprio.putShort(0, priority.type);
		rtprio.putShort(2, priority.prio);
		long r = SYS_rtprio_thread.call(RTP_SET, 0, rtprio.address());
		if (r != 0) throw new SystemCallFailed("SET, 0, " + priority.toString(), errno(), libc);
	}

	public long cpuGetCurrentThreadId() {
		Buffer buf = new Buffer(8);
		SYS_thr_self.call(buf.address());
		return buf.getLong(0);
	}

	/***********************************************************************************************
	 * Sleep/wait
	 **********************************************************************************************/

	public void nanosleep(long seconds, long nanoseconds) {
		Buffer timespec = new Buffer(16);
		timespec.putLong(0, seconds);
		timespec.putLong(8, nanoseconds);
		long r = SYS_nanosleep.call(timespec.address(), /* unslept time */ 0);
		if (r != 0) throw new SystemCallFailed("sec=" + seconds + " nsec=" + nanoseconds, errno(), libc);
	}

	public void sleep(long milliseconds) {
		long seconds = milliseconds / 1000;
		long nanoseconds = (milliseconds - (seconds * 1000)) * 1000000;
		nanosleep(seconds, nanoseconds);
	}

	public boolean waitUntilEqualLong(Buffer buffer, int offset, long reference, int intervalMs, int timeoutMs) {
		while (timeoutMs > 0) {
			long value = buffer.getLong(offset);
			if (value == reference) {
				return true;
			}
			sleep(intervalMs);
			timeoutMs -= intervalMs;
		}
		return false;
	}

	public boolean waitUntilNotEqualLong(Buffer buffer, int offset, long reference, int intervalMs, int timeoutMs) {
		while (timeoutMs > 0) {
			long value = buffer.getLong(offset);
			if (value != reference) {
				return true;
			}
			sleep(intervalMs);
			timeoutMs -= intervalMs;
		}
		return false;
	}

	public void yield() {
		SYS_sched_yield.call();
	}

	/***********************************************************************************************
	 * pthreads
	 **********************************************************************************************/

	public Function PthreadBarrierInit = new Function("pthread_barrier_init");
	public Function PthreadBarrierWait = new Function("pthread_barrier_wait");

	public class PthreadBarrier {
		private Buffer handle;

		public PthreadBarrier(int count) {
			handle = new Buffer(8);
			handle.fill((byte)0);
			PthreadBarrierInit.call(handle.address(), 0, count);
		}

		public void barrierWait() {
			PthreadBarrierWait.call(handle.address());
		}
	}

	/***********************************************************************************************
	 * evf
	 **********************************************************************************************/

	public class Evf {
		public int id = -1;

		public Evf(int attributes, long flags) {
			Buffer name = new Buffer(1);
			name.putByte(0, (byte)1);
			long r = SYS_evf_create.call(name.address(), attributes, flags);
			if (r == -1) {
				throw new SystemCallFailed("", errno(), libc);
			}
			id = (int)r;
		}

		public void set(long flags) {
			long r = SYS_evf_set.call(id, flags);
			if (r == -1) throw new SystemCallFailed("", errno(), libc);
		}

		public void clear() {
			long r = SYS_evf_clear.call(id);
			if (r == -1) throw new SystemCallFailed("", errno(), libc);
		}

		public void delete() {
			if (id == -1) return;
			long r = SYS_evf_delete.call(id);
			if (r == -1) throw new SystemCallFailed("", errno(), libc);
		}
	}
}
