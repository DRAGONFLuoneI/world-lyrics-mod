package dev.lyricsoverlay.worldlyrics.bridge;

import java.util.List;

/** Неизменяемый снимок состояния плеера, полученный от Lyrics Overlay. */
public record Snapshot(String title, String artist, String player, double duration, boolean synced, boolean approx,
                       String status, int rev, String key, int offsetMs, List<Line> lines) {

    public record Line(double t, String text) {
    }

    public static final Snapshot EMPTY = new Snapshot("", "", "", 0, false, false, "", -1, "", 0, List.of());

    public boolean hasTrack() {
        return title != null && !title.isEmpty();
    }

    public boolean hasLyrics() {
        return !lines.isEmpty();
    }
}
