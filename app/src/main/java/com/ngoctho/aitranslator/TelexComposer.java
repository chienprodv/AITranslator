package com.ngoctho.aitranslator;

import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

public final class TelexComposer {
    private static final String WORD_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZáàảãạăắằẳẵặâấầẩẫậÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬéèẻẽẹêếềểễệÉÈẺẼẸÊẾỀỂỄỆíìỉĩịÍÌỈĨỊóòỏõọôốồổỗộơớờởỡợÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢúùủũụưứừửữựÚÙỦŨỤƯỨỪỬỮỰýỳỷỹỵÝỲỶỸỴđĐ";

    private TelexComposer() {}

    private static String prefix = "", raw = "", rendered = "";

    public static void reset() { prefix = ""; raw = ""; rendered = ""; }

    public static boolean handle(InputConnection ic, char key) {
        if (ic == null || !Character.isLetter(key)) return false;
        CharSequence selection = ic.getSelectedText(0);
        String before = text(ic.getTextBeforeCursor(512, 0));
        if ((selection != null && selection.length() > 0) || !before.equals(prefix + rendered)) {
            String word = selection != null && selection.length() > 0 ? "" : currentWord(before);
            prefix = before.substring(0, before.length() - word.length());
            raw = word; rendered = word;
        }
        raw += key;
        String next = compose(raw);
        ic.beginBatchEdit();
        try {
            if (selection == null || selection.length() == 0) ic.deleteSurroundingText(rendered.length(), 0);
            ic.commitText(next, 1);
        } finally { ic.endBatchEdit(); }
        rendered = next;
        return true;
    }

    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }

    /** Raw keystrokes let a repeated modifier undo its transformation without losing letters. */
    public static String compose(String source) {
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        if (java.util.Arrays.asList("asus", "dell", "acer", "lenovo", "samsung", "iphone", "windows", "wifi", "usb").contains(lower)) return source;
        if (source.length() == 3 && "aeoAEO".indexOf(source.charAt(0)) >= 0
                && Character.toLowerCase(source.charAt(0)) == Character.toLowerCase(source.charAt(2))
                && "sfrxj".indexOf(Character.toLowerCase(source.charAt(1))) >= 0) return source;
        String result = "";
        int toneIndex = -1;
        boolean literal = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i), k = Character.toLowerCase(c);
            if (!literal && "sfrxjz".indexOf(k) >= 0 && hasVowel(result)) {
                int tone = toneFor(k);
                int oldTone = wordTone(result);
                if (tone > 0 && oldTone == tone) {
                    if (toneIndex >= 0 && i > toneIndex + 1) return source;
                    result = applyTone(result, 0) + c;
                    literal = true;
                } else { result = applyTone(result, tone); toneIndex = i; }
                continue;
            }
            if (!literal && !result.isEmpty()) {
                int lastIndex = result.length() - 1;
                char last = result.charAt(lastIndex), base = baseVowel(last);
                char shape = k == 'a' ? 'â' : k == 'e' ? 'ê' : k == 'o' ? 'ô' : 0;
                if (shape != 0 && base == k) {
                    result = result.substring(0, lastIndex) + toneChar(matchCase(last, shape), toneOf(last));
                    continue;
                }
                if (shape != 0 && base == shape) {
                    result = result.substring(0, lastIndex) + toneChar(matchCase(last, k), toneOf(last)) + c;
                    literal = true; continue;
                }
                if (k == 'd' && Character.toLowerCase(last) == 'd') {
                    result = result.substring(0, lastIndex) + matchCase(last, 'đ'); continue;
                }
                if (k == 'd' && Character.toLowerCase(last) == 'đ') {
                    result = result.substring(0, lastIndex) + matchCase(last, 'd') + c;
                    literal = true; continue;
                }
                if (k == 'w') {
                    String changed = applyWordW(result);
                    if (!changed.equals(result)) { result = changed; continue; }
                }
            }
            int tone = wordTone(result);
            result += c;
            if (!literal && tone > 0) result = applyTone(result, tone);
        }
        return result;
    }

    private static int toneOf(char c) {
        String all = "áắấéếíóốớúứýàằầèềìòồờùừỳảẳẩẻểỉỏổởủửỷãẵẫẽễĩõỗỡũữỹạặậẹệịọộợụựỵ";
        int index = all.indexOf(Character.toLowerCase(c));
        return index < 0 ? 0 : index / 12 + 1;
    }
    private static int wordTone(String word) {
        for (char c : word.toCharArray()) if (toneOf(c) > 0) return toneOf(c);
        return 0;
    }
    private static String applyWordW(String word) {
        char[] chars = word.toCharArray();
        int last = -1;
        for (int i = 0; i < chars.length; i++) if (isVowel(chars[i])) last = i;
        if (last < 0) return word;
        int tone = wordTone(word);
        char base = baseVowel(chars[last]);
        if (base == 'ơ' || base == 'ư' || base == 'ă') {
            for (int i = 0; i < chars.length; i++) {
                char b = baseVowel(chars[i]);
                if (b == 'ơ' || b == 'ư' || b == 'ă') chars[i] = matchCase(chars[i], b == 'ơ' ? 'o' : b == 'ư' ? 'u' : 'a');
            }
            return applyTone(new String(chars), tone) + "w";
        }
        if (base == 'o' && last > 0 && baseVowel(chars[last - 1]) == 'u') {
            chars[last - 1] = matchCase(chars[last - 1], 'ư'); chars[last] = matchCase(chars[last], 'ơ');
        } else if (base == 'a' || base == 'o' || base == 'u') {
            chars[last] = matchCase(chars[last], base == 'a' ? 'ă' : base == 'o' ? 'ơ' : 'ư');
        } else return word;
        return applyTone(new String(chars), tone);
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
                target = endsWithVowel ? vowels.get(vowels.size() >= 3 ? vowels.size() - 2 : 0) : lastIdx;
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
