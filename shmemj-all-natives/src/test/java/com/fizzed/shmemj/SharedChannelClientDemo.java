package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.DemoHelper.getStringUTF8;
import static com.fizzed.shmemj.DemoHelper.putStringUTF8;

public class SharedChannelClientDemo {
    static private final Logger log = LoggerFactory.getLogger(SharedChannelClientDemo.class);

    static public void main(String[] args) throws Exception {
        final Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        final Path flinkPath = tempDir.resolve("shared_channel_demo.shmem");

        try (final SharedMemory shmem = new SharedMemoryFactory().setFlink(flinkPath.toString()).open()) {
            log.info("Created shmem: owner={}, size={}, os_id={}", shmem.isOwner(), shmem.getSize(), shmem.getOsId());

            final SharedChannel channel = SharedChannel.create(shmem);

            log.info("Will connect to owner process...");
            final long ownerPid = channel.connect(120, TimeUnit.SECONDS);
            log.info("Shared channel connected with owner process {}", ownerPid);

            for (int i = 0; i < 20; i++) {
                log.info("readBegin()");
                final ByteBuffer readBuffer = channel.readBegin(120, TimeUnit.SECONDS);

                String recvMessage = getStringUTF8(readBuffer);

                log.info("Recv message: {}", recvMessage);

                log.info("readEnd()");
                channel.readEnd();

                // we want to write to the channel!
                log.info("beginWrite()");
                final ByteBuffer writeBuffer = channel.writeBegin(120, TimeUnit.SECONDS);

                String sendMessage = recvMessage + " (this is the reply)";
                putStringUTF8(writeBuffer, sendMessage);
                log.info("Send message: {}", sendMessage);

                log.info("writeEnd()");
                channel.writeEnd();
            }
        }

        log.info("Done, shmem will have been closed");
    }

}