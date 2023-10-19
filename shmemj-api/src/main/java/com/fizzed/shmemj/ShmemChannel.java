package com.fizzed.shmemj;

import com.fizzed.crux.util.WaitFor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShmemChannel implements AutoCloseable {
    static private final Logger log = LoggerFactory.getLogger(ShmemChannel.class);

    // probably best to keep control buffer as divisible by 8
    static private final int CONTROL_BUFFER_SIZE = 24;
    static private final int CONTROL_MAGIC_POS = 0;
    static private final int CONTROL_VERSION_POS = 1;
    static private final int CONTROL_SERVER_PID_POS = 2;
    static private final int CONTROL_CLIENT_PID_POS = 10;
    static private final int CONTROL_SPIN_LOCK_POS = 18;

    static private final long NOT_CONNECTED_PID = 0L;
    static private final byte MAGIC = (byte)42;         // random value to detect this is most likely a shmem channel
    static private final byte VERSION_1_0 = (byte)10;   // safety of versioned channels in case of long running processes...
    static private final byte STANDARD_LOCKS = (byte)0;
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
            this.buffer.put(CONTROL_SPIN_LOCK_POS, spinLocks ? SPIN_LOCKS : STANDARD_LOCKS);
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

    public class Read extends AbstractOp {

        public Read(ByteBuffer buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            ShmemChannel.this.readEnd();
        }
    }

    public class Write extends AbstractOp {

        public Write(ByteBuffer buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            ShmemChannel.this.writeEnd();
        }
    }

    private final Shmem shmem;
    private final boolean server;
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
    private final AtomicBoolean closed;

    public ShmemChannel(Shmem shmem, Control control, ShmemCondition clientConnectCondition, ShmemCondition serverWriteCondition,
                        ShmemCondition serverReadCondition, ShmemCondition clientWriteCondition,
                        ShmemCondition clientReadCondition, ByteBuffer serverBuffer, ByteBuffer clientBuffer) {

        this.shmem = shmem;
        this.server = shmem.isOwner();
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
        this.closed = new AtomicBoolean(false);
    }

    public boolean isServer() {
        return this.server;
    }

    public long getServerPid() {
        this.checkShmem();
        return this.control.getServerPid();
    }

    public long getClientPid() {
        this.checkShmem();
        return this.control.getClientPid();
    }

    public boolean isSpinLocks() {
        this.checkShmem();
        return this.control.isSpinLocks();
    }

    /*protected long awaitConnected(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {
        // we need to wait for th other party to signal
        final ShmemCondition condition = this.server ? this.serverWriteCondition : this.clientWriteCondition;
        boolean signaled = condition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        // since we consumed the signal above, we need to reset it so a "write" won't block
        condition.signal();

        // what happened to the other party?
        final long pid = this.server ? this.getClientPid() : this.getServerPid();

        this.checkClosed(false);

        return pid;
    }*/

    public long accept(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        this.checkShmem();

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
            this.control.setServerPid(ProcessHandle.current().pid());

            try {
                // wait for the client to connect
                boolean signaled = this.clientConnectCondition.await(timeout, unit);
                if (!signaled) {
                    throw new TimeoutException();
                }

                // double check client is connected (we could have been signaled to close)
                this.checkClosed(false);

                // now we can signal that writes are allowed
                this.clientWriteCondition.signal();
                this.serverWriteCondition.signal();

                // register ourselves with the shmem to be closed if its closed
                this.shmem.addCloseable(this);

                return this.getClientPid();
            } catch (TimeoutException e) {
                this.control.setServerPid(NOT_CONNECTED_PID);
                throw e;
            }
        } finally {
            this.connecting.set(false);
        }
    }

    public long connect(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        this.checkShmem();

        // only clients can connect
        if (this.server) {
            throw new IllegalStateException("Only channel clients are allowed to connect (did you mean to use accept?)");
        }

        this.connecting.set(true);
        try {
            // set the pid to indicate our end is ready
            this.control.setClientPid(ProcessHandle.current().pid());

            // we could wait for the server pid OR someone closing this client
            if (!WaitFor.of(() -> this.control.getServerPid() > 0 || this.isClientClosed()).awaitMillis(unit.toMillis(timeout), 50L)) {
                throw new TimeoutException();
            }

            // double check client is connected (we could have been signaled to close)
            this.checkClosed(false);

            // signal the server we are ready
            this.clientConnectCondition.signal();

            // NOTE: it's possible that despite the signal above, the server accept() could have timed out, so we
            // have not really connected to it, we'll see if that's an issue

            // register ourselves with the shmem to be closed if its closed
            this.shmem.addCloseable(this);

            return this.control.getServerPid();
        } catch (IOException | TimeoutException | InterruptedException e) {
            this.control.setClientPid(NOT_CONNECTED_PID);
            throw e;
        } finally {
            this.connecting.set(false);
        }
    }

    protected boolean isClosed() {
        return this.isServerClosed() || this.isClientClosed();
    }

    protected boolean isServerClosed() {
        this.checkShmem();

        final long serverPid = this.control.getServerPid();

        return serverPid <= NOT_CONNECTED_PID;
    }

    protected boolean isClientClosed() {
        this.checkShmem();

        final long clientPid = this.control.getClientPid();

        return clientPid <= NOT_CONNECTED_PID;
    }

    protected void checkClosed(boolean forWriting) throws IOException {
        this.checkShmem();

        if (this.isClosed()) {
            throw new ClosedChannelException();
        }
    }

    @Override
    public void close() throws Exception {
        // TODO: should we ignore a bad shmem and mark this as closed?
        this.checkShmem();

        // mark the side that is initiating the close
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

        this.shmem.removeCloseable(this);
    }

    protected void checkShmem() {
        if (this.shmem.isDestroyed()) {
            throw new IllegalStateException("Shared memory backing this channel is destroyed (you should ensure you have closed the channel before the shared memory!)");
        }
    }

    public Write write(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {
        // 1. check if the channel is closed
        this.checkClosed(false);

        this.writing.set(true);
        try {
            // 2.  wait till we are allowed to write
            final ShmemCondition condition = this.server ? this.serverWriteCondition : this.clientWriteCondition;
            boolean signaled = condition.await(timeout, unit);
            if (!signaled) {
                throw new TimeoutException();
            }

            // 3. check if we were signaled b/c the channel is closed
            this.checkClosed(false);

            // 4. ready for writing
            final ByteBuffer buffer = this.server ? this.serverBuffer : this.clientBuffer;
            buffer.rewind();
            return new Write(buffer);
        } catch (IOException | TimeoutException |InterruptedException e) {
            this.writing.set(false);
            throw e;
        }
    }

    protected void writeEnd() {
        // TODO: is this overkill?
        this.checkShmem();

        if (this.server) {
            // client may now read AND must be the only operation that occurs next
            this.clientReadCondition.signal();
        } else {
            // owner may now read AND must be the only operation that occurs next
            this.serverReadCondition.signal();
        }

        this.writing.set(false);
    }

    public Read read(long timeout, TimeUnit unit) throws IOException, TimeoutException, InterruptedException {
        // 1. check if the channel is closed
        this.checkClosed(false);

        this.reading.set(true);
        try {
            // 2.  wait till we are allowed to read
            final ShmemCondition condition = this.server ? this.serverReadCondition : this.clientReadCondition;
            final boolean signaled = condition.await(timeout, unit);
            if (!signaled) {
                throw new TimeoutException();
            }

            // 3. check if we were signaled b/c the channel is closed
            this.checkClosed(false);

            // 4. ready for reading
            final ByteBuffer buffer = this.server ? this.clientBuffer : this.serverBuffer;
            buffer.rewind();
            return new Read(buffer);
        } catch (IOException | TimeoutException |InterruptedException e) {
            this.reading.set(false);
            throw e;
        }
    }

    private void readEnd() {
        // TODO: is this overkill?
        this.checkShmem();

        if (this.server) {
            // client may now write
            this.clientWriteCondition.signal();
        } else {
            // owner may now write
            this.serverWriteCondition.signal();
        }

        this.reading.set(false);
    }

    static ShmemChannel create(Shmem shmem, boolean spinLock) {
        return createOrExisting(shmem, spinLock);
    }

    static ShmemChannel existing(Shmem shmem) {
        return createOrExisting(shmem, false);  // spinLock argument irr
    }

    static private ShmemChannel createOrExisting(Shmem shmem, Boolean spinLocks) {
        long offset = 0L;

        // attach the "control" to the memory, so we can quickly detect how to proceed
        final Control control = new Control(shmem, offset);
        offset += control.getSize();

        final ShmemCondition clientConnectCondition;
        final ShmemCondition serverWriteCondition;
        final ShmemCondition serverReadCondition;
        final ShmemCondition clientWriteCondition;
        final ShmemCondition clientReadCondition;

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

            // zero out control buffer, set spin lock used
            control.setMagic(MAGIC);
            control.setVersion(VERSION_1_0);
            control.setServerPid(0);
            control.setClientPid(0);
            control.setSpinLocks(_spinLocks);
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
        }

        // buffers takes up the rest of the available space
        long totalBuffersLen = shmem.getSize() - offset;
        long ownerBufferLen = totalBuffersLen / 2;
        long clientBufferLen = totalBuffersLen - ownerBufferLen;

        final ByteBuffer serverBuffer = shmem.newByteBuffer(offset, ownerBufferLen);
        final ByteBuffer clientBuffer = shmem.newByteBuffer(offset+ownerBufferLen, clientBufferLen);

        ShmemChannel channel = new ShmemChannel(shmem, control, clientConnectCondition, serverWriteCondition, serverReadCondition, clientWriteCondition, clientReadCondition, serverBuffer, clientBuffer);

        shmem.addCloseable(channel);

        return channel;
    }

}