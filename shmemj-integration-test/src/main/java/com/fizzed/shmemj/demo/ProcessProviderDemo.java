package com.fizzed.shmemj.demo;

import com.fizzed.crux.util.StopWatch;
import com.fizzed.shmemj.DefaultProcessProvider;
import com.fizzed.shmemj.ProcessProvider;
import com.fizzed.shmemj.Shmem;
import com.fizzed.shmemj.ShmemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class ProcessProviderDemo {
    static private final Logger log = LoggerFactory.getLogger(ProcessProviderDemo.class);

    static public void main(String[] args) throws Exception {
        ProcessProvider processProvider = new DefaultProcessProvider();

        StopWatch pidTimer = StopWatch.timeMillis();
        long pid = processProvider.getCurrentPid();
        log.debug("Our pid {} (in {})", pid, pidTimer);

        StopWatch isAliveTimer = StopWatch.timeMillis();
        boolean alive = processProvider.isAlive(pid);
        log.debug("Alive {} (in {})", alive, isAliveTimer);
    }

}