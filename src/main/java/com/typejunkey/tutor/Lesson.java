package com.typejunkey.tutor;

/** One tutorial: instruction text (lines starting with #) plus the exercise to type. */
public final class Lesson {
    public final String level, file, title, instruction, exercise;
    public final int number;
    public final boolean fromFolder;

    Lesson(String level, String file, int number, String title, String instruction, String exercise, boolean fromFolder) {
        this.level = level; this.file = file; this.number = number; this.title = title;
        this.instruction = instruction; this.exercise = exercise; this.fromFolder = fromFolder;
    }

    /** Stable id used for saved progress. */
    public String id() { return level + "/" + file; }

    @Override public String toString() { return String.format("%02d \u00b7 %s", number, title); }

    static Lesson parse(String level, String file, String text, boolean fromFolder) {
        String base = file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;
        if (base.toLowerCase().endsWith(".txt")) base = base.substring(0, base.length() - 4);
        int number = 0, i = 0;
        while (i < base.length() && Character.isDigit(base.charAt(i))) i++;
        if (i > 0) { try { number = Integer.parseInt(base.substring(0, i)); } catch (NumberFormatException ignored) { } }
        String title = base.substring(i).replaceAll("^[\\s_.-]+", "").replace('-', ' ').replace('_', ' ').trim();
        if (title.isEmpty()) title = base;
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        StringBuilder ins = new StringBuilder(), ex = new StringBuilder();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("#")) {
                String s = t.replaceFirst("^#+\\s?", "");
                if (ins.length() > 0) ins.append('\n');
                ins.append(s);
            } else if (!t.isEmpty()) {
                if (ex.length() > 0) ex.append(' ');
                ex.append(t);
            }
        }
        String exercise = ex.toString().replaceAll("\\s+", " ").trim();
        if (exercise.isEmpty()) return null;
        return new Lesson(level, file, number, title, ins.toString(), exercise, fromFolder);
    }
}
