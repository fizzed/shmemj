package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class ShmemChannelClientDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelClientDemo.class);

    static public void main(String[] args) throws Exception {
        final Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        final Path flinkPath = tempDir.resolve("shared_channel_demo.shmem");

        final boolean debug = false;
        final int iterations = 200000;

        try (final Shmem shmem = new ShmemFactory()
                .setFlink(flinkPath.toString())
                .setDestroyOnExit(true)
                .open()) {

            log.info("Created shmem: owner={}, size={}, os_id={}, flink={}", shmem.isOwner(), shmem.getSize(), shmem.getOsId(), shmem.getFlink());

            final ShmemChannel channel = ShmemChannel.existing(shmem);

            log.info("Connecting to owner process...");

            final long ownerPid = channel.connect(120, TimeUnit.SECONDS);

            log.info("Connected with owner process {}", ownerPid);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < iterations; i++) {
                if (debug) log.info("writeBegin(): want to send request #{}", i);
                try (final ShmemChannel.Write write = channel.write(120, TimeUnit.SECONDS)) {
                    final ByteBuffer writeBuffer = write.getBuffer();

                    writeBuffer.putLong(i);
                    writeBuffer.putLong(2L);
                    writeBuffer.putLong(3L);

                    if (debug) log.info("writeEnd(): sent request #{} ({} bytes)", i, writeBuffer.position());
                }

                // now we want to read from the channel!
                if (debug) log.info("readBegin(): waiting for response #{}", i);
                try (final ShmemChannel.Read read = channel.read(120, TimeUnit.SECONDS)) {
                    final ByteBuffer readBuffer = read.getBuffer();

                    if (debug) log.info("readEnd(): received response #{} ({} bytes)", i, readBuffer.remaining());

                    readBuffer.getLong();
                }
            }

            long endTime = System.currentTimeMillis();
            log.info("Took {} ms", (endTime-startTime));
        }

        log.info("Done, shmem will have been closed");
    }

}