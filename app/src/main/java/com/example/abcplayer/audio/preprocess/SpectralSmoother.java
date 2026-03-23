package com.example.abcplayer.audio.preprocess;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class SpectralSmoother {

    private final int frameWindow;
    private final Deque<double[]> history = new ArrayDeque<>();
    private double[] smoothed;

    public SpectralSmoother(int frameWindow) {
        this.frameWindow = Math.max(1, frameWindow);
    }

    public double[] smooth(double[] magnitudes) {
        if (smoothed == null || smoothed.length != magnitudes.length) {
            smoothed = new double[magnitudes.length];
        }
        history.addLast(Arrays.copyOf(magnitudes, magnitudes.length));
        while (history.size() > frameWindow) {
            history.removeFirst();
        }

        Arrays.fill(smoothed, 0.0);
        for (double[] frame : history) {
            for (int i = 0; i < frame.length; i++) {
                smoothed[i] += frame[i];
            }
        }
        double scale = 1.0 / Math.max(1, history.size());
        for (int i = 0; i < smoothed.length; i++) {
            smoothed[i] *= scale;
        }
        return Arrays.copyOf(smoothed, smoothed.length);
    }

    public void reset() {
        history.clear();
        if (smoothed != null) {
            Arrays.fill(smoothed, 0.0);
        }
    }
}
