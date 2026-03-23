package com.example.abcplayer.audio;

public final class TestSignalFactory {

    private TestSignalFactory() {
    }

    public static short[] sineFrame(int sampleRate, int frameSize, double frequencyHz, double amplitude) {
        return chordFrame(sampleRate, frameSize, new double[]{frequencyHz}, amplitude);
    }

    public static short[] chordFrame(int sampleRate, int frameSize, double[] frequenciesHz, double amplitudePerTone) {
        short[] frame = new short[frameSize];
        for (int i = 0; i < frameSize; i++) {
            double sample = 0.0;
            for (double frequencyHz : frequenciesHz) {
                sample += amplitudePerTone * Math.sin((2.0 * Math.PI * frequencyHz * i) / sampleRate);
            }
            sample = Math.max(-0.95, Math.min(0.95, sample));
            frame[i] = (short) Math.round(sample * 32767.0);
        }
        return frame;
    }

    public static double[] toNormalizedDoubles(short[] frame) {
        double[] samples = new double[frame.length];
        for (int i = 0; i < frame.length; i++) {
            samples[i] = frame[i] / 32768.0;
        }
        return samples;
    }
}
