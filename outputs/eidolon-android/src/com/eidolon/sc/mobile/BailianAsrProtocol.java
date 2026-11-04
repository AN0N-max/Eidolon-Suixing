package com.eidolon.sc.mobile;

import java.util.UUID;
import org.json.*;

/** DashScope task messages. No credentials or audio in these objects. */
final class BailianAsrProtocol {
    static final String DEFAULT_MODEL = "qwen-audio-3.1-asr-flash-streaming";
    final String taskId = UUID.randomUUID().toString().replace("-", "");
    volatile boolean started, finished, finishSent, serverFinished, failed;
    volatile String requestId="",errorCode="";
    final java.util.TreeMap<Integer,String> finals=new java.util.TreeMap<>(),partials=new java.util.TreeMap<>();
    volatile String text = "", finalText = "", failure = "";

    static String endpoint(String region) {
        if ("beijing".equals(region)) return "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        if ("singapore".equals(region)) return "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference";
        throw new IllegalArgumentException("请选择百炼密钥所属地域");
    }

    static String model(String value) {
        String m = value.trim();
        if (m.isEmpty() || m.length() > 100 || !m.matches("[A-Za-z0-9_.-]+"))
            throw new IllegalArgumentException("请填写百炼实时语音识别模型 ID");
        return m;
    }

    String start(String model) throws JSONException {
        JSONObject parameters = new JSONObject().put("format", "pcm").put("sample_rate", 16000)
            .put("vad_model", "near_meeting_16k").put("language_hints", new JSONArray().put("zh").put("ja"))
            .put("heartbeat", true);
        return new JSONObject().put("header", header("run-task")).put("payload", new JSONObject()
            .put("task_group", "audio").put("task", "asr").put("function", "recognition")
            .put("model", model(model)).put("parameters", parameters).put("input", new JSONObject())).toString();
    }

    String finish() throws JSONException {
        finishSent=true;
        return new JSONObject().put("header", header("finish-task"))
            .put("payload", new JSONObject().put("input", new JSONObject())).toString();
    }

    private JSONObject header(String action) throws JSONException {
        return new JSONObject().put("action", action).put("task_id", taskId).put("streaming", "duplex");
    }

    /** Returns true only for a nonempty final sentence belonging to this task, once. */
    synchronized boolean accept(String raw) throws JSONException {
        if (raw.length() > 128 * 1024) throw new JSONException("识别响应过大");
        JSONObject event = new JSONObject(raw), h = event.optJSONObject("header");
        if (h == null || !taskId.equals(h.optString("task_id")) || finished) return false;
        String kind = h.optString("event");
        if (kind.equals("task-failed")) {
            String code = h.optString("error_code").replaceAll("[^A-Za-z0-9_.-]", "");
            failure = "百炼识别任务失败" + (code.isEmpty() ? "" : "（" + code.substring(0, Math.min(80, code.length())) + "）")
                + "，请检查密钥地域、模型开通与额度；未自动重试";
            finished = true;failed=true;errorCode=code;
        } else if (kind.equals("task-started")) started = true;
        else if (kind.equals("task-finished")) {finished=true;serverFinished=true;}
        else if (kind.equals("result-generated") && started) {
            JSONObject payload = event.optJSONObject("payload"), output = payload == null ? null : payload.optJSONObject("output");
            JSONObject sentence = output == null ? null : output.optJSONObject("sentence");
            if (sentence == null || sentence.optBoolean("heartbeat")) return false;
            String value = sentence.optString("text").trim();
            if (value.length() > 6000) throw new JSONException("识别文本过长");
            if (!hasWords(value)) return false;
            requestId=AsrDiagnostic.safe(payload.optString("request_id",output.optString("request_id",requestId)));
            int id=sentence.optInt("sentence_id",1);if(id<1||id>256)throw new JSONException("识别分段数量无效");
            boolean isFinal=sentence.optBoolean("sentence_end"),changed=isFinal&&!value.equals(finals.get(id));
            if(isFinal){finals.put(id,value);partials.remove(id);}else if(!finals.containsKey(id))partials.put(id,value);
            java.util.TreeMap<Integer,String> all=new java.util.TreeMap<>(finals);all.putAll(partials);text=join(all);finalText=join(finals);
            if(text.length()>6000)throw new JSONException("整轮识别文本过长");return changed;
        }
        return false;
    }

    static String join(java.util.TreeMap<Integer,String> map){StringBuilder b=new StringBuilder();for(String value:map.values()){if(b.length()>0)b.append(' ');b.append(value);}return b.toString();}
    static boolean hasWords(String text) { return text.codePoints().anyMatch(Character::isLetterOrDigit); }
}
