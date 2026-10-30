package com.eidolon.sc.mobile;
import java.time.*;import org.json.*;
/** Pure on-demand projection. Plans never become completed events merely because time passed. */
final class TemporalData {
 static JSONObject build(Clock clock,ZoneId zone,JSONObject plan,JSONObject status,long received,long due,long last,long previous,JSONObject override)throws Exception{
  if(status.optString("state").equals("off"))plan=null;long now=clock.millis();ZonedDateTime at=Instant.ofEpochMilli(now).atZone(zone);JSONObject activity=activity(plan,at),next=next(plan,at);
  String mode=status.optString("state","unknown"),style="normal",availability="unknown",interrupt="unknown";
  if(mode.equals("bath")){style="brief_after_interruption";availability="low";interrupt="low";}
  else if(mode.equals("asleep")||mode.equals("woken")){style="possibly_drowsy";availability="low";interrupt="low";}
  else if(mode.equals("bedtime")){style="gentle_brief";availability="medium";interrupt="medium";}
  else if(activity!=null){String name=activity.optString("name");boolean low=name.matches(".*(上课|课程|工作|排练|演出|录音|会议|训练).*"),meal=name.matches(".*(早餐|午餐|晚餐|午休|休息|吃饭).*" );availability=low?"low":meal?"medium":"high";interrupt=low?"low":"medium";style=low?"brief_attentive":"normal";}
  else if(mode.equals("free")||mode.equals("awake")){availability="high";interrupt="high";}
  JSONObject actual=new JSONObject().put("state","unknown").put("source","no_confirmed_observation");
  if(mode.equals("woken")||mode.equals("awake"))actual.put("state","recent_chat_contact").put("source","persisted_contact_timestamps");
  if(override!=null&&override.optLong("from")<=now&&now<override.optLong("until")&&override.optBoolean("confirmed")&&!override.optString("source").isEmpty()){
   actual=new JSONObject(override.toString());availability=override.optString("availability",availability);interrupt=override.optString("interruptibility",interrupt);style=override.optString("response_style",style);
  }
  boolean rollback=previous>0&&now<previous;
  JSONObject out=new JSONObject().put("schema",1).put("current_time",at.toOffsetDateTime().toString()).put("timestamp",now).put("timezone",zone.getId()).put("weekday",at.getDayOfWeek().toString()).put("time_period",period(at.getHour())).put("clock_source","device_wall_clock").put("clock_rollback",rollback)
   .put("received_at",received>0?Instant.ofEpochMilli(received).atZone(zone).toOffsetDateTime().toString():JSONObject.NULL).put("received_in_future",received>now)
   .put("last_interaction_minutes_ago",last>0&&last<=now?(now-last)/60000:JSONObject.NULL).put("interaction_time_uncertain",last>now)
   .put("planned_activity",activity==null?JSONObject.NULL:activity).put("next_planned_activity",next==null?JSONObject.NULL:next).put("planned_state",mode).put("actual_state",actual)
   .put("behavior",new JSONObject().put("availability",availability).put("interruptibility",interrupt).put("response_style",style).put("energy",JSONObject.NULL).put("mood",JSONObject.NULL).put("conversation_willingness","not_inferred").put("temporal_relevance","context_dependent"))
   .put("catch_up",previous>0&&now-previous>3600000?"recomputed_current_state_without_replaying_events":"none").put("today_events",new JSONArray()).put("missed_plan_completion","unknown");
  long target=Math.max(now,due);out.put("expected_delivery_at",Instant.ofEpochMilli(target).atZone(zone).toOffsetDateTime().toString()).put("delivery_may_be_delayed",true);
  if(target>now)out.put("planned_activity_at_expected_delivery",Instant.ofEpochMilli(target).atZone(zone).toLocalDate().equals(at.toLocalDate())?activity(plan,Instant.ofEpochMilli(target).atZone(zone)):JSONObject.NULL);
  return out;
 }
 static String period(int h){return h<5?"late_night":h<11?"morning":h<14?"midday":h<18?"afternoon":h<22?"evening":"night";}
 static long boundary(LocalDate day,String value,ZoneId zone)throws Exception{int m=ScheduleData.minute(value,true);return day.plusDays(m/1440).atTime((m%1440)/60,m%60).atZone(zone).toInstant().toEpochMilli();}
 static JSONObject activity(JSONObject plan,ZonedDateTime at)throws Exception{
  if(plan==null)return null;JSONArray slots=plan.optJSONArray("slots");if(slots==null)return null;long now=at.toInstant().toEpochMilli();
  for(int i=0;i<slots.length();i++){JSONObject v=slots.getJSONObject(i);long start=boundary(at.toLocalDate(),v.getString("start"),at.getZone()),end=boundary(at.toLocalDate(),v.getString("end"),at.getZone());
   if(start<=now&&now<end&&end>start)return new JSONObject().put("name",v.getString("activity")).put("start",start).put("end",end).put("status","scheduled_in_progress").put("progress",(now-start)/(double)(end-start)).put("source","stored_plan").put("actual_completion_known",false);
  }return null;
 }
 static JSONObject next(JSONObject plan,ZonedDateTime at)throws Exception{if(plan==null)return null;JSONArray slots=plan.optJSONArray("slots");if(slots==null)return null;for(int i=0;i<slots.length();i++){JSONObject v=slots.getJSONObject(i);long start=boundary(at.toLocalDate(),v.getString("start"),at.getZone());if(start>at.toInstant().toEpochMilli())return new JSONObject().put("name",v.getString("activity")).put("start",start).put("status","planned_future");}return null;}
 static String prompt(JSONObject data){return "\n【应用时间状态：结构化参考，不是角色台词】\n"+data.toString()+"\n当前时间只以本快照为准，档案或其他参考中的时刻可能构建得更早。使用以上时间状态裁定当下与未来，历史消息里的今天/昨天以各自时间戳为准。planned只是计划，actual_state为unknown时不能断言已入睡、已洗完、活动已完成或编造今日经历。用户发消息、生成、预计投放是三个时刻；延迟投放不代表时刻一定准点。行为参数影响措辞与长短，不替代既有关系和安全边界；仅在与话题有关时自然流露时间感，不逐条报时或重复活动。过去未来的记忆不能覆盖当前状态；时间不确定时表达不确定。\n";}
 private TemporalData(){}
}
