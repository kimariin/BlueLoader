package org.bdj;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;

import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;

import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;

public class Stage1 extends Thread implements Xlet {
	public HScene mScene = null;
	public UITextBox mTextBox = null;

	private DisableSecurity disableSecurity = null;
	private StringWriter message = new StringWriter();

	public void initXlet(XletContext ctx) {
		mScene = HSceneFactory.getInstance().getDefaultHScene();
		mTextBox = new UITextBox("BlueLapse started");
		mTextBox.setBackground(new Color(0, 0, 0));
		mTextBox.setForeground(new Color(240, 240, 240));
		mTextBox.setSize((int)(mScene.getWidth() * 0.75), (int)(mScene.getHeight() * 0.75));
		mTextBox.setFont(new Font(null, Font.PLAIN, 24));
		mScene.add(mTextBox, BorderLayout.CENTER);
		mScene.validate();
		mScene.repaint();
	}

	public void startXlet() {
		mTextBox.setVisible(true);
		mScene.setVisible(true);
		start();
	}

	public void run() {
		disableSecurity = new DisableSecurity();
		switch (disableSecurity.status) {
			case DisableSecurity.ENABLED:
				message.write("BD-JB-1250 failed to disable JVM security");
				break;
			case DisableSecurity.DISABLED_BY_EXPLOIT:
				message.write("BD-JB-1250 successfully disabled JVM security");
				break;
			case DisableSecurity.DISABLED_PREVIOUSLY:
				message.write("JVM security previously disabled");
				break;
		}
		if (disableSecurity.exception != null) {
			message.write('\n');
			PrintWriter messagePrintWriter = new PrintWriter(message);
			disableSecurity.exception.printStackTrace(messagePrintWriter);
			messagePrintWriter.close();
		}
		mTextBox.setText(message.toString());
		mScene.repaint();
		if (disableSecurity.status == DisableSecurity.ENABLED) {
			return;
		}
		try {
			URL[] urls = { new URL("file:///disc/BDMV/JAR/00001.jar") };
			URLClassLoader cl = new URLClassLoader(urls);
			Class stage2Class = cl.loadClass("org.bdj.Stage2");
			Stage2 stage2 = (Stage2)stage2Class.newInstance();
			stage2.run(this);
		} catch (Throwable e) {
			message.write('\n');
			PrintWriter messagePrintWriter = new PrintWriter(message);
			disableSecurity.exception.printStackTrace(messagePrintWriter);
			messagePrintWriter.close();
		}
		mTextBox.setText(message.toString());
		mScene.repaint();
	}

	public void pauseXlet() {}

	public void destroyXlet(boolean unconditional) {
		interrupt();
	}
}
