// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.net.InetAddress;

import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;

import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;

public class MainXlet implements Xlet {
	public HScene scene = null;

	// FIXME: Disable text console since it does more harm than good
	// public TextConsole textConsole = new TextConsole();

	public TextBox textBox = new TextBox();

	private DisableSecurity disableSecurity = null;

	public void initXlet(XletContext ctx) {
		// FIXME: Is this actually needed for BD-JB-1250 to work?
		HScene hScene = HSceneFactory.getInstance().getDefaultHScene();
		Frame frame = (Frame)hScene.getParent();
		hScene.dispose();
		frame.dispose();

		disableSecurity = new DisableSecurity();
		switch (disableSecurity.status) {
			case DisableSecurity.ENABLED:
				Console.log("BD-JB-1250 failed to disable JVM security");
				textBox.set("BD-JB-1250 failed to disable JVM security");
				break;
			case DisableSecurity.DISABLED_BY_EXPLOIT:
				Console.log("BD-JB-1250 successfully disabled JVM security");
				textBox.set("BD-JB-1250 successfully disabled JVM security");
				break;
			case DisableSecurity.DISABLED_PREVIOUSLY:
				Console.log("JVM security previously disabled");
				textBox.set("JVM security previously disabled");
				break;
		}
		if (disableSecurity.exception != null) {
			Console.log(disableSecurity.exception);
			textBox.add(disableSecurity.exception);
		}

		scene = HSceneFactory.getInstance().getDefaultHScene();
		// scene.add(textConsole, BorderLayout.CENTER);
		scene.add(textBox, BorderLayout.CENTER);
		scene.validate();
	}

	public void startXlet() {
		// textConsole.setVisible(true);
		textBox.setVisible(true);
		scene.setVisible(true);
		scene.repaint();

		// getLocalHost can fail if called from two threads at the same time, apparently?
		InetAddress localhost;
		try {
			localhost = InetAddress.getLocalHost();
		} catch (Exception e) {
			localhost = InetAddress.getLoopbackAddress();
			Console.log(e);
		}
		String name = localhost.getHostName();
		String addr = localhost.getHostAddress();

		try {
			final int port = 9020;
			RemoteConsole remoteConsole = new RemoteConsole(localhost, port);
			remoteConsole.start();
			if (name.equals(addr)) {
				textBox.add("Console: " + name + ":" + port);
			} else {
				textBox.add("Console: " + name + ":" + port + " / " + addr + ":" + port);
			}
		} catch (Throwable e) {
			Console.log(e);
			textBox.set(e);
		}

		try {
			final int port = 9025;
			RemoteLoader remoteLoader = new RemoteLoader(localhost, port);
			remoteLoader.start();
			if (name.equals(addr)) {
				textBox.add("Loader: " + name + ":" + port);
			} else {
				textBox.add("Loader: " + name + ":" + port + " / " + addr + ":" + port);
			}
		} catch (Throwable e) {
			Console.log(e);
			textBox.set(e);
		}
	}

	public void pauseXlet() {}
	public void destroyXlet(boolean unconditional) {}
}
