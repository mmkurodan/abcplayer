package com.example.abcplayer.audio.tempo_calibration;

import java.util.Locale;

public class TempoMetadata {

    public static final String PREFIX = "%abcplayer-tempo";

    public final double actualBpm;
    public final double userBpm;
    public final double correctionFactor;
    public final double beatCount;
    public final int onsetCount;
    public final double elapsedSeconds;

    public TempoMetadata(
            double actualBpm,
            double userBpm,
            double correctionFactor,
            double beatCount,
            int onsetCount,
            double elapsedSeconds
    ) {
        this.actualBpm = actualBpm;
        this.userBpm = userBpm;
        this.correctionFactor = correctionFactor;
        this.beatCount = beatCount;
        this.onsetCount = onsetCount;
        this.elapsedSeconds = elapsedSeconds;
    }

    public String toCommentLine() {
        return String.format(
                Locale.US,
                "%s actual-bpm=%.4f user-bpm=%.4f correction=%.6f beat-count=%.4f onset-count=%d elapsed-sec=%.4f",
                PREFIX,
                actualBpm,
                userBpm,
                correctionFactor,
                beatCount,
                onsetCount,
                elapsedSeconds
        );
    }

    public static TempoMetadata identity(double bpm) {
        double safeBpm = bpm > 0.0 ? bpm : 120.0;
        return new TempoMetadata(safeBpm, safeBpm, 1.0, 0.0, 0, 0.0);
    }

    public static TempoMetadata fromAbc(String abcText) {
        if (abcText == null || abcText.isEmpty()) {
            return null;
        }
        String[] lines = abcText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(PREFIX)) {
                continue;
            }
            double actualBpm = Double.NaN;
            double userBpm = Double.NaN;
            double correctionFactor = Double.NaN;
            double beatCount = 0.0;
            int onsetCount = 0;
            double elapsedSeconds = 0.0;

            String[] parts = trimmed.substring(PREFIX.length()).trim().split("\\s+");
            for (String part : parts) {
                int equalsIndex = part.indexOf('=');
                if (equalsIndex <= 0 || equalsIndex >= part.length() - 1) {
                    continue;
                }
                String key = part.substring(0, equalsIndex);
                String value = part.substring(equalsIndex + 1);
                try {
                    if ("actual-bpm".equals(key)) {
                        actualBpm = Double.parseDouble(value);
                    } else if ("user-bpm".equals(key)) {
                        userBpm = Double.parseDouble(value);
                    } else if ("correction".equals(key)) {
                        correctionFactor = Double.parseDouble(value);
                    } else if ("beat-count".equals(key)) {
                        beatCount = Double.parseDouble(value);
                    } else if ("onset-count".equals(key)) {
                        onsetCount = Integer.parseInt(value);
                    } else if ("elapsed-sec".equals(key)) {
                        elapsedSeconds = Double.parseDouble(value);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed metadata tokens and fall back to sane defaults below.
                }
            }

            if (!Double.isFinite(userBpm) || userBpm <= 0.0) {
                userBpm = Double.isFinite(actualBpm) && actualBpm > 0.0 ? actualBpm : 120.0;
            }
            if (!Double.isFinite(actualBpm) || actualBpm <= 0.0) {
                actualBpm = userBpm;
            }
            if (!Double.isFinite(correctionFactor) || correctionFactor <= 0.0) {
                correctionFactor = actualBpm / userBpm;
            }
            return new TempoMetadata(actualBpm, userBpm, correctionFactor, beatCount, onsetCount, elapsedSeconds);
        }
        return null;
    }
}
