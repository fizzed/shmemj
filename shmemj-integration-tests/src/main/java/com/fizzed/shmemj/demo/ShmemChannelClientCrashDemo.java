package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.demo.DemoHelper.*;

public class ShmemChannelClientCrashDemo {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelClientCrashDemo.class);

    static public void main(String[] args) throws Exception {
        final Path address = temporaryFile("shmem_channel_demo.sock");

        try (final ShmemClientChannel channel = new ShmemChannelFactory().setAddress(address).setDestroyOnExit(false).createClientChannel()) {
            log.info("Connecting to channel {}", channel.getAddress());

            try (final ShmemChannelConnection conn = channel.connect(5, TimeUnit.SECONDS)) {

                log.info("Connected with process pid={}", conn.getRemotePid());

                // send request
                try (ShmemChannel.Write write = conn.write(5, TimeUnit.SECONDS)) {
                    String s = "Hello";
                    putStringUTF8(write.getBuffer(), s);
                    log.debug("Sending: {}", s);
                }

                System.exit(5);
            }
        } catch (ShmemClosedConnectionException e) {
            log.info("Channel closed");
        }

        log.info("Done. Exiting.");
    }

}