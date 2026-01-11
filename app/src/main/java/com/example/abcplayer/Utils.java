package com.example.abcplayer;

public class Utils {

    // A4 = 440Hz, MIDI 69
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

    public static double parseLength(String token) {
        if (token.contains("/")) {
            String[] parts = token.split("/");
            if (parts.length == 1) {
                return 1.0 / Double.parseDouble(parts[0]);
            } else {
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            }
        }
        return Double.parseDouble(token);
    }
}
