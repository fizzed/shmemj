package com.fizzed.shmemj;

public interface ShmemChannel extends AutoCloseable {

    Shmem getShmem();

    String getAddress();

    boolean isServer();

    boolean isSpinLocks();

    long getServerPid();

    long getClientPid();

    boolean isClosed();

    @Override
    void close() throws Exception;

}