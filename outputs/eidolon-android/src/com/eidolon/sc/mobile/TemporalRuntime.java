package com.eidolon.sc.mobile;
import android.content.*;import android.database.Cursor;import java.time.*;import org.json.*;
/** No model calls, no historical event replay. Cache is reconstructible observation metadata. */
final class TemporalRuntime {
 static final Object LOCK=new Object();
 static ZoneId zone(Context c,JSONObject personal,String who){String id=c.getSharedPreferences("settings",0).getString("roleZone/"+who,"");return id.isEmpty()?ScheduleData.zone(personal):ZoneId.of(id);}
 static JSONObject profile(Context c,JSONObject personal,String who)throws Exception{return new JSONObject().put("character",who).put("override",c.getSharedPreferences("settings",0).getString("roleZone/"+who,"")).put("effective",zone(c,personal,who).getId());}
 static String prompt(Context c,ChatStore store,JSONObject personal,String who,long firstId,long received,long due)throws Exception{return prompt(c,store,personal,who,firstId,received,due,Clock.systemUTC());}
 static String prompt(Context c,ChatStore store,JSONObject personal,String who,long firstId,long received,long due,Clock clock)throws Exception{
  long now=clock.millis(),last=0;try(Cursor row=store.getReadableDatabase().rawQuery("SELECT coalesce(max(sent_at),0) FROM messages WHERE character=? AND role IN ('user','assistant') AND id<? AND sent_at>0",new String[]{who,firstId>0?""+firstId:"9223372036854775807"})){if(row.moveToFirst())last=row.getLong(0);}
  SharedPreferences p=c.getSharedPreferences("settings",0);JSONObject out; synchronized(LOCK){String raw=p.getString("temporal/"+who,"");JSONObject prior=raw.isEmpty()?new JSONObject():new JSONObject(raw);JSONObject status=ScheduleRuntime.state(c,store,personal,who,now);
   out=TemporalData.build(Clock.fixed(Instant.ofEpochMilli(now),ZoneOffset.UTC),zone(c,personal,who),status.optJSONObject("plan"),status,received,due,last,prior.optLong("timestamp"),override(c,who,now));
   long mono=android.os.SystemClock.elapsedRealtime();long oldMono=prior.optLong("elapsed",-1),oldWall=prior.optLong("timestamp",0);boolean sameBoot=oldMono>=0&&mono>=oldMono;out.put("clock_jump_detected",sameBoot&&Math.abs((now-oldWall)-(mono-oldMono))>120000).put("time_zone_changed",!prior.optString("zone",zone(c,personal,who).getId()).equals(zone(c,personal,who).getId()));
   JSONArray known=new JSONArray();String day=Instant.ofEpochMilli(now).atZone(zone(c,personal,who)).toLocalDate().toString();JSONArray rows=new JSONArray(p.getString("temporalEvents/"+who,"[]"));for(int i=0;i<rows.length();i++){JSONObject e=rows.getJSONObject(i);if(e.optLong("at")<=now&&Instant.ofEpochMilli(e.getLong("at")).atZone(zone(c,personal,who)).toLocalDate().toString().equals(day))known.put(e);}out.put("today_events",known);
   out.put("state_version",prior.optLong("state_version")+1);if(!p.edit().putString("temporal/"+who,new JSONObject().put("timestamp",now).put("elapsed",mono).put("zone",zone(c,personal,who).getId()).put("state_version",out.getLong("state_version")).toString()).commit())throw new Exception("时间状态保存失败");
  }return TemporalData.prompt(out);
 }
 static JSONObject override(Context c,String who,long now)throws Exception{String raw=c.getSharedPreferences("settings",0).getString("temporalOverride/"+who,"");if(raw.isEmpty())return null;JSONObject row=new JSONObject(raw);return row.optLong("from")<=now&&now<row.optLong("until")?row:null;}
 static JSONObject setOverride(Context c,String who,String name,int minutes,long now)throws Exception{
  if(name.length()>80||minutes<1||minutes>1440)throw new Exception("临时状态最多80字，有效期1到1440分钟");SharedPreferences p=c.getSharedPreferences("settings",0);synchronized(LOCK){if(name.trim().isEmpty()){p.edit().remove("temporalOverride/"+who).commit();return new JSONObject();}
   String availability=name.matches(".*(休息|午休|空闲).*" )?"high":name.matches(".*(工作|上课|排练|睡觉|洗澡).*" )?"low":"unknown";String style=availability.equals("high")?"normal":availability.equals("low")?"brief_attentive":"normal";JSONObject row=new JSONObject().put("name",name.trim()).put("state","user_defined_activity").put("from",now).put("until",now+minutes*60000L).put("confirmed",true).put("source","explicit_user_override").put("availability",availability).put("interruptibility",availability).put("response_style",style);
   JSONArray old=new JSONArray(p.getString("temporalEvents/"+who,"[]")),events=new JSONArray();for(int i=Math.max(0,old.length()-19);i<old.length();i++)events.put(old.get(i));events.put(new JSONObject().put("at",now).put("name",name.trim()).put("status","user_reported_start").put("source","explicit_user_override"));if(!p.edit().putString("temporalOverride/"+who,row.toString()).putString("temporalEvents/"+who,events.toString()).commit())throw new Exception("临时状态保存失败");return row;}
 }
 private TemporalRuntime(){}
}
