package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;

public class AbcParser {

    private AbcHeader header = new AbcHeader();

    public AbcHeader getHeader() {
        return header;
    }

    public NoteEvent[] parse(String src) {
        AbcTokenizer tokenizer = new AbcTokenizer();
        List<AbcTokenizer.Token> tokens = tokenizer.tokenize(src);

        List<NoteEvent> events = new ArrayList<>();

        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i).text;

            // ヘッダ処理
            if (t.equals("M:")) {
                i++;
                header.setMeter(tokens.get(i).text);
                i++;
                continue;
            }
            if (t.equals("L:")) {
                i++;
                header.setDefaultLength(tokens.get(i).text);
                i++;
                continue;
            }
            if (t.equals("Q:")) {
                i++;
                header.setTempo(tokens.get(i).text);
                i++;
                continue;
            }
            if (t.equals("K:")) {
                i++;
                header.setKey(tokens.get(i).text);
                i++;
                continue;
            }

            // 和音
            if (t.equals("[")) {
                i++;
                List<Integer> chord = new ArrayList<>();
                while (i < tokens.size() && !tokens.get(i).text.equals("]")) {
                    chord.add(parseSingleNote(tokens, i));
                    i++;
                }
                i++; // skip ']'

                // 長さ
                double len = header.defaultNoteLength;
                if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
                    len = Utils.parseLength(tokens.get(i).text) * header.defaultNoteLength;
                    i++;
                }

                int[] chordNotes = chord.stream().mapToInt(x -> x).toArray();
                events.add(new NoteEvent(chordNotes, len));
                continue;
            }

            // 単音 or 休符
            if (isNoteLetter(t) || t.equals("z")) {
                int midi = parseSingleNote(tokens, i);
                i++;

                // 長さ
                double len = header.defaultNoteLength;
                if (i < tokens.size() && isLengthToken(tokens.get(i).text)) {
                    len = Utils.parseLength(tokens.get(i).text) * header.defaultNoteLength;
                    i++;
                }

                events.add(new NoteEvent(new int[]{midi}, len));
                continue;
            }

            i++;
        }

        return events.toArray(new NoteEvent[0]);
    }

    private boolean isNoteLetter(String t) {
        return t.matches("[A-Ga-g]");
    }

    private boolean isLengthToken(String t) {
        return t.matches("[0-9/]+");
    }

    private int parseSingleNote(List<AbcTokenizer.Token> tokens, int i) {
        int accidental = 0;
        int octaveShift = 0;

        // 変化記号
        while (i < tokens.size()) {
            String t = tokens.get(i).text;
            if (t.equals("^")) { accidental++; i++; continue; }
            if (t.equals("_")) { accidental--; i++; continue; }
            if (t.equals("=")) { accidental = 0; i++; continue; }
            break;
        }

        // 音符 or 休符
        String note = tokens.get(i).text;
        int midi;
        if (note.equals("z")) {
            midi = -1; // 休符
        } else {
            midi = Utils.noteLetterToMidi(note.charAt(0));
        }
        i++;

        // オクターブ
        while (i < tokens.size()) {
            String t = tokens.get(i).text;
            if (t.equals("'")) { octaveShift++; i++; continue; }
            if (t.equals(",")) { octaveShift--; i++; continue; }
            break;
        }

        if (midi >= 0) {
            midi = Utils.applyAccidental(midi, accidental);
            midi = Utils.applyOctave(midi, octaveShift);
        }

        return midi;
    }
}
