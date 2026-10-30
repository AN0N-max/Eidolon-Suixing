package com.eidolon.sc.mobile;
import java.time.*;import java.util.*;import org.json.*;
public class TemporalTests {
 static int count;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
 static JSONObject plan()throws Exception{return new JSONObject("{wake:'07:00',sleep:'23:45',slots:[{start:'08:00',end:'09:40',activity:'上课'},{start:'12:00',end:'13:00',activity:'午休'},{start:'15:00',end:'16:00',activity:'排练'}]}");}
 static JSONObject at(String iso,ZoneId zone,JSONObject override,long previous)throws Exception{Clock clock=Clock.fixed(Instant.parse(iso),ZoneOffset.UTC);JSONObject p=plan();ZonedDateTime z=clock.instant().atZone(zone);JSONObject status=ScheduleData.status(p,p,z,true,0,0);return TemporalData.build(clock,zone,p,status,clock.millis(),clock.millis(),clock.millis()-25*3600000L,previous,override);}
 public static void main(String[] args)throws Exception{
  ZoneId z=ZoneId.of("Asia/Shanghai");JSONObject morning=at("2026-10-30T00:30:00Z",z,null,0),noon=at("2026-10-30T04:30:00Z",z,null,0),bed=at("2026-10-30T15:30:00Z",z,null,0),sleep=at("2026-10-29T18:00:00Z",z,null,0);
  check(morning.getString("weekday").equals("FRIDAY")&&morning.getString("time_period").equals("morning"),"08:30 role clock weekday");
  check(morning.getJSONObject("planned_activity").getString("name").equals("上课")&&morning.getJSONObject("behavior").getString("availability").equals("low"),"class activity changes behavior");
  check(noon.getJSONObject("planned_activity").getString("name").equals("午休")&&!morning.getJSONObject("behavior").toString().equals(noon.getJSONObject("behavior").toString()),"12:30 rest behavior differs");
  check(bed.getJSONObject("behavior").getString("response_style").equals("gentle_brief"),"23:30 preparation before sleep");
  check(sleep.getString("planned_state").equals("asleep")&&sleep.getJSONObject("actual_state").getString("state").equals("unknown"),"02:00 planned sleep is not actual proof");
  check(at("2026-10-30T00:00:00Z",z,null,0).getJSONObject("planned_activity").getDouble("progress")==0,"activity exact start included");
  check(at("2026-10-30T01:40:00Z",z,null,0).isNull("planned_activity"),"activity exact end excluded");
  check(morning.getLong("last_interaction_minutes_ago")==1500,"gap above24h keeps real duration");
  long now=Instant.parse("2026-10-30T00:30:00Z").toEpochMilli();JSONObject override=new JSONObject().put("name","临时休息").put("confirmed",true).put("source","explicit_user_override").put("from",now-1).put("until",now+1).put("availability","high");
  check(at("2026-10-30T00:30:00Z",z,override,0).getJSONObject("actual_state").getString("name").equals("临时休息"),"valid sourced override takes precedence");override.put("until",now);check(at("2026-10-30T00:30:00Z",z,override,0).getJSONObject("actual_state").getString("state").equals("unknown"),"override exact expiry excluded");
  JSONObject restored=at("2026-10-31T00:30:00Z",z,null,now);check(restored.getString("catch_up").startsWith("recomputed")&&restored.getJSONArray("today_events").length()==0,"restart or missed jobs do not invent completed events");
  check(at("2026-10-30T00:30:00Z",z,null,now+1000).getBoolean("clock_rollback"),"clock rollback is explicit uncertainty");
  check(at("2026-10-30T00:30:00Z",ZoneId.of("America/Los_Angeles"),null,0).getString("current_time").startsWith("2026-10-29"),"role/user distinct dates");
  check(TemporalData.boundary(LocalDate.of(2026,3,8),"02:30",ZoneId.of("America/New_York"))==Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),"DST nonexistent local time resolved by zone rules");
  check(TemporalData.boundary(LocalDate.of(2026,11,1),"01:30",ZoneId.of("America/New_York"))==Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),"DST overlap uses explicit earlier-offset policy");
  JSONObject unknown=TemporalData.build(Clock.fixed(Instant.ofEpochMilli(now),ZoneOffset.UTC),z,null,new JSONObject().put("state","no_plan"),now,now,now+1000,0,null);check(unknown.isNull("last_interaction_minutes_ago")&&unknown.getBoolean("interaction_time_uncertain"),"future interaction timestamp not negative gap");
  JSONObject longChat=ScheduleData.status(plan(),plan(),ZonedDateTime.ofInstant(Instant.parse("2026-10-29T18:00:00Z"),z),true,Instant.parse("2026-10-29T17:00:00Z").toEpochMilli(),Instant.parse("2026-10-29T17:59:00Z").toEpochMilli());check(longChat.getString("state").equals("awake"),"continuous hour chat retains awake observation");
  JSONObject projected=TemporalData.build(Clock.fixed(Instant.ofEpochMilli(now),ZoneOffset.UTC),z,plan(),new JSONObject().put("state","activity"),now,now+86400000,0,0,null);check(projected.isNull("planned_activity_at_expected_delivery"),"future day does not reuse today's plan");
  JSONObject request=ModelClient.request("test-model","role data"+TemporalData.prompt(morning),Collections.emptyList(),"现在可以聊吗？");String system=request.getJSONArray("messages").getJSONObject(0).getString("content");check(system.contains("brief_attentive")&&system.contains("scheduled_in_progress")&&request.getJSONArray("messages").length()==2,"final chat request carries behavior and evidence semantics");
  check(!system.contains("早上差点迟到"),"unrecorded today event is not fabricated");
  JSONObject folded=plan().put("sleep","01:30");ZoneId ny=ZoneId.of("America/New_York");check(ScheduleData.asleepAt(folded,folded,Instant.parse("2026-11-01T06:10:00Z").atZone(ny)),"DST repeated hour after actual sleep start remains asleep");check(!ScheduleData.asleepAt(folded,folded,Instant.parse("2026-11-01T05:10:00Z").atZone(ny)),"DST first hour before sleep remains before start");
  System.out.println("PASS "+count+" temporal cases; request/behavior contracts only, no claim about real-model natural language.");
 }
}
