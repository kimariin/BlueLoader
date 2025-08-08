package org.bdj.payload;

import java.util.HashMap;

import org.bdj.UITextConsole;
import org.bdj.api.API;
import org.bdj.api.Buffer;

public class LibKernel {
	public static API api;

	private UITextConsole console;
	private long mLibKernelBase;

	public LibKernel(UITextConsole console) throws Exception {
		api = API.getInstance();
		this.console = console;

		KernelModuleInfo kmi = sceKernelGetModuleInfoFromAddr();
		mLibKernelBase = kmi.firstSegment;
	}

	/***********************************************************************************************
	 * FreeBSD syscalls
	 * http://fxr.watson.org/fxr/source/sys/syscall.h?v=FREEBSD-9-1
	 **********************************************************************************************/

	public static final int SYS_syscall                             = 0;
	public static final int SYS_exit                                = 1;
	public static final int SYS_fork                                = 2;
	public static final int SYS_read                                = 3;
	public static final int SYS_write                               = 4;
	public static final int SYS_open                                = 5;
	public static final int SYS_close                               = 6;
	public static final int SYS_wait4                               = 7;
	public static final int SYS_link                                = 9;
	public static final int SYS_unlink                              = 10;
	public static final int SYS_chdir                               = 12;
	public static final int SYS_fchdir                              = 13;
	public static final int SYS_mknod                               = 14;
	public static final int SYS_chmod                               = 15;
	public static final int SYS_chown                               = 16;
	public static final int SYS_break                               = 17;
	public static final int SYS_freebsd4_getfsstat                  = 18;
	public static final int SYS_getpid                              = 20;
	public static final int SYS_mount                               = 21;
	public static final int SYS_unmount                             = 22;
	public static final int SYS_setuid                              = 23;
	public static final int SYS_getuid                              = 24;
	public static final int SYS_geteuid                             = 25;
	public static final int SYS_ptrace                              = 26;
	public static final int SYS_recvmsg                             = 27;
	public static final int SYS_sendmsg                             = 28;
	public static final int SYS_recvfrom                            = 29;
	public static final int SYS_accept                              = 30;
	public static final int SYS_getpeername                         = 31;
	public static final int SYS_getsockname                         = 32;
	public static final int SYS_access                              = 33;
	public static final int SYS_chflags                             = 34;
	public static final int SYS_fchflags                            = 35;
	public static final int SYS_sync                                = 36;
	public static final int SYS_kill                                = 37;
	public static final int SYS_getppid                             = 39;
	public static final int SYS_dup                                 = 41;
	public static final int SYS_pipe                                = 42;
	public static final int SYS_getegid                             = 43;
	public static final int SYS_profil                              = 44;
	public static final int SYS_ktrace                              = 45;
	public static final int SYS_getgid                              = 47;
	public static final int SYS_getlogin                            = 49;
	public static final int SYS_setlogin                            = 50;
	public static final int SYS_acct                                = 51;
	public static final int SYS_sigaltstack                         = 53;
	public static final int SYS_ioctl                               = 54;
	public static final int SYS_reboot                              = 55;
	public static final int SYS_revoke                              = 56;
	public static final int SYS_symlink                             = 57;
	public static final int SYS_readlink                            = 58;
	public static final int SYS_execve                              = 59;
	public static final int SYS_umask                               = 60;
	public static final int SYS_chroot                              = 61;
	public static final int SYS_msync                               = 65;
	public static final int SYS_vfork                               = 66;
	public static final int SYS_sbrk                                = 69;
	public static final int SYS_sstk                                = 70;
	public static final int SYS_vadvise                             = 72;
	public static final int SYS_munmap                              = 73;
	public static final int SYS_mprotect                            = 74;
	public static final int SYS_madvise                             = 75;
	public static final int SYS_mincore                             = 78;
	public static final int SYS_getgroups                           = 79;
	public static final int SYS_setgroups                           = 80;
	public static final int SYS_getpgrp                             = 81;
	public static final int SYS_setpgid                             = 82;
	public static final int SYS_setitimer                           = 83;
	public static final int SYS_swapon                              = 85;
	public static final int SYS_getitimer                           = 86;
	public static final int SYS_getdtablesize                       = 89;
	public static final int SYS_dup2                                = 90;
	public static final int SYS_fcntl                               = 92;
	public static final int SYS_select                              = 93;
	public static final int SYS_fsync                               = 95;
	public static final int SYS_setpriority                         = 96;
	public static final int SYS_socket                              = 97;
	public static final int SYS_connect                             = 98;
	public static final int SYS_getpriority                         = 100;
	public static final int SYS_bind                                = 104;
	public static final int SYS_setsockopt                          = 105;
	public static final int SYS_listen                              = 106;
	public static final int SYS_gettimeofday                        = 116;
	public static final int SYS_getrusage                           = 117;
	public static final int SYS_getsockopt                          = 118;
	public static final int SYS_readv                               = 120;
	public static final int SYS_writev                              = 121;
	public static final int SYS_settimeofday                        = 122;
	public static final int SYS_fchown                              = 123;
	public static final int SYS_fchmod                              = 124;
	public static final int SYS_setreuid                            = 126;
	public static final int SYS_setregid                            = 127;
	public static final int SYS_rename                              = 128;
	public static final int SYS_flock                               = 131;
	public static final int SYS_mkfifo                              = 132;
	public static final int SYS_sendto                              = 133;
	public static final int SYS_shutdown                            = 134;
	public static final int SYS_socketpair                          = 135;
	public static final int SYS_mkdir                               = 136;
	public static final int SYS_rmdir                               = 137;
	public static final int SYS_utimes                              = 138;
	public static final int SYS_adjtime                             = 140;
	public static final int SYS_setsid                              = 147;
	public static final int SYS_quotactl                            = 148;
	public static final int SYS_nlm_syscall                         = 154;
	public static final int SYS_nfssvc                              = 155;
	public static final int SYS_freebsd4_statfs                     = 157;
	public static final int SYS_freebsd4_fstatfs                    = 158;
	public static final int SYS_lgetfh                              = 160;
	public static final int SYS_getfh                               = 161;
	public static final int SYS_freebsd4_getdomainname              = 162;
	public static final int SYS_freebsd4_setdomainname              = 163;
	public static final int SYS_freebsd4_uname                      = 164;
	public static final int SYS_sysarch                             = 165;
	public static final int SYS_rtprio                              = 166;
	public static final int SYS_semsys                              = 169;
	public static final int SYS_msgsys                              = 170;
	public static final int SYS_shmsys                              = 171;
	public static final int SYS_freebsd6_pread                      = 173;
	public static final int SYS_freebsd6_pwrite                     = 174;
	public static final int SYS_setfib                              = 175;
	public static final int SYS_ntp_adjtime                         = 176;
	public static final int SYS_setgid                              = 181;
	public static final int SYS_setegid                             = 182;
	public static final int SYS_seteuid                             = 183;
	public static final int SYS_stat                                = 188;
	public static final int SYS_fstat                               = 189;
	public static final int SYS_lstat                               = 190;
	public static final int SYS_pathconf                            = 191;
	public static final int SYS_fpathconf                           = 192;
	public static final int SYS_getrlimit                           = 194;
	public static final int SYS_setrlimit                           = 195;
	public static final int SYS_getdirentries                       = 196;
	public static final int SYS_freebsd6_mmap                       = 197;
	public static final int SYS___syscall                           = 198;
	public static final int SYS_freebsd6_lseek                      = 199;
	public static final int SYS_freebsd6_truncate                   = 200;
	public static final int SYS_freebsd6_ftruncate                  = 201;
	public static final int SYS___sysctl                            = 202;
	public static final int SYS_mlock                               = 203;
	public static final int SYS_munlock                             = 204;
	public static final int SYS_undelete                            = 205;
	public static final int SYS_futimes                             = 206;
	public static final int SYS_getpgid                             = 207;
	public static final int SYS_poll                                = 209;
	public static final int SYS_freebsd7___semctl                   = 220;
	public static final int SYS_semget                              = 221;
	public static final int SYS_semop                               = 222;
	public static final int SYS_freebsd7_msgctl                     = 224;
	public static final int SYS_msgget                              = 225;
	public static final int SYS_msgsnd                              = 226;
	public static final int SYS_msgrcv                              = 227;
	public static final int SYS_shmat                               = 228;
	public static final int SYS_freebsd7_shmctl                     = 229;
	public static final int SYS_shmdt                               = 230;
	public static final int SYS_shmget                              = 231;
	public static final int SYS_clock_gettime                       = 232;
	public static final int SYS_clock_settime                       = 233;
	public static final int SYS_clock_getres                        = 234;
	public static final int SYS_ktimer_create                       = 235;
	public static final int SYS_ktimer_delete                       = 236;
	public static final int SYS_ktimer_settime                      = 237;
	public static final int SYS_ktimer_gettime                      = 238;
	public static final int SYS_ktimer_getoverrun                   = 239;
	public static final int SYS_nanosleep                           = 240;
	public static final int SYS_ntp_gettime                         = 248;
	public static final int SYS_minherit                            = 250;
	public static final int SYS_rfork                               = 251;
	public static final int SYS_openbsd_poll                        = 252;
	public static final int SYS_issetugid                           = 253;
	public static final int SYS_lchown                              = 254;
	public static final int SYS_aio_read                            = 255;
	public static final int SYS_aio_write                           = 256;
	public static final int SYS_lio_listio                          = 257;
	public static final int SYS_getdents                            = 272;
	public static final int SYS_lchmod                              = 274;
	public static final int SYS_netbsd_lchown                       = 275;
	public static final int SYS_lutimes                             = 276;
	public static final int SYS_netbsd_msync                        = 277;
	public static final int SYS_nstat                               = 278;
	public static final int SYS_nfstat                              = 279;
	public static final int SYS_nlstat                              = 280;
	public static final int SYS_preadv                              = 289;
	public static final int SYS_pwritev                             = 290;
	public static final int SYS_freebsd4_fhstatfs                   = 297;
	public static final int SYS_fhopen                              = 298;
	public static final int SYS_fhstat                              = 299;
	public static final int SYS_modnext                             = 300;
	public static final int SYS_modstat                             = 301;
	public static final int SYS_modfnext                            = 302;
	public static final int SYS_modfind                             = 303;
	public static final int SYS_kldload                             = 304;
	public static final int SYS_kldunload                           = 305;
	public static final int SYS_kldfind                             = 306;
	public static final int SYS_kldnext                             = 307;
	public static final int SYS_kldstat                             = 308;
	public static final int SYS_kldfirstmod                         = 309;
	public static final int SYS_getsid                              = 310;
	public static final int SYS_setresuid                           = 311;
	public static final int SYS_setresgid                           = 312;
	public static final int SYS_aio_return                          = 314;
	public static final int SYS_aio_suspend                         = 315;
	public static final int SYS_aio_cancel                          = 316;
	public static final int SYS_aio_error                           = 317;
	public static final int SYS_oaio_read                           = 318;
	public static final int SYS_oaio_write                          = 319;
	public static final int SYS_olio_listio                         = 320;
	public static final int SYS_yield                               = 321;
	public static final int SYS_mlockall                            = 324;
	public static final int SYS_munlockall                          = 325;
	public static final int SYS___getcwd                            = 326;
	public static final int SYS_sched_setparam                      = 327;
	public static final int SYS_sched_getparam                      = 328;
	public static final int SYS_sched_setscheduler                  = 329;
	public static final int SYS_sched_getscheduler                  = 330;
	public static final int SYS_sched_yield                         = 331;
	public static final int SYS_sched_get_priority_max              = 332;
	public static final int SYS_sched_get_priority_min              = 333;
	public static final int SYS_sched_rr_get_interval               = 334;
	public static final int SYS_utrace                              = 335;
	public static final int SYS_freebsd4_sendfile                   = 336;
	public static final int SYS_kldsym                              = 337;
	public static final int SYS_jail                                = 338;
	public static final int SYS_nnpfs_syscall                       = 339;
	public static final int SYS_sigprocmask                         = 340;
	public static final int SYS_sigsuspend                          = 341;
	public static final int SYS_freebsd4_sigaction                  = 342;
	public static final int SYS_sigpending                          = 343;
	public static final int SYS_freebsd4_sigreturn                  = 344;
	public static final int SYS_sigtimedwait                        = 345;
	public static final int SYS_sigwaitinfo                         = 346;
	public static final int SYS___acl_get_file                      = 347;
	public static final int SYS___acl_set_file                      = 348;
	public static final int SYS___acl_get_fd                        = 349;
	public static final int SYS___acl_set_fd                        = 350;
	public static final int SYS___acl_delete_file                   = 351;
	public static final int SYS___acl_delete_fd                     = 352;
	public static final int SYS___acl_aclcheck_file                 = 353;
	public static final int SYS___acl_aclcheck_fd                   = 354;
	public static final int SYS_extattrctl                          = 355;
	public static final int SYS_extattr_set_file                    = 356;
	public static final int SYS_extattr_get_file                    = 357;
	public static final int SYS_extattr_delete_file                 = 358;
	public static final int SYS_aio_waitcomplete                    = 359;
	public static final int SYS_getresuid                           = 360;
	public static final int SYS_getresgid                           = 361;
	public static final int SYS_kqueue                              = 362;
	public static final int SYS_kevent                              = 363;
	public static final int SYS_extattr_set_fd                      = 371;
	public static final int SYS_extattr_get_fd                      = 372;
	public static final int SYS_extattr_delete_fd                   = 373;
	public static final int SYS___setugid                           = 374;
	public static final int SYS_eaccess                             = 376;
	public static final int SYS_afs3_syscall                        = 377;
	public static final int SYS_nmount                              = 378;
	public static final int SYS___mac_get_proc                      = 384;
	public static final int SYS___mac_set_proc                      = 385;
	public static final int SYS___mac_get_fd                        = 386;
	public static final int SYS___mac_get_file                      = 387;
	public static final int SYS___mac_set_fd                        = 388;
	public static final int SYS___mac_set_file                      = 389;
	public static final int SYS_kenv                                = 390;
	public static final int SYS_lchflags                            = 391;
	public static final int SYS_uuidgen                             = 392;
	public static final int SYS_sendfile                            = 393;
	public static final int SYS_mac_syscall                         = 394;
	public static final int SYS_getfsstat                           = 395;
	public static final int SYS_statfs                              = 396;
	public static final int SYS_fstatfs                             = 397;
	public static final int SYS_fhstatfs                            = 398;
	public static final int SYS_ksem_close                          = 400;
	public static final int SYS_ksem_post                           = 401;
	public static final int SYS_ksem_wait                           = 402;
	public static final int SYS_ksem_trywait                        = 403;
	public static final int SYS_ksem_init                           = 404;
	public static final int SYS_ksem_open                           = 405;
	public static final int SYS_ksem_unlink                         = 406;
	public static final int SYS_ksem_getvalue                       = 407;
	public static final int SYS_ksem_destroy                        = 408;
	public static final int SYS___mac_get_pid                       = 409;
	public static final int SYS___mac_get_link                      = 410;
	public static final int SYS___mac_set_link                      = 411;
	public static final int SYS_extattr_set_link                    = 412;
	public static final int SYS_extattr_get_link                    = 413;
	public static final int SYS_extattr_delete_link                 = 414;
	public static final int SYS___mac_execve                        = 415;
	public static final int SYS_sigaction                           = 416;
	public static final int SYS_sigreturn                           = 417;
	public static final int SYS_getcontext                          = 421;
	public static final int SYS_setcontext                          = 422;
	public static final int SYS_swapcontext                         = 423;
	public static final int SYS_swapoff                             = 424;
	public static final int SYS___acl_get_link                      = 425;
	public static final int SYS___acl_set_link                      = 426;
	public static final int SYS___acl_delete_link                   = 427;
	public static final int SYS___acl_aclcheck_link                 = 428;
	public static final int SYS_sigwait                             = 429;
	public static final int SYS_thr_create                          = 430;
	public static final int SYS_thr_exit                            = 431;
	public static final int SYS_thr_self                            = 432;
	public static final int SYS_thr_kill                            = 433;
	public static final int SYS__umtx_lock                          = 434;
	public static final int SYS__umtx_unlock                        = 435;
	public static final int SYS_jail_attach                         = 436;
	public static final int SYS_extattr_list_fd                     = 437;
	public static final int SYS_extattr_list_file                   = 438;
	public static final int SYS_extattr_list_link                   = 439;
	public static final int SYS_ksem_timedwait                      = 441;
	public static final int SYS_thr_suspend                         = 442;
	public static final int SYS_thr_wake                            = 443;
	public static final int SYS_kldunloadf                          = 444;
	public static final int SYS_audit                               = 445;
	public static final int SYS_auditon                             = 446;
	public static final int SYS_getauid                             = 447;
	public static final int SYS_setauid                             = 448;
	public static final int SYS_getaudit                            = 449;
	public static final int SYS_setaudit                            = 450;
	public static final int SYS_getaudit_addr                       = 451;
	public static final int SYS_setaudit_addr                       = 452;
	public static final int SYS_auditctl                            = 453;
	public static final int SYS__umtx_op                            = 454;
	public static final int SYS_thr_new                             = 455;
	public static final int SYS_sigqueue                            = 456;
	public static final int SYS_kmq_open                            = 457;
	public static final int SYS_kmq_setattr                         = 458;
	public static final int SYS_kmq_timedreceive                    = 459;
	public static final int SYS_kmq_timedsend                       = 460;
	public static final int SYS_kmq_notify                          = 461;
	public static final int SYS_kmq_unlink                          = 462;
	public static final int SYS_abort2                              = 463;
	public static final int SYS_thr_set_name                        = 464;
	public static final int SYS_aio_fsync                           = 465;
	public static final int SYS_rtprio_thread                       = 466;
	public static final int SYS_sctp_peeloff                        = 471;
	public static final int SYS_sctp_generic_sendmsg                = 472;
	public static final int SYS_sctp_generic_sendmsg_iov            = 473;
	public static final int SYS_sctp_generic_recvmsg                = 474;
	public static final int SYS_pread                               = 475;
	public static final int SYS_pwrite                              = 476;
	public static final int SYS_mmap                                = 477;
	public static final int SYS_lseek                               = 478;
	public static final int SYS_truncate                            = 479;
	public static final int SYS_ftruncate                           = 480;
	public static final int SYS_thr_kill2                           = 481;
	public static final int SYS_shm_open                            = 482;
	public static final int SYS_shm_unlink                          = 483;
	public static final int SYS_cpuset                              = 484;
	public static final int SYS_cpuset_setid                        = 485;
	public static final int SYS_cpuset_getid                        = 486;
	public static final int SYS_cpuset_getaffinity                  = 487;
	public static final int SYS_cpuset_setaffinity                  = 488;
	public static final int SYS_faccessat                           = 489;
	public static final int SYS_fchmodat                            = 490;
	public static final int SYS_fchownat                            = 491;
	public static final int SYS_fexecve                             = 492;
	public static final int SYS_fstatat                             = 493;
	public static final int SYS_futimesat                           = 494;
	public static final int SYS_linkat                              = 495;
	public static final int SYS_mkdirat                             = 496;
	public static final int SYS_mkfifoat                            = 497;
	public static final int SYS_mknodat                             = 498;
	public static final int SYS_openat                              = 499;
	public static final int SYS_readlinkat                          = 500;
	public static final int SYS_renameat                            = 501;
	public static final int SYS_symlinkat                           = 502;
	public static final int SYS_unlinkat                            = 503;
	public static final int SYS_posix_openpt                        = 504;
	public static final int SYS_gssd_syscall                        = 505;
	public static final int SYS_jail_get                            = 506;
	public static final int SYS_jail_set                            = 507;
	public static final int SYS_jail_remove                         = 508;
	public static final int SYS_closefrom                           = 509;
	public static final int SYS___semctl                            = 510;
	public static final int SYS_msgctl                              = 511;
	public static final int SYS_shmctl                              = 512;
	public static final int SYS_lpathconf                           = 513;
	public static final int SYS_cap_new                             = 514;
	public static final int SYS_cap_getrights                       = 515;
	public static final int SYS_cap_enter                           = 516;
	public static final int SYS_cap_getmode                         = 517;
	public static final int SYS_pdfork                              = 518;
	public static final int SYS_pdkill                              = 519;
	public static final int SYS_pdgetpid                            = 520;
	public static final int SYS_pselect                             = 522;
	public static final int SYS_getloginclass                       = 523;
	public static final int SYS_setloginclass                       = 524;
	public static final int SYS_rctl_get_racct                      = 525;
	public static final int SYS_rctl_get_rules                      = 526;
	public static final int SYS_rctl_get_limits                     = 527;
	public static final int SYS_rctl_add_rule                       = 528;
	public static final int SYS_rctl_remove_rule                    = 529;
	public static final int SYS_posix_fallocate                     = 530;
	public static final int SYS_posix_fadvise                       = 531;

