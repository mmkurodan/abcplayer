package com.example.abcplayer.audio.harmonic_filter;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.PeakCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HarmonicFilter {

    private final AnalysisConfig config;

    public HarmonicFilter(AnalysisConfig config) {
        this.config = config;
    }

    public List<PeakCandidate> filter(List<PeakCandidate> rawPeaks) {
        List<PeakCandidate> byFrequency = new ArrayList<>(rawPeaks);
        byFrequency.sort(Comparator.comparingDouble(peak -> peak.frequencyHz));

        List<PeakCandidate> kept = new ArrayList<>();
        for (PeakCandidate peak : byFrequency) {
            if (isHarmonicOrDuplicate(peak, kept)) {
                continue;
            }
            kept.add(peak);
        }

        kept.sort(Comparator.comparingDouble((PeakCandidate peak) -> peak.magnitude).reversed());
        
        int maxCount = getMaxPolyphonyCount();
        if (kept.size() > maxCount) {
            kept = new ArrayList<>(kept.subList(0, maxCount));
        }
        kept.sort(Comparator.comparingDouble(peak -> peak.frequencyHz));
        return kept;
    }

    private int getMaxPolyphonyCount() {
        switch (config.polyphonyMode) {
            case AUTOMATIC:
                return config.maxFundamentals;
            case FIXED:
                return Math.max(1, Math.min(config.fixedPolyphonyCount, config.maxFundamentals));
            default:
                return config.maxFundamentals;
        }
    }

    private boolean isHarmonicOrDuplicate(PeakCandidate candidate, List<PeakCandidate> kept) {
        for (PeakCandidate base : kept) {
            if (Math.abs(candidate.frequencyHz - base.frequencyHz) <= config.minimumPeakDistanceHz) {
                return true;
            }
            double ratio = candidate.frequencyHz / base.frequencyHz;
            double nearestMultiple = Math.rint(ratio);
            if (nearestMultiple < 2.0 || nearestMultiple > config.maxHarmonicMultiple) {
                continue;
            }
            double expectedHarmonicHz = base.frequencyHz * nearestMultiple;
            double toleranceHz = Math.max(config.minimumPeakDistanceHz * 0.5, expectedHarmonicHz * config.harmonicTolerance);
            if (Math.abs(candidate.frequencyHz - expectedHarmonicHz) <= toleranceHz) {
                return true;
            }
        }
        return false;
    }
}
