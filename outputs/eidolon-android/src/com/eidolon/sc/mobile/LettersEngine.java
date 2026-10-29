package com.eidolon.sc.mobile;

import android.content.*;
import android.database.Cursor;
import java.time.*;
import java.util.*;
import org.json.*;

/** Persisted local letter calendar. Refresh/delivery never call a model. */
final class LettersEngine {
    static volatile String inflight="";
    private static final Object LOCK=new Object();
    static boolean eligible(ChatStore s,JSONObject data,String who,long now,boolean affinity)throws Exception{
        if(s.lastActivity(who)<now-30L*86400000||s.hasBlockingPending(who))return false;
        List<ChatStore.Conversation> sessions=s.conversations(who);if(sessions.isEmpty()||Progression.session(data,sessions.get(0).id).optBoolean("ended"))return false;
        try(Cursor c=s.getReadableDatabase().rawQuery("SELECT 1 FROM messages WHERE conversation=? AND role='user' LIMIT 1",new String[]{sessions.get(0).id})){if(!c.moveToFirst())return false;}
        return !affinity||(data.getJSONObject("affinity").optDouble(who,50)>=40&&!Progression.relation(data,who,now).optBoolean("cooling"));
    }
    static int deliverAvailable(Context c,ChatStore store)throws Exception{
        if(!c.getSharedPreferences("settings",0).getBoolean("hibiLetters",false))return 0;
        synchronized(LOCK){return LetterPlanStore.deliver(store,System.currentTimeMillis(),WritingStore.epoch.get());}
    }
    static void refresh(Context c)throws Exception{
        SharedPreferences prefs=c.getSharedPreferences("settings",0);
        if(!prefs.getBoolean("hibiLetters",false)){LetterJob.cancel(c);return;}
        int arrived;
        synchronized(LOCK){try(ChatStore store=new ChatStore(c)){
            long epoch=WritingStore.epoch.get(),stamp=System.currentTimeMillis();JSONObject data=store.personal();Progression.upgrade(data);
            ZonedDateTime now=Instant.ofEpochMilli(stamp).atZone(ScheduleData.zone(data));LocalDate today=now.toLocalDate();
            arrived=LetterPlanStore.deliver(store,stamp,epoch);
            LetterPlanStore.prune(store,today);
            JSONArray rows=LetterPlanStore.all(store);
            for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(!r.getString("state").equals("pending")||LetterPlanStore.key(r).equals(inflight))continue;
                if(WritingStore.exists(store,"letter",r.getString("character"),r.getString("date"))||LetterPlanData.expired(r,stamp))LetterPlanStore.update(store,r,new JSONObject(r.toString()).put("state","skipped"),epoch);
            }
            JSONObject calendar=WritingEngine.asset(c,"calendar.json");JSONArray chars=WritingEngine.characters(c);Random random=new java.security.SecureRandom();boolean affinity=prefs.getBoolean("affinity",true);
            for(int i=0;i<chars.length();i++){String who=chars.getJSONObject(i).getString("id");if(!eligible(store,data,who,stamp,affinity))continue;
                for(LocalDate day:Arrays.asList(today,today.plusDays(1))){
                    if(LetterPlanStore.get(store,who+"/"+day)!=null)continue;
                    JSONObject plan=LetterPlanData.plan(who,day,now,calendar,data,data.getJSONObject("affinity").optDouble(who,50),affinity,WritingStore.lastLetter(store,who),random);
                    if(plan!=null)LetterPlanStore.insert(store,plan,epoch);
                }
            }
        }}
        reschedule(c);
        if(arrived>0){WebActivity activity=WebActivity.foregroundActivity;if(activity!=null)activity.runOnUiThread(()->{if(!activity.destroyed)activity.event("writingsChanged","");});}
    }
    /** Called with WritingEngine.running held; stages at most one letter per invocation. */
    static JSONObject generateNext(Context c,ChatStore store,JSONObject data,JSONArray characters,long epoch)throws Exception{
        SharedPreferences prefs=c.getSharedPreferences("settings",0);if(!prefs.getBoolean("hibiLetters",false))return null;
        if(epoch!=WritingStore.epoch.get())return null;
        long stamp=System.currentTimeMillis();List<JSONObject> rows=new ArrayList<>();JSONArray all=LetterPlanStore.all(store);
        for(int i=0;i<all.length();i++)rows.add(all.getJSONObject(i));rows.sort(Comparator.comparingLong(r->r.optLong("deliverAt")));
        for(JSONObject r:rows){
            if(!r.getString("state").equals("pending")||r.getLong("nextAttempt")>stamp||LetterPlanData.expired(r,stamp)||!eligible(store,data,r.getString("character"),stamp,prefs.getBoolean("affinity",true)))continue;
            if(LetterPlanStore.reserved(store,r.getString("date"))>=3){LetterPlanStore.update(store,r,new JSONObject(r.toString()).put("state","skipped"),epoch);continue;}
            inflight=LetterPlanStore.key(r);
            JSONObject attempt=new JSONObject(r.toString()).put("attempts",r.getInt("attempts")+1).put("nextAttempt",Math.min(stamp+LetterPlanData.RETRY,r.getLong("deliverAt")+LetterPlanData.HOUR));
            try{
                if(Thread.currentThread().isInterrupted()||!LetterPlanStore.update(store,r,attempt,epoch))return null;
                WritingEngine.generate(c,store,WritingEngine.character(characters,r.getString("character")),"letter",r.getString("date"),LetterPlanData.reason(r),false,attempt,epoch);
            }catch(Exception e){
                if(epoch==WritingStore.epoch.get()&&!Thread.currentThread().isInterrupted()&&(WritingEngine.active==null||!WritingEngine.active.cancelled)){
                    String error=e.getMessage()==null?"来信未写完，请检查后台模型":e.getMessage();if(error.length()>500)error=error.substring(0,500);
                    LetterPlanStore.update(store,attempt,new JSONObject(attempt.toString()).put("error",error),epoch);
                    prefs.edit().putString("letterError",error).apply();
                }
            }finally{inflight="";reschedule(c);}
            // Deliver only after the persisted target time, including an overdue generation.
            if(deliverAvailable(c,store)>0)return new JSONObject().put("kind","letter");
            return new JSONObject().put("staged",true);
        }return null;
    }
    static void reschedule(Context c){
        try(ChatStore store=new ChatStore(c)){
            if(!c.getSharedPreferences("settings",0).getBoolean("hibiLetters",false)){LetterJob.cancel(c);return;}
            long write=Long.MAX_VALUE,delivery=Long.MAX_VALUE;JSONArray rows=LetterPlanStore.all(store);long now=System.currentTimeMillis();JSONObject data=store.personal();Progression.upgrade(data);
            boolean affinity=c.getSharedPreferences("settings",0).getBoolean("affinity",true);
            for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);String state=r.getString("state");
                if(state.equals("ready"))delivery=Math.min(delivery,r.getLong("deliverAt"));
                if(state.equals("pending")&&!LetterPlanData.expired(r,now)&&!LetterPlanStore.key(r).equals(inflight)&&eligible(store,data,r.getString("character"),now,affinity))write=Math.min(write,r.getLong("nextAttempt"));
            }
            if(Vault.read(c).isEmpty()&&Vault.read(c,"backgroundKey").isEmpty())write=Long.MAX_VALUE;
            if(write!=Long.MAX_VALUE&&(WritingEngine.chatting()||WritingEngine.running.get()))write=Math.max(write,now+LetterPlanData.RETRY);
            LetterJob.schedule(c,LetterJob.WRITE,write,true);LetterJob.schedule(c,LetterJob.DELIVER,delivery,false);
        }catch(Exception e){c.getSharedPreferences("settings",0).edit().putString("letterError","来信任务暂未安排，打开应用后会继续检查。").apply();android.util.Log.w("EidolonLetters","Unable to restore letter jobs",e);}
    }
}
