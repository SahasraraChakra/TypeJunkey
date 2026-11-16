package com.typejunkey.model;

import java.util.List;

public final class QuoteSet {
    public final List<Quote> quotes;
    public final int[][] groups; // [group] -> {minLen, maxLen}
    public QuoteSet(List<Quote> quotes, int[][] groups) { this.quotes = quotes; this.groups = groups; }
}
