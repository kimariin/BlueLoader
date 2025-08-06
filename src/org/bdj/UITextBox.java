package org.bdj;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

public class UITextBox extends Container {
	public int mMaxFontSize = 32;
	public int mMinFontSize = 8;
	public Color mBgColor = new Color(20, 20, 60);
	public Color mFgColor = new Color(240, 240, 240);

	private String mText;
	private String[] mLines;
	private int[] mLineHeights;
	private int mFontSize, mLineSpace;
	private Font mFont;
	private FontRenderContext mFontRenderContext;
	private int mWidth, mHeight;

	public String getText() {
		return mText;
	}

	private void setTextNoRepaint(String text) {
		mText = text;
		mLines = text.split("\n");
		mLineHeights = new int[mLines.length];
		mFontSize = mMaxFontSize;
		while (mFontSize > mMinFontSize) {
			mLineSpace = mFontSize / 8;
			mFont = new Font(null, Font.PLAIN, mFontSize);
			mFontRenderContext = new FontRenderContext(null, true, false);
			mWidth = mHeight = 0;
			for (int i = 0; i < mLines.length; i++) {
				Rectangle2D r = mFont.getStringBounds(mLines[i], mFontRenderContext);
				mWidth = Math.max(((int)r.getWidth()), mWidth);
				mHeight += ((int)r.getHeight()) + (i > 0 ? mLineSpace : 0);
				mLineHeights[i] = ((int)r.getHeight());
			}
			if (mWidth <= getWidth() && mHeight <= getHeight()) {
				break;
			}
			mFontSize = Math.max(mMinFontSize, mFontSize - 4);
		}
	}

	public void setText(String text) {
		setTextNoRepaint(text);
		repaint();
	}

	public UITextBox(String text) {
		setTextNoRepaint(text);
	}

	public void paint(Graphics g) {
		int w = getWidth(), h = getHeight();
		g.setFont(mFont);
		g.setColor(mBgColor);
		g.fillRect(0, 0, w, h);
		g.setColor(mFgColor);
		g.setFont(mFont);
		int x = w/2 - mWidth /2;
		int y = h/2 - mHeight/2;
		for (int i = 0; i < mLines.length; i++) {
			g.drawString(mLines[i], x, y);
			y += mLineHeights[i] + mLineSpace;
		}
	}
}
