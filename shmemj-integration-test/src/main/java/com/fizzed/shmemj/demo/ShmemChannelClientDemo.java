package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.ShmemChannel;
import com.fizzed.shmemj.ShmemChannelFactory2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.demo.DemoHelper.*;

public class ShmemChannelClientDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelClientDemo.class);

    static public void main(String[] args) throws Exception {
        final ShmemChannel channel = new ShmemChannelFactory2()
            .setAddress(temporaryFile("shmem_channel_demo.sock"))
            .createClientChannel();

        try {
            log.info("Connecting to channel {}", channel.getAddress());

            long serverPid = channel.connect(5, TimeUnit.SECONDS);

            log.info("Connected with server process pid={}", serverPid);

            // send request
            try (ShmemChannel.Write write = channel.write(5, TimeUnit.SECONDS)) {
                String s = "Hello";
                putStringUTF8(write.getBuffer(), s);
                log.debug("Sending: {}", s);
            }

            // recv response
            try (ShmemChannel.Read read = channel.read(5, TimeUnit.SECONDS)) {
                String s = getStringUTF8(read.getBuffer());
                log.debug("Received: {}", s);
            }
        } catch (ClosedChannelException e) {
            log.info("Server channel closed");
        } finally {
            channel.close();
        }

        log.info("Done. Exiting.");
    }

}