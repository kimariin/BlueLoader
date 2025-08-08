// SPDX-License-Identifier: MIT
// Copyright (C) 2021 Andy Nguyen - BD-JB
// Copyright (C) 2025 Gezine - BD-JB-1250

package org.bdj;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

// Part of BD-JB-1250 by Gezine.
// Privileged action that disables JVM security.

public class DisableSecurityAction implements PrivilegedExceptionAction {
	public DisableSecurityAction() throws PrivilegedActionException {
		AccessController.doPrivileged(this);
	}
	public Object run() throws Exception {
		System.setSecurityManager(null);
		return null;
	}
}
