package com.fizzed.siamese;

import com.fizzed.jne.JNE;
import com.fizzed.jne.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SharedMemoryDemo {
    static private final Logger log = LoggerFactory.getLogger(SharedMemoryDemo.class);

    static public void main(String[] args) throws Exception {
        Options options = new Options();
        String libraryName = options.createLibraryName("jsiamese", options.getOperatingSystem(), null, null, null);
        Path libFile = Paths.get("native/target/debug", libraryName);
        String libPath = libFile.toAbsolutePath().toString();

        log.debug("Loading lib {}", libPath);

        System.load(libPath);

        log.debug("Loaded library!");

        final SharedMemory shmem = new SharedMemoryFactory()
            .setSize(4096L)
            .create();

        log.debug("Shared memory: {}", shmem);

        log.debug("OsId: {}", shmem.getOsId());
        log.debug("Size: {}", shmem.getSize());

        final ByteBuffer buf = shmem.getByteBuffer();
        buf.putDouble(5.4d);
        buf.flip();

        log.debug("Buf: d={}", buf.getDouble());
        log.debug("Buf: isDirect={}, class={}", buf.isDirect(), buf.getClass());


        final ByteBuffer buf2 = shmem.getByteBuffer();

        log.debug("Buf2: d={}", buf2.getDouble());
        log.debug("Buf2: d={}", buf2.getDouble());

        /*String r = SharedMemory.hello("yo");

        log.debug("r was {}", r);*/

        Thread.sleep(600000000L);
    }

}