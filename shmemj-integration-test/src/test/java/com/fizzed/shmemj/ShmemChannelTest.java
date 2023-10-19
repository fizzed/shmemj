package com.fizzed.shmemj;

import com.fizzed.crux.util.WaitFor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.sql.Time;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.fail;

public class ShmemChannelTest {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelTest.class);

    //
    // Helpers
    //

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    static public interface Async {
        public void apply() throws Exception;
    }

    public Future<?> async(Async async) {
        return this.executor.submit(() -> {
            try {
                async.apply();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
    }

    public void awaitSecs(CountDownLatch latch, int secs) throws TimeoutException, InterruptedException {
        if (!latch.await(secs, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
    }

    public void awaitSecs(Future<?> future, int secs) throws TimeoutException, InterruptedException, ExecutionException {
        future.get(secs, TimeUnit.SECONDS);
    }

    static public interface Consumer {
        void apply(Shmem ownerShmem, ShmemChannel ownerChannel, Shmem clientShmem, ShmemChannel clientChannel) throws Exception;
    }

    public void createChannels(Consumer consumer) throws Exception {
        this.createChannels(2048L, true, consumer);
    }

    public void createChannels(long size, boolean spinLocks, Consumer consumer) throws Exception {
        final Shmem ownerShmem = new ShmemFactory()
            .setSize(size)
            .create();

        final ShmemChannel ownerChannel = new ShmemChannelFactory().setShmem(ownerShmem).setSpinLocks(spinLocks).create();

        final Shmem clientShmem = new ShmemFactory()
            .setOsId(ownerShmem.getOsId())
            .open();

        final ShmemChannel clientChannel = new ShmemChannelFactory().setShmem(clientShmem).existing();

        consumer.apply(ownerShmem, ownerChannel, clientShmem, clientChannel);
    }

    private void connectChannels(ShmemChannel serverChannel, ShmemChannel clientChannel) throws Exception {
        final Future<?> acceptFuture = this.async(() -> {
            serverChannel.accept(2, TimeUnit.SECONDS);
        });

        // waitfor serverPid to be populated
        WaitFor.requireMillis(() -> serverChannel.getServerPid() > 0, 2000L, 10L);

        // client can now connect
        clientChannel.connect(3, TimeUnit.SECONDS);

        acceptFuture.get(4, TimeUnit.SECONDS);
    }

    //
    // Test methods
    //

    @Test
    public void create() throws Exception {
        final Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        final ShmemChannel channel = new ShmemChannelFactory().setShmem(shmem).setSpinLocks(true).create();

        try {
            assertThat(channel.getServerPid(), is(0L));
            assertThat(channel.getClientPid(), is(0L));
            assertThat(channel.isSpinLocks(), is(true));
        } finally {
            shmem.close();
        }
    }

    @Test
    public void existing() throws Exception {
        this.createChannels((ownerShmem, ownerChannel, clientShmem, clientChannel) -> {
            try {
                assertThat(ownerChannel.isServer(), is(true));
                assertThat(ownerChannel.getServerPid(), is(0L));
                assertThat(ownerChannel.getClientPid(), is(0L));
                assertThat(ownerChannel.isSpinLocks(), is(true));

                assertThat(clientChannel.isServer(), is(false));
                assertThat(clientChannel.getServerPid(), is(0L));
                assertThat(clientChannel.getClientPid(), is(0L));
                assertThat(clientChannel.isSpinLocks(), is(true));
            } finally {
                clientShmem.close();
                ownerShmem.close();
            }
        });
    }

    @Test
    public void existingWithNonInitializedShmem() throws Exception {
        final Shmem serverShmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        final Shmem clientShmem = new ShmemFactory()
            .setOsId(serverShmem.getOsId())
            .open();

        try {
            // try to "existing" which is a non-initialized shmem
            final ShmemChannel channel = new ShmemChannelFactory()
                .setShmem(clientShmem)
                .existing();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), containsString("unexpected magic value"));
        } finally {
            clientShmem.close();
            serverShmem.close();
        }
    }

    @Test
    public void destroyingShmemInvalidatesNativeCalls() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                // closing the shared memory makes the condition impossible to use (its methods should fail, not segfault)
                serverShmem.close();

                try {
                    serverChannel.getServerPid();
                    fail();
                } catch (ShmemDestroyedException e) {
                    // expected
                }

                try {
                    serverChannel.getClientPid();
                    fail();
                } catch (ShmemDestroyedException e) {
                    // expected
                }

                try {
                    serverChannel.isSpinLocks();
                    fail();
                } catch (ShmemDestroyedException e) {
                    // expected
                }

                try {
                    serverChannel.accept(1, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemDestroyedException e) {
                    // expected
                }

                try {
                    serverChannel.connect(1, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemDestroyedException e) {
                     // expected
                }

                try {
                    serverChannel.read(1, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemDestroyedException e) {
                    // expected
                }

                try {
                    serverChannel.write(1, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemDestroyedException e) {
                    // expected
                }
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void accept() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                // client is not allowed to "accept"
                try {
                    clientChannel.accept(2, TimeUnit.SECONDS);
                    fail();
                } catch (IllegalStateException e) {
                    // expected
                }

                // accept should timeout if no client connects
                try {
                    serverChannel.accept(50L, TimeUnit.MILLISECONDS);
                    fail();
                } catch (TimeoutException e) {
                    // expected
                }

                // we should not allow accept to have side effects of making us look like we're ready

                assertThat(serverChannel.getServerPid(), is(0L));
                // the client connect should timeout
                try {
                    clientChannel.connect(50L, TimeUnit.MILLISECONDS);
                    fail();
                } catch (TimeoutException e) {
                    // expected
                }

                // async mimic an owner accepting connections
                final Future<?> acceptFuture = this.async(() -> {
                    long clientPid = serverChannel.accept(3, TimeUnit.SECONDS);

                    assertThat(clientPid, is(serverChannel.getServerPid()));
                });

                final long serverPid = clientChannel.connect(5, TimeUnit.SECONDS);

                assertThat(serverPid, is(serverChannel.getServerPid()));

                acceptFuture.get(5, TimeUnit.SECONDS);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void acceptCloseAccept() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                // start server accept
                final Future<?> acceptFuture1 = this.async(() -> {
                    long clientPid = serverChannel.accept(2, TimeUnit.SECONDS);
                    assertThat(clientPid, greaterThan(0L));
                });

                // client connects
                clientChannel.connect(5, TimeUnit.SECONDS);

                // server accept should finish
                this.awaitSecs(acceptFuture1, 5);

                clientChannel.close();
                serverChannel.close();

                // server should be able to "accept" again
                final Future<?> acceptFuture2 = this.async(() -> {
                    long clientPid = serverChannel.accept(2, TimeUnit.SECONDS);
                    assertThat(clientPid, greaterThan(0L));
                });

                // client connects again
                clientChannel.connect(5, TimeUnit.SECONDS);

                // server accept should finish
                this.awaitSecs(acceptFuture2, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void connect() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                // owner is not allowed to "connect"
                try {
                    serverChannel.connect(2, TimeUnit.SECONDS);
                    fail();
                } catch (IllegalStateException e) {
                    // expected
                }

                // connect should timeout if no owner accepts
                try {
                    clientChannel.connect(50L, TimeUnit.MILLISECONDS);
                    fail();
                } catch (TimeoutException e) {
                    // expected
                }

                // we should not allow accept to have side effects of making us look like we're ready
                // owner pid should be zero
                assertThat(clientChannel.getClientPid(), is(0L));
                // the owner accept should timeout
                try {
                    serverChannel.accept(50L, TimeUnit.MILLISECONDS);
                    fail();
                } catch (TimeoutException e) {
                    // expected
                }

                // async mimic a client connecting
                final Future<?> connectFuture = this.async(() -> {
                    long ownerPid = clientChannel.connect(3, TimeUnit.SECONDS);

                    assertThat(ownerPid, is(serverChannel.getServerPid()));
                });

                final long ownerPid = serverChannel.accept(5, TimeUnit.SECONDS);

                assertThat(ownerPid, is(serverChannel.getServerPid()));

                connectFuture.get(5, TimeUnit.SECONDS);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void selfClose() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                this.connectChannels(serverChannel, clientChannel);

                // we will close ourselves now and then try to write to ourselves
                serverChannel.close();

                try {
                    serverChannel.write(5, TimeUnit.SECONDS);
                    fail();
                } catch (ClosedChannelException e) {
                    // expected
                }

                try {
                    serverChannel.read(5, TimeUnit.SECONDS);
                    fail();
                } catch (ClosedChannelException e) {
                    // expected
                }

                // these should both work
                serverChannel.close();
                clientChannel.close();

            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void otherClose() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                this.connectChannels(serverChannel, clientChannel);

                // we will close ourselves now and then try to write from the other side
                serverChannel.close();

                try {
                    clientChannel.write(5, TimeUnit.SECONDS);
                    fail();
                } catch (ClosedChannelException e) {
                    // expected
                }

                try {
                    clientChannel.read(5, TimeUnit.SECONDS);
                    fail();
                } catch (ClosedChannelException e) {
                    // expected
                }

                // these should both work
                serverChannel.close();
                clientChannel.close();
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void closeUnblocksAccept() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                // owner will block on accepting
                final CountDownLatch acceptWaitLatch = new CountDownLatch(1);
                final Future<?> acceptFuture = this.async(() -> {
                    try {
                        acceptWaitLatch.countDown();
                        serverChannel.accept(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ClosedChannelException e) {
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(acceptWaitLatch, 5);
                // TODO: any other way to guarantee await()?
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                serverChannel.close();

                this.awaitSecs(acceptFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void closeUnblocksConnect() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                // client will block on connecting
                final CountDownLatch connectWaitLatch = new CountDownLatch(1);
                final Future<?> connectFuture = this.async(() -> {
                    try {
                        connectWaitLatch.countDown();
                        clientChannel.connect(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ClosedChannelException e) {
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(connectWaitLatch, 5);
                // TODO: any other way to guarantee await()?
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                clientChannel.close();

                this.awaitSecs(connectFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void serverCloseUnblocksReads() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                this.connectChannels(serverChannel, clientChannel);

                // owner will block on reading
                final CountDownLatch ownerReadWaitLatch = new CountDownLatch(1);
                final Future<?> ownerReadFuture = this.async(() -> {
                    try {
                        ownerReadWaitLatch.countDown();
                        serverChannel.read(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ClosedChannelException e) {
                        // expected
                    }
                });

                // client will block on reading
                final CountDownLatch clientReadWaitLatch = new CountDownLatch(1);
                final Future<?> clientReadFuture = this.async(() -> {
                    try {
                        clientReadWaitLatch.countDown();
                        clientChannel.read(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ClosedChannelException e) {
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(ownerReadWaitLatch, 5);
                this.awaitSecs(clientReadWaitLatch, 5);
                // TODO: any other way to guarantee clientChannel.read is await()?
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                serverChannel.close();

                this.awaitSecs(ownerReadFuture, 5);
                this.awaitSecs(clientReadFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void serverCloseUnblocksClientWrite() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                this.connectChannels(serverChannel, clientChannel);

                // since client is ready to write, we need to write once, so the next write will block
                serverChannel.write(2, TimeUnit.SECONDS).close();
                clientChannel.write(2, TimeUnit.SECONDS).close();

                // owner will block on writing again
                final CountDownLatch ownerWriteWaitLatch = new CountDownLatch(1);
                final Future<?> ownerWriteFuture = this.async(() -> {
                    try {
                        ownerWriteWaitLatch.countDown();
                        serverChannel.write(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ClosedChannelException e) {
                        // expected
                    }
                });

                // client will block on writing again
                final CountDownLatch clientWriteWaitLatch = new CountDownLatch(1);
                final Future<?> clientWriteFuture = this.async(() -> {
                    try {
                        clientWriteWaitLatch.countDown();
                        clientChannel.write(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ClosedChannelException e) {
                        // expected
                    }
                });

                // wait till client is reading & waiting (as best we can)
                this.awaitSecs(ownerWriteWaitLatch, 5);
                this.awaitSecs(clientWriteWaitLatch, 5);
                Thread.yield();
                // TODO: any other way to guarantee clientChannel.read is await()?
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                serverChannel.close();

                this.awaitSecs(ownerWriteFuture, 5);
                this.awaitSecs(clientWriteFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelAccept() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                final CountDownLatch connectWaitLatch = new CountDownLatch(1);
                final Future<?> connectFuture = this.async(() -> {
                    try {
                        connectWaitLatch.countDown();
                        serverChannel.accept(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ShmemDestroyedException e) {
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(connectWaitLatch, 5);
                Thread.yield();
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                serverShmem.close();
                clientShmem.close();

                this.awaitSecs(connectFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelConnect() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                final CountDownLatch connectWaitLatch = new CountDownLatch(1);
                final Future<?> connectFuture = this.async(() -> {
                    try {
                        connectWaitLatch.countDown();
                        clientChannel.connect(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ShmemDestroyedException e) {
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(connectWaitLatch, 5);
                Thread.yield();
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                clientShmem.close();
                serverShmem.close();

                this.awaitSecs(connectFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelRead() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                this.connectChannels(serverChannel, clientChannel);

                final CountDownLatch readWaitLatch = new CountDownLatch(1);
                final Future<?> readFuture = this.async(() -> {
                    try {
                        readWaitLatch.countDown();
                        serverChannel.read(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ShmemDestroyedException e) {
                        log.debug("Destroyed");
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(readWaitLatch, 5);
                Thread.yield();
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                log.debug("Closing clientShmem");
                clientShmem.close();
                log.debug("Closing serverShmem");
                serverShmem.close();

                log.debug("Awaiting now...");
                this.awaitSecs(readFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelWrite() throws Exception {
        this.createChannels((serverShmem, serverChannel, clientShmem, clientChannel) -> {
            try {
                this.connectChannels(serverChannel, clientChannel);

                // write, so we'll block trying to write again
                serverChannel.write(2, TimeUnit.SECONDS).close();

                final CountDownLatch writeWaitLatch = new CountDownLatch(1);
                final Future<?> writeFuture = this.async(() -> {
                    try {
                        writeWaitLatch.countDown();
                        serverChannel.write(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ShmemDestroyedException e) {
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(writeWaitLatch, 5);
                Thread.yield();
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                clientShmem.close();
                serverShmem.close();

                this.awaitSecs(writeFuture, 5);
            } finally {
                clientShmem.close();
                serverShmem.close();
            }
        });
    }

}