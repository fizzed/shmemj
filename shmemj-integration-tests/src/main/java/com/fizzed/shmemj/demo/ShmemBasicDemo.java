package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.Shmem;
import com.fizzed.shmemj.ShmemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class ShmemBasicDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemBasicDemo.class);

    static public void main(String[] args) throws Exception {
        final Shmem shmem = new ShmemFactory()
            .setSize(4096L)
            .setOsId("/shmem_190754AB6F13382B")
            .setDestroyOnExit(true)
            .create();

        log.debug("Shared memory: {}", shmem);

        log.debug("OsId: {}", shmem.getOsId());
        log.debug("Size: {}", shmem.getSize());

        final ByteBuffer buf = shmem.newByteBuffer(0, 20);

        log.debug("Buf: d={}", buf.getDouble());
        log.debug("Buf: isDirect={}, class={}", buf.isDirect(), buf.getClass());
    }

}