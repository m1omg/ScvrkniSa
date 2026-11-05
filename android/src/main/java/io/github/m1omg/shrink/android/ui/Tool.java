package io.github.m1omg.shrink.android.ui;

import androidx.fragment.app.Fragment;

import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ui.tools.CompressFragment;
import io.github.m1omg.shrink.android.ui.tools.ConvertFragment;
import io.github.m1omg.shrink.android.ui.tools.ExtractAudioFragment;
import io.github.m1omg.shrink.android.ui.tools.FrameRateFragment;
import io.github.m1omg.shrink.android.ui.tools.InfoFragment;
import io.github.m1omg.shrink.android.ui.tools.MuteFragment;
import io.github.m1omg.shrink.android.ui.tools.ResolutionFragment;
import io.github.m1omg.shrink.android.ui.tools.RotateFragment;
import io.github.m1omg.shrink.android.ui.tools.TrimFragment;

import java.util.function.Supplier;

/** The tools on the home screen, in display order. */
public enum Tool {
    COMPRESS(R.drawable.ic_compress, R.string.tool_compress_title, R.string.tool_compress_description,
            CompressFragment::new),
    TRIM(R.drawable.ic_trim, R.string.tool_trim_title, R.string.tool_trim_description, TrimFragment::new),
    ROTATE(R.drawable.ic_rotate, R.string.tool_rotate_title, R.string.tool_rotate_description, RotateFragment::new),
    MUTE(R.drawable.ic_mute, R.string.tool_mute_title, R.string.tool_mute_description, MuteFragment::new),
    EXTRACT_AUDIO(R.drawable.ic_audio, R.string.tool_extract_audio_title, R.string.tool_extract_audio_description,
            ExtractAudioFragment::new),
    CONVERT(R.drawable.ic_convert, R.string.tool_convert_title, R.string.tool_convert_description,
            ConvertFragment::new),
    RESOLUTION(R.drawable.ic_resolution, R.string.tool_resolution_title, R.string.tool_resolution_description,
            ResolutionFragment::new),
    FRAME_RATE(R.drawable.ic_framerate, R.string.tool_frame_rate_title, R.string.tool_frame_rate_description,
            FrameRateFragment::new),
    INFO(R.drawable.ic_info, R.string.tool_info_title, R.string.tool_info_description, InfoFragment::new);

    public final int icon;
    public final int title;
    public final int description;
    private final Supplier<Fragment> factory;

    Tool(int icon, int title, int description, Supplier<Fragment> factory) {
        this.icon = icon;
        this.title = title;
        this.description = description;
        this.factory = factory;
    }

    public Fragment create() {
        return factory.get();
    }

    /** Tools that need a video stream (all except audio extraction and information). */
    public boolean needsVideo() {
        return this != EXTRACT_AUDIO && this != INFO;
    }
}
