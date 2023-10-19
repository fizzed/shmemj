package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.ShmemChannelConnection;
import com.fizzed.shmemj.ShmemChannelFactory;
import com.fizzed.shmemj.DefaultShmemChannel;
import com.fizzed.shmemj.ShmemClientChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.demo.DemoHelper.*;

public class ShmemChannelClientDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelClientDemo.class);

    static public void main(String[] args) throws Exception {
        final Path address = temporaryFile("shmem_channel_demo.sock");

        try (final ShmemClientChannel channel = new ShmemChannelFactory().setAddress(address).createClientChannel()) {
            log.info("Connecting to channel {}", channel.getAddress());

            try (final ShmemChannelConnection conn = channel.connect(5, TimeUnit.SECONDS)) {

                log.info("Connected with process pid={}", conn.getRemotePid());

                // send request
                try (DefaultShmemChannel.Write write = conn.write(5, TimeUnit.SECONDS)) {
                    String s = "Hello";
                    putStringUTF8(write.getBuffer(), s);
                    log.debug("Sending: {}", s);
                }

                // recv response
                try (DefaultShmemChannel.Read read = conn.read(5, TimeUnit.SECONDS)) {
                    String s = getStringUTF8(read.getBuffer());
                    log.debug("Received: {}", s);
                }
            }
        } catch (ClosedChannelException e) {
            log.info("Channel closed");
        }

        log.info("Done. Exiting.");
    }

}