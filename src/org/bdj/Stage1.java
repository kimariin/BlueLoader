package org.bdj;

import java.awt.BorderLayout;

import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;

import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;

public class Stage1 extends Thread implements Xlet {
	private HScene mScene;
	private UI mUI;

	public void initXlet(XletContext ctx) {
		mScene = HSceneFactory.getInstance().getDefaultHScene();

		mUI = new UI("BlueLapse Xlet started");
		mUI.setSize(mScene.getWidth(), mScene.getHeight());

		mScene.add(mUI, BorderLayout.CENTER);
		mScene.validate();
		mScene.repaint();
	}

	public void startXlet() {
		mUI.setVisible(true);
		mScene.setVisible(true);
		start();
	}

	public void run() {
		mUI.setText("BlueLapse Xlet running");
	}

	public void pauseXlet() {}

	public void destroyXlet(boolean unconditional) {
		interrupt();
	}
}
