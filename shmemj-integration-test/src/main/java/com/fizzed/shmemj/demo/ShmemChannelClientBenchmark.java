package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.fizzed.shmemj.demo.DemoHelper.temporaryFile;

public class ShmemChannelClientBenchmark {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelClientBenchmark.class);

    static public void main(String[] args) throws Exception {
        final Path address = temporaryFile("shmem_channel_benchmark.sock");
        final boolean debug = false;
        final int iterations = 200000;

        try (final ShmemClientChannel channel = new ShmemChannelFactory().setAddress(address).createClientChannel()) {

            log.info("Connecting to server process...");

            try (final ShmemChannelConnection conn = channel.connect(120, TimeUnit.SECONDS)) {

                log.info("Connected with process {}", conn.getRemotePid());

                long startTime = System.currentTimeMillis();

                for (int i = 0; i < iterations; i++) {
                    sendRequestAndGetResponse(debug, i, conn);
                }

                long endTime = System.currentTimeMillis();
                log.info("Took {} ms", (endTime - startTime));
                log.info("Will close channel now...");
            }
        }

        log.info("Done, shmem will have been closed");
    }

    static public void sendRequestAndGetResponse(boolean debug, int i, ShmemChannelConnection conn) throws IOException, InterruptedException, TimeoutException {
        if (debug) log.info("writeBegin(): want to send request #{}", i);

        try (final DefaultShmemChannel.Write write = conn.write(120, TimeUnit.SECONDS)) {
            final ByteBuffer writeBuffer = write.getBuffer();

            writeBuffer.putLong(i);
            writeBuffer.putLong(2L);
            writeBuffer.putLong(3L);

            if (debug) log.info("writeEnd(): sent request #{} ({} bytes)", i, writeBuffer.position());
        }

        // now we want to read from the channel!
        if (debug) log.info("readBegin(): waiting for response #{}", i);

        try (final DefaultShmemChannel.Read read = conn.read(120, TimeUnit.SECONDS)) {
            final ByteBuffer readBuffer = read.getBuffer();

            if (debug)
                log.info("readEnd(): received response #{} ({} bytes)", i, readBuffer.remaining());

            long v1 = readBuffer.getLong();

            if (debug) log.info(" v1={}", v1);
        }
    }

}