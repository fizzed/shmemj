package com.fizzed.shmemj;

public class DefaultProcessProvider implements ProcessProvider {


    @Override
    public long getCurrentPid() {
        return ProcessHandle.current().pid();
    }

    @Override
    public boolean isAlive(long pid) {
        ProcessHandle ph = ProcessHandle.of(pid).orElse(null);
        if (ph == null) {
            return false;
        }
        return ph.isAlive();
    }
}