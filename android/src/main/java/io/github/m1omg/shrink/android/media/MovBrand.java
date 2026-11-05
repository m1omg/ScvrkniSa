package io.github.m1omg.shrink.android.media;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * MOV and MP4 share the same structure. After writing an MP4, changing the
 * "ftyp" brand to QuickTime ({@code qt  }) turns it into a proper .mov file.
 */
public final class MovBrand {

    private MovBrand() {
    }

    public static void apply(File file) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(file, "rw")) {
            if (f.length() < 16) {
                return;
            }
            int size = f.readInt();
            byte[] type = new byte[4];
            f.readFully(type);
            if (!"ftyp".equals(new String(type, java.nio.charset.StandardCharsets.US_ASCII)) || size < 16
                    || size > f.length()) {
                return;
            }
            byte[] qt = "qt  ".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            f.write(qt);                // major brand
            f.writeInt(0x00000200);     // minor version, as QuickTime and ffmpeg write it
            for (int pos = 16; pos + 4 <= size; pos += 4) {
                f.write(qt);            // compatible brands
            }
        }
    }
}
