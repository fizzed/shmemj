package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShmemFactory {
    static private final Logger log = LoggerFactory.getLogger(ShmemFactory.class);

    static {
        LibraryLoader.loadLibrary();
    }

    private long size;
    private String osId;
    private String flink;
    private boolean destroyOnExit;

    public long getSize() {
        return size;
    }

    public ShmemFactory setSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
        this.size = size;
        return this;
    }

    public String getOsId() {
        return osId;
    }

    public ShmemFactory setOsId(String osId) {
        this.osId = osId;
        return this;
    }

    public String getFlink() {
        return flink;
    }

    public ShmemFactory setFlink(String flink) {
        this.flink = flink;
        return this;
    }

    public boolean isDestroyOnExit() {
        return destroyOnExit;
    }

    public ShmemFactory setDestroyOnExit(boolean destroyOnExit) {
        this.destroyOnExit = destroyOnExit;
        return this;
    }

    public Shmem create() {
        final Shmem shmem = this.nativeCreate(this.size, this.flink);
        if (this.destroyOnExit) {
            this.addShutdownHook(shmem);
        }
        return shmem;
    }

    public Shmem open() {
        final Shmem shmem = this.nativeOpen(this.flink, this.osId);
        if (this.destroyOnExit) {
            this.addShutdownHook(shmem);
        }
        return shmem;
    }

    private void addShutdownHook(Shmem shmem) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (shmem != null) {
                    try {
                        if (!shmem.isDestroyed()) {
                            log.warn("Destroying shared memory on exit: owner={}, size={}, os_id={}, flink={}",
                                shmem.isOwner(), shmem.getSize(), shmem.getOsId(), shmem.getFlink());
                            shmem.close();
                        }
                    } catch (Throwable t) {
                        log.error("Unable to cleanly close shared memory", t);
                    }
                }
            }
        });
    }

    //
    // native methods
    //

    protected native Shmem nativeCreate(long size, String flink);

    protected native Shmem nativeOpen(String flink, String osId);

}