package com.fizzed.shmemj;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class ShmemTest {

    @Test
    public void destroyInvalidatesOtherMethods() {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        try {
            shmem.close();

            try {
                shmem.getSize();
            } catch (Exception e) {
                assertThat(e.getMessage(), containsString("no native resource"));
            }

            try {
                shmem.getOsId();
            } catch (Exception e) {
                assertThat(e.getMessage(), containsString("no native resource"));
            }

            try {
                shmem.newByteBuffer(1L, 1L);
            } catch (Exception e) {
                assertThat(e.getMessage(), containsString("no native resource"));
            }

            try {
                shmem.newCondition(1L, true, true);
            } catch (Exception e) {
                assertThat(e.getMessage(), containsString("no native resource"));
            }

        } finally {
            shmem.close();
        }
    }

    @Test
    public void destroyingMulipleTimes() {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        try {
            // closing multiple times in row is fine
            shmem.close();
            shmem.close();
            shmem.close();
        } finally {
            shmem.close();
        }
    }

    @Test
    public void newByteBuffer() {
        final Shmem shmem = new ShmemFactory()
             .setSize(2048L)
             .create();

        try {
            final ByteBuffer buf = shmem.newByteBuffer(0, 2048L);

            // basic methods to see if it works
            assertThat(buf.capacity(), greaterThan(2047));
            assertThat(buf.position(), is(0));
        } finally {
            shmem.close();
        }
    }

    @Test
    public void newCondition() throws Exception {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        try {
            final ShmemCondition condition1 = shmem.newCondition(0, false, true);

            assertThat(condition1.getSize(), greaterThan(1L));
            condition1.signal();
            condition1.clear();
        } finally {
            shmem.close();
        }
    }

}