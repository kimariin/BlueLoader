// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj.fsdump;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bdj.Console;

/* Payload that dumps out everything accessible from the JVM process. It's not much.
 * TODO: Try using syscalls? JVM security shouldn't matter because of BD-JB-1250, but who knows? */

public class FilesystemDump {
	public static final int PORT = 9030;

	public static void main() throws Exception {
		try {
			ServerSocket server = new ServerSocket(PORT);
			Console.log("FSDump: listening on port " + PORT);

			Socket client = server.accept();
			String clientAddr = client.getInetAddress().getHostAddress();
			int clientPort = client.getPort();
			Console.log("FSDump: client connected: " + clientAddr + ":" + clientPort);

			OutputStream output = client.getOutputStream();
			ZipOutputStream zip = new ZipOutputStream(output);

			dump(zip, "/app0");         // Blu-ray player directory
			dump(zip, "/disc");         // Blu-ray contents
			dump(zip, ".");             // BD-J application JAR
			dump(zip, "..");            // doesn't work
			dump(zip, "/");             // doesn't work
			dump(zip, "/adm");          // doesn't work
			dump(zip, "/app_tmp");      // doesn't work
			dump(zip, "/data");         // doesn't work
			dump(zip, "/dsm");          // doesn't work
			dump(zip, "/dsm/app_base"); // same as . but doesn't work
			dump(zip, "/eap_user");     // doesn't work
			dump(zip, "/eap_vsh");      // doesn't work
			dump(zip, "/hdd");          // doesn't work
			dump(zip, "/host");         // doesn't work
			dump(zip, "/hostapp");      // doesn't work
			dump(zip, "/mnt");          // doesn't work
			dump(zip, "/mnt/disc");     // doesn't work
			dump(zip, "/mnt/ext0");     // doesn't work
			dump(zip, "/mnt/pfs");      // doesn't work
			dump(zip, "/mnt/rnps");     // doesn't work
			dump(zip, "/mnt/sandbox");  // doesn't work
			dump(zip, "/mnt/usb0");     // doesn't work
			dump(zip, "/mnt/usb1");     // doesn't work
			dump(zip, "/mnt/usb2");     // doesn't work
			dump(zip, "/mnt/usb3");     // doesn't work
			dump(zip, "/mnt/usb4");     // doesn't work
			dump(zip, "/mnt/usb5");     // doesn't work
			dump(zip, "/mnt/usb6");     // doesn't work
			dump(zip, "/mnt/usb7");     // doesn't work
			dump(zip, "/preinst");      // doesn't work
			dump(zip, "/preinst2");     // doesn't work
			dump(zip, "/system");       // doesn't work
			dump(zip, "/system_data");  // doesn't work
			dump(zip, "/system_ex");    // some VSH resources readable
			dump(zip, "/system_tmp");   // can list but can't read
			dump(zip, "/update");       // doesn't work
			dump(zip, "/usb");          // doesn't work
			dump(zip, "/user");         // doesn't work

			zip.finish();
			zip.close();
			client.close();

			server.close();
		} catch (Throwable e) {
			Console.log(e);
		}
	}

	private static void dump(ZipOutputStream zip, String dirname) {
		File dir = new File(dirname);
		HashSet dumped = new HashSet(1024);
		dump(zip, dumped, dir);
	}

	private static void dump(ZipOutputStream zip, HashSet dumped, File dir) {
		if (!dir.isDirectory()) {
			Console.log("FSDump: not a directory: " + dir.getAbsolutePath());
			return;
		}
		File[] fs = dir.listFiles();
		for (int i = 0; i < fs.length; i++) {
			File f = fs[i];
			if (dumped.contains(f.getAbsolutePath())) {
				continue;
			}
			if (f.isDirectory()) {
				dump(zip, dumped, f);
				continue;
			}
			if (f.canRead()) {
				try {
					Console.log("FSDump: " + f.getAbsolutePath());
					ZipEntry e = new ZipEntry(f.getAbsolutePath().substring(1));
					zip.putNextEntry(e);
					FileInputStream fi = new FileInputStream(f);
					while (fi.available() > 0) {
						int maxlen = 4 * 1024 * 1024; // to not run out of memory
						int len = Math.min(fi.available(), maxlen);
						byte[] bytes = new byte[len];
						fi.read(bytes);
						zip.write(bytes);
					}
					fi.close();
					zip.closeEntry();
					dumped.add(f.getAbsolutePath());
				} catch (Throwable e) {
					Console.log("FSDump: error while reading " + f.getAbsolutePath());
					Console.log(e);
				}
			} else {
				Console.log("FSDump: not allowed to read " + f.getAbsolutePath());
			}
		}
	}
}
