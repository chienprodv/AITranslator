package com.ngoctho.aitranslator;

public final class BiTranslationResult {
    public final String source;
    public final String interpretedSource;
    public final String translated;
    public final String backTranslated;
    public final int score;
    public final String warning;
    public final String direction;

    public BiTranslationResult(
            String source,
            String interpretedSource,
            String translated,
            String backTranslated,
            int score,
            String warning,
            String direction) {
        this.source = source == null ? "" : source;
        this.interpretedSource = interpretedSource == null ? "" : interpretedSource;
        this.translated = translated == null ? "" : translated;
        this.backTranslated = backTranslated == null ? "" : backTranslated;
        this.score = Math.max(0, Math.min(100, score));
        this.warning = warning == null ? "" : warning;
        this.direction = direction == null ? "" : direction;
    }
}
