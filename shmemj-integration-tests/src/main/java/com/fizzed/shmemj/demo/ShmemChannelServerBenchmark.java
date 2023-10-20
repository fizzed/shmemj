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

        try (final ShmemServerChannel channel = new ShmemChannelFactory().setSize(8192L).setAddress(address).setSpinLocks(true).createServerChannel()) {
            for (;;) {
                log.info("Listening on channel {} (as pid {})", channel.getAddress(), ProcessProvider.DEFAULT.getCurrentPid());

                try (final ShmemChannelConnection conn = channel.accept(120, TimeUnit.SECONDS)) {

                    log.info("Connected with process pid={}", conn.getRemotePid());

                    for (int count = 0; ; count++) {
                        long iteration = 0;

                        if (debug) log.info("readBegin(): waiting for request #{}", count);

                        try (final ShmemChannel.Read read = conn.read(120, TimeUnit.SECONDS)) {
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

                        try (final ShmemChannel.Write write = conn.write(120, TimeUnit.SECONDS)) {
                            final ByteBuffer writeBuffer = write.getBuffer();

                            writeBuffer.putLong(iteration);

                            if (debug)
                                log.info("writeEnd(): sent response #{} ({} bytes)", iteration, writeBuffer.position());
                        }
                    }
                } catch (ShmemClosedConnectionException e) {
                    log.info("Closed connection {}: error={}", channel.getAddress(), e.getMessage());
                }
            }
        } catch (ShmemDestroyedException e) {
            log.info("Destroyed channel {}: error={}", address, e.getMessage());
        }

        log.info("Done, shmem will have been deleted");
    }

}