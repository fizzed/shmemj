package com.fizzed.shmemj;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SharedCondition implements Closeable {
    static {
        LibraryLoader.loadLibrary();
    }

    /**
     * pointer to the native object
     */
    private long ptr;
    private long size;

    public SharedCondition() {
        this.ptr = 0;
        this.size = 0;
    }

    public long getSize() {
        return this.size;
    }

    protected native boolean awaitMillis(long timeoutMillis);

    public void await() {
        this.awaitMillis(0);
    }

    public boolean await(long time, TimeUnit unit) {
        return this.awaitMillis(unit.toMillis(time));
    }

    public native void signal();

    public native void clear();

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
        return "SharedCondition{" +
            "ptr=" + ptr +
            ", size=" + size +
            '}';
    }

}