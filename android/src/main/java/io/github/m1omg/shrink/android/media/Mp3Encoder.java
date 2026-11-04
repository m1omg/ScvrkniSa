package io.github.m1omg.shrink.android.media;

import de.sciss.jump3r.mp3.BitStream;
import de.sciss.jump3r.mp3.GainAnalysis;
import de.sciss.jump3r.mp3.GetAudio;
import de.sciss.jump3r.mp3.ID3Tag;
import de.sciss.jump3r.mp3.Lame;
import de.sciss.jump3r.mp3.LameGlobalFlags;
import de.sciss.jump3r.mp3.MPEGMode;
import de.sciss.jump3r.mp3.Parse;
import de.sciss.jump3r.mp3.Presets;
import de.sciss.jump3r.mp3.Quantize;
import de.sciss.jump3r.mp3.QuantizePVT;
import de.sciss.jump3r.mp3.Reservoir;
import de.sciss.jump3r.mp3.Takehiro;
import de.sciss.jump3r.mp3.VBRTag;
import de.sciss.jump3r.mp3.VbrMode;
import de.sciss.jump3r.mp3.Version;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;

/**
 * MP3 encoder (a pure Java port of LAME). Android has no MP3 encoder of its
 * own, so decoded PCM audio is fed through this class.
 *
 * <p>The jump3r library's convenience class uses javax.sound, which Android
 * lacks, so this wires up the encoder modules directly, exactly as that class
 * does internally.
 */
public final class Mp3Encoder {

    private final Lame lame = new Lame();
    private final LameGlobalFlags flags;
    private final int channels;
    private int[] left = new int[0];
    private int[] right = new int[0];

    /**
     * @param sampleRate input sample rate in Hz (8000..48000)
     * @param channels   1 or 2 (interleaved input)
     * @param kbps       constant bitrate in kilobits per second, e.g. 192
     */
    public Mp3Encoder(int sampleRate, int channels, int kbps) {
        this.channels = channels <= 1 ? 1 : 2;
        GetAudio getAudio = new GetAudio();
        GainAnalysis gain = new GainAnalysis();
        BitStream bitStream = new BitStream();
        Presets presets = new Presets();
        QuantizePVT quantizePvt = new QuantizePVT();
        Quantize quantize = new Quantize();
        VBRTag vbrTag = new VBRTag();
        Version version = new Version();
        ID3Tag id3 = new ID3Tag();
        Reservoir reservoir = new Reservoir();
        Takehiro takehiro = new Takehiro();
        Parse parse = new Parse();
        MPGLib mpg = new MPGLib();
        Interface intf = new Interface();
        Common common = new Common();

        lame.setModules(gain, bitStream, presets, quantizePvt, quantize, vbrTag, version, id3, mpg);
        bitStream.setModules(gain, mpg, version, vbrTag);
        id3.setModules(bitStream, version);
        presets.setModules(lame);
        quantize.setModules(bitStream, reservoir, quantizePvt, takehiro);
        quantizePvt.setModules(takehiro, reservoir, lame.enc.psy);
        reservoir.setModules(bitStream);
        takehiro.setModules(quantizePvt);
        vbrTag.setModules(lame, bitStream, version);
        getAudio.setModules(parse, mpg);
        parse.setModules(version, id3, presets);
        mpg.setModules(intf, common);
        intf.setModules(vbrTag, common);

        flags = lame.lame_init();
        flags.num_channels = this.channels;
        flags.in_samplerate = sampleRate;
        flags.out_samplerate = sampleRate;
        flags.mode = this.channels == 1 ? MPEGMode.MONO : MPEGMode.JOINT_STEREO;
        flags.VBR = VbrMode.vbr_off;
        flags.brate = kbps;
        flags.quality = 3;
        flags.bWriteVbrTag = false;
        id3.id3tag_init(flags);
        flags.write_id3tag_automatic = false;
        if (lame.lame_init_params(flags) < 0) {
            throw new IllegalArgumentException("Unsupported MP3 settings: " + sampleRate + " Hz, " + kbps + " kbps");
        }
    }

    /** Size of an output buffer that is always big enough for {@code frames} input frames. */
    public static int outputSize(int frames) {
        return (int) (1.25 * frames) + 7200;
    }

    /**
     * Encodes interleaved 16-bit samples.
     *
     * @return number of MP3 bytes written to {@code out}
     */
    public int encode(short[] interleaved, int frames, byte[] out) {
        if (left.length < frames) {
            left = new int[frames];
            right = new int[frames];
        }
        for (int i = 0; i < frames; i++) {
            if (channels == 1) {
                left[i] = interleaved[i] << 16;
                right[i] = left[i];
            } else {
                left[i] = interleaved[2 * i] << 16;
                right[i] = interleaved[2 * i + 1] << 16;
            }
        }
        int n = lame.lame_encode_buffer_int(flags, left, right, frames, out, 0, out.length);
        if (n < 0) {
            throw new IllegalStateException("MP3 encoder error " + n);
        }
        return n;
    }

    /** Writes the remaining buffered audio; call once at the end. */
    public int flush(byte[] out) {
        int n = lame.lame_encode_flush(flags, out, 0, out.length);
        lame.lame_close(flags);
        return Math.max(n, 0);
    }
}
