// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj;

import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Color;

public class TextConsole extends Container implements Console.Listener {
	public Font font = new Font(null, Font.PLAIN, 20);
	private static final Color bg = new Color(20, 20, 60);
	private static final Color fg = new Color(250, 250, 250);

	public TextConsole() {
		super();
		setSize(1920, 1080); // absolutely required
		setBackground(bg);
		setForeground(fg);
		Console.register(this);
	}

	public void react(String line) {
		repaint(); // this is async, right?
	}

	public void update(Graphics g) {
		super.update(g);
		paint(g);
	}

	public void paint(Graphics g) {
		super.paint(g);
		int w = getWidth(), h = getHeight();
		g.setColor(bg);
		g.fillRect(0, 0, w, h);
		g.setColor(fg);
		g.setFont(font);
		try {
			int margin = 10, spacing = 2;
			int lineHeight = g.getFontMetrics().getHeight();
			int x = margin;
			int y = h - margin;
			for (int i = Console.lines.size() - 1; i >= 0; i--) {
				String str = (String)Console.lines.get(i);
				g.drawString(str, x, y); // note that x,y is bottom/baseline left
				y -= lineHeight - spacing;
				if (y < -lineHeight) {
					break;
				}
			}
		} catch (Throwable e) {
			g.drawString(e.toString(), 100, 100);
			g.drawString(e.getStackTrace()[0].toString(), 100, 100 + font.getSize() + 2);
		}
	}
}
