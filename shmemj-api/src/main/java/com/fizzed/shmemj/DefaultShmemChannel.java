package com.fizzed.shmemj;

import com.fizzed.crux.util.WaitFor;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DefaultShmemChannel implements ShmemServerChannel, ShmemClientChannel {

    // probably best to keep control buffer as divisible by 8
    static private final int CONTROL_BUFFER_SIZE = 40;
    static private final int CONTROL_MAGIC_POS = 0;
    static private final int CONTROL_VERSION_POS = 1;
    static private final int CONTROL_SERVER_PID_POS = 2;
    static private final int CONTROL_CLIENT_PID_POS = 10;
    static private final int CONTROL_SPIN_LOCK_POS = 18;
    static private final int CONTROL_SERVER_BUFFER_SIZE_POS = 19;
    static private final int CONTROL_CLIENT_BUFFER_SIZE_POS = 27;

    static private final long NOT_CONNECTED_PID = 0L;
    static private final byte MAGIC = (byte)42;         // random value to detect this is most likely a shmem channel
    static private final byte VERSION_1_0 = (byte)10;   // safety of versioned channels in case of long running processes...
    static private final byte THREAD_LOCKS = (byte)0;
    static private final byte SPIN_LOCKS = (byte)1;

    static private class Control {

        private final ByteBuffer buffer;

        public Control(Shmem shmem, long offset) {
            this.buffer = shmem.newByteBuffer(offset, CONTROL_BUFFER_SIZE);
        }

        public long getSize() {
            return this.buffer.capacity();
        }

        public byte getMagic() {
            return this.buffer.get(CONTROL_MAGIC_POS);
        }

        public void setMagic(byte magic) {
            this.buffer.put(CONTROL_MAGIC_POS, magic);
        }

        public byte getVersion() {
            return this.buffer.get(CONTROL_VERSION_POS);
        }

        public void setVersion(byte version) {
            this.buffer.put(CONTROL_VERSION_POS, version);
        }

        public long getServerPid() {
            return this.buffer.getLong(CONTROL_SERVER_PID_POS);
        }

        public void setServerPid(long pid) {
            this.buffer.putLong(CONTROL_SERVER_PID_POS, pid);
        }

        public long getClientPid() {
            return this.buffer.getLong(CONTROL_CLIENT_PID_POS);
        }

        public void setClientPid(long pid) {
            this.buffer.putLong(CONTROL_CLIENT_PID_POS, pid);
        }

        public boolean isSpinLocks() {
            return this.buffer.get(CONTROL_SPIN_LOCK_POS) == SPIN_LOCKS;
        }

        public void setSpinLocks(boolean spinLocks) {
            this.buffer.put(CONTROL_SPIN_LOCK_POS, spinLocks ? SPIN_LOCKS : THREAD_LOCKS);
        }

        public long getServerBufferSize() {
            return this.buffer.getLong(CONTROL_SERVER_BUFFER_SIZE_POS);
        }

        public void setServerBufferSize(long pid) {
            this.buffer.putLong(CONTROL_SERVER_BUFFER_SIZE_POS, pid);
        }

        public long getClientBufferSize() {
            return this.buffer.getLong(CONTROL_CLIENT_BUFFER_SIZE_POS);
        }

        public void setClientBufferSize(long pid) {
            this.buffer.putLong(CONTROL_CLIENT_BUFFER_SIZE_POS, pid);
        }

    }

    abstract protected static class AbstractOp implements Closeable {

        final protected ByteBuffer buffer;

        public AbstractOp(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }
    }

    public class Read extends AbstractOp implements ShmemChannel.Read {

        public Read(ByteBuffer buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            DefaultShmemChannel.this.readEnd();
        }
    }

    public class Write extends AbstractOp implements ShmemChannel.Write {

        public Write(ByteBuffer buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            DefaultShmemChannel.this.writeEnd();
        }
    }

    private final Shmem shmem;
    private final String address;
    private final boolean server;
    private final ProcessProvider processProvider;
    private final Control control;
    private final ShmemCondition clientConnectCondition;;
    private final ShmemCondition serverWriteCondition;
    private final ShmemCondition serverReadCondition;
    private final ShmemCondition clientWriteCondition;
    private final ShmemCondition clientReadCondition;
    private final ByteBuffer serverBuffer;
    private final ByteBuffer clientBuffer;
    private final AtomicBoolean connecting;
    private final AtomicBoolean reading;
    private final AtomicBoolean writing;
    private boolean destroyed;

    private DefaultShmemChannel(Shmem shmem, ProcessProvider processProvider, Control control, ShmemCondition clientConnectCondition,
                                ShmemCondition serverWriteCondition, ShmemCondition serverReadCondition, ShmemCondition clientWriteCondition,
                                ShmemCondition clientReadCondition, ByteBuffer serverBuffer, ByteBuffer clientBuffer) {

        this.shmem = shmem;
        this.server = shmem.isOwner();
        this.processProvider = processProvider;
        this.control = control;
        this.clientConnectCondition = clientConnectCondition;
        this.serverWriteCondition = serverWriteCondition;
        this.serverReadCondition = serverReadCondition;
        this.clientWriteCondition = clientWriteCondition;
        this.clientReadCondition = clientReadCondition;
        this.serverBuffer = serverBuffer;
        this.clientBuffer = clientBuffer;
        this.connecting = new AtomicBoolean(false);
        this.reading = new AtomicBoolean(false);
        this.writing = new AtomicBoolean(false);
        this.destroyed = false;

        String flink = this.shmem.getFlink();
        if (flink != null) {
            this.address = "shmem://" + flink;
        } else {
            this.address = "shmem+osid://" + this.shmem.getOsId();
        }
    }

    @Override
    public Shmem getShmem() {
        return shmem;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public boolean isServer() {
        return this.server;
    }

    @Override
    public long getServerPid() {
        this.checkShmem(true);
        return this.control.getServerPid();
    }

    @Override
    public long getClientPid() {
        this.checkShmem(true);
        return this.control.getClientPid();
    }

    @Override
    public boolean isSpinLocks() {
        this.checkShmem(true);
        return this.control.isSpinLocks();
    }

    @Override
    public long getWriteBufferSize() {
        if (this.server) {
            return this.serverBuffer.capacity();
        } else {
            return this.clientBuffer.capacity();
        }
    }

    @Override
    public long getReadBufferSize() {
        if (this.server) {
            return this.clientBuffer.capacity();
        } else {
            return this.serverBuffer.capacity();
        }
    }

    public ShmemChannelConnection accept(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        this.checkShmem(true);

        // only servers can accept
        if (!this.server) {
            throw new IllegalStateException("Only channel owners are allowed to accept (did you mean to use connect?)");
        }

        // TODO: only allow 1 thread in at a time
        this.connecting.set(true);

        // clear all signals, reset everything
        this.clientConnectCondition.clear();
        this.serverWriteCondition.clear();
        this.serverReadCondition.clear();
        this.clientWriteCondition.clear();
        this.clientReadCondition.clear();

        try {
            // set the pid to indicate our end is ready (after this is done, a client can theoretically connect now)
            this.control.setServerPid(this.processProvider.getCurrentPid());

            try {
                // wait for the client to connect
                boolean signaled = this.clientConnectCondition.await(timeout, unit);
                if (!signaled) {
                    throw new TimeoutException();
                }

                // double check client is connected (we could have been signaled to close)
                this.checkConnectionClosed(true);

                // now we can signal that writes are allowed
                this.clientWriteCondition.signal();
                this.serverWriteCondition.signal();

                return new ShmemChannelConnection(this);
            } catch (TimeoutException e) {
                this.control.setServerPid(NOT_CONNECTED_PID);
                throw e;
            }
        } finally {
            this.connecting.set(false);
        }
    }

    public ShmemChannelConnection connect(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        this.checkShmem(true);

        // only clients can connect
        if (this.server) {
            throw new IllegalStateException("Only channel clients are allowed to connect (did you mean to use accept?)");
        }

        this.connecting.set(true);
        try {
            // set the pid to indicate our end is ready
            this.control.setClientPid(this.processProvider.getCurrentPid());

            // we could wait for the server pid OR someone closing this client
            if (!WaitFor.of(() -> this.control.getServerPid() > 0 || this.isClientConnectionClosed()).awaitMillis(unit.toMillis(timeout), 50L)) {
                throw new TimeoutException();
            }

            // double check client is connected (we could have been signaled to close)
            this.checkConnectionClosed(true);

            // signal the server we are ready
            this.clientConnectCondition.signal();

            // NOTE: it's possible that despite the signal above, the server accept() could have timed out, so we
            // have not really connected to it, we'll see if that's an issue
            // TODO: should we let the server signal us now? based on testing this does not seem to be an issue

            return new ShmemChannelConnection(this);
        } catch (Exception e) {
            this.control.setClientPid(NOT_CONNECTED_PID);
            throw e;
        } finally {
            this.connecting.set(false);
        }
    }

    protected boolean isConnectionClosed() {
        return this.isServerConnectionClosed() || this.isClientConnectionClosed();
    }

    protected boolean isServerConnectionClosed() {
        this.checkShmem(true);

        final long serverPid = this.control.getServerPid();

        return serverPid <= NOT_CONNECTED_PID;
    }

    protected boolean isClientConnectionClosed() {
        this.checkShmem(true);

        final long clientPid = this.control.getClientPid();

        return clientPid <= NOT_CONNECTED_PID;
    }

    protected void checkConnectionClosed(boolean includeDestroyed) throws IOException {
        this.checkShmem(includeDestroyed);

        if (this.isConnectionClosed()) {
            throw new ShmemClosedConnectionException("Connection closed");
        }
    }

    protected void checkShmem(boolean includeDestroyed) {
        if (includeDestroyed && this.destroyed) {
            throw new ShmemDestroyedException("Shmem channel was destroyed");
        }
        if (this.shmem.isDestroyed()) {
            throw new ShmemDestroyedException("Shared memory backing this channel is destroyed (you should ensure you have closed the channel before the shared memory!)");
        }
    }

    private Consumer<Long> createProcessDiedMonitor() {
        long remotePid = this.server ? this.getClientPid() : this.getServerPid();
        return new Consumer<>() {
            private long lastElaspedMillis = 0;
            @Override
            public void accept(Long elapsedMillis) {
                // only check every 1 sec so we're not doing this too frequently
                if ((elapsedMillis - lastElaspedMillis) >= 1000L) {
                    if (!DefaultShmemChannel.this.processProvider.isAlive(remotePid)) {
                        throw new ShmemProcessDiedException("Remote process " + remotePid + " either crashed or exited w/o properly closing this channel");
                    }
                    lastElaspedMillis = elapsedMillis;
                }
            }
        };
    }

    public boolean isClosed() {
        return this.destroyed;
    }

    @Override
    public void close() throws Exception {
        // prevent duplicate closes from doing anything (which would/could cause exceptions)
        if (this.destroyed) {
            return;
        }

        // mark that we are being destroyed
        this.destroyed = true;

        // delegate rest of destroying to close the connection
        this.closeConnection(true);

        this.shmem.unregisterResource(this);
    }

    public void closeConnection(boolean force) throws InterruptedException, TimeoutException {
        if (!force) {
            if (this.destroyed) {
                return; // nothing to do
            }
        }

        this.checkShmem(false);

        // does the side initiating the close matter?
        if (this.server) {
            this.control.setServerPid(0L);
        } else {
            this.control.setClientPid(0L);
        }

        // unblock any read/writes on client & owner
        this.clientConnectCondition.signal();
        this.serverWriteCondition.signal();
        this.serverReadCondition.signal();
        this.clientWriteCondition.signal();
        this.clientReadCondition.signal();

        // wait for connecting to be false, if we don't wait, segfaults are potentially on the table since these flags
        // indicate that some thread is possibly accessing the shmem
        WaitFor.requireMillis(() -> !this.connecting.get(), 5000, 25);
        WaitFor.requireMillis(() -> !this.reading.get(), 5000, 25);
        WaitFor.requireMillis(() -> !this.writing.get(), 5000, 25);
    }

    protected Write write(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {
        // 1. check if the channel is closed
        this.checkConnectionClosed(true);

        this.writing.set(true);
        try {
            final Consumer<Long> processCrashDetector = this.createProcessDiedMonitor();

            // 2.  wait till we are allowed to write
            final ShmemCondition condition = this.server ? this.serverWriteCondition : this.clientWriteCondition;
            boolean signaled = condition.await(timeout, unit, processCrashDetector);
            if (!signaled) {
                throw new TimeoutException();
            }

            // 3. check if we were signaled b/c the channel is closed
            this.checkConnectionClosed(true);

            // 4. ready for writing
            final ByteBuffer buffer = this.server ? this.serverBuffer : this.clientBuffer;
            buffer.rewind();
            return new Write(buffer);
        } catch (Exception e) {
            this.writing.set(false);

            if (e instanceof ShmemProcessDiedException) {
                this.closeConnection(false);
                throw new ShmemClosedConnectionException(e.getMessage(), e);
            }

            throw e;
        }
    }

    protected void writeEnd() {
        // TODO: is this overkill?
        this.checkShmem(true);

        if (this.server) {
            // client may now read AND must be the only operation that occurs next
            this.clientReadCondition.signal();
        } else {
            // owner may now read AND must be the only operation that occurs next
            this.serverReadCondition.signal();
        }

        this.writing.set(false);
    }

    Read read(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {
        // 1. check if the channel is closed
        this.checkConnectionClosed(true);

        this.reading.set(true);
        try {
            final Consumer<Long> processCrashDetector = this.createProcessDiedMonitor();

            // 2.  wait till we are allowed to read
            final ShmemCondition condition = this.server ? this.serverReadCondition : this.clientReadCondition;
            final boolean signaled = condition.await(timeout, unit, processCrashDetector);
            if (!signaled) {
                throw new TimeoutException();
            }

            // 3. check if we were signaled b/c the channel is closed
            this.checkConnectionClosed(true);

            // 4. ready for reading
            final ByteBuffer buffer = this.server ? this.clientBuffer : this.serverBuffer;
            buffer.rewind();
            return new Read(buffer);
        } catch (Exception e) {
            // set reading to false so that close connection doesn't hang
            this.reading.set(false);

            if (e instanceof ShmemProcessDiedException) {
                this.closeConnection(false);
                throw new ShmemClosedConnectionException(e.getMessage(), e);
            }

            throw e;
        }
    }

    private void readEnd() {
        // TODO: is this overkill?
        this.checkShmem(true);

        if (this.server) {
            // client may now write
            this.clientWriteCondition.signal();
        } else {
            // owner may now write
            this.serverWriteCondition.signal();
        }

        this.reading.set(false);
    }

    static DefaultShmemChannel create(ProcessProvider processProvider, Shmem shmem, boolean spinLocks) {
        return createOrExisting(processProvider, shmem, spinLocks);
    }

    static DefaultShmemChannel existing(ProcessProvider processProvider, Shmem shmem) {
        return createOrExisting(processProvider, shmem, false);  // spinLock argument irr
    }

    static private DefaultShmemChannel createOrExisting(ProcessProvider processProvider, Shmem shmem, Boolean spinLocks) {
        long offset = 0L;

        // attach the "control" to the memory, so we can quickly detect how to proceed
        final Control control = new Control(shmem, offset);
        offset += control.getSize();

        final ShmemCondition clientConnectCondition;
        final ShmemCondition serverWriteCondition;
        final ShmemCondition serverReadCondition;
        final ShmemCondition clientWriteCondition;
        final ShmemCondition clientReadCondition;
        final long serverBufferSize;
        final long clientBufferSize;

        if (shmem.isOwner()) {
            final boolean _spinLocks = spinLocks != null ? spinLocks : false;

            clientConnectCondition = shmem.newCondition(offset, _spinLocks, true);
            offset += clientConnectCondition.getSize();

            serverWriteCondition = shmem.newCondition(offset, _spinLocks, true);
            offset += serverWriteCondition.getSize();

            serverReadCondition = shmem.newCondition(offset, _spinLocks, true);
            offset += serverReadCondition.getSize();

            clientWriteCondition = shmem.newCondition(offset, _spinLocks, true);
            offset += clientWriteCondition.getSize();

            clientReadCondition = shmem.newCondition(offset, _spinLocks, true);
            offset += clientReadCondition.getSize();

            // buffers takes up the rest of the available space
            long totalBuffersLen = shmem.getSize() - offset;
            serverBufferSize = totalBuffersLen / 2;
            clientBufferSize = totalBuffersLen - serverBufferSize;

            // zero out control buffer, set spin lock used
            control.setMagic(MAGIC);
            control.setVersion(VERSION_1_0);
            control.setServerPid(0);
            control.setClientPid(0);
            control.setSpinLocks(_spinLocks);
            // important: on windows and mac, the operating system will round up on shmem, but only tell the owner
            // the original size requested, while the non-owner sees the full shmem, causing calculation issues if we're
            // dividing by 2 -- so we will include the length of the buffer as part of the control
            control.setServerBufferSize(serverBufferSize);
            control.setClientBufferSize(clientBufferSize);
        } else {
            // validate magic and version are what we expect
            if (control.getMagic() != MAGIC) {
                throw new IllegalStateException("Shared memory channel has an unexpected magic value (it is either corrupted or not initialized as a channel yet)");
            }
            if (control.getVersion() != VERSION_1_0) {
                throw new IllegalStateException("Shared memory channel has an unexpected version value (it is either corrupted or not initialized as a channel yet)");
            }

            // the control buffer will help figure out if it's using SPIN vs. STANDARD locks
            final boolean _spinLocks = control.isSpinLocks();

            clientConnectCondition = shmem.existingCondition(offset, _spinLocks);
            offset += clientConnectCondition.getSize();

            serverWriteCondition = shmem.existingCondition(offset, _spinLocks);
            offset += serverWriteCondition.getSize();

            serverReadCondition = shmem.existingCondition(offset, _spinLocks);
            offset += serverReadCondition.getSize();

            clientWriteCondition = shmem.existingCondition(offset, _spinLocks);
            offset += clientWriteCondition.getSize();

            clientReadCondition = shmem.existingCondition(offset, _spinLocks);
            offset += clientReadCondition.getSize();

            serverBufferSize = control.getServerBufferSize();
            clientBufferSize = control.getClientBufferSize();
        }

        final ByteBuffer serverBuffer = shmem.newByteBuffer(offset, serverBufferSize);
        final ByteBuffer clientBuffer = shmem.newByteBuffer(offset+serverBufferSize, clientBufferSize);

        DefaultShmemChannel channel = new DefaultShmemChannel(shmem, processProvider, control, clientConnectCondition,
            serverWriteCondition, serverReadCondition, clientWriteCondition, clientReadCondition, serverBuffer, clientBuffer);

        shmem.registerResource(channel);

        return channel;
    }

}