package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    /** If the shmem that this condition is from is closed/destroyed, the native methods here would cause a segfault.
     * Also, if the caller is relying on GC to close it, keeping a reference here will help prevent that until both
     * this condition AND the shmem are ready for GC.
     */
    private SharedMemory shmem;

    public SharedCondition() {
        this.ptr = 0;
        this.size = 0;
    }

    // package-level access
    void setShmem(SharedMemory shmem) {
        this.shmem = shmem;
    }

    public boolean isDestroyed() {
        return this.ptr == 0;
    }

    protected void checkIfShmemDestroyed() {
        if (this.shmem.isDestroyed()) {
            throw new IllegalStateException("Underlying shared memory was destroyed");
        }
    }

    public long getSize() {
        return this.size;
    }

    /**
     * Causes the current thread to wait until it is signalled or interrupted, or the specified waiting time elapses.
     * @param time
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        this.checkIfShmemDestroyed();

        // we can only simulate interruptibly via checking with a spinlock technique
        long willTimeoutAt = System.currentTimeMillis() + unit.toMillis(time);
        int awaitCount = 0;
        do {
            // IMPORTANT: the underlying "nativeAwaitMillis" uses a CAS spinlock under-the-hood, which will eat up
            // cpu if it needs to wait for long periods of time.  We'll use a backoff strategy and put ourselves to
            // sleep, rather than continuously killing the cpu.
            // NOTE: anything less than 1 second usually results in almost instantaneous return
            if (this.nativeAwaitMillis(0L)) {
                return true;
            }

            if (awaitCount < 20) {  // roughly up to < 1 sec
                // we will quickly try awaitAgain
            } else if (awaitCount < 80) {   // roughly up to 3 secs
                // go to sleep for a short duration
                Thread.sleep(5L);
            } else {
                // go to sleep for a longer duration
                Thread.sleep(100L);
            }

            // were we interrupted?
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            awaitCount++;
        } while (System.currentTimeMillis() < willTimeoutAt);

        return false;
    }

    public void signal() {
        this.checkIfShmemDestroyed();
        this.nativeSignal();
    }

    public void clear() {
        this.checkIfShmemDestroyed();
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