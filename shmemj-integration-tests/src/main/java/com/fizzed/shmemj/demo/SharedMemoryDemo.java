package com.fizzed.shmemj.demo;

import com.fizzed.shmemj.Shmem;
import com.fizzed.shmemj.ShmemFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class SharedMemoryDemo {
    static private final Logger log = LoggerFactory.getLogger(SharedMemoryDemo.class);

    static public void main(String[] args) throws Exception {
        Shmem shmem = new ShmemFactory()
            .setSize(2048L)
            .create();

        log.debug("Shared memory: {}", shmem);

        log.debug("OsId: {}", shmem.getOsId());
        log.debug("Size: {}", shmem.getSize());


        /*final SharedCondition condition1 = shmem.newCondition(0);

        log.debug("condition1: {}", condition1);

        *//*ExecutorService es = Executors.newSingleThreadExecutor();
        es.submit(() -> {
           try {
               Thread.sleep(2000L);
                log.debug("Signaling from another thread...");
               condition1.signal();
           } catch (Exception e) {
               log.error("", e);
               return;
           }
        });*//*

        condition1.signal();

        log.debug("Will try to await...");
        boolean signaled = condition1.await(5, TimeUnit.SECONDS);
        log.debug("Returned, was signaled? {}", signaled);

        condition1.signal();

        log.debug("Signaled");

        condition1.close();

        condition1.clear();

        log.debug("Cleared");*/


        final ByteBuffer buf = shmem.newByteBuffer(0, 30);
        buf.putDouble(5.4d);
        buf.putDouble(3.12345d);
        buf.putDouble(3.12345d);

        log.debug("Buf: isDirect={}, class={}, capacity={}, position={}", buf.isDirect(), buf.getClass(), buf.capacity(), buf.position());

        buf.flip();

        log.debug("Buf: d={}", buf.getDouble());

        final ByteBuffer buf2 = shmem.newByteBuffer(0, 20);

        log.debug("Buf2: d={}", buf2.getDouble());
        log.debug("Buf2: d={}", buf2.getDouble());



        shmem.close();

        shmem = null;
        System.gc();

        Thread.sleep(600000000L);
    }

}