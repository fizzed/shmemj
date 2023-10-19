package com.fizzed.shmemj;

public interface ProcessProvider {

    static public final ProcessProvider DEFAULT = new DefaultProcessProvider();

    long getCurrentPid();

    boolean isAlive(long pid);

}