	/***********************************************************************************************
	 * Sony custom syscalls
	 * https://www.psdevwiki.com/ps4/Syscalls
	 **********************************************************************************************/

	public static final int SYS_netcontrol                          = 99;
	public static final int SYS_netabort                            = 101;
	public static final int SYS_netgetsockinfo                      = 102;
	public static final int SYS_socketex                            = 113;
	public static final int SYS_socketclose                         = 114;
	public static final int SYS_netgetiflist                        = 125;
	public static final int SYS_kqueueex                            = 141;
	public static final int SYS_mtypeprotect                        = 379;
	public static final int SYS_regmgr_call                         = 532;
	public static final int SYS_jitshm_create                       = 533;
	public static final int SYS_jitshm_alias                        = 534;
	public static final int SYS_dl_get_list                         = 535;
	public static final int SYS_dl_get_info                         = 536;
	public static final int SYS_dl_notify_event                     = 537;
	public static final int SYS_evf_create                          = 538;
	public static final int SYS_evf_delete                          = 539;
	public static final int SYS_evf_open                            = 540;
	public static final int SYS_evf_close                           = 541;
	public static final int SYS_evf_wait                            = 542;
	public static final int SYS_evf_trywait                         = 543;
	public static final int SYS_evf_set                             = 544;
	public static final int SYS_evf_clear                           = 545;
	public static final int SYS_evf_cancel                          = 546;
	public static final int SYS_query_memory_protection             = 547;
	public static final int SYS_batch_map                           = 548;
	public static final int SYS_osem_create                         = 549;
	public static final int SYS_osem_delete                         = 550;
	public static final int SYS_osem_open                           = 551;
	public static final int SYS_osem_close                          = 552;
	public static final int SYS_osem_wait                           = 553;
	public static final int SYS_osem_trywait                        = 554;
	public static final int SYS_osem_post                           = 555;
	public static final int SYS_osem_cancel                         = 556;
	public static final int SYS_namedobj_create                     = 557;
	public static final int SYS_namedobj_delete                     = 558;
	public static final int SYS_set_vm_container                    = 559;
	public static final int SYS_debug_init                          = 560;
	public static final int SYS_suspend_process                     = 561;
	public static final int SYS_resume_process                      = 562;
	public static final int SYS_opmc_enable                         = 563;
	public static final int SYS_opmc_disable                        = 564;
	public static final int SYS_opmc_set_ctl                        = 565;
	public static final int SYS_opmc_set_ctr                        = 566;
	public static final int SYS_opmc_get_ctr                        = 567;
	public static final int SYS_budget_create                       = 568;
	public static final int SYS_budget_delete                       = 569;
	public static final int SYS_budget_get                          = 570;
	public static final int SYS_budget_set                          = 571;
	public static final int SYS_virtual_query                       = 572;
	public static final int SYS_mdbg_call                           = 573;
	public static final int SYS_sblock_create                       = 574;
	public static final int SYS_sblock_delete                       = 575;
	public static final int SYS_sblock_enter                        = 576;
	public static final int SYS_sblock_exit                         = 577;
	public static final int SYS_sblock_xenter                       = 578;
	public static final int SYS_sblock_xexit                        = 579;
	public static final int SYS_eport_create                        = 580;
	public static final int SYS_eport_delete                        = 581;
	public static final int SYS_eport_trigger                       = 582;
	public static final int SYS_eport_open                          = 583;
	public static final int SYS_eport_close                         = 584;
	public static final int SYS_is_in_sandbox                       = 585;
	public static final int SYS_dmem_container                      = 586;
	public static final int SYS_get_authinfo                        = 587;
	public static final int SYS_mname                               = 588;
	public static final int SYS_dynlib_dlopen                       = 589;
	public static final int SYS_dynlib_dlclose                      = 590;
	public static final int SYS_dynlib_dlsym                        = 591;
	public static final int SYS_dynlib_get_list                     = 592;
	public static final int SYS_dynlib_get_info                     = 593;
	public static final int SYS_dynlib_load_prx                     = 594;
	public static final int SYS_dynlib_unload_prx                   = 595;
	public static final int SYS_dynlib_do_copy_relocations          = 596;
	public static final int SYS_dynlib_prepare_dlclose              = 597;
	public static final int SYS_dynlib_get_proc_param               = 598;
	public static final int SYS_dynlib_process_needed_and_relocate  = 599;
	public static final int SYS_sandbox_path                        = 600;
	public static final int SYS_mdbg_service                        = 601;
	public static final int SYS_randomized_path                     = 602;
	public static final int SYS_rdup                                = 603;
	public static final int SYS_dl_get_metadata                     = 604;
	public static final int SYS_workaround8849                      = 605;
	public static final int SYS_is_development_mode                 = 606;
	public static final int SYS_get_self_auth_info                  = 607;
	public static final int SYS_dynlib_get_info_ex                  = 608;
	public static final int SYS_budget_getid                        = 609;
	public static final int SYS_budget_get_ptype                    = 610;
	public static final int SYS_get_paging_stats_of_all_threads     = 611;
	public static final int SYS_get_proc_type_info                  = 612;
	public static final int SYS_get_resident_count                  = 613;
	public static final int SYS_prepare_to_suspend_process          = 614;
	public static final int SYS_get_resident_fmem_count             = 615;
	public static final int SYS_thr_get_name                        = 616;
	public static final int SYS_set_gpo                             = 617;
	public static final int SYS_get_paging_stats_of_all_objects     = 618;
	public static final int SYS_test_debug_rwmem                    = 619;
	public static final int SYS_free_stack                          = 620;
	public static final int SYS_suspend_system                      = 621;
	public static final int SYS_ipmimgr_call                        = 622;
	public static final int SYS_get_gpo                             = 623;
	public static final int SYS_get_vm_map_timestamp                = 624;
	public static final int SYS_opmc_set_hw                         = 625;
	public static final int SYS_opmc_get_hw                         = 626;
	public static final int SYS_get_cpu_usage_all                   = 627;
	public static final int SYS_mmap_dmem                           = 628;
	public static final int SYS_physhm_open                         = 629;
	public static final int SYS_physhm_unlink                       = 630;
	public static final int SYS_resume_internal_hdd                 = 631;
	public static final int SYS_thr_suspend_ucontext                = 632;
	public static final int SYS_thr_resume_ucontext                 = 633;
	public static final int SYS_thr_get_ucontext                    = 634;
	public static final int SYS_thr_set_ucontext                    = 635;
	public static final int SYS_set_timezone_info                   = 636;
	public static final int SYS_set_phys_fmem_limit                 = 637;
	public static final int SYS_utc_to_localtime                    = 638;
	public static final int SYS_localtime_to_utc                    = 639;
	public static final int SYS_set_uevt                            = 640;
	public static final int SYS_get_cpu_usage_proc                  = 641;
	public static final int SYS_get_map_statistics                  = 642;
	public static final int SYS_set_chicken_switches                = 643;
	public static final int SYS_extend_page_table_pool              = 644;
	public static final int SYS_extend_page_table_pool2             = 645;
	public static final int SYS_get_kernel_mem_statistics           = 646;
	public static final int SYS_get_sdk_compiled_version            = 647;
	public static final int SYS_app_state_change                    = 648;
	public static final int SYS_dynlib_get_obj_member               = 649;
	public static final int SYS_budget_get_ptype_of_budget          = 650;
	public static final int SYS_prepare_to_resume_process           = 651;
	public static final int SYS_process_terminate                   = 652;
	public static final int SYS_blockpool_open                      = 653;
	public static final int SYS_blockpool_map                       = 654;
	public static final int SYS_blockpool_unmap                     = 655;
	public static final int SYS_dynlib_get_info_for_libdbg          = 656;
	public static final int SYS_blockpool_batch                     = 657;
	public static final int SYS_fdatasync                           = 658;
	public static final int SYS_dynlib_get_list2                    = 659;
	public static final int SYS_dynlib_get_info2                    = 660;
	public static final int SYS_aio_submit                          = 661;
	public static final int SYS_aio_multi_delete                    = 662;
	public static final int SYS_aio_multi_wait                      = 663;
	public static final int SYS_aio_multi_poll                      = 664;
	public static final int SYS_aio_get_data                        = 665;
	public static final int SYS_aio_multi_cancel                    = 666;
	public static final int SYS_get_bio_usage_all                   = 667;
	public static final int SYS_aio_create                          = 668;
	public static final int SYS_aio_submit_cmd                      = 669;
	public static final int SYS_aio_init                            = 670;
	public static final int SYS_get_page_table_stats                = 671;
	public static final int SYS_dynlib_get_list_for_libdbg          = 672;
	public static final int SYS_blockpool_move                      = 673;
	public static final int SYS_virtual_query_all                   = 674;
	public static final int SYS_reserve_2mb_page                    = 675;
	public static final int SYS_cpumode_yield                       = 676;
	public static final int SYS_get_phys_page_size                  = 677;

