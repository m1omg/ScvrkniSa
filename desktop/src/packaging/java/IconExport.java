import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders the SVG logo into the icon files the installers need: a 512 px PNG
 * (Linux), an .icns (macOS) and an .ico (Windows). Run by the Gradle task
 * "generateIcons"; uses only Java and JSVG.
 */
public final class IconExport {

    private IconExport() {
    }

    public static void main(String[] args) throws IOException {
        File svg = new File(args[0]);
        File out = new File(args[1]);
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IOException("Cannot create " + out);
        }
        SVGDocument doc = new SVGLoader().load(svg.toURI().toURL());
        if (doc == null) {
            throw new IOException("Cannot read " + svg);
        }
        ImageIO.write(render(doc, 512), "png", new File(out, "shrink.png"));
        writeIcns(doc, new File(out, "shrink.icns"));
        writeIco(doc, new File(out, "shrink.ico"));
    }

    static BufferedImage render(SVGDocument doc, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double scale = size / (double) doc.size().width;
        g.scale(scale, scale);
        doc.render(null, g);
        g.dispose();
        return img;
    }

    static byte[] png(SVGDocument doc, int size) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(render(doc, size), "png", bytes);
        return bytes.toByteArray();
    }

    /** Apple icon file: "icns" header followed by PNG images tagged with their size. */
    static void writeIcns(SVGDocument doc, File file) throws IOException {
        Map<String, Integer> types = new LinkedHashMap<>();
        types.put("icp4", 16);
        types.put("icp5", 32);
        types.put("ic11", 32);
        types.put("ic12", 64);
        types.put("ic07", 128);
        types.put("ic13", 256);
        types.put("ic08", 256);
        types.put("ic14", 512);
        types.put("ic09", 512);
        types.put("ic10", 1024);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        for (Map.Entry<String, Integer> e : types.entrySet()) {
            byte[] image = png(doc, e.getValue());
            data.writeBytes(e.getKey());
            data.writeInt(image.length + 8);
            data.write(image);
        }
        try (DataOutputStream f = new DataOutputStream(new FileOutputStream(file))) {
            f.writeBytes("icns");
            f.writeInt(body.size() + 8);
            body.writeTo(f);
        }
    }

    /** Windows icon file with PNG-compressed images. */
    static void writeIco(SVGDocument doc, File file) throws IOException {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        byte[][] images = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            images[i] = png(doc, sizes[i]);
        }
        int offset = 6 + 16 * sizes.length;
        ByteBuffer header = ByteBuffer.allocate(offset).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i] >= 256 ? 0 : sizes[i];
            header.put((byte) s).put((byte) s).put((byte) 0).put((byte) 0);
            header.putShort((short) 1).putShort((short) 32);
            header.putInt(images[i].length).putInt(offset);
            offset += images[i].length;
        }
        try (FileOutputStream f = new FileOutputStream(file)) {
            f.write(header.array());
            for (byte[] image : images) {
                f.write(image);
            }
        }
    }
}
