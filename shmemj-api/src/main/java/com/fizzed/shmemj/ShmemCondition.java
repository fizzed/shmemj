package com.fizzed.shmemj;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ShmemCondition implements Closeable {
    static {
        LibraryLoader.loadLibrary();
    }

    /**
     * pointer to the native object
     */
    private long ptr;
    private long size;
    private boolean spinLock;
    /** If the shmem that this condition is from is closed/destroyed, the native methods here would cause a segfault.
     * Also, if the caller is relying on GC to close it, keeping a reference here will help prevent that until both
     * this condition AND the shmem are ready for GC.
     */
    private Shmem shmem;

    public ShmemCondition() {
        this.ptr = 0;
        this.size = 0;
    }

    // package-level access
    void setShmem(Shmem shmem) {
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
        if (this.spinLock) {
            return this.awaitSpinLock(time, unit, null);
        } else {
            return this.awaitThreadLock(time, unit, null);
        }
    }

    public boolean await(long time, TimeUnit unit, Consumer<Long> waitingConsumer) throws InterruptedException {
        if (this.spinLock) {
            return this.awaitSpinLock(time, unit, waitingConsumer);
        } else {
            return this.awaitThreadLock(time, unit, waitingConsumer);
        }
    }

    private boolean awaitSpinLock(long time, TimeUnit unit, Consumer<Long> waitingConsumer) throws InterruptedException {
        this.checkIfShmemDestroyed();

        // we can only simulate interruptibly via checking with a spinlock technique
        final long timeoutMillis = unit.toMillis(time);
        final long startTimeMillis = System.currentTimeMillis();
        long elapsedMillis = 0;
        int awaitCount = 0;
        boolean triggerConsumer = false;
        long nativeAwaitMillis = 10L;
        do {
            // IMPORTANT: the underlying "nativeAwaitMillis" uses a CAS spinlock under-the-hood, which will eat up
            // cpu if it needs to wait for long periods of time.  We'll use a backoff strategy and put ourselves to
            // sleep, rather than continuously killing the cpu.
            // NOTE: anything less than 1 second usually results in almost instantaneous return
            if (this.nativeAwaitMillis(nativeAwaitMillis)) {
                return true;
            }

            if (awaitCount < 10) {          // 10 * 10 mills = 100 millis
                // we will quickly try to await again
            } else if (awaitCount < 80) {   // 60 * (10 + 25 millis) = 2100 millis
//                if (awaitCount == 20) { System.out.println("Spin lock short duration sleep   @ " + System.currentTimeMillis()); }
                // go to sleep for a very short duration, should be interruptible
                Thread.sleep(25L);
                // switch to a very short CAS cycle now
                nativeAwaitMillis = 1L;
            } else if (awaitCount < 160) {   // 60 * (10 + 100 millis) = 2100 millis
//                if (awaitCount == 80) { System.out.println("Spin lock medium duration sleep  @ " + System.currentTimeMillis()); }
                // go to sleep for a longer duration (this represents main latency)
                Thread.sleep(50L);
                triggerConsumer = !triggerConsumer && waitingConsumer != null;
            } else {
//                if (awaitCount == 160) { System.out.println("Spin lock long duration sleep   @ " + System.currentTimeMillis()); }
                // go to sleep for a longer duration (this represents main latency)
                Thread.sleep(200L);
            }

            elapsedMillis = System.currentTimeMillis() - startTimeMillis;

            if (triggerConsumer) {
                waitingConsumer.accept(elapsedMillis);
            }

            awaitCount++;
        } while (elapsedMillis < timeoutMillis);

        return false;
    }

    private boolean awaitThreadLock(long time, TimeUnit unit, Consumer<Long> waitingConsumer) throws InterruptedException {
        this.checkIfShmemDestroyed();

        final long timeoutMillis = unit.toMillis(time);
        final long startTimeMillis = System.currentTimeMillis();
        long elapsedMillis = 0;
        boolean triggerConsumer = waitingConsumer != null;
        int awaitCount = 0;
        do {
            // IMPORTANT: anything less than 1 second usually results in almost instantaneous return
            // since the underlying event is a pthread mutex condition
            if (this.nativeAwaitMillis(timeoutMillis >= 1000L ? 1000L : 10L)) {
                return true;
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

//            if (awaitCount == 0) { System.out.println("Standard condition long duration sleep  @ " + System.currentTimeMillis()); }

            elapsedMillis = System.currentTimeMillis() - startTimeMillis;

            if (triggerConsumer) {
                waitingConsumer.accept(elapsedMillis);
            }

            awaitCount++;
        } while (elapsedMillis < timeoutMillis);

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
        return "ShmemCondition{" +
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