package com.ngoctho.aitranslator;

public final class TranslationResult {
    public final String source;
    public final String intentVi;
    public final String chinese;
    public final String backVi;
    public final int score;
    public final String warning;

    public TranslationResult(String source, String intentVi, String chinese, String backVi, int score, String warning) {
        this.source = source == null ? "" : source;
        this.intentVi = intentVi == null ? "" : intentVi;
        this.chinese = chinese == null ? "" : chinese;
        this.backVi = backVi == null ? "" : backVi;
        this.score = Math.max(0, Math.min(100, score));
        this.warning = warning == null ? "" : warning;
    }
}
