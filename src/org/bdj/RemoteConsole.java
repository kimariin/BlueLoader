// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Gezine - BD-JB-1250
// Copyright (C) 2025 Kimari

package org.bdj;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class RemoteConsole extends Thread {
	private int port;
	private InetAddress localhost;

	public RemoteConsole(InetAddress localhost, int port) {
		this.localhost = localhost;
		this.port = port;
	}

	private ServerSocket server;

	public void run() {
		try {
			server = new ServerSocket(port);
			String name = localhost.getHostName();
			String addr = localhost.getHostAddress();
			Console.log("Remote console ready on " + name + ":" + port);
			if (!name.equals(addr)) {
				Console.log("Remote console ready on " + addr + ":" + port);
			}
			while (true) {
				Socket client = server.accept();
				ClientThread thread = new ClientThread(client);
				thread.start();
			}
		} catch (Throwable e) {
			Console.log(e);
		}
	}

	private class ClientThread extends Thread implements Console.Listener {
		private Socket client;

		public ClientThread(Socket client) {
			super();
			this.client = client;
		}

		OutputStream output = null;

		public void run() {
			String clientAddr = client.getInetAddress().getHostAddress();
			int clientPort = client.getPort();
			Console.log("Remote console client connected: " + clientAddr + ":" + clientPort);
			try {
				output = client.getOutputStream();
				// Initial dump of everything already logged:
				for (int i = 0; i < Console.lines.size(); i++) {
					String line = (String)Console.lines.get(i);
					output.write(line.getBytes("UTF-8"));
					output.write('\n');
				}
				Console.register(this);
				while (!client.isClosed()) {
					synchronized(output) {
						output.wait();
					}
				}
				Console.unregister(this);
				Console.log("Remote console client disconnected: " + clientAddr + ":" + clientPort);
			} catch (Throwable e) {
				Console.log("Error in remote console client thread for " + clientAddr + ":" + clientPort);
				Console.log(e);
			}
		}

		public void react(String line) throws Throwable {
			synchronized(output) {
				output.notify();
				output.write(line.getBytes("UTF-8"));
				output.write('\n');
			}
		}
	}
}
