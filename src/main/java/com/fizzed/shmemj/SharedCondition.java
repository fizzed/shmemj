package com.fizzed.shmemj;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SharedCondition {

    /** pointer to the native object */
    private long ptr;
    private long size;

    public SharedCondition() {
        this.ptr = 0;
        this.size = 0;
    }

    public long getSize() {
        return this.size;
    }

    //public native void waitMillis(long millis);

    /*private native void destroy();

    @Override
    public void close() throws IOException {
        this.destroy();
    }

    @Override
    protected void finalize() throws Throwable {
        this.destroy();
    }*/

    @Override
    public String toString() {
        return "SharedCondition{" +
            "ptr=" + ptr +
            ", size=" + size +
            '}';
    }

}