package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.demo.DemoHelper.*;

public class ShmemChannelServerDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelServerDemo.class);

    static public void main(String[] args) throws Exception {
        final Path address = temporaryFile("shmem_channel_demo.sock");

        try (final ShmemServerChannel channel = new ShmemChannelFactory().setSize(4096L).setAddress(address).createServerChannel()) {
            for (;;) {
                log.info("Listening on channel {}", channel.getAddress());

                try (final ShmemChannelConnection conn = channel.accept(120, TimeUnit.SECONDS)) {

                    log.info("Connected with process pid={}", conn.getRemotePid());

                    for (;;) {
                        // recv request
                        String req;
                        try (DefaultShmemChannel.Read read = conn.read(5, TimeUnit.SECONDS)) {
                            req = getStringUTF8(read.getBuffer());
                            log.debug("Received: {}", req);
                        }

                        // send response
                        try (DefaultShmemChannel.Write write = conn.write(5, TimeUnit.SECONDS)) {
                            String resp = req + " World!";
                            putStringUTF8(write.getBuffer(), resp);
                            log.debug("Sending: {}", resp);
                        }
                    }
                } catch (ClosedChannelException e) {
                    log.info("Closed connection {}", channel.getAddress());
                }
            }
        } catch (ShmemDestroyedException e) {
            log.info("Destroyed channel {}", address);
        }

        log.info("Done. Exiting.");
    }

}