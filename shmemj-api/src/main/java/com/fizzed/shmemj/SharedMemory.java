package com.fizzed.shmemj;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SharedMemory implements Closeable {
    static {
        LibraryLoader.loadLibrary();
    }

    /** pointer to the native object */
    private long ptr;

    public SharedMemory() {
        this.ptr = 0;
    }

    public native String getOsId();

    public native long getSize();

    public SharedCondition newCondition(long offset) {
        long size = this.getSize();
        if (offset >= size) {
            throw new IllegalArgumentException("Offset " + offset + " exceeds shared memory size of " + size);
        }
        return this.doNewCondition(offset);
    }

    public ByteBuffer newByteBuffer(long offset, long length) {
        long size = this.getSize();
        if (length <= 0) {
            throw new IllegalArgumentException("Length " + length + " must be > 0");
        }
        if (offset >= size) {
            throw new IllegalArgumentException("Offset " + offset + " exceeds shared memory size of " + size);
        }
        if (offset+length >= size) {
            throw new IllegalArgumentException("Offset+length " + (offset+length) + " exceeds shared memory size of " + size);
        }
        return this.doNewByteBuffer(offset, length);
    }

    protected native SharedCondition doNewCondition(long offset);

    protected native ByteBuffer doNewByteBuffer(long offset, long length);

    private native void destroy();

    @Override
    public void close() throws IOException {
        this.destroy();
    }

    @Override
    protected void finalize() throws Throwable {
        this.destroy();
    }

    @Override
    public String toString() {
        return "SharedMemory{" +
            "ptr=" + ptr +
            '}';
    }

}