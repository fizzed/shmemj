package com.fizzed.shmemj;

import java.io.IOException;

public class ShmemClosedConnectionException extends IOException {

    public ShmemClosedConnectionException(Throwable cause) {
        super(cause);
    }

    public ShmemClosedConnectionException(String message) {
        super(message);
    }

    public ShmemClosedConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

}