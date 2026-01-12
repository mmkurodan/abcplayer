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
     * ABC の長さトークンを「L に対する倍率」として返す。
     *
     * 例:
     *  "2"   → 2.0
     *  "3/2" → 1.5
     *  "/2"  → 0.5
     *  ""    → 1.0
     */
    public static double parseLength(String token) {
        if (token == null || token.isEmpty()) return 1.0;

        if (token.contains("/")) {
            String[] parts = token.split("/", -1);

            // "/n" のケース
            if (parts[0].isEmpty() && parts.length == 2 && !parts[1].isEmpty()) {
                double denom = Double.parseDouble(parts[1]);
                return 1.0 / denom;
            }

            // "n/m" のケース
            if (!parts[0].isEmpty() && parts.length == 2 && !parts[1].isEmpty()) {
                double num = Double.parseDouble(parts[0]);
                double denom = Double.parseDouble(parts[1]);
                return num / denom;
            }

            return 1.0;
        }

        // 整数
        return Double.parseDouble(token);
    }
}
