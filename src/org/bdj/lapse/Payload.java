package org.bdj.lapse;

import org.bdj.UITextConsole;

public class Payload {
	public static void main(UITextConsole console) throws Exception {
		LibKernel libkernel = new LibKernel(console);
		Lapse lapse = new Lapse(libkernel, console);
		lapse.start();
	}
}
