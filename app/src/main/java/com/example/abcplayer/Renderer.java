package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Score（複数ボイス構造）を単一の NoteEvent[] に変換するレンダラ。
 *
 * 役割:
 *  - 各ボイスの NoteEvent を時間軸に沿って展開
 *  - 全ボイスを合成（ミックス）
 *  - 最終的に 1 本の NoteEvent[] として返す
 *
 * ※ Synth は既存の和音合成ロジックを使うため、
 *   複数ボイスは「巨大な和音」として自然に合成される。
 */
public class Renderer {

    /**
     * Score → NoteEvent[]（ミックス済み）に変換する。
     */
    public static NoteEvent[] renderToEvents(Score score) {

        // ----------------------------------------
        // 1. 各ボイスの NoteEvent[] を取得
        // ----------------------------------------
        Map<String, List<NoteEvent>> voices = score.voices;

        // ボイスが 1 つもない場合は空
        if (voices.isEmpty()) {
            return new NoteEvent[0];
        }

        // ----------------------------------------
        // 2. 各ボイスの総拍数を計算し、最大値を求める
        // ----------------------------------------
        double maxBeats = 0;

        for (List<NoteEvent> list : voices.values()) {
            double sum = 0;
            for (NoteEvent e : list) {
                sum += e.beats;
            }
            if (sum > maxBeats) {
                maxBeats = sum;
            }
        }

        // ----------------------------------------
        // 3. 全ボイスを「拍単位」でミックスする
        //
        //    例:
        //      Voice1: C2 D2 E2 F2   → 8拍
        //      Voice2: G4 A4         → 8拍
        //
        //    → 拍ごとに和音として合成
        // ----------------------------------------

        List<NoteEvent> mixed = new ArrayList<>();

        // 各ボイスの進行位置（拍単位）
        double[] pos = new double[voices.size()];
        int voiceIndex = 0;

        // ボイスごとの NoteEvent リストを配列化
        List<NoteEvent>[] voiceEvents = new List[voices.size()];
        int idx = 0;
        for (List<NoteEvent> list : voices.values()) {
            voiceEvents[idx++] = list;
        }

        // 拍単位で走査
        double beat = 0;
        while (beat < maxBeats) {

            List<Integer> activeNotes = new ArrayList<>();

            // 各ボイスの現在の音を取得
            for (int v = 0; v < voiceEvents.length; v++) {
                List<NoteEvent> list = voiceEvents[v];

                double acc = 0;
                for (NoteEvent e : list) {
                    if (beat >= acc && beat < acc + e.beats) {
                        if (!e.isRest) {
                            for (int m : e.midiNotes) {
                                if (m >= 0) activeNotes.add(m);
                            }
                        }
                        break;
                    }
                    acc += e.beats;
                }
            }

            // この拍の長さは 1 拍
            double duration = 1.0;

            if (activeNotes.isEmpty()) {
                mixed.add(new NoteEvent("mix", new int[]{-1}, duration, true));
            } else {
                int[] arr = activeNotes.stream().mapToInt(x -> x).toArray();
                mixed.add(new NoteEvent("mix", arr, duration, false));
            }

            beat += 1.0;
        }

        return mixed.toArray(new NoteEvent[0]);
    }
}
