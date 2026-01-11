package com.example.abcplayer;

public class SineWaveSynth {

    public static short[] generatePcm(
            NoteEvent[] notes,
            int sampleRate,
            double tempoBpm,
            double defaultLength
    ) {

        double secPerBeat = 60.0 / tempoBpm;

        int totalSamples = 0;
        for (NoteEvent n : notes) {
            double beats = n.length / defaultLength;
            double sec = beats * secPerBeat;
            totalSamples += (int) (sec * sampleRate);
        }

        short[] pcm = new short[totalSamples];
        int pos = 0;

        for (NoteEvent n : notes) {

            double beats = n.length / defaultLength;
            double sec = beats * secPerBeat;
            int samples = (int) (sec * sampleRate);

            int[] chord = n.midiNotes;

            for (int i = 0; i < samples && pos < pcm.length; i++, pos++) {

                double t = (double) i / sampleRate;

                double sum = 0.0;
                int activeNotes = 0;

                for (int midi : chord) {
                    if (midi < 0) continue;
                    double freq = midiToFreq(midi);
                    sum += Math.sin(2.0 * Math.PI * freq * t);
                    activeNotes++;
                }

                double value = (activeNotes > 0) ? sum / activeNotes : 0.0;

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
