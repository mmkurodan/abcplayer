package com.example.abcplayer.audio.preprocess;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class SpectralSmootherTest {

    @Test
    public void smoothAveragesRecentFrames() {
        SpectralSmoother smoother = new SpectralSmoother(3);

        smoother.smooth(new double[]{1.0, 3.0});
        smoother.smooth(new double[]{2.0, 5.0});
        double[] smoothed = smoother.smooth(new double[]{4.0, 7.0});

        assertArrayEquals(new double[]{7.0 / 3.0, 5.0}, smoothed, 1e-9);
    }
}
