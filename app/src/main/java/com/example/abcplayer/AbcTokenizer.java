package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;

public class AbcTokenizer {

    public static class Token {
        public final String text;
        public Token(String text) {
            this.text = text;
        }
    }

    public List<Token> tokenize(String src) {
        List<Token> tokens = new ArrayList<>();

        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);

            // コメント行（%〜行末）
            if (c == '%') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
                continue;
            }

            // 空白・改行
            if (Character.isWhitespace(c)) { i++; continue; }

            // 小節線 | とリピート |:
            if (c == '|') {
                if (i + 1 < src.length() && src.charAt(i + 1) == ':') { tokens.add(new Token("|:")); i += 2; continue; }
                else if (i + 1 < src.length() && src.charAt(i + 1) == '|') { tokens.add(new Token("||")); i += 2; continue; }
                else {
                    if (i > 0 && src.charAt(i - 1) == ':') { tokens.add(new Token(":|")); i++; continue; }
                    tokens.add(new Token("|")); i++; continue;
                }
            }
            if (c == ':' && i + 1 < src.length() && src.charAt(i + 1) == '|') { tokens.add(new Token(":|")); i += 2; continue; }

            // Voice 宣言 V:
            if (c == 'V' && i + 1 < src.length() && src.charAt(i + 1) == ':') {
                tokens.add(new Token("V:"));
                i += 2;
                int start = i;
                while (i < src.length() && !Character.isWhitespace(src.charAt(i))) i++;
                tokens.add(new Token(src.substring(start, i)));
                continue;
            }

            // ボルタ表記 (1, [1
            if (c == '(' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1))) {
                int j = i + 1; String num = ""; while (j < src.length() && Character.isDigit(src.charAt(j))) { num += src.charAt(j); j++; }
                if (!num.isEmpty()) { tokens.add(new Token("(" + num)); i = j; continue; }
            }
            if (c == '[' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1))) {
                int j = i + 1; String num = ""; while (j < src.length() && Character.isDigit(src.charAt(j))) { num += src.charAt(j); j++; }
                if (!num.isEmpty()) { tokens.add(new Token("[" + num)); i = j; continue; }
            }

            // ヘッダ (X:, T:, M:, L:, Q:, K:, I:, %%MIDI)
            if (i + 1 < src.length() && src.charAt(i + 1) == ':') {
                String header = "" + c + ":";
                tokens.add(new Token(header));
                i += 2;
                int start = i;
                while (i < src.length() && src.charAt(i) != '\n') i++;
                String value = src.substring(start, i).trim();
                if (!value.isEmpty()) tokens.add(new Token(value));
                continue;
            }

            // デコレーション !...!
            if (c == '!') {
                int j = i + 1; while (j < src.length() && src.charAt(j) != '!') j++;
                if (j < src.length()) { tokens.add(new Token(src.substring(i, j + 1))); i = j + 1; continue; }
                else { tokens.add(new Token("!")); i++; continue; }
            }

            // グレース { }
            if (c == '{') { tokens.add(new Token("{")); i++; continue; }
            if (c == '}') { tokens.add(new Token("}")); i++; continue; }

            // スラー ) （(数字 は上で処理）
            if (c == ')') { tokens.add(new Token(")")); i++; continue; }

            // 装飾 ~ . > <
            if (c == '~' || c == '.' || c == '>' || c == '<') { tokens.add(new Token("" + c)); i++; continue; }

            // 和音開始・終了
            if (c == '[' || c == ']') { tokens.add(new Token("" + c)); i++; continue; }

            // タイ
            if (c == '-') { tokens.add(new Token("-")); i++; continue; }

            // 変化記号
            if (c == '^' || c == '_' || c == '=') { tokens.add(new Token("" + c)); i++; continue; }

            // オクターブ記号
            if (c == '\'' || c == ',') { tokens.add(new Token("" + c)); i++; continue; }

            // 長さ（数字 or /）
            if (Character.isDigit(c) || c == '/') {
                int start = i; while (i < src.length() && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '/')) i++;
                tokens.add(new Token(src.substring(start, i))); continue;
            }

            // 音符 or 休符
            if (Character.isLetter(c)) { tokens.add(new Token("" + c)); i++; continue; }

            i++;
        }
        return tokens;
    }
}
