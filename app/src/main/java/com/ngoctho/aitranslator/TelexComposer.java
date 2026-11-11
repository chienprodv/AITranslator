package com.ngoctho.aitranslator;

import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

public final class TelexComposer {
    private static final String WORD_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZáàảãạăắằẳẵặâấầẩẫậÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬéèẻẽẹêếềểễệÉÈẺẼẸÊẾỀỂỄỆíìỉĩịÍÌỈĨỊóòỏõọôốồổỗộơớờởỡợÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢúùủũụưứừửữựÚÙỦŨỤƯỨỪỬỮỰýỳỷỹỵÝỲỶỸỴđĐ";

    private TelexComposer() {}

    public static boolean handle(InputConnection ic, char key) {
        if (ic == null) return false;
        CharSequence before = ic.getTextBeforeCursor(80, 0);
        String word = currentWord(before == null ? "" : before.toString());
        char k = Character.toLowerCase(key);

        if ("sfrxjz".indexOf(k) >= 0 && !word.isEmpty() && hasVowel(word)) {
            int tone = toneFor(k);
            String transformed = applyTone(word, tone);
            if (!transformed.equals(word)) {
                replaceWord(ic, word, transformed);
                return true;
            }
        }

        if (k == 'w' && !word.isEmpty()) {
            String transformed = applyW(word);
            if (!transformed.equals(word)) {
                replaceWord(ic, word, transformed);
                return true;
            }
        }

        if (!word.isEmpty()) {
            char last = word.charAt(word.length() - 1);
            char lastLower = Character.toLowerCase(last);
            String transformed = null;
            if (k == 'a' && lastLower == 'a') transformed = word.substring(0, word.length() - 1) + matchCase(last, 'â');
            if (k == 'e' && lastLower == 'e') transformed = word.substring(0, word.length() - 1) + matchCase(last, 'ê');
            if (k == 'o' && lastLower == 'o') transformed = word.substring(0, word.length() - 1) + matchCase(last, 'ô');
            if (k == 'd' && lastLower == 'd') transformed = word.substring(0, word.length() - 1) + matchCase(last, 'đ');
            if (transformed != null) {
                replaceWord(ic, word, transformed);
                return true;
            }
        }
        return false;
    }

    private static String applyW(String word) {
        if (word.length() >= 2) {
            String tail2 = word.substring(word.length() - 2).toLowerCase();
            if (tail2.equals("uo")) {
                char u = word.charAt(word.length() - 2);
                char o = word.charAt(word.length() - 1);
                return word.substring(0, word.length() - 2) + matchCase(u, 'ư') + matchCase(o, 'ơ');
            }
        }
        char last = word.charAt(word.length() - 1);
        char b = baseVowel(last);
        char replacement = 0;
        if (b == 'a') replacement = 'ă';
        else if (b == 'o') replacement = 'ơ';
        else if (b == 'u') replacement = 'ư';
        if (replacement == 0) return word;
        return word.substring(0, word.length() - 1) + matchCase(last, replacement);
    }

    private static String applyTone(String word, int tone) {
        char[] chars = word.toCharArray();
        for (int i = 0; i < chars.length; i++) chars[i] = stripTone(chars[i]);

        List<Integer> vowels = new ArrayList<>();
        for (int i = 0; i < chars.length; i++) {
            if (isVowel(chars[i])) vowels.add(i);
        }
        if (vowels.isEmpty()) return word;

        if (vowels.size() > 1) {
            if (Character.toLowerCase(chars[0]) == 'q' && vowels.get(0) == 1 && Character.toLowerCase(chars[1]) == 'u') {
                vowels.remove(0);
            }
            if (chars.length > 2 && Character.toLowerCase(chars[0]) == 'g' && vowels.get(0) == 1 && Character.toLowerCase(chars[1]) == 'i') {
                vowels.remove(0);
            }
        }
        if (vowels.isEmpty()) return word;

        int target = -1;
        for (int idx : vowels) {
            char b = Character.toLowerCase(baseVowel(chars[idx]));
            if (b == 'ă' || b == 'â' || b == 'ê' || b == 'ô' || b == 'ơ' || b == 'ư') target = idx;
        }
        if (target < 0) {
            if (vowels.size() == 1) target = vowels.get(0);
            else {
                int lastIdx = vowels.get(vowels.size() - 1);
                boolean endsWithVowel = lastIdx == chars.length - 1;
                target = endsWithVowel ? vowels.get(0) : lastIdx;
            }
        }
        chars[target] = toneChar(chars[target], tone);
        return new String(chars);
    }

    private static int toneFor(char k) {
        switch (k) {
            case 's': return 1;
            case 'f': return 2;
            case 'r': return 3;
            case 'x': return 4;
            case 'j': return 5;
            default: return 0;
        }
    }

    private static boolean hasVowel(String s) {
        for (int i = 0; i < s.length(); i++) if (isVowel(s.charAt(i))) return true;
        return false;
    }

    private static boolean isVowel(char c) {
        char b = Character.toLowerCase(baseVowel(c));
        return "aăâeêioôơuưy".indexOf(b) >= 0;
    }

    private static char baseVowel(char c) {
        char lower = Character.toLowerCase(c);
        String[] groups = {
                "aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ", "eéèẻẽẹ", "êếềểễệ", "iíìỉĩị",
                "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ", "uúùủũụ", "ưứừửữự", "yýỳỷỹỵ"
        };
        char[] bases = {'a','ă','â','e','ê','i','o','ô','ơ','u','ư','y'};
        for (int i = 0; i < groups.length; i++) if (groups[i].indexOf(lower) >= 0) return bases[i];
        return lower;
    }

    private static char stripTone(char c) {
        char b = baseVowel(c);
        if (!isVowelBase(b)) return c;
        return matchCase(c, b);
    }

    private static boolean isVowelBase(char c) {
        return "aăâeêioôơuưy".indexOf(Character.toLowerCase(c)) >= 0;
    }

    private static char toneChar(char c, int tone) {
        boolean upper = Character.isUpperCase(c);
        char b = Character.toLowerCase(baseVowel(c));
        String row;
        switch (b) {
            case 'a': row = "aáàảãạ"; break;
            case 'ă': row = "ăắằẳẵặ"; break;
            case 'â': row = "âấầẩẫậ"; break;
            case 'e': row = "eéèẻẽẹ"; break;
            case 'ê': row = "êếềểễệ"; break;
            case 'i': row = "iíìỉĩị"; break;
            case 'o': row = "oóòỏõọ"; break;
            case 'ô': row = "ôốồổỗộ"; break;
            case 'ơ': row = "ơớờởỡợ"; break;
            case 'u': row = "uúùủũụ"; break;
            case 'ư': row = "ưứừửữự"; break;
            case 'y': row = "yýỳỷỹỵ"; break;
            default: return c;
        }
        char out = row.charAt(Math.max(0, Math.min(5, tone)));
        return upper ? Character.toUpperCase(out) : out;
    }

    private static char matchCase(char source, char replacement) {
        return Character.isUpperCase(source) ? Character.toUpperCase(replacement) : replacement;
    }

    private static String currentWord(String before) {
        if (before == null || before.isEmpty()) return "";
        int i = before.length() - 1;
        while (i >= 0 && WORD_CHARS.indexOf(before.charAt(i)) >= 0) i--;
        return before.substring(i + 1);
    }

    private static void replaceWord(InputConnection ic, String oldWord, String newWord) {
        ic.deleteSurroundingText(oldWord.length(), 0);
        ic.commitText(newWord, 1);
    }
}
