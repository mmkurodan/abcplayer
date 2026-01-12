package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbcParser {

    private AbcTokenizer tokenizer = new AbcTokenizer();

    private Score score;
    private String currentVoice = "1";

    // 四分音符 = 1 beat
    private double defaultNoteLength = 1.0;
    private double tempoBpm = 120.0;

    public Score parseScore(String src) {
        score = new Score();
        currentVoice = "1";

        List<AbcTokenizer.Token> tokens = tokenizer.tokenize(src);
        int i = 0;

        while (i < tokens.size()) {
            String t = tokens.get(i).text;

            // -----------------------------
            // ヘッダ
            // -----------------------------
            if (t.equals("L:")) {
                i++;
                // L:1/4 の場合は四分音符が基準なので 1.0 に固定
                defaultNoteLength = 1.0;
                score.header.defaultNoteLength = defaultNoteLength;
                i++;
                continue;
            }
            if (t.equals("Q:")) {
                i++;
                tempoBpm = Double.parseDouble(tokens.get(i).text);
                score.header.tempoBpm = tempoBpm;
                i++;
                continue;
            }
            if (t.equals("K:")) {
                i++;
                score.header.key = tokens.get(i).text;
                i++;
                continue;
            }
            if (t.equals("V:")) {
                i++;
                currentVoice = tokens.get(i).text;
                score.getVoice(currentVoice);
                i++;
                continue;
            }

            // -----------------------------
            // 音符・和音・休符
            // -----------------------------
            if (isNoteLetter(t) || t.equals("z") || t.equals("[")) {
                i = parseElement(tokens, i);
                continue;
            }

            // タイ
            if (t.equals("-")) {
                i++;
                continue;
            }

            i++;
        }

        score.ensureDefaultVoice();
        return score;
    }

    private int parseElement(List<AbcTokenizer.Token> tokens, int i) {
        String t = tokens.get(i).text;

        if (t.equals("[")) return parseChord(tokens, i);
        if (t.equals("z")) return parseRest(tokens, i);
        return parseNote(tokens, i);
    }

    private int parseChord(List<AbcTokenizer.Token> tokens, int i) {
        i++;
        List<Integer> midiList = new ArrayList<>();

        while (i < tokens.size()) {
            String t = tokens.get(i).text;

            if (t.equals("]")) {
                i++;
                break;
            }

            int accidental = 0;
            int octaveShift = 0;

            while (t.equals("^") || t.equals("_") || t.equals("=")) {
                if (t.equals("^")) accidental++;
                if (t.equals("_")) accidental--;
                i++;
                t = tokens.get(i).text;
            }

            if (!isNoteLetter(t)) {
                i++;
                continue;
            }

            int midi = Utils.noteLetterToMidi(t.charAt(0));
            i++;

            while (i < tokens.size()) {
                t = tokens.get(i).text;
                if (t.equals("'")) { octaveShift++; i++; }
                else if (t.equals(",")) { octaveShift--; i++; }
                else break;
            }

            midi = Utils.applyAccidental(midi, accidental);
            midi = Utils.applyOctave(midi, octaveShift);
            midiList.add(midi);
        }

        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            double mul = Utils.parseLength(tokens.get(i).text);
            beats = mul * defaultNoteLength;
            i++;
        }

        int[] arr = midiList.stream().mapToInt(x -> x).toArray();
        score.getVoice(currentVoice).add(new NoteEvent(currentVoice, arr, beats, false));

        return i;
    }

    private int parseNote(List<AbcTokenizer.Token> tokens, int i) {
        String t = tokens.get(i).text;

        int accidental = 0;
        int octaveShift = 0;

        while (t.equals("^") || t.equals("_") || t.equals("=")) {
            if (t.equals("^")) accidental++;
            if (t.equals("_")) accidental--;
            i++;
            t = tokens.get(i).text;
        }

        if (!isNoteLetter(t)) return i + 1;

        int midi = Utils.noteLetterToMidi(t.charAt(0));
        i++;

        while (i < tokens.size()) {
            t = tokens.get(i).text;
            if (t.equals("'")) { octaveShift++; i++; }
            else if (t.equals(",")) { octaveShift--; i++; }
            else break;
        }

        midi = Utils.applyAccidental(midi, accidental);
        midi = Utils.applyOctave(midi, octaveShift);

        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            double mul = Utils.parseLength(tokens.get(i).text);
            beats = mul * defaultNoteLength;
            i++;
        }

        if (i < tokens.size() && tokens.get(i).text.equals("-")) {
            i++;
            int[] next = parseTiedNote(tokens, i);
            beats += next[1];
            i = next[0];
        }

        score.getVoice(currentVoice).add(new NoteEvent(currentVoice, new int[]{midi}, beats, false));
        return i;
    }

    private int[] parseTiedNote(List<AbcTokenizer.Token> tokens, int i) {
        String t = tokens.get(i).text;

        int accidental = 0;
        int octaveShift = 0;

        while (t.equals("^") || t.equals("_") || t.equals("=")) {
            if (t.equals("^")) accidental++;
            if (t.equals("_")) accidental--;
            i++;
            t = tokens.get(i).text;
        }

        if (!isNoteLetter(t)) return new int[]{i + 1, 0};

        int midi = Utils.noteLetterToMidi(t.charAt(0));
        i++;

        while (i < tokens.size()) {
            t = tokens.get(i).text;
            if (t.equals("'")) { octaveShift++; i++; }
            else if (t.equals(",")) { octaveShift--; i++; }
            else break;
        }

        midi = Utils.applyAccidental(midi, accidental);
        midi = Utils.applyOctave(midi, octaveShift);

        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            double mul = Utils.parseLength(tokens.get(i).text);
            beats = mul * defaultNoteLength;
            i++;
        }

        return new int[]{i, (int) beats};
    }

    private int parseRest(List<AbcTokenizer.Token> tokens, int i) {
        i++;

        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            double mul = Utils.parseLength(tokens.get(i).text);
            beats = mul * defaultNoteLength;
            i++;
        }

        score.getVoice(currentVoice).add(new NoteEvent(currentVoice, new int[]{-1}, beats, true));
        return i;
    }

    private boolean isNoteLetter(String t) {
        if (t.length() != 1) return false;
        return "ABCDEFGabcdefg".indexOf(t.charAt(0)) >= 0;
    }

    private boolean isLengthToken(String t) {
        return t.matches("[0-9/]+");
    }
}
