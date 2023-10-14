package com.fizzed.shmemj;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SharedChannel {

    private final SharedMemory shmem;
    private final boolean owner;
    private final ByteBuffer controlBuffer;
    private final SharedCondition ownerWriteCondition;
    private final SharedCondition ownerReadCondition;
    private final SharedCondition clientWriteCondition;
    private final SharedCondition clientReadCondition;
    private final ByteBuffer ownerBuffer;
    private final ByteBuffer clientBuffer;

    public SharedChannel(SharedMemory shmem, ByteBuffer controlBuffer, SharedCondition ownerWriteCondition,
                         SharedCondition ownerReadCondition, SharedCondition clientWriteCondition,
                         SharedCondition clientReadCondition, ByteBuffer ownerBuffer, ByteBuffer clientBuffer) {

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
        // is a client already connected?
        long pid = this.owner ? this.getClientPid() : this.getOwnerPid();

        // if the clientPid == 0, we need to wait for it to arrive
        if (pid == 0) {
            System.out.println("Waiting on write condition...");
            // we need to wait for the other party to be ready to write
            final SharedCondition condition = this.owner ? this.clientWriteCondition : this.ownerWriteCondition;
            boolean signaled = condition.await(timeout, unit);
            if (!signaled) {
                throw new TimeoutException();
            }

            System.out.println("Signaled..");

            // re-fetch the pid value again to see what happened
            pid = this.owner ? this.getClientPid() : this.getOwnerPid();

            // we must "re-signal" the condition so we can write
            condition.signal();
        }

        System.out.println("ummm pid was " + pid);

        if (pid > 0) {
            return pid;             // success, owner/client is connected
        } else if (pid < 0) {       // owner/client was connected, but is now closed
            throw new ClosedChannelException();
        } else {                    // still zero? this is some kind of logic error
            throw new IllegalStateException("Should be impossible case of connected (did someone write to the channel before it was connected?)");
        }
    }

    public long connect(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException {
        long pid = ProcessHandle.current().pid();

        if (this.owner) {
            this.setOwnerPid(pid);
            this.ownerWriteCondition.signal();
        } else {
            this.setClientPid(pid);
            this.clientWriteCondition.signal();
        }

        return this.awaitConnected(timeout, unit);
    }

    public ByteBuffer writeBegin(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
        final SharedCondition condition = this.owner ? this.ownerWriteCondition : this.clientWriteCondition;

        // we need to wait till we are allowed to write
        boolean signaled = condition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        final ByteBuffer buffer = this.owner ? this.ownerBuffer : this.clientBuffer;

        buffer.rewind();
        return buffer;
    }

    public void writeEnd() {
        if (this.owner) {
            // client may now read AND must be the only operation that occurs next
            this.clientReadCondition.signal();
        } else {
            // owner may now read AND must be the only operation that occurs next
            this.ownerReadCondition.signal();
        }
    }

    public ByteBuffer readBegin(long timeout, TimeUnit unit) throws TimeoutException, InterruptedException {
        final SharedCondition condition = this.owner ? this.ownerReadCondition : this.clientReadCondition;

        // we need to wait till we are allowed to read
        boolean signaled = condition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        final ByteBuffer buffer = this.owner ? this.clientBuffer : this.ownerBuffer;

        buffer.rewind();
        return buffer;
    }

    public void readEnd() {
        if (this.owner) {
            // client may now write
            this.clientWriteCondition.signal();
        } else {
            // owner may now write
            this.ownerWriteCondition.signal();
        }
    }

    static SharedChannel create(SharedMemory shmem) {
        final SharedCondition ownerWriteCondition;
        final SharedCondition ownerReadCondition;
        final SharedCondition clientWriteCondition;
        final SharedCondition clientReadCondition;

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

        return new SharedChannel(shmem, controlBuffer, ownerWriteCondition, ownerReadCondition, clientWriteCondition, clientReadCondition, ownerBuffer, clientBuffer);
    }

}