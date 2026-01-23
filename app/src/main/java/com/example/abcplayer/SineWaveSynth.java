package com.example.abcplayer;

public class SineWaveSynth {

    /**
     * NoteEvent[] → PCM(short[]) を生成する。
     *
     * 修正点:
     *  - 曲全体で 1 本の時間軸を使う「連続時間方式」
     *  - PCM 書き込みを「上書き」ではなく「加算ミックス」に変更
     *  - 長い音符が短い音符に上書きされる問題を解消
     *  - 複数ボイスが正しく同時発声される
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
                globalSampleIndex += samples;
                continue;
            }

            // 和音（複数周波数）を合成
            for (int i = 0; i < samples; i++) {

                double t = (double) (globalSampleIndex + i) / sampleRate;

                double sum = 0;
                for (int midi : n.midiNotes) {
                    double freq = midiToFreq(midi);
                    sum += Math.sin(2.0 * Math.PI * freq * t);
                }

                // 正規化
                sum /= n.midiNotes.length;

                short s = (short) (sum * 32767);

                int pos = globalSampleIndex + i;
                if (pos < pcm.length) {

                    // ★ 加算ミックス（上書きではない）
                    int mixed = pcm[pos] + s;

                    // クリッピング
                    if (mixed > 32767) mixed = 32767;
                    if (mixed < -32768) mixed = -32768;

                    pcm[pos] = (short) mixed;
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

    /**
     * 指定サンプル範囲のみを生成する（ストリーミング向け）
     *
     * startSample: 全体の先頭からのオフセット（サンプル数）
     * chunkSamples: 返すサンプル数
     */
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
                    int i = s - startSample; // index in chunk
                    double t = (double) s / sampleRate;

                    double sum = 0;
                    for (int midi : n.midiNotes) {
                        double freq = midiToFreq(midi);
                        sum += Math.sin(2.0 * Math.PI * freq * t);
                    }

                    // 正規化
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
}
