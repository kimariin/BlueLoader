package org.bdj;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

public class UI extends Container {
	public Font mFont = new Font(null, Font.PLAIN, 32);
	public Color mBgColor = new Color(20, 20, 60);
	public Color mFgColor = new Color(240, 240, 240);

	private String mText;
	private Rectangle2D mTextRect;
	private FontRenderContext mFontRenderContext = new FontRenderContext(null, true, false);

	public String getText() {
		return mText;
	}

	public void setText(String text) {
		mText = text;
		mTextRect = mFont.getStringBounds(mText, mFontRenderContext);
		repaint();
	}

	public UI(String text) {
		setText(text);
	}

	public void paint(Graphics g) {
		int w = getWidth(), h = getHeight();
		g.setFont(mFont);
		g.setColor(mBgColor);
		g.fillRect(0, 0, w, h);
		g.setColor(mFgColor);
		g.setFont(mFont);
		int tw = (int)Math.round(mTextRect.getWidth());
		int th = (int)Math.round(mTextRect.getHeight());
		g.drawString(mText, w/2-tw/2, h/2-th/2);
	}
}
