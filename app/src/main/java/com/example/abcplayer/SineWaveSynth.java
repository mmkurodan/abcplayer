package com.example.abcplayer;

/**
 * PCM 合成（簡易）。

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
                double t = (double) (globalSampleIndex + i) / sampleRate;
                double sum = 0;
                for (int midi : n.midiNotes) {
                    sum += voiceSample(n.program, midi, t);
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
                    double t = (double) s / sampleRate;
                    double sum = 0;
                    for (int midi : n.midiNotes) {
                        sum += voiceSample(n.program, midi, t);
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

    private static double voiceSample(int program, int midi, double t) {
        double freq = midiToFreq(midi);
        double omega = 2.0 * Math.PI * freq;
        // 簡易波形マップ
        int p = program;
        if (p >= 0 && p <= 7) {
            // Acoustic piano 系: 軽い減衰付きサイン
            double env = Math.exp(-t * 6.0);
            return env * Math.sin(omega * t);
        } else if (p >= 16 && p <= 23) {
            // オルガン: 矩形に近い
            return square(omega * t) * 0.6;
        } else if (p >= 24 && p <= 31) {
            // ギター: 三角波
            double env = Math.exp(-t * 4.0);
            return env * triangle(omega * t);
        } else if (p >= 40 && p <= 47) {
            // ストリングス: のこぎりに軽い減衰
            double env = Math.exp(-t * 3.0);
            return env * saw(omega * t) * 0.7;
        } else {
            // デフォルト: サイン
            return Math.sin(omega * t);
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
