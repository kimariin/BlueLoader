// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj.payload;

import org.bdj.UITextConsole;

public class Payload {
	public static void main(UITextConsole console) throws Exception {
		LibKernel libkernel = new LibKernel(console);

		// If someone connects to port 9030, dump the entire accessible filesystem to a zip file
		FilesystemDump.run(console, 9030);

		// FIXME: For development only
		// LapseMainThread lapse = new LapseMainThread(libkernel, console);
		// lapse.start();
	}
}
