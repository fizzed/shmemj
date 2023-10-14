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
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative");
        }
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
        return this.nativeCreate();
    }

    public SharedMemory open() {
        return this.nativeOpen();
    }

    //
    // native methods
    //

    protected native SharedMemory nativeCreate();

    protected native SharedMemory nativeOpen();

}