package com.eidolon.sc.mobile;
import java.time.*;import java.util.*;import org.json.*;
public class ArchitectureTests {
 static int count;static void check(boolean b,String n){if(!b)throw new AssertionError(n);count++;System.out.println("PASS "+n);}
 interface Work{void run()throws Exception;}static void reject(Work w,String n)throws Exception{try{w.run();}catch(Exception ok){check(true,n);return;}throw new AssertionError(n);}
 static JSONObject spec(String title)throws Exception{return new JSONObject().put("title",title).put("interest","creative").put("steps",new JSONArray().put(new JSONObject().put("title","准备").put("external",false)).put(new JSONObject().put("title","完成").put("external",false)));}
 public static void main(String[] args)throws Exception{
  ZoneId z=LifeTests.Z;JSONObject d=LifeTests.entry("2026-10-30",true,false),s=LifeTests.next(LifeTests.fresh("mano","温柔","2026-10-30T07:00:00"),"2026-10-30T08:10:00",d,null);
  String key=LifeData.semanticKey(s);JSONObject observed=LifeTests.next(s,"2026-10-30T08:20:00",d,null);
  check(key.equals(LifeData.semanticKey(observed))&&observed.getLong("observedAt")!=s.getLong("observedAt"),"observation progress and mood decay do not invalidate generation revision");
  JSONObject ended=LifeTests.next(observed,"2026-10-30T09:40:00",d,null);check(!key.equals(LifeData.semanticKey(ended)),"activity completion is a semantic mutation");
  JSONObject repeated=LifeTests.next(ended,"2026-10-30T09:45:00",d,null);check(LifeData.semanticKey(repeated).equals(LifeData.semanticKey(ended)),"completed history order remains stable during repeated observation");
  JSONObject settings=new JSONObject(observed.toString()).put("eventOnlyReach",true);check(!key.equals(LifeData.semanticKey(settings)),"concurrent user settings invalidate a stale generation");
  JSONObject d2=LifeTests.entry("2026-10-31",false,false);ended=LifeTests.next(ended,"2026-10-31T08:30:00",d2,d);ended=LifeTests.next(ended,"2026-10-31T09:40:00",d2,d);
  check(ended.getJSONObject("profile").getDouble("confidence")==50.5,"fixture has evidenced cross-day evolution");
  long repairedAt=LifeTests.clock("2026-10-30T10:00:00").millis();LifeData.calibrate(ended,repairedAt,z);
  check(LifeData.goal(ended).getInt("progress")==1&&ended.getJSONObject("profile").getDouble("confidence")==50&&ended.getJSONArray("evolutionLog").length()==0,"clock repair rewinds future goal progress and derived traits");
  check(ended.isNull("actual")&&ended.getJSONObject("emotion").getDouble("valence")==0,"clock repair resets unsupported aggregate emotion and activity");
  JSONObject resumed=LifeTests.next(ended,"2026-10-31T08:30:00",d2,d);resumed=LifeTests.next(resumed,"2026-10-31T09:40:00",d2,d);LifeData.validate(resumed,Collections.singleton("mano"));check(LifeData.goal(resumed).getInt("progress")==2,"recalibrated future step can complete again without duplicate event IDs");
  boolean futureConfirmed=false;for(int i=0;i<ended.getJSONArray("memories").length();i++){JSONObject m=ended.getJSONArray("memories").getJSONObject(i);if(m.optLong("at")>repairedAt&&m.optBoolean("confirmed"))futureConfirmed=true;}
  check(!futureConfirmed,"future memories cannot remain confirmed after recalibration");LifeData.validate(ended,Collections.singleton("mano"));
  JSONObject archive=LifeTests.fresh("mano","温柔","2026-10-30T07:00:00");long now=LifeTests.clock("2026-10-30T07:00:00").millis();String first="";
  for(int i=0;i<20;i++){LifeData.seedGoal(archive,spec("创作作品"+i),now+i*LifeData.DAY);JSONObject g=LifeData.goal(archive);if(i==0)first=g.getString("id");g.put("status","cancelled");JSONObject root=LifeData.find(archive.getJSONArray("events"),g.getString("id"));root.put("status","cancelled");archive.getJSONArray("events").put(LifeData.event("activity/"+i,"练习","activity",now,now+1000,"").put("goalId",g.getString("id")).put("status","completed").put("at",now));LifeData.prune(archive,now);LifeData.validate(archive,Collections.singleton("mano"));}
  check(archive.getJSONArray("goals").length()==4&&archive.getJSONArray("archives").length()==12,"twenty goals retain bounded live goals and twelve historical summaries");
  check(LifeData.archivedRelevant(archive,"创作作品",now+30*LifeData.DAY).length()==2,"archived goals are selectively retrievable with bounded context");
  JSONObject archiveContext=LifeStore.project(archive,new JSONObject().put("timezone",z.getId()).put("behavior",new JSONObject()),"创作作品",false,now+30*LifeData.DAY,null);check(archiveContext.getJSONArray("archived_history").getJSONObject(0).has("localTime")&&archiveContext.getJSONArray("archived_history").getJSONObject(0).has("daysBeforeCurrent"),"archived model context carries trusted role-local dates and day offsets");
  boolean dangling=false;for(int i=0;i<archive.getJSONArray("events").length();i++){JSONObject e=archive.getJSONArray("events").getJSONObject(i);if(!e.optString("goalId").isEmpty()&&LifeData.find(archive.getJSONArray("goals"),e.getString("goalId"))==null)dangling=true;}
  check(!dangling,"retiring goals removes associated activity references as well as descendants");
  JSONObject restored=new JSONObject(archive.toString());LifeData.validate(restored,Collections.singleton("mano"));check(restored.getJSONArray("archives").toString().equals(archive.getJSONArray("archives").toString()),"bounded archive survives JSON backup roundtrip");
  LifeData.calibrate(restored,now,z);check(LifeData.archivedRelevant(restored,"创作作品",now).length()==0,"future archived summaries excluded after clock rollback");
  JSONObject config=new JSONObject().put("character","mano").put("zone","America/New_York").put("events",new JSONArray());RuntimeSettings.validateBackup(new JSONObject().put("runtimeSettings",new JSONArray().put(config)),Collections.singleton("mano"));check(true,"portable independent role timezone validates including DST zones");
  JSONObject secret=new JSONObject(config.toString()).put("apiKey","must-not-be-imported");reject(()->RuntimeSettings.validateRow(secret),"backup allowlist rejects credentials and unknown fields");
  reject(()->RuntimeSettings.validateBackup(new JSONObject().put("runtimeSettings",new JSONArray().put(config).put(config)),Collections.singleton("mano")),"duplicate role configuration rejected before restore");
  JSONObject badZone=new JSONObject(config.toString()).put("zone","Mars/Future");reject(()->RuntimeSettings.validateRow(badZone),"unknown role timezone rejected");
  System.out.println("PASS "+count+" architecture state contracts; no network or paid model calls.");
 }
}
