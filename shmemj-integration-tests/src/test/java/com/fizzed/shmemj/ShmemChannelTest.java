package com.fizzed.shmemj;

import com.fizzed.crux.util.WaitFor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class ShmemChannelTest {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannelTest.class);

    //
    // Helpers
    //

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private ProcessProvider serverProcessProvider;
    private ProcessProvider clientProcessProvider;

    @BeforeEach
    public void beforeEach() {
        this.serverProcessProvider = mock(ProcessProvider.class);
        doReturn(12345L).when(this.serverProcessProvider).getCurrentPid();
        this.clientProcessProvider = mock(ProcessProvider.class);
        doReturn(98765L).when(this.clientProcessProvider).getCurrentPid();
    }

    public interface Async {
        void apply() throws Exception;
    }

    public interface AsyncResult<T> {
        T apply() throws Exception;
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

    public <T> Future<T> asyncResult(AsyncResult<T> asyncResult) {
        return this.executor.submit(() -> {
            try {
                return asyncResult.apply();
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

    public <T> T awaitSecs(Future<T> future, int secs) throws TimeoutException, InterruptedException, ExecutionException {
        return future.get(secs, TimeUnit.SECONDS);
    }

    public interface CreateChannelsConsumer {
        void apply(ShmemServerChannel serverChannel, ShmemClientChannel clientChannel) throws Exception;
    }

    public interface ConnectChannelsConsumer {
        void apply(ShmemChannelConnection serverConn, ShmemChannelConnection clientConn) throws Exception;
    }

    public void createChannels(CreateChannelsConsumer consumer) throws Exception {
        this.createChannels(2048L, true, consumer);
    }

    public void createChannels(long size, boolean spinLocks, CreateChannelsConsumer consumer) throws Exception {
        final Shmem serverShmem = new ShmemFactory()
            .setSize(size)
            .create();

        final ShmemServerChannel serverChannel = DefaultShmemChannel.create(this.serverProcessProvider, serverShmem, spinLocks);

        final Shmem clientShmem = new ShmemFactory()
            .setOsId(serverShmem.getOsId())
            .open();

        final ShmemClientChannel clientChannel = DefaultShmemChannel.existing(this.clientProcessProvider, clientShmem);

        consumer.apply(serverChannel, clientChannel);

        // always cleanup
        clientChannel.close();
        serverChannel.close();
    }

    private void connectChannels(ShmemServerChannel serverChannel, ShmemClientChannel clientChannel, ConnectChannelsConsumer consumer) throws Exception {
        final Future<ShmemChannelConnection> acceptFuture = this.asyncResult(() -> {
            return serverChannel.accept(2, TimeUnit.SECONDS);
        });

        // waitfor serverPid to be populated
        WaitFor.requireMillis(() -> serverChannel.getServerPid() > 0, 2000L, 10L);

        // client can now connect
        ShmemChannelConnection clientConn = clientChannel.connect(3, TimeUnit.SECONDS);

        ShmemChannelConnection serverConn = acceptFuture.get(4, TimeUnit.SECONDS);

        consumer.apply(serverConn, clientConn);

        // always cleanup
        clientConn.close();
        serverConn.close();
    }

    //
    // Test methods
    //

    @Test
    public void create() throws Exception {
        try (final ShmemChannel channel = new ShmemChannelFactory().setSize(2048L).setSpinLocks(true).createServerChannel()) {
            assertThat(channel.getServerPid(), is(0L));
            assertThat(channel.getClientPid(), is(0L));
            assertThat(channel.isSpinLocks(), is(true));
        }
    }

    @Test
    public void existing() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            assertThat(serverChannel.isServer(), is(true));
            assertThat(serverChannel.getServerPid(), is(0L));
            assertThat(serverChannel.getClientPid(), is(0L));
            assertThat(serverChannel.isSpinLocks(), is(true));

            assertThat(clientChannel.isServer(), is(false));
            assertThat(clientChannel.getServerPid(), is(0L));
            assertThat(clientChannel.getClientPid(), is(0L));
            assertThat(clientChannel.isSpinLocks(), is(true));
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
            final ShmemChannel channel = DefaultShmemChannel.existing(this.serverProcessProvider, clientShmem);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), containsString("unexpected magic value"));
        } finally {
            clientShmem.close();
            serverShmem.close();
        }
    }

    @Test
    public void destroyingShmemInvalidatesNativeCalls() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            // closing the shared memory makes the condition impossible to use (its methods should fail, not segfault)
            serverChannel.getShmem().close();
            clientChannel.getShmem().close();

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
                clientChannel.connect(1, TimeUnit.SECONDS);
                fail();
            } catch (ShmemDestroyedException e) {
                 // expected
            }
        });
    }

    @Test
    public void accept() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
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
                ShmemChannelConnection conn = serverChannel.accept(3, TimeUnit.SECONDS);

                assertThat(conn.getRemotePid(), is(serverChannel.getClientPid()));
            });

            final ShmemChannelConnection conn = clientChannel.connect(5, TimeUnit.SECONDS);

            assertThat(conn.getRemotePid(), is(serverChannel.getServerPid()));

            acceptFuture.get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    public void acceptCloseAccept() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            // start server accept
            final Future<ShmemChannelConnection> acceptFuture1 = this.asyncResult(() -> {
                ShmemChannelConnection conn = serverChannel.accept(2, TimeUnit.SECONDS);
                assertThat(conn.getRemotePid(), greaterThan(0L));
                return conn;
            });

            // client connects
            final ShmemChannelConnection clientConn = clientChannel.connect(5, TimeUnit.SECONDS);

            // server accept should finish
            final ShmemChannelConnection serverConn = this.awaitSecs(acceptFuture1, 5);

            clientConn.close();
            serverConn.close();

            // server should be able to "accept" again
            final Future<?> acceptFuture2 = this.async(() -> {
                ShmemChannelConnection conn = serverChannel.accept(2, TimeUnit.SECONDS);
                assertThat(conn.getRemotePid(), greaterThan(0L));
            });

            // client connects again
            clientChannel.connect(5, TimeUnit.SECONDS);

            // server accept should finish
            this.awaitSecs(acceptFuture2, 5);
        });
    }

    @Test
    public void connect() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
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
                ShmemChannelConnection conn = clientChannel.connect(3, TimeUnit.SECONDS);

                assertThat(conn.getRemotePid(), is(serverChannel.getServerPid()));
            });

            final ShmemChannelConnection conn = serverChannel.accept(5, TimeUnit.SECONDS);

            assertThat(conn.getRemotePid(), is(serverChannel.getClientPid()));

            connectFuture.get(5, TimeUnit.SECONDS);
        });
    }

    @Test
    public void selfClose() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {
                // we will close ourselves now and then try to write
                serverConn.close();

                try {
                    serverConn.write(5, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemClosedConnectionException e) {
                    // expected
                }

                try {
                    serverConn.read(5, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemClosedConnectionException e) {
                    // expected
                }

                // these should both work
                serverConn.close();
                clientConn.close();
            }));
        });
    }

    @Test
    public void otherClose() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {
                // we will close ourselves now and then try to write from the other side
                serverConn.close();

                try {
                    clientConn.write(5, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemClosedConnectionException e) {
                    // expected
                }

                try {
                    clientConn.read(5, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemClosedConnectionException e) {
                    // expected
                }

                // these should both work
                serverConn.close();
                clientConn.close();
            }));
        });
    }

    @Test
    public void closeUnblocksAccept() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            // server will block on accepting
            final CountDownLatch acceptWaitLatch = new CountDownLatch(1);
            final Future<?> acceptFuture = this.async(() -> {
                try {
                    acceptWaitLatch.countDown();
                    serverChannel.accept(2, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemDestroyedException e) {
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
        });
    }

    @Test
    public void closeUnblocksConnect() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            // client will block on connecting
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
            // TODO: any other way to guarantee await()?
            Thread.sleep(500L);

            // owner closes (should unblock itself & client)
            clientChannel.close();

            this.awaitSecs(connectFuture, 5);
        });
    }

    @Test
    public void serverCloseUnblocksReads() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {
                // server will block on reading
                final CountDownLatch serverReadWaitLatch = new CountDownLatch(1);
                final Future<?> serverReadFuture = this.async(() -> {
                    try {
                        serverReadWaitLatch.countDown();
                        serverConn.read(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ShmemClosedConnectionException e) {
                        // expected
                    }
                });

                // client will block on reading
                final CountDownLatch clientReadWaitLatch = new CountDownLatch(1);
                final Future<?> clientReadFuture = this.async(() -> {
                    try {
                        clientReadWaitLatch.countDown();
                        clientConn.read(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ShmemClosedConnectionException e) {
                        // expected
                    }
                });

                // wait till server & client are reading & waiting (as best we can)
                this.awaitSecs(serverReadWaitLatch, 5);
                this.awaitSecs(clientReadWaitLatch, 5);
                // TODO: any other way to guarantee clientChannel.read is await()?
                Thread.sleep(500L);

                // server closes (should unblock itself & client)
                serverConn.close();

                this.awaitSecs(serverReadFuture, 5);
                this.awaitSecs(clientReadFuture, 5);
            }));
        });
    }

    @Test
    public void serverCloseUnblocksClientWrite() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {
                // since client is ready to write, we need to write once, so the next write will block
                serverConn.write(2, TimeUnit.SECONDS).close();
                clientConn.write(2, TimeUnit.SECONDS).close();

                // owner will block on writing again
                final CountDownLatch ownerWriteWaitLatch = new CountDownLatch(1);
                final Future<?> ownerWriteFuture = this.async(() -> {
                    try {
                        ownerWriteWaitLatch.countDown();
                        serverConn.write(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ShmemClosedConnectionException e) {
                        // expected
                    }
                });

                // client will block on writing again
                final CountDownLatch clientWriteWaitLatch = new CountDownLatch(1);
                final Future<?> clientWriteFuture = this.async(() -> {
                    try {
                        clientWriteWaitLatch.countDown();
                        clientConn.write(2, TimeUnit.SECONDS).close();
                        fail();
                    } catch (ShmemClosedConnectionException e) {
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
                serverConn.close();

                this.awaitSecs(ownerWriteFuture, 5);
                this.awaitSecs(clientWriteFuture, 5);
            }));
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelAccept() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
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
            serverChannel.getShmem().close();
            clientChannel.getShmem().close();

            this.awaitSecs(connectFuture, 5);
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelConnect() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
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
            clientChannel.getShmem().close();
            serverChannel.getShmem().close();

            this.awaitSecs(connectFuture, 5);
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelRead() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, (serverConn, clientConn) -> {
                final CountDownLatch readWaitLatch = new CountDownLatch(1);
                final Future<?> readFuture = this.async(() -> {
                    try {
                        readWaitLatch.countDown();
                        serverConn.read(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ShmemClosedConnectionException | ShmemDestroyedException e) {
//                        log.debug("Destroyed");
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(readWaitLatch, 5);
                Thread.yield();
                Thread.sleep(500L);

                // server closes (should unblock itself & client)
//                log.debug("Closing clientShmem");
                clientChannel.getShmem().close();
//                log.debug("Closing serverShmem");
                serverChannel.getShmem().close();

//                log.debug("Awaiting now...");
                this.awaitSecs(readFuture, 5);
            });
        });
    }

    @Test
    public void destroyingShmemCanSegfaultChannelWrite() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {
                // write, so we'll block trying to write again
                serverConn.write(2, TimeUnit.SECONDS).close();

                final CountDownLatch writeWaitLatch = new CountDownLatch(1);
                final Future<?> writeFuture = this.async(() -> {
                    try {
                        writeWaitLatch.countDown();
                        serverConn.write(2, TimeUnit.SECONDS);
                        fail();
                    } catch (ShmemClosedConnectionException | ShmemDestroyedException e) {
                        // expected
                    }
                });

                // wait till owner & client are reading & waiting (as best we can)
                this.awaitSecs(writeWaitLatch, 5);
                Thread.yield();
                Thread.sleep(500L);

                // owner closes (should unblock itself & client)
                clientChannel.getShmem().close();
                serverChannel.getShmem().close();

                this.awaitSecs(writeFuture, 5);
            }));
        });
    }

    @Test
    public void readFailureIfClientProcessDies() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {

                // mimic the client crashing by saying its process is no longer "alive"
                long clientPid = this.clientProcessProvider.getCurrentPid();
                doReturn(false).when(this.serverProcessProvider).isAlive(eq(clientPid));

                try {
                    serverConn.read(2, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemClosedConnectionException e) {
                    // expected
                    assertThat(e.getCause(), instanceOf(ShmemProcessDiedException.class));
                }

                assertThat(serverChannel.isClosed(), is(false));
                assertThat(clientChannel.isClosed(), is(false));
                assertThat(serverConn.isClosed(), is(true));
                assertThat(clientConn.isClosed(), is(true));
            }));
        });
    }

    @Test
    public void writeFailureIfClientProcessDies() throws Exception {
        this.createChannels((serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {

                // write something (so that the next write blocks)
                try (ShmemChannel.Write write = serverConn.write(1, TimeUnit.SECONDS)) {
                    write.getBuffer().putInt(1);
                }

                // mimic the client crashing by saying its process is no longer "alive"
                long clientPid = this.clientProcessProvider.getCurrentPid();
                doReturn(false).when(this.serverProcessProvider).isAlive(eq(clientPid));

                try {
                    serverConn.write(2, TimeUnit.SECONDS);
                    fail();
                } catch (ShmemClosedConnectionException e) {
                    // expected
                    assertThat(e.getCause(), instanceOf(ShmemProcessDiedException.class));
                }

                assertThat(serverChannel.isClosed(), is(false));
                assertThat(clientChannel.isClosed(), is(false));
                assertThat(serverConn.isClosed(), is(true));
                assertThat(clientConn.isClosed(), is(true));
            }));
        });
    }

    @Test
    public void writeAndReadBufferSizes() throws Exception {
        // IMPORTANT: macos will not honor the 5K and round up to the nearest block size on the SHMEM that the remote
        // process uses.  This will throw off where the "read" buffer starts between processes
        this.createChannels(5000, true, (serverChannel, clientChannel) -> {
            this.connectChannels(serverChannel, clientChannel, ((serverConn, clientConn) -> {
                log.debug("server shmem size: {}", serverChannel.getShmem().getSize());
                log.debug("client shmem size: {}", clientChannel.getShmem().getSize());

                assertThat(serverChannel.getWriteBufferSize(), is(clientChannel.getReadBufferSize()));
                assertThat(serverChannel.getReadBufferSize(), is(clientChannel.getWriteBufferSize()));
            }));
        });
    }

}