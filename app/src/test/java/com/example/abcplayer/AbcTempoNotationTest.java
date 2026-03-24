package com.example.abcplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AbcTempoNotationTest {

    @Test
    public void normalizesStandardQuarterNoteTempo() {
        AbcHeader header = new AbcHeader();
        header.setTempo("1/4=120");

        assertEquals(120.0, header.tempoBpm, 1e-9);
        assertEquals("Q:1/4=120", AbcTempoNotation.formatQuarterNoteTempo(120.0));
    }

    @Test
    public void normalizesTempoUsingDifferentBeatUnit() {
        AbcHeader header = new AbcHeader();
        header.setTempo("1/8=120");

        assertEquals(60.0, header.tempoBpm, 1e-9);
    }

    @Test
    public void ignoresTempoTextWhileParsing() {
        AbcHeader header = new AbcHeader();
        header.setTempo("\"Allegro\" 1/4=120");

        assertEquals(120.0, header.tempoBpm, 1e-9);
    }
}
