// SPDX-License-Identifier: MIT
// Copyright (C) 2021 Andy Nguyen - BD-JB
// Copyright (C) 2025 Gezine - BD-JB-1250

package org.bdj;

// NOTE: These imports absolutely have to be from javax.microedition.xlet
import javax.microedition.xlet.Xlet;
import javax.microedition.xlet.XletContext;
import javax.microedition.xlet.XletStateChangeException;

// Part of BD-JB-1250 by Gezine
// Stub required as an argument to createXlet

public class DisableSecurityXlet implements Xlet {
	// FIXME: Not sure if 'throws XletStateChangeException' is actually needed
	public void initXlet(XletContext ctx) throws XletStateChangeException {}
	public void startXlet() throws XletStateChangeException {}
	public void pauseXlet() {}
	public void destroyXlet(boolean unconditional) throws XletStateChangeException {}
}
