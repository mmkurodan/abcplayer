package com.example.abcplayer;

public class SineWaveSynth {

    /**
     * NoteEvent[] → PCM(short[]) を生成する。
     *
     * @param notes       ミックス済み NoteEvent[]
     * @param sampleRate  サンプリングレート（44100）
     * @param tempoBpm    Q: のテンポ
     * @param defaultLen  L: のデフォルト長さ
     */
    public static short[] generatePcm(
            NoteEvent[] notes,
            int sampleRate,
            double tempoBpm,
            double defaultLen
    ) {
        // 1拍の秒数
        double secPerBeat = 60.0 / tempoBpm;

        // 全体のサンプル数を計算
        int totalSamples = 0;
        for (NoteEvent n : notes) {
            double seconds = n.beats * secPerBeat;
            totalSamples += (int) (seconds * sampleRate);
        }

        short[] pcm = new short[totalSamples];

        int writePos = 0;

        for (NoteEvent n : notes) {

            double seconds = n.beats * secPerBeat;
            int samples = (int) (seconds * sampleRate);

            if (n.isRest) {
                // 休符 → 無音
                for (int i = 0; i < samples; i++) {
                    if (writePos + i < pcm.length) {
                        pcm[writePos + i] = 0;
                    }
                }
                writePos += samples;
                continue;
            }

            // 和音（複数周波数）を合成
            double[][] waves = new double[n.midiNotes.length][samples];

            for (int v = 0; v < n.midiNotes.length; v++) {
                int midi = n.midiNotes[v];
                double freq = midiToFreq(midi);

                for (int i = 0; i < samples; i++) {
                    double t = (double) i / sampleRate;
                    waves[v][i] = Math.sin(2.0 * Math.PI * freq * t);
                }
            }

            // 和音をミックス
            for (int i = 0; i < samples; i++) {
                double sum = 0;
                for (int v = 0; v < waves.length; v++) {
                    sum += waves[v][i];
                }
                sum /= waves.length; // 正規化

                short s = (short) (sum * 32767);

                if (writePos + i < pcm.length) {
                    pcm[writePos + i] = s;
                }
            }

            writePos += samples;
        }

        return pcm;
    }

    /**
     * MIDI ノート番号 → 周波数（Hz）
     */
    private static double midiToFreq(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }
}
