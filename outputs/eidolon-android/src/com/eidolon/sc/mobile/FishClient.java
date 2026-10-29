package com.eidolon.sc.mobile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.*;
import org.json.*;

final class FishClient {
    static final String[] MODELS = {"s2.1-pro-free", "s2.1-pro", "drama-3", "s2-pro", "s1"};
    static final int SAMPLE_RATE = 44100;
    volatile boolean cancelled;
    volatile HttpURLConnection connection;
    JSONArray pronunciation = new JSONArray();
    void cancel() {
        cancelled = true;
        HttpURLConnection c = connection;
        if (c != null) c.disconnect();
    }
    static String speech(String text) throws Exception {
        text=ReplyText.clean(text);
        Matcher m = Pattern.compile("<ja>(.*?)</ja>", Pattern.DOTALL).matcher(text);
        StringBuilder ja = new StringBuilder();
        while (m.find()) ja.append(m.group(1)).append('\n');
        String s = (ja.length() > 0 ? ja.toString() : text.replaceAll("(?s)<zh>.*?</zh>", ""))
                .replaceAll("<[^>]*>", "").replaceAll("\\[[^\\]]*\\]", "").trim();
        if (s.isEmpty()) throw new Exception("这条消息没有可播放的台词");
        if (s.length() > 5000) throw new Exception("单条语音暂限 5000 字，请选择较短的回复");
        return s;
    }
    static JSONObject request(String text, String voice, String model) throws Exception {
        if (!Arrays.asList(MODELS).contains(model)) throw new Exception("不支持的 Fish Audio 模型");
        if (!voice.matches("[a-fA-F0-9]{32}")) throw new Exception("角色音色 ID 不正确");
        return new JSONObject().put("text", speech(text)).put("reference_id", voice)
                .put("format", "pcm").put("sample_rate", SAMPLE_RATE)
                .put("latency", "balanced").put("chunk_length", 150);
    }
    void stream(JSONObject body, String model, String key, File target,
                PcmSpool spool, TtsTrace trace) throws Exception {
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        if (cancelled) throw new IOException("已停止");
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.fish.audio/v1/tts").openConnection();
        connection = c;
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "audio/pcm, application/octet-stream");
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("model", model);
            c.setFixedLengthStreamingMode(payload.length);
            trace.event("connection_start");
            c.connect(); // DNS + TCP + TLS, or pooled connection. Not separate DNS/TLS metrics.
            trace.event("connection_established");
            if (cancelled) throw new IOException("已停止");
            try (OutputStream out = c.getOutputStream()) { out.write(payload); out.flush(); }
            trace.event("request_sent", payload.length); // Local write complete, NOT server acknowledgement.
            int code = c.getResponseCode();
            trace.event("response_headers");
            if (code == 401 || code == 403) throw new IOException("Fish Audio 密钥无效或无权使用这个音色");
            if (code == 402) throw new IOException("Fish Audio 额度不足，请检查账户与所选模型");
            if (code == 404) throw new IOException("角色音色不存在或已下架，请更换音色 ID");
            if (code == 429) throw new IOException("Fish Audio 请求受限，请稍后重试");
            if (code < 200 || code >= 300) throw new IOException("Fish Audio 请求失败（HTTP " + code + "）");
            String type = c.getContentType();
            if (type != null) {
                type = type.toLowerCase(Locale.ROOT);
                if (!(type.startsWith("audio/pcm") || type.startsWith("audio/raw")
                        || type.startsWith("audio/x-pcm") || type.startsWith("application/octet-stream")))
                    throw new IOException("语音服务未返回预期的原始 PCM 音频");
            }
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (cancelled) throw new IOException("已停止");
                    if (n == 0) continue;
                    if (total == 0) trace.event("first_audio_byte", n);
                    if (total + n > PcmSpool.MAX_BYTES) throw new IOException("音频过大");
                    out.write(buffer, 0, n);
                    total += n;
                    spool.published(total);
                }
                if (cancelled) throw new IOException("已停止");
                if (c.getContentLengthLong() >= 0 && c.getContentLengthLong() != total)
                    throw new IOException("音频下载不完整");
                if (total == 0 || (total & 1) != 0) throw new IOException("音频为空或 PCM 不完整");
                out.getFD().sync();
                trace.event("download_complete", total);
                spool.complete();
            }
        } finally {
            connection = null;
            c.disconnect();
        }
    }
}
