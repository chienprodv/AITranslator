package com.ngoctho.aitranslator;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

public final class MeaningCheck {
    private MeaningCheck() {}
    public static List<String> facts(String value) {
        List<String> facts = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?<![\\p{L}\\d])[A-Za-z]{1,12}[-_/]?\\d+[A-Za-z0-9_/-]*|[+-]?\\d+(?:[.,]\\d+)*").matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.matches("[+-]?\\d+(?:[.,]\\d+)?")) {
                try { token = new BigDecimal(token.replace(',', '.')).stripTrailingZeros().toPlainString(); } catch (Exception ignored) {}
            }
            facts.add(token);
        }
        return facts;
    }
    public static String warning(String source, String target, String lockedTerms) {
        List<String> expected = facts(source), actual = facts(target), remaining = new ArrayList<>(actual);
        List<String> missing = new ArrayList<>();
        for (String item : expected) if (!remaining.remove(item)) missing.add(item);
        StringBuilder warning = new StringBuilder();
        if (!missing.isEmpty() || !remaining.isEmpty()) warning.append("Kiểm tra số/mã: gốc ").append(expected).append("; bản dịch ").append(actual).append(".");
        for (String term : lockedTerms.split("[;\n]")) {
            term = term.trim();
            if (!term.isEmpty() && source.contains(term) && !target.contains(term)) warning.append(" Giữ nguyên thuật ngữ: ").append(term).append('.');
        }
        return warning.toString().trim();
    }
    public static TranslationResult check(TranslationResult result, String lockedTerms) {
        String issue = warning(result.source, result.chinese, lockedTerms);
        return issue.isEmpty() ? result : new TranslationResult(result.source, result.intentVi, result.chinese, result.backVi,
                result.score, (result.warning.isEmpty() ? "" : result.warning + " · ") + issue);
    }
}
