package com.example.abcplayer.audio;

public class AnalysisConfig {

    public int sampleRate;
    public int fftSize;
    public double highPassHz = 120.0;
    public double lowPassHz = 6000.0;
    public double minimumAnalyzedFrequencyHz = 120.0;
    public double maximumAnalyzedFrequencyHz = 6000.0;
    public double noiseGateThresholdRms = 0.008;
    public int spectralSmoothingFrames = 3;
    public double minimumPeakHeight = 0.15;
    public double minimumPeakDistanceHz = 45.0;
    public int maxRawPeaks = 12;
    public int maxFundamentals = 4;
    public double harmonicTolerance = 0.04;
    public int maxHarmonicMultiple = 8;
    public double hysteresisRatio = 0.04;
    public int modeWindowFrames = 7;
    public double frequencySmoothingWindowMs = 80.0;
    public int holdFrames = 2;
    public double minimumConfidence = 0.15;
    public double minimumOnsetIntervalMs = 90.0;
    public int displayMinMidi = 36;
    public int displayMaxMidi = 96;

    public static AnalysisConfig realtimeDefaults(int sampleRate) {
        AnalysisConfig config = new AnalysisConfig();
        config.sampleRate = sampleRate;
        config.fftSize = selectRealtimeFftSize(sampleRate);
        config.lowPassHz = Math.min(config.lowPassHz, sampleRate * 0.45);
        config.maximumAnalyzedFrequencyHz = Math.min(config.maximumAnalyzedFrequencyHz, sampleRate * 0.45);
        return config;
    }

    private static int selectRealtimeFftSize(int sampleRate) {
        int targetSamples = Math.max(512, sampleRate / 40);
        int fftSize = 256;
        while (fftSize * 2 <= targetSamples) {
            fftSize *= 2;
        }
        return Math.max(512, fftSize);
    }
}
