package com.fizzed.shmemj;

import java.io.Closeable;
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

    public String getOsId() {
        return this.nativeGetOsId();
    }

    public boolean isOwner() {
        return this.nativeIsOwner();
    }

    public long getSize() {
        return this.nativeGetSize();
    }

    public SharedCondition newCondition(long offset, boolean autoReset) {
        this.checkConditionOffset(offset);
        SharedCondition c = this.nativeNewCondition(offset, autoReset);
        c.setShmem(this);
        return c;
    }

    public SharedCondition existingCondition(long offset) {
        this.checkConditionOffset(offset);
        SharedCondition c = this.nativeExistingCondition(offset);
        c.setShmem(this);
        return c;
    }

    private void checkConditionOffset(long offset) {
        long size = this.getSize();
        if (offset >= size) {
            throw new IllegalArgumentException("Offset " + offset + " exceeds shared memory size of " + size);
        }
    }

    public ByteBuffer newByteBuffer(long offset, long length) {
        long size = this.getSize();
        if (length <= 0) {
            throw new IllegalArgumentException("Length " + length + " must be > 0");
        }
        if (offset >= size) {
            throw new IllegalArgumentException("Offset " + offset + " exceeds shared memory size of " + size);
        }
        if (offset+length > size) {
            throw new IllegalArgumentException("Offset+length " + (offset+length) + " exceeds shared memory size of " + size);
        }
        return this.nativeNewByteBuffer(offset, length);
    }

    public boolean isDestroyed() {
        return this.ptr == 0;
    }

    @Override
    public void close() {
        this.nativeDestroy();
    }

    //
    // native methods
    //

    protected native String nativeGetOsId();

    protected native boolean nativeIsOwner();

    protected native long nativeGetSize();

    protected native SharedCondition nativeNewCondition(long offset, boolean autoReset);

    protected native SharedCondition nativeExistingCondition(long offset);

    protected native ByteBuffer nativeNewByteBuffer(long offset, long length);

    protected native void nativeDestroy();

    @Override
    protected void finalize() throws Throwable {
        this.nativeDestroy();
    }

    @Override
    public String toString() {
        return "SharedMemory{" +
            "ptr=" + ptr +
            '}';
    }

}