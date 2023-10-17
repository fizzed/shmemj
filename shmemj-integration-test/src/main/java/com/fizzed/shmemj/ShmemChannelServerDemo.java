package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class ShmemChannelServerDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelServerDemo.class);

    static public void main(String[] args) throws Exception {
        final Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        final Path flinkPath = tempDir.resolve("shared_channel_demo.shmem");
        Files.deleteIfExists(flinkPath);

        final boolean debug = false;
        boolean shutdown = false;

        try (final Shmem shmem = new ShmemFactory()
                .setSize(8192L)
                .setFlink(flinkPath.toString())
                // will cause segfault if we're waiting in a read() call
                // TODO: make shutdown hook gracefully cleanup resources
                //.setDestroyOnExit(true)
                .create()) {

            log.info("Created shmem: owner={}, size={}, os_id={}, flink={}", shmem.isOwner(), shmem.getSize(), shmem.getOsId(), shmem.getFlink());

            final ShmemChannel channel = ShmemChannel.create(shmem, false);

            // we'll connect ourselves, then wait for the client
            log.info("Waiting for client process to connect...");

            final long clientPid = channel.accept(120, TimeUnit.SECONDS);

            log.info("Connected with client process {}", clientPid);

            int count = 0;
            while (!shutdown) {
                long iteration = 0;

                if (debug) log.info("readBegin(): waiting for request #{}", count);

                try (final ShmemChannel.Read read = channel.read(120, TimeUnit.SECONDS)) {
                    final ByteBuffer readBuffer = read.getBuffer();

                    if (debug) log.info("readEnd(): received request #{} ({} bytes)", iteration, readBuffer.remaining());

                    iteration = readBuffer.getLong();
                    readBuffer.getLong();
                    readBuffer.getLong();
                }

                // we want to write to the channel!
                if (debug) log.info("beginWrite(): want to send response #{}", iteration);

                try (final ShmemChannel.Write write = channel.write(120, TimeUnit.SECONDS)) {
                    final ByteBuffer writeBuffer = write.getBuffer();

                    writeBuffer.putLong(iteration);

                    if (debug) log.info("writeEnd(): sent response #{} ({} bytes)", iteration, writeBuffer.position());
                }

                count++;
            }
        }

        log.info("Done, shmem will have been deleted");
    }

}