package com.fizzed.shmemj;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

public class Shmem implements java.lang.AutoCloseable {
    static {
        LibraryLoader.loadLibrary();
    }

    /** pointer to the native object */
    private long ptr;
    final private CopyOnWriteArrayList<AutoCloseable> resources;

    public Shmem() {
        this.ptr = 0;
        this.resources = new CopyOnWriteArrayList<>();
    }

    public String getOsId() {
        return this.nativeGetOsId();
    }

    public String getFlink() {
        return this.nativeGetFlink();
    }


    public boolean isOwner() {
        return this.nativeIsOwner();
    }

    public long getSize() {
        return this.nativeGetSize();
    }

    public ShmemCondition newCondition(long offset, boolean spinLock, boolean autoReset) {
        this.checkConditionOffset(offset);
        ShmemCondition c = this.nativeNewCondition(offset, spinLock, autoReset);
        c.setShmem(this);
        //this.closeables.add(c);
        return c;
    }

    public ShmemCondition existingCondition(long offset, boolean spinLock) {
        this.checkConditionOffset(offset);
        ShmemCondition c = this.nativeExistingCondition(offset, spinLock);
        c.setShmem(this);
        //this.closeables.add(c);
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

    void registerResource(AutoCloseable resource) {
        this.resources.addIfAbsent(resource);
    }

    void unregisterResource(AutoCloseable resource) {
        this.resources.remove(resource);
    }

    public boolean isDestroyed() {
        return this.ptr == 0;
    }

    @Override
    public void close() {
        // close all resources first in reverse order
        while (!this.resources.isEmpty()) {
            // remove the last one
            AutoCloseable resource = this.resources.remove(this.resources.size()-1);
            try {
                resource.close();
            } catch (Exception e) {
                // do we ignore this?
            }
        }
        this.nativeDestroy();
    }

    //
    // native methods
    //

    protected native String nativeGetOsId();

    protected native String nativeGetFlink();

    protected native boolean nativeIsOwner();

    protected native long nativeGetSize();

    protected native ShmemCondition nativeNewCondition(long offset, boolean spinLock, boolean autoReset);

    protected native ShmemCondition nativeExistingCondition(long offset, boolean spinLock);

    protected native ByteBuffer nativeNewByteBuffer(long offset, long length);

    protected native void nativeDestroy();

    @Override
    protected void finalize() throws Throwable {
        this.nativeDestroy();
    }

    @Override
    public String toString() {
        return "Shmem{" +
            "ptr=" + ptr +
            '}';
    }

}