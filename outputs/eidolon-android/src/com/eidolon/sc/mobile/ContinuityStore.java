package com.eidolon.sc.mobile;
import android.content.Context;import java.time.*;import java.util.*;import org.json.*;

/** Bridges the shared projection to the existing document and delivery transaction. */
final class ContinuityStore {
 static JSONObject frame(Context c,ChatStore s,JSONObject personal,JSONObject persona,JSONObject temporal,List<ChatStore.Message> history,String user,boolean proactive,long baseEpoch)throws Exception{
  boolean enabled=c.getSharedPreferences("settings",0).getBoolean("conversationContinuity",true);
  JSONObject state=LifeStore.read(s,persona.getString("id"));if(state==null)state=LifeData.fresh(persona.getString("id"),persona,Clock.fixed(Instant.ofEpochMilli(temporal.getLong("timestamp")),ZoneOffset.UTC));
  JSONObject scoped=personal;if(!c.getSharedPreferences("settings",0).getBoolean("affinity",true)){scoped=new JSONObject(personal.toString());scoped.put("affinity",new JSONObject()).put("relations",new JSONObject());}return ContinuityContext.project(state,scoped,persona,temporal,history,user,proactive,baseEpoch,enabled);
 }
 static void finish(String raw,String answer,JSONObject frame,List<ChatStore.Message> history,JSONObject lifeEffects)throws Exception{
  String error=ContinuityContext.guardAnswer(answer,frame);if(!error.isEmpty())throw new java.io.IOException(error+"；未自动重新生成");
  if(frame.optBoolean("enabled"))lifeEffects.put("continuity",ContinuityLife.stage(raw,answer,frame,history));
 }
 static String deliveryGuard(Context c,ChatStore s,ChatStore.PendingReply owner,JSONObject effects,long now)throws Exception{ChatStore.PendingReply fresh=s.pending(owner.id);if(fresh==null||fresh.revision!=owner.revision||!fresh.status.equals("waiting"))return "原回复已取消或版本已经改变";return guard(c,s,owner.character,effects,now);}
 static String guard(Context c,ChatStore s,String who,JSONObject effect,long now)throws Exception{
  JSONObject continuity=effect==null?null:effect.optJSONObject("continuity");if(continuity==null)return "";JSONObject frame=continuity.optJSONObject("frame");if(frame==null||!frame.optBoolean("enabled"))return "";
  if(!c.getSharedPreferences("settings",0).getBoolean("conversationContinuity",true))return "连续性设置已关闭，旧候选不会生效";
  JSONObject personal=s.personal(),state=LifeStore.advance(c,s,personal,who,Clock.fixed(Instant.ofEpochMilli(now),ZoneOffset.UTC));String error=ContinuityContext.revalidate(frame,state,personal);if(!error.isEmpty())return error;
  JSONArray ops=continuity.optJSONArray("operations");if(ops!=null)for(int i=0;i<ops.length();i++){JSONObject op=ops.getJSONObject(i);String kind=op.optString("kind");if(kind.equals("habit"))continue;
   boolean goal=Arrays.asList("suggestion","adjust","pause","resume","cancel").contains(kind);JSONObject actual=LifeData.find(state.optJSONArray(goal?"goals":"events"),op.optString(kind.equals("suggestion")?"goalId":"id"));long expected=op.optLong(kind.equals("suggestion")?"goalVersion":"version");
   if(actual==null||ContinuityLife.version(actual)!=expected||actual.optBoolean("sourceMissing"))return "连续性候选的目标或来源已经变化";
  }return "";
 }
 static JSONObject writingFrame(Context c,ChatStore s,JSONObject data,JSONObject persona,String day,List<ChatStore.Message> history,long now)throws Exception{
  ZoneId zone=TemporalRuntime.zone(c,data,persona.getString("id"));long cutoff=Math.min(now,LocalDate.parse(day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()-1);
  LifeStore.advance(c,s,data,persona.getString("id"),Clock.fixed(Instant.ofEpochMilli(now),ZoneOffset.UTC));
  JSONObject temporal=new JSONObject().put("timestamp",cutoff).put("timezone",zone.getId());JSONObject frame=frame(c,s,data,persona,temporal,history,"",false,data.optLong("memoryEpoch"));frame.put("recordDate",day);return frame;
 }
 static void restore(JSONObject state,Map<String,Long> ids)throws Exception{
  JSONArray all=state.getJSONArray("events");for(int i=0;i<all.length();i++){JSONObject e=all.getJSONObject(i);if(!e.optString("kind").equals("exchange"))continue;boolean missing=false;
   for(String key:new String[]{"sourceMessageId","problemSourceId","replyMessageId","resultMessageId","sharedMessageId"})if(e.optLong(key)>0){Long mapped=ids.get(""+e.optLong(key));e.put(key,mapped==null?0:mapped);if(mapped==null)missing=true;}if(missing)e.put("sourceMissing",true);
  }
  JSONArray archives=state.optJSONArray("archives");if(archives!=null)for(int i=0;i<archives.length();i++){JSONObject a=archives.getJSONObject(i);JSONArray shared=a.optJSONArray("exchanges");if(shared!=null)for(int r=0;r<shared.length();r++){JSONObject e=shared.getJSONObject(r);for(String k:new String[]{"sourceMessageId","problemSourceId","replyMessageId","resultMessageId","sharedMessageId"})if(e.optLong(k)>0){Long mapped=ids.get(""+e.optLong(k));e.put(k,mapped==null?0:mapped);if(mapped==null)e.put("sourceMissing",true);}}JSONArray journey=a.optJSONArray("journey");if(journey!=null)for(int r=0;r<journey.length();r++){JSONObject e=journey.getJSONObject(r);if(e.optLong("messageId")>0)e.put("messageId",ids.getOrDefault(""+e.optLong("messageId"),0L));}}
  JSONArray goals=state.getJSONArray("goals");for(int i=0;i<goals.length();i++){JSONObject g=goals.getJSONObject(i);if(g.optLong("sourceMessageId")>0)g.put("sourceMessageId",ids.getOrDefault(""+g.optLong("sourceMessageId"),0L));JSONArray log=g.optJSONArray("journey");if(log!=null)for(int r=0;r<log.length();r++){JSONObject entry=log.getJSONObject(r);if(entry.optLong("messageId")>0)entry.put("messageId",ids.getOrDefault(""+entry.optLong("messageId"),0L));}}
  state.remove("continuityDeliveredThrough");
 }
 private ContinuityStore(){}
}
