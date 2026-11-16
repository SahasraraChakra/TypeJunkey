package com.typejunkey;

import com.typejunkey.data.Content;
import com.typejunkey.ui.AppTheme;
import com.typejunkey.ui.MainPanel;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dev-only: run with and without bundled resources on the classpath; optional side folder content. */
public final class Robust {
    static void check(boolean ok, String msg) { System.out.println((ok ? "PASS " : "FAIL ") + msg); if (!ok) System.exit(1); }

    public static void main(String[] a) throws Exception {
        boolean noBundled = a.length > 0 && a[0].equals("nobundled");
        Path home = Files.createTempDirectory("tj-home");
        System.setProperty("typejunkey.home", home.toString());
        if (a.length > 0 && a[0].equals("side")) {
            Files.createDirectories(home.resolve("languages"));
            Files.writeString(home.resolve("languages/zzz_custom.json"), "{\"name\":\"zzz_custom\",\"words\":[\"alpha\",\"beta\",\"gamma\"]}");
            Files.writeString(home.resolve("languages/english.json"), "{\"name\":\"english\",\"words\":[\"override\",\"override2\"]}");
            Files.createDirectories(home.resolve("themes"));
            Files.writeString(home.resolve("themes/mytheme.css"), ":root{--bg-color:#102030;--main-color:#ff8800;--caret-color:#ff8800;--sub-color:#445566;--sub-alt-color:#0c1824;--text-color:#eeeeee;--error-color:#ff0000;--error-extra-color:#990000;}");
            Files.createDirectories(home.resolve("tutor/basic"));
            Files.writeString(home.resolve("tutor/basic/99_my-own.txt"), "# My own lesson\nhello world again\n");
            Files.createDirectories(home.resolve("tutor/myteam"));
            Files.writeString(home.resolve("tutor/myteam/01_welcome.txt"), "# Welcome\nteam typing\n");
        }
        check(Content.language("english").words.length > 0, "english loads (or falls back)");
        check(Content.themes().size() > 0, "themes never empty: " + Content.themes().size());
        check(Content.layout("qwerty").rows.size() == 5, "qwerty layout never missing");
        check(Content.quotes("english").quotes.size() > 0, "quotes never empty");
        check(!Content.languages().isEmpty(), "language list never empty: " + Content.languages().size());
        if (noBundled) {
            check(com.typejunkey.tutor.TutorLibrary.levels().isEmpty(), "no bundled tutor lessons -> empty library, no crash");
        }
        if (a.length > 0 && a[0].equals("side")) {
            check(Content.languages().contains("zzz_custom"), "side-folder language is listed");
            check(Content.language("english").words[0].equals("override"), "side-folder english overrides bundled");
            check(Content.themes().stream().anyMatch(t -> t.name.equals("mytheme") && t.main.getRed() == 255), "side-folder css theme is loaded");
            check(com.typejunkey.tutor.TutorLibrary.lessons("basic").stream().anyMatch(l -> l.title.equals("My own")), "side-folder tutor lesson added");
            check(com.typejunkey.tutor.TutorLibrary.levels().contains("myteam"), "extra tutor level folder shows up");
            com.typejunkey.data.Settings.get().setHidden(Content.LANGUAGE, "zzz_custom", true);
            Content.reload();
            check(!Content.languages().contains("zzz_custom"), "hiding a language removes it from the list");
        }
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.prepare();
            AppTheme.apply(AppTheme.desiredDark());
            MainPanel p = new MainPanel();
            p.setSize(1000, 700);
        });
        check(true, "MainPanel builds");
        System.exit(0);
    }
}
