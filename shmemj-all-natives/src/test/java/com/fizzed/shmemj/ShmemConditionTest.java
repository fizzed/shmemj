package com.fizzed.shmemj;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class ShmemConditionTest {

    @Test
    public void destroyingShmemInvalidatesNativeCalls() throws Exception {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        final ShmemCondition condition = shmem.newCondition(0, true);

        try {
            // closing the shared memory makes the condition impossible to use (its methods should fail, not segfault)
            shmem.close();

            try {
                condition.signal();
            } catch (IllegalStateException e) {
                assertThat(e.getMessage(), containsString("shared memory was destroyed"));
            }

            try {
                condition.clear();
            } catch (IllegalStateException e) {
                assertThat(e.getMessage(), containsString("shared memory was destroyed"));
            }

            try {
                condition.await(1, TimeUnit.SECONDS);
            } catch (IllegalStateException e) {
                assertThat(e.getMessage(), containsString("shared memory was destroyed"));
            }
        } finally {
            // we can still close condition and shmem again though
            condition.close();
            shmem.close();
        }
    }

    @Test
    public void destroyingConditionInvalidatesNativeCalls() throws Exception {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        final ShmemCondition condition = shmem.newCondition(0, true);

        try {
            // closing the shared memory makes the condition impossible to use (its methods should fail, not segfault)
            condition.close();

            try {
                condition.signal();
            } catch (RuntimeException e) {
                assertThat(e.getMessage(), containsString("no native resource attached"));
            }

            try {
                condition.clear();
            } catch (RuntimeException e) {
                assertThat(e.getMessage(), containsString("no native resource attached"));
            }

            try {
                condition.await(1, TimeUnit.SECONDS);
            } catch (RuntimeException e) {
                assertThat(e.getMessage(), containsString("no native resource attached"));
            }
        } finally {
            // we can still close condition and shmem again though
            condition.close();
            shmem.close();
        }
    }

    @Test
    public void awaitIsInterruptible() throws Exception {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        final ShmemCondition condition = shmem.newCondition(0, true);

        try {
            final CountDownLatch interruptedLatch = new CountDownLatch(1);
            // fire up a thread that will wait on the condition
            final Thread t = new Thread() {
                public void run() {
                    try {
                        condition.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        // this is what we expect, we'll return from here too
                        interruptedLatch.countDown();
                    }
                }
            };
            t.start();

            // interrupt thread, countdown latch should be invoked
            t.interrupt();

            if (!interruptedLatch.await(5, TimeUnit.SECONDS)) {
                fail("await was NOT interrupted");
            }
        } finally {
            // we can still close condition and shmem again though
            condition.close();
            shmem.close();
        }
    }

}