	public static final int SYS_MAXSYSCALL                          = 678;

	public static String getSyscallName(int sys) {
		switch (sys) {
			case SYS_syscall:                             return "SYS_syscall";
			case SYS_exit:                                return "SYS_exit";
			case SYS_fork:                                return "SYS_fork";
			case SYS_read:                                return "SYS_read";
			case SYS_write:                               return "SYS_write";
			case SYS_open:                                return "SYS_open";
			case SYS_close:                               return "SYS_close";
			case SYS_wait4:                               return "SYS_wait4";
			case SYS_link:                                return "SYS_link";
			case SYS_unlink:                              return "SYS_unlink";
			case SYS_chdir:                               return "SYS_chdir";
			case SYS_fchdir:                              return "SYS_fchdir";
			case SYS_mknod:                               return "SYS_mknod";
			case SYS_chmod:                               return "SYS_chmod";
			case SYS_chown:                               return "SYS_chown";
			case SYS_break:                               return "SYS_break";
			case SYS_freebsd4_getfsstat:                  return "SYS_freebsd4_getfsstat";
			case SYS_getpid:                              return "SYS_getpid";
			case SYS_mount:                               return "SYS_mount";
			case SYS_unmount:                             return "SYS_unmount";
			case SYS_setuid:                              return "SYS_setuid";
			case SYS_getuid:                              return "SYS_getuid";
			case SYS_geteuid:                             return "SYS_geteuid";
			case SYS_ptrace:                              return "SYS_ptrace";
			case SYS_recvmsg:                             return "SYS_recvmsg";
			case SYS_sendmsg:                             return "SYS_sendmsg";
			case SYS_recvfrom:                            return "SYS_recvfrom";
			case SYS_accept:                              return "SYS_accept";
			case SYS_getpeername:                         return "SYS_getpeername";
			case SYS_getsockname:                         return "SYS_getsockname";
			case SYS_access:                              return "SYS_access";
			case SYS_chflags:                             return "SYS_chflags";
			case SYS_fchflags:                            return "SYS_fchflags";
			case SYS_sync:                                return "SYS_sync";
			case SYS_kill:                                return "SYS_kill";
			case SYS_getppid:                             return "SYS_getppid";
			case SYS_dup:                                 return "SYS_dup";
			case SYS_pipe:                                return "SYS_pipe";
			case SYS_getegid:                             return "SYS_getegid";
			case SYS_profil:                              return "SYS_profil";
			case SYS_ktrace:                              return "SYS_ktrace";
			case SYS_getgid:                              return "SYS_getgid";
			case SYS_getlogin:                            return "SYS_getlogin";
			case SYS_setlogin:                            return "SYS_setlogin";
			case SYS_acct:                                return "SYS_acct";
			case SYS_sigaltstack:                         return "SYS_sigaltstack";
			case SYS_ioctl:                               return "SYS_ioctl";
			case SYS_reboot:                              return "SYS_reboot";
			case SYS_revoke:                              return "SYS_revoke";
			case SYS_symlink:                             return "SYS_symlink";
			case SYS_readlink:                            return "SYS_readlink";
			case SYS_execve:                              return "SYS_execve";
			case SYS_umask:                               return "SYS_umask";
			case SYS_chroot:                              return "SYS_chroot";
			case SYS_msync:                               return "SYS_msync";
			case SYS_vfork:                               return "SYS_vfork";
			case SYS_sbrk:                                return "SYS_sbrk";
			case SYS_sstk:                                return "SYS_sstk";
			case SYS_vadvise:                             return "SYS_vadvise";
			case SYS_munmap:                              return "SYS_munmap";
			case SYS_mprotect:                            return "SYS_mprotect";
			case SYS_madvise:                             return "SYS_madvise";
			case SYS_mincore:                             return "SYS_mincore";
			case SYS_getgroups:                           return "SYS_getgroups";
			case SYS_setgroups:                           return "SYS_setgroups";
			case SYS_getpgrp:                             return "SYS_getpgrp";
			case SYS_setpgid:                             return "SYS_setpgid";
			case SYS_setitimer:                           return "SYS_setitimer";
			case SYS_swapon:                              return "SYS_swapon";
			case SYS_getitimer:                           return "SYS_getitimer";
			case SYS_getdtablesize:                       return "SYS_getdtablesize";
			case SYS_dup2:                                return "SYS_dup2";
			case SYS_fcntl:                               return "SYS_fcntl";
			case SYS_select:                              return "SYS_select";
			case SYS_fsync:                               return "SYS_fsync";
			case SYS_setpriority:                         return "SYS_setpriority";
			case SYS_socket:                              return "SYS_socket";
			case SYS_connect:                             return "SYS_connect";
			case SYS_getpriority:                         return "SYS_getpriority";
			case SYS_bind:                                return "SYS_bind";
			case SYS_setsockopt:                          return "SYS_setsockopt";
			case SYS_listen:                              return "SYS_listen";
			case SYS_gettimeofday:                        return "SYS_gettimeofday";
			case SYS_getrusage:                           return "SYS_getrusage";
			case SYS_getsockopt:                          return "SYS_getsockopt";
			case SYS_readv:                               return "SYS_readv";
			case SYS_writev:                              return "SYS_writev";
			case SYS_settimeofday:                        return "SYS_settimeofday";
			case SYS_fchown:                              return "SYS_fchown";
			case SYS_fchmod:                              return "SYS_fchmod";
			case SYS_setreuid:                            return "SYS_setreuid";
			case SYS_setregid:                            return "SYS_setregid";
			case SYS_rename:                              return "SYS_rename";
			case SYS_flock:                               return "SYS_flock";
			case SYS_mkfifo:                              return "SYS_mkfifo";
			case SYS_sendto:                              return "SYS_sendto";
			case SYS_shutdown:                            return "SYS_shutdown";
			case SYS_socketpair:                          return "SYS_socketpair";
			case SYS_mkdir:                               return "SYS_mkdir";
			case SYS_rmdir:                               return "SYS_rmdir";
			case SYS_utimes:                              return "SYS_utimes";
			case SYS_adjtime:                             return "SYS_adjtime";
			case SYS_setsid:                              return "SYS_setsid";
			case SYS_quotactl:                            return "SYS_quotactl";
			case SYS_nlm_syscall:                         return "SYS_nlm_syscall";
			case SYS_nfssvc:                              return "SYS_nfssvc";
			case SYS_freebsd4_statfs:                     return "SYS_freebsd4_statfs";
			case SYS_freebsd4_fstatfs:                    return "SYS_freebsd4_fstatfs";
			case SYS_lgetfh:                              return "SYS_lgetfh";
			case SYS_getfh:                               return "SYS_getfh";
			case SYS_freebsd4_getdomainname:              return "SYS_freebsd4_getdomainname";
			case SYS_freebsd4_setdomainname:              return "SYS_freebsd4_setdomainname";
			case SYS_freebsd4_uname:                      return "SYS_freebsd4_uname";
			case SYS_sysarch:                             return "SYS_sysarch";
			case SYS_rtprio:                              return "SYS_rtprio";
			case SYS_semsys:                              return "SYS_semsys";
			case SYS_msgsys:                              return "SYS_msgsys";
			case SYS_shmsys:                              return "SYS_shmsys";
			case SYS_freebsd6_pread:                      return "SYS_freebsd6_pread";
			case SYS_freebsd6_pwrite:                     return "SYS_freebsd6_pwrite";
			case SYS_setfib:                              return "SYS_setfib";
			case SYS_ntp_adjtime:                         return "SYS_ntp_adjtime";
			case SYS_setgid:                              return "SYS_setgid";
			case SYS_setegid:                             return "SYS_setegid";
			case SYS_seteuid:                             return "SYS_seteuid";
			case SYS_stat:                                return "SYS_stat";
			case SYS_fstat:                               return "SYS_fstat";
			case SYS_lstat:                               return "SYS_lstat";
			case SYS_pathconf:                            return "SYS_pathconf";
			case SYS_fpathconf:                           return "SYS_fpathconf";
			case SYS_getrlimit:                           return "SYS_getrlimit";
			case SYS_setrlimit:                           return "SYS_setrlimit";
			case SYS_getdirentries:                       return "SYS_getdirentries";
			case SYS_freebsd6_mmap:                       return "SYS_freebsd6_mmap";
			case SYS___syscall:                           return "SYS___syscall";
			case SYS_freebsd6_lseek:                      return "SYS_freebsd6_lseek";
			case SYS_freebsd6_truncate:                   return "SYS_freebsd6_truncate";
			case SYS_freebsd6_ftruncate:                  return "SYS_freebsd6_ftruncate";
			case SYS___sysctl:                            return "SYS___sysctl";
			case SYS_mlock:                               return "SYS_mlock";
			case SYS_munlock:                             return "SYS_munlock";
			case SYS_undelete:                            return "SYS_undelete";
			case SYS_futimes:                             return "SYS_futimes";
			case SYS_getpgid:                             return "SYS_getpgid";
			case SYS_poll:                                return "SYS_poll";
			case SYS_freebsd7___semctl:                   return "SYS_freebsd7___semctl";
			case SYS_semget:                              return "SYS_semget";
			case SYS_semop:                               return "SYS_semop";
			case SYS_freebsd7_msgctl:                     return "SYS_freebsd7_msgctl";
			case SYS_msgget:                              return "SYS_msgget";
			case SYS_msgsnd:                              return "SYS_msgsnd";
			case SYS_msgrcv:                              return "SYS_msgrcv";
			case SYS_shmat:                               return "SYS_shmat";
			case SYS_freebsd7_shmctl:                     return "SYS_freebsd7_shmctl";
			case SYS_shmdt:                               return "SYS_shmdt";
			case SYS_shmget:                              return "SYS_shmget";
			case SYS_clock_gettime:                       return "SYS_clock_gettime";
			case SYS_clock_settime:                       return "SYS_clock_settime";
			case SYS_clock_getres:                        return "SYS_clock_getres";
			case SYS_ktimer_create:                       return "SYS_ktimer_create";
			case SYS_ktimer_delete:                       return "SYS_ktimer_delete";
			case SYS_ktimer_settime:                      return "SYS_ktimer_settime";
			case SYS_ktimer_gettime:                      return "SYS_ktimer_gettime";
			case SYS_ktimer_getoverrun:                   return "SYS_ktimer_getoverrun";
			case SYS_nanosleep:                           return "SYS_nanosleep";
			case SYS_ntp_gettime:                         return "SYS_ntp_gettime";
			case SYS_minherit:                            return "SYS_minherit";
			case SYS_rfork:                               return "SYS_rfork";
			case SYS_openbsd_poll:                        return "SYS_openbsd_poll";
			case SYS_issetugid:                           return "SYS_issetugid";
			case SYS_lchown:                              return "SYS_lchown";
			case SYS_aio_read:                            return "SYS_aio_read";
			case SYS_aio_write:                           return "SYS_aio_write";
			case SYS_lio_listio:                          return "SYS_lio_listio";
			case SYS_getdents:                            return "SYS_getdents";
			case SYS_lchmod:                              return "SYS_lchmod";
			case SYS_netbsd_lchown:                       return "SYS_netbsd_lchown";
			case SYS_lutimes:                             return "SYS_lutimes";
			case SYS_netbsd_msync:                        return "SYS_netbsd_msync";
			case SYS_nstat:                               return "SYS_nstat";
			case SYS_nfstat:                              return "SYS_nfstat";
			case SYS_nlstat:                              return "SYS_nlstat";
			case SYS_preadv:                              return "SYS_preadv";
			case SYS_pwritev:                             return "SYS_pwritev";
			case SYS_freebsd4_fhstatfs:                   return "SYS_freebsd4_fhstatfs";
			case SYS_fhopen:                              return "SYS_fhopen";
			case SYS_fhstat:                              return "SYS_fhstat";
			case SYS_modnext:                             return "SYS_modnext";
			case SYS_modstat:                             return "SYS_modstat";
			case SYS_modfnext:                            return "SYS_modfnext";
			case SYS_modfind:                             return "SYS_modfind";
			case SYS_kldload:                             return "SYS_kldload";
			case SYS_kldunload:                           return "SYS_kldunload";
			case SYS_kldfind:                             return "SYS_kldfind";
			case SYS_kldnext:                             return "SYS_kldnext";
			case SYS_kldstat:                             return "SYS_kldstat";
			case SYS_kldfirstmod:                         return "SYS_kldfirstmod";
			case SYS_getsid:                              return "SYS_getsid";
			case SYS_setresuid:                           return "SYS_setresuid";
			case SYS_setresgid:                           return "SYS_setresgid";
			case SYS_aio_return:                          return "SYS_aio_return";
			case SYS_aio_suspend:                         return "SYS_aio_suspend";
			case SYS_aio_cancel:                          return "SYS_aio_cancel";
			case SYS_aio_error:                           return "SYS_aio_error";
			case SYS_oaio_read:                           return "SYS_oaio_read";
			case SYS_oaio_write:                          return "SYS_oaio_write";
			case SYS_olio_listio:                         return "SYS_olio_listio";
			case SYS_yield:                               return "SYS_yield";
			case SYS_mlockall:                            return "SYS_mlockall";
			case SYS_munlockall:                          return "SYS_munlockall";
			case SYS___getcwd:                            return "SYS___getcwd";
			case SYS_sched_setparam:                      return "SYS_sched_setparam";
			case SYS_sched_getparam:                      return "SYS_sched_getparam";
			case SYS_sched_setscheduler:                  return "SYS_sched_setscheduler";
			case SYS_sched_getscheduler:                  return "SYS_sched_getscheduler";
			case SYS_sched_yield:                         return "SYS_sched_yield";
			case SYS_sched_get_priority_max:              return "SYS_sched_get_priority_max";
			case SYS_sched_get_priority_min:              return "SYS_sched_get_priority_min";
			case SYS_sched_rr_get_interval:               return "SYS_sched_rr_get_interval";
			case SYS_utrace:                              return "SYS_utrace";
			case SYS_freebsd4_sendfile:                   return "SYS_freebsd4_sendfile";
			case SYS_kldsym:                              return "SYS_kldsym";
			case SYS_jail:                                return "SYS_jail";
			case SYS_nnpfs_syscall:                       return "SYS_nnpfs_syscall";
			case SYS_sigprocmask:                         return "SYS_sigprocmask";
			case SYS_sigsuspend:                          return "SYS_sigsuspend";
			case SYS_freebsd4_sigaction:                  return "SYS_freebsd4_sigaction";
			case SYS_sigpending:                          return "SYS_sigpending";
			case SYS_freebsd4_sigreturn:                  return "SYS_freebsd4_sigreturn";
			case SYS_sigtimedwait:                        return "SYS_sigtimedwait";
			case SYS_sigwaitinfo:                         return "SYS_sigwaitinfo";
			case SYS___acl_get_file:                      return "SYS___acl_get_file";
			case SYS___acl_set_file:                      return "SYS___acl_set_file";
			case SYS___acl_get_fd:                        return "SYS___acl_get_fd";
			case SYS___acl_set_fd:                        return "SYS___acl_set_fd";
			case SYS___acl_delete_file:                   return "SYS___acl_delete_file";
			case SYS___acl_delete_fd:                     return "SYS___acl_delete_fd";
			case SYS___acl_aclcheck_file:                 return "SYS___acl_aclcheck_file";
			case SYS___acl_aclcheck_fd:                   return "SYS___acl_aclcheck_fd";
			case SYS_extattrctl:                          return "SYS_extattrctl";
			case SYS_extattr_set_file:                    return "SYS_extattr_set_file";
			case SYS_extattr_get_file:                    return "SYS_extattr_get_file";
			case SYS_extattr_delete_file:                 return "SYS_extattr_delete_file";
			case SYS_aio_waitcomplete:                    return "SYS_aio_waitcomplete";
			case SYS_getresuid:                           return "SYS_getresuid";
			case SYS_getresgid:                           return "SYS_getresgid";
			case SYS_kqueue:                              return "SYS_kqueue";
			case SYS_kevent:                              return "SYS_kevent";
			case SYS_extattr_set_fd:                      return "SYS_extattr_set_fd";
			case SYS_extattr_get_fd:                      return "SYS_extattr_get_fd";
			case SYS_extattr_delete_fd:                   return "SYS_extattr_delete_fd";
			case SYS___setugid:                           return "SYS___setugid";
			case SYS_eaccess:                             return "SYS_eaccess";
			case SYS_afs3_syscall:                        return "SYS_afs3_syscall";
			case SYS_nmount:                              return "SYS_nmount";
			case SYS___mac_get_proc:                      return "SYS___mac_get_proc";
			case SYS___mac_set_proc:                      return "SYS___mac_set_proc";
			case SYS___mac_get_fd:                        return "SYS___mac_get_fd";
			case SYS___mac_get_file:                      return "SYS___mac_get_file";
			case SYS___mac_set_fd:                        return "SYS___mac_set_fd";
			case SYS___mac_set_file:                      return "SYS___mac_set_file";
			case SYS_kenv:                                return "SYS_kenv";
			case SYS_lchflags:                            return "SYS_lchflags";
			case SYS_uuidgen:                             return "SYS_uuidgen";
			case SYS_sendfile:                            return "SYS_sendfile";
			case SYS_mac_syscall:                         return "SYS_mac_syscall";
			case SYS_getfsstat:                           return "SYS_getfsstat";
			case SYS_statfs:                              return "SYS_statfs";
			case SYS_fstatfs:                             return "SYS_fstatfs";
			case SYS_fhstatfs:                            return "SYS_fhstatfs";
			case SYS_ksem_close:                          return "SYS_ksem_close";
			case SYS_ksem_post:                           return "SYS_ksem_post";
			case SYS_ksem_wait:                           return "SYS_ksem_wait";
			case SYS_ksem_trywait:                        return "SYS_ksem_trywait";
			case SYS_ksem_init:                           return "SYS_ksem_init";
			case SYS_ksem_open:                           return "SYS_ksem_open";
			case SYS_ksem_unlink:                         return "SYS_ksem_unlink";
			case SYS_ksem_getvalue:                       return "SYS_ksem_getvalue";
			case SYS_ksem_destroy:                        return "SYS_ksem_destroy";
			case SYS___mac_get_pid:                       return "SYS___mac_get_pid";
			case SYS___mac_get_link:                      return "SYS___mac_get_link";
			case SYS___mac_set_link:                      return "SYS___mac_set_link";
			case SYS_extattr_set_link:                    return "SYS_extattr_set_link";
			case SYS_extattr_get_link:                    return "SYS_extattr_get_link";
			case SYS_extattr_delete_link:                 return "SYS_extattr_delete_link";
			case SYS___mac_execve:                        return "SYS___mac_execve";
			case SYS_sigaction:                           return "SYS_sigaction";
			case SYS_sigreturn:                           return "SYS_sigreturn";
			case SYS_getcontext:                          return "SYS_getcontext";
			case SYS_setcontext:                          return "SYS_setcontext";
			case SYS_swapcontext:                         return "SYS_swapcontext";
			case SYS_swapoff:                             return "SYS_swapoff";
			case SYS___acl_get_link:                      return "SYS___acl_get_link";
			case SYS___acl_set_link:                      return "SYS___acl_set_link";
			case SYS___acl_delete_link:                   return "SYS___acl_delete_link";
			case SYS___acl_aclcheck_link:                 return "SYS___acl_aclcheck_link";
			case SYS_sigwait:                             return "SYS_sigwait";
			case SYS_thr_create:                          return "SYS_thr_create";
			case SYS_thr_exit:                            return "SYS_thr_exit";
			case SYS_thr_self:                            return "SYS_thr_self";
			case SYS_thr_kill:                            return "SYS_thr_kill";
			case SYS__umtx_lock:                          return "SYS__umtx_lock";
			case SYS__umtx_unlock:                        return "SYS__umtx_unlock";
			case SYS_jail_attach:                         return "SYS_jail_attach";
			case SYS_extattr_list_fd:                     return "SYS_extattr_list_fd";
			case SYS_extattr_list_file:                   return "SYS_extattr_list_file";
			case SYS_extattr_list_link:                   return "SYS_extattr_list_link";
			case SYS_ksem_timedwait:                      return "SYS_ksem_timedwait";
			case SYS_thr_suspend:                         return "SYS_thr_suspend";
			case SYS_thr_wake:                            return "SYS_thr_wake";
			case SYS_kldunloadf:                          return "SYS_kldunloadf";
			case SYS_audit:                               return "SYS_audit";
			case SYS_auditon:                             return "SYS_auditon";
			case SYS_getauid:                             return "SYS_getauid";
			case SYS_setauid:                             return "SYS_setauid";
			case SYS_getaudit:                            return "SYS_getaudit";
			case SYS_setaudit:                            return "SYS_setaudit";
			case SYS_getaudit_addr:                       return "SYS_getaudit_addr";
			case SYS_setaudit_addr:                       return "SYS_setaudit_addr";
			case SYS_auditctl:                            return "SYS_auditctl";
			case SYS__umtx_op:                            return "SYS__umtx_op";
			case SYS_thr_new:                             return "SYS_thr_new";
			case SYS_sigqueue:                            return "SYS_sigqueue";
			case SYS_kmq_open:                            return "SYS_kmq_open";
			case SYS_kmq_setattr:                         return "SYS_kmq_setattr";
			case SYS_kmq_timedreceive:                    return "SYS_kmq_timedreceive";
			case SYS_kmq_timedsend:                       return "SYS_kmq_timedsend";
			case SYS_kmq_notify:                          return "SYS_kmq_notify";
			case SYS_kmq_unlink:                          return "SYS_kmq_unlink";
			case SYS_abort2:                              return "SYS_abort2";
			case SYS_thr_set_name:                        return "SYS_thr_set_name";
			case SYS_aio_fsync:                           return "SYS_aio_fsync";
			case SYS_rtprio_thread:                       return "SYS_rtprio_thread";
			case SYS_sctp_peeloff:                        return "SYS_sctp_peeloff";
			case SYS_sctp_generic_sendmsg:                return "SYS_sctp_generic_sendmsg";
			case SYS_sctp_generic_sendmsg_iov:            return "SYS_sctp_generic_sendmsg_iov";
			case SYS_sctp_generic_recvmsg:                return "SYS_sctp_generic_recvmsg";
			case SYS_pread:                               return "SYS_pread";
			case SYS_pwrite:                              return "SYS_pwrite";
			case SYS_mmap:                                return "SYS_mmap";
			case SYS_lseek:                               return "SYS_lseek";
			case SYS_truncate:                            return "SYS_truncate";
			case SYS_ftruncate:                           return "SYS_ftruncate";
			case SYS_thr_kill2:                           return "SYS_thr_kill2";
			case SYS_shm_open:                            return "SYS_shm_open";
			case SYS_shm_unlink:                          return "SYS_shm_unlink";
			case SYS_cpuset:                              return "SYS_cpuset";
			case SYS_cpuset_setid:                        return "SYS_cpuset_setid";
			case SYS_cpuset_getid:                        return "SYS_cpuset_getid";
			case SYS_cpuset_getaffinity:                  return "SYS_cpuset_getaffinity";
			case SYS_cpuset_setaffinity:                  return "SYS_cpuset_setaffinity";
			case SYS_faccessat:                           return "SYS_faccessat";
			case SYS_fchmodat:                            return "SYS_fchmodat";
			case SYS_fchownat:                            return "SYS_fchownat";
			case SYS_fexecve:                             return "SYS_fexecve";
			case SYS_fstatat:                             return "SYS_fstatat";
			case SYS_futimesat:                           return "SYS_futimesat";
			case SYS_linkat:                              return "SYS_linkat";
			case SYS_mkdirat:                             return "SYS_mkdirat";
			case SYS_mkfifoat:                            return "SYS_mkfifoat";
			case SYS_mknodat:                             return "SYS_mknodat";
			case SYS_openat:                              return "SYS_openat";
			case SYS_readlinkat:                          return "SYS_readlinkat";
			case SYS_renameat:                            return "SYS_renameat";
			case SYS_symlinkat:                           return "SYS_symlinkat";
			case SYS_unlinkat:                            return "SYS_unlinkat";
			case SYS_posix_openpt:                        return "SYS_posix_openpt";
			case SYS_gssd_syscall:                        return "SYS_gssd_syscall";
			case SYS_jail_get:                            return "SYS_jail_get";
			case SYS_jail_set:                            return "SYS_jail_set";
			case SYS_jail_remove:                         return "SYS_jail_remove";
			case SYS_closefrom:                           return "SYS_closefrom";
			case SYS___semctl:                            return "SYS___semctl";
			case SYS_msgctl:                              return "SYS_msgctl";
			case SYS_shmctl:                              return "SYS_shmctl";
			case SYS_lpathconf:                           return "SYS_lpathconf";
			case SYS_cap_new:                             return "SYS_cap_new";
			case SYS_cap_getrights:                       return "SYS_cap_getrights";
			case SYS_cap_enter:                           return "SYS_cap_enter";
			case SYS_cap_getmode:                         return "SYS_cap_getmode";
			case SYS_pdfork:                              return "SYS_pdfork";
			case SYS_pdkill:                              return "SYS_pdkill";
			case SYS_pdgetpid:                            return "SYS_pdgetpid";
			case SYS_pselect:                             return "SYS_pselect";
			case SYS_getloginclass:                       return "SYS_getloginclass";
			case SYS_setloginclass:                       return "SYS_setloginclass";
			case SYS_rctl_get_racct:                      return "SYS_rctl_get_racct";
			case SYS_rctl_get_rules:                      return "SYS_rctl_get_rules";
			case SYS_rctl_get_limits:                     return "SYS_rctl_get_limits";
			case SYS_rctl_add_rule:                       return "SYS_rctl_add_rule";
			case SYS_rctl_remove_rule:                    return "SYS_rctl_remove_rule";
			case SYS_posix_fallocate:                     return "SYS_posix_fallocate";
			case SYS_posix_fadvise:                       return "SYS_posix_fadvise";
			case SYS_netcontrol:                          return "SYS_netcontrol";
			case SYS_netabort:                            return "SYS_netabort";
			case SYS_netgetsockinfo:                      return "SYS_netgetsockinfo";
			case SYS_socketex:                            return "SYS_socketex";
			case SYS_socketclose:                         return "SYS_socketclose";
			case SYS_netgetiflist:                        return "SYS_netgetiflist";
			case SYS_kqueueex:                            return "SYS_kqueueex";
			case SYS_mtypeprotect:                        return "SYS_mtypeprotect";
			case SYS_regmgr_call:                         return "SYS_regmgr_call";
			case SYS_jitshm_create:                       return "SYS_jitshm_create";
			case SYS_jitshm_alias:                        return "SYS_jitshm_alias";
			case SYS_dl_get_list:                         return "SYS_dl_get_list";
			case SYS_dl_get_info:                         return "SYS_dl_get_info";
			case SYS_dl_notify_event:                     return "SYS_dl_notify_event";
			case SYS_evf_create:                          return "SYS_evf_create";
			case SYS_evf_delete:                          return "SYS_evf_delete";
			case SYS_evf_open:                            return "SYS_evf_open";
			case SYS_evf_close:                           return "SYS_evf_close";
			case SYS_evf_wait:                            return "SYS_evf_wait";
			case SYS_evf_trywait:                         return "SYS_evf_trywait";
			case SYS_evf_set:                             return "SYS_evf_set";
			case SYS_evf_clear:                           return "SYS_evf_clear";
			case SYS_evf_cancel:                          return "SYS_evf_cancel";
			case SYS_query_memory_protection:             return "SYS_query_memory_protection";
			case SYS_batch_map:                           return "SYS_batch_map";
			case SYS_osem_create:                         return "SYS_osem_create";
			case SYS_osem_delete:                         return "SYS_osem_delete";
			case SYS_osem_open:                           return "SYS_osem_open";
			case SYS_osem_close:                          return "SYS_osem_close";
			case SYS_osem_wait:                           return "SYS_osem_wait";
			case SYS_osem_trywait:                        return "SYS_osem_trywait";
			case SYS_osem_post:                           return "SYS_osem_post";
			case SYS_osem_cancel:                         return "SYS_osem_cancel";
			case SYS_namedobj_create:                     return "SYS_namedobj_create";
			case SYS_namedobj_delete:                     return "SYS_namedobj_delete";
			case SYS_set_vm_container:                    return "SYS_set_vm_container";
			case SYS_debug_init:                          return "SYS_debug_init";
			case SYS_suspend_process:                     return "SYS_suspend_process";
			case SYS_resume_process:                      return "SYS_resume_process";
			case SYS_opmc_enable:                         return "SYS_opmc_enable";
			case SYS_opmc_disable:                        return "SYS_opmc_disable";
			case SYS_opmc_set_ctl:                        return "SYS_opmc_set_ctl";
			case SYS_opmc_set_ctr:                        return "SYS_opmc_set_ctr";
			case SYS_opmc_get_ctr:                        return "SYS_opmc_get_ctr";
			case SYS_budget_create:                       return "SYS_budget_create";
			case SYS_budget_delete:                       return "SYS_budget_delete";
			case SYS_budget_get:                          return "SYS_budget_get";
			case SYS_budget_set:                          return "SYS_budget_set";
			case SYS_virtual_query:                       return "SYS_virtual_query";
			case SYS_mdbg_call:                           return "SYS_mdbg_call";
			case SYS_sblock_create:                       return "SYS_sblock_create";
			case SYS_sblock_delete:                       return "SYS_sblock_delete";
			case SYS_sblock_enter:                        return "SYS_sblock_enter";
			case SYS_sblock_exit:                         return "SYS_sblock_exit";
			case SYS_sblock_xenter:                       return "SYS_sblock_xenter";
			case SYS_sblock_xexit:                        return "SYS_sblock_xexit";
			case SYS_eport_create:                        return "SYS_eport_create";
			case SYS_eport_delete:                        return "SYS_eport_delete";
			case SYS_eport_trigger:                       return "SYS_eport_trigger";
			case SYS_eport_open:                          return "SYS_eport_open";
			case SYS_eport_close:                         return "SYS_eport_close";
			case SYS_is_in_sandbox:                       return "SYS_is_in_sandbox";
			case SYS_dmem_container:                      return "SYS_dmem_container";
			case SYS_get_authinfo:                        return "SYS_get_authinfo";
			case SYS_mname:                               return "SYS_mname";
			case SYS_dynlib_dlopen:                       return "SYS_dynlib_dlopen";
			case SYS_dynlib_dlclose:                      return "SYS_dynlib_dlclose";
			case SYS_dynlib_dlsym:                        return "SYS_dynlib_dlsym";
			case SYS_dynlib_get_list:                     return "SYS_dynlib_get_list";
			case SYS_dynlib_get_info:                     return "SYS_dynlib_get_info";
			case SYS_dynlib_load_prx:                     return "SYS_dynlib_load_prx";
			case SYS_dynlib_unload_prx:                   return "SYS_dynlib_unload_prx";
			case SYS_dynlib_do_copy_relocations:          return "SYS_dynlib_do_copy_relocations";
			case SYS_dynlib_prepare_dlclose:              return "SYS_dynlib_prepare_dlclose";
			case SYS_dynlib_get_proc_param:               return "SYS_dynlib_get_proc_param";
			case SYS_dynlib_process_needed_and_relocate:  return "SYS_dynlib_process_needed_and_relocat";
			case SYS_sandbox_path:                        return "SYS_sandbox_path";
			case SYS_mdbg_service:                        return "SYS_mdbg_service";
			case SYS_randomized_path:                     return "SYS_randomized_path";
			case SYS_rdup:                                return "SYS_rdup";
			case SYS_dl_get_metadata:                     return "SYS_dl_get_metadata";
			case SYS_workaround8849:                      return "SYS_workaround8849";
			case SYS_is_development_mode:                 return "SYS_is_development_mode";
			case SYS_get_self_auth_info:                  return "SYS_get_self_auth_info";
			case SYS_dynlib_get_info_ex:                  return "SYS_dynlib_get_info_ex";
			case SYS_budget_getid:                        return "SYS_budget_getid";
			case SYS_budget_get_ptype:                    return "SYS_budget_get_ptype";
			case SYS_get_paging_stats_of_all_threads:     return "SYS_get_paging_stats_of_all_threads";
			case SYS_get_proc_type_info:                  return "SYS_get_proc_type_info";
			case SYS_get_resident_count:                  return "SYS_get_resident_count";
			case SYS_prepare_to_suspend_process:          return "SYS_prepare_to_suspend_process";
			case SYS_get_resident_fmem_count:             return "SYS_get_resident_fmem_count";
			case SYS_thr_get_name:                        return "SYS_thr_get_name";
			case SYS_set_gpo:                             return "SYS_set_gpo";
			case SYS_get_paging_stats_of_all_objects:     return "SYS_get_paging_stats_of_all_objects";
			case SYS_test_debug_rwmem:                    return "SYS_test_debug_rwmem";
			case SYS_free_stack:                          return "SYS_free_stack";
			case SYS_suspend_system:                      return "SYS_suspend_system";
			case SYS_ipmimgr_call:                        return "SYS_ipmimgr_call";
			case SYS_get_gpo:                             return "SYS_get_gpo";
			case SYS_get_vm_map_timestamp:                return "SYS_get_vm_map_timestamp";
			case SYS_opmc_set_hw:                         return "SYS_opmc_set_hw";
			case SYS_opmc_get_hw:                         return "SYS_opmc_get_hw";
			case SYS_get_cpu_usage_all:                   return "SYS_get_cpu_usage_all";
			case SYS_mmap_dmem:                           return "SYS_mmap_dmem";
			case SYS_physhm_open:                         return "SYS_physhm_open";
			case SYS_physhm_unlink:                       return "SYS_physhm_unlink";
			case SYS_resume_internal_hdd:                 return "SYS_resume_internal_hdd";
			case SYS_thr_suspend_ucontext:                return "SYS_thr_suspend_ucontext";
			case SYS_thr_resume_ucontext:                 return "SYS_thr_resume_ucontext";
			case SYS_thr_get_ucontext:                    return "SYS_thr_get_ucontext";
			case SYS_thr_set_ucontext:                    return "SYS_thr_set_ucontext";
			case SYS_set_timezone_info:                   return "SYS_set_timezone_info";
			case SYS_set_phys_fmem_limit:                 return "SYS_set_phys_fmem_limit";
			case SYS_utc_to_localtime:                    return "SYS_utc_to_localtime";
			case SYS_localtime_to_utc:                    return "SYS_localtime_to_utc";
			case SYS_set_uevt:                            return "SYS_set_uevt";
			case SYS_get_cpu_usage_proc:                  return "SYS_get_cpu_usage_proc";
			case SYS_get_map_statistics:                  return "SYS_get_map_statistics";
			case SYS_set_chicken_switches:                return "SYS_set_chicken_switches";
			case SYS_extend_page_table_pool:              return "SYS_extend_page_table_pool";
			case SYS_extend_page_table_pool2:             return "SYS_extend_page_table_pool2";
			case SYS_get_kernel_mem_statistics:           return "SYS_get_kernel_mem_statistics";
			case SYS_get_sdk_compiled_version:            return "SYS_get_sdk_compiled_version";
			case SYS_app_state_change:                    return "SYS_app_state_change";
			case SYS_dynlib_get_obj_member:               return "SYS_dynlib_get_obj_member";
			case SYS_budget_get_ptype_of_budget:          return "SYS_budget_get_ptype_of_budget";
			case SYS_prepare_to_resume_process:           return "SYS_prepare_to_resume_process";
			case SYS_process_terminate:                   return "SYS_process_terminate";
			case SYS_blockpool_open:                      return "SYS_blockpool_open";
			case SYS_blockpool_map:                       return "SYS_blockpool_map";
			case SYS_blockpool_unmap:                     return "SYS_blockpool_unmap";
			case SYS_dynlib_get_info_for_libdbg:          return "SYS_dynlib_get_info_for_libdbg";
			case SYS_blockpool_batch:                     return "SYS_blockpool_batch";
			case SYS_fdatasync:                           return "SYS_fdatasync";
			case SYS_dynlib_get_list2:                    return "SYS_dynlib_get_list2";
			case SYS_dynlib_get_info2:                    return "SYS_dynlib_get_info2";
			case SYS_aio_submit:                          return "SYS_aio_submit";
			case SYS_aio_multi_delete:                    return "SYS_aio_multi_delete";
			case SYS_aio_multi_wait:                      return "SYS_aio_multi_wait";
			case SYS_aio_multi_poll:                      return "SYS_aio_multi_poll";
			case SYS_aio_get_data:                        return "SYS_aio_get_data";
			case SYS_aio_multi_cancel:                    return "SYS_aio_multi_cancel";
			case SYS_get_bio_usage_all:                   return "SYS_get_bio_usage_all";
			case SYS_aio_create:                          return "SYS_aio_create";
			case SYS_aio_submit_cmd:                      return "SYS_aio_submit_cmd";
			case SYS_aio_init:                            return "SYS_aio_init";
			case SYS_get_page_table_stats:                return "SYS_get_page_table_stats";
			case SYS_dynlib_get_list_for_libdbg:          return "SYS_dynlib_get_list_for_libdbg";
			case SYS_blockpool_move:                      return "SYS_blockpool_move";
			case SYS_virtual_query_all:                   return "SYS_virtual_query_all";
			case SYS_reserve_2mb_page:                    return "SYS_reserve_2mb_page";
			case SYS_cpumode_yield:                       return "SYS_cpumode_yield";
			case SYS_get_phys_page_size:                  return "SYS_get_phys_page_size";
			default:                                      return Integer.toString(sys);
		}
	}

