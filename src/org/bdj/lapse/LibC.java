// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj.lapse;

import org.bdj.UITextConsole;
import org.bdj.api.API;

/** Wrapper around libc */

public class LibC extends Library {
	public LibC(UITextConsole console) throws Exception {
		super(console, API.LIBC_MODULE_HANDLE);
		console.add("Loaded LibC: handle " + handle);
		console.add("Loaded LibC: strerror = 0x" + Long.toHexString(this.Strerror.address));
	}

	public Function Strerror = new Function("strerror");
	public String strerror(int errno) {
		if (Strerror.address != 0) {
			long s = Strerror.call(errno);
			if (s != 0) {
				return api.readString(s);
			}
		}
		return "errno=" + Integer.toString(errno);
	}
}
