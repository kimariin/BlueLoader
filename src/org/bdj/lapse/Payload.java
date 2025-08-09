package org.bdj.lapse;

import org.bdj.UITextConsole;

public class Payload {
	public static void main(UITextConsole console) throws Exception {
		LibKernel libkernel = new LibKernel(console);

		LapseMainThread lapse = new LapseMainThread(libkernel, console);
		lapse.start();
	}
}
