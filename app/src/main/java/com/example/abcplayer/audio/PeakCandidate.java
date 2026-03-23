package com.example.abcplayer.audio;

public class PeakCandidate {

    public final int bin;
    public final double refinedBin;
    public final double frequencyHz;
    public final double magnitude;
    public final double confidence;

    public PeakCandidate(int bin, double refinedBin, double frequencyHz, double magnitude, double confidence) {
        this.bin = bin;
        this.refinedBin = refinedBin;
        this.frequencyHz = frequencyHz;
        this.magnitude = magnitude;
        this.confidence = confidence;
    }
}
