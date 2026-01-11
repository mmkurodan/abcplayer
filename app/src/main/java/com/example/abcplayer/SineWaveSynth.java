package com.example.abcplayer;

public class SineWaveSynth {

    /**
     * ABC の NoteEvent 列から PCM を生成する。
     *
     * @param notes           NoteEvent[]
     * @param sampleRate      例: 44100
     * @param tempoBpm        Q: の BPM
     * @param defaultLength   L: のデフォルト音符長（例: 1/8 = 0.125）
     * @return short[] PCM（16bit, mono）
     */
    public static short[] generatePcm(
            NoteEvent[] notes,
            int sampleRate,
            double tempoBpm,
            double defaultLength
    ) {

        // 1拍の秒数
        double secPerBeat = 60.0 / tempoBpm;

        // L: の長さ（例: L:1/8 → 1/8拍）
        // ABC の長さは「拍数」で表現されるので、
        // durationSec = length * secPerBeat
        int totalSamples = 0;
        for (NoteEvent n : notes) {
            double beats = n.length / defaultLength; // L: を基準にした拍数
            double sec = beats * secPerBeat;
            totalSamples += (int) (sec * sampleRate);
        }

        short[] pcm = new short[totalSamples];
        int pos = 0;

        for (NoteEvent n : notes) {

            double beats = n.length / defaultLength;
            double sec = beats * secPerBeat;
            int samples = (int) (sec * sampleRate);

            // 和音対応：複数の MIDI ノートを合成
            int[] chord = n.midiNotes;

            for (int i = 0; i < samples && pos < pcm.length; i++, pos++) {

                double t = (double) i / sampleRate;

                double sum = 0.0;
                int activeNotes = 0;

                for (int midi : chord) {
                    if (midi < 0) {
                        // 休符
                        continue;
                    }
                    double freq = midiToFreq(midi);
                    sum += Math.sin(2.0 * Math.PI * freq * t);
                    activeNotes++;
                }

                double value = 0.0;
                if (activeNotes > 0) {
                    value = sum / activeNotes; // 正規化
                }

                // 振幅を抑える
                short s = (short) (value * 0.8 * Short.MAX_VALUE);
                pcm[pos] = s;
            }
        }

        return pcm;
    }

    /**
     * MIDIノート番号 → 周波数（Hz）
     * A4 (69) = 440Hz
     */
    private static double midiToFreq(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }
}
