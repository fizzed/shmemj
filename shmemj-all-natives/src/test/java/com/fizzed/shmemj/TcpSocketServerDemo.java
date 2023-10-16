package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TcpSocketServerDemo {
    static private final Logger log = LoggerFactory.getLogger(TcpSocketServerDemo.class);

    static public void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(12244);

        log.info("Waiting for client connection...");

        Socket socket = serverSocket.accept();

        log.info("Client connected: {}", socket.getRemoteSocketAddress());

        byte[] readBuffer = new byte[200];
        try (OutputStream output = socket.getOutputStream()) {
            try (InputStream input = socket.getInputStream()) {
                for (int i = 0; i < 20; i++) {
                    log.info("Send-recv loop #{}", i);

                    String sendMessage = "Hello from loop " + i;

                    log.info("writeBegin()");
                    log.info("Send message: {}", sendMessage);
                    output.write(sendMessage.getBytes(StandardCharsets.UTF_8));
                    log.info("writeEnd()");

                    int readLen = input.read(readBuffer);


                }
            }
        }
    }

}