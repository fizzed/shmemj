package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.demo.DemoHelper.*;

public class ShmemChannelServerDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelServerDemo.class);

    static public void main(String[] args) throws Exception {
        final ShmemChannel channel = new ShmemChannelFactory2()
            .setAddress(temporaryFile("shmem_channel_demo.sock"))
            .setSize(4096L)
            .createServerChannel();

        boolean shutdown = false;
        while (!shutdown) {
            try {
                log.info("Listening on channel {}", channel.getAddress());

                long clientPid = channel.accept(120, TimeUnit.SECONDS);

                log.info("Connected with client process pid={}", clientPid);

                while (!shutdown) {
                    // recv request
                    String req;
                    try (ShmemChannel.Read read = channel.read(5, TimeUnit.SECONDS)) {
                        req = getStringUTF8(read.getBuffer());
                        log.debug("Received: {}", req);
                    }

                    // send response
                    try (ShmemChannel.Write write = channel.write(5, TimeUnit.SECONDS)) {
                        String resp = req + " World!";
                        putStringUTF8(write.getBuffer(), resp);
                        log.debug("Sending: {}", resp);
                    }
                }
            } catch (ClosedChannelException e) {
                log.info("Closed channel {}", channel.getAddress());
            } catch (ShmemDestroyedException e) {
                log.info("Destroyed channel {}", channel.getAddress());
                shutdown = true;
            }
        }

        log.info("Done. Exiting.");
    }

}