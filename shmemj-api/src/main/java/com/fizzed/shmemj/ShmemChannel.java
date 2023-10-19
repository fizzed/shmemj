package com.fizzed.shmemj;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ShmemChannel extends AutoCloseable {

    Shmem getShmem();

    String getAddress();

    boolean isServer();

    boolean isSpinLocks();

    long getServerPid();

    long getClientPid();

    ShmemChannelConnection accept(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException;

    ShmemChannelConnection connect(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException;

    @Override
    void close() throws Exception;

}