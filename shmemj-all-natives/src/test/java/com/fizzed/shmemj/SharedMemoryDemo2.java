package com.fizzed.shmemj;

import com.fizzed.jne.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SharedMemoryDemo2 {
    static private final Logger log = LoggerFactory.getLogger(SharedMemoryDemo2.class);

    static public void main(String[] args) throws Exception {
        Options options = new Options();
        String libraryName = options.createLibraryName("shmemj", options.getOperatingSystem(), null, null, null);
        Path libFile = Paths.get("native/target/debug", libraryName);
        String libPath = libFile.toAbsolutePath().toString();

        log.debug("Loading lib {}", libPath);

        System.load(libPath);

        log.debug("Loaded library!");

        final SharedMemory shmem = new SharedMemoryFactory()
            .setOsId("/shmem_190754AB6F13382B")
            .open();

        log.debug("Shared memory: {}", shmem);

        log.debug("OsId: {}", shmem.getOsId());
        log.debug("Size: {}", shmem.getSize());

        final ByteBuffer buf = shmem.newByteBuffer(0, 20);

        log.debug("Buf: d={}", buf.getDouble());
        log.debug("Buf: isDirect={}, class={}", buf.isDirect(), buf.getClass());
    }

}