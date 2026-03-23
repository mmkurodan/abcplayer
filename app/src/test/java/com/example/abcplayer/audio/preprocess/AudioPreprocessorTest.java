package com.example.abcplayer.audio.preprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.TestSignalFactory;

import org.junit.Test;

public class AudioPreprocessorTest {

    @Test
    public void noiseGateSuppressesQuietFrames() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.fftSize = 1024;
        config.noiseGateThresholdRms = 0.05;
        AudioPreprocessor preprocessor = new AudioPreprocessor(config);

        short[] quietFrame = TestSignalFactory.sineFrame(config.sampleRate, config.fftSize, 440.0, 0.004);
        AudioPreprocessor.PreprocessedFrame processed = preprocessor.process(quietFrame, quietFrame.length);

        assertTrue(processed.gated);
        for (double sample : processed.samples) {
            assertEquals(0.0, sample, 1e-9);
        }
    }

    @Test
    public void bandPassFavorsInBandSignal() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.fftSize = 1024;
        config.noiseGateThresholdRms = 0.0;

        double inBandRms = new AudioPreprocessor(config)
                .process(TestSignalFactory.sineFrame(config.sampleRate, config.fftSize, 440.0, 0.6), config.fftSize)
                .rms;
        double lowBandRms = new AudioPreprocessor(config)
                .process(TestSignalFactory.sineFrame(config.sampleRate, config.fftSize, 40.0, 0.6), config.fftSize)
                .rms;
        double highBandRms = new AudioPreprocessor(config)
                .process(TestSignalFactory.sineFrame(config.sampleRate, config.fftSize, 8000.0, 0.6), config.fftSize)
                .rms;

        assertTrue(inBandRms > lowBandRms * 1.5);
        assertTrue(inBandRms > highBandRms * 1.5);
    }
}
