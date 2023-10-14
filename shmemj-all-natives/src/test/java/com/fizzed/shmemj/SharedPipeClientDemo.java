package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.DemoHelper.getStringUTF8;
import static com.fizzed.shmemj.DemoHelper.putStringUTF8;

public class SharedPipeClientDemo {
    static private final Logger log = LoggerFactory.getLogger(SharedPipeClientDemo.class);

    static public void main(String[] args) throws Exception {
        final String osId = "/shmem_4D970E56854B037D";

        try (final SharedMemory shmem = new SharedMemoryFactory().setOsId(osId).open()) {
            log.info("Created shmem: owner={}, size={}, os_id={}", shmem.isOwner(), shmem.getSize(), shmem.getOsId());

            final SharedPipe channel = SharedPipe.create(shmem);

            for (int i = 0; i < 20; i++) {
                // we want to read from the channel!
                log.info("beginRead()");
                final ByteBuffer readBuffer = channel.beginRead(120, TimeUnit.SECONDS);

                String recvMessage = getStringUTF8(readBuffer);

                log.info("Recv message: {}", recvMessage);

                /*log.info("endRead()");
                channel.endRead();

                // we want to write to the channel!
                log.info("beginWrite()");
                final ByteBuffer writeBuffer = channel.beginWrite(120, TimeUnit.SECONDS);*/

                log.info("endReadBeginWrite()");
                final ByteBuffer writeBuffer = channel.endReadBeginWrite();


                String sendMessage = recvMessage + " (this is the reply)";
                putStringUTF8(writeBuffer, sendMessage);
                log.info("Send message: {}", sendMessage);

                log.info("endWrite()");
                channel.endWrite();
            }
        }

        log.info("Done, shmem will have been closed");
    }

}