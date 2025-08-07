package org.bdj;

import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.awt.Color;
import java.util.StringTokenizer;
import java.util.Vector;

public class UITextConsole extends Container {
	private static final long serialVersionUID = 0x4141414141414141L;

	public Font mFont = new Font(null, Font.PLAIN, 20);
	public Color mBgColor = new Color(20, 20, 60);
	public Color mFgColor = new Color(250, 250, 250);
	public Vector mLines = new Vector(1024);

	public UITextConsole() {
		super();
		setSize(1920, 1080); // Absolutely required
		setBackground(Color.BLACK); // Possibly required?
		setForeground(Color.WHITE); // Possibly required?
	}

	public void add(String line) {
		try {
			StringTokenizer st = new StringTokenizer(line, "\n");
			int tokens = st.countTokens();
			// Might have to break out line into multiple lines
			// Also have to filter out tabs and CRs
			for (int i = 0; i < tokens; i++) {
				String l = st.nextToken();
				StringBuffer sb = new StringBuffer(l.length() + 32);
				for (int j = 0; j < l.length(); j++) {
					char c = l.charAt(j);
					if (c == '\t') {
						sb.append("    ");
					} else if (c == '\r') {
						continue;
					} else {
						sb.append(c);
					}
				}
				synchronized(mLines) {
					mLines.add(sb.toString());
				}
			}
			repaint();
		} catch (Throwable e) {
			synchronized(mLines) {
				mLines.add(e.toString());
			}
			repaint();
		}
	}

	public void add(Throwable e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		pw.close();
		add(sw.toString());
	}

	public void update(Graphics g) {
		super.update(g);
		paint(g);
	}

	public void paint(Graphics g) {
		super.paint(g);
		int w = getWidth(), h = getHeight();
		g.setColor(mBgColor);
		g.fillRect(0, 0, w, h);
		g.setColor(mFgColor);
		g.setFont(mFont);
		try {
			int margin = 10, spacing = 2;
			int lineHeight = g.getFontMetrics().getHeight();
			int x = margin;
			int y = h - margin;
			synchronized(mLines) {
				for (int i = mLines.size() - 1; i >= 0; i--) {
					// Note that x,y is the bottom/baseline left corner of the text
					g.drawString((String)mLines.elementAt(i), x, y);
					y -= lineHeight - spacing;
					if (y < -lineHeight) {
						break;
					}
				}
			}
		} catch (Throwable e) {
			g.drawString("UITextConsole: " + e.toString(), 100, 100);
		}
	}
}
