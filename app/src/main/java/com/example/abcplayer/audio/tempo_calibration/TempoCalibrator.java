package com.example.abcplayer.audio.tempo_calibration;

import com.example.abcplayer.audio.DetectedPitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TempoCalibrator {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final double requestedBpm;
    private final long minimumOnsetIntervalNanos;
    private final List<Long> onsetTimestamps = new ArrayList<>();

    private long recordingStartNanos = -1L;
    private long lastObservedNanos = -1L;
    private long lastOnsetNanos = -1L;
    private String lastPitchSignature = "";
    private double recordedBeatCount;

    public TempoCalibrator(double requestedBpm, double minimumOnsetIntervalMs) {
        this.requestedBpm = requestedBpm > 0.0 ? requestedBpm : 120.0;
        this.minimumOnsetIntervalNanos = (long) Math.max(1.0, minimumOnsetIntervalMs * 1_000_000.0);
    }

    public void start(long startNanos) {
        recordingStartNanos = startNanos;
        lastObservedNanos = startNanos;
        lastOnsetNanos = -1L;
        lastPitchSignature = "";
        recordedBeatCount = 0.0;
        onsetTimestamps.clear();
    }

    public void observeDetectedPitches(List<DetectedPitch> stablePitches, long timestampNanos) {
        if (recordingStartNanos < 0L) {
            start(timestampNanos);
        }
        lastObservedNanos = timestampNanos;
        String signature = buildSignature(stablePitches);
        if (!signature.isEmpty() && !signature.equals(lastPitchSignature)) {
            if (lastOnsetNanos < 0L || timestampNanos - lastOnsetNanos >= minimumOnsetIntervalNanos) {
                onsetTimestamps.add(timestampNanos);
                lastOnsetNanos = timestampNanos;
            }
        }
        lastPitchSignature = signature;
    }

    public void recordQuantizedBeats(double beats) {
        if (Double.isFinite(beats) && beats > 0.0) {
            recordedBeatCount += beats;
        }
    }

    public TempoMetadata finish(long endNanos) {
        if (recordingStartNanos < 0L) {
            return TempoMetadata.identity(requestedBpm);
        }
        long effectiveEndNanos = Math.max(endNanos, lastObservedNanos);
        double elapsedSeconds = Math.max(0.0, (effectiveEndNanos - recordingStartNanos) / NANOS_PER_SECOND);
        double beatBasedBpm = elapsedSeconds > 0.0 && recordedBeatCount > 0.0
                ? (recordedBeatCount / elapsedSeconds) * 60.0
                : Double.NaN;
        double onsetBasedBpm = estimateOnsetBpm();
        double actualBpm = blendActualBpm(beatBasedBpm, onsetBasedBpm);
        if (!Double.isFinite(actualBpm) || actualBpm <= 0.0) {
            actualBpm = requestedBpm;
        }
        double correctionFactor = actualBpm / requestedBpm;
        return new TempoMetadata(actualBpm, requestedBpm, correctionFactor, recordedBeatCount, onsetTimestamps.size(), elapsedSeconds);
    }

    public boolean shouldRecalculate(long nowNanos, double everySeconds) {
        return lastObservedNanos > 0L && nowNanos - lastObservedNanos >= everySeconds * NANOS_PER_SECOND;
    }

    private double estimateOnsetBpm() {
        if (onsetTimestamps.size() < 2) {
            return Double.NaN;
        }
        List<Double> intervalsSeconds = new ArrayList<>();
        for (int i = 1; i < onsetTimestamps.size(); i++) {
            long deltaNanos = onsetTimestamps.get(i) - onsetTimestamps.get(i - 1);
            if (deltaNanos > 0L) {
                intervalsSeconds.add(deltaNanos / NANOS_PER_SECOND);
            }
        }
        if (intervalsSeconds.isEmpty()) {
            return Double.NaN;
        }
        Collections.sort(intervalsSeconds);
        double medianInterval = intervalsSeconds.get(intervalsSeconds.size() / 2);
        if (medianInterval <= 0.0) {
            return Double.NaN;
        }
        return 60.0 / medianInterval;
    }

    private double blendActualBpm(double beatBasedBpm, double onsetBasedBpm) {
        boolean hasBeatEstimate = Double.isFinite(beatBasedBpm) && beatBasedBpm > 0.0;
        boolean hasOnsetEstimate = Double.isFinite(onsetBasedBpm) && onsetBasedBpm > 0.0;
        if (hasBeatEstimate && hasOnsetEstimate) {
            return (beatBasedBpm * 0.75) + (onsetBasedBpm * 0.25);
        }
        if (hasBeatEstimate) {
            return beatBasedBpm;
        }
        if (hasOnsetEstimate) {
            return onsetBasedBpm;
        }
        return requestedBpm;
    }

    private String buildSignature(List<DetectedPitch> stablePitches) {
        StringBuilder builder = new StringBuilder();
        for (DetectedPitch pitch : stablePitches) {
            if (pitch.confidence < 0.2) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(pitch.midiNote);
        }
        return builder.toString();
    }
}
