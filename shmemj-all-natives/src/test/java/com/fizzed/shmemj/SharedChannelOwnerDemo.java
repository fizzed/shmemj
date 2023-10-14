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

public class SharedChannelOwnerDemo {
    static private final Logger log = LoggerFactory.getLogger(SharedChannelOwnerDemo.class);

    static public void main(String[] args) throws Exception {
        final Path flinkPath = Paths.get("/tmp/shared_channel_demo.shmem");
        Files.deleteIfExists(flinkPath);

        try (final SharedMemory shmem = new SharedMemoryFactory().setSize(8192L).setFlink(flinkPath.toString()).create()) {

            log.info("Created shmem: owner={}, size={}, os_id={}", shmem.isOwner(), shmem.getSize(), shmem.getOsId());

            final SharedChannel channel = SharedChannel.create(shmem);

            // asynchronously read responses
            final Thread readThread = new Thread() {
                public void run() {
                    try {
                        while (true) {
                            // now we want to read from the channel!
                            log.info("readBegin()");
                            final ByteBuffer readBuffer = channel.readBegin(120, TimeUnit.SECONDS);

                            String recvMessage = getStringUTF8(readBuffer);
                            log.info("Recv message: {}", recvMessage);

                            log.info("readEnd()");
                            channel.readEnd();
                        }
                    } catch (Exception e) {
                        log.error("Error while reading", e);
                    }
                    log.debug("Read task exiting...");
                }
            };

            log.debug("Our pid just for info sake: {}", ProcessHandle.current().pid());
            log.debug("Channel owner pid: {}", channel.getOwnerPid());
            log.debug("Channel client pid: {}", channel.getClientPid());

            // we'll connect ourselves, then wait for the client
            final long clientPid = channel.connect(120, TimeUnit.SECONDS);
            log.info("Connected with client process {}", clientPid);

            // okay to start reading now
            readThread.start();;

            for (int i = 0; i < 20; i++) {
                log.info("Send-recv loop #{}", i);

                // we want to write to the channel!
                log.info("writeBegin()");
                final ByteBuffer writeBuffer = channel.writeBegin(120, TimeUnit.SECONDS);

                String sendMessage = "Hello from loop " + i;
                putStringUTF8(writeBuffer, sendMessage);
                log.info("Send message: {}", sendMessage);

                log.info("writeEnd()");
                channel.writeEnd();
            }

            log.info("Shutting executor down...");
            readThread.interrupt();
            Thread.sleep(1000L);
        }

        log.info("Done, shmem will have been deleted");
    }

}