package org.bdj;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringTokenizer;

public class TextBox extends Container {
	public Font font = new Font(null, Font.PLAIN, 32);

	private static final Color bg = new Color(20, 20, 60);
	private static final Color fg = new Color(250, 250, 250);

	public String text;

	public TextBox() {
		super();
		setSize(1920, 1080); // absolutely required
		setBackground(bg);
		setForeground(fg);
		text = "BlueLoader";
	}

	public void set(String text) {
		this.text = text;
		repaint();
	}

	public void set(Throwable e) {
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			set(sw.toString());
		} catch (Throwable e2) {
			set(e.toString() + "\n" + e2.toString());
		}
	}

	public void add(String text) {
		this.text += "\n" + text;
		repaint();
	}

	public void add(Throwable e) {
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			add(sw.toString());
		} catch (Throwable e2) {
			add(e.toString() + "\n" + e2.toString());
		}
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

		// Break text into lines
		StringTokenizer st = new StringTokenizer(text, "\n");
		int tokens = st.countTokens();
		String lines[] = new String[tokens];
		for (int i = 0; i < tokens; i++) {
			lines[i] = st.nextToken();
		}

		// Measure text box height
		FontMetrics fm = g.getFontMetrics();
		int th = 0, lspace = 4, lheight = fm.getHeight();
		for (int i = 0; i < lines.length; i++) {
			th += lheight + (i > 0 ? lspace : 0);
		}

		// Draw centered text
		int y = h/2 - th/2 + lheight;
		for (int i = 0; i < lines.length; i++) {
			int x = w/2 - fm.stringWidth(lines[i])/2;
			g.drawString(lines[i], x, y);
			y += lheight + lspace;
		}
	}
}
