// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringTokenizer;
import java.util.Vector;

public class Console {
	public static interface Listener {
		public void react(String line) throws Throwable;
	}

	private static Vector listeners = new Vector(8);

	public static void register(Listener listener) {
		listeners.add(listener);
	}

	public static void unregister(Listener listener) {
		int i = listeners.indexOf(listener);
		if (i == -1) return;
		listeners.set(i, null);
	}

	public static Vector lines = new Vector(1024);

	private static void broadcast(String line) {
		lines.add(line);
		try {
			for (int i = 0; i < listeners.size(); i++) {
				Listener listener = (Listener)listeners.get(i);
				if (listener != null) {
					listener.react(line);
				}
			}
		} catch (Throwable e) {
			lines.add(e.toString());
		}
	}

	public static void log(String line) {
		try {
			// Break line down if needed
			StringTokenizer st = new StringTokenizer(line, "\n");
			int ntok = st.countTokens();
			for (int itok = 0; itok < ntok; itok++) {
				String tok = st.nextToken();
				int len = tok.length();
				StringBuffer buf = new StringBuffer(len + 4);
				// Filter out any tabs or CRs
				for (int i = 0; i < tok.length(); i++) {
					char c = tok.charAt(i);
					switch (c) {
						case '\t': buf.append("  "); break;
						case '\r': break;
						default: buf.append(c);
					}
				}
				broadcast(buf.toString());
			}
		} catch (Throwable e) {
			broadcast(line);
			broadcast(e.toString());
		}
	}

	public static void log(Throwable e) {
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			pw.close();
			log(sw.toString());
		} catch (Throwable e2) {
			broadcast(e.toString());
			broadcast(e2.toString());
		}
	}

	public static String hex(long x) { return Long.toHexString(x); }
	public static String bin(long x) { return Long.toBinaryString(x); }
}
