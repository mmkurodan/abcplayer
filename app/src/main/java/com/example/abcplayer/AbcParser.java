package com.example.abcplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbcParser {

    private AbcTokenizer tokenizer = new AbcTokenizer();

    private Score score;
    private String currentVoice = "1";

    // 四分音符 = 1 beat
    private double defaultNoteLength = 1.0;
    private double tempoBpm = 120.0;

    // per-parse working state
    private Map<String, List<String>> lyricsByVoice;
    private List<String> pendingOrnaments;
    private List<String> pendingDecorations;
    private boolean inGrace = false;

    private static class RepeatState {
        boolean inRepeat = false;
        int repeatStart = -1;
        boolean inFirstEnding = false;
        int firstEndingStart = -1;
        int firstEndingEnd = -1;
        boolean inSecondEnding = false;
        int secondEndingStart = -1;
    }

    private static class SlurState {
        int depth = 0;
    }

    public Score parseScore(String src) {
        score = new Score();
        currentVoice = "1";

        List<AbcTokenizer.Token> tokens = tokenizer.tokenize(src);
        int i = 0;

        // ボイスごとのリピート状態・スラー状態を保持
        Map<String, RepeatState> repeatStates = new HashMap<>();
        Map<String, SlurState> slurStates = new HashMap<>();

        // 歌詞・装飾バッファ
        lyricsByVoice = new HashMap<>();
        pendingOrnaments = new ArrayList<>();
        pendingDecorations = new ArrayList<>();

        while (i < tokens.size()) {
            String t = tokens.get(i).text;

            // -----------------------------
            // ヘッダ
            // -----------------------------
            if (t.equals("X:")) { i++; score.header.setNumber(tokens.get(i).text); i++; continue; }
            if (t.equals("T:")) { i++; /* title ignored */ i++; continue; }
            if (t.equals("C:")) { i++; score.header.setComposer(tokens.get(i).text); i++; continue; }
            if (t.equals("P:")) { i++; score.header.setPart(tokens.get(i).text); i++; continue; }
            if (t.equals("N:")) { i++; score.header.addNote(tokens.get(i).text); i++; continue; }
            if (t.equals("W:")) { i++; score.header.addWordLine(tokens.get(i).text); i++; continue; }
            if (t.equals("U:")) {
                i++;
                String def = tokens.get(i).text;
                String[] kv = def.split("\\s+", 2);
                if (kv.length == 2) score.header.setUserDef(kv[0], kv[1]);
                i++;
                continue;
            }
            if (t.equals("I:")) {
                i++;
                // "program 41" or "Piano" or "program=41" etc. 簡易対応: 最初の数字を program として解釈
                String val = tokens.get(i).text;
                int program = parseProgram(val);
                score.header.setProgram(currentVoice, program);
                i++;
                continue;
            }
            if (t.startsWith("%%MIDI")) {
                // 例: %%MIDI program 41
                String val = t.substring(6).trim();
                int program = parseProgram(val);
                score.header.setProgram(currentVoice, program);
                i++;
                continue;
            }
            if (t.equals("L:")) {
                i++;
                score.header.setDefaultLength(tokens.get(i).text);
                // L: の値を四分音符=1に正規化
                defaultNoteLength = score.header.defaultNoteLength / (1.0 / 4.0);
                i++;
                continue;
            }
            if (t.equals("Q:")) {
                i++;
                score.header.setTempo(tokens.get(i).text);
                tempoBpm = score.header.tempoBpm;
                i++;
                continue;
            }
            if (t.equals("M:")) {
                i++;
                score.header.setMeter(tokens.get(i).text);
                i++;
                continue;
            }
            if (t.equals("K:")) {
                i++;
                score.header.setKey(tokens.get(i).text);
                i++;
                continue;
            }
            if (t.equals("V:")) {
                i++;
                currentVoice = tokens.get(i).text;
                score.getVoice(currentVoice);
                if (!repeatStates.containsKey(currentVoice)) repeatStates.put(currentVoice, new RepeatState());
                if (!slurStates.containsKey(currentVoice)) slurStates.put(currentVoice, new SlurState());
                i++;
                continue;
            }

            // ボイス状態取得
            RepeatState rs = repeatStates.computeIfAbsent(currentVoice, k -> new RepeatState());
            SlurState ss = slurStates.computeIfAbsent(currentVoice, k -> new SlurState());

            // -----------------------------
            // リピート / ボルタ
            // -----------------------------
            if (t.equals("|:")) {
                rs.inRepeat = true;
                rs.repeatStart = score.getVoice(currentVoice).size();
                rs.inFirstEnding = false;
                rs.firstEndingStart = -1;
                rs.firstEndingEnd = -1;
                rs.inSecondEnding = false;
                rs.secondEndingStart = -1;
                i++;
                continue;
            }
            if (t.equals(":|")) {
                if (rs.inRepeat) {
                    List<NoteEvent> events = score.getVoice(currentVoice);
                    int curSize = events.size();
                    if (rs.inFirstEnding && rs.firstEndingStart >= 0) {
                        rs.firstEndingEnd = curSize;
                        rs.inFirstEnding = false;
                    }
                    int preStart = rs.repeatStart >= 0 ? rs.repeatStart : 0;
                    int preEnd = rs.firstEndingStart >= 0 ? rs.firstEndingStart : curSize;
                    List<NoteEvent> toAppend = new ArrayList<>();
                    if (rs.secondEndingStart >= 0) {
                        for (int idx = preStart; idx < preEnd; idx++) toAppend.add(events.get(idx).copy());
                        for (int idx = rs.secondEndingStart; idx < curSize; idx++) toAppend.add(events.get(idx).copy());
                    } else {
                        if (rs.firstEndingStart >= 0) {
                            for (int idx = preStart; idx < preEnd; idx++) toAppend.add(events.get(idx).copy());
                        } else {
                            for (int idx = preStart; idx < curSize; idx++) toAppend.add(events.get(idx).copy());
                        }
                    }
                    events.addAll(toAppend);
                    rs.inRepeat = false;
                    rs.inFirstEnding = false;
                    rs.inSecondEnding = false;
                    rs.repeatStart = -1;
                    rs.firstEndingStart = -1;
                    rs.firstEndingEnd = -1;
                    rs.secondEndingStart = -1;
                }
                i++;
                continue;
            }
            if ((t.startsWith("[") || t.startsWith("(")) && t.length() > 1 && Character.isDigit(t.charAt(1))) {
                int num = Integer.parseInt(t.substring(1));
                if (num == 1) {
                    rs.inFirstEnding = true;
                    rs.firstEndingStart = score.getVoice(currentVoice).size();
                } else if (num == 2) {
                    if (rs.inFirstEnding && rs.firstEndingStart >= 0) {
                        rs.firstEndingEnd = score.getVoice(currentVoice).size();
                        rs.inFirstEnding = false;
                    }
                    rs.inSecondEnding = true;
                    rs.secondEndingStart = score.getVoice(currentVoice).size();
                }
                i++;
                continue;
            }

            // -----------------------------
            // 歌詞・装飾・グレース
            // -----------------------------
            if (t.equals("w:")) {
                i++;
                List<String> syllables = new ArrayList<>();
                while (i < tokens.size() && !tokens.get(i).text.contains(":")) {
                    syllables.add(tokens.get(i).text);
                    i++;
                }
                lyricsByVoice.put(currentVoice, syllables);
                continue;
            }
            if (t.equals("!trill!") || t.equals("!fermata!") || t.startsWith("!")) { pendingDecorations.add(t); i++; continue; }
            if (t.equals("~") || t.equals(".") || t.equals(">") || t.equals("<")) { pendingOrnaments.add(t); i++; continue; }
            if (t.equals("{")) { inGrace = true; i++; continue; }
            if (t.equals("}")) { inGrace = false; i++; continue; }

            // -----------------------------
            // 音符・和音・休符・マルチメジャー休符
            // -----------------------------
            if (isNoteLetter(t) || t.equals("z") || t.equals("[") || t.equals("Z")) {
                i = parseElement(tokens, i, ss);
                continue;
            }

            // スラー開始/終了 (数字付きは先に処理済み)
            if (t.equals("(")) { ss.depth++; i++; continue; }
            if (t.equals(")")) {
                if (ss.depth > 0) ss.depth--;
                List<NoteEvent> v = score.getVoice(currentVoice);
                if (!v.isEmpty()) v.get(v.size() - 1).slurEnd = true;
                i++;
                continue;
            }

            // タイ
            if (t.equals("-")) { i++; continue; }

            i++;
        }

        score.ensureDefaultVoice();
        return score;
    }

    private int parseElement(List<AbcTokenizer.Token> tokens, int i, SlurState ss) {
        String t = tokens.get(i).text;
        if (t.equals("[")) return parseChord(tokens, i, ss);
        if (t.equals("z")) return parseRest(tokens, i);
        if (t.equals("Z")) return parseMultiRest(tokens, i);
        return parseNote(tokens, i, ss);
    }

    private int parseChord(List<AbcTokenizer.Token> tokens, int i, SlurState ss) {
        i++; List<Integer> midiList = new ArrayList<>();
        while (i < tokens.size()) {
            String t = tokens.get(i).text;
            if (t.equals("]")) { i++; break; }
            int accidental = 0; int octaveShift = 0;
            while (t.equals("^") || t.equals("_") || t.equals("=")) { if (t.equals("^")) accidental++; if (t.equals("_")) accidental--; i++; t = tokens.get(i).text; }
            if (!isNoteLetter(t)) { i++; continue; }
            int midi = Utils.noteLetterToMidi(t.charAt(0)); i++;
            while (i < tokens.size()) { t = tokens.get(i).text; if (t.equals("'")) { octaveShift++; i++; } else if (t.equals(",")) { octaveShift--; i++; } else break; }
            midi = Utils.applyAccidental(midi, accidental); midi = Utils.applyOctave(midi, octaveShift); midiList.add(midi);
        }
        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) { double mul = Utils.parseLength(tokens.get(i).text); beats = mul * defaultNoteLength; i++; }
        int[] arr = midiList.stream().mapToInt(x -> x).toArray();
        NoteEvent ev = new NoteEvent(currentVoice, arr, beats, false);
        applyPendingMarks(ev, ss);
        score.getVoice(currentVoice).add(ev);
        return i;
    }

    private int parseNote(List<AbcTokenizer.Token> tokens, int i, SlurState ss) {
        String t = tokens.get(i).text;
        int accidental = 0; int octaveShift = 0;
        while (t.equals("^") || t.equals("_") || t.equals("=")) { if (t.equals("^")) accidental++; if (t.equals("_")) accidental--; i++; t = tokens.get(i).text; }
        if (!isNoteLetter(t)) return i + 1;
        int midi = Utils.noteLetterToMidi(t.charAt(0)); i++;
        while (i < tokens.size()) { t = tokens.get(i).text; if (t.equals("'")) { octaveShift++; i++; } else if (t.equals(",")) { octaveShift--; i++; } else break; }
        midi = Utils.applyAccidental(midi, accidental); midi = Utils.applyOctave(midi, octaveShift);
        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) { double mul = Utils.parseLength(tokens.get(i).text); beats = mul * defaultNoteLength; i++; }
        if (i < tokens.size() && tokens.get(i).text.equals("-")) { i++; int[] next = parseTiedNote(tokens, i); beats += next[1]; i = next[0]; }
        NoteEvent ev = new NoteEvent(currentVoice, new int[]{midi}, beats, false);
        applyPendingMarks(ev, ss);
        score.getVoice(currentVoice).add(ev);
        return i;
    }

    private int[] parseTiedNote(List<AbcTokenizer.Token> tokens, int i) {
        String t = tokens.get(i).text;
        int accidental = 0; int octaveShift = 0;
        while (t.equals("^") || t.equals("_") || t.equals("=")) { if (t.equals("^")) accidental++; if (t.equals("_")) accidental--; i++; t = tokens.get(i).text; }
        if (!isNoteLetter(t)) return new int[]{i + 1, 0};
        int midi = Utils.noteLetterToMidi(t.charAt(0)); i++;
        while (i < tokens.size()) { t = tokens.get(i).text; if (t.equals("'")) { octaveShift++; i++; } else if (t.equals(",")) { octaveShift--; i++; } else break; }
        midi = Utils.applyAccidental(midi, accidental); midi = Utils.applyOctave(midi, octaveShift);
        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) { double mul = Utils.parseLength(tokens.get(i).text); beats = mul * defaultNoteLength; i++; }
        return new int[]{i, (int) beats};
    }

    private int parseRest(List<AbcTokenizer.Token> tokens, int i) {
        i++;
        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) { double mul = Utils.parseLength(tokens.get(i).text); beats = mul * defaultNoteLength; i++; }
        NoteEvent ev = new NoteEvent(currentVoice, new int[]{-1}, beats, true);
        applyPendingMarks(ev, null);
        score.getVoice(currentVoice).add(ev);
        return i;
    }

    private int parseMultiRest(List<AbcTokenizer.Token> tokens, int i) {
        i++;
        double bars = 1.0;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) { bars = Utils.parseLength(tokens.get(i).text); i++; }
        double beatsPerMeasure = (score.header.meterNum * (4.0 / score.header.meterDen));
        double beats = bars * beatsPerMeasure;
        NoteEvent ev = new NoteEvent(currentVoice, new int[]{-1}, beats, true);
        applyPendingMarks(ev, null);
        score.getVoice(currentVoice).add(ev);
        return i;
    }

    private boolean isNoteLetter(String t) { return t.length() == 1 && "ABCDEFGabcdefg".indexOf(t.charAt(0)) >= 0; }
    private boolean isLengthToken(String t) { return t.matches("[0-9/]+"); }

    private void applyPendingMarks(NoteEvent ev, SlurState ss) {
        if (ss != null && ss.depth > 0) ev.slurStart = true;
        List<String> syllables = lyricsByVoice.get(currentVoice);
        if (syllables != null && !syllables.isEmpty()) ev.lyric = syllables.remove(0);
        ev.ornaments.addAll(pendingOrnaments);
        ev.decorations.addAll(pendingDecorations);
        pendingOrnaments.clear();
        pendingDecorations.clear();
        if (inGrace) { ev.isGrace = true; ev.beats *= 0.25; }
        ev.program = score.header.getProgram(currentVoice);
    }

    private int parseProgram(String val) {
        try {
 program として解釈
            String[] parts = val.split("\\D+");
            for (String p : parts) {
                if (!p.isEmpty()) return Integer.parseInt(p);
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
