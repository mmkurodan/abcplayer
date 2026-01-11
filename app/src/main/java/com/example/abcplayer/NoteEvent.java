package com.example.abcplayer;

public class NoteEvent {
    public final int midiNote;
    public final double durationSec;

    public NoteEvent(int midiNote, double durationSec) {
        this.midiNote = midiNote;
        this.durationSec = durationSec;
    }
}
