// SPDX-License-Identifier: MIT
// Copyright (C) 2025 Kimari

package org.bdj.lapse;

import org.bdj.UITextConsole;
import org.bdj.api.API;

/** Common helper classes for libc and libkernel wrappers */

public class Library {
	public final API api;
	public final UITextConsole console;
	public final int handle;

	public Library(UITextConsole console, int handle) throws Exception {
		this.api = API.getInstance();
		this.console = console;
		this.handle = handle;
	}

	/** We have lots of buffer-like classes that can't inherit from Buffer for stupid reasons */
	public interface BufferLike {
		public long address();
		public long size();
	};

	/** Represents a field inside a Buffer */
	public class Field {
		public final BufferLike buffer;
		public final int size;
		public final int offset;
		public final int next;

		public Field(BufferLike buffer, int size, int offset) {
			this.buffer = buffer;
			this.size = size;
			this.offset = offset;
			this.next = offset + size;
		}

		public byte  read8 () { return api.read8 (buffer.address() + offset); }
		public short read16() { return api.read16(buffer.address() + offset); }
		public int   read32() { return api.read32(buffer.address() + offset); }
		public long  read64() { return api.read64(buffer.address() + offset); }

		public void write8 (byte  x) { api.write8 (buffer.address() + offset, x); }
		public void write16(short x) { api.write16(buffer.address() + offset, x); }
		public void write32(int   x) { api.write32(buffer.address() + offset, x); }
		public void write64(long  x) { api.write64(buffer.address() + offset, x); }
	}

	public class FieldInt32 extends Field {
		public FieldInt32(BufferLike buffer, int offset) { super(buffer, 4, offset); }
		public int  get()      { return read32(); }
		public void set(int x) { write32(x); }
	}

	public class FieldInt64 extends Field {
		public FieldInt64(BufferLike buffer, int offset) { super(buffer, 8, offset); }
		public long get()       { return read64(); }
		public void set(long x) { write64(x); }
	}

	public class FieldSizeT extends FieldInt64 {
		public FieldSizeT(BufferLike buffer, int offset) { super(buffer, offset); }
	}

	public class FieldPtr extends FieldInt64 {
		public FieldPtr(BufferLike buffer, int offset) { super(buffer, offset); }
	}

	public class FieldOffT extends FieldInt64 {
		public FieldOffT(BufferLike buffer, int offset) { super(buffer, offset); }
	}

	/** Native callable function/syscall was never resolved to an address */
	public static class CallableNotResolved extends RuntimeException {
		public CallableNotResolved(String msg) { super(msg); }
	}

	/** Native callable function/syscall failed with an error */
	public static class SystemCallFailed extends RuntimeException {
		public int errno = 0;       // number returned by libkernel __error (or 0)
		public String error = null; // string returned by libc strerror (or null)
		public SystemCallFailed(String msg) {
			super(msg);
		}
		public SystemCallFailed(String msg, int errno, String error) {
			super(msg + " (" + error + ")");
			this.errno = errno;
			this.error = error;
		}
		public SystemCallFailed(String msg, int errno, LibC libc) {
			super(msg + " (" + libc.strerror(errno) + ")");
			this.errno = errno;
			this.error = libc.strerror(errno);
		}
	}

	/** Native callable function/syscall was used with invalid arguments */
	public static class SystemCallInvalid extends RuntimeException {
		public SystemCallInvalid(String msg) {
			super(msg);
		}
	}

	/** Native callable function/syscall. Use Function/Syscall classes instead. */
	public class Callable {
		public final String name;
		public long address = 0;

		public Callable(String name) {
			this.name = name;
		}

		public long call() throws CallableNotResolved {
			if (this.address == 0) throw new CallableNotResolved(this.name);
			return api.call(this.address);
		}

		public long call(long arg0) throws CallableNotResolved {
			if (this.address == 0) throw new CallableNotResolved(this.name);
			return api.call(this.address, arg0);
		}

		public long call(long arg0, long arg1) throws CallableNotResolved {
			if (this.address == 0) throw new CallableNotResolved(this.name);
			return api.call(this.address, arg0, arg1);
		}

		public long call(long arg0, long arg1, long arg2) throws CallableNotResolved {
			if (this.address == 0) throw new CallableNotResolved(this.name);
			return api.call(this.address, arg0, arg1, arg2);
		}

		public long call(long arg0, long arg1, long arg2, long arg3) throws CallableNotResolved {
			if (this.address == 0) throw new CallableNotResolved(this.name);
			return api.call(this.address, arg0, arg1, arg2, arg3);
		}

		public long call(long arg0, long arg1, long arg2, long arg3, long arg4) throws CallableNotResolved {
			if (this.address == 0) throw new CallableNotResolved(this.name);
			return api.call(this.address, arg0, arg1, arg2, arg3, arg4);
		}

		public long call(long arg0, long arg1, long arg2, long arg3, long arg4, long arg5) throws CallableNotResolved {
			if (this.address == 0) throw new CallableNotResolved(this.name);
			return api.call(this.address, arg0, arg1, arg2, arg3, arg4, arg5);
		}
	}

	/** Native callable function */
	public class Function extends Callable {
		public Function(String name) {
			super(name);
			this.address = api.dlsym(handle, name);
		}
	}
}
