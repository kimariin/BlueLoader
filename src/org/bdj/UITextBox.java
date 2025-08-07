package org.bdj;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.StringTokenizer;

public class UITextBox extends Container {
	private static final long serialVersionUID = 0x4141414141414141L;

	public int mMaxFontSize = 32;
	public int mMinFontSize = 8;
	public Color mBgColor = new Color(20, 20, 60);
	public Color mFgColor = new Color(250, 250, 250);

	private String mText;
	private String[] mLines;
	private int mLineSpace;
	private int mFontSize;
	private Font mFont, mFallbackFont;
	private FontMetrics mFontMetrics;
	private int mWidth, mHeight;

	public String getText() {
		return mText;
	}

	private void setTextNoRepaint(String text) {
		mText = text;
		StringTokenizer st = new StringTokenizer(text, "\n");
		int tokens = st.countTokens();
		mLines = new String[tokens];
		for (int i = 0; i < tokens; i++) {
			mLines[i] = st.nextToken();
		}
	}

	public void setText(String text) {
		setTextNoRepaint(text);
		repaint();
	}

	public UITextBox(String text) {
		super();
		setBackground(Color.BLACK);
		setForeground(Color.WHITE);
		setTextNoRepaint(text);
		mFontSize = mMaxFontSize;
		mFallbackFont = new Font(null, Font.PLAIN, mFontSize);
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void paint(Graphics g) {
		try {
			int w = getWidth(), h = getHeight();
			mFontSize = mMaxFontSize;
			while (mFontSize > mMinFontSize) {
				mLineSpace = mFontSize / 8;
				mFont = new Font(null, Font.PLAIN, mFontSize);
				mFontMetrics = g.getFontMetrics(mFont);
				mWidth = mHeight = 0;
				for (int i = 0; i < mLines.length; i++) {
					mWidth = Math.max(mFontMetrics.stringWidth(mLines[i]), mWidth);
					mHeight += mFontMetrics.getHeight() + (i > 0 ? mLineSpace : 0);
				}
				if (mWidth <= w && mHeight <= h) {
					break;
				}
				mFontSize = Math.max(mMinFontSize, mFontSize - 4);
			}
			g.setFont(mFont);
			g.setColor(mBgColor);
			g.fillRect(0, 0, w, h);
			g.setColor(mFgColor);
			int x = w/2 - mWidth /2;
			int y = h/2 - mHeight/2;
			for (int i = 0; i < mLines.length; i++) {
				g.drawString(mLines[i], x, y);
				y += mFontMetrics.getHeight() + mLineSpace;
			}
		} catch (Throwable e) {
			try {
				int w = getWidth(), h = getHeight();
				g.setColor(mBgColor);
				g.fillRect(0, 0, w, h);
				g.setColor(mFgColor);
				g.setFont(mFallbackFont);
				g.drawString(mText, 100, 100);
				g.drawString(e.toString(), 100, 200);
			} catch (Throwable e2) {}
		}
	}
}
