package com.example.abcplayer;

/**
 * PCM 合成（簡易）。

 * エンベロープはノート開始からの相対時間で計算し、曲全体が減衰し続ける問題を防ぐ。
 */
public class SineWaveSynth {

    public static short[] generatePcm(
            NoteEvent[] notes,
            int sampleRate,
            double tempoBpm,
            double defaultLen
    ) {
        double secPerBeat = 60.0 / tempoBpm;
        int totalSamples = 0;
        for (NoteEvent n : notes) {
            double seconds = n.beats * secPerBeat;
            totalSamples += (int) (seconds * sampleRate);
        }

        short[] pcm = new short[totalSamples];
        int globalSampleIndex = 0;

        for (NoteEvent n : notes) {
            double seconds = n.beats * secPerBeat;
            int samples = (int) (seconds * sampleRate);
            if (n.isRest) { globalSampleIndex += samples; continue; }

            for (int i = 0; i < samples; i++) {
                double tAbs = (double) (globalSampleIndex + i) / sampleRate;
                double tLocal = (double) i / sampleRate; // ノート開始からの時間
                double sum = 0;
                for (int midi : n.midiNotes) {
                    sum += voiceSample(n.program, midi, tAbs, tLocal);
                }
                sum /= n.midiNotes.length;
                short s = (short) (sum * 32767);
                int pos = globalSampleIndex + i;
                if (pos < pcm.length) {
                    int mixed = pcm[pos] + s;
                    if (mixed > 32767) mixed = 32767;
                    if (mixed < -32768) mixed = -32768;
                    pcm[pos] = (short) mixed;
                }
            }
            globalSampleIndex += samples;
        }
        return pcm;
    }

    public static short[] generatePcmChunk(
            NoteEvent[] notes,
            int sampleRate,
            double tempoBpm,
            double defaultLen,
            int startSample,
            int chunkSamples
    ) {
        double secPerBeat = 60.0 / tempoBpm;
        short[] pcm = new short[chunkSamples];
        int curSampleIndex = 0;

        for (NoteEvent n : notes) {
            double seconds = n.beats * secPerBeat;
            int noteSamples = (int) (seconds * sampleRate);
            int noteStart = curSampleIndex;
            int noteEnd = curSampleIndex + noteSamples;
            int overlapStart = Math.max(noteStart, startSample);
            int overlapEnd = Math.min(noteEnd, startSample + chunkSamples);

            if (!n.isRest && overlapStart < overlapEnd) {
                for (int s = overlapStart; s < overlapEnd; s++) {
                    int i = s - startSample;
                    double tAbs = (double) s / sampleRate;
                    double tLocal = (double) (s - noteStart) / sampleRate;
                    double sum = 0;
                    for (int midi : n.midiNotes) {
                        sum += voiceSample(n.program, midi, tAbs, tLocal);
                    }
                    sum /= n.midiNotes.length;
                    short sVal = (short) (sum * 32767);
                    int mixed = pcm[i] + sVal;
                    if (mixed > 32767) mixed = 32767;
                    if (mixed < -32768) mixed = -32768;
                    pcm[i] = (short) mixed;
                }
            }

            curSampleIndex += noteSamples;
            if (curSampleIndex >= startSample + chunkSamples) break;
        }
        return pcm;
    }

    private static double midiToFreq(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private static double voiceSample(int program, int midi, double tAbs, double tLocal) {
        double freq = midiToFreq(midi);
        double omega = 2.0 * Math.PI * freq;
        int p = program;
        if (p >= 0 && p <= 7) {
            double env = Math.exp(-tLocal * 6.0); // ピアノ系: ノート頭から減衰
            return env * Math.sin(omega * tAbs);
        } else if (p >= 16 && p <= 23) {
            return square(omega * tAbs) * 0.6;
        } else if (p >= 24 && p <= 31) {
            double env = Math.exp(-tLocal * 4.0); // ギター系
            return env * triangle(omega * tAbs);
        } else if (p >= 40 && p <= 47) {
            double env = Math.exp(-tLocal * 3.0); // ストリングス系
            return env * saw(omega * tAbs) * 0.7;
        } else {
            return Math.sin(omega * tAbs);
        }
    }

    private static double square(double x) {
        double s = Math.sin(x);
        return s >= 0 ? 1.0 : -1.0;
    }

    private static double saw(double x) {
        double t = x / (2.0 * Math.PI);
        return 2.0 * (t - Math.floor(t + 0.5));
    }

    private static double triangle(double x) {
        return 2.0 / Math.PI * Math.asin(Math.sin(x));
    }
}
