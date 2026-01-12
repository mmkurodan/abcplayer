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

            // ------------------------------------
            // コメント行（%〜行末）をスキップ
            // ------------------------------------
            if (c == '%') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            // ------------------------------------
            // 空白・改行はスキップ
            // ------------------------------------
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // ------------------------------------
            // 小節線 | は完全に無視
            // ------------------------------------
            if (c == '|') {
                i++;
                continue;
            }

            // ------------------------------------
            // ★ Voice 宣言 V: の特別扱い（最重要）
            // ------------------------------------
            if (c == 'V' && i + 1 < src.length() && src.charAt(i + 1) == ':') {
                tokens.add(new Token("V:"));
                i += 2;
                continue;
            }

            // ------------------------------------
            // ヘッダ (X:, T:, M:, L:, Q:, K:)
            // ------------------------------------
            if (i + 1 < src.length() && src.charAt(i + 1) == ':') {
                tokens.add(new Token("" + c + ":"));
                i += 2;
                continue;
            }

            // ------------------------------------
            // 和音開始・終了
            // ------------------------------------
            if (c == '[' || c == ']') {
                tokens.add(new Token("" + c));
                i++;
                continue;
            }

            // ------------------------------------
            // タイ
            // ------------------------------------
            if (c == '-') {
                tokens.add(new Token("-"));
                i++;
                continue;
            }

            // ------------------------------------
            // 変化記号 (^, _, =)
            // ------------------------------------
            if (c == '^' || c == '_' || c == '=') {
                tokens.add(new Token("" + c));
                i++;
                continue;
            }

            // ------------------------------------
            // オクターブ記号 (' ,)
            // ------------------------------------
            if (c == '\'' || c == ',') {
                tokens.add(new Token("" + c));
                i++;
                continue;
            }

            // ------------------------------------
            // 長さ（数字 or /）
            // 例: 2, 3/2, /2
            // ------------------------------------
            if (Character.isDigit(c) || c == '/') {
                int start = i;
                while (i < src.length() &&
                        (Character.isDigit(src.charAt(i)) || src.charAt(i) == '/')) {
                    i++;
                }
                tokens.add(new Token(src.substring(start, i)));
                continue;
            }

            // ------------------------------------
            // 音符 or 休符
            // ------------------------------------
            if (Character.isLetter(c)) {
                tokens.add(new Token("" + c));
                i++;
                continue;
            }

            // ------------------------------------
            // その他は無視
            // ------------------------------------
            i++;
        }

        return tokens;
    }
}
