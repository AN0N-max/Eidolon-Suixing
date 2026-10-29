package com.eidolon.sc.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Durable task state. Message linkage and completion are committed with the message. */
final class ReachPlanStore {
    static final String MANUAL="用户重新安排，未执行计划已替换";
    static void replacePending(ChatStore s,long now){s.getWritableDatabase().execSQL("UPDATE reach_tasks SET status='SKIPPED',finished=?,token='',error=? WHERE status='PENDING'",new Object[]{now,MANUAL});}

    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE reach_days(day TEXT NOT NULL,zone TEXT NOT NULL,revision INTEGER NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(day,zone,revision))");
        db.execSQL("CREATE TABLE reach_tasks(id TEXT PRIMARY KEY,character TEXT NOT NULL,session TEXT NOT NULL,day TEXT NOT NULL,zone TEXT NOT NULL,due INTEGER NOT NULL,expires INTEGER NOT NULL,type TEXT NOT NULL DEFAULT 'daily',status TEXT NOT NULL DEFAULT 'PENDING',created INTEGER NOT NULL,finished INTEGER NOT NULL DEFAULT 0,message_id INTEGER NOT NULL DEFAULT 0,contact INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL,token TEXT NOT NULL DEFAULT '',attempts INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',opportunity INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX reach_tasks_due ON reach_tasks(status,due)");
    }
    static JSONObject row(Cursor c)throws Exception{JSONObject o=new JSONObject();String[] names=c.getColumnNames();for(int i=0;i<names.length;i++)o.put(names[i],c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));return o;}
    static JSONObject get(ChatStore s,String id)throws Exception{try(Cursor c=s.getReadableDatabase().rawQuery("SELECT * FROM reach_tasks WHERE id=?",new String[]{id})){return c.moveToFirst()?row(c):null;}}
    static JSONArray today(ChatStore s,ReachConfig c,long now)throws Exception{JSONArray a=new JSONArray();try(Cursor r=s.getReadableDatabase().rawQuery("SELECT * FROM reach_tasks WHERE day=? AND zone=? ORDER BY CASE WHEN status IN ('PENDING','RUNNING','FAILED') THEN 0 ELSE 1 END,due,created DESC LIMIT 300",new String[]{ReachPolicy.date(c,now),c.text("zone")})){while(r.moveToNext())a.put(row(r));}return a;}
    static void stop(ChatStore s,String reason,long now){s.getWritableDatabase().execSQL("UPDATE reach_tasks SET status='SKIPPED',finished=?,token='',error=? WHERE status IN ('PENDING','RUNNING')",new Object[]{now,reason});}
    static void interrupted(ChatStore s,long now){s.getWritableDatabase().execSQL("UPDATE reach_tasks SET status='FAILED',finished=?,token='',error='请求被中断，结果与服务计费未知；为避免重复请求未自动重发' WHERE status='RUNNING'",new Object[]{now});}
    static void finish(ChatStore s,String id,String token,String state,String error,long now){s.getWritableDatabase().execSQL("UPDATE reach_tasks SET status=?,finished=?,error=?,token='' WHERE id=? AND status='RUNNING' AND token=?",new Object[]{state,now,error,id,token});}
    static String claim(ChatStore s,String id,long contact,long now){String token=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("status","RUNNING");v.put("token",token);v.put("contact",contact);v.put("error","");if(s.getWritableDatabase().update("reach_tasks",v,"id=? AND status='PENDING' AND due<=? AND expires>?",new String[]{id,""+now,""+now})!=1)return "";return token;}
    static String due(ChatStore s,long now){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT id FROM reach_tasks WHERE status='PENDING' AND due<=? AND expires>? ORDER BY due LIMIT 1",new String[]{""+now,""+now})){return c.moveToFirst()?c.getString(0):"";}}
    static long next(ChatStore s){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT coalesce(min(due),0) FROM reach_tasks WHERE status='PENDING'",null)){c.moveToFirst();return c.getLong(0);}}
    static void defer(ChatStore s,String id,String reason,long at){s.getWritableDatabase().execSQL("UPDATE reach_tasks SET due=?,error=? WHERE id=? AND status='PENDING'",new Object[]{at,reason,id});}
    static void expire(ChatStore s,long now){s.getWritableDatabase().execSQL("UPDATE reach_tasks SET status='SKIPPED',finished=?,error='已过有效期，不补发历史搭话' WHERE status='PENDING' AND expires<=?",new Object[]{now,now});}
    static void skip(ChatStore s,String id,String reason,long now){s.getWritableDatabase().execSQL("UPDATE reach_tasks SET status='SKIPPED',finished=?,error=? WHERE id=? AND status='PENDING'",new Object[]{now,reason,id});}
    static boolean isRunning(ChatStore s,String id,String token){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT 1 FROM reach_tasks WHERE id=? AND status='RUNNING' AND token=?",new String[]{id,token})){return c.moveToFirst();}}
    private ReachPlanStore(){}
}
