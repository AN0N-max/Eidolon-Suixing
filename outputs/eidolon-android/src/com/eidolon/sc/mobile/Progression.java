package com.eidolon.sc.mobile;
import org.json.*;
import java.util.*;

/** Deterministic rules ported from affinity, happenings and achievements. */
final class Progression {
    static final String[] IDS={"turns","call_time","gifts","tier","characters","intimate_characters","days","draws"};
    static final int[][] GOALS={{10,100,300,1000,3000},{600,3600,18000,72000},{1,10,30,100},{1,2},{3,10,29},{1,3,10},{7,30,100,365},{10,50,200,500}};
    static void upgrade(JSONObject d)throws Exception{
        if(!d.has("progress"))d.put("progress",new JSONObject().put("counts",new JSONObject()).put("unlocked",new JSONObject()).put("daily",new JSONObject().put("day",d.getString("day")).put("turns",0).put("gifts",0).put("claimed",new JSONObject())).put("lastDay","").put("seeded",false));
        if(!d.has("relations"))d.put("relations",new JSONObject());
        if(!d.has("sessions"))d.put("sessions",new JSONObject());
        if(!d.has("memoryEpoch"))d.put("memoryEpoch",0);
    }
    static void validate(JSONObject d)throws Exception{
        upgrade(d);
        JSONObject p=d.getJSONObject("progress"),counts=p.getJSONObject("counts"),unlocked=p.getJSONObject("unlocked");
        for(Iterator<String> it=counts.keys();it.hasNext();){String k=it.next();double n=counts.getDouble(k);if(!Double.isFinite(n)||n<0||n>1e9)throw new Exception("成就计数无效");}
        for(Iterator<String> it=unlocked.keys();it.hasNext();)if(!unlocked.getBoolean(it.next()))throw new Exception("奖励状态无效");
        JSONObject daily=p.getJSONObject("daily");java.time.LocalDate.parse(daily.getString("day"));daily.getJSONObject("claimed");
        if(daily.getInt("turns")<0||daily.getInt("gifts")<0||d.getLong("memoryEpoch")<0)throw new Exception("每日任务数据无效");
        JSONObject rel=d.getJSONObject("relations");for(Iterator<String> it=rel.keys();it.hasNext();){JSONObject r=rel.getJSONObject(it.next());if(r.getLong("at")<0||r.getDouble("gained")<0||r.getDouble("gained")>6)throw new Exception("关系状态无效");r.getBoolean("cooling");java.time.LocalDate.parse(r.getString("day"));}
        JSONObject sessions=d.getJSONObject("sessions");for(Iterator<String> it=sessions.keys();it.hasNext();){JSONObject s=sessions.getJSONObject(it.next());s.getBoolean("ended");if(s.getInt("out")<0)throw new Exception("会话状态无效");JSONArray hurt=s.getJSONArray("hurt");if(hurt.length()>8)throw new Exception("会话状态无效");for(int i=0;i<hurt.length();i++)if(hurt.getDouble(i)<0||hurt.getDouble(i)>15)throw new Exception("会话状态无效");}
    }
    static void refreshDaily(JSONObject d,String day)throws Exception{upgrade(d);JSONObject p=d.getJSONObject("progress");if(day.compareTo(p.getJSONObject("daily").getString("day"))>0)p.put("daily",new JSONObject().put("day",day).put("turns",0).put("gifts",0).put("claimed",new JSONObject()));}
    static JSONObject relation(JSONObject d,String who,long now)throws Exception{
        upgrade(d);JSONObject all=d.getJSONObject("relations"),r=all.optJSONObject(who);
        if(r==null){r=new JSONObject().put("at",now).put("gained",0).put("day",PersonalData.today()).put("cooling",false);all.put(who,r);}
        double value=d.getJSONObject("affinity").optDouble(who,50),delta=Math.max(0,now-r.getLong("at"))/86400000.0;
        if(value<50)value=Math.min(50,value+10*delta);
        d.getJSONObject("affinity").put(who,value);r.put("at",Math.max(now,r.getLong("at")));if(value>=10)r.put("cooling",false);
        if(PersonalData.today().compareTo(r.getString("day"))>0)r.put("day",PersonalData.today()).put("gained",0);
        return r;
    }
    static JSONObject session(JSONObject d,String id)throws Exception{
        upgrade(d);JSONObject all=d.getJSONObject("sessions"),s=all.optJSONObject(id);if(s==null){s=new JSONObject().put("ended",false).put("out",0).put("hurt",new JSONArray());all.put(id,s);}return s;
    }
    static JSONObject apply(JSONObject d,String who,String session,JSONObject assessment,long now)throws Exception{
        JSONObject r=relation(d,who,now),s=session(d,session);double before=d.getJSONObject("affinity").optDouble(who,50);
        int level=assessment.optInt("level",2);if(level<0||level>4)level=2;
        double step=new int[]{-5,-2,0,1,2}[level];boolean out=assessment.optBoolean("out_of_line"),manip=assessment.optBoolean("manipulation");
        if(manip)step=Math.min(step,0);if(out)step=-10;
        if(step>0)step=Math.min(step,Math.max(0,6-r.getDouble("gained")));
        double after=Math.max(0,Math.min(100,before+step));double priorGained=r.getDouble("gained");
        if(after<=0)r.put("cooling",true);d.getJSONObject("affinity").put(who,after);HeartScenes.gate(d,who,before);after=d.getJSONObject("affinity").getDouble(who);r.put("gained",priorGained+Math.max(0,after-before));
        JSONArray hurt=s.getJSONArray("hurt");double previousHurt=0;for(int i=0;i<hurt.length();i++)previousHurt+=hurt.getDouble(i);hurt.put(Math.max(0,before-after));while(hurt.length()>8)hurt.remove(0);double total=0;for(int i=0;i<hurt.length();i++)total+=hurt.getDouble(i);
        if(out)s.put("out",s.getInt("out")+1);
        boolean ended=r.getBoolean("cooling")||s.getInt("out")>=2||total>=12;s.put("ended",ended);
        return new JSONObject().put("tier",PersonalData.tier((int)after)).put("delta",after-before).put("warning",!ended&&total>=6&&previousHurt<6).put("ended",ended).put("cooling",r.getBoolean("cooling"));
    }
    static void add(JSONObject c,String key,int amount)throws Exception{c.put(key,Math.min(1000000000,c.optLong(key)+amount));}
    static void reward(JSONObject d,JSONObject done,String key,int amount)throws Exception{if(done.optBoolean(key))return;done.put(key,true);d.put("draws",d.getInt("draws")+amount);}
    static void happened(JSONObject d,String who,int turns,int gifts,int draws,String day)throws Exception{
        upgrade(d);JSONObject p=d.getJSONObject("progress"),c=p.getJSONObject("counts"),daily=p.getJSONObject("daily");
        if(day.compareTo(daily.getString("day"))>0){daily=new JSONObject().put("day",day).put("turns",0).put("gifts",0).put("claimed",new JSONObject());p.put("daily",daily);}
        if(day.equals(daily.getString("day"))&&(turns>0||gifts>0)){
            add(daily,"turns",turns);add(daily,"gifts",gifts);JSONObject claimed=daily.getJSONObject("claimed");
            if(daily.getInt("turns")>=1)reward(d,claimed,"one_turn",1);if(daily.getInt("gifts")>=1)reward(d,claimed,"one_gift",1);if(daily.getInt("turns")>=10)reward(d,claimed,"ten_turns",1);
        }
        if(who!=null){add(c,"turns/"+who,turns);add(c,"gifts/"+who,gifts);double score=d.getJSONObject("affinity").optDouble(who,50);c.put("tier/"+who,Math.max(c.optInt("tier/"+who),score>=85?2:score>=65?1:0));}
        add(c,"draws/",draws);if((turns>0||gifts>0)&&day.compareTo(p.getString("lastDay"))>0){add(c,"days/",1);p.put("lastDay",day);}
        unlock(d);
    }
    static void unlock(JSONObject d)throws Exception{
        JSONObject p=d.getJSONObject("progress"),counts=p.getJSONObject("counts"),done=p.getJSONObject("unlocked");Set<String> chars=new HashSet<>();int talked=0,intimate=0;
        for(Iterator<String> it=counts.keys();it.hasNext();){String k=it.next();int slash=k.indexOf('/');if(slash>=0&&slash<k.length()-1)chars.add(k.substring(slash+1));}
        for(String who:chars){if(counts.optInt("turns/"+who)>0)talked++;if(counts.optInt("tier/"+who)>=2)intimate++;}
        counts.put("characters/",talked).put("intimate_characters/",intimate);
        for(int i=0;i<IDS.length;i++)for(String who:i<4?chars:Collections.singleton("")){
            String key=IDS[i]+"/"+who;for(int j=0;j<GOALS[i].length;j++)if(counts.optDouble(key)>=GOALS[i][j])reward(d,done,key+"/"+(j+1),j+1);
        }
        for(String who:chars){boolean complete=true;for(int i=0;i<4;i++)for(int j=0;j<GOALS[i].length;j++)if(!done.optBoolean(IDS[i]+"/"+who+"/"+(j+1)))complete=false;
            if(complete&&!done.optBoolean("complete/"+who)){done.put("complete/"+who,true);JSONObject bag=d.getJSONObject("inventory");bag.put("reset-card",bag.optInt("reset-card")+1);}}
    }
    static JSONArray catalog()throws Exception{JSONArray out=new JSONArray();for(int i=0;i<IDS.length;i++){JSONArray goals=new JSONArray();for(int n:GOALS[i])goals.put(n);out.put(new JSONObject().put("id",IDS[i]).put("personal",i<4).put("goals",goals));}return out;}
}