	/***********************************************************************************************
	 * Syscall wrapper retrieval & calling
	 **********************************************************************************************/

	private long[] mSyscallWrappers = new long[SYS_MAXSYSCALL];

	private long findSyscallWrapper(int sys) {
		// FIXME: Number taken from Lapse; is this always the size of libkernel?
		final long LIBKERNEL_SIZE = 0x40000;
		byte[] pattern = {
			(byte)0x48, (byte)0xc7, (byte)0xc0,
			(byte)((sys>>0)&0xFF), (byte)((sys>>8)&0xFF), (byte)((sys>>16)&0xFF), (byte)((sys>>24)&0xFF),
			(byte)0x49, (byte)0x89, (byte)0xca, (byte)0x0f, (byte)0x05
		};
		long wrapper = 0;
		for (long p = mLibKernelBase; p < mLibKernelBase + LIBKERNEL_SIZE - pattern.length; p++) {
			boolean found = true;
			for (int i = 0; i < pattern.length; i++) {
				if (api.read8(p + i) != pattern[i]) {
					found = false;
					break;
				}
			}
			if (found) {
				wrapper = p;
				break;
			}
		}
		String name = LibKernel.getSyscallName(sys);
		if (wrapper != 0) {
			mSyscallWrappers[sys] = wrapper;
			// String addrx = Long.toHexString(wrapper);
			// console.add("libkernel: wrapper for " + name + " (" + sys + ") at 0x" + addrx);
		} else {
			console.add("libkernel: no wrapper for " + name + " (" + sys + ")");
		}
		return wrapper;
	}

