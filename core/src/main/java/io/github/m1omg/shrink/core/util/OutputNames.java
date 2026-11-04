package io.github.m1omg.shrink.core.util;

import java.util.function.Predicate;

/** Builds output file names such as {@code holiday-compressed.mp4}. */
public final class OutputNames {

    private OutputNames() {
    }

    /** File name without its extension. */
    public static String baseName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "video";
        }
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.isEmpty() ? "video" : name;
    }

    /** Extension of a file name in lower case, without the dot, or an empty string. */
    public static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (dot <= slash + 1 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Suggested name for a result: the original base name plus a suffix and the
     * new extension, made unique with " (2)", " (3)"... when {@code exists} says a
     * name is taken.
     */
    public static String suggest(String inputName, String suffix, String extension, Predicate<String> exists) {
        String base = baseName(inputName) + (suffix == null ? "" : suffix);
        String candidate = base + "." + extension;
        if (exists == null || !exists.test(candidate)) {
            return candidate;
        }
        for (int i = 2; i < 10_000; i++) {
            candidate = base + " (" + i + ")." + extension;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        return base + "-" + System.currentTimeMillis() + "." + extension;
    }

    /** Replaces characters that are not allowed in file names on common systems. */
    public static String sanitize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || "/\\:*?\"<>|".indexOf(c) >= 0) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "video" : result;
    }
}
