package com.fizzed.shmemj;

public class SharedMemoryFactory {
    static {
        LibraryLoader.loadLibrary();
    }

    private long size;
    private String osId;
    private String flink;

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

    public String getFlink() {
        return flink;
    }

    public SharedMemoryFactory setFlink(String flink) {
        this.flink = flink;
        return this;
    }

    public SharedMemory create() {
        return this.nativeCreate(this.size, this.flink);
    }

    public SharedMemory open() {
        return this.nativeOpen(this.flink, this.osId);
    }

    //
    // native methods
    //

    protected native SharedMemory nativeCreate(long size, String flink);

    protected native SharedMemory nativeOpen(String flink, String osId);

}