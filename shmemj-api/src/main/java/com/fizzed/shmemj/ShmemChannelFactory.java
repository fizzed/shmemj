package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ShmemChannelFactory {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelFactory.class);

    private Shmem shmem;
    private boolean spinLocks;

    public Shmem getShmem() {
        return shmem;
    }

    public ShmemChannelFactory setShmem(Shmem shmem) {
        this.shmem = shmem;
        return this;
    }

    public boolean isSpinLocks() {
        return spinLocks;
    }

    public ShmemChannelFactory setSpinLocks(boolean spinLocks) {
        this.spinLocks = spinLocks;
        return this;
    }

    public ShmemChannel create() {
        Objects.requireNonNull(this.shmem, "shmem was null");
        return ShmemChannel.create(this.shmem, this.spinLocks);
    }

    public ShmemChannel existing() {
        Objects.requireNonNull(this.shmem, "shmem was null");
        return ShmemChannel.existing(this.shmem);
    }

}