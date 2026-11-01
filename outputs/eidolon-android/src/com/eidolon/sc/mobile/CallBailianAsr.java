package com.eidolon.sc.mobile;

import android.media.*;
import android.os.SystemClock;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.*;
import okhttp3.*;
import okio.ByteString;

/** One bounded, live PCM recognition task per listening turn. No automatic retries or recording files. */
final class CallBailianAsr {
    interface Listener {
        void ready(); void partial(String text); void ended(long first, long last);
        void result(String text); void error(String safeMessage);
    }
    interface Clock { long now(); }
    interface Audio { void start() throws Exception; int read(byte[] target) throws Exception; void stop(); void close(); }
    interface AudioFactory { Audio create() throws Exception; }
    interface Socket { boolean text(String s); boolean audio(byte[] b); long queued(); void cancel(); }
    interface Events { void open(); void text(String s); void failure(int status); }
    interface Transport { Socket connect(String endpoint, String key, Events events); }

    private final Clock clock;
    private final AudioFactory source;
    private final Transport transport;
    private final long timeout;
    private volatile boolean cancelled;
    private volatile Audio audio;
    private volatile Socket socket;
    private final CountDownLatch opened = new CountDownLatch(1), started = new CountDownLatch(1), done = new CountDownLatch(1);
    private volatile String failure = "";
    private final BailianAsrProtocol protocol = new BailianAsrProtocol();

    CallBailianAsr() { this(SystemClock::elapsedRealtime, NativeAudio::new, new NativeTransport(), 10000); }
    CallBailianAsr(Clock clock, AudioFactory source, Transport transport, long timeout) {
        this.clock = clock; this.source = source; this.transport = transport; this.timeout = timeout;
    }

    void cancel() {
        cancelled = true; opened.countDown(); started.countDown(); done.countDown();
        Socket s = socket; if (s != null) s.cancel();
        Audio a = audio; if (a != null) a.stop();
    }

    private void fail(String message) {
        failure = message; opened.countDown(); started.countDown(); done.countDown();
        Audio a = audio; if (a != null) a.stop();
    }

