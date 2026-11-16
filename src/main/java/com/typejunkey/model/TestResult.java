package com.typejunkey.model;

/** Outcome of one finished test. Series arrays are kept in memory only (not persisted). */
public final class TestResult {
    public long when;
    public String mode = "time", param = "", language = "english";
    public boolean punctuation, numbers;
    public double wpm, raw, acc, consistency, seconds;
    public int correct, incorrect, extra, missed;
    public double[] wpmSeries = new double[0], rawSeries = new double[0];
    public int[] errSeries = new int[0];
    // in-memory only
    public boolean failed;
    public String failReason = "";
    public java.util.List<String[]> words = new java.util.ArrayList<>(); // {target, typed} per word
    public double[] bursts = new double[0];                               // wpm of each typed word
    public String funbox = "";
    public double paceWpm;

    public static String key(String mode, String param, String lang, boolean p, boolean n) {
        return mode + "|" + param + "|" + lang + "|" + (p ? "p" : "") + (n ? "n" : "");
    }

    public String key() { return key(mode, param, language, punctuation, numbers); }

    /** Human label such as "time 30" or "quote short". */
    public String label() { return param.isEmpty() ? mode : mode + " " + param; }

    public String flags() {
        String f = (punctuation ? "punctuation" : "");
        if (numbers) f += (f.isEmpty() ? "" : ", ") + "numbers";
        return f;
    }

    public String toLine() {
        return String.join("\t", Long.toString(when), mode, param.isEmpty() ? "-" : param, language,
                punctuation ? "1" : "0", numbers ? "1" : "0", Double.toString(wpm), Double.toString(raw),
                Double.toString(acc), Double.toString(consistency), Integer.toString(correct),
                Integer.toString(incorrect), Integer.toString(extra), Integer.toString(missed),
                Double.toString(seconds));
    }

    public static TestResult fromLine(String line) {
        try {
            String[] p = line.split("\t");
            TestResult r = new TestResult();
            r.when = Long.parseLong(p[0]); r.mode = p[1]; r.param = p[2].equals("-") ? "" : p[2]; r.language = p[3];
            r.punctuation = p[4].equals("1"); r.numbers = p[5].equals("1");
            r.wpm = Double.parseDouble(p[6]); r.raw = Double.parseDouble(p[7]); r.acc = Double.parseDouble(p[8]);
            r.consistency = Double.parseDouble(p[9]); r.correct = Integer.parseInt(p[10]);
            r.incorrect = Integer.parseInt(p[11]); r.extra = Integer.parseInt(p[12]);
            r.missed = Integer.parseInt(p[13]); r.seconds = Double.parseDouble(p[14]);
            return r;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
