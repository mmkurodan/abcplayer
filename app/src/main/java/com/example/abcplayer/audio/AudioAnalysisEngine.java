package com.example.abcplayer.audio;

import com.example.abcplayer.audio.harmonic_filter.HarmonicFilter;
import com.example.abcplayer.audio.preprocess.AudioPreprocessor;
import com.example.abcplayer.audio.preprocess.SpectralSmoother;
import com.example.abcplayer.audio.smoothing.TemporalPitchSmoother;
import com.example.abcplayer.audio.spectrum.SpectrumAnalyzer;

import java.util.ArrayList;
import java.util.List;

public class AudioAnalysisEngine {

    private final AudioPreprocessor preprocessor;
    private final SpectralSmoother spectralSmoother;
    private final SpectrumAnalyzer spectrumAnalyzer;
    private final HarmonicFilter harmonicFilter;
    private final TemporalPitchSmoother temporalSmoother;

    public AudioAnalysisEngine(AnalysisConfig config) {
        this.preprocessor = new AudioPreprocessor(config);
        this.spectralSmoother = new SpectralSmoother(config.spectralSmoothingFrames);
        this.spectrumAnalyzer = new SpectrumAnalyzer(config);
        this.harmonicFilter = new HarmonicFilter(config);
        this.temporalSmoother = new TemporalPitchSmoother(config);
    }

    public AudioAnalysisResult analyze(short[] buffer, int read, double frameDurationSec, double thresholdMultiplier) {
        AudioPreprocessor.PreprocessedFrame preprocessed = preprocessor.process(buffer, read);
        double[] spectrum = preprocessed.gated
                ? new double[buffer.length / 2]
                : spectrumAnalyzer.computeMagnitudeSpectrum(preprocessed.samples);
        double[] smoothedSpectrum;
        if (preprocessed.gated) {
            spectralSmoother.reset();
            smoothedSpectrum = spectrum;
        } else {
            smoothedSpectrum = spectralSmoother.smooth(spectrum);
        }

        float[] noteMagnitudes = spectrumAnalyzer.buildNoteMagnitudes(smoothedSpectrum);
        double threshold = preprocessed.gated ? 0.0 : spectrumAnalyzer.computeThreshold(smoothedSpectrum, thresholdMultiplier);

        List<PeakCandidate> rawPeaks = preprocessed.gated
                ? new ArrayList<>()
                : spectrumAnalyzer.detectPeaks(smoothedSpectrum, threshold);
        List<PeakCandidate> fundamentals = harmonicFilter.filter(rawPeaks);

        List<DetectedPitch> rawDetectedPitches = new ArrayList<>(fundamentals.size());
        for (PeakCandidate peak : fundamentals) {
            rawDetectedPitches.add(DetectedPitch.fromFrequency(peak.frequencyHz, peak.confidence));
        }
        List<DetectedPitch> stablePitches = temporalSmoother.smooth(rawDetectedPitches, frameDurationSec);

        return new AudioAnalysisResult(
                stablePitches,
                noteMagnitudes,
                threshold,
                preprocessed.rms,
                frameDurationSec,
                peakFrequencies(rawPeaks),
                peakFrequencies(fundamentals),
                buildNoteSummary(stablePitches)
        );
    }

    public void reset() {
        preprocessor.reset();
        spectralSmoother.reset();
        temporalSmoother.reset();
    }

    private double[] peakFrequencies(List<PeakCandidate> peaks) {
        double[] frequencies = new double[peaks.size()];
        for (int i = 0; i < peaks.size(); i++) {
            frequencies[i] = peaks.get(i).frequencyHz;
        }
        return frequencies;
    }

    private String buildNoteSummary(List<DetectedPitch> pitches) {
        if (pitches.isEmpty()) {
            return "z";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pitches.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(pitches.get(i).noteName);
        }
        return builder.toString();
    }
}
