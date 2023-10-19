package com.fizzed.shmemj;

public interface ProcessProvider {

    long getCurrentPid();

    boolean isAlive(long pid);

}