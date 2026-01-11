package com.example.abcplayer;

public class Utils {

    public static int noteLetterToMidi(char c) {
        switch (c) {
            case 'C': return 60;
            case 'D': return 62;
            case 'E': return 64;
            case 'F': return 65;
            case 'G': return 67;
            case 'A': return 69;
            case 'B': return 71;

            case 'c': return 72;
            case 'd': return 74;
            case 'e': return 76;
            case 'f': return 77;
            case 'g': return 79;
            case 'a': return 81;
            case 'b': return 83;
        }
        return -1;
    }

    public static int applyAccidental(int midi, int accidental) {
        return midi + accidental;
    }

    public static int applyOctave(int midi, int octaveShift) {
        return midi + (12 * octaveShift);
    }

    /**
     * ABC の長さトークンを数値に変換する。
     *
     * 例:
     *  "2"   → 2.0
     *  "3/2" → 1.5
     *  "/2"  → 0.5
     */
    public static double parseLength(String token) {
        if (token.contains("/")) {
            // 空要素も保持する
            String[] parts = token.split("/", -1);

            // "/n" のケース（例: "/2"）
            if (parts[0].isEmpty() && parts.length == 2 && !parts[1].isEmpty()) {
                double denom = Double.parseDouble(parts[1]);
                return 1.0 / denom;
            }

            // "n/m" のケース（例: "3/2")
            if (!parts[0].isEmpty() && parts.length == 2 && !parts[1].isEmpty()) {
                double num = Double.parseDouble(parts[0]);
                double denom = Double.parseDouble(parts[1]);
                return num / denom;
            }

            // 想定外フォーマットは防御的に 1.0 にフォールバック
            return 1.0;
        }

        // 単純な整数 "2" など
        return Double.parseDouble(token);
    }
}
