package com.eidolon.sc.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.*;
import org.json.*;

/** Migration, independent diary retry budget and private-page reads. */
final class DiaryStore {
    static void create(SQLiteDatabase db){
        db.execSQL("ALTER TABLE writings ADD COLUMN secret TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE writings ADD COLUMN secret_read INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE diary_attempts(id TEXT PRIMARY KEY,character TEXT NOT NULL,date TEXT NOT NULL,tries INTEGER NOT NULL,at INTEGER NOT NULL,error TEXT NOT NULL DEFAULT '')");
    }
    static JSONArray attempts(ChatStore s)throws Exception{
        JSONArray out=new JSONArray();try(Cursor c=s.getReadableDatabase().rawQuery("SELECT character,date,tries,at,error FROM diary_attempts ORDER BY date DESC,id",null)){
            while(c.moveToNext())out.put(new JSONObject().put("character",c.getString(0)).put("date",c.getString(1)).put("tries",c.getInt(2)).put("at",c.getLong(3)).put("error",c.getString(4)));}return out;
    }
    static JSONObject attempt(ChatStore s,String who,String day)throws Exception{
        try(Cursor c=s.getReadableDatabase().rawQuery("SELECT tries,at FROM diary_attempts WHERE id=?",new String[]{who+"/"+day})){return c.moveToFirst()?new JSONObject().put("tries",c.getInt(0)).put("at",c.getLong(1)):null;}
    }
    static void restoreAttempt(ChatStore s,JSONObject r)throws Exception{
        ContentValues v=new ContentValues();v.put("id",r.getString("character")+"/"+r.getString("date"));for(String key:new String[]{"character","date","error"})v.put(key,r.getString(key));v.put("tries",r.getInt("tries"));v.put("at",r.getLong("at"));s.getWritableDatabase().insertOrThrow("diary_attempts",null,v);
    }
    static boolean begin(ChatStore s,String who,String day,LocalDate today,long now,long epoch)throws Exception{
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            JSONObject prior=attempt(s,who,day);if(epoch!=WritingStore.epoch.get()||WritingStore.exists(s,"diary",who,day)||!DiaryData.mayTry(day,today,now,prior))return false;
            ContentValues v=new ContentValues();v.put("id",who+"/"+day);v.put("character",who);v.put("date",day);v.put("tries",prior==null?1:prior.getInt("tries")+1);v.put("at",now);v.put("error","");
            if(db.insertWithOnConflict("diary_attempts",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new Exception("自动日记尝试次数未能保存，本次没有请求模型");
            db.execSQL("DELETE FROM diary_attempts WHERE id IN (SELECT id FROM diary_attempts ORDER BY date DESC,id LIMIT -1 OFFSET 1000)");db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    static void failed(ChatStore s,String who,String day,String error,long epoch){
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{if(epoch!=WritingStore.epoch.get())return;ContentValues v=new ContentValues();v.put("error",error.substring(0,Math.min(500,error.length())));db.update("diary_attempts",v,"id=?",new String[]{who+"/"+day});db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    static String secret(ChatStore s,String who,String day,boolean affinity)throws Exception{
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            JSONObject personal=s.personal();boolean cooling=Progression.relation(personal,who,System.currentTimeMillis()).optBoolean("cooling");
            if(!DiaryData.shown(personal.getJSONObject("affinity").optDouble(who,50),cooling,affinity).equals("secret"))throw new Exception("关系达到亲密且不在冷却期时，才能翻开隐藏页");
            String text;try(Cursor c=db.rawQuery("SELECT secret FROM writings WHERE id=? AND kind='diary'",new String[]{WritingData.key("diary",who,day)})){if(!c.moveToFirst()||(text=c.getString(0)).isEmpty())throw new Exception("这篇日记没有隐藏页");}
            ContentValues v=new ContentValues();v.put("secret_read",1);v.put("is_read",1);db.update("writings",v,"id=?",new String[]{WritingData.key("diary",who,day)});db.setTransactionSuccessful();return text;
        }finally{db.endTransaction();}
    }
}
