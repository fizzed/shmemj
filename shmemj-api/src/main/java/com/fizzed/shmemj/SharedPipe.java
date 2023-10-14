package com.fizzed.shmemj;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SharedPipe {

    private final SharedMemory shmem;
    private final boolean owner;
    private final SharedCondition ownerWriteCondition;
    private final SharedCondition ownerReadCondition;
    private final SharedCondition clientWriteCondition2;
    private final SharedCondition clientReadCondition;

    private final ByteBuffer buffer;

    public SharedPipe(SharedMemory shmem, SharedCondition ownerWriteCondition, SharedCondition ownerReadCondition,
                      SharedCondition clientWriteCondition, SharedCondition clientReadCondition, ByteBuffer buffer) {

        this.shmem = shmem;
        this.owner = shmem.isOwner();
        this.ownerWriteCondition = ownerWriteCondition;
        this.ownerReadCondition = ownerReadCondition;
        this.clientWriteCondition2 = clientWriteCondition;
        this.clientReadCondition = clientReadCondition;
        this.buffer = buffer;
    }

    public ByteBuffer beginWrite(long timeout, TimeUnit unit) throws TimeoutException {
//        final SharedCondition writeCondition = this.owner ? this.ownerWriteCondition : this.clientWriteCondition;
        final SharedCondition writeCondition = this.ownerWriteCondition;

        boolean signaled = writeCondition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        // we need exclusive access to the channel (do not allow any other op to proceed)
        /*this.ownerWriteCondition.clear();
        this.ownerReadCondition.clear();
        this.clientWriteCondition.clear();
        this.clientReadCondition.clear();*/

        // awesome, we are ready to write
        this.buffer.rewind();
        return this.buffer;
    }

    public void endWrite() {
        if (this.owner) {
            // client may now read AND must be the only operation that occurs next
            this.clientReadCondition.signal();
        } else {
            // owner may now read AND must be the only operation that occurs next
            this.ownerReadCondition.signal();
        }
    }

    public ByteBuffer beginRead(long timeout, TimeUnit unit) throws TimeoutException {
        final SharedCondition readCondition = this.owner ? this.ownerReadCondition : this.clientReadCondition;

        // we need to wait till we are allowed to read
        boolean signaled = readCondition.await(timeout, unit);
        if (!signaled) {
            throw new TimeoutException();
        }

        // we need exclusive access to the channel (do not allow any other op to proceed)
        /*this.ownerWriteCondition.clear();
        this.ownerReadCondition.clear();
        this.clientWriteCondition.clear();
        this.clientReadCondition.clear();*/

        // awesome, someone wrote something
        this.buffer.rewind();
        return this.buffer;
    }

    public void endRead() {
        // anyone is allowed to write (either owner or client)
        //this.clientWriteCondition.signal();
        this.ownerWriteCondition.signal();
    }

    public ByteBuffer endReadBeginWrite() {
        // anyone is allowed to write (either owner or client)
        //this.clientWriteCondition.signal();
        //this.ownerWriteCondition.signal();

        // no one is signaled since we are now writing too
        this.buffer.rewind();
        return this.buffer;
    }

    static SharedPipe create(SharedMemory shmem) {
        final SharedCondition ownerWriteCondition;
        final SharedCondition ownerReadCondition;
        final SharedCondition clientWriteCondition;
        final SharedCondition clientReadCondition;

        long offset = 0L;

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

        // buffer takes up the rest of the available space
        final ByteBuffer buffer = shmem.newByteBuffer(offset, shmem.getSize() - offset);

        return new SharedPipe(shmem, ownerWriteCondition, ownerReadCondition, clientWriteCondition, clientReadCondition, buffer);
    }

}