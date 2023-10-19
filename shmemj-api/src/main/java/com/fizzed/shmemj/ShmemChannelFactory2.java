package com.fizzed.shmemj;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ShmemChannelFactory2 {

    private final ShmemFactory shmemFactory;
    private boolean spinLocks;

    public ShmemChannelFactory2() {
        this.shmemFactory = new ShmemFactory();
        this.setDestroyOnExit(true);
        this.spinLocks = true;
    }

    public long getSize() {
        return this.shmemFactory.getSize();
    }

    public ShmemChannelFactory2 setSize(long size) {
        this.shmemFactory.setSize(size);
        return this;
    }

    public String getOsId() {
        return this.shmemFactory.getOsId();
    }

    public ShmemChannelFactory2 setOsId(String osId) {
        this.shmemFactory.setOsId(osId);
        return this;
    }

    public Path getAddress() {
        return Paths.get(this.shmemFactory.getFlink());
    }

    public ShmemChannelFactory2 setAddress(Path file) {
        this.shmemFactory.setFlink(file.toAbsolutePath().toString());
        return this;
    }

    public boolean isDestroyOnExit() {
        return this.shmemFactory.isDestroyOnExit();
    }

    public ShmemChannelFactory2 setDestroyOnExit(boolean destroyOnExit) {
        this.shmemFactory.setDestroyOnExit(destroyOnExit);
        return this;
    }

    public boolean isSpinLocks() {
        return spinLocks;
    }

    public ShmemChannelFactory2 setSpinLocks(boolean spinLocks) {
        this.spinLocks = spinLocks;
        return this;
    }

    public ShmemChannel createServerChannel() {
        final Shmem shmem = this.shmemFactory.create();

        return ShmemChannel.create(shmem, this.spinLocks);
    }

    public ShmemChannel createClientChannel() {
        final Shmem shmem = this.shmemFactory.open();

        return ShmemChannel.existing(shmem);
    }

}