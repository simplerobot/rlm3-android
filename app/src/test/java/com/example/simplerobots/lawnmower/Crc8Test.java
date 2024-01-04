package com.example.simplerobots.lawnmower;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class Crc8Test {
    @Test
    public void Constructor() {
        Crc8 test = new Crc8();

        assertEquals(0x00, test.get());
    }

    @Test
    public void ValidateTestVectors() {
        assertEquals((byte)0x00, CalculateCrc(""));
        assertEquals((byte)0x25, CalculateCrc("Hello World"));
        assertEquals((byte)0x00, CalculateCrc(new int[]{0x00, 0x00, 0x00, 0x00}));
        assertEquals((byte)0x2F, CalculateCrc(new int[]{ 0xF2, 0x01, 0x83 }));
        assertEquals((byte)0xB1, CalculateCrc(new int[]{ 0x0F, 0xAA, 0x00, 0x55 }));
        assertEquals((byte)0x11, CalculateCrc(new int[]{ 0x00, 0xFF, 0x55, 0x11 }));
        assertEquals((byte)0x59, CalculateCrc(new int[]{ 0x33, 0x22, 0x55, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF }));
        assertEquals((byte)0xB1, CalculateCrc(new int[]{ 0x92, 0x6B, 0x55 }));
        assertEquals((byte)0xDE, CalculateCrc(new int[]{ 0xFF, 0xFF, 0xFF, 0xFF }));
        assertEquals((byte)0x1a, CalculateCrc(new int[]{ 0xBE, 0xEF }));
    }

    private byte CalculateCrc(int[] data) {
        Crc8 test = new Crc8();
        for (int x : data) {
            test.add((byte)x);
        }
        return test.get();
    }

    private byte CalculateCrc(byte[] data) {
        Crc8 test = new Crc8();
        for (byte x : data) {
            test.add(x);
        }
        return test.get();
    }

    private byte CalculateCrc(String s) {
        return CalculateCrc(s.getBytes(StandardCharsets.UTF_8));
    }

}
