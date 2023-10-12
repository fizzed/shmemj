package com.fizzed.shmemj;

import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

public class SharedMemoryFactoryTest {

    @Test
    public void create() {
        final SharedMemory shmem = new SharedMemoryFactory()
             .setSize(2048L)
             .create();

        try {
            assertThat(shmem.getSize(), is(2048L));
            assertThat(shmem.getOsId(), startsWith("/shmem_"));
        } finally {
            shmem.close();
        }
    }

    @Test
    public void createFailsWithNegativeSize() {
        try {
            final SharedMemory shmem = new SharedMemoryFactory()
                .setSize(-1L)
                .create();
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("UnknownOsError"));
        }
    }

    @Test
    public void open() {
        // create 2 shared mems
        final SharedMemory shmem1 = new SharedMemoryFactory()
            .setSize(2048L)
            .create();

        try {
            final SharedMemory shmem2 = new SharedMemoryFactory()
                .setOsId(shmem1.getOsId())
                .open();

            try {
                assertThat(shmem1.getSize(), is(shmem2.getSize()));
                assertThat(shmem1.getOsId(), is(shmem2.getOsId()));

            } finally {
                shmem2.close();
            }
        } finally {
            shmem1.close();
        }
    }

    @Test
    public void openFailsWithNonExistingId() {
        try {
            final SharedMemory shmem = new SharedMemoryFactory()
                .setOsId("/shmem_thisdoesnotexist")
                .open();
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("MapOpenFailed"));
        }
    }

}