package io.github.m1omg.shrink.core.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the English and Slovak texts complete and consistent. */
class MessagesTest {

    private static final Pattern ARG = Pattern.compile("\\{(\\d+)}");

    private static Properties load(String name) throws IOException {
        Properties p = new Properties();
        try (InputStream in = MessagesTest.class.getResourceAsStream("/io/github/m1omg/shrink/i18n/" + name)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        Properties en = load("messages.properties");
        Properties sk = load("messages_sk.properties");
        Set<String> missingInSk = new TreeSet<>(en.stringPropertyNames());
        missingInSk.removeAll(sk.stringPropertyNames());
        Set<String> missingInEn = new TreeSet<>(sk.stringPropertyNames());
        missingInEn.removeAll(en.stringPropertyNames());
        assertTrue(missingInSk.isEmpty(), "Missing in Slovak: " + missingInSk);
        assertTrue(missingInEn.isEmpty(), "Missing in English: " + missingInEn);
    }

    @Test
    void placeholdersMatchAndPatternsAreValid() throws IOException {
        Properties en = load("messages.properties");
        Properties sk = load("messages_sk.properties");
        List<String> problems = new ArrayList<>();
        for (String key : en.stringPropertyNames()) {
            String e = en.getProperty(key);
            String s = sk.getProperty(key, "");
            if (!args(e).equals(args(s))) {
                problems.add(key + " has different arguments");
            }
            for (String text : new String[] {e, s}) {
                if (text.indexOf('\'') >= 0) {
                    problems.add(key + " contains an ASCII apostrophe");
                }
                if (!args(text).isEmpty()) {
                    new MessageFormat(text, Locale.ENGLISH); // throws if malformed
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static Set<String> args(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = ARG.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    void lookupFallsBackAndFormats() {
        I18n.setLocale(I18n.SLOVAK);
        assertEquals("Komprimovať", I18n.tr("tool.compress.title"));
        assertEquals("Ukážka (5 s): 412 KB", I18n.tr("preview.size", "412 KB", "5 s"));
        I18n.setLocale(Locale.ENGLISH);
        assertEquals("Compress", I18n.tr("tool.compress.title"));
        assertEquals("!does.not.exist!", I18n.tr("does.not.exist"));
        assertFalse(I18n.current().isSlovak());
    }
}
