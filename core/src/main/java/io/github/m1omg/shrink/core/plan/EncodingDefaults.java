package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.EncoderSpeed;
import io.github.m1omg.shrink.core.model.VideoCodec;

/**
 * User preferences that influence planning (from the settings screen).
 *
 * @param codec        preferred video codec for compression
 * @param container    preferred container for compression
 * @param speed        encoder speed preset
 * @param twoPass      use two-pass encoding when a bitrate is targeted
 * @param hardware     prefer hardware encoders
 * @param keepMetadata copy metadata such as creation date and location
 */
public record EncodingDefaults(VideoCodec codec, Container container, EncoderSpeed speed,
                               boolean twoPass, boolean hardware, boolean keepMetadata) {

    public static EncodingDefaults standard() {
        return new EncodingDefaults(VideoCodec.H264, Container.MP4, EncoderSpeed.BALANCED, true, false, true);
    }

    public EncodingDefaults {
        if (codec == null || container == null || speed == null) {
            throw new IllegalArgumentException("codec, container and speed are required");
        }
    }
}
