package io.github.m1omg.shrink.android.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.m1omg.shrink.android.BuildConfig;
import io.github.m1omg.shrink.android.R;
import io.github.m1omg.shrink.android.ShrinkApp;
import io.github.m1omg.shrink.android.media.EncoderSupport;
import io.github.m1omg.shrink.android.media.OutputStore;
import io.github.m1omg.shrink.core.AppInfo;
import io.github.m1omg.shrink.core.i18n.I18n;
import io.github.m1omg.shrink.core.i18n.Language;
import io.github.m1omg.shrink.core.model.Container;
import io.github.m1omg.shrink.core.model.VideoCodec;
import io.github.m1omg.shrink.core.store.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Settings, applied immediately. */
public final class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        ShrinkApp app = ShrinkApp.get();
        Settings s = app.settings();
        NestedScrollView scroll = new NestedScrollView(c);
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        column.setPadding(pad, Ui.dp(c, 4), pad, Ui.dp(c, 32));
        scroll.addView(column);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.ImageButton back = new android.widget.ImageButton(c);
        back.setImageResource(R.drawable.ic_chevron_left);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.color(c, R.color.text_primary)));
        back.setBackgroundResource(android.R.color.transparent);
        back.setContentDescription(I18n.tr("nav.back"));
        back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        header.addView(Ui.title(c, I18n.tr("settings.title")));
        column.addView(header);

        // Language: AppCompat stores the choice and recreates the screens.
        column.addView(Ui.section(c, I18n.tr("settings.language"), Ui.chips(c, Arrays.asList(Language.values()),
                s.getLanguage(), l -> l == Language.SYSTEM ? I18n.tr("settings.language.system") : l.nativeName(),
                l -> {
                    s.setLanguage(l);
                    app.saveSettings();
                    AppCompatDelegate.setApplicationLocales(l == Language.SYSTEM
                            ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(l.id()));
                })), Ui.matchWrap(c, 8));

        column.addView(Ui.section(c, I18n.tr("settings.theme"), Ui.chips(c, Arrays.asList(Settings.Theme.values()),
                s.getTheme(), t -> I18n.tr("settings.theme." + t.name().toLowerCase(Locale.ROOT)), t -> {
                    s.setTheme(t);
                    app.saveSettings();
                    app.applyNightMode();
                })), Ui.matchWrap(c, 12));

        List<VideoCodec> codecs = EncoderSupport.videoCodecs();
        column.addView(Ui.section(c, I18n.tr("settings.codec"), Ui.chips(c, codecs,
                codecs.contains(s.getVideoCodec()) ? s.getVideoCodec() : VideoCodec.H264,
                v -> v.displayName() + " – " + I18n.tr("codec." + v.id() + ".hint"), v -> {
                    s.setVideoCodec(v);
                    app.saveSettings();
                })), Ui.matchWrap(c, 12));

        List<Container> containers = new ArrayList<>();
        for (Container ct : Container.values()) {
            if (EncoderSupport.canWrite(ct)) {
                containers.add(ct);
            }
        }
        column.addView(Ui.section(c, I18n.tr("settings.container"), Ui.chips(c, containers, s.getContainer(),
                Container::displayName, ct -> {
                    s.setContainer(ct);
                    app.saveSettings();
                })), Ui.matchWrap(c, 12));

        List<Settings.OutputLocation> locations = Arrays.asList(Settings.OutputLocation.NEXT_TO_SOURCE,
                Settings.OutputLocation.ASK);
        Settings.OutputLocation current = s.getOutputLocation() == Settings.OutputLocation.ASK
                ? Settings.OutputLocation.ASK : Settings.OutputLocation.NEXT_TO_SOURCE;
        TextView folder = Ui.secondary(c, OutputStore.folderLabel(false) + " · " + OutputStore.folderLabel(true));
        column.addView(Ui.section(c, I18n.tr("android.saveLocation"), Ui.chips(c, locations, current,
                l -> I18n.tr(l == Settings.OutputLocation.ASK ? "android.saveLocation.ask"
                        : "android.saveLocation.gallery"), l -> {
                    s.setOutputLocation(l);
                    app.saveSettings();
                }), folder), Ui.matchWrap(c, 12));

        GlassCard general = new GlassCard(c);
        general.addView(toggle(c, I18n.tr("settings.animatedBackground"), s.isAnimatedBackground(), v -> {
            s.setAnimatedBackground(v);
            app.saveSettings();
            View bg = requireActivity().findViewById(R.id.background);
            if (bg instanceof GlassBackgroundView) {
                ((GlassBackgroundView) bg).setAnimated(v);
            }
        }));
        general.addView(toggle(c, I18n.tr("settings.showAdvanced"), s.isShowAdvanced(), v -> {
            s.setShowAdvanced(v);
            app.saveSettings();
        }), Ui.matchWrap(c, 6));
        general.addView(toggle(c, I18n.tr("settings.history.keep"), s.isHistoryEnabled(), v -> {
            s.setHistoryEnabled(v);
            app.saveSettings();
        }), Ui.matchWrap(c, 6));
        MaterialButton clear = Ui.glassButton(c, I18n.tr("settings.history.clear"), R.drawable.ic_trash);
        clear.setOnClickListener(v -> new MaterialAlertDialogBuilder(c)
                .setMessage(I18n.tr("history.clearConfirm"))
                .setPositiveButton(I18n.tr("common.ok"), (d, w) -> app.history().clear())
                .setNegativeButton(I18n.tr("common.cancel"), null)
                .show());
        general.addView(clear, Ui.matchWrap(c, 10));
        column.addView(general, Ui.matchWrap(c, 12));

        GlassCard about = new GlassCard(c);
        android.widget.ImageView logo = new android.widget.ImageView(c);
        logo.setImageResource(R.drawable.ic_logo);
        about.addView(logo, new LinearLayout.LayoutParams(Ui.dp(c, 64), Ui.dp(c, 64)));
        about.addView(Ui.title(c, AppInfo.NAME), Ui.matchWrap(c, 10));
        about.addView(Ui.secondary(c, I18n.tr("settings.about.version", BuildConfig.VERSION_NAME)), Ui.matchWrap(c, 2));
        about.addView(Ui.body(c, I18n.tr("about.text")), Ui.matchWrap(c, 10));
        about.addView(Ui.secondary(c, I18n.tr("settings.about.license")), Ui.matchWrap(c, 6));
        MaterialButton website = Ui.glassButton(c, I18n.tr("settings.about.website"), R.drawable.ic_external);
        website.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AppInfo.WEBSITE))));
        about.addView(website, Ui.matchWrap(c, 12));
        column.addView(about, Ui.matchWrap(c, 12));
        return scroll;
    }

    private static View toggle(Context c, String label, boolean value, Consumer<Boolean> onChange) {
        MaterialSwitch sw = new MaterialSwitch(c);
        sw.setText(label);
        sw.setTextColor(Ui.color(c, R.color.text_primary));
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, checked) -> onChange.accept(checked));
        return sw;
    }
}
