package org.jed2k.protocol;

import static org.jed2k.Utils.sizeof;

import java.nio.ByteBuffer;

public class UInt8 extends UNumber implements Comparable<UInt8> {

    private static final long serialVersionUID = -6821055240959745390L;
    public static final short MIN_VALUE = 0x00;
    public static final short MAX_VALUE = 0xff;
    private byte container;

    UInt8() {
        container = 0;
    }

    UInt8(byte value) {
        container = value;
    }

    UInt8(int value) {
        container = (byte) value;
    }

    UInt8(short value) {
        container = (byte) value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof UInt8) {
            return container == ((UInt8) obj).container;
        }

        return false;
    }

    @Override
    public int compareTo(UInt8 o) {
        return (shortValue() < o.shortValue() ? -1 : (shortValue() == o
                .shortValue() ? 0 : 1));
    }

    @Override
    public double doubleValue() {
        return container & 0xff;
    }

    @Override
    public float floatValue() {
        return container & MAX_VALUE;
    }

    @Override
    public int intValue() {
        return container & MAX_VALUE;
    }

    @Override
    public long longValue() {
        return container & MAX_VALUE;
    }

    @Override
    public ByteBuffer get(ByteBuffer src) throws ProtocolException {
        container = src.get();
        return src;
    }

    @Override
    public ByteBuffer put(ByteBuffer dst) throws ProtocolException {
        return dst.put(container);
    }

    @Override
    public UNumber assign(byte b) {
        container = b;
        return this;
    }

    @Override
    public UNumber assign(short value) {
        container = (byte) value;
        return this;
    }

    @Override
    public UNumber assign(int value) {
        container = (byte) value;
        return this;
    }

    @Override
    public int size() {
        return sizeof(container);
    }
    
    @Override
    public String toString() {
        return "uint8{" + intValue() + "}";
    }
}