	/** Find a syscall wrapper inside libkernel for the given syscall number, 0 if none found */
	public long get(int sys) {
		long wrapper = mSyscallWrappers[sys];
		if (wrapper == 0) {
			wrapper = findSyscallWrapper(sys);
		}
		return wrapper;
	}

	/** Invoke a syscall with no arguments */
	public long syscall(int sys) {
		long wrapper = get(sys);
		return wrapper == 0 ? -1 : api.call(wrapper);
	}

	/** Invoke a syscall with one argument */
	public long syscall(int sys, long arg0) {
		long wrapper = get(sys);
		return wrapper == 0 ? -1 : api.call(wrapper, arg0);
	}

	/** Invoke a syscall with 2 arguments */
	public long syscall(int sys, long arg0, long arg1) {
		long wrapper = get(sys);
		return wrapper == 0 ? -1 : api.call(wrapper, arg0, arg1);
	}

	/** Invoke a syscall with 3 arguments */
	public long syscall(int sys, long arg0, long arg1, long arg2) {
		long wrapper = get(sys);
		return wrapper == 0 ? -1 : api.call(wrapper, arg0, arg1, arg2);
	}

	/** Invoke a syscall with 4 arguments */
	public long syscall(int sys, long arg0, long arg1, long arg2, long arg3) {
		long wrapper = get(sys);
		return wrapper == 0 ? -1 : api.call(wrapper, arg0, arg1, arg2, arg3);
	}

