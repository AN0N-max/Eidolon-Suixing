package com.eidolon.sc.mobile;

import android.content.*;
import android.speech.SpeechRecognizer;
import java.io.IOException;
import org.json.*;

/** Recognition settings and keys are independent from the chat model and Fish. */
final class CallAsrSettings {
    static String provider(Context c) {
        SharedPreferences p = c.getSharedPreferences("settings", 0);
        return p.getString("callSttProvider", p.getString("callSttUrl", "").isEmpty() ? "system" : "http");
    }
    static void preflight(Context c) throws Exception {
        SharedPreferences p = c.getSharedPreferences("settings", 0);
        String provider = provider(c);
        if (provider.equals("system")) {
            if (!SpeechRecognizer.isRecognitionAvailable(c)) throw new IOException("手机没有可用的系统识别服务，请在电话设置选择百炼或HTTPS识别");
        } else if (provider.equals("bailian")) {
            BailianAsrProtocol.endpoint(p.getString("callBailianRegion", "beijing"));
            BailianAsrProtocol.model(p.getString("callBailianModel", BailianAsrProtocol.DEFAULT_MODEL));
            if (Vault.read(c, "callBailianKey").isEmpty()) throw new IOException("请先保存百炼语音识别密钥");
        } else if (provider.equals("http")) {
            CallHttpAsr.endpoint(p.getString("callSttUrl", ""));
            if (Vault.read(c, "callSttKey").isEmpty()) throw new IOException("请先保存HTTPS识别密钥");
        } else throw new IOException("未知语音识别提供商，请重新保存电话设置");
    }
    static JSONObject config(Context c, JSONObject a) throws Exception {
        SharedPreferences p = c.getSharedPreferences("settings", 0);
        if (a.has("provider") || a.has("url")) {
            if (CallService.live()) throw new IOException("请挂断后修改识别服务");
            String provider = a.optString("provider", a.optString("url").isEmpty() ? "system" : "http");
            if (!provider.equals("system") && !provider.equals("http") && !provider.equals("bailian")) throw new IOException("未知识别提供商");
            SharedPreferences.Editor edit = p.edit();
            String key = a.optString("key").trim();
            if (key.length() > 4096 || key.contains("\r") || key.contains("\n")) throw new IOException("识别密钥格式无效");
            if (provider.equals("bailian")) {
                String region = a.optString("region", "beijing"); BailianAsrProtocol.endpoint(region);
                String model = BailianAsrProtocol.model(a.optString("bailianModel", BailianAsrProtocol.DEFAULT_MODEL));
                if (key.isEmpty() && Vault.read(c, "callBailianKey").isEmpty()) throw new IOException("请填写百炼语音识别密钥");
                if (!key.isEmpty()) Vault.save(c, "callBailianKey", key);
                edit.putString("callBailianRegion", region).putString("callBailianModel", model);
            } else if (provider.equals("http")) {
                String url = a.optString("url").trim(), model = a.optString("model").trim(); CallHttpAsr.endpoint(url);
                if (model.isEmpty() || model.length() > 100 || model.contains("\r") || model.contains("\n")) throw new IOException("请填写正确的识别模型名");
                if (key.isEmpty() && (!url.equals(p.getString("callSttUrl", "")) || Vault.read(c, "callSttKey").isEmpty())) throw new IOException("首次配置或更换HTTPS地址须同时填写对应密钥");
                if (!key.isEmpty()) Vault.save(c, "callSttKey", key);
                edit.putString("callSttUrl", url).putString("callSttModel", model);
            }
            if (!edit.putString("callSttProvider", provider).putBoolean("callIncoming", a.optBoolean("incoming", false)).putBoolean("callAutoListen",a.optBoolean("autoListen",p.getBoolean("callAutoListen",true))).commit()) throw new IOException("电话设置未能保存");
        }
        return new JSONObject().put("provider", provider(c)).put("url", p.getString("callSttUrl", ""))
            .put("model", p.getString("callSttModel", "")).put("hasKey", !Vault.read(c, "callSttKey").isEmpty())
            .put("region", p.getString("callBailianRegion", "beijing"))
            .put("bailianModel", p.getString("callBailianModel", BailianAsrProtocol.DEFAULT_MODEL))
            .put("hasBailianKey", !Vault.read(c, "callBailianKey").isEmpty()).put("incoming", p.getBoolean("callIncoming", false)).put("autoListen",p.getBoolean("callAutoListen",true)).put("maxMinutes",CallLimits.MAX_MINUTES).put("maxTurns",CallLimits.MAX_TURNS);
    }
}
