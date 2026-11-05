package io.github.m1omg.shrink.core.i18n;

import java.util.Locale;

/** Languages of the user interface. */
public enum Language {
    /** Follow the operating system (Slovak if the system is Slovak, otherwise English). */
    SYSTEM("system"),
    ENGLISH("en"),
    SLOVAK("sk");

    private final String id;

    Language(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Name of the language written in that language, for the language menu. */
    public String nativeName() {
        switch (this) {
            case ENGLISH:
                return "English";
            case SLOVAK:
                return "Slovenčina";
            default:
                return "";
        }
    }

    /** The locale to use for this choice. */
    public Locale toLocale() {
        switch (this) {
            case ENGLISH:
                return Locale.ENGLISH;
            case SLOVAK:
                return I18n.SLOVAK;
            default:
                return systemLocale();
        }
    }

    /** Slovak when the system language is Slovak, otherwise English. */
    public static Locale systemLocale() {
        Locale system = Locale.getDefault();
        return "sk".equals(system.getLanguage()) ? I18n.SLOVAK : Locale.ENGLISH;
    }

    public static Language fromId(String id) {
        if (id != null) {
            for (Language l : values()) {
                if (l.id.equalsIgnoreCase(id)) {
                    return l;
                }
            }
        }
        return SYSTEM;
    }
}
