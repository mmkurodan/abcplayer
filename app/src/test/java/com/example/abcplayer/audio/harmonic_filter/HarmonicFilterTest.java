package com.example.abcplayer.audio.harmonic_filter;

import static org.junit.Assert.assertEquals;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.PeakCandidate;
import com.example.abcplayer.audio.PolyphonyMode;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HarmonicFilterTest {

    @Test
    public void removesIntegerMultipleHarmonics() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        HarmonicFilter harmonicFilter = new HarmonicFilter(config);

        List<PeakCandidate> peaks = Arrays.asList(
                new PeakCandidate(0, 0.0, 220.0, 10.0, 0.9),
                new PeakCandidate(0, 0.0, 277.18, 9.5, 0.9),
                new PeakCandidate(0, 0.0, 440.0, 9.0, 0.8),
                new PeakCandidate(0, 0.0, 660.0, 8.0, 0.7),
                new PeakCandidate(0, 0.0, 880.0, 7.0, 0.6)
        );

        List<PeakCandidate> fundamentals = harmonicFilter.filter(peaks);

        assertEquals(2, fundamentals.size());
        assertEquals(220.0, fundamentals.get(0).frequencyHz, 0.01);
        assertEquals(277.18, fundamentals.get(1).frequencyHz, 0.01);
    }

    @Test
    public void automaticModeRespectsMaxFundamentals() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.polyphonyMode = PolyphonyMode.AUTOMATIC;
        config.maxFundamentals = 2;
        HarmonicFilter harmonicFilter = new HarmonicFilter(config);

        List<PeakCandidate> peaks = Arrays.asList(
                new PeakCandidate(0, 0.0, 250.0, 10.0, 0.9),
                new PeakCandidate(1, 1.0, 350.0, 9.0, 0.8),
                new PeakCandidate(2, 2.0, 450.0, 8.0, 0.7),
                new PeakCandidate(3, 3.0, 550.0, 7.0, 0.6)
        );

        List<PeakCandidate> fundamentals = harmonicFilter.filter(peaks);

        assertEquals(2, fundamentals.size());
    }

    @Test
    public void fixedModeLimitsToSpecifiedCount() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.polyphonyMode = PolyphonyMode.FIXED;
        config.fixedPolyphonyCount = 2;
        config.maxFundamentals = 4;
        HarmonicFilter harmonicFilter = new HarmonicFilter(config);

        List<PeakCandidate> peaks = Arrays.asList(
                new PeakCandidate(0, 0.0, 250.0, 10.0, 0.9),
                new PeakCandidate(1, 1.0, 350.0, 9.0, 0.8),
                new PeakCandidate(2, 2.0, 450.0, 8.0, 0.7),
                new PeakCandidate(3, 3.0, 550.0, 7.0, 0.6)
        );

        List<PeakCandidate> fundamentals = harmonicFilter.filter(peaks);

        assertEquals(2, fundamentals.size());
    }

    @Test
    public void fixedModeCapToMaxFundamentals() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.polyphonyMode = PolyphonyMode.FIXED;
        config.fixedPolyphonyCount = 8;
        config.maxFundamentals = 2;
        HarmonicFilter harmonicFilter = new HarmonicFilter(config);

        List<PeakCandidate> peaks = Arrays.asList(
                new PeakCandidate(0, 0.0, 250.0, 10.0, 0.9),
                new PeakCandidate(1, 1.0, 350.0, 9.0, 0.8),
                new PeakCandidate(2, 2.0, 450.0, 8.0, 0.7),
                new PeakCandidate(3, 3.0, 550.0, 7.0, 0.6)
        );

        List<PeakCandidate> fundamentals = harmonicFilter.filter(peaks);

        assertEquals(2, fundamentals.size());
    }

    @Test
    public void fixedModeWithMinimumCount() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.polyphonyMode = PolyphonyMode.FIXED;
        config.fixedPolyphonyCount = 1;
        config.maxFundamentals = 4;
        HarmonicFilter harmonicFilter = new HarmonicFilter(config);

        List<PeakCandidate> peaks = Arrays.asList(
                new PeakCandidate(0, 0.0, 250.0, 10.0, 0.9),
                new PeakCandidate(1, 1.0, 350.0, 9.0, 0.8),
                new PeakCandidate(2, 2.0, 450.0, 8.0, 0.7)
        );

        List<PeakCandidate> fundamentals = harmonicFilter.filter(peaks);

        assertEquals(1, fundamentals.size());
    }
}
