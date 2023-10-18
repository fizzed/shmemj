package com.fizzed.shmemj.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class TcpSocketClientDemo {
    static private final Logger log = LoggerFactory.getLogger(TcpSocketClientDemo.class);

    static public void main(String[] args) throws Exception {

        int iterations = 200000;

        try (Socket socket = new Socket()) {

            log.info("Waiting for client connection...");

            socket.connect(new InetSocketAddress("localhost", 12244));

            byte[] readBuffer = new byte[200];
            ByteBuffer readWrappedBuffer = ByteBuffer.wrap(readBuffer);

            byte[] sendBuffer = new byte[200];
            ByteBuffer sendWrappedBuffer = ByteBuffer.wrap(sendBuffer);

            try (OutputStream output = socket.getOutputStream()) {
                try (InputStream input = socket.getInputStream()) {

                    long startTime = System.currentTimeMillis();

                    for (int i = 0; i < iterations; i++) {
                        // send request
                        //log.info("Send-recv loop #{}", i);

                        //String sendMessage = "Hello from loop " + i;

                        //log.info("writeBegin()");
                        //log.info("Send message: {}", sendMessage);
                        //output.write(sendMessage.getBytes(StandardCharsets.UTF_8));
                        //log.info("writeEnd()");

                        sendWrappedBuffer.rewind();
                        sendWrappedBuffer.putLong(i);
                        sendWrappedBuffer.putLong(2L);
                        sendWrappedBuffer.putLong(3L);

                        output.write(sendBuffer, 0, 24);


                        // read response
                        // now we want to read from the channel!
                        //log.info("readBegin()");
                        int readLen = input.read(readBuffer);

                        readWrappedBuffer.rewind();
                        readWrappedBuffer.getLong();

                        //String recvMessage = new String(readBuffer, 0, readLen);
                        //log.info("Recv message: {}", recvMessage);

                        //log.info("readEnd()");
                    }

                    long endTime = System.currentTimeMillis();
                    log.info("Took {} ms", (endTime-startTime));
                }
            }
        }
    }

}