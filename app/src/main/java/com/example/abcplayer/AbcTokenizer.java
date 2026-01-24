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
                while (i < src.length() && src.charAt(i) != '\n') i++;
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
            // 小節線 | とリピート |: の処理
            // ------------------------------------
            if (c == '|') {
                if (i + 1 < src.length() && src.charAt(i + 1) == ':') {
                    tokens.add(new Token("|:"));
                    i += 2;
                    continue;
                } else if (i + 1 < src.length() && src.charAt(i + 1) == '|') {
                    // treat || as a single bar for now
                    tokens.add(new Token("||"));
                    i += 2;
                    continue;
                } else {
                    // check for :| pattern where ':' precedes '|'
                    // if previous char is ':' then we want ':|' token. However tokenizer moves forward only,
                    // so also handle ':|' here by peeking next char (rare case when ':' appears before '|')
                    if (i > 0 && src.charAt(i - 1) == ':') {
                        tokens.add(new Token(":|"));
                        i++;
                        continue;
                    }

                    // 単純な小節線はトークン化しておく（将来的な拡張のため）
                    tokens.add(new Token("|"));
                    i++;
                    continue;
                }
            }

            // 直後に | が来て :| を形成するケースを補完（別ループでは検出しにくい）
            if (c == ':' && i + 1 < src.length() && src.charAt(i + 1) == '|') {
                tokens.add(new Token(":|"));
                i += 2;
                continue;
            }

            // ------------------------------------
            // ★ Voice 宣言 V: の特別扱い
            // ------------------------------------
            if (c == 'V' && i + 1 < src.length() && src.charAt(i + 1) == ':') {
                tokens.add(new Token("V:"));
                i += 2;

                // voice 名を読み取る（行末 or 空白まで）
                int start = i;
                while (i < src.length() && !Character.isWhitespace(src.charAt(i))) i++;
                tokens.add(new Token(src.substring(start, i)));
                continue;
            }

            // ------------------------------------
            // ★ ヘッダ (X:, T:, M:, L:, Q:, K:) の処理
            // さらにボルタ表記の "(1" や "[1" を単独トークンとして扱う
            // ------------------------------------
            if (c == '(' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1))) {
                int j = i + 1;
                String num = "";
                while (j < src.length() && Character.isDigit(src.charAt(j))) { num += src.charAt(j); j++; }
                if (!num.isEmpty()) {
                    tokens.add(new Token("(" + num));
                    i = j;
                    continue;
                }
            }
            if (c == '[' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1))) {
                int j = i + 1;
                String num = "";
                while (j < src.length() && Character.isDigit(src.charAt(j))) { num += src.charAt(j); j++; }
                if (!num.isEmpty()) {
                    tokens.add(new Token("[" + num));
                    i = j;
                    continue;
                }
            }
            if (i + 1 < src.length() && src.charAt(i + 1) == ':') {
                String header = "" + c + ":";

                tokens.add(new Token(header));
                i += 2;

                // 行末までを値として読み取る
                int start = i;
                while (i < src.length() && src.charAt(i) != '\n') i++;
                String value = src.substring(start, i).trim();
                if (!value.isEmpty()) tokens.add(new Token(value));
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
            // ------------------------------------
            if (Character.isDigit(c) || c == '/') {
                int start = i;
                while (i < src.length() &&
                        (Character.isDigit(src.charAt(i)) || src.charAt(i) == '/')) i++;
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
