package org.bdj;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class RemoteLoader extends Thread {
	public int mPort;

	private InetAddress mLocalHost;
	private UITextConsole mConsole;

	public RemoteLoader(InetAddress localHost, int port, UITextConsole console) {
		super();
		mLocalHost = localHost;
		mPort = port;
		mConsole = console;
	}

	private ServerSocket mServer;

	public void run() {
		try {
			mServer = new ServerSocket(mPort);
			String name = mLocalHost.getHostName();
			String addr = mLocalHost.getHostAddress();
			mConsole.add("Remote JAR loader listening on " + name + ":" + mPort);
			if (!name.equals(addr)) {
				mConsole.add("Remote JAR loader listening on " + addr + ":" + mPort);
			}

			while (true) {
				Socket client = mServer.accept();
				String clientAddr = client.getInetAddress().getHostAddress();
				int clientPort = client.getPort();
				mConsole.add("Remote loader client connected: " + clientAddr + ":" + clientPort);

				File payload = File.createTempFile("payload", ".jar");
				payload.deleteOnExit();

				try {
					InputStream input = client.getInputStream();
					FileOutputStream output = new FileOutputStream(payload);

					byte[] buffer = new byte[8192];
					int read = 0, total = 0;
					while ((read = input.read(buffer)) != -1) {
						output.write(buffer, 0, read);
						total += read;
						mConsole.add("Transfer: received " + total + " bytes");
					}
					output.close();

					JarFile jar = new JarFile(payload);
					Manifest manifest = jar.getManifest();
					jar.close();

					if (manifest == null) {
						throw new Exception("No manifest found in JAR");
					}

					String payloadClassName = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
					if (payloadClassName == null) {
						payloadClassName = "org.bdj.payload.Payload";
					}

					ClassLoader parentLoader = RemoteLoader.class.getClassLoader();
					ClassLoader bypassRestrictionsLoader = new URLClassLoader(new URL[0], parentLoader) {
						protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
							if (name.startsWith("java.nio") || name.startsWith("javax.security.auth") ||
								name.startsWith("javax.net.ssl"))
							{
								return findSystemClass(name);
							}
							return super.loadClass(name, resolve);
						}
					};

					URL url = payload.toURL();
					URLClassLoader cl = new URLClassLoader(new URL[]{url}, bypassRestrictionsLoader);
					Class payloadClass = cl.loadClass(payloadClassName);

					// Expected signature: void main(UITextConsole)
					Method main = payloadClass.getMethod("main", new Class[]{UITextConsole.class});

					mConsole.add("Running: " + payloadClassName + ".main()");
					main.invoke(null, new Object[]{mConsole});

					mConsole.add("Finished running JAR payload with no errors");
				} catch (Throwable e) {
					mConsole.add(e);
				}

				payload.delete();
			}
		} catch (Throwable e) {
			mConsole.add(e);
		}
	}
}
