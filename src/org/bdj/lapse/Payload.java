package org.bdj.lapse;

public class Payload {
	public static void main() throws Exception {
		LibKernel libkernel = new LibKernel();
		Lapse lapse = new Lapse(libkernel);
		lapse.start();
	}
}
