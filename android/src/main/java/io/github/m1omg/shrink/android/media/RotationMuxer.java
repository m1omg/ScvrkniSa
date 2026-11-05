package io.github.m1omg.shrink.android.media;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.muxer.BufferInfo;
import androidx.media3.muxer.Muxer;
import androidx.media3.muxer.MuxerException;

import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;

/**
 * Wraps another muxer and changes only the video's orientation flag. Used for
 * lossless rotation: the frames are copied, players rotate them on display.
 */
@OptIn(markerClass = UnstableApi.class)
public final class RotationMuxer implements Muxer {

    /** Factory that wraps {@code delegate} and sets the clockwise rotation {@code degrees}. */
    public static final class Factory implements Muxer.Factory {
        private final Muxer.Factory delegate;
        private final int degrees;

        public Factory(Muxer.Factory delegate, int degrees) {
            this.delegate = delegate;
            this.degrees = ((degrees % 360) + 360) % 360;
        }

        @Override
        public Muxer create(String path) throws MuxerException {
            return new RotationMuxer(delegate.create(path), degrees);
        }

        @Override
        public ImmutableList<String> getSupportedSampleMimeTypes(int trackType) {
            return delegate.getSupportedSampleMimeTypes(trackType);
        }
    }

    private final Muxer delegate;
    private final int degrees;

    private RotationMuxer(Muxer delegate, int degrees) {
        this.delegate = delegate;
        this.degrees = degrees;
    }

    @Override
    public int addTrack(Format format) throws MuxerException {
        if (format.sampleMimeType != null && MimeTypes.isVideo(format.sampleMimeType)) {
            format = format.buildUpon().setRotationDegrees(degrees).build();
        }
        return delegate.addTrack(format);
    }

    @Override
    public void writeSampleData(int trackId, ByteBuffer byteBuffer, BufferInfo bufferInfo) throws MuxerException {
        delegate.writeSampleData(trackId, byteBuffer, bufferInfo);
    }

    @Override
    public void addMetadataEntry(Metadata.Entry metadataEntry) {
        delegate.addMetadataEntry(metadataEntry);
    }

    @Override
    public void close() throws MuxerException {
        delegate.close();
    }
}
