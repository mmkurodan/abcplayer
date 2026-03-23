package com.example.abcplayer.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AudioAnalysisResult {

    public final List<DetectedPitch> stablePitches;
    public final float[] noteMagnitudes;
    public final double threshold;
    public final double rms;
    public final double frameDurationSec;
    public final double[] rawPeakFrequenciesHz;
    public final double[] candidateFundamentalFrequenciesHz;
    public final String noteSummary;

    public AudioAnalysisResult(
            List<DetectedPitch> stablePitches,
            float[] noteMagnitudes,
            double threshold,
            double rms,
            double frameDurationSec,
            double[] rawPeakFrequenciesHz,
            double[] candidateFundamentalFrequenciesHz,
            String noteSummary
    ) {
        this.stablePitches = Collections.unmodifiableList(new ArrayList<>(stablePitches));
        this.noteMagnitudes = Arrays.copyOf(noteMagnitudes, noteMagnitudes.length);
        this.threshold = threshold;
        this.rms = rms;
        this.frameDurationSec = frameDurationSec;
        this.rawPeakFrequenciesHz = Arrays.copyOf(rawPeakFrequenciesHz, rawPeakFrequenciesHz.length);
        this.candidateFundamentalFrequenciesHz = Arrays.copyOf(candidateFundamentalFrequenciesHz, candidateFundamentalFrequenciesHz.length);
        this.noteSummary = noteSummary;
    }
}
