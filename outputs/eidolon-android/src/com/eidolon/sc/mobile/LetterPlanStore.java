package com.eidolon.sc.mobile;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** A prepared letter stays outside writings until atomically delivered. */
final class LetterPlanStore {
    static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE letter_plans(id TEXT PRIMARY KEY,body TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 0)");}
    static String key(JSONObject r)throws Exception{return r.getString("character")+"/"+r.getString("date");}
    static JSONObject get(ChatStore s,String key)throws Exception{try(Cursor c=s.getReadableDatabase().rawQuery("SELECT body FROM letter_plans WHERE id=?",new String[]{key})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    static JSONArray all(ChatStore s)throws Exception{JSONArray rows=new JSONArray();try(Cursor c=s.getReadableDatabase().rawQuery("SELECT body FROM letter_plans ORDER BY id",null)){while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));}return rows;}
    static void insert(ChatStore s,JSONObject row,long epoch)throws Exception{
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            if(epoch!=WritingStore.epoch.get())return;
            ContentValues v=new ContentValues();v.put("id",key(row));v.put("body",row.toString());v.put("revision",row.getLong("revision"));if(db.insertWithOnConflict("letter_plans",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0&&get(s,key(row))==null)throw new Exception("来信计划保存失败");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static boolean update(ChatStore s,JSONObject old,JSONObject next,long epoch)throws Exception{
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            if(epoch!=WritingStore.epoch.get())return false;
            next.put("revision",old.getLong("revision")+1);ContentValues v=new ContentValues();v.put("body",next.toString());v.put("revision",next.getLong("revision"));
            boolean changed=db.update("letter_plans",v,"id=? AND revision=?",new String[]{key(old),""+old.getLong("revision")})==1;db.setTransactionSuccessful();return changed;
        }finally{db.endTransaction();}
    }
    static int reserved(ChatStore s,String day)throws Exception{
        int count=WritingStore.countLetters(s,day);JSONArray rows=all(s);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(day.equals(r.optString("date"))&&r.optString("state").equals("ready")&&!WritingStore.exists(s,"letter",r.getString("character"),day))count++;}return count;
    }
    static int preparedCount(ChatStore s)throws Exception{int count=0;JSONArray rows=all(s);for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("state").equals("ready"))count++;return count;}
    static boolean ready(ChatStore s,JSONObject old,String text,long epoch)throws Exception{
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            if(epoch!=WritingStore.epoch.get()||!old.getString("state").equals("pending")||reserved(s,old.getString("date"))>=3)return false;
            JSONObject next=new JSONObject(old.toString()).put("state","ready").put("text",text).put("error","");
            boolean saved=update(s,old,next,epoch);db.setTransactionSuccessful();return saved;
        }finally{db.endTransaction();}
    }
    static int deliver(ChatStore s,long now,long epoch)throws Exception{
        int delivered=0;JSONArray rows=all(s);for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);if(!r.getString("state").equals("ready")||r.getLong("deliverAt")>now)continue;
            SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
                JSONObject current=get(s,key(r));if(epoch!=WritingStore.epoch.get()||current==null||current.getLong("revision")!=r.getLong("revision"))continue;
                String who=r.getString("character"),day=r.getString("date");boolean exists=WritingStore.exists(s,"letter",who,day),full=WritingStore.countLetters(s,day)>=3;
                try(Cursor capacity=db.rawQuery("SELECT count(*),coalesce(sum(length(body)+length(secret)),0) FROM writings",null)){capacity.moveToFirst();full|=capacity.getInt(0)>=500||capacity.getLong(1)+r.getString("text").length()>2000000;}
                JSONObject next=new JSONObject(r.toString()).put("state",full&&!exists?"skipped":"delivered").put("text","");
                if(!update(s,r,next,epoch))continue;
                if(!exists&&!full){WritingStore.save(s,new JSONObject().put("kind","letter").put("character",who).put("date",day).put("text",r.getString("text")).put("read",false),epoch);delivered++;}
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }return delivered;
    }
    static void prune(ChatStore s,java.time.LocalDate today)throws Exception{
        JSONArray rows=all(s);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(!r.getString("state").equals("ready")&&r.getString("date").compareTo(today.minusDays(35).toString())<0)s.getWritableDatabase().delete("letter_plans","id=? AND revision=?",new String[]{key(r),""+r.getLong("revision")});}
    }
}
