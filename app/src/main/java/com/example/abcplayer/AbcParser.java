package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 標準 ABC Notation の複数ボイス対応版パーサ。
 *
 * 対応機能:
 *  - ヘッダ (X:, T:, M:, L:, Q:, K:, V:)
 *  - 複数ボイス (V:)
 *  - 単音
 *  - 和音 [CEG]
 *  - 休符 z
 *  - 長さ 2, /2, 3/2
 *  - 変化記号 ^ _ =
 *  - オクターブ ' ,
 *  - タイ C-D
 *  - コメント %
 *  - 小節線 |
 *
 * 非対応（今後のステップで追加）:
 *  - スラー ( )
 *  - 装飾記号 ~ . > <
 *  - グレースノート { }
 *  - リピート |: :|
 *  - 装飾 !trill!
 *  - 複雑キー
 *  - 歌詞 w:
 */
public class AbcParser {

    private AbcTokenizer tokenizer = new AbcTokenizer();

    private Score score;
    private String currentVoice = "1";

    private double defaultNoteLength = 0.25; // L:1/4 の場合
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
                defaultNoteLength = Utils.parseLength(tokens.get(i).text);
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
                score.getVoice(currentVoice); // ensure exists
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

            // -----------------------------
            // タイ
            // -----------------------------
            if (t.equals("-")) {
                // タイは parseElement 内で処理するのでここでは無視
                i++;
                continue;
            }

            // -----------------------------
            // その他は無視
            // -----------------------------
            i++;
        }

        score.ensureDefaultVoice();
        return score;
    }

    /**
     * 単音・和音・休符をパースする。
     */
    private int parseElement(List<AbcTokenizer.Token> tokens, int i) {
        String t = tokens.get(i).text;

        // -----------------------------
        // 和音 [CEG]
        // -----------------------------
        if (t.equals("[")) {
            return parseChord(tokens, i);
        }

        // -----------------------------
        // 休符 z
        // -----------------------------
        if (t.equals("z")) {
            return parseRest(tokens, i);
        }

        // -----------------------------
        // 単音
        // -----------------------------
        return parseNote(tokens, i);
    }

    /**
     * 和音 [CEG] をパースする。
     */
    private int parseChord(List<AbcTokenizer.Token> tokens, int i) {
        i++; // skip '['
        List<Integer> midiList = new ArrayList<>();

        while (i < tokens.size()) {
            String t = tokens.get(i).text;

            if (t.equals("]")) {
                i++;
                break;
            }

            int accidental = 0;
            int octaveShift = 0;

            // accidental
            while (t.equals("^") || t.equals("_") || t.equals("=")) {
                if (t.equals("^")) accidental++;
                if (t.equals("_")) accidental--;
                i++;
                t = tokens.get(i).text;
            }

            // note letter
            if (!isNoteLetter(t)) {
                i++;
                continue;
            }
            int midi = Utils.noteLetterToMidi(t.charAt(0));
            i++;

            // octave
            while (i < tokens.size()) {
                t = tokens.get(i).text;
                if (t.equals("'")) {
                    octaveShift++;
                    i++;
                } else if (t.equals(",")) {
                    octaveShift--;
                    i++;
                } else break;
            }

            midi = Utils.applyAccidental(midi, accidental);
            midi = Utils.applyOctave(midi, octaveShift);
            midiList.add(midi);
        }

        // length
        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            beats = Utils.parseLength(tokens.get(i).text) * defaultNoteLength;
            i++;
        }

        int[] midiArr = midiList.stream().mapToInt(x -> x).toArray();
        score.getVoice(currentVoice).add(new NoteEvent(currentVoice, midiArr, beats, false));

        return i;
    }

    /**
     * 単音 C D E F...
     */
    private int parseNote(List<AbcTokenizer.Token> tokens, int i) {
        String t = tokens.get(i).text;

        int accidental = 0;
        int octaveShift = 0;

        // accidental
        while (t.equals("^") || t.equals("_") || t.equals("=")) {
            if (t.equals("^")) accidental++;
            if (t.equals("_")) accidental--;
            i++;
            t = tokens.get(i).text;
        }

        // note letter
        if (!isNoteLetter(t)) return i + 1;
        int midi = Utils.noteLetterToMidi(t.charAt(0));
        i++;

        // octave
        while (i < tokens.size()) {
            t = tokens.get(i).text;
            if (t.equals("'")) {
                octaveShift++;
                i++;
            } else if (t.equals(",")) {
                octaveShift--;
                i++;
            } else break;
        }

        midi = Utils.applyAccidental(midi, accidental);
        midi = Utils.applyOctave(midi, octaveShift);

        // length
        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            beats = Utils.parseLength(tokens.get(i).text) * defaultNoteLength;
            i++;
        }

        // tie C-D
        if (i < tokens.size() && tokens.get(i).text.equals("-")) {
            i++;
            int[] next = parseTiedNote(tokens, i);
            beats += next[1];
            i = next[0];
        }

        score.getVoice(currentVoice).add(new NoteEvent(currentVoice, new int[]{midi}, beats, false));
        return i;
    }

    /**
     * タイ C-D の後半を読む。
     */
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
            if (t.equals("'")) {
                octaveShift++;
                i++;
            } else if (t.equals(",")) {
                octaveShift--;
                i++;
            } else break;
        }

        midi = Utils.applyAccidental(midi, accidental);
        midi = Utils.applyOctave(midi, octaveShift);

        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            beats = Utils.parseLength(tokens.get(i).text) * defaultNoteLength;
            i++;
        }

        return new int[]{i, (int) beats};
    }

    /**
     * 休符 z
     */
    private int parseRest(List<AbcTokenizer.Token> tokens, int i) {
        i++; // skip z

        double beats = defaultNoteLength;
        if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
            beats = Utils.parseLength(tokens.get(i).text) * defaultNoteLength;
            i++;
        }

        score.getVoice(currentVoice).add(new NoteEvent(currentVoice, new int[]{-1}, beats, true));
        return i;
    }

    private boolean isNoteLetter(String t) {
        if (t.length() != 1) return false;
        char c = t.charAt(0);
        return "ABCDEFGabcdefg".indexOf(c) >= 0;
    }

    private boolean isLengthToken(String t) {
        return t.matches("[0-9/]+");
    }
                }
