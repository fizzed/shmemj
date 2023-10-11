package com.fizzed.shmemj;

import java.nio.ByteBuffer;

public class SharedMemory {

    /** pointer to the native object */
    private long ptr;

    public SharedMemory() {
        this.ptr = 0;
    }

    public native String getOsId();

    public native long getSize();

    public native ByteBuffer getByteBuffer();

    @Override
    public String toString() {
        return "SharedMemory{" +
            "ptr=" + ptr +
            '}';
    }

}