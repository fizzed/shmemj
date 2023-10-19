package com.fizzed.shmemj;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ShmemChannelFactory {

    private final ShmemFactory shmemFactory;
    private boolean spinLocks;
    private ProcessProvider processProvider;

    public ShmemChannelFactory() {
        this.shmemFactory = new ShmemFactory();
        this.setDestroyOnExit(true);
        this.spinLocks = true;
        this.processProvider = ProcessProvider.DEFAULT;
    }

    public long getSize() {
        return this.shmemFactory.getSize();
    }

    public ShmemChannelFactory setSize(long size) {
        this.shmemFactory.setSize(size);
        return this;
    }

    public String getOsId() {
        return this.shmemFactory.getOsId();
    }

    public ShmemChannelFactory setOsId(String osId) {
        this.shmemFactory.setOsId(osId);
        return this;
    }

    public Path getAddress() {
        return Paths.get(this.shmemFactory.getFlink());
    }

    public ShmemChannelFactory setAddress(Path file) {
        this.shmemFactory.setFlink(file.toAbsolutePath().toString());
        return this;
    }

    public boolean isDestroyOnExit() {
        return this.shmemFactory.isDestroyOnExit();
    }

    public ShmemChannelFactory setDestroyOnExit(boolean destroyOnExit) {
        this.shmemFactory.setDestroyOnExit(destroyOnExit);
        return this;
    }

    public boolean isSpinLocks() {
        return spinLocks;
    }

    public ShmemChannelFactory setSpinLocks(boolean spinLocks) {
        this.spinLocks = spinLocks;
        return this;
    }

    public ProcessProvider getProcessProvider() {
        return processProvider;
    }

    public ShmemChannelFactory setProcessProvider(ProcessProvider processProvider) {
        this.processProvider = processProvider;
        return this;
    }

    public ShmemServerChannel createServerChannel() {
        final Shmem shmem = this.shmemFactory.create();

        return DefaultShmemChannel.create(this.processProvider, shmem, this.spinLocks);
    }

    public ShmemClientChannel createClientChannel() {
        final Shmem shmem = this.shmemFactory.open();

        return DefaultShmemChannel.existing(this.processProvider, shmem);
    }

}