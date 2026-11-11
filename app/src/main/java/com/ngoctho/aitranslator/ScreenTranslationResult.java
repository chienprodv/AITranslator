package com.ngoctho.aitranslator;

public final class ScreenTranslationResult {
    public final String source;
    public final String vietnamese;
    public final String warning;

    public ScreenTranslationResult(String source, String vietnamese, String warning) {
        this.source = source == null ? "" : source;
        this.vietnamese = vietnamese == null ? "" : vietnamese;
        this.warning = warning == null ? "" : warning;
    }
}
