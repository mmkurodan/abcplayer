package com.example.abcplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbcHeader {

    // 拍子 M: 例 "4/4"
    public int meterNum = 4;
    public int meterDen = 4;

    // デフォルト音符長 L: 例 "1/8"
    public double defaultNoteLength = 0.125;

    // テンポ Q: 例 "1/4=120"。内部では四分音符 = 1 beat の BPM に正規化して保持する
    public double tempoBpm = 120.0;

    // キー K: 例 "C", "G", "D", "F", "Am" など
    public String key = "C";

    // 作曲者 C:, 部分タイトル P:, フリーテキスト N:, 楽曲番号 X:, 楽譜行 W:, ユーザ定義 U:
    public String composer = "";
    public String part = "";
    public String notes = "";
    public String number = "";
    public List<String> words = new ArrayList<>();
    public Map<String, String> userDefs = new HashMap<>();

    // Voiceごとのプログラム (General MIDI program number)。未指定は 0 (Acoustic Grand)
    public Map<String, Integer> programByVoice = new HashMap<>();

    public AbcHeader() {
    }

    public void setMeter(String m) {
        if (m.contains("/")) {
            String[] parts = m.split("/");
            meterNum = Integer.parseInt(parts[0]);
            meterDen = Integer.parseInt(parts[1]);
        }
    }

    public void setDefaultLength(String l) {
        if (l.contains("/")) {
            String[] parts = l.split("/");
            defaultNoteLength = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        }
    }

    public void setTempo(String q) {
        tempoBpm = AbcTempoNotation.normalizeTempoBpm(q, tempoBpm);
    }

    public void setKey(String k) {
        key = k.trim();
    }

    public void setNumber(String x) { number = x.trim(); }
    public void setComposer(String c) { composer = c.trim(); }
    public void setPart(String p) { part = p.trim(); }
    public void addWordLine(String w) { words.add(w); }
    public void addNote(String n) { notes = notes.isEmpty() ? n : (notes + "\n" + n); }
    public void setUserDef(String key, String val) { userDefs.put(key, val); }

    public void setProgram(String voice, int program) {
        programByVoice.put(voice, program);
    }

    public int getProgram(String voice) {
        return programByVoice.getOrDefault(voice, 0);
    }
}
