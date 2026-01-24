package com.example.abcplayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 標準 ABC Notation の複数ボイス対応のためのトップレベル構造。
 *
 * Score
 header : AbcHeader *  ├
 *  └─ voices : Map<String, List<NoteEvent>>
 *
 * voices は LinkedHashMap を使うことで、
Echo
 */
public class Score {

    public AbcHeader header;

    // key = voice name ("1", "2", "Soprano", "Bass" など)
    public Map<String, List<NoteEvent>> voices = new LinkedHashMap<>();

    public Score() {
        header = new AbcHeader();
    }

    /**
     * 指定したボイス名の NoteEvent リストを返す。
     * 存在しなければ新規作成して返す。
     */
    public List<NoteEvent> getVoice(String voiceName) {
        if (!voices.containsKey(voiceName)) {
            voices.put(voiceName, new ArrayList<>());
        }
        return voices.get(voiceName);
    }

    /**
     * ボイスが1つも宣言されていない場合、
     * デフォルトで "1" を作成する。
     */
    public void ensureDefaultVoice() {
        if (voices.isEmpty()) {
            voices.put("1", new ArrayList<>());
        }
    }
}
