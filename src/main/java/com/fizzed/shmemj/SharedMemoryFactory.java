package com.fizzed.shmemj;

public class SharedMemoryFactory {
    static {
        LibraryLoader.loadLibrary();
    }

    private long size;
    private String osId;

    public long getSize() {
        return size;
    }

    public SharedMemoryFactory setSize(long size) {
        this.size = size;
        return this;
    }

    public String getOsId() {
        return osId;
    }

    public SharedMemoryFactory setOsId(String osId) {
        this.osId = osId;
        return this;
    }

    public SharedMemory create() {
        return this.doCreate();
    }

    public SharedMemory open() {
        return this.doOpen();
    }

    protected native SharedMemory doCreate();

    protected native SharedMemory doOpen();

}