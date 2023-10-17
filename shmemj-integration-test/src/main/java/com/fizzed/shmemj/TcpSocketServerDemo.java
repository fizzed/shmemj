package com.fizzed.shmemj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.fizzed.shmemj.DemoHelper.getStringUTF8;

public class TcpSocketServerDemo {
    static private final Logger log = LoggerFactory.getLogger(TcpSocketServerDemo.class);

    static public void main(String[] args) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(12244)) {

            log.info("Waiting for client connection...");

            while (true) {
                try (Socket socket = serverSocket.accept()) {

                    log.info("Client connected: {}", socket.getRemoteSocketAddress());

                    byte[] readBuffer = new byte[200];
                    ByteBuffer readWrappedBuffer = ByteBuffer.wrap(readBuffer);

                    byte[] sendBuffer = new byte[200];
                    ByteBuffer sendWrappedBuffer = ByteBuffer.wrap(sendBuffer);

                    try (OutputStream output = socket.getOutputStream()) {
                        try (InputStream input = socket.getInputStream()) {
                            while (true) {
                                // read request
                                int readLen = input.read(readBuffer);
                                //String recvMessage = new String(readBuffer, 0, readLen);

                                readWrappedBuffer.rewind();
                                long iteration = readWrappedBuffer.getLong();
                                readWrappedBuffer.getLong();
                                readWrappedBuffer.getLong();

                                //log.info("Recv message: {}", recvMessage);

                                //log.info("readEnd()");

                                // we want to write to the channel!
                                //log.info("beginWrite()");

                                // write response
                                //                            String sendMessage = recvMessage + " (this is the reply)";
                                //                            output.write(sendMessage.getBytes(StandardCharsets.UTF_8));

                                sendWrappedBuffer.rewind();
                                sendWrappedBuffer.putLong(iteration);
                                output.write(sendBuffer, 0, 8);
                            }
                        }
                    }
                } catch (IOException e) {
                    log.error("IO exception during client", e);
                }
            }
        }
    }

}