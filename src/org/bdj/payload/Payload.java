package org.bdj.payload;

import org.bdj.UITextConsole;
import org.bdj.api.API;
import org.bdj.api.Buffer;

public class Payload {
	public static void main(UITextConsole console) throws Exception {
		// Port of syscall.lua from https://github.com/shahrilnet/remote_lua_loader
		API api = API.getInstance();

		long addressInsideLibKernel = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "gettimeofday");
		console.add("addressInsideLibKernel: 0x" + Long.toHexString(addressInsideLibKernel));

		Buffer kernelModuleInfo = new Buffer(0x300);
		long sceKernelGetModuleInfoFromAddr = api.dlsym(API.LIBKERNEL_MODULE_HANDLE, "sceKernelGetModuleInfoFromAddr");
		console.add("sceKernelGetModuleInfoFromAddr: 0x" + Long.toHexString(sceKernelGetModuleInfoFromAddr));
		long ret = api.call(sceKernelGetModuleInfoFromAddr, addressInsideLibKernel, 1, kernelModuleInfo.address());
		if (ret != 0) {
			console.add("sceKernelGetModuleInfoFromAddr error " + Long.toString(ret));
			return;
		}

		// https://github.com/shadps4-emu/shadPS4/blob/0bdd21b4e49c25955b16a3651255381b4a60f538/src/core/module.h#L32
		final long MODULEINFO_INIT_PROC_ADDR_OFFSET = 0x160;
		final long MODULEINFO_SEGMENTS_OFFSET = 0x160;
		long libKernelInit = api.read64(kernelModuleInfo.address() + MODULEINFO_INIT_PROC_ADDR_OFFSET);
		long libKernelBase = api.read64(kernelModuleInfo.address() + MODULEINFO_SEGMENTS_OFFSET);
		console.add("libkernel init address: 0x" + Long.toHexString(libKernelInit));
		console.add("libkernel base address: 0x" + Long.toHexString(libKernelBase));

		// credit to flatz for this technique
		if (libKernelInit - libKernelBase == 0) {
			console.add("platform is PS4");
		} else if (libKernelInit - libKernelBase == 0x10) {
			console.add("platform is PS5");
		} else {
			console.add("platform is unknown");
		}

		long firstQwordOfLibKernel = api.read64(libKernelBase);
		console.add("libkernel first qword: 0x" + Long.toHexString(firstQwordOfLibKernel));

		// Let's find the wrapper for getpid (syscall 20) for starters

		int nr = 20;
		byte[] pattern = {
			(byte)0x48, (byte)0xc7, (byte)0xc0,
			(byte)((nr>>0)&0xFF), (byte)((nr>>8)&0xFF), (byte)((nr>>16)&0xFF), (byte)((nr>>24)&0xFF),
			(byte)0x49, (byte)0x89, (byte)0xca, (byte)0x0f, (byte)0x05
		};
		long getpid = 0;
		for (long p = libKernelBase; p < libKernelBase + 0x40000 - pattern.length; p++) {
			boolean found = true;
			for (int i = 0; i < pattern.length; i++) {
				if (api.read8(p + i) != pattern[i]) {
					found = false;
					break;
				}
			}
			if (found) {
				getpid = p;
				break;
			}
		}

		if (getpid == 0) {
			console.add("couldn't find wrapper for getpid");
			return;
		}
		console.add("getpid wrapper: 0x" + Long.toHexString(getpid));

		// Seems to work. Now to package everything up I guess

		long pid = api.call(getpid);
		console.add("getpid result: " + Long.toString(pid));
	}
}
