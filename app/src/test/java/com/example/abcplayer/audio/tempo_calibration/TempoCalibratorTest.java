package com.example.abcplayer.audio.tempo_calibration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class TempoCalibratorTest {

    @Test
    public void calculatesActualBpmFromRecordedBeatsAndElapsedTime() {
        TempoCalibrator calibrator = new TempoCalibrator(120.0, 90.0);
        calibrator.start(0L);
        calibrator.recordQuantizedBeats(8.0);

        TempoMetadata metadata = calibrator.finish(4_000_000_000L);

        assertEquals(120.0, metadata.actualBpm, 0.01);
        assertEquals(1.0, metadata.correctionFactor, 1e-9);
    }

    @Test
    public void metadataRoundTripsAndBuildsPlaybackPlan() {
        TempoMetadata original = new TempoMetadata(118.5, 120.0, 118.5 / 120.0, 16.0, 12, 8.10);
        String abc = "X:1\nK:C\n" + original.toCommentLine() + "\nC ";

        TempoMetadata parsed = TempoMetadata.fromAbc(abc);
        PlaybackTempoPlan plan = PlaybackTempoPlan.fromMetadata(parsed, 120.0);

        assertNotNull(parsed);
        assertEquals(original.actualBpm, parsed.actualBpm, 0.0001);
        assertEquals(original.userBpm, parsed.userBpm, 0.0001);
        assertEquals(original.correctionFactor, plan.correctionFactor, 0.0001);
        assertEquals((60.0 / 120.0) * (118.5 / 120.0), plan.correctedIntervalSec, 1e-9);
    }
}
