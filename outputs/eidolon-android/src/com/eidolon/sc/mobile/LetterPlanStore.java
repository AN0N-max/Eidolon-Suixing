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
    static boolean markRequested(ChatStore s,JSONObject plan,long expected)throws Exception{if(plan.optBoolean("requestAttempted"))return false;JSONObject before=new JSONObject(plan.toString()),next=new JSONObject(plan.toString()).put("requestAttempted",true);if(!update(s,before,next,expected))return false;for(java.util.Iterator<String> it=next.keys();it.hasNext();){String key=it.next();plan.put(key,next.get(key));}return true;}
    static boolean ready(ChatStore s,JSONObject old,String text,long epoch)throws Exception{return ready(s,old,text,epoch,null);}
    static boolean ready(ChatStore s,JSONObject old,String text,long epoch,JSONObject frame)throws Exception{return ready(s,old,text,epoch,frame,null);}
    static boolean ready(ChatStore s,JSONObject old,String text,long epoch,JSONObject frame,JSONObject exchange)throws Exception{
        SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
            if(epoch!=WritingStore.epoch.get()||!old.getString("state").equals("pending")||reserved(s,old.getString("date"))>=3)return false;
            String conflict=frame==null?"":ContinuityContext.revalidate(frame,LifeStore.read(s,old.getString("character")),s.personal());JSONObject next=new JSONObject(old.toString()).put("state",conflict.isEmpty()?"ready":"skipped").put("text",conflict.isEmpty()?text:"").put("error",conflict.isEmpty()?"":conflict+"；未自动重新生成");if(frame!=null)next.put("continuityFrame",frame);if(exchange!=null)next.put("exchangeEffects",exchange);
            boolean saved=update(s,old,next,epoch);db.setTransactionSuccessful();return saved;
        }finally{db.endTransaction();}
    }
    static int deliver(ChatStore s,long now,long epoch)throws Exception{return deliver(null,s,now,epoch);}
    static int deliver(android.content.Context context,ChatStore s,long now,long epoch)throws Exception{
        int delivered=0;JSONArray rows=all(s);for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);if(!r.getString("state").equals("ready")||r.getLong("deliverAt")>now)continue;
            SQLiteDatabase db=s.getWritableDatabase();db.beginTransaction();try{
                JSONObject current=get(s,key(r));if(epoch!=WritingStore.epoch.get()||current==null||current.getLong("revision")!=r.getLong("revision"))continue;
                String who=r.getString("character"),day=r.getString("date");if(context!=null&&r.optJSONObject("exchangeEffects")==null){update(s,r,new JSONObject(r.toString()).put("state","skipped").put("text","").put("error","旧版来信缺少当前交流依据，本次不补投、不重新生成"),epoch);db.setTransactionSuccessful();continue;}String conflict=r.optJSONObject("continuityFrame")==null?"":ContinuityContext.revalidate(r.getJSONObject("continuityFrame"),LifeStore.read(s,who),s.personal());if(conflict.isEmpty()&&r.optJSONObject("exchangeEffects")!=null){JSONObject ef=r.getJSONObject("exchangeEffects").optJSONObject("exchangeFrame");conflict=context==null?ExchangeIntent.guard(ef,LifeStore.read(s,who),ExchangeStore.lastUser(s,who),s.personal().optLong("memoryEpoch"),null,now):ExchangeStore.guard(context,s,ef,now);}if(!conflict.isEmpty()){update(s,r,new JSONObject(r.toString()).put("state","skipped").put("text","").put("error",conflict+"；未自动重新生成"),epoch);db.setTransactionSuccessful();continue;}boolean exists=WritingStore.exists(s,"letter",who,day),full=WritingStore.countLetters(s,day)>=3;
                try(Cursor capacity=db.rawQuery("SELECT count(*),coalesce(sum(length(body)+length(secret)),0) FROM writings",null)){capacity.moveToFirst();full|=capacity.getInt(0)>=500||capacity.getLong(1)+r.getString("text").length()>2000000;}
                JSONObject next=new JSONObject(r.toString()).put("state",full&&!exists?"skipped":"delivered").put("text","");
                if(!update(s,r,next,epoch))continue;
                if(!exists&&!full){WritingStore.save(s,new JSONObject().put("kind","letter").put("character",who).put("date",day).put("text",r.getString("text")).put("read",false).put("exchangeEffects",r.optJSONObject("exchangeEffects")),epoch);delivered++;}
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }return delivered;
    }
    static void prune(ChatStore s,java.time.LocalDate today)throws Exception{
        JSONArray rows=all(s);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(!r.getString("state").equals("ready")&&r.getString("date").compareTo(today.minusDays(35).toString())<0)s.getWritableDatabase().delete("letter_plans","id=? AND revision=?",new String[]{key(r),""+r.getLong("revision")});}
    }
}