	/** Invoke a syscall with 5 arguments */
	public long syscall(int sys, long arg0, long arg1, long arg2, long arg3, long arg4) {
		long wrapper = get(sys);
		return wrapper == 0 ? -1 : api.call(wrapper, arg0, arg1, arg2, arg3, arg4);
	}

	/** Invoke a syscall with 6 arguments */
	public long syscall(int sys, long arg0, long arg1, long arg2, long arg3, long arg4, long arg5) {
		long wrapper = get(sys);
		return wrapper == 0 ? -1 : api.call(wrapper, arg0, arg1, arg2, arg3, arg4, arg5);
	}

	/***********************************************************************************************
	 * Dynamic library symbols
	 **********************************************************************************************/

	private HashMap symbolToObject = new HashMap();

	/** Retrieve the address corresponding to a symbol inside libkernel */
	public long get(String symbol) {
		Long val = (Long)symbolToObject.get(symbol);
		if (val != null) {
			return val.longValue();
		}
		long addr = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, symbol);
		if (addr == 0) {
			console.add("libkernel: couldn't find address for " + symbol);
		} else {
			console.add("libkernel: " + symbol + " is 0x" + Long.toHexString(addr));
		}
		symbolToObject.put(symbol, new Long(addr));
		return addr;
	}

	// FIXME: Basic stuff like errno should have helpers

	/***********************************************************************************************
	 * Field declaration helper classes
	 * https://github.com/xvortex/ps4-payload-sdk/blob/master/libPS4/include/types.h - for sizes
	 **********************************************************************************************/

	 public static class Field {
		public final int size;
		public final int offset;
		public final int next;

		public Field(int size, int offset) {
			this.size = size;
			this.offset = offset;
			this.next = offset + size;
		}

		public byte  read8 (long objaddr) { return api.read8 (objaddr + offset); }
		public short read16(long objaddr) { return api.read16(objaddr + offset); }
		public int   read32(long objaddr) { return api.read32(objaddr + offset); }
		public long  read64(long objaddr) { return api.read64(objaddr + offset); }

		public void write8 (long objaddr, byte  x) { api.write8 (objaddr + offset, x); }
		public void write16(long objaddr, short x) { api.write16(objaddr + offset, x); }
		public void write32(long objaddr, int   x) { api.write32(objaddr + offset, x); }
		public void write64(long objaddr, long  x) { api.write64(objaddr + offset, x); }
	}

	public static class FieldInt32 extends Field {
		public FieldInt32(int offset) { super(4, offset); }
		public int  get(long objaddr) { return read32(objaddr); }
		public int  get(Buffer buf)   { return read32(buf.address()); }
		public void set(long objaddr, int x) { write32(objaddr, x); }
		public void set(Buffer buf,   int x) { write32(buf.address(), x); }
	}

	public static class FieldInt64 extends Field {
		public FieldInt64(int offset) { super(8, offset); }
		public long get(long objaddr) { return read64(objaddr); }
		public long get(Buffer buf)   { return read64(buf.address()); }
		public void set(long objaddr, long x) { write64(objaddr, x); }
		public void set(Buffer buf,   long x) { write64(buf.address(), x); }
	}

	public static class FieldSizeT extends FieldInt64 {
		public FieldSizeT(int offset) { super(offset); }
	}

	public static class FieldOffT extends FieldInt64 {
		public FieldOffT(int offset) { super(offset); }
	}

	public static class FieldPtr extends FieldInt64 {
		public FieldPtr(int offset) { super(offset); }
	}

	/***********************************************************************************************
	 * sceKernelGetModuleInfoFromAddr
	 **********************************************************************************************/

	/** Wrapper for data returned by sceKernelGetModuleInfoFromAddr */
	public class KernelModuleInfo {
		// https://github.com/shadps4-emu/shadPS4/blob/0bdd21b/src/core/module.h#L32
		public static final long OFFSET_INIT_PROC_ADDR = 0x128;
		public static final long OFFSET_FIRST_SEGMENT = 0x160;
		public long initProcAddr;
		public long firstSegment;
	}

	/** Returns information about a loaded executable module, given an address that is inside it */
	public KernelModuleInfo sceKernelGetModuleInfoFromAddr(long addr) {
		String addrx = Long.toHexString(addr);
		Buffer kmiBuf = new Buffer(0x300);
		long ret = api.call(get("sceKernelGetModuleInfoFromAddr"), addr, 1, kmiBuf.address());
		if (ret != 0) {
			console.add("sceKernelGetModuleInfoFromAddr(0x" + addrx + ") failed: " + ret);
			return null;
		}
		KernelModuleInfo kmi = new KernelModuleInfo();
		kmi.initProcAddr = api.read64(kmiBuf.address() + KernelModuleInfo.OFFSET_INIT_PROC_ADDR);
		kmi.firstSegment = api.read64(kmiBuf.address() + KernelModuleInfo.OFFSET_FIRST_SEGMENT);
		console.add("sceKernelGetModuleInfoFromAddr(0x" + addrx + ") => initProcAddr 0x" + kmi.initProcAddr);
		console.add("sceKernelGetModuleInfoFromAddr(0x" + addrx + ") => firstSegment 0x" + kmi.firstSegment);
		return kmi;
	}

	/** Returns information about libkernel itself */
	public KernelModuleInfo sceKernelGetModuleInfoFromAddr() {
		long addr = get("sceKernelGetModuleInfoFromAddr");
		return sceKernelGetModuleInfoFromAddr(addr);
	}

	/***********************************************************************************************
	 * SceAIO subsystem calls
	 * https://www.psdevwiki.com/ps4/Vulnerabilities - PoC for PS4 5.00-12.02 ... by abc
	 **********************************************************************************************/

	public static final int AIO_CMD_READ  = 0x0001;
	public static final int AIO_CMD_WRITE = 0x0002;
	public static final int AIO_CMD_MASK  = 0x0fff;
	public static final int AIO_CMD_MULTI = 0x1000;

	public static final int AIO_PRIORITY_LOW  = 1;
	public static final int AIO_PRIORITY_MID  = 2;
	public static final int AIO_PRIORITY_HIGH = 3;

	public static final int AIO_STATE_COMPLETED = 2;
	public static final int AIO_STATE_ABORTED   = 3;

	// Max number of requests that can be created/polled/canceled/deleted/waited
	public static final int MAX_AIO_IDS = 128;

	// The various SceAIO syscalls that copies out errors/states will not check if the address is
	// NULL and will return EFAULT. this dummy buffer will serve as the default argument so users
	// don't need to specify one
	public Buffer AIO_ERRORS = new Buffer(4 * MAX_AIO_IDS);

	public static class AioResult extends Buffer {
		public static final FieldInt64 retval = new FieldInt64(0);
		public static final FieldInt32 state  = new FieldInt32(retval.next);
		public static final FieldInt32 pad    = new FieldInt32(state.next);
		public static final int SIZE = pad.next;
		public AioResult() {
			super(SIZE);
			fill((byte)0);
		}
		public AioResult(long address) {
			super(address, SIZE);
		}
		public AioResult(long retval, int state) {
			super(SIZE);
			set(retval, state);
		}
		public void set(long retval, int state) {
			AioResult.retval.set(this, retval);
			AioResult.state .set(this, state);
		}
	}

	public static class AioRWRequest extends Buffer {
		public static final FieldOffT  offset = new FieldOffT (0);
		public static final FieldSizeT nbyte  = new FieldSizeT(offset.next);
		public static final FieldPtr   buf    = new FieldPtr  (nbyte.next);
		public static final FieldPtr   result = new FieldPtr  (buf.next);
		public static final FieldInt32 fd     = new FieldInt32(result.next);
		public static final FieldInt32 pad    = new FieldInt32(fd.next);
		public static final int SIZE = pad.next;
		public AioRWRequest() {
			super(SIZE);
			fill((byte)0);
		}
		public AioRWRequest(long address) {
			super(address, SIZE);
		}
		public AioRWRequest(long offset, long nbyte, Buffer buf, Buffer result, int fd) {
			super(SIZE);
			set(offset, nbyte, buf, result, fd);
		}
		public void set(long offset, long nbyte, Buffer buf, Buffer result, int fd) {
			AioRWRequest.offset.set(this, offset);
			AioRWRequest.nbyte .set(this, nbyte);
			AioRWRequest.buf   .set(this, buf    == null ? 0 : buf   .address());
			AioRWRequest.result.set(this, result == null ? 0 : result.address());
			AioRWRequest.fd    .set(this, fd);
		}
		public AioResult getResult() {
			return new AioResult(AioRWRequest.result.get(this));
		}
	}

	public static class AioRWRequests extends Buffer {
		public static final int STRIDE = AioRWRequest.SIZE;
		public int count;
		public AioRWRequests(int count) {
			super(STRIDE * count);
			fill((byte)0);
			this.count = count;
		}
		public AioRWRequests(long address, int count) {
			super(address, STRIDE * count);
			this.count = count;
		}
		public AioRWRequest get(int index) {
			return new AioRWRequest(address() + index * STRIDE);
		}
		public void set(int index, long offset, long nbyte, Buffer buf, Buffer result, int fd) {
			get(index).set(offset, nbyte, buf, result, fd);
		}
	}

	public static class AioSubmitIds extends Buffer {
		public static final int STRIDE = 4; // int
		public int count;
		public AioSubmitIds(int count) {
			super(STRIDE * count);
			fill((byte)0);
			this.count = count;
		}
		public AioSubmitIds(long address, int count) {
			super(address, STRIDE * count);
			this.count = count;
		}
		public int get(int index) {
			return api.read32(address() + index * STRIDE);
		}
		public void set(int index, int id) {
			api.write32(address() + index * STRIDE, id);
		}
	}

	// FIXME: Documentation, checks?
	public long aioSubmitCmd(int cmd, AioRWRequests requests, int priority, AioSubmitIds ids) {
		return syscall(SYS_aio_submit_cmd, cmd, requests.address(), requests.count, priority, ids.address());
	}

	// Lapse always uses AIO_PRIORITY_HIGH
	public long aioSubmitCmd(int cmd, AioRWRequests requests, AioSubmitIds ids) {
		return syscall(SYS_aio_submit_cmd, cmd, requests.address(), requests.count, AIO_PRIORITY_HIGH, ids.address());
	}

	public long aioMultiCancel(AioSubmitIds ids) {
		int rem = ids.count % MAX_AIO_IDS;
		int batches = (ids.count - rem) / MAX_AIO_IDS;
		for (int batch = 0; batch < batches; batch++) {
			long addr = ids.address() + (batch * MAX_AIO_IDS * AioSubmitIds.STRIDE);
			long r = syscall(SYS_aio_multi_cancel, addr, MAX_AIO_IDS, AIO_ERRORS.address());
			if (r != 0) {
				return r;
			}
		}
		if (rem > 0) {
			long addr = ids.address() + (batches * MAX_AIO_IDS * AioSubmitIds.STRIDE);
			long r = syscall(SYS_aio_multi_cancel, addr, rem, AIO_ERRORS.address());
			if (r != 0) {
				return r;
			}
		}
		return 0;
	}

	public long aioMultiPoll(AioSubmitIds ids, Buffer errors) {
		int rem = ids.count % MAX_AIO_IDS;
		int batches = (ids.count - rem) / MAX_AIO_IDS;
		for (int batch = 0; batch < batches; batch++) {
			long addr = ids.address() + (batch * MAX_AIO_IDS * AioSubmitIds.STRIDE);
			long r = syscall(SYS_aio_multi_poll, addr, MAX_AIO_IDS, errors.address());
			if (r != 0) {
				return r;
			}
		}
		if (rem > 0) {
			long addr = ids.address() + (batches * MAX_AIO_IDS * AioSubmitIds.STRIDE);
			long r = syscall(SYS_aio_multi_poll, addr, rem, errors.address());
			if (r != 0) {
				return r;
			}
		}
		return 0;
	}

	public long aioMultiPoll(AioSubmitIds ids) {
		return aioMultiPoll(ids, AIO_ERRORS);
	}

	public long aioMultiDelete(AioRWRequests requests, Buffer errors) {
		if (errors == null) {
			errors = AIO_ERRORS;
		}
		return syscall(SYS_aio_multi_delete, requests.address(), requests.count, errors.address());
	}
}
