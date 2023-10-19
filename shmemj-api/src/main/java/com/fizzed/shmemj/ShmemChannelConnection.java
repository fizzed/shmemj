package com.fizzed.shmemj;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ShmemChannelConnection implements AutoCloseable {

    final private DefaultShmemChannel channel;

    public ShmemChannelConnection(DefaultShmemChannel channel) {
        this.channel = channel;
    }

    public long getLocalPid() {
        if (this.channel.isServer()) {
            return this.channel.getServerPid();
        } else {
            return this.channel.getClientPid();
        }
    }

    public long getRemotePid() {
        if (this.channel.isServer()) {
            return this.channel.getClientPid();
        } else {
            return this.channel.getServerPid();
        }
    }

    public DefaultShmemChannel.Write write(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {
        return this.channel.write(timeout, unit);
    }

    public DefaultShmemChannel.Read read(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {
        return this.channel.read(timeout, unit);
    }

    @Override
    public void close() throws Exception {
        this.channel.closeConnection(false);
    }

}