package com.typejunkey.model;

public final class Quote {
    public final String text, source;
    public final int length;
    public Quote(String text, String source, int length) { this.text = text; this.source = source; this.length = length; }
}
