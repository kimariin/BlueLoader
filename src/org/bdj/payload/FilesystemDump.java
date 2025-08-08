package org.bdj.payload;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bdj.UITextConsole;

public class FilesystemDump {
	public static void run(UITextConsole console, int port) {
		try {
			ServerSocket server = new ServerSocket(port);
			console.add("ZIP dumper listening on port " + port);

			boolean dumped = false;
			while (!dumped) {
				Socket client = server.accept();
				String clientAddr = client.getInetAddress().getHostAddress();
				int clientPort = client.getPort();
				console.add("ZIP dumper client connected: " + clientAddr + ":" + clientPort);

				OutputStream output = client.getOutputStream();
				ZipOutputStream zip = new ZipOutputStream(output);

				File app0 = new File("/app0");
				FilesystemDump.dumpDirectoryToZip(zip, app0, console);

				// Just class files, e.g. /dsm/app_base/org/bdj/MainXlet.class
				// File cwd = new File(".");
				// FilesystemDump.dumpDirectoryToZip(zip, cwd, console);

				// Just to show where the Blu-ray is mounted
				// FilesystemDump.dumpDirectoryToZip(zip, disc, console);

				zip.finish();
				zip.close();
				client.close();
				dumped = true;
			}
			server.close();
		} catch (Throwable e) {
			console.add(e);
		}
	}

	private static void dumpDirectoryToZip(ZipOutputStream zip, File dir, UITextConsole console) {
		if (!dir.isDirectory()) {
			console.add(dir.getAbsolutePath() + " is not a directory");
			return;
		}
		File[] fs = dir.listFiles();
		for (int i = 0; i < fs.length; i++) {
			File f = fs[i];
			if (f.isDirectory()) {
				dumpDirectoryToZip(zip, f, console);
			} else if (f.canRead()) {
				try {
					// I assume the leading / is a bad idea?
					console.add("Dumping: " + f.getAbsolutePath());
					ZipEntry e = new ZipEntry(f.getAbsolutePath().substring(1));
					zip.putNextEntry(e);
					FileInputStream fi = new FileInputStream(f);
					while (fi.available() > 0) {
						byte[] bytes = new byte[fi.available()];
						fi.read(bytes);
						zip.write(bytes);
					}
					fi.close();
					zip.closeEntry();
				} catch (Throwable e) {
					console.add("Error while reading " + f.getAbsolutePath());
					console.add(e);
				}
			} else {
				console.add("Not allowed to read " + f.getAbsolutePath());
			}
		}
	}
}
