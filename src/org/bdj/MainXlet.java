package org.bdj;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.io.PrintWriter;
import java.io.StringWriter;

// FIXME: Why are imports from javax.tv okay here but not in DisableSecurityXlet?
import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;

import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;

public class MainXlet implements Xlet {
	public HScene mScene = null;
	public UITextBox mTextBox = null;

	private DisableSecurity disableSecurity = null;
	private StringWriter message = new StringWriter();

	public void initXlet(XletContext ctx) {
		// FIXME: Is this actually needed for BD-JB-1250 to work?
		HScene hScene = HSceneFactory.getInstance().getDefaultHScene();
		Frame frame = (Frame)hScene.getParent();
		hScene.dispose();
		frame.dispose();

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

		// NOTE: I'm pretty sure everything breaks if you don't call setSize here
		mTextBox = new UITextBox(message.toString());
		mTextBox.setSize(1920, 1080);

		mScene = HSceneFactory.getInstance().getDefaultHScene();
		mScene.add(mTextBox, BorderLayout.CENTER);
		mScene.validate();
	}

	public void startXlet() {
		mTextBox.setVisible(true);
		mScene.setVisible(true);
		mScene.repaint();
	}

	public void pauseXlet() {
		// FIXME: Is this actually needed?
		mTextBox.setVisible(true);
	}

	public void destroyXlet(boolean unconditional) {
		// FIXME: Is this actually needed?
		mScene.remove(mTextBox);
	}
}
