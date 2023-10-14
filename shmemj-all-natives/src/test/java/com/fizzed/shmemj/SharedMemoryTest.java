package com.fizzed.shmemj;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class SharedMemoryTest {

    @Test
    public void destroyInvalidatesOtherMethods() {
        final SharedMemory shmem = new SharedMemoryFactory()
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
                shmem.newCondition(1L, true);
            } catch (Exception e) {
                assertThat(e.getMessage(), containsString("no native resource"));
            }

        } finally {
            shmem.close();
        }
    }

    @Test
    public void destroyingMulipleTimes() {
        final SharedMemory shmem = new SharedMemoryFactory()
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
        final SharedMemory shmem = new SharedMemoryFactory()
             .setSize(2048L)
             .create();

        try {
            final ByteBuffer buf = shmem.newByteBuffer(0, 2048L);

            assertThat(buf.isDirect(), is(true));
            assertThat(buf.capacity(), is(2048));
            assertThat(buf.position(), is(0));

            buf.put((byte)88);
            buf.put((byte)12);
            buf.put((byte)56);

            buf.flip();
            assertThat(buf.get(), is((byte)88));
            assertThat(buf.get(), is((byte)12));
            assertThat(buf.get(), is((byte)56));
        } finally {
            shmem.close();
        }
    }

    @Test
    public void newCondition() throws Exception {
        final SharedMemory shmem = new SharedMemoryFactory()
            .setSize(2048L)
            .create();

        try {
            final SharedCondition condition1 = shmem.newCondition(0, true);

            assertThat(condition1.getSize(), greaterThan(1L));

            // this should work
            condition1.signal();
            condition1.clear();

            boolean signaled;

            // with no signal, we should timeout
            signaled = condition1.await(10, TimeUnit.MILLISECONDS);
            assertThat(signaled, is(false));

            // we shouldn't actually need to wait
            condition1.signal();
            signaled = condition1.await(10, TimeUnit.MILLISECONDS);
            assertThat(signaled, is(true));
        } finally {
            shmem.close();
        }
    }

}