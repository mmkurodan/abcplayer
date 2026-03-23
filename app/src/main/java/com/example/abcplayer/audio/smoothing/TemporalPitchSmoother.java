package com.example.abcplayer.audio.smoothing;

import com.example.abcplayer.audio.AnalysisConfig;
import com.example.abcplayer.audio.DetectedPitch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemporalPitchSmoother {

    private static final double MAX_MATCH_RATIO = 0.12;

    private final AnalysisConfig config;
    private final PitchSlot[] slots;

    public TemporalPitchSmoother(AnalysisConfig config) {
        this.config = config;
        this.slots = new PitchSlot[config.maxFundamentals];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new PitchSlot();
        }
    }

    public List<DetectedPitch> smooth(List<DetectedPitch> rawPitches, double frameDurationSec) {
        rawPitches.sort(Comparator.comparingDouble(pitch -> pitch.frequencyHz));
        int frequencyWindowFrames = frequencyWindowFrames(frameDurationSec);
        boolean[] matched = new boolean[rawPitches.size()];

        for (PitchSlot slot : slots) {
            if (!slot.active) {
                continue;
            }
            int bestIndex = findBestMatch(slot, rawPitches, matched);
            if (bestIndex >= 0) {
                slot.update(rawPitches.get(bestIndex), config, frequencyWindowFrames);
                matched[bestIndex] = true;
            } else {
                slot.markMissing(config.holdFrames);
            }
        }

        for (int i = 0; i < rawPitches.size(); i++) {
            if (matched[i]) {
                continue;
            }
            PitchSlot target = firstInactiveSlot();
            if (target == null) {
                target = stalestSlot();
            }
            if (target != null) {
                target.initialize(rawPitches.get(i), config, frequencyWindowFrames);
            }
        }

        List<DetectedPitch> smoothed = new ArrayList<>();
        for (PitchSlot slot : slots) {
            if (slot.shouldEmit(config.minimumConfidence)) {
                smoothed.add(slot.toDetectedPitch());
            }
        }
        smoothed.sort(Comparator.comparingDouble(pitch -> pitch.frequencyHz));
        return smoothed;
    }

    public void reset() {
        for (PitchSlot slot : slots) {
            slot.clear();
        }
    }

    private int findBestMatch(PitchSlot slot, List<DetectedPitch> rawPitches, boolean[] matched) {
        int bestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < rawPitches.size(); i++) {
            if (matched[i]) {
                continue;
            }
            double ratioDistance = slot.frequencyRatioDistance(rawPitches.get(i).frequencyHz);
            if (ratioDistance > MAX_MATCH_RATIO) {
                continue;
            }
            if (ratioDistance < bestDistance) {
                bestDistance = ratioDistance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private PitchSlot firstInactiveSlot() {
        for (PitchSlot slot : slots) {
            if (!slot.active) {
                return slot;
            }
        }
        return null;
    }

    private PitchSlot stalestSlot() {
        PitchSlot stalest = null;
        for (PitchSlot slot : slots) {
            if (stalest == null || slot.staleFrames > stalest.staleFrames || slot.stableConfidence < stalest.stableConfidence) {
                stalest = slot;
            }
        }
        return stalest;
    }

    private int frequencyWindowFrames(double frameDurationSec) {
        double millis = Math.max(1.0, frameDurationSec * 1000.0);
        return Math.max(2, (int) Math.round(config.frequencySmoothingWindowMs / millis));
    }

    private static final class PitchSlot {
        boolean active;
        double stableFrequency;
        int stableMidi = -1;
        double stableConfidence;
        int staleFrames;
        final Deque<Double> frequencyHistory = new ArrayDeque<>();
        final Deque<Integer> midiHistory = new ArrayDeque<>();
        final Deque<Double> confidenceHistory = new ArrayDeque<>();

        void initialize(DetectedPitch pitch, AnalysisConfig config, int frequencyWindowFrames) {
            clear();
            active = true;
            update(pitch, config, frequencyWindowFrames);
        }

        void update(DetectedPitch pitch, AnalysisConfig config, int frequencyWindowFrames) {
            if (!active) {
                initialize(pitch, config, frequencyWindowFrames);
                return;
            }
            int midiForHistory = stableMidi;
            if (stableMidi < 0 || noteChangeRatioDistance(pitch.frequencyHz) > config.hysteresisRatio) {
                midiForHistory = pitch.midiNote;
            }

            addDouble(frequencyHistory, pitch.frequencyHz, frequencyWindowFrames);
            addInt(midiHistory, midiForHistory, config.modeWindowFrames);
            addDouble(confidenceHistory, pitch.confidence, frequencyWindowFrames);

            stableFrequency = average(frequencyHistory);
            stableMidi = mode(midiHistory, midiForHistory);
            stableConfidence = average(confidenceHistory);
            staleFrames = 0;
            active = true;
        }

        void markMissing(int holdFrames) {
            if (!active) {
                return;
            }
            staleFrames++;
            stableConfidence *= 0.85;
            if (staleFrames > holdFrames || stableConfidence < 0.05) {
                clear();
            }
        }

        boolean shouldEmit(double minimumConfidence) {
            return active && stableMidi >= 0 && stableConfidence >= minimumConfidence;
        }

        double frequencyRatioDistance(double frequencyHz) {
            if (!active || stableFrequency <= 0.0 || frequencyHz <= 0.0) {
                return Double.MAX_VALUE;
            }
            return Math.abs(frequencyHz - stableFrequency) / stableFrequency;
        }

        double noteChangeRatioDistance(double frequencyHz) {
            double referenceFrequencyHz = stableMidi >= 0
                    ? DetectedPitch.midiToFrequency(stableMidi)
                    : stableFrequency;
            if (!active || referenceFrequencyHz <= 0.0 || frequencyHz <= 0.0) {
                return Double.MAX_VALUE;
            }
            return Math.abs(frequencyHz - referenceFrequencyHz) / referenceFrequencyHz;
        }

        DetectedPitch toDetectedPitch() {
            return new DetectedPitch(stableFrequency, stableMidi, DetectedPitch.midiToNoteName(stableMidi), stableConfidence);
        }

        void clear() {
            active = false;
            stableFrequency = 0.0;
            stableMidi = -1;
            stableConfidence = 0.0;
            staleFrames = 0;
            frequencyHistory.clear();
            midiHistory.clear();
            confidenceHistory.clear();
        }

        private static void addDouble(Deque<Double> deque, double value, int maxSize) {
            deque.addLast(value);
            while (deque.size() > maxSize) {
                deque.removeFirst();
            }
        }

        private static void addInt(Deque<Integer> deque, int value, int maxSize) {
            deque.addLast(value);
            while (deque.size() > maxSize) {
                deque.removeFirst();
            }
        }

        private static double average(Deque<Double> values) {
            if (values.isEmpty()) {
                return 0.0;
            }
            double sum = 0.0;
            for (double value : values) {
                sum += value;
            }
            return sum / values.size();
        }

        private static int mode(Deque<Integer> values, int fallback) {
            if (values.isEmpty()) {
                return fallback;
            }
            Map<Integer, Integer> counts = new HashMap<>();
            int bestValue = fallback;
            int bestCount = -1;
            for (int value : values) {
                int count = counts.containsKey(value) ? counts.get(value) + 1 : 1;
                counts.put(value, count);
                if (count > bestCount || (count == bestCount && value == fallback)) {
                    bestCount = count;
                    bestValue = value;
                }
            }
            return bestValue;
        }
    }
}
