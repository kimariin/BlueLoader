// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Gezine - BD-JB-1250
// Copyright (C) 2025 Kimari

package org.bdj;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class RemoteConsole extends Thread {
	public int mPort;

	private InetAddress mLocalHost;
	private UITextConsole mConsole;

	public RemoteConsole(InetAddress localHost, int port, UITextConsole console) {
		super();
		mLocalHost = localHost;
		mPort = port;
		mConsole = console;
	}

	private class ClientThread extends Thread {
		public Socket mClient;
		public UITextConsole mConsole;

		public ClientThread(Socket client, UITextConsole console) {
			super();
			mClient = client;
			mConsole = console;
		}

		public void run() {
			String clientAddr = mClient.getInetAddress().getHostAddress();
			int clientPort = mClient.getPort();
			mConsole.add("Remote console client connected: " + clientAddr + ":" + clientPort);
			try {
				OutputStream output = mClient.getOutputStream();
				int nextLine = 0;
				while (!mClient.isClosed()) {
					int lineCount;
					synchronized(mConsole.mLines) {
						lineCount = mConsole.mLines.size();
					}
					for (int i = nextLine; i < lineCount; i++) {
						String line = (String)mConsole.mLines.elementAt(i) + "\n";
						output.write(line.getBytes("UTF-8"));
					}
					nextLine = lineCount;
					synchronized(mClient) {
						mClient.wait(500);
					}
				}
				mConsole.add("Remote console client disconnected: " + clientAddr + ":" + clientPort);
			} catch (Throwable e) {
				mConsole.add("Error in remote console client thread for " + clientAddr + ":" + clientPort);
				mConsole.add(e);
			}
		}
	}

	private ServerSocket mServer;

	public void run() {
		try {
			mServer = new ServerSocket(mPort);
			String name = mLocalHost.getHostName();
			String addr = mLocalHost.getHostAddress();
			mConsole.add("Remote console ready on " + name + ":" + mPort);
			if (!name.equals(addr)) {
				mConsole.add("Remote console ready on " + addr + ":" + mPort);
			}
			while (true) {
				Socket client = mServer.accept();
				ClientThread thread = new ClientThread(client, mConsole);
				thread.start();
			}
		} catch (Throwable e) {
			mConsole.add(e);
		}
	}
}
