package com.example.abcplayer;

public class SineWaveSynth {

    public static short[] generatePcm(NoteEvent[] notes,
                                      int sampleRate,
                                      double baseDurationSec) {

        int totalSamples = 0;
        for (NoteEvent n : notes) {
            double sec = n.durationSec * baseDurationSec;
            totalSamples += (int) (sec * sampleRate);
        }

        short[] pcm = new short[totalSamples];
        int pos = 0;

        for (NoteEvent n : notes) {
            double noteSec = n.durationSec * baseDurationSec;
            int samples = (int) (noteSec * sampleRate);
            double freq = midiToFreq(n.midiNote);

            for (int i = 0; i < samples && pos < pcm.length; i++, pos++) {
                double t = (double) i / sampleRate;
                double angle = 2.0 * Math.PI * freq * t;
                double value = Math.sin(angle);
                short s = (short) (value * 0.8 * Short.MAX_VALUE);
                pcm[pos] = s;
            }
        }

        return pcm;
    }

    private static double midiToFreq(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }
}
