package com.example.abcplayer.audio.smoothing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.DetectedPitch;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class TemporalPitchSmootherTest {

    @Test
    public void hysteresisKeepsStableNoteThroughSmallJitter() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.maxFundamentals = 1;
        TemporalPitchSmoother smoother = new TemporalPitchSmoother(config);

        List<DetectedPitch> smoothed = Collections.emptyList();
        double frameDurationSec = config.fftSize / (double) config.sampleRate;
        double[] jitteredFrequencies = {440.0, 443.0, 437.0, 441.0, 444.0};
        for (double frequencyHz : jitteredFrequencies) {
            smoothed = smoother.smooth(
                    Collections.singletonList(DetectedPitch.fromFrequency(frequencyHz, 0.9)),
                    frameDurationSec
            );
        }

        assertEquals(1, smoothed.size());
        assertEquals("A4", smoothed.get(0).noteName);
        assertTrue(Math.abs(smoothed.get(0).frequencyHz - 441.0) < 4.0);
    }

    @Test
    public void modeFilterPrefersMostFrequentRecentNote() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.maxFundamentals = 1;
        config.modeWindowFrames = 5;
        TemporalPitchSmoother smoother = new TemporalPitchSmoother(config);

        double frameDurationSec = config.fftSize / (double) config.sampleRate;
        double[] sequence = {440.0, 466.16, 440.0, 440.0, 466.16};
        List<DetectedPitch> smoothed = Collections.emptyList();
        for (double frequencyHz : sequence) {
            smoothed = smoother.smooth(
                    Collections.singletonList(DetectedPitch.fromFrequency(frequencyHz, 0.9)),
                    frameDurationSec
            );
        }

        assertEquals(1, smoothed.size());
        assertEquals("A4", smoothed.get(0).noteName);
    }
}
