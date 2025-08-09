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

import org.bdj.UITextConsole;

/* Payload that dumps out everything accessible from the JVM process. It's not much.
 * TODO: Try using syscalls? JVM security shouldn't matter because of BD-JB-1250, but who knows? */

public class FilesystemDump {
	public static final int PORT = 9030;

	public static void main(UITextConsole console) throws Exception {
		try {
			ServerSocket server = new ServerSocket(PORT);
			console.add("FSDump: listening on port " + PORT);

			Socket client = server.accept();
			String clientAddr = client.getInetAddress().getHostAddress();
			int clientPort = client.getPort();
			console.add("FSDump: client connected: " + clientAddr + ":" + clientPort);

			OutputStream output = client.getOutputStream();
			ZipOutputStream zip = new ZipOutputStream(output);

			dump(zip, console, "/app0");         // Blu-ray player directory
			dump(zip, console, "/disc");         // Blu-ray contents
			dump(zip, console, ".");             // BD-J application JAR
			dump(zip, console, "..");            // doesn't work
			dump(zip, console, "/");             // doesn't work
			dump(zip, console, "/adm");          // doesn't work
			dump(zip, console, "/app_tmp");      // doesn't work
			dump(zip, console, "/data");         // doesn't work
			dump(zip, console, "/dsm");          // doesn't work
			dump(zip, console, "/dsm/app_base"); // same as . but doesn't work
			dump(zip, console, "/eap_user");     // doesn't work
			dump(zip, console, "/eap_vsh");      // doesn't work
			dump(zip, console, "/hdd");          // doesn't work
			dump(zip, console, "/host");         // doesn't work
			dump(zip, console, "/hostapp");      // doesn't work
			dump(zip, console, "/mnt");          // doesn't work
			dump(zip, console, "/mnt/disc");     // doesn't work
			dump(zip, console, "/mnt/ext0");     // doesn't work
			dump(zip, console, "/mnt/pfs");      // doesn't work
			dump(zip, console, "/mnt/rnps");     // doesn't work
			dump(zip, console, "/mnt/sandbox");  // doesn't work
			dump(zip, console, "/mnt/usb0");     // doesn't work
			dump(zip, console, "/mnt/usb1");     // doesn't work
			dump(zip, console, "/mnt/usb2");     // doesn't work
			dump(zip, console, "/mnt/usb3");     // doesn't work
			dump(zip, console, "/mnt/usb4");     // doesn't work
			dump(zip, console, "/mnt/usb5");     // doesn't work
			dump(zip, console, "/mnt/usb6");     // doesn't work
			dump(zip, console, "/mnt/usb7");     // doesn't work
			dump(zip, console, "/preinst");      // doesn't work
			dump(zip, console, "/preinst2");     // doesn't work
			dump(zip, console, "/system");       // doesn't work
			dump(zip, console, "/system_data");  // doesn't work
			dump(zip, console, "/system_ex");    // some VSH resources readable
			dump(zip, console, "/system_tmp");   // can list but can't read
			dump(zip, console, "/update");       // doesn't work
			dump(zip, console, "/usb");          // doesn't work
			dump(zip, console, "/user");         // doesn't work

			zip.finish();
			zip.close();
			client.close();

			server.close();
		} catch (Throwable e) {
			console.add(e);
		}
	}

	private static void dump(ZipOutputStream zip, UITextConsole console, String dirname) {
		File dir = new File(dirname);
		HashSet dumped = new HashSet(1024);
		dump(zip, console, dumped, dir);
	}

	private static void dump(ZipOutputStream zip, UITextConsole console, HashSet dumped, File dir) {
		if (!dir.isDirectory()) {
			console.add("FSDump: not a directory: " + dir.getAbsolutePath());
			return;
		}
		File[] fs = dir.listFiles();
		for (int i = 0; i < fs.length; i++) {
			File f = fs[i];
			if (dumped.contains(f.getAbsolutePath())) {
				continue;
			}
			if (f.isDirectory()) {
				dump(zip, console, dumped, f);
				continue;
			}
			if (f.canRead()) {
				try {
					console.add("FSDump: " + f.getAbsolutePath());
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
					console.add("FSDump: error while reading " + f.getAbsolutePath());
					console.add(e);
				}
			} else {
				console.add("FSDump: not allowed to read " + f.getAbsolutePath());
			}
		}
	}
}
