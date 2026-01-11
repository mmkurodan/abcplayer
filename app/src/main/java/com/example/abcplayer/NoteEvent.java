package com.example.abcplayer;

import java.util.Arrays;

/**
 * 1つの音符または和音を表すイベント。
 *
 * 標準 ABC Notation の複数ボイス対応のため、
 * voiceName を追加している。
 *
 * midiNotes:
 *   - 単音なら 1 要素
 *   - 和音なら複数要素
 *
 * beats:
 *   - L: と Q: を適用した後の「拍数」
 *   - Synth 側では beats * (60 / tempoBpm) で秒に変換する
 */
public class NoteEvent {

    /** この音が属するボイス名（例: "1", "2", "Soprano"） */
    public String voiceName;

    /** MIDI ノート番号（和音の場合は複数） */
    public int[] midiNotes;

    /** この音の長さ（拍数） */
    public double beats;

    /** 休符なら true */
    public boolean isRest;

    public NoteEvent(String voiceName, int[] midiNotes, double beats, boolean isRest) {
        this.voiceName = voiceName;
        this.midiNotes = midiNotes;
        this.beats = beats;
        this.isRest = isRest;
    }

    @Override
    public String toString() {
        return "NoteEvent{" +
                "voice='" + voiceName + '\'' +
                ", midi=" + Arrays.toString(midiNotes) +
                ", beats=" + beats +
                ", rest=" + isRest +
                '}';
    }
}
