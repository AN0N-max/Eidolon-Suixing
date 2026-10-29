package com.eidolon.sc.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

final class ScheduleStore {
    static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE schedules(id TEXT PRIMARY KEY,character TEXT NOT NULL,date TEXT NOT NULL,body TEXT NOT NULL)");}
    static JSONObject entry(ChatStore s,String who,String day)throws Exception{try(Cursor c=s.getReadableDatabase().rawQuery("SELECT body FROM schedules WHERE id=?",new String[]{who+"/"+day})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    static JSONObject plan(ChatStore s,String who,String day)throws Exception{JSONObject e=entry(s,who,day);return e==null?null:e.optJSONObject("plan");}
    static int count(ChatStore s,String day){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT count(*) FROM schedules WHERE date=?",new String[]{day})){c.moveToFirst();return c.getInt(0);}}
    static JSONArray all(ChatStore s)throws Exception{JSONArray a=new JSONArray();try(Cursor c=s.getReadableDatabase().rawQuery("SELECT body FROM schedules ORDER BY date DESC,id",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
    static void save(ChatStore store,JSONObject entry,long epoch)throws Exception{
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            if(epoch>=0&&WritingStore.epoch.get()!=epoch)throw new Exception("恢复备份后原行程请求已失效");
            ContentValues v=new ContentValues();String who=entry.getString("character"),day=entry.getString("date");v.put("id",who+"/"+day);v.put("character",who);v.put("date",day);v.put("body",entry.toString());
            if(db.insertWithOnConflict("schedules",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new Exception("行程保存失败");db.execSQL("DELETE FROM schedules WHERE id IN (SELECT id FROM schedules ORDER BY date DESC,id LIMIT -1 OFFSET 250)");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static boolean talked(ChatStore s,String who){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT 1 FROM messages WHERE character=? AND role='user' LIMIT 1",new String[]{who})){return c.moveToFirst();}}
}
