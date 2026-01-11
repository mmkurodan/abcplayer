package com.example.abcplayer;

public class SineWaveSynth {

    /**
     * NoteEvent[] → PCM(short[]) を生成する。
     *
     * 重要：
     *   - 音符ごとに t=0 から開始する旧方式を廃止
     *   - 曲全体で 1 本の時間軸を使う「連続時間方式」
     *   - 複数ボイスの和音が正しく同時発声される
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

        // 曲全体の時間軸（サンプル位置）
        int globalSampleIndex = 0;

        for (NoteEvent n : notes) {

            double seconds = n.beats * secPerBeat;
            int samples = (int) (seconds * sampleRate);

            if (n.isRest) {
                // 休符 → 無音
                for (int i = 0; i < samples; i++) {
                    if (globalSampleIndex + i < pcm.length) {
                        pcm[globalSampleIndex + i] = 0;
                    }
                }
                globalSampleIndex += samples;
                continue;
            }

            // 和音（複数周波数）を合成
            for (int i = 0; i < samples; i++) {

                double sum = 0;

                // 曲全体の時間 t（秒）
                double t = (double) (globalSampleIndex + i) / sampleRate;

                for (int midi : n.midiNotes) {
                    double freq = midiToFreq(midi);
                    sum += Math.sin(2.0 * Math.PI * freq * t);
                }

                // 正規化
                sum /= n.midiNotes.length;

                short s = (short) (sum * 32767);

                if (globalSampleIndex + i < pcm.length) {
                    pcm[globalSampleIndex + i] = s;
                }
            }

            globalSampleIndex += samples;
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
