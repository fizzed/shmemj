package com.fizzed.shmemj;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SharedMemory implements Closeable {

    /** pointer to the native object */
    private long ptr;

    public SharedMemory() {
        this.ptr = 0;
    }

    public native String getOsId();

    public native long getSize();

    public native ByteBuffer getByteBuffer();



    public native SharedCondition newCondition(long offset);


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