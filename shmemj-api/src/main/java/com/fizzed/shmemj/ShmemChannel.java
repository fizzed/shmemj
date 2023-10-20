package com.fizzed.shmemj;

import java.io.Closeable;
import java.nio.ByteBuffer;

public interface ShmemChannel extends AutoCloseable {

    interface Read extends Closeable {

        ByteBuffer getBuffer();

    }

    interface Write extends Closeable {

        ByteBuffer getBuffer();

    }

    Shmem getShmem();

    String getAddress();

    boolean isServer();

    boolean isSpinLocks();

    long getServerPid();

    long getClientPid();

    long getWriteBufferSize();

    long getReadBufferSize();

    boolean isClosed();

    @Override
    void close() throws Exception;

}