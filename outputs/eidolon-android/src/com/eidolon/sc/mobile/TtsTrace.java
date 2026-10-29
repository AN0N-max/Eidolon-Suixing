package com.eidolon.sc.mobile;

import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.atomic.AtomicInteger;

/** Never logs keys, text, voice IDs, URLs with credentials, or server error bodies. */
final class TtsTrace {
    private static final AtomicInteger NEXT = new AtomicInteger();
    final int id = NEXT.incrementAndGet();
    final long start = SystemClock.elapsedRealtime();
    volatile boolean cached;
    volatile long first = -1, ready = -1, downloaded = -1;
    long elapsed() { return SystemClock.elapsedRealtime() - start; }
    void event(String stage) { event(stage, 0); }
    void event(String stage, long bytes) {
        long ms = elapsed();
        if (stage.equals("cache_hit")) cached = true;
        if (stage.equals("first_audio_byte")) first = ms;
        if (stage.equals("playback_ready")) ready = ms;
        if (stage.equals("download_complete")) downloaded = ms;
        Log.i("EidolonTTS", "{\"request\":" + id + ",\"stage\":\"" + stage
                + "\",\"elapsed_ms\":" + ms + ",\"bytes\":" + bytes + "}");
    }
    String summary() {
        if (cached) return "缓存命中 / 可播放 " + ready + "ms";
        return "首包 " + first + "ms / 可播放 " + ready + "ms / 收完 " + downloaded + "ms";
    }
}
