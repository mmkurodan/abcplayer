package com.example.abcplayer.audio.spectrum;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.DetectedPitch;
import com.example.abcplayer.audio.PeakCandidate;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SpectrumAnalyzer {

    private final AnalysisConfig config;
    private final DoubleFFT_1D fft;
    private final double[] window;
    private final double[] fftBuffer;
    private final double[] magnitudeBuffer;

    public SpectrumAnalyzer(AnalysisConfig config) {
        this.config = config;
        this.fft = new DoubleFFT_1D(config.fftSize);
        this.window = new double[config.fftSize];
        this.fftBuffer = new double[config.fftSize * 2];
        this.magnitudeBuffer = new double[config.fftSize / 2];
        for (int i = 0; i < config.fftSize; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (config.fftSize - 1));
        }
    }

    public double[] computeMagnitudeSpectrum(double[] samples) {
        int len = Math.min(samples.length, config.fftSize);
        for (int i = 0; i < config.fftSize; i++) {
            double sample = i < len ? samples[i] : 0.0;
            fftBuffer[2 * i] = sample * window[i];
            fftBuffer[2 * i + 1] = 0.0;
        }

        fft.complexForward(fftBuffer);

        for (int i = 0; i < magnitudeBuffer.length; i++) {
            double frequencyHz = binToFrequency(i);
            if (frequencyHz < config.minimumAnalyzedFrequencyHz || frequencyHz > config.maximumAnalyzedFrequencyHz) {
                magnitudeBuffer[i] = 0.0;
                continue;
            }
            double re = fftBuffer[2 * i];
            double im = fftBuffer[2 * i + 1];
            magnitudeBuffer[i] = Math.hypot(re, im);
        }
        return Arrays.copyOf(magnitudeBuffer, magnitudeBuffer.length);
    }

    public float[] buildNoteMagnitudes(double[] spectrum) {
        int noteCount = config.displayMaxMidi - config.displayMinMidi + 1;
        float[] noteMagnitudes = new float[noteCount];
        for (int bin = 1; bin < spectrum.length; bin++) {
            double frequencyHz = binToFrequency(bin);
            if (frequencyHz <= 0.0) {
                continue;
            }
            int midi = DetectedPitch.frequencyToMidi(frequencyHz);
            if (midi < config.displayMinMidi || midi > config.displayMaxMidi) {
                continue;
            }
            int index = midi - config.displayMinMidi;
            noteMagnitudes[index] = Math.max(noteMagnitudes[index], (float) spectrum[bin]);
        }
        return noteMagnitudes;
    }

    public double computeThreshold(double[] spectrum, double thresholdMultiplier) {
        double noiseFloor = percentileInRange(spectrum, 20.0);
        return Math.max(config.minimumPeakHeight, noiseFloor * Math.max(1.0, thresholdMultiplier));
    }

    public List<PeakCandidate> detectPeaks(double[] spectrum, double threshold) {
        int startBin = Math.max(1, frequencyToBin(config.minimumAnalyzedFrequencyHz));
        int endBin = Math.min(spectrum.length - 2, frequencyToBin(config.maximumAnalyzedFrequencyHz));
        int minimumDistanceBins = Math.max(1, (int) Math.round(config.minimumPeakDistanceHz / binResolutionHz()));
        double maxMagnitude = 0.0;
        for (int i = startBin; i <= endBin; i++) {
            maxMagnitude = Math.max(maxMagnitude, spectrum[i]);
        }

        List<Integer> candidateBins = new ArrayList<>();
        for (int i = startBin; i <= endBin; i++) {
            double magnitude = spectrum[i];
            if (magnitude < threshold || magnitude < spectrum[i - 1] || magnitude < spectrum[i + 1]) {
                continue;
            }
            candidateBins.add(i);
        }

        candidateBins.sort((left, right) -> Double.compare(spectrum[right], spectrum[left]));
        List<PeakCandidate> peaks = new ArrayList<>();
        for (int bin : candidateBins) {
            boolean tooClose = false;
            for (PeakCandidate kept : peaks) {
                if (Math.abs(bin - kept.bin) < minimumDistanceBins) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }

            double refinedBin = refineBin(spectrum, bin);
            double frequencyHz = binToFrequency(refinedBin);
            if (frequencyHz < config.minimumAnalyzedFrequencyHz || frequencyHz > config.maximumAnalyzedFrequencyHz) {
                continue;
            }
            double normalizedMagnitude = normalizeMagnitude(spectrum[bin], threshold, maxMagnitude);
            double localContrast = spectrum[bin] - Math.max(spectrum[bin - 1], spectrum[bin + 1]);
            double contrastScore = spectrum[bin] <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, localContrast / spectrum[bin]));
            double confidence = (normalizedMagnitude * 0.7) + (contrastScore * 0.3);
            peaks.add(new PeakCandidate(bin, refinedBin, frequencyHz, spectrum[bin], confidence));
            if (peaks.size() >= config.maxRawPeaks) {
                break;
            }
        }
        peaks.sort(Comparator.comparingDouble((PeakCandidate peak) -> peak.magnitude).reversed());
        return peaks;
    }

    public double binToFrequency(double bin) {
        return bin * binResolutionHz();
    }

    public double binResolutionHz() {
        return config.sampleRate / (double) config.fftSize;
    }

    private int frequencyToBin(double frequencyHz) {
        return (int) Math.round(frequencyHz / binResolutionHz());
    }

    private double percentileInRange(double[] spectrum, double percentile) {
        int startBin = Math.max(1, frequencyToBin(config.minimumAnalyzedFrequencyHz));
        int endBin = Math.min(spectrum.length - 1, frequencyToBin(config.maximumAnalyzedFrequencyHz));
        if (endBin <= startBin) {
            return 0.0;
        }
        double[] copy = Arrays.copyOfRange(spectrum, startBin, endBin + 1);
        Arrays.sort(copy);
        double rank = (percentile / 100.0) * (copy.length - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) {
            return copy[low];
        }
        double weight = rank - low;
        return copy[low] * (1.0 - weight) + copy[high] * weight;
    }

    private double normalizeMagnitude(double magnitude, double threshold, double maxMagnitude) {
        if (maxMagnitude <= threshold) {
            return magnitude > threshold ? 1.0 : 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (magnitude - threshold) / (maxMagnitude - threshold)));
    }

    private double refineBin(double[] spectrum, int bin) {
        if (bin <= 0 || bin >= spectrum.length - 1) {
            return bin;
        }
        double alpha = spectrum[bin - 1];
        double beta = spectrum[bin];
        double gamma = spectrum[bin + 1];
        double denominator = alpha - (2.0 * beta) + gamma;
        if (Math.abs(denominator) < 1e-12) {
            return bin;
        }
        double delta = 0.5 * (alpha - gamma) / denominator;
        if (!Double.isFinite(delta)) {
            return bin;
        }
        return bin + Math.max(-1.0, Math.min(1.0, delta));
    }
}
