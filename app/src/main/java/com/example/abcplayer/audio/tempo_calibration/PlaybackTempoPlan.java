package com.example.abcplayer.audio.tempo_calibration;

public class PlaybackTempoPlan {

    public final double requestedBpm;
    public final double actualBpm;
    public final double correctionFactor;
    public final double baseIntervalSec;
    public final double correctedIntervalSec;
    public final double correctedTempoBpm;

    public PlaybackTempoPlan(
            double requestedBpm,
            double actualBpm,
            double correctionFactor,
            double baseIntervalSec,
            double correctedIntervalSec,
            double correctedTempoBpm
    ) {
        this.requestedBpm = requestedBpm;
        this.actualBpm = actualBpm;
        this.correctionFactor = correctionFactor;
        this.baseIntervalSec = baseIntervalSec;
        this.correctedIntervalSec = correctedIntervalSec;
        this.correctedTempoBpm = correctedTempoBpm;
    }

    public static PlaybackTempoPlan fromMetadata(TempoMetadata metadata, double requestedBpm) {
        double safeRequestedBpm = requestedBpm > 0.0
                ? requestedBpm
                : metadata != null && metadata.userBpm > 0.0 ? metadata.userBpm : 120.0;
        double actualBpm = metadata != null && metadata.actualBpm > 0.0 ? metadata.actualBpm : safeRequestedBpm;
        double correctionFactor = safeRequestedBpm > 0.0 ? actualBpm / safeRequestedBpm : 1.0;
        if (!Double.isFinite(correctionFactor) || correctionFactor <= 0.0) {
            correctionFactor = 1.0;
        }
        double baseIntervalSec = 60.0 / safeRequestedBpm;
        double correctedIntervalSec = baseIntervalSec * correctionFactor;
        double correctedTempoBpm = correctedIntervalSec > 0.0 ? 60.0 / correctedIntervalSec : safeRequestedBpm;
        return new PlaybackTempoPlan(
                safeRequestedBpm,
                actualBpm,
                correctionFactor,
                baseIntervalSec,
                correctedIntervalSec,
                correctedTempoBpm
        );
    }
}
