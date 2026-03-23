package com.example.abcplayer.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AudioAnalysisEngineTest {

    @Test
    public void detectsMultipleStablePitchesForChord() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.fftSize = 1024;
        config.noiseGateThresholdRms = 0.0;
        config.minimumPeakHeight = 0.1;
        AudioAnalysisEngine engine = new AudioAnalysisEngine(config);

        short[] frame = TestSignalFactory.chordFrame(
                config.sampleRate,
                config.fftSize,
                new double[]{349.23, 466.16, 587.33, 739.99},
                0.20
        );

        AudioAnalysisResult result = null;
        double frameDurationSec = config.fftSize / (double) config.sampleRate;
        for (int i = 0; i < 6; i++) {
            result = engine.analyze(frame, frame.length, frameDurationSec, 20.0);
        }

        assertEquals("stable=" + result.stablePitches + " raw=" + Arrays.toString(result.rawPeakFrequenciesHz),
                4,
                result.stablePitches.size());
        Set<String> noteNames = new HashSet<>();
        for (DetectedPitch pitch : result.stablePitches) {
            noteNames.add(pitch.noteName);
        }
        assertTrue(noteNames.contains("F4"));
        assertTrue(noteNames.contains("A#4"));
        assertTrue(noteNames.contains("D5"));
        assertTrue(noteNames.contains("F#5"));
        assertTrue(result.rawPeakFrequenciesHz.length <= 12);
    }
}
