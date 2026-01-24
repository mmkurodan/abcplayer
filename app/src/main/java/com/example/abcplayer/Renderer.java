package com.example.abcplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Renderer {

    private static final double EPS = 1e-9;

    private static boolean containsApprox(List<Double> list, double value) {
        for (double v : list) {
            if (Math.abs(v - value) < EPS) return true;
        }
        return false;
    }

    public static NoteEvent[] renderToEvents(Score score) {

        Map<String, List<NoteEvent>> voices = score.voices;

        if (voices.isEmpty()) {
            return new NoteEvent[0];
        }

        // 1. 各ボイスの「音符境界（累積拍位置）」を計算
        List<List<Span>> voiceSpans = new ArrayList<>();

        for (Map.Entry<String, List<NoteEvent>> entry : voices.entrySet()) {
            String voiceName = entry.getKey();
            List<NoteEvent> list = entry.getValue();
            int program = score.header.getProgram(voiceName);

            List<Span> spans = new ArrayList<>();
            double pos = 0;

            for (NoteEvent e : list) {
                // program をボイス設定で上書き
                e.program = program;
                spans.add(new Span(pos, pos + e.beats, e));
                pos += e.beats;
            }

            voiceSpans.add(spans);
        }

        // 2. 全ボイスの境界を統合
        List<Double> boundaries = new ArrayList<>();
        for (List<Span> spans : voiceSpans) {
            for (Span s : spans) {
                if (!containsApprox(boundaries, s.start)) boundaries.add(s.start);
                if (!containsApprox(boundaries, s.end)) boundaries.add(s.end);
            }
        }
        boundaries.sort(Double::compare);

        // 3. 各区間で鳴っている音を集めて和音化
        List<NoteEvent> mixed = new ArrayList<>();

        for (int i = 0; i < boundaries.size() - 1; i++) {
            double start = boundaries.get(i);
            double end   = boundaries.get(i + 1);
            double duration = end - start;

            List<Integer> activeNotes = new ArrayList<>();
            List<Integer> activePrograms = new ArrayList<>();

            for (List<Span> spans : voiceSpans) {
                for (Span s : spans) {
                    if (start >= s.start - EPS && start < s.end - EPS) {
                        if (!s.event.isRest) {
                            for (int m : s.event.midiNotes) {
                                if (m >= 0) activeNotes.add(m);
                            }
                            // program を覚えて代表値として使う（単純に最初のものを採用）
                            if (!activePrograms.contains(s.event.program)) activePrograms.add(s.event.program);
                        }
                        break;
                    }
                }
            }

            if (activeNotes.isEmpty()) {
                mixed.add(new NoteEvent("mix", new int[]{-1}, duration, true));
            } else {
                int[] arr = activeNotes.stream().mapToInt(x -> x).toArray();
                NoteEvent ev = new NoteEvent("mix", arr, duration, false);
                ev.program = activePrograms.isEmpty() ? 0 : activePrograms.get(0);
                mixed.add(ev);
            }
        }

        return mixed.toArray(new NoteEvent[0]);
    }

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
