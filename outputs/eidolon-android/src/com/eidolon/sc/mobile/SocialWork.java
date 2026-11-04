package com.eidolon.sc.mobile;
import android.content.Context;import java.time.*;import java.util.*;import org.json.*;
/** Reads the existing persisted schedule and observed virtual life; never turns a plan into history. */
final class SocialWork {
 static long after(long last,long now,Random random){long low=Math.max(last+30*60000L,now+1),high=last+2*SocialData.HOUR;return last<=0||low>=high?0:low+(long)(random.nextDouble()*(high-low));}
 static long lastPlanned(Context c,ChatStore s,String who,long now)throws Exception{
  if(!c.getSharedPreferences("settings",0).getBoolean("hibiSchedules",false))return 0;
  ZoneId zone=TemporalRuntime.zone(c,s.personal(),who);String day=SocialData.day(now);JSONArray rows=ScheduleStore.all(s);long end=0;
  for(int r=0;r<rows.length();r++){JSONObject row=rows.getJSONObject(r);if(!row.optString("character").equals(who))continue;JSONObject plan=row.optJSONObject("plan");if(plan==null)continue;LocalDate date=LocalDate.parse(row.getString("date"));JSONArray slots=plan.getJSONArray("slots");
   for(int i=0;i<slots.length();i++){JSONObject slot=slots.getJSONObject(i);if(!slot.optString("kind").equals("work"))continue;long at=TemporalData.boundary(date,slot.getString("end"),zone);if(SocialData.day(at).equals(day))end=Math.max(end,at);}
  }return end;
 }
 static JSONArray completed(ChatStore s,String who,long now,boolean workOnly)throws Exception {
  JSONArray out=new JSONArray();JSONObject life=LifeStore.read(s,who);if(life==null||!life.optBoolean("enabled"))return out;
  JSONArray events=life.optJSONArray("events");if(events==null)return out;
  for(int i=events.length()-1;i>=0&&out.length()<5;i++){
   JSONObject e=events.getJSONObject(i);long at=e.optLong("at");if(at<=0||at>now||now-at>SocialData.DAY||!e.optString("status").equals("completed")||!e.optString("source").equals("simulation_rule")||!e.optString("origin").equals("simulated_life"))continue;
   if(workOnly){boolean match=false;JSONArray plans=ScheduleStore.all(s);for(int r=0;r<plans.length();r++){JSONObject row=plans.getJSONObject(r),p=row.optJSONObject("plan");if(!row.optString("character").equals(who)||p==null)continue;JSONArray slots=p.getJSONArray("slots");for(int n=0;n<slots.length();n++){JSONObject slot=slots.getJSONObject(n);if(slot.optString("kind").equals("work")&&e.optString("id").equals("slot/"+who+"/"+row.getString("date")+"/"+n)&&slot.optString("activity").equals(e.optString("title")))match=true;}}if(!match)continue;}
   out.put(new JSONObject().put("title",MediaData.cut(e.optString("title"),160)).put("at",at).put("id",e.optString("id")).put("source",e.optString("source")));
  }return out;
 }
 static Set<String> categories(JSONArray events)throws Exception{Set<String> out=new HashSet<>();for(int i=0;i<events.length();i++)out.addAll(PlacePool.workCategories(events.getJSONObject(i).getString("title")));return out;}
}
