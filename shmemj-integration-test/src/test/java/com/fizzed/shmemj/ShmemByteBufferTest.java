package com.fizzed.shmemj;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ShmemByteBufferTest {

    @Test
    public void standardMethods() {
        final Shmem shmem = new ShmemFactory()
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