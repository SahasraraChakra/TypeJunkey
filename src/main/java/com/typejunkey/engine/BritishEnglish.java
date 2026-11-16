package com.typejunkey.engine;

import com.typejunkey.util.Json;
import com.typejunkey.util.Resources;

import java.util.HashMap;
import java.util.Map;

/** American to British spelling replacement for English word lists and quotes. */
public final class BritishEnglish {
    private BritishEnglish() {}

    private static Map<String, String> map;

    private static synchronized Map<String, String> map() {
        if (map == null) {
            map = new HashMap<>();
            try {
                String s = Resources.text(Resources.readBundled("others/british-english.json"));
                if (s != null) for (Map.Entry<String, Object> e : Json.obj(Json.parse(s)).entrySet()) map.put(e.getKey(), String.valueOf(e.getValue()));
            } catch (RuntimeException ignored) { }
        }
        return map;
    }

    /** Replaces the word (keeping capitalisation and trailing punctuation) when a British spelling is known. */
    public static String apply(String word) {
        if (word.isEmpty()) return word;
        int end = word.length();
        while (end > 0 && !Character.isLetter(word.charAt(end - 1))) end--;
        int start = 0;
        while (start < end && !Character.isLetter(word.charAt(start))) start++;
        String core = word.substring(start, end);
        String rep = map().get(core.toLowerCase());
        if (rep == null) return word;
        if (Character.isUpperCase(core.charAt(0))) rep = Character.toUpperCase(rep.charAt(0)) + rep.substring(1);
        return word.substring(0, start) + rep + word.substring(end);
    }
}
