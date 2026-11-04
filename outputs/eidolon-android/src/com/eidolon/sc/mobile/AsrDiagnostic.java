package com.eidolon.sc.mobile;
import org.json.*;
/** Allowlisted metadata only: no provider prose, keys, audio or transcribed words. */
final class AsrDiagnostic {
 final JSONObject data=new JSONObject();
 AsrDiagnostic(String provider,String model,String region,String task){try{data.put("provider",safe(provider)).put("model",safe(model)).put("region",safe(region)).put("task_id",safe(task));}catch(Exception ignored){}}
 static String safe(String s){return s==null?"":s.replaceAll("[^A-Za-z0-9_.:/-]","").substring(0,Math.min(120,s.replaceAll("[^A-Za-z0-9_.:/-]","").length()));}
 synchronized JSONObject stage(String phase,String code,String request){try{data.put("stage",safe(phase)).put("error_code",safe(code)).put("request_id",safe(request)).put("at",System.currentTimeMillis());return new JSONObject(data.toString());}catch(Exception e){return new JSONObject();}}
}
