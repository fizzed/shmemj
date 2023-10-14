package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.DemoHelper.getStringUTF8;
import static com.fizzed.shmemj.DemoHelper.putStringUTF8;

public class SharedPipeOwnerDemo {
    static private final Logger log = LoggerFactory.getLogger(SharedPipeOwnerDemo.class);

    static public void main(String[] args) throws Exception {
        try (final SharedMemory shmem = new SharedMemoryFactory().setSize(8192L).create()) {
            log.info("Created shmem: owner={}, size={}, os_id={}", shmem.isOwner(), shmem.getSize(), shmem.getOsId());

            final SharedPipe channel = SharedPipe.create(shmem);

            // to demonstrate another thread processing responses
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                while (true) {
                    // now we want to read from the channel!
                    log.info("beginRead()");
                    final ByteBuffer readBuffer = channel.beginRead(120, TimeUnit.SECONDS);

                    String recvMessage = getStringUTF8(readBuffer);
                    log.info("Recv message: {}", recvMessage);

                    log.info("endRead()");
                    channel.endRead();
                }
            });

            for (int i = 0; i < 20; i++) {
                log.info("Send-recv loop #{}", i);

                // we want to write to the channel!
                log.info("beginWrite()");
                final ByteBuffer writeBuffer = channel.beginWrite(120, TimeUnit.SECONDS);

                String sendMessage = "Hello from loop " + i;
                putStringUTF8(writeBuffer, sendMessage);
                log.info("Send message: {}", sendMessage);

                log.info("endWrite()");
                channel.endWrite();


                /*// now we want to read from the channel!
                log.info("beginRead()");
                final ByteBuffer readBuffer = channel.beginRead(120, TimeUnit.SECONDS);

                String recvMessage = getStringUTF8(readBuffer);
                log.info("Recv message: {}", recvMessage);

                log.info("endRead()");
                channel.endRead();*/
            }

            executor.shutdownNow();
        }

        log.info("Done, shmem will have been deleted");
    }

}