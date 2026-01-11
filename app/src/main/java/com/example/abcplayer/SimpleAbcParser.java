package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;

public class SimpleAbcParser {

    public static NoteEvent[] parse(String abc) {
        String[] tokens = abc.split("\\s+");
        List<NoteEvent> result = new ArrayList<>();

        for (String t : tokens) {
            if (t.isEmpty()) continue;

            Integer midi = mapTokenToMidi(t);
            if (midi != null) {
                result.add(new NoteEvent(midi, 1.0));
            }
        }

        return result.toArray(new NoteEvent[0]);
    }

    private static Integer mapTokenToMidi(String token) {
        switch (token) {
            case "C": return 60;
            case "D": return 62;
            case "E": return 64;
            case "F": return 65;
            case "G": return 67;
            case "A": return 69;
            case "B": return 71;
            case "c": return 72;
            default:
                return null;
        }
    }
}
