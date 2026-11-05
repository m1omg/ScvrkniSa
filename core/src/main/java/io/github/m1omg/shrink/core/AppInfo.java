package io.github.m1omg.shrink.core;

/** Name, version and links of the application. */
public final class AppInfo {

    public static final String NAME = "Shrink!";
    /** Name without punctuation, for folders and file names. */
    public static final String ID = "Shrink";
    public static final String WEBSITE = "https://github.com/m1omg/ScvrkniSa";

    private AppInfo() {
    }

    /** Version from the jar manifest, or "dev" when running from an IDE. */
    public static String version() {
        Package p = AppInfo.class.getPackage();
        String v = p == null ? null : p.getImplementationVersion();
        return v == null || v.isEmpty() ? "dev" : v;
    }
}
