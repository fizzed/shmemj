package com.fizzed.siamese;

public class SharedMemoryFactory {

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

    public native SharedMemory create();

    public native SharedMemory open();

}