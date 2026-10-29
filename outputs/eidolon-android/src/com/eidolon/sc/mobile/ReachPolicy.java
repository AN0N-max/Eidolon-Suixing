package com.eidolon.sc.mobile;
import android.content.SharedPreferences;
import java.util.*;
final class ReachPolicy {
 static final long HOUR=3600000L,WINDOW=7*24*HOUR;
 static long delay(int score,double random){int lo=score>=85?3:score>=65?8:20,hi=score>=85?6:score>=65?16:36;return (long)((lo+(hi-lo)*random)*HOUR);}
 static Calendar clock(long now){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));c.setTimeInMillis(now);return c;}
 static long morning(long due,double random){Calendar c=clock(due);int h=c.get(Calendar.HOUR_OF_DAY);if(h>=22)c.add(Calendar.DAY_OF_MONTH,1);if(h>=22||h<8){c.set(Calendar.HOUR_OF_DAY,8);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis()+(long)(random*2*HOUR);}return due;}
 static long dayStart(long now){Calendar c=clock(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
 static boolean allowed(int score,long contact,long now,boolean ended,boolean cooling){int h=clock(now).get(Calendar.HOUR_OF_DAY);return score>=40&&!ended&&!cooling&&now>=contact&&now-contact<=WINDOW&&h>=8&&h<22;}
 static boolean allowed(int score,long contact,long now,boolean ended,boolean cooling,SharedPreferences prefs){return allowed(ReachConfig.read(prefs),score,contact,now,ended,cooling);}
 static long due(SharedPreferences prefs,int score,long after){return due(ReachConfig.read(prefs),score,after,Math.random(),Math.random());}
 static long due(String mode,int hours,String time,int score,long after){if(mode.equals("daily")){String[] parts=time.split(":");Calendar c=clock(after);c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));c.set(Calendar.MINUTE,Integer.parseInt(parts[1]));c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);if(c.getTimeInMillis()<=after)c.add(Calendar.DAY_OF_MONTH,1);return c.getTimeInMillis();}if(mode.equals("interval"))return morning(after+hours*HOUR,Math.random());return morning(after+delay(score,Math.random()),Math.random());}
 static java.time.ZonedDateTime local(ReachConfig c,long at){return java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.of(c.text("zone")));}
 static String date(ReachConfig c,long at){return local(c,at).toLocalDate().toString();}
 static long dayStart(ReachConfig c,long at){return local(c,at).toLocalDate().atStartOfDay(java.time.ZoneId.of(c.text("zone"))).toInstant().toEpochMilli();}
 static int minute(String text){return Integer.parseInt(text.substring(0,2))*60+Integer.parseInt(text.substring(3));}
 static boolean quiet(ReachConfig c,long at){
  if(!c.quietApplies())return false;java.time.ZonedDateTime now=local(c,at);int n=now.getHour()*60+now.getMinute(),start=minute(c.text("quietStart")),end=minute(c.text("quietEnd"));
  return start<end?n>=start&&n<end:n>=start||n<end;
 }
 static long outsideQuiet(ReachConfig c,long at,double random){
  if(!quiet(c,at))return at;java.time.ZonedDateTime now=local(c,at);java.time.LocalDate endDay=now.toLocalDate();int start=minute(c.text("quietStart")),end=minute(c.text("quietEnd")),n=now.getHour()*60+now.getMinute();
  if(start>end&&n>=start)endDay=endDay.plusDays(1);
  java.time.ZoneId zone=now.getZone();java.time.ZonedDateTime finish=endDay.atTime(java.time.LocalTime.parse(c.text("quietEnd"))).atZone(zone);
  java.time.ZonedDateTime nextStart=endDay.atTime(java.time.LocalTime.parse(c.text("quietStart"))).atZone(zone);if(!nextStart.isAfter(finish))nextStart=nextStart.plusDays(1);
  long base=finish.toInstant().toEpochMilli(),room=Math.max(0,nextStart.toInstant().toEpochMilli()-base-1),spread=Math.min(room,c.integer("spreadMinutes")*60000L);
  return base+(long)(Math.max(0,Math.min(1,random))*spread);
 }
 static boolean allowed(ReachConfig c,int score,long contact,long now,boolean ended,boolean cooling){return score>=c.integer("minAffinity")&&!ended&&!cooling&&now>=contact&&now-contact<=c.integer("contactDays")*24*HOUR&&!quiet(c,now);}
 static long due(ReachConfig c,int score,long after,double random,double spread){
  long target;if(c.text("mode").equals("daily")){java.time.ZonedDateTime now=local(c,after);java.time.ZonedDateTime next=now.toLocalDate().atTime(java.time.LocalTime.parse(c.text("time"))).atZone(now.getZone());if(next.toInstant().toEpochMilli()<=after)next=next.plusDays(1);target=next.toInstant().toEpochMilli();}
  else {String tier=score>=85?"intimate":score>=65?"close":"trust";double hours=c.text("mode").equals("interval")?c.number("hours"):c.number(tier+"Min")+(c.number(tier+"Max")-c.number(tier+"Min"))*Math.max(0,Math.min(1,random));target=after+(long)(hours*HOUR);}
  return outsideQuiet(c,target,spread);
 }
 static long plan(ReachConfig c,int score,long contact,long now,long stored,double random,double spread){
  if(stored>0)return stored;
  return due(c,score,c.text("mode").equals("daily")?contact:Math.max(contact,now),random,spread);
 }
}
