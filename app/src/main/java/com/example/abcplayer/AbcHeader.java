package com.example.abcplayer;

public class AbcHeader {

    // 拍子 M: 例 "4/4"
    public int meterNum = 4;
    public int meterDen = 4;

    // デフォルト音符長 L: 例 "1/8"
    public double defaultNoteLength = 0.125;

    // テンポ Q: 例 "1/4=120"
    public double tempoBpm = 120.0;

    // キー K: 例 "C", "G", "D", "F", "Am" など
    public String key = "C";

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
        // 例: "1/4=120"
        if (q.contains("=")) {
            String[] parts = q.split("=");
            tempoBpm = Double.parseDouble(parts[1]);
        }
    }

    public void setKey(String k) {
        key = k.trim();
    }
}
