package io.github.m1omg.shrink.core.mkv;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for H.264/H.265 bitstreams. Encoders on Android produce "Annex B"
 * data (NAL units separated by {@code 00 00 01} start codes), while MP4 and
 * Matroska store each NAL unit with a 4-byte length in front.
 */
public final class NalUnits {

    private NalUnits() {
    }

    /** Whether the data starts with a 3- or 4-byte start code. */
    public static boolean hasStartCode(byte[] d, int offset, int length) {
        if (length >= 4 && d[offset] == 0 && d[offset + 1] == 0 && d[offset + 2] == 0 && d[offset + 3] == 1) {
            return true;
        }
        return length >= 3 && d[offset] == 0 && d[offset + 1] == 0 && d[offset + 2] == 1;
    }

    /** Splits Annex B data into NAL units (without start codes). */
    public static List<byte[]> split(byte[] d, int offset, int length) {
        List<byte[]> units = new ArrayList<>();
        int end = offset + length;
        int i = findStartCode(d, offset, end);
        if (i < 0) {
            byte[] copy = new byte[length];
            System.arraycopy(d, offset, copy, 0, length);
            units.add(copy);
            return units;
        }
        while (i >= 0) {
            int nalStart = i + startCodeLength(d, i);
            int next = findStartCode(d, nalStart, end);
            int nalEnd = next < 0 ? end : next;
            // Trailing zero bytes belong to the next start code, not to this NAL unit.
            while (nalEnd > nalStart && d[nalEnd - 1] == 0 && next >= 0) {
                nalEnd--;
            }
            if (nalEnd > nalStart) {
                byte[] nal = new byte[nalEnd - nalStart];
                System.arraycopy(d, nalStart, nal, 0, nal.length);
                units.add(nal);
            }
            i = next;
        }
        return units;
    }

    private static int findStartCode(byte[] d, int from, int end) {
        for (int i = from; i + 2 < end; i++) {
            if (d[i] == 0 && d[i + 1] == 0 && d[i + 2] == 1) {
                return (i > from && d[i - 1] == 0) ? i - 1 : i;
            }
        }
        return -1;
    }

    private static int startCodeLength(byte[] d, int i) {
        return d[i + 2] == 1 ? 3 : 4;
    }

    /** Converts Annex B data to 4-byte length-prefixed NAL units. Data without start codes is returned unchanged. */
    public static byte[] toLengthPrefixed(byte[] d, int offset, int length) {
        if (!hasStartCode(d, offset, length)) {
            byte[] copy = new byte[length];
            System.arraycopy(d, offset, copy, 0, length);
            return copy;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(length + 16);
        for (byte[] nal : split(d, offset, length)) {
            int n = nal.length;
            out.write(n >>> 24);
            out.write(n >>> 16);
            out.write(n >>> 8);
            out.write(n);
            out.write(nal, 0, n);
        }
        return out.toByteArray();
    }

    /** Removes a leading start code if present. */
    public static byte[] stripStartCode(byte[] nal) {
        if (nal.length >= 4 && nal[0] == 0 && nal[1] == 0 && nal[2] == 0 && nal[3] == 1) {
            byte[] r = new byte[nal.length - 4];
            System.arraycopy(nal, 4, r, 0, r.length);
            return r;
        }
        if (nal.length >= 3 && nal[0] == 0 && nal[1] == 0 && nal[2] == 1) {
            byte[] r = new byte[nal.length - 3];
            System.arraycopy(nal, 3, r, 0, r.length);
            return r;
        }
        return nal;
    }

    /** H.264 NAL unit type (5 = IDR slice, 7 = SPS, 8 = PPS). */
    public static int h264Type(byte[] nal) {
        return nal.length == 0 ? -1 : nal[0] & 0x1F;
    }

    /**
     * Builds an {@code AVCDecoderConfigurationRecord} ("avcC"), which Matroska
     * stores as CodecPrivate for H.264.
     *
     * @param parameterSets SPS and PPS units, with or without start codes, in any
     *                      order; one buffer may also hold several units
     */
    public static byte[] avcDecoderConfigurationRecord(List<byte[]> parameterSets) {
        List<byte[]> sps = new ArrayList<>();
        List<byte[]> pps = new ArrayList<>();
        for (byte[] buffer : parameterSets) {
            for (byte[] nal : split(buffer, 0, buffer.length)) {
                int type = h264Type(nal);
                if (type == 7) {
                    sps.add(nal);
                } else if (type == 8) {
                    pps.add(nal);
                }
            }
        }
        if (sps.isEmpty() || sps.get(0).length < 4) {
            throw new IllegalArgumentException("No H.264 SPS found");
        }
        byte[] first = sps.get(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1);              // configurationVersion
        out.write(first[1]);       // AVCProfileIndication
        out.write(first[2]);       // profile_compatibility
        out.write(first[3]);       // AVCLevelIndication
        out.write(0xFF);           // 6 reserved bits + lengthSizeMinusOne = 3 (4-byte lengths)
        out.write(0xE0 | sps.size());
        for (byte[] s : sps) {
            out.write(s.length >>> 8);
            out.write(s.length);
            out.write(s, 0, s.length);
        }
        out.write(pps.size());
        for (byte[] p : pps) {
            out.write(p.length >>> 8);
            out.write(p.length);
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }
}
