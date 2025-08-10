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
	public TextConsole textConsole = new TextConsole();

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
				break;
			case DisableSecurity.DISABLED_BY_EXPLOIT:
				Console.log("BD-JB-1250 successfully disabled JVM security");
				break;
			case DisableSecurity.DISABLED_PREVIOUSLY:
				Console.log("JVM security previously disabled");
				break;
		}
		if (disableSecurity.exception != null) {
			Console.log(disableSecurity.exception);
		}

		scene = HSceneFactory.getInstance().getDefaultHScene();
		scene.add(textConsole, BorderLayout.CENTER);
		scene.validate();
	}

	public void startXlet() {
		textConsole.setVisible(true);
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

		try {
			RemoteConsole remoteConsole = new RemoteConsole(localhost, 9020);
			remoteConsole.start();
		} catch (Throwable e) {
			Console.log(e);
		}

		try {
			RemoteLoader remoteLoader = new RemoteLoader(localhost, 9025);
			remoteLoader.start();
		} catch (Throwable e) {
			Console.log(e);
		}
	}

	public void pauseXlet() {
		// FIXME: Is this actually needed?
		textConsole.setVisible(true);
	}

	public void destroyXlet(boolean unconditional) {
		// FIXME: Is this actually needed?
		scene.remove(textConsole);
	}
}
