package com.eidolon.sc.mobile;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import org.json.*;

/** Separate tables let background writing finish without replacing personal data. */
final class WritingStore {
    static final java.util.concurrent.atomic.AtomicLong epoch=new java.util.concurrent.atomic.AtomicLong();
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE writings(id TEXT PRIMARY KEY,kind TEXT NOT NULL,character TEXT NOT NULL,date TEXT NOT NULL,body TEXT NOT NULL,is_read INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE writing_events(id TEXT PRIMARY KEY,character TEXT NOT NULL,date TEXT NOT NULL,body TEXT NOT NULL)");
    }
    static void event(ChatStore store,String who,String date,String summary)throws Exception{
        SQLiteDatabase db=store.getWritableDatabase();String id=who+"/"+date;db.beginTransaction();try{
            String text="";try(Cursor c=db.rawQuery("SELECT body FROM writing_events WHERE id=?",new String[]{id})){if(c.moveToFirst())text=c.getString(0);}
            if(text.contains(summary)){db.setTransactionSuccessful();return;}
            text=text.isEmpty()?summary:text+"\n"+summary;if(text.length()>4000)text=text.substring(0,4000);
            ContentValues v=new ContentValues();v.put("id",id);v.put("character",who);v.put("date",date);v.put("body",text);db.insertWithOnConflict("writing_events",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            db.execSQL("DELETE FROM writing_events WHERE id IN (SELECT id FROM writing_events ORDER BY date DESC,id LIMIT -1 OFFSET 1000)");
            while(true){try(Cursor c=db.rawQuery("SELECT coalesce(sum(length(body)),0) FROM writing_events",null)){c.moveToFirst();if(c.getLong(0)<=500000)break;}db.execSQL("DELETE FROM writing_events WHERE id=(SELECT id FROM writing_events ORDER BY date,id LIMIT 1)");}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static boolean exists(ChatStore s,String kind,String who,String date){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT 1 FROM writings WHERE id=?",new String[]{WritingData.key(kind,who,date)})){return c.moveToFirst();}}
    static int countLetters(ChatStore s,String day){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT count(*) FROM writings WHERE kind='letter' AND date=?",new String[]{day})){c.moveToFirst();return c.getInt(0);}}
    static String lastLetter(ChatStore s,String who){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT coalesce(max(date),'') FROM writings WHERE kind='letter' AND character=?",new String[]{who})){c.moveToFirst();return c.getString(0);}}
    static void save(ChatStore s,JSONObject row)throws Exception{save(s,row,-1);}
    static void save(ChatStore s,JSONObject row,long expectedEpoch)throws Exception{
        WritingData.validate(row);SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            if(expectedEpoch>=0&&expectedEpoch!=epoch.get())throw new Exception("备份恢复后，原写作请求已失效");
            try(Cursor c=db.rawQuery("SELECT count(*),coalesce(sum(length(body)+length(secret)),0) FROM writings",null)){c.moveToFirst();if(c.getInt(0)>=500||c.getLong(1)+row.getString("text").length()+row.optString("secret","").length()>2000000)throw new Exception("信件日记已达到本机存储上限");}
            ContentValues v=new ContentValues();String kind=row.getString("kind"),who=row.getString("character"),day=row.getString("date");v.put("id",WritingData.key(kind,who,day));v.put("kind",kind);v.put("character",who);v.put("date",day);v.put("body",row.getString("text"));v.put("is_read",row.optBoolean("read")?1:0);
            v.put("secret",row.optString("secret",""));v.put("secret_read",row.optBoolean("secretRead")?1:0);
            if(db.insertWithOnConflict("writings",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0)throw new Exception("这一天已有记录，请刷新查看");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static JSONArray all(ChatStore s)throws Exception{JSONArray a=new JSONArray();try(Cursor c=s.getReadableDatabase().rawQuery("SELECT kind,character,date,body,is_read,secret,secret_read FROM writings ORDER BY date DESC,id",null)){while(c.moveToNext())a.put(new JSONObject().put("kind",c.getString(0)).put("character",c.getString(1)).put("date",c.getString(2)).put("text",c.getString(3)).put("read",c.getInt(4)!=0).put("secret",c.getString(5)).put("secretRead",c.getInt(6)!=0));}return a;}
    static JSONArray events(ChatStore s)throws Exception{JSONArray a=new JSONArray();try(Cursor c=s.getReadableDatabase().rawQuery("SELECT character,date,body FROM writing_events ORDER BY date DESC,id",null)){while(c.moveToNext())a.put(new JSONObject().put("character",c.getString(0)).put("date",c.getString(1)).put("text",c.getString(2)));}return a;}
    static JSONArray visible(ChatStore s,JSONObject personal,boolean affinity)throws Exception{JSONArray a=all(s),out=new JSONArray();JSONObject relations=personal.optJSONObject("relations");for(int i=0;i<a.length();i++){JSONObject row=a.getJSONObject(i),r=relations==null?null:relations.optJSONObject(row.getString("character"));out.put(WritingData.visible(row,personal.getJSONObject("affinity").optDouble(row.getString("character"),50),r!=null&&r.optBoolean("cooling"),affinity));}return out;}
    static void read(ChatStore s,String kind,String who,String day){setRead(s,kind,who,day,true);}
    static void setRead(ChatStore s,String kind,String who,String day,boolean read){ContentValues v=new ContentValues();v.put("is_read",read?1:0);if(kind.equals("diary")&&!read)v.put("secret_read",0);s.getWritableDatabase().update("writings",v,"id=?",new String[]{WritingData.key(kind,who,day)});}
    static String context(ChatStore s,String who,String today)throws Exception{StringBuilder b=new StringBuilder();try(Cursor c=s.getReadableDatabase().rawQuery("SELECT date,body,is_read FROM writings WHERE character=? AND kind='letter' AND date>=? ORDER BY date DESC LIMIT 1",new String[]{who,java.time.LocalDate.parse(today).minusDays(14).toString()})){if(c.moveToFirst()){String text=WritingData.preview(c.getString(1));b.append("\n你在").append(c.getString(0)).append("给制作人写过信（").append(c.getInt(2)>0?"已读":"未读").append("），内容供回忆参考：").append(text.substring(0,Math.min(700,text.length())));}}return b.toString();}
}
