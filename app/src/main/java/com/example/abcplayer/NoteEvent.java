package com.example.abcplayer;

public class NoteEvent {
    public final int[] midiNotes;   // 和音対応
    public final double length;     // ABCの長さ（L: を基準にした相対値）

    public NoteEvent(int[] midiNotes, double length) {
        this.midiNotes = midiNotes;
        this.length = length;
    }
}