    void run(String region, String model, String key, Listener listener) {
        long first = 0, last = 0;
        try {
            if (cancelled) return;
            String endpoint = BailianAsrProtocol.endpoint(region); BailianAsrProtocol.model(model);
            if (key.trim().isEmpty()) throw new IOException("请填写百炼语音识别密钥");
            socket = transport.connect(endpoint, key, new Events() {
                public void open() { opened.countDown(); }
                public void failure(int status) {
                    if (cancelled || !protocol.finalText.isEmpty()) return;
                    fail(status == 401 || status == 403 ? "百炼拒绝了密钥，请检查地域与权限；未自动切换地域或重试"
                        : status == 429 ? "百炼请求受限或额度不足；未自动重试"
                        : "百炼识别连接中断，请检查网络后手动重试");
                }
                public void text(String raw) {
                    if (cancelled) return;
                    try {
                        boolean complete = protocol.accept(raw);
                        if (!protocol.failure.isEmpty()) { fail(protocol.failure); return; }
                        if (protocol.started) started.countDown();
                        if (complete) { done.countDown(); Audio a = audio; if (a != null) a.stop(); }
                        else if (protocol.finished) { if (protocol.finalText.isEmpty()) fail("百炼识别已结束，但没有有效讲话，请重新聆听"); }
                        else if (!protocol.text.isEmpty()) listener.partial(protocol.text);
                    } catch (Exception e) { fail("百炼识别响应格式异常，本轮未提交"); }
                }
            });
            if (cancelled) return;
            await(opened, "连接百炼超时，请检查网络");
            if (cancelled) return;
            if (!socket.text(protocol.start(model))) throw new IOException("百炼识别连接不可用");
            await(started, "百炼识别任务启动超时，请检查模型开通与地域");
            if (cancelled) return;
            audio = source.create();
            if (cancelled) return;
            audio.start();
            if (cancelled) return;
            if (!failure.isEmpty()) throw new IOException(failure);
            listener.ready();
            byte[] buffer = new byte[3200]; long begin = clock.now();
            while (!cancelled && done.getCount() != 0 && clock.now() - begin < 30000) {
                int n = audio.read(buffer); long now = clock.now();
                if (cancelled || done.getCount() == 0) break;
                if (n <= 0 || n > buffer.length || (n & 1) != 0) throw new IOException("麦克风采集失败，请检查音频设备");
                double sum = 0; for (int i = 0; i < n; i += 2) { int sample = (short)((buffer[i] & 255) | (buffer[i+1] << 8)); sum += (double)sample * sample; }
                if (Math.sqrt(sum / (n / 2)) > 700) { if (first == 0) first = now - n / 32; last = now; }
                if (socket.queued() > 64000 || !socket.audio(Arrays.copyOf(buffer, n)))
                    throw new IOException("网络上传跟不上采音，本轮停止，请手动重试");
                if (first == 0 && protocol.text.isEmpty() && now - begin > 12000)
                    throw new IOException("一段时间没有识别到讲话，请重新聆听");
            }
            if (cancelled) return;
            audio.stop();
            if (!failure.isEmpty()) throw new IOException(failure);
            long stopped = clock.now();
            listener.ended(first > 0 ? first : begin, last > 0 ? last : stopped);
            if (!socket.text(protocol.finish()) && protocol.finalText.isEmpty()) throw new IOException("百炼识别连接已断开");
            if (protocol.finalText.isEmpty()) await(done, "百炼没有返回句尾结果，本轮未提交；可手动重试");
            if (cancelled) return;
            if (!failure.isEmpty()) throw new IOException(failure);
            if (protocol.finalText.isEmpty()) throw new IOException("百炼没有识别到有效讲话，请重新聆听");
            listener.result(protocol.finalText);
        } catch (Exception e) {
            if (!cancelled) listener.error(e instanceof IOException && e.getMessage() != null ? e.getMessage() : "百炼识别未完成，请检查设置后手动重试");
        } finally {
            cancelled = true;
            Audio a = audio; audio = null; if (a != null) { a.stop(); a.close(); }
            Socket s = socket; socket = null; if (s != null) s.cancel();
        }
    }

    private void await(CountDownLatch latch, String message) throws Exception {
        if (!latch.await(timeout, TimeUnit.MILLISECONDS)) throw new IOException(message);
        if (!failure.isEmpty()) throw new IOException(failure);
    }

    private static final class NativeAudio implements Audio {
        private final AudioRecord record;
        NativeAudio() throws IOException {
            int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IOException("手机不支持16k语音采集");
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 6400));
            if (record.getState() != AudioRecord.STATE_INITIALIZED) { record.release(); throw new IOException("麦克风初始化失败"); }
        }
        public void start() throws IOException { record.startRecording(); if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("麦克风未开始采集"); }
        public int read(byte[] b) { return record.read(b, 0, b.length); }
        public void stop() { try { record.stop(); } catch (Exception ignored) {} }
        public void close() { record.release(); }
    }

    private static final class NativeTransport implements Transport {
        // Shared connection resources, but each listening turn owns a separate cancellable socket.
        private static final OkHttpClient CLIENT = new OkHttpClient.Builder().retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false).build();
        public Socket connect(String endpoint, String key, Events events) {
            WebSocket ws = CLIENT.newWebSocket(new Request.Builder().url(endpoint)
                .header("Authorization", "Bearer " + key).build(), new WebSocketListener() {
                    @Override public void onOpen(WebSocket s, Response r) { events.open(); }
                    @Override public void onMessage(WebSocket s, String text) { events.text(text); }
                    @Override public void onFailure(WebSocket s, Throwable t, Response r) { events.failure(r == null ? 0 : r.code()); }
                    @Override public void onClosing(WebSocket s, int code, String reason) { events.failure(0); s.close(1000, null); }
                    @Override public void onClosed(WebSocket s, int code, String reason) { events.failure(0); }
                });
            return new Socket() {
                public boolean text(String s) { return ws.send(s); }
                public boolean audio(byte[] b) { return ws.send(ByteString.of(b)); }
                public long queued() { return ws.queueSize(); }
                public void cancel() { ws.cancel(); }
            };
        }
    }
}
