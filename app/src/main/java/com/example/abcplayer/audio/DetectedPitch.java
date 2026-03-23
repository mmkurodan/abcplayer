package com.example.abcplayer.audio;

import java.util.Locale;

public class DetectedPitch {

    public final double frequencyHz;
    public final int midiNote;
    public final String noteName;
    public final double confidence;

    public DetectedPitch(double frequencyHz, int midiNote, String noteName, double confidence) {
        this.frequencyHz = frequencyHz;
        this.midiNote = midiNote;
        this.noteName = noteName;
        this.confidence = clamp(confidence);
    }

    public static DetectedPitch fromFrequency(double frequencyHz, double confidence) {
        int midi = frequencyToMidi(frequencyHz);
        return new DetectedPitch(frequencyHz, midi, midiToNoteName(midi), confidence);
    }

    public String toAbcNote() {
        return midiToAbcNote(midiNote);
    }

    public static int frequencyToMidi(double frequencyHz) {
        if (!Double.isFinite(frequencyHz) || frequencyHz <= 0.0) {
            return -1;
        }
        return (int) Math.round(69 + 12.0 * Math.log(frequencyHz / 440.0) / Math.log(2.0));
    }

    public static String midiToNoteName(int midiNote) {
        if (midiNote < 0) {
            return "z";
        }
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = midiNote / 12 - 1;
        return names[(midiNote % 12 + 12) % 12] + octave;
    }

    public static double midiToFrequency(int midiNote) {
        if (midiNote < 0) {
            return 0.0;
        }
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }

    public static String midiToAbcNote(int midiNote) {
        if (midiNote < 0) {
            return "z";
        }
        int octave = midiNote / 12 - 1;
        int pc = (midiNote % 12 + 12) % 12;
        String note;
        switch (pc) {
            case 0:
                note = "C";
                break;
            case 1:
                note = "^C";
                break;
            case 2:
                note = "D";
                break;
            case 3:
                note = "^D";
                break;
            case 4:
                note = "E";
                break;
            case 5:
                note = "F";
                break;
            case 6:
                note = "^F";
                break;
            case 7:
                note = "G";
                break;
            case 8:
                note = "^G";
                break;
            case 9:
                note = "A";
                break;
            case 10:
                note = "^A";
                break;
            case 11:
                note = "B";
                break;
            default:
                note = "C";
                break;
        }

        if (octave >= 5) {
            for (int i = 0; i < octave - 4; i++) {
                note += "'";
            }
        } else if (octave <= 3) {
            for (int i = 0; i < 4 - octave; i++) {
                note += ",";
            }
        }
        return note;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s %.1fHz %.2f", noteName, frequencyHz, confidence);
    }
}
