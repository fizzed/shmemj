package com.fizzed.shmemj;

import java.nio.ByteBuffer;
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

    public long getOwnerBufferLength() {
        return this.ownerBuffer.capacity();
    }

    public long getClientBufferLength() {
        return this.ownerBuffer.capacity();
    }

    public ByteBuffer writeBegin(long timeout, TimeUnit unit) throws TimeoutException {
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

    public ByteBuffer readBegin(long timeout, TimeUnit unit) throws TimeoutException {
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
        offset += controlBuffer.capacity();

        // let's make sure the control buffer is zeroed out
        controlBuffer.putLong(0);
        controlBuffer.putLong(0);

        if (shmem.isOwner()) {
            ownerWriteCondition = shmem.newCondition(offset, true);
            offset += ownerWriteCondition.getSize();

            ownerReadCondition = shmem.newCondition(offset, true);
            offset += ownerReadCondition.getSize();

            clientWriteCondition = shmem.newCondition(offset, true);
            offset += clientWriteCondition.getSize();

            clientReadCondition = shmem.newCondition(offset, true);
            offset += clientReadCondition.getSize();

            // by default a shared channel is ready for writing by owner or client
            ownerWriteCondition.signal();
            clientWriteCondition.signal();
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