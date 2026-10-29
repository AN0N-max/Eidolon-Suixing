package com.eidolon.sc.mobile;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;

final class VoicePlayer {
    interface Listener { void status(String text); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService audio = Executors.newSingleThreadExecutor();
    private volatile Session active;
    private boolean closed;
    private static final int RATE = FishClient.SAMPLE_RATE;
    private static final int START_BYTES = RATE * 2 / 10; // 100ms of actual PCM, no full-file wait.
    private static final AudioAttributes ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();

    private static final class Session {
        final FishClient client = new FishClient();
        final PcmSpool spool = new PcmSpool();
        final TtsTrace trace = new TtsTrace();
        final Listener listener;
        final PlaybackControl control;
        final Object trackLock;
        final String text;
        boolean started;
        volatile boolean cancelled;
        AudioTrack track;
        AudioFocusRequest focus; // Main thread only.
        Session(Listener listener, String identity, String text) {
            this.listener=listener; this.control=new PlaybackControl(identity);
            this.trackLock=this.control; this.text=text;
        }
    }
    VoicePlayer(Context context) { this.context = context.getApplicationContext(); }
    private boolean current(Session s) { return active == s && !s.cancelled; }
    private void status(Session s, String text) {
        main.post(() -> { if (current(s)) s.listener.status(text); });
    }
    static boolean controlAction(String action) { return "voiceStatus".equals(action)||"voiceToggle".equals(action); }
    JSONObject toggleText(String text) throws Exception {
        Session s=active;
        if(s!=null&&current(s)&&s.text.equals(text))toggleCurrent(s.control.identity);
        return state();
    }
    JSONObject state() throws Exception {
        Session s=active;
        if(s==null||!current(s))return new JSONObject().put("state","idle").put("text","");
        return new JSONObject().put("state",s.control.state()).put("text",s.text);
    }
    private boolean toggleCurrent(String identity) {
        Session s=active;
        if(s==null||!current(s))return false;
        synchronized(s.trackLock) {
            if(!s.control.reusable(identity))return false;
            s.control.toggle();
            try {
                if(s.track!=null&&s.started) {
                    if(s.control.isPaused())s.track.pause(); else s.track.play();
                }
            } catch(IllegalStateException e) {
                stop(); s.listener.status("音频状态已重置，请重新播放"); return true;
            }
        }
        s.trace.event(s.control.isPaused()?"playback_paused":"playback_resumed");
        s.listener.status(s.control.isPaused()?"语音已暂停":"继续播放语音");
        return true;
    }
    void play(String text, String voice, String model, String key, Listener listener) {
        String identity;
        try { identity=VoiceCache.digest(text+"\n"+voice+"\n"+model+"\n"+VoiceCache.digest(key)); }
        catch(Exception e) { listener.status("语音配置无法读取"); return; }
        if(toggleCurrent(identity))return;
        stop();
        if (closed) return;
        Session s = new Session(listener,identity,text);
        active = s;
        s.trace.event("button_received");
        listener.status("正在连接角色语音…");
        network.execute(() -> generate(s, text, voice, model, key));
    }
    void playSaved(File file, Listener listener) {
        String identity="saved:"+file.getAbsolutePath();
        if(toggleCurrent(identity))return;
        stop();
        if (closed) return;
        Session s = new Session(listener,identity,"");
        active = s;
        listener.status("正在读取已保存语音…");
        network.execute(() -> {
            try {
                if (!current(s)) return;
                if (!file.isFile() || file.length()==0 || (file.length()&1)!=0 || file.length()>PcmSpool.MAX_BYTES)
                    throw new IOException("语音已被删除或文件不完整");
                s.trace.event("cache_hit", file.length());
                s.spool.published(file.length());
                s.spool.complete();
                final RandomAccessFile reader = new RandomAccessFile(file, "r");
                try { audio.execute(() -> consume(s, reader)); }
                catch (RuntimeException e) { reader.close(); throw e; }
            } catch (Exception e) {
                s.control.finish();
                status(s,"已保存语音无法播放，请刷新列表后重试");
            }
        });
    }
    private void generate(Session s, String text, String voice, String model, String key) {
        File part = null;
        RandomAccessFile reader = null;
        Future<?> playing = null;
        try {
            if (!current(s)) return;
            try (InputStream in = context.getAssets().open("pronunciation.json");
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
                s.client.pronunciation = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
            JSONObject body = FishClient.request(text, voice, model);
            if (s.client.pronunciation.length() > 0) body.put("pronunciation_dictionary", s.client.pronunciation);
            VoiceCache cache = new VoiceCache(context.getFilesDir());
            cache.migrate(context.getCacheDir());
            cache.trim();
            String cacheKey = VoiceCache.key(model, body.toString(), key);
            File cached = cache.get(cacheKey);
            if (!current(s)) return;
            if (cached != null) {
                s.trace.event("cache_hit", cached.length());
                s.spool.published(cached.length());
                s.spool.complete();
                reader = new RandomAccessFile(cached, "r");
            } else {
                part = cache.temporary();
                reader = new RandomAccessFile(part, "r");
            }
            final RandomAccessFile ownedReader = reader;
            playing = audio.submit(() -> consume(s, ownedReader));
            reader = null; // The audio worker exclusively owns this descriptor now.
            if (cached == null) {
                s.client.stream(body, model, key, part, s.spool, s.trace);
                try { cache.commit(part, cacheKey, body.getString("text"), model); s.trace.event("cache_committed"); }
                catch (IOException e) { s.trace.event("cache_commit_failed"); } // Playback still succeeds.
            }
            playing.get(); // Includes audio tail, then safely remove an uncommitted .part.
        } catch (Exception e) {
            s.spool.fail(new IOException("语音接收失败"));
            if (current(s)) {
                s.trace.event("request_failed");
                String error = e instanceof SocketTimeoutException ? "语音连接超时"
                        : e instanceof UnknownHostException ? "无法连接 Fish Audio，请检查网络"
                        : e instanceof IOException ? e.getMessage()
                        : "语音生成失败，请检查配置后重试";
                cancelSession(s);
                final String safe = error == null ? "语音生成失败" : error;
                main.post(() -> { if (active == s) s.listener.status(safe); });
            }
            if (playing != null) try { playing.get(); } catch (Exception ignored) { }
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
            if (part != null) part.delete();
        }
    }
    private boolean acquireFocus(Session s) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] granted = {false};
        main.post(() -> {
            try {
                if (!current(s)) return;
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                s.focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(ATTRS).setOnAudioFocusChangeListener(change -> {
                            if (change < 0 && current(s)) {
                                s.listener.status("语音已停止，请重新播放");
                                stop();
                            }
                        }, main).build();
                granted[0] = am.requestAudioFocus(s.focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } finally { latch.countDown(); }
        });
        while (!latch.await(100, TimeUnit.MILLISECONDS)) if (!current(s)) return false;
        return granted[0] && current(s);
    }
    private void consume(Session s, RandomAccessFile reader) {
        AudioTrack track = null;
        try (RandomAccessFile in = reader) {
            s.control.awaitResumed();
            if (s.spool.await(0, START_BYTES) == 0 || !current(s)) return;
            s.control.awaitResumed();
            if(!current(s))return;
            if (!acquireFocus(s)) throw new IOException("暂时无法播放，请结束其他通话后重试");
            int minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IOException("设备不支持当前音频格式");
            track = new AudioTrack.Builder().setAudioAttributes(ATTRS)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum, START_BYTES)).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("音频设备初始化失败");
            synchronized (s.trackLock) {
                if (!current(s)) return;
                s.track = track;
            }
            byte[] buffer = new byte[4096];
            long offset = 0, framesWritten = 0;
            boolean started = false, presented = false;
            while (current(s)) {
                s.control.awaitResumed();
                if(!current(s))return;
                long available = s.spool.await(offset, 2);
                if (available == 0) break;
                int n = (int) Math.min(buffer.length, available);
                in.readFully(buffer, 0, n);
                int written = 0;
                while (written < n && current(s)) {
                    s.control.awaitResumed();
                    int count;
                    synchronized (s.trackLock) {
                        if (!current(s)) return;
                        if(s.control.isPaused())continue;
                        count = track.write(buffer, written, n - written, AudioTrack.WRITE_NON_BLOCKING);
                        if (count < 0 || (count & 1) != 0) throw new IOException("音频写入失败");
                        if (!started && count > 0) {
                            s.trace.event("playback_ready");
                            track.play();
                            started = true;
                            s.started = true;
                            s.control.ready();
                            s.trace.event("playback_start");
                            status(s, s.trace.cached?"正在播放已保存语音":"正在播放角色语音");
                        }
                    }
                    written += count;
                    framesWritten += count / 2;
                    if (!presented && (track.getPlaybackHeadPosition() & 0xffffffffL) > 0) {
                        presented = true;
                        s.trace.event("first_audio_presented");
                    }
                    if (count == 0) Thread.sleep(5);
                }
                offset += written;
            }
            if (!current(s)) return;
            long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
            while (current(s) && (track.getPlaybackHeadPosition() & 0xffffffffL) < framesWritten) {
                long beforePause=android.os.SystemClock.elapsedRealtime();
                s.control.awaitResumed();
                deadline+=android.os.SystemClock.elapsedRealtime()-beforePause;
                if(!current(s))return;
                if (android.os.SystemClock.elapsedRealtime() > deadline) throw new IOException("音频播放超时");
                if (!presented && (track.getPlaybackHeadPosition() & 0xffffffffL) > 0) {
                    presented = true; s.trace.event("first_audio_presented");
                }
                Thread.sleep(10);
            }
            if (current(s)) {
                s.control.finish();
                s.trace.event("playback_complete");
                status(s, "播放结束 · " + s.trace.summary());
            }
        } catch (Exception e) {
            if (current(s)) {
                s.trace.event("playback_failed");
                final String message = e instanceof IOException ? e.getMessage() : "音频无法播放，请重试";
                main.post(() -> { if (active == s) s.listener.status(message); });
                cancelSession(s);
            }
        } finally {
            synchronized (s.trackLock) {
                s.track = null;
                if (track != null) track.release();
            }
            releaseFocus(s);
        }
    }
    private void releaseFocus(Session s) {
        main.post(() -> {
            if (s.focus != null) {
                ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocusRequest(s.focus);
                s.focus = null;
            }
        });
    }
    private void cancelSession(Session s) {
        s.cancelled = true;
        s.control.cancel();
        s.spool.cancel();
        synchronized (s.trackLock) {
            if (s.track != null) try { s.track.pause(); s.track.flush(); } catch (IllegalStateException ignored) { }
        }
        s.client.cancel();
        releaseFocus(s);
    }
    void stop() {
        Session s = active;
        active = null;
        if (s != null) { s.trace.event("stopped"); cancelSession(s); }
    }
    void close() {
        closed = true;
        stop();
        network.shutdown();
        audio.shutdown();
    }
}
