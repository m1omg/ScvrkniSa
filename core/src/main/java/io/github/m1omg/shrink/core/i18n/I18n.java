package io.github.m1omg.shrink.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Translated texts. The texts live in {@code messages.properties} (English) and
 * {@code messages_sk.properties} (Slovak) next to this class.
 *
 * <p>Usage: {@code I18n.tr("tool.trim.title")} or with arguments
 * {@code I18n.tr("preview.size", "412 KB", "5 s")}. Arguments use
 * {@link MessageFormat} syntax ({@code {0}}, {@code {1}}). Texts never contain
 * ASCII apostrophes (MessageFormat treats them specially); use ’ instead.
 */
public final class I18n {

    public static final Locale SLOVAK = Locale.forLanguageTag("sk-SK");
    private static final String BUNDLE = "io.github.m1omg.shrink.i18n.messages";

    private static volatile I18n current = new I18n(Locale.ENGLISH);

    private final Locale locale;
    private final ResourceBundle bundle;
    private final Formats formats;

    private I18n(Locale locale) {
        this.locale = locale;
        this.bundle = ResourceBundle.getBundle(BUNDLE, locale, I18n.class.getClassLoader(), new Utf8Control());
        this.formats = new Formats(locale);
    }

    /** Switches the language used by {@link #tr}. */
    public static void setLocale(Locale locale) {
        current = new I18n(locale == null ? Locale.ENGLISH : locale);
    }

    public static I18n current() {
        return current;
    }

    /** Translated text for {@code key}, with optional arguments. */
    public static String tr(String key, Object... args) {
        return current.text(key, args);
    }

    /** Locale-aware number, size and time formatting for the current language. */
    public static Formats fmt() {
        return current.formats;
    }

    public Locale locale() {
        return locale;
    }

    public boolean isSlovak() {
        return "sk".equals(locale.getLanguage());
    }

    public Formats formats() {
        return formats;
    }

    /** Whether a text exists for this key. */
    public boolean has(String key) {
        try {
            bundle.getString(key);
            return true;
        } catch (MissingResourceException e) {
            return false;
        }
    }

    public String text(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, locale).format(args);
    }

    /** Reads .properties files as UTF-8 (older Java and Android default to ISO-8859-1). */
    private static final class Utf8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader,
                                        boolean reload) throws IOException {
            String resource = toResourceName(toBundleName(baseName, locale), "properties");
            InputStream stream = loader.getResourceAsStream(resource);
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            // Fall back to the English base bundle, not to the JVM default locale.
            return null;
        }
    }
}
