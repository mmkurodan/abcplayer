package com.example.abcplayer.audio.preprocess;

import com.example.abcplayer.audio.AnalysisConfig;

import java.util.Arrays;

public class AudioPreprocessor {

    public static final class PreprocessedFrame {
        public final double[] samples;
        public final double rms;
        public final boolean gated;

        PreprocessedFrame(double[] samples, double rms, boolean gated) {
            this.samples = samples;
            this.rms = rms;
            this.gated = gated;
        }
    }

    private final AnalysisConfig config;
    private final double[] filteredSamples;
    private final double highPassAlpha;
    private final double lowPassAlpha;

    private double previousHighPassInput;
    private double previousHighPassOutput;
    private double lowPassOutput;

    public AudioPreprocessor(AnalysisConfig config) {
        this.config = config;
        this.filteredSamples = new double[config.fftSize];
        this.highPassAlpha = computeHighPassAlpha(config.sampleRate, config.highPassHz);
        this.lowPassAlpha = computeLowPassAlpha(config.sampleRate, config.lowPassHz);
    }

    public PreprocessedFrame process(short[] buffer, int read) {
        int len = Math.min(read, config.fftSize);
        double sumSq = 0.0;
        for (int i = 0; i < config.fftSize; i++) {
            double sample = i < len ? buffer[i] / 32768.0 : 0.0;
            double filtered = applyLowPass(applyHighPass(sample));
            filteredSamples[i] = filtered;
            if (i < len) {
                sumSq += filtered * filtered;
            }
        }

        double rms = Math.sqrt(sumSq / Math.max(1, len));
        boolean gated = rms < config.noiseGateThresholdRms;
        if (gated) {
            Arrays.fill(filteredSamples, 0.0);
        }
        return new PreprocessedFrame(Arrays.copyOf(filteredSamples, filteredSamples.length), rms, gated);
    }

    public void reset() {
        previousHighPassInput = 0.0;
        previousHighPassOutput = 0.0;
        lowPassOutput = 0.0;
        Arrays.fill(filteredSamples, 0.0);
    }

    private double applyHighPass(double sample) {
        if (config.highPassHz <= 0.0 || highPassAlpha <= 0.0) {
            return sample;
        }
        double output = highPassAlpha * (previousHighPassOutput + sample - previousHighPassInput);
        previousHighPassInput = sample;
        previousHighPassOutput = output;
        return output;
    }

    private double applyLowPass(double sample) {
        if (config.lowPassHz <= 0.0 || lowPassAlpha <= 0.0) {
            return sample;
        }
        lowPassOutput += lowPassAlpha * (sample - lowPassOutput);
        return lowPassOutput;
    }

    private static double computeHighPassAlpha(int sampleRate, double cutoffHz) {
        if (cutoffHz <= 0.0 || sampleRate <= 0) {
            return 0.0;
        }
        double dt = 1.0 / sampleRate;
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        return rc / (rc + dt);
    }

    private static double computeLowPassAlpha(int sampleRate, double cutoffHz) {
        if (cutoffHz <= 0.0 || sampleRate <= 0) {
            return 0.0;
        }
        double dt = 1.0 / sampleRate;
        double rc = 1.0 / (2.0 * Math.PI * cutoffHz);
        return dt / (rc + dt);
    }
}
