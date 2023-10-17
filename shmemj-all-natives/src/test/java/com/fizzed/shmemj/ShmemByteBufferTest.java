package com.fizzed.shmemj;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ShmemByteBufferTest {

    @Test
    public void putAndGet() throws Exception {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        final ByteBuffer buf = shmem.newByteBuffer(0, 2048L);

        try {
            assertThat(buf.getLong(0), is(0L));
            assertThat(buf.getLong(8), is(0L));

            buf.putLong(0, 123456789L);
            buf.putLong(8, 6543L);

            assertThat(buf.getLong(0), is(123456789L));
            assertThat(buf.getLong(8), is(6543L));
        } finally {
            shmem.close();
        }
    }

}