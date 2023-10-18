package com.fizzed.shmemj.demo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DemoHelper {

    static public void putStringUTF8(ByteBuffer buf, String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        buf.putInt(bytes.length);
        buf.put(bytes);
    }

    static public String getStringUTF8(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

}