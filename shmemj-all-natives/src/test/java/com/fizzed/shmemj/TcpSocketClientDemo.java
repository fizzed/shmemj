package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpSocketClientDemo {
    static private final Logger log = LoggerFactory.getLogger(TcpSocketClientDemo.class);

    static public void main(String[] args) throws Exception {
        Socket socket = new Socket();

        log.info("Waiting for client connection...");

        socket.connect(new InetSocketAddress("localhost", 12244));


    }

}