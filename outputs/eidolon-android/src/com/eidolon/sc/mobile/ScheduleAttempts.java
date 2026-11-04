package com.eidolon.sc.mobile;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;
import java.util.*;import java.time.*;import org.json.*;import java.io.IOException;

/** Durable admission before a schedule API call. A restarted process cannot replay its unknown result. */
final class ScheduleAttempts {
 static final Object LOCK=new Object();
 static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE schedule_attempts(id TEXT PRIMARY KEY,character TEXT NOT NULL,day TEXT NOT NULL,at INTEGER NOT NULL,state TEXT NOT NULL,token TEXT NOT NULL,error TEXT NOT NULL DEFAULT '')");}
 static JSONObject get(ChatStore s,String who,String day)throws Exception{
  try(Cursor c=s.getReadableDatabase().rawQuery("SELECT character,day,at,state,error FROM schedule_attempts WHERE id=?",new String[]{who+"/"+day})){return c.moveToFirst()?new JSONObject().put("character",c.getString(0)).put("day",c.getString(1)).put("at",c.getLong(2)).put("state",c.getString(3)).put("error",c.getString(4)):null;}
 }
 static int count(ChatStore s,String day,ZoneId zone)throws Exception {
  Set<String> ids=new HashSet<>();JSONArray plans=ScheduleStore.all(s);
  for(int i=0;i<plans.length();i++){JSONObject row=plans.getJSONObject(i);long at=row.optLong("createdAt");if(at>0?Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString().equals(day):row.optString("date").equals(day))ids.add(row.getString("character")+"/"+row.getString("date"));}
  try(Cursor c=s.getReadableDatabase().rawQuery("SELECT id,at FROM schedule_attempts",null)){while(c.moveToNext())if(Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDate().toString().equals(day))ids.add(c.getString(0));}return ids.size();
 }
 static String claim(ChatStore s,String who,String day,boolean manual,long at,long epoch)throws Exception{synchronized(LOCK){SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
  if(epoch!=WritingStore.epoch.get())throw new IOException("恢复备份后原行程请求已失效");
  JSONObject prior=get(s,who,day);if(ScheduleStore.plan(s,who,day)!=null)throw new IOException("今天已经有安排");
  if(prior!=null&&prior.getString("state").equals("RUNNING"))throw new IOException("她正在安排行程");
  if(!manual&&(prior!=null||ScheduleStore.entry(s,who,day)!=null))return "";
  String token=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",who+"/"+day);v.put("character",who);v.put("day",day);v.put("at",at);v.put("state","RUNNING");v.put("token",token);v.put("error","");
  if(db.insertWithOnConflict("schedule_attempts",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IOException("无法保存行程请求身份，尚未调用模型");db.setTransactionSuccessful();return token;
 }finally{db.endTransaction();}}}
 static boolean owns(ChatStore s,String who,String day,String token){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT 1 FROM schedule_attempts WHERE id=? AND state='RUNNING' AND token=?",new String[]{who+"/"+day,token})){return c.moveToFirst();}}
 static void finish(ChatStore s,String who,String day,String token,String state,String error){ContentValues v=new ContentValues();v.put("state",state);v.put("token","");v.put("error",MediaData.cut(error,500));s.getWritableDatabase().update("schedule_attempts",v,"id=? AND state='RUNNING' AND token=?",new String[]{who+"/"+day,token});}
 static void recover(ChatStore s){s.getWritableDatabase().execSQL("UPDATE schedule_attempts SET state='FAILED',token='',error='上次生成已中断，服务计费结果未知；未自动重试' WHERE state='RUNNING'");}
 static JSONArray backup(ChatStore s)throws Exception{JSONArray out=new JSONArray();try(Cursor c=s.getReadableDatabase().rawQuery("SELECT character,day,at,state,error FROM schedule_attempts ORDER BY at DESC LIMIT 10000",null)){while(c.moveToNext())out.put(new JSONObject().put("character",c.getString(0)).put("day",c.getString(1)).put("at",c.getLong(2)).put("state",c.getString(3)).put("error",c.getString(4)));}return out;}
 static void validate(JSONObject data,Set<String> known)throws Exception {
  JSONArray rows=data.getJSONArray("scheduleAttempts");if(rows.length()>10000||rows.toString().length()>3000000)throw new IOException("行程请求记录过多");Set<String> ids=new HashSet<>();
  for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);String who=r.getString("character"),day=r.getString("day");LocalDate.parse(day);if(!known.contains(who)||!ids.add(who+"/"+day)||!Arrays.asList("RUNNING","DONE","FAILED").contains(r.getString("state")))throw new IOException("行程请求记录无效");SocialBackup.stamp(r,"at",true);SocialData.text(r,"error",500,false);}
 }
 static void restore(ChatStore s,JSONObject data)throws Exception {
  recover(s);if(data.optInt("version")<18)return;JSONArray rows=data.getJSONArray("scheduleAttempts");
  for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);ContentValues v=new ContentValues();v.put("id",r.getString("character")+"/"+r.getString("day"));v.put("character",r.getString("character"));v.put("day",r.getString("day"));v.put("at",r.getLong("at"));boolean interrupted=r.getString("state").equals("RUNNING");v.put("state",interrupted?"FAILED":r.getString("state"));v.put("token","");v.put("error",interrupted?"备份中的未确认生成不自动重试":r.optString("error"));s.getWritableDatabase().insertWithOnConflict("schedule_attempts",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 }
}
