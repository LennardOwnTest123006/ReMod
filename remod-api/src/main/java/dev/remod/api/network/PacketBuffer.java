package dev.remod.api.network;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A growable byte buffer with Minecraft's wire encodings.
 *
 * <p>Uses the same VarInt/length-prefixed-UTF-8 conventions as Minecraft's own
 * packet buffer, so payloads written here travel over the vanilla custom-payload
 * channel unchanged.</p>
 *
 * <p>Reads are bounds-checked and strings are length-capped: a payload arriving
 * from another player is untrusted input, and a malformed one must raise a
 * clear {@link MalformedPacketException} rather than allocating a gigabyte or
 * throwing an opaque index error deep inside a mod.</p>
 */
public final class PacketBuffer {

    /** Hard cap on a single decoded string, matching Minecraft's own limit. */
    public static final int MAX_STRING_LENGTH = 32767;

    private byte[] data;
    private int writeIndex;
    private int readIndex;

    public PacketBuffer() {
        this(64);
    }

    public PacketBuffer(int initialCapacity) {
        this.data = new byte[Math.max(16, initialCapacity)];
    }

    /** Wraps existing bytes for reading. */
    public static PacketBuffer wrap(byte[] bytes) {
        PacketBuffer buffer = new PacketBuffer(Math.max(16, bytes.length));
        System.arraycopy(bytes, 0, buffer.data, 0, bytes.length);
        buffer.writeIndex = bytes.length;
        return buffer;
    }

    /** The written bytes, ready to send. */
    public byte[] toByteArray() {
        byte[] out = new byte[writeIndex];
        System.arraycopy(data, 0, out, 0, writeIndex);
        return out;
    }

    public int readableBytes() {
        return writeIndex - readIndex;
    }

    public int size() {
        return writeIndex;
    }

    private void ensure(int extra) {
        if (writeIndex + extra <= data.length) {
            return;
        }
        int capacity = Math.max(data.length * 2, writeIndex + extra);
        byte[] grown = new byte[capacity];
        System.arraycopy(data, 0, grown, 0, writeIndex);
        data = grown;
    }

    private void require(int count) {
        if (readableBytes() < count) {
            throw new MalformedPacketException(
                    "Packet ended early: needed " + count + " more byte(s) but only "
                            + readableBytes() + " remain");
        }
    }

    public PacketBuffer writeByte(int value) {
        ensure(1);
        data[writeIndex++] = (byte) value;
        return this;
    }

    public byte readByte() {
        require(1);
        return data[readIndex++];
    }

    public PacketBuffer writeBoolean(boolean value) {
        return writeByte(value ? 1 : 0);
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public PacketBuffer writeInt(int value) {
        ensure(4);
        data[writeIndex++] = (byte) (value >>> 24);
        data[writeIndex++] = (byte) (value >>> 16);
        data[writeIndex++] = (byte) (value >>> 8);
        data[writeIndex++] = (byte) value;
        return this;
    }

    public int readInt() {
        require(4);
        return ((data[readIndex++] & 0xFF) << 24)
                | ((data[readIndex++] & 0xFF) << 16)
                | ((data[readIndex++] & 0xFF) << 8)
                | (data[readIndex++] & 0xFF);
    }

    public PacketBuffer writeLong(long value) {
        writeInt((int) (value >>> 32));
        writeInt((int) value);
        return this;
    }

    public long readLong() {
        return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
    }

    public PacketBuffer writeDouble(double value) {
        return writeLong(Double.doubleToLongBits(value));
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public PacketBuffer writeFloat(float value) {
        return writeInt(Float.floatToIntBits(value));
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    /** Minecraft's 7-bit-per-byte variable-length integer. */
    public PacketBuffer writeVarInt(int value) {
        int remaining = value;
        while (true) {
            if ((remaining & ~0x7F) == 0) {
                writeByte(remaining);
                return this;
            }
            writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
    }

    public int readVarInt() {
        int result = 0;
        int shift = 0;
        while (true) {
            byte current = readByte();
            result |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift >= 35) {
                throw new MalformedPacketException("VarInt is longer than 5 bytes");
            }
        }
    }

    /** A length-prefixed UTF-8 string. */
    public PacketBuffer writeString(String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_LENGTH) {
            throw new MalformedPacketException(
                    "String is " + bytes.length + " bytes, over the " + MAX_STRING_LENGTH
                            + " byte limit");
        }
        writeVarInt(bytes.length);
        ensure(bytes.length);
        System.arraycopy(bytes, 0, data, writeIndex, bytes.length);
        writeIndex += bytes.length;
        return this;
    }

    public String readString() {
        int length = readVarInt();
        if (length < 0 || length > MAX_STRING_LENGTH) {
            throw new MalformedPacketException(
                    "String claims to be " + length + " bytes, which is out of range");
        }
        require(length);
        String value = new String(data, readIndex, length, StandardCharsets.UTF_8);
        readIndex += length;
        return value;
    }

    public PacketBuffer writeUuid(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
        return this;
    }

    public UUID readUuid() {
        long most = readLong();
        long least = readLong();
        return new UUID(most, least);
    }

    public PacketBuffer writeBytes(byte[] bytes) {
        writeVarInt(bytes.length);
        ensure(bytes.length);
        System.arraycopy(bytes, 0, data, writeIndex, bytes.length);
        writeIndex += bytes.length;
        return this;
    }

    public byte[] readBytes() {
        int length = readVarInt();
        if (length < 0) {
            throw new MalformedPacketException("Byte array claims a negative length");
        }
        require(length);
        byte[] out = new byte[length];
        System.arraycopy(data, readIndex, out, 0, length);
        readIndex += length;
        return out;
    }

    /** Rewinds the read cursor to the start. */
    public PacketBuffer resetReader() {
        readIndex = 0;
        return this;
    }

    /** Discards everything written so the buffer can be reused. */
    public PacketBuffer clear() {
        writeIndex = 0;
        readIndex = 0;
        return this;
    }

    @Override
    public String toString() {
        ByteArrayOutputStream ignored = new ByteArrayOutputStream();
        return "PacketBuffer[" + writeIndex + " bytes, " + readableBytes() + " readable]"
                + (ignored.size() == 0 ? "" : "");
    }
}
