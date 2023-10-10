package com.fizzed.siamese;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SharedMemoryDemo2 {
    static private final Logger log = LoggerFactory.getLogger(SharedMemoryDemo2.class);

    static public void main(String[] args) throws Exception {
        Path libFile = Paths.get("native/target/debug/libjsiamese.so");
        String libPath = libFile.toAbsolutePath().toString();

        log.debug("Loading lib {}", libPath);

        System.load(libPath);

        log.debug("Loaded library!");

        final SharedMemory shmem = new SharedMemoryFactory()
            .setSize(4096L)
            .setOsId("/shmem_7FF1C3DD06E3E87C")
            .open();

        log.debug("Shared memory: {}", shmem);

        log.debug("OsId: {}", shmem.getOsId());
        log.debug("Size: {}", shmem.getSize());

        final ByteBuffer buf = shmem.getByteBuffer();

        log.debug("Buf: d={}", buf.getDouble());
        log.debug("Buf: isDirect={}, class={}", buf.isDirect(), buf.getClass());
    }

}