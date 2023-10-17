package com.fizzed.shmemj;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ShmemChannel {

    abstract protected class AbstractOp implements Closeable {

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
    private final boolean owner;
    private final ByteBuffer controlBuffer;
    private final ShmemCondition ownerWriteCondition;
    private final ShmemCondition ownerReadCondition;
    private final ShmemCondition clientWriteCondition;
    private final ShmemCondition clientReadCondition;
    private final ByteBuffer ownerBuffer;
    private final ByteBuffer clientBuffer;

    public ShmemChannel(Shmem shmem, ByteBuffer controlBuffer, ShmemCondition ownerWriteCondition,
                        ShmemCondition ownerReadCondition, ShmemCondition clientWriteCondition,
                        ShmemCondition clientReadCondition, ByteBuffer ownerBuffer, ByteBuffer clientBuffer) {

        this.shmem = shmem;
        this.owner = shmem.isOwner();
        this.controlBuffer = controlBuffer;
        this.ownerWriteCondition = ownerWriteCondition;
        this.ownerReadCondition = ownerReadCondition;
        this.clientWriteCondition = clientWriteCondition;
        this.clientReadCondition = clientReadCondition;
        this.ownerBuffer = ownerBuffer;
        this.clientBuffer = clientBuffer;
    }

    public long getOwnerPid() {
        return this.controlBuffer.getLong(0);
    }

    public long getClientPid() {
        return this.controlBuffer.getLong(8);
    }

    protected void setOwnerPid(long pid) {
        this.controlBuffer.putLong(0, pid);
    }

    protected void setClientPid(long pid) {
        this.controlBuffer.putLong(8, pid);
    }

    public long getOwnerBufferLength() {
        return this.ownerBuffer.capacity();
    }

    public long getClientBufferLength() {
        return this.ownerBuffer.capacity();
    }

    protected long awaitConnected(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException, IOException {
        // we need to wait for th other party to signal
        final ShmemCondition condition = this.owner ? this.ownerWriteCondition : this.clientWriteCondition;
        boolean signaled = condition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        // since we consumed the signal above, we need to reset it so a "write" won't block
        condition.signal();

        // what happened to the other party?
        final long pid = this.owner ? this.getClientPid() : this.getOwnerPid();

        if (pid > 0) {
            return pid;             // success, owner/client is connected
        } else if (pid < 0) {       // owner/client was connected, but is now closed
            throw new ClosedChannelException();
        } else {                    // still zero? this is some kind of logic error
            throw new IllegalStateException("Should be impossible case of connected (did someone write to the channel before it was connected?)");
        }
    }

    public long accept(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        // only owners can accept
        if (!this.owner) {
            throw new IllegalStateException("Only channel owners are allowed to accept (did you mean to use connect?)");
        }

        // set the pid to indicate our end is ready
        this.setOwnerPid(ProcessHandle.current().pid());

        // signal the other party we are ready
        this.clientWriteCondition.signal();

        return this.awaitConnected(timeout, unit);
    }

    public long connect(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        // only clients can connect
        if (this.owner) {
            throw new IllegalStateException("Only channel clients are allowed to connect (did you mean to use accept?)");
        }

        // set the pid to indicate our end is ready
        this.setClientPid(ProcessHandle.current().pid());

        // signal the other party we are ready
        this.ownerWriteCondition.signal();

        return this.awaitConnected(timeout, unit);
    }

    public void close() {
        // negative 1 for process ids indicates the channel is now closed on that side
        if (this.owner) {
            this.setOwnerPid(-1);
            // unblock any read/writes on client
            this.clientWriteCondition.signal();
            this.clientReadCondition.signal();
        } else {
            this.setClientPid(-1);
            // unblock any read/writes on owner
            this.ownerWriteCondition.signal();
            this.ownerReadCondition.signal();
        }
    }

    protected void checkIfClosed(boolean forWriting) throws IOException {
        long thisPid = this.owner ? this.getOwnerPid() : this.getClientPid();
        long otherPid = !this.owner ? this.getOwnerPid() : this.getClientPid();

        // are we closed? or never connected?
        if (thisPid <= 0) {
            throw new ClosedChannelException();
        }

        // on writes, no point writing if we're not closed, but the other side is closed
        // but for reading, there may be an in-flight
        if (forWriting && otherPid <= 0) {
            throw new ClosedChannelException();
        }
    }

    public Write write(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
        final ShmemCondition condition = this.owner ? this.ownerWriteCondition : this.clientWriteCondition;

        // we need to wait till we are allowed to write
        boolean signaled = condition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        final ByteBuffer buffer = this.owner ? this.ownerBuffer : this.clientBuffer;

        buffer.rewind();
        return new Write(buffer);
    }

    protected void writeEnd() {
        if (this.owner) {
            // client may now read AND must be the only operation that occurs next
            this.clientReadCondition.signal();
        } else {
            // owner may now read AND must be the only operation that occurs next
            this.ownerReadCondition.signal();
        }
    }

    public Read read(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
        final ShmemCondition condition = this.owner ? this.ownerReadCondition : this.clientReadCondition;

        // we need to wait till we are allowed to read
        boolean signaled = condition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        final ByteBuffer buffer = this.owner ? this.clientBuffer : this.ownerBuffer;

        buffer.rewind();
        return new Read(buffer);
    }

    private void readEnd() {
        if (this.owner) {
            // client may now write
            this.clientWriteCondition.signal();
        } else {
            // owner may now write
            this.ownerWriteCondition.signal();
        }
    }

    static ShmemChannel create(Shmem shmem) {
        final ShmemCondition ownerWriteCondition;
        final ShmemCondition ownerReadCondition;
        final ShmemCondition clientWriteCondition;
        final ShmemCondition clientReadCondition;

        long offset = 0L;

        // control buffer is 16 bytes (8 bytes for 2 pids)
        final ByteBuffer controlBuffer = shmem.newByteBuffer(offset, 16);
        offset += 16;

        if (shmem.isOwner()) {
            ownerWriteCondition = shmem.newCondition(offset, true);
            offset += ownerWriteCondition.getSize();

            ownerReadCondition = shmem.newCondition(offset, true);
            offset += ownerReadCondition.getSize();

            clientWriteCondition = shmem.newCondition(offset, true);
            offset += clientWriteCondition.getSize();

            clientReadCondition = shmem.newCondition(offset, true);
            offset += clientReadCondition.getSize();

            // zero out control buffer
            controlBuffer.putLong(0, 0);
            controlBuffer.putLong(1, 0);
        } else {
            ownerWriteCondition = shmem.existingCondition(offset);
            offset += ownerWriteCondition.getSize();

            ownerReadCondition = shmem.existingCondition(offset);
            offset += ownerReadCondition.getSize();

            clientWriteCondition = shmem.existingCondition(offset);
            offset += clientWriteCondition.getSize();

            clientReadCondition = shmem.existingCondition(offset);
            offset += clientReadCondition.getSize();
        }

        // buffers takes up the rest of the available space
        long totalBuffersLen = shmem.getSize() - offset;
        long ownerBufferLen = totalBuffersLen / 2;
        long clientBufferLen = totalBuffersLen - ownerBufferLen;

        final ByteBuffer ownerBuffer = shmem.newByteBuffer(offset, ownerBufferLen);
        final ByteBuffer clientBuffer = shmem.newByteBuffer(offset+ownerBufferLen, clientBufferLen);

        return new ShmemChannel(shmem, controlBuffer, ownerWriteCondition, ownerReadCondition, clientWriteCondition, clientReadCondition, ownerBuffer, clientBuffer);
    }

}