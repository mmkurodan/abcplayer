package com.example.abcplayer.audio;

public enum PolyphonyMode {
    AUTOMATIC("自動検出"),
    FIXED("固定数");

    public final String displayName;

    PolyphonyMode(String displayName) {
        this.displayName = displayName;
    }
}
