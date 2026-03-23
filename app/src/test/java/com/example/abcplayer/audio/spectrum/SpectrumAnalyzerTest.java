package com.example.abcplayer.audio.spectrum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.PeakCandidate;
import com.example.abcplayer.audio.TestSignalFactory;

import org.junit.Test;

import java.util.List;

public class SpectrumAnalyzerTest {

    @Test
    public void parabolicInterpolationRefinesSineFrequency() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(44100);
        config.fftSize = 1024;
        config.minimumPeakHeight = 0.1;
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer(config);

        short[] frame = TestSignalFactory.sineFrame(config.sampleRate, config.fftSize, 440.0, 0.8);
        double[] spectrum = analyzer.computeMagnitudeSpectrum(TestSignalFactory.toNormalizedDoubles(frame));
        double threshold = analyzer.computeThreshold(spectrum, 50.0);
        List<PeakCandidate> peaks = analyzer.detectPeaks(spectrum, threshold);

        assertTrue(!peaks.isEmpty());
        assertEquals(440.0, peaks.get(0).frequencyHz, 6.0);
    }

    @Test
    public void peakDetectionRespectsDistanceAndLimit() {
        AnalysisConfig config = AnalysisConfig.realtimeDefaults(6400);
        config.fftSize = 128;
        config.minimumPeakDistanceHz = 100.0;
        config.maxRawPeaks = 3;
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer(config);

        double[] spectrum = new double[config.fftSize / 2];
        spectrum[5] = 10.0;
        spectrum[6] = 9.0;
        spectrum[9] = 8.0;
        spectrum[12] = 7.0;
        spectrum[15] = 6.0;

        List<PeakCandidate> peaks = analyzer.detectPeaks(spectrum, 1.0);

        assertEquals(3, peaks.size());
        assertEquals(5, peaks.get(0).bin);
        assertEquals(9, peaks.get(1).bin);
        assertEquals(12, peaks.get(2).bin);
    }
}
