package org.bdj.payload;

import org.bdj.UITextConsole;
import org.bdj.payload.LibKernel.KernelModuleInfo;

public class Payload {
	public static void main(UITextConsole console) throws Exception {
		LibKernel lk = new LibKernel(console);

		// credit to flatz for this technique
		KernelModuleInfo kmi = lk.sceKernelGetModuleInfoFromAddr();
		if (kmi.initProcAddr - kmi.firstSegment == 0) {
			console.add("platform is PS4");
		} else if (kmi.initProcAddr - kmi.firstSegment == 0x10) {
			console.add("platform is PS5");
		} else {
			console.add("platform is unknown");
		}

		// Example for working syscall:
		console.add("getpid: " + lk.syscall(LibKernel.SYS_getpid));

		// Example for non-working syscall:
		console.add("getpgid: " + lk.syscall(LibKernel.SYS_getpgid));
	}
}
