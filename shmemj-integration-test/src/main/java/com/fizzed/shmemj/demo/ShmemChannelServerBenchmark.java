package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.demo.DemoHelper.temporaryFile;

public class ShmemChannelServerBenchmark {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelServerBenchmark.class);

    static public void main(String[] args) throws Exception {
        final Path address = temporaryFile("shmem_channel_benchmark.sock");

        final boolean debug = false;
        boolean shutdown = false;

        try (final ShmemServerChannel channel = new ShmemChannelFactory().setSize(8192L).setAddress(address).setSpinLocks(true).createServerChannel()) {
            while (!shutdown) {
                try (final ShmemChannel c = channel) {
                    // we'll connect ourselves, then wait for the client
                    log.info("Waiting for client process to connect...");

                    final ShmemChannelConnection conn = channel.accept(120, TimeUnit.SECONDS);

                    log.info("Connected with process {}", conn.getRemotePid());

                    int count = 0;
                    while (!shutdown) {
                        long iteration = 0;

                        if (debug) log.info("readBegin(): waiting for request #{}", count);

                        try (final DefaultShmemChannel.Read read = conn.read(120, TimeUnit.SECONDS)) {
                            final ByteBuffer readBuffer = read.getBuffer();

                            if (debug)
                                log.info("readEnd(): received request #{} ({} bytes)", iteration, readBuffer.remaining());

                            iteration = readBuffer.getLong();
                            long v2 = readBuffer.getLong();
                            long v3 = readBuffer.getLong();

                            if (debug) log.info(" iteration={}, v2={}, v3={}", iteration, v2, v3);
                        }

                        // we want to write to the channel!
                        if (debug) log.info("beginWrite(): want to send response #{}", iteration);

                        try (final DefaultShmemChannel.Write write = conn.write(120, TimeUnit.SECONDS)) {
                            final ByteBuffer writeBuffer = write.getBuffer();

                            writeBuffer.putLong(iteration);

                            if (debug)
                                log.info("writeEnd(): sent response #{} ({} bytes)", iteration, writeBuffer.position());
                        }

                        count++;
                    }
                } catch (ClosedChannelException e) {
                    log.info("Channel closed");
                }
            }
        }

        log.info("Done, shmem will have been deleted");
    }

}