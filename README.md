# Shmemj (Shared Memory for Java) by Fizzed

[![Maven Central](https://img.shields.io/maven-central/v/com.fizzed/shmemj?color=blue&style=flat-square)](https://mvnrepository.com/artifact/com.fizzed/shmemj)

[![Java 11](https://img.shields.io/github/actions/workflow/status/fizzed/shmemj/java11.yaml?branch=master&label=Java%2011&style=flat-square)](https://github.com/fizzed/shmemj/actions/workflows/java11.yaml)
[![Java 17](https://img.shields.io/github/actions/workflow/status/fizzed/shmemj/java17.yaml?branch=master&label=Java%2017&style=flat-square)](https://github.com/fizzed/shmemj/actions/workflows/java17.yaml)
[![Java 19](https://img.shields.io/github/actions/workflow/status/fizzed/shmemj/java21.yaml?branch=master&label=Java%2021&style=flat-square)](https://github.com/fizzed/shmemj/actions/workflows/java21.yaml)

## Overview

Access and use shared memory from the host operating system in Java 11+ on a wide variety of operating systems. Extremely
fast and efficient method of IPC (interprocess communication) between Java-to-Java processes or even Java-to-other 
processes written in different languages.

 - Lightweight wrappers around shared memory APIs in an OS agnostic way
 - Lightweight wrapper around OS synchronization primitives including conditions with lock and atomic variable implementations
 - Native binding for Java are written in Rust
 - Thorough unit tests along with automated testing across operating systems and versions
 - Sophisticated ```ShmemChannel``` which provides a socket-like interface for communicating between Java programs
 - Supports Java 11+ (could support earlier versions but this lib uses the new process id utilities added in Java 9)

## Sponsorship & Support

![](https://cdn.fizzed.com/github/fizzed-logo-100.png)

Project by [Fizzed, Inc.](http://fizzed.com) (Follow on Twitter: [@fizzed_inc](http://twitter.com/fizzed_inc))

**Developing and maintaining opensource projects requires significant time.** If you find this project useful or need
commercial support, we'd love to chat. Drop us an email at [ping@fizzed.com](mailto:ping@fizzed.com)

Project sponsors may include the following benefits:

- Priority support (outside of Github)
- Feature development & roadmap
- Priority bug fixes
- Privately hosted continuous integration tests for their unique edge or use cases

## Performance

```ShmemChannel``` is up to 2-3x faster on linux compared to TCP/Unix Domain sockets, 5-6x faster on Windows, and
almost 9-10x faster on MacOS.

## Usage

Add the following to your maven POM file for Linux x64

```xml
<dependency>
  <groupId>com.fizzed</groupId>
  <artifactId>shmemj-linux-x64</artifactId>
  <version>VERSION-HERE</version>
</dependency>
```

Or MacOS arm64 (Apple silicon)

```xml
<dependency>
  <groupId>com.fizzed</groupId>
  <artifactId>shmemj-macos-arm64</artifactId>
  <version>VERSION-HERE</version>
</dependency>
```

Or for all operating system & arches

```xml
<dependency>
  <groupId>com.fizzed</groupId>
  <artifactId>shmemj-all-natives</artifactId>
  <version>VERSION-HERE</version>
</dependency>
```

To simplify versions, you may optionally want to import our BOM (bill of materials)

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.fizzed</groupId>
            <artifactId>shmemj-bom</artifactId>
            <version>VERSION-HERE</version>
            <scope>import</scope>
            <type>pom</type>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The easiest way to see this library in action is to peruse the various demos and benchmarks: https://github.com/fizzed/shmemj/tree/master/shmemj-integration-tests/src/main/java/com/fizzed/shmemj/demo

To use shared memory, use the factory to build one:

```java
import com.fizzed.shmemj.Shmem;
import com.fizzed.shmemj.ShmemFactory;

... other code

Shmem shmem = new ShmemFactory()
    .setSize(2048L)
    .create();

ByteBuffer buf = shmem.newByteBuffer(0, 30);
buf.putDouble(5.4d);
buf.putDouble(3.12345d);
buf.putDouble(3.12345d);
```

To use the shared memory channel (a socket-like class):

```java
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

        try (final ShmemServerChannel channel = new ShmemChannelFactory().setSize(4096L).setAddress(address).setSpinLocks(true).createServerChannel()) {
            for (;;) {
                log.info("Listening on channel {} (as pid {})", channel.getAddress(), ProcessProvider.DEFAULT.getCurrentPid());

                try (final ShmemChannelConnection conn = channel.accept(120, TimeUnit.SECONDS)) {

                    log.info("Connected with process pid={}", conn.getRemotePid());

                    for (;;) {
                        // recv request
                        String req;
                        try (ShmemChannel.Read read = conn.read(5, TimeUnit.SECONDS)) {
                            req = getStringUTF8(read.getBuffer());
                            log.debug("Received: {}", req);
                        }

                        // send response
                        try (ShmemChannel.Write write = conn.write(5, TimeUnit.SECONDS)) {
                            String resp = req + " World!";
                            putStringUTF8(write.getBuffer(), resp);
                            log.debug("Sending: {}", resp);
                        }
                    }
                } catch (ShmemClosedConnectionException e) {
                    log.info("Closed connection {}: error={}", channel.getAddress(), e.getMessage());
                }
            }
        } catch (ShmemDestroyedException e) {
            log.info("Destroyed channel {}", address);
        }

        log.info("Done. Exiting.");
    }

}
```

## Native Libs

| Platform | Artifact | Notes |
| :--------------- | :----------- | :---- |
| freebsd x64 | shmemj-freebsd-x64 | freebsd 12+ |
| linux arm64 | shmemj-linux-arm64 | built on ubuntu 16.04, glibc 2.23+ |
| linux armel | shmemj-linux-armel | built on ubuntu 16.04, glibc 2.23+ |
| linux armhf | shmemj-linux-armhf | built on ubuntu 16.04, glibc 2.23+ |
| linux riscv64 | shmemj-linux-riscv64 | built on ubuntu 18.04, glibc 2.31+ |
| linux x32 | shmemj-linux-x32 | built on ubuntu 16.04, glibc 2.23+ |
| linux x64 | shmemj-linux-x64 | built on ubuntu 16.04, glibc 2.23+ |
| linux_musl x64 | shmemj-linux_musl-x64 | alpine 3.11+ |
| macos arm64 | shmemj-macos-arm64 | macos 11+ |
| macos x64 | shmemj-macos-x64 | macos 10.13+ |
| windows arm64 | shmemj-windows-arm64 | win 10+ |
| windows x32 | shmemj-windows-x32 | win 7+ |
| windows x64 | shmemj-windows-x64 | win 7+ |

## Development

We leverage Rust for the native implementation.  If you need to hack on the Rust code, install the rust toolchain along
with cargo.

    java -jar blaze.jar build_natives

To run tests

    mvn test

### Cross Building

We use a simple, yet quite sophisticated build system for fast, local builds across operating system and architectures.

For linux targets, we leverage docker containers either running locally on an x86_64 host, or remotely on dedicated
build machines running on arm64, macos x64, and macos arm64.

To build containers, you'll want to edit setup/blaze.java and comment out/edit which platforms you'd like to build for,
or potentially change them running on a remote machine via SSH.  Once you're happy with what you want to build for:

     java -jar blaze.jar cross_build_containers
     java -jar blaze.jar cross_build_natives
     java -jar blaze.jar cross_tests

For information on registering your x86_64 host to run other architectures (e.g. riscv64 or aarch64), please see
the readme for https://github.com/fizzed/buildx

You need to install the target for rust to compile with.  On windows:

    rustup target add x86_64-pc-windows-msvc
    rustup target add i686-pc-windows-msvc
    rustup target add aarch64-pc-windows-msvc

On macos:

    rustup target add x86_64-apple-darwin
    rustup target add aarch64-apple-darwin

## License

Copyright (C) 2023 Fizzed, Inc.

This work is licensed under the Apache License, Version 2.0. See LICENSE for details.