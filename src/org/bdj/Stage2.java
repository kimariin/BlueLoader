package org.bdj;

public class Stage2 extends Thread {
	private Stage1 mStage1 = null;

	public void run(Stage1 stage1) {
		mStage1 = stage1;
		run();
	}

	public void run() {
		String text = mStage1.mTextBox.getText();
		text += "\n";
		text += "Stage2 loaded";
		mStage1.mTextBox.setText(text);
	}
}
