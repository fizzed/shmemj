package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SharedCondition implements Closeable {
    static private final Logger log = LoggerFactory.getLogger(SharedCondition.class);

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

    /*public void await() {
        this.nativeAwaitMillis(0);
    }*/

    /**
     * Causes the current thread to wait until it is signalled or interrupted, or the specified waiting time elapses.
     * @param time
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        // we can only simulate interruptibly via checking with a spinlock technique
        long timeoutAt = System.currentTimeMillis() + unit.toMillis(time);
        int i = 0;
        do {
            log.debug("Waiting for condition #{}", i);
            // NOTE: anything less than 1 second results in almost instantaneous return
            if (this.nativeAwaitMillis(500L)) {
                return true;
            }
            // were we interrupted?
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            i++;
        } while (System.currentTimeMillis() < timeoutAt);

        return false;
    }

    public boolean awaitUninterruptibly(long time, TimeUnit unit) {
        return this.nativeAwaitMillis(unit.toMillis(time));
    }

    public void signal() {
        this.nativeSignal();
    }

    public void clear() {
        this.nativeClear();
    }

    @Override
    public void close() throws IOException {
        this.nativeDestroy();
    }

    @Override
    protected void finalize() throws Throwable {
        this.nativeDestroy();
    }

    @Override
    public String toString() {
        return "SharedCondition{" +
            "ptr=" + ptr +
            ", size=" + size +
            '}';
    }

    //
    // native methods
    //

    protected native boolean nativeAwaitMillis(long timeoutMillis);

    protected native void nativeSignal();

    protected native void nativeClear();

    protected native void nativeDestroy();

}