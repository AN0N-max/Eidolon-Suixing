package com.eidolon.sc.mobile;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import org.json.*;

final class ReachPlans {
    static final Object LOCK=new Object();
    static long contactAt(ChatStore s,String session){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT updated FROM conversations WHERE id=?",new String[]{session})){return c.moveToFirst()?c.getLong(0):0;}}
    static boolean deliveryGap(ChatStore s,ReachConfig config,long now){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT coalesce(max(reach_at),0) FROM messages",null)){c.moveToFirst();long last=c.getLong(0);return last==0||now>=last&&now-last>=config.integer("planGapMinutes")*60000L;}}
    static String ineligible(Context c,ChatStore s,ReachConfig config,JSONObject personal,String who,String session,long now)throws Exception{
        if(!ReachPlanRules.characterAllowed(config,who))return "角色未在允许范围";
        List<ChatStore.Conversation> list=s.conversations(who);if(list.isEmpty()||!session.equals(list.get(0).id)||s.lastUserId(session)==0)return "会话不存在或已变更";
        if(Progression.session(personal,session).optBoolean("ended"))return "会话已结束";
        int score=c.getSharedPreferences("settings",0).getBoolean("affinity",true)?personal.getJSONObject("affinity").optInt(who,50):50;
        long contact=contactAt(s,session);if(contact<=0||contact>now||now-contact>config.integer("contactDays")*24*ReachPolicy.HOUR)return "近期没有互动";
        if(score<config.integer("minAffinity")||Progression.relation(personal,who,now).optBoolean("cooling"))return "关系条件尚未满足";
        if(!ReachOut.quotaAllowed(s,config,who,now))return "今日消息额度已满";
        JSONObject life=LifeStore.advance(c,s,personal,who,java.time.Clock.fixed(java.time.Instant.ofEpochMilli(now),java.time.ZoneOffset.UTC));if(life.optBoolean("enabled")&&life.optBoolean("eventOnlyReach")&&LifeData.cue(life,now)==null)return "当前没有未分享的生活事件";
        return "";
    }
    static long lastReach(ChatStore s,String who){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT coalesce(max(reach_at),0) FROM messages WHERE character=?",new String[]{who})){c.moveToFirst();return c.getLong(0);}}
    static boolean active(ChatStore s,String who){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT 1 FROM reach_tasks WHERE character=? AND status IN ('PENDING','RUNNING') LIMIT 1",new String[]{who})){return c.moveToFirst();}}
    static boolean handled(ChatStore s,String who,String day,String zone,long user,long revision){try(Cursor c=s.getReadableDatabase().rawQuery("SELECT 1 FROM reach_tasks WHERE character=? AND day=? AND zone=? AND (opportunity=? OR (opportunity=0 AND contact>=?)) AND (revision=? OR status IN ('COMPLETED','FAILED')) AND status<>'COMPLETED' AND NOT(status='SKIPPED' AND attempts=0 AND error=?) LIMIT 1",new String[]{who,day,zone,""+user,""+user,""+revision,ReachPlanStore.MANUAL})){return c.moveToFirst();}}
    static void upgradeAlgorithm(Context c,ChatStore s)throws Exception{synchronized(LOCK){SharedPreferences p=c.getSharedPreferences("settings",0);if(p.getInt("reachAlgorithm",0)>=2)return;ReachPlanStore.stop(s,"已切换均衡计划，旧的重复安排已替换",System.currentTimeMillis());if(!p.edit().putInt("reachAlgorithm",2).putLong("reachSettingsRevision",p.getLong("reachSettingsRevision",0)+1).commit())throw new Exception("计划升级保存失败");}}
    static void ensure(Context c,ChatStore s,long now)throws Exception{synchronized(LOCK){
        SharedPreferences p=c.getSharedPreferences("settings",0);if(!p.getBoolean("reachEnabled",false))return;ReachConfig config=ReachConfig.read(p);long revision=p.getLong("reachSettingsRevision",0);String day=ReachPolicy.date(config,now);SQLiteDatabase db=s.getWritableDatabase();
        db.beginTransaction();try{ReachPlanStore.expire(s,now);db.execSQL("INSERT OR IGNORE INTO reach_days VALUES(?,?,?,?)",new Object[]{day,config.text("zone"),revision,now});JSONObject personal=s.personal();Progression.upgrade(personal);JSONArray chars=WritingEngine.characters(c);List<JSONObject> candidates=new ArrayList<>();
            for(int i=0;i<chars.length();i++){String who=chars.getJSONObject(i).getString("id");List<ChatStore.Conversation> sessions=s.conversations(who);if(sessions.isEmpty()||active(s,who))continue;String session=sessions.get(0).id;long user=s.lastUserId(session);if(handled(s,who,day,config.text("zone"),user,revision)||!ineligible(c,s,config,personal,who,session,now).isEmpty())continue;candidates.add(new JSONObject().put("character",who).put("session",session).put("user",user).put("contact",contactAt(s,session)).put("sent",ReachOut.sent(s,config,who,now)).put("lastReach",lastReach(s,who)).put("score",p.getBoolean("affinity",true)?personal.getJSONObject("affinity").optInt(who,50):50));}
            ReachFairness.order(candidates,day);int budget=remaining(s,config,now);List<Long> occupied=occupied(s);long end=ReachPolicy.local(config,now).toLocalDate().plusDays(1).atStartOfDay(ReachPolicy.local(config,now).getZone()).toInstant().toEpochMilli();
            for(JSONObject candidate:candidates){if(budget<=0)break;String who=candidate.getString("character"),session=candidate.getString("session");long due=ReachPlanRules.choose(config,now,end,candidate.getLong("contact"),candidate.getInt("score"),(day+who+candidate.getLong("user")).hashCode(),occupied,availability(c,s,personal,who));if(due<0)continue;db.execSQL("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision,opportunity) VALUES(?,?,?,?,?,?,?,?,?,?)",new Object[]{UUID.randomUUID().toString(),who,session,day,config.text("zone"),due,Math.min(end,due+config.integer("expiryMinutes")*60000L),now,revision,candidate.getLong("user")});occupied.add(due);budget--;}
            db.execSQL("DELETE FROM reach_tasks WHERE status NOT IN ('PENDING','RUNNING') AND day<?",new Object[]{ReachPolicy.local(config,now).toLocalDate().minusDays(14).toString()});db.execSQL("DELETE FROM reach_days WHERE day<?",new Object[]{ReachPolicy.local(config,now).toLocalDate().minusDays(14).toString()});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }}
    static int remaining(ChatStore s,ReachConfig config,long now){
        try(Cursor rows=s.getReadableDatabase().rawQuery("SELECT count(*) FROM reach_tasks WHERE status IN ('PENDING','RUNNING') AND day=? AND zone=?",new String[]{ReachPolicy.date(config,now),config.text("zone")})){
            rows.moveToFirst();return Math.max(0,config.integer("dailyLimit")-ReachOut.sent(s,config,null,now)-rows.getInt(0));
        }
    }
    static JSONArray explain(Context c,ChatStore s,long now)throws Exception{JSONArray out=new JSONArray();SharedPreferences p=c.getSharedPreferences("settings",0);ReachConfig config=ReachConfig.read(p);JSONObject personal=s.personal();Progression.upgrade(personal);JSONArray chars=WritingEngine.characters(c);for(int i=0;i<chars.length();i++){String who=chars.getJSONObject(i).getString("id"),reason="";List<ChatStore.Conversation> sessions=s.conversations(who);if(!p.getBoolean("reachEnabled",false))reason="主动搭话已关闭";else if(active(s,who))reason="已有一条待执行或生成中的计划";else if(sessions.isEmpty()||s.lastUserId(sessions.get(0).id)==0)reason="还没有用户聊天记录";else{String session=sessions.get(0).id;reason=ineligible(c,s,config,personal,who,session,now);if(reason.isEmpty()&&handled(s,who,ReachPolicy.date(config,now),config.text("zone"),s.lastUserId(session),p.getLong("reachSettingsRevision",0)))reason="本轮机会已处理；回复她或查看执行记录";if(reason.isEmpty()){long end=ReachPolicy.local(config,now).toLocalDate().plusDays(1).atStartOfDay(ReachPolicy.local(config,now).getZone()).toInstant().toEpochMilli();int score=p.getBoolean("affinity",true)?personal.getJSONObject("affinity").optInt(who,50):50;long slot=ReachPlanRules.choose(config,now,end,contactAt(s,session),score,(ReachPolicy.date(config,now)+who+s.lastUserId(session)).hashCode(),occupied(s),availability(c,s,personal,who));reason=slot<0?ReachPlanRules.unavailable(config,now,end,contactAt(s,session),score,(ReachPolicy.date(config,now)+who+s.lastUserId(session)).hashCode(),occupied(s),availability(c,s,personal,who)):remaining(s,config,now)<=0?"今日名额已被其他待执行计划预留":"有可用时段，但尚未入队；请刷新计划查看检查结果";}}out.put(new JSONObject().put("character",who).put("reason",reason));}return out;}
    static List<Long> occupied(ChatStore s){List<Long> out=new ArrayList<>();try(Cursor r=s.getReadableDatabase().rawQuery("SELECT due FROM reach_tasks WHERE status IN ('PENDING','RUNNING','COMPLETED') UNION SELECT reach_at FROM messages WHERE reach_at>0",null)){while(r.moveToNext())out.add(r.getLong(0));}return out;}
    static ReachPlanRules.Available availability(Context c,ChatStore s,JSONObject personal,String who){
        SharedPreferences p=c.getSharedPreferences("settings",0);boolean enabled=p.getBoolean("hibiSchedules",false),rest=p.getBoolean("hibiRest",true);java.time.ZoneId zone=TemporalRuntime.zone(c,personal,who);Map<String,JSONObject[]> cache=new HashMap<>();
        return stamp->{if(!enabled)return true;java.time.ZonedDateTime at=java.time.Instant.ofEpochMilli(stamp).atZone(zone);String day=at.toLocalDate().toString();JSONObject[] plans=cache.get(day);if(plans==null){plans=new JSONObject[]{ScheduleStore.plan(s,who,day),ScheduleStore.plan(s,who,at.toLocalDate().minusDays(1).toString())};cache.put(day,plans);}return ReachPlanRules.suitable(ScheduleData.status(plans[0],plans[1],at,rest,0,0));};
    }
    static void reconcile(Context c,ChatStore s,long now)throws Exception{synchronized(LOCK){
        ReachConfig config=ReachConfig.read(c.getSharedPreferences("settings",0));JSONObject personal=s.personal();JSONArray rows=ReachPlanStore.today(s,config,now);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!row.getString("status").equals("PENDING"))continue;long due=row.getLong("due");String who=row.getString("character");if(ReachPlanRules.suitable(ScheduleRuntime.state(c,s,personal,who,due)))continue;
            // Changes invalidate only remaining tasks; never create extra chances or alter a running call.
            ReachPlanStore.skip(s,row.getString("id"),"日程变化，原时段不适合搭话；可手动重新安排",now);
        }
    }}
    private ReachPlans(){}
}
