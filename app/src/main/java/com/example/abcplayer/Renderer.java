package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Score（複数ボイス構造）を単一の NoteEvent[] に変換するレンダラ。
 *
 * 音符境界を正しく統合するために「誤差許容付き double 比較」を導入。
 * これにより、V1 と V2 の音符長が異なる場合でも完全に同期する。
 */
public class Renderer {

    private static final double EPS = 1e-9;

    /**
     * 誤差許容付きの contains
     */
    private static boolean containsApprox(List<Double> list, double value) {
        for (double v : list) {
            if (Math.abs(v - value) < EPS) return true;
        }
        return false;
    }

    /**
     * Score → NoteEvent[]（ミックス済み）に変換する。
     */
    public static NoteEvent[] renderToEvents(Score score) {

        Map<String, List<NoteEvent>> voices = score.voices;

        if (voices.isEmpty()) {
            return new NoteEvent[0];
        }

        // ----------------------------------------
        // 1. 各ボイスの「音符境界（累積拍位置）」を計算
        // ----------------------------------------
        List<List<Span>> voiceSpans = new ArrayList<>();

        for (List<NoteEvent> list : voices.values()) {
            List<Span> spans = new ArrayList<>();
            double pos = 0;

            for (NoteEvent e : list) {
                spans.add(new Span(pos, pos + e.beats, e));
                pos += e.beats;
            }

            voiceSpans.add(spans);
        }

        // ----------------------------------------
        // 2. 全ボイスの境界を統合して「全体の区間」を作る
        // ----------------------------------------
        List<Double> boundaries = new ArrayList<>();

        for (List<Span> spans : voiceSpans) {
            for (Span s : spans) {
                if (!containsApprox(boundaries, s.start)) boundaries.add(s.start);
                if (!containsApprox(boundaries, s.end)) boundaries.add(s.end);
            }
        }

        boundaries.sort(Double::compare);

        // ----------------------------------------
        // 3. 各区間ごとに「その区間で鳴っている音」を集めて和音化
        // ----------------------------------------
        List<NoteEvent> mixed = new ArrayList<>();

        for (int i = 0; i < boundaries.size() - 1; i++) {
            double start = boundaries.get(i);
            double end = boundaries.get(i + 1);
            double duration = end - start;

            List<Integer> activeNotes = new ArrayList<>();

            // 各ボイスの該当区間の音を探す
            for (List<Span> spans : voiceSpans) {
                for (Span s : spans) {
                    if (start + EPS >= s.start && start < s.end - EPS) {
                        if (!s.event.isRest) {
                            for (int m : s.event.midiNotes) {
                                if (m >= 0) activeNotes.add(m);
                            }
                        }
                        break;
                    }
                }
            }

            if (activeNotes.isEmpty()) {
                mixed.add(new NoteEvent("mix", new int[]{-1}, duration, true));
            } else {
                int[] arr = activeNotes.stream().mapToInt(x -> x).toArray();
                mixed.add(new NoteEvent("mix", arr, duration, false));
            }
        }

        return mixed.toArray(new NoteEvent[0]);
    }

    /**
     * ボイス内の音符を「開始拍」「終了拍」で表す構造
     */
    private static class Span {
        double start;
        double end;
        NoteEvent event;

        Span(double s, double e, NoteEvent ev) {
            start = s;
            end = e;
            event = ev;
        }
    }
}
