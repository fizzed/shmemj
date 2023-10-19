package com.fizzed.shmemj;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ShmemClientChannel extends ShmemChannel {

    ShmemChannelConnection connect(long timeout, TimeUnit unit) throws IOException, InterruptedException, TimeoutException;

}
