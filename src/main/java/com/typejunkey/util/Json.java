package com.typejunkey.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny dependency-free JSON reader (objects, arrays, strings, numbers, booleans, null). */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json j = new Json(text);
        return j.value();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return (List<Object>) o; }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') i++; else break;
        }
    }

    private Object value() {
        ws();
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': i += 4; return Boolean.TRUE;
            case 'f': i += 5; return Boolean.FALSE;
            case 'n': i += 4; return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            String k = string();
            ws(); i++; // ':'
            m.put(k, value());
            ws();
            if (s.charAt(i++) == '}') break;
        }
        return m;
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (s.charAt(i) == ']') { i++; return l; }
        while (true) {
            l.add(value());
            ws();
            if (s.charAt(i++) == ']') break;
        }
        return l;
    }

    private String string() {
        i++;
        StringBuilder b = null;
        int st = i;
        while (true) {
            char c = s.charAt(i);
            if (c == '"') {
                String r = b == null ? s.substring(st, i) : b.append(s, st, i).toString();
                i++;
                return r;
            }
            if (c == '\\') {
                if (b == null) b = new StringBuilder();
                b.append(s, st, i);
                i++;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
                st = i;
            } else i++;
        }
    }

    private Object number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw new IllegalStateException("Bad JSON at " + i);
        return Double.valueOf(s.substring(st, i));
    }
}
