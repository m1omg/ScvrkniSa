package io.github.m1omg.shrink.core.mkv;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A growable byte buffer with helpers for writing EBML, the binary format
 * Matroska and WebM are built on. Every EBML element is
 * {@code [ID][size][payload]}, where ID and size use a variable-length encoding.
 */
final class EbmlBuffer {

    private byte[] data;
    private int length;

    EbmlBuffer() {
        this(256);
    }

    EbmlBuffer(int capacity) {
        data = new byte[Math.max(16, capacity)];
    }

    int length() {
        return length;
    }

    byte[] array() {
        return data;
    }

    byte[] toByteArray() {
        return Arrays.copyOf(data, length);
    }

    void reset() {
        length = 0;
    }

    private void ensure(int extra) {
        if (length + extra > data.length) {
            data = Arrays.copyOf(data, Math.max(data.length * 2, length + extra));
        }
    }

    void writeByte(int b) {
        ensure(1);
        data[length++] = (byte) b;
    }

    void writeBytes(byte[] bytes, int offset, int count) {
        ensure(count);
        System.arraycopy(bytes, offset, data, length, count);
        length += count;
    }

    void writeBytes(byte[] bytes) {
        writeBytes(bytes, 0, bytes.length);
    }

    /** Element IDs are written as-is; their length is implied by the leading bits. */
    void writeId(int id) {
        if ((id & 0xFF000000) != 0) {
            writeByte(id >>> 24);
        }
        if ((id & 0xFFFF0000) != 0) {
            writeByte(id >>> 16);
        }
        if ((id & 0xFFFFFF00) != 0) {
            writeByte(id >>> 8);
        }
        writeByte(id);
    }

    /** Writes a size with the shortest EBML varint encoding. */
    void writeSize(long size) {
        int bytes = sizeLength(size);
        writeSize(size, bytes);
    }

    /** Writes a size using exactly {@code bytes} bytes (so it can be patched later). */
    void writeSize(long size, int bytes) {
        long marker = 1L << (7 * bytes);
        long value = size | marker;
        for (int i = bytes - 1; i >= 0; i--) {
            writeByte((int) (value >>> (8 * i)));
        }
    }

    static int sizeLength(long size) {
        for (int n = 1; n <= 8; n++) {
            if (size < (1L << (7 * n)) - 1) {
                return n;
            }
        }
        throw new IllegalArgumentException("Size too large: " + size);
    }

    void writeUInt(int id, long value) {
        int bytes = 1;
        while (bytes < 8 && (value >>> (8 * bytes)) != 0) {
            bytes++;
        }
        writeId(id);
        writeSize(bytes);
        for (int i = bytes - 1; i >= 0; i--) {
            writeByte((int) (value >>> (8 * i)));
        }
    }

    void writeFloat(int id, double value) {
        writeId(id);
        writeSize(8);
        writeLongBigEndian(Double.doubleToLongBits(value));
    }

    void writeLongBigEndian(long v) {
        for (int i = 7; i >= 0; i--) {
            writeByte((int) (v >>> (8 * i)));
        }
    }

    void writeString(int id, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeBinary(id, bytes);
    }

    void writeBinary(int id, byte[] value) {
        writeId(id);
        writeSize(value.length);
        writeBytes(value);
    }

    /** Writes a master element whose children are already in {@code children}. */
    void writeMaster(int id, EbmlBuffer children) {
        writeId(id);
        writeSize(children.length());
        writeBytes(children.array(), 0, children.length());
    }
}
