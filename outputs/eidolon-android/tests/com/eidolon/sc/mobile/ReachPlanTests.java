package com.eidolon.sc.mobile;
import org.json.*;
import java.time.*;
import java.util.*;
public final class ReachPlanTests {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    static ReachConfig config(Object... args)throws Exception{JSONObject o=new JSONObject();for(int i=0;i<args.length;i+=2)o.put((String)args[i],args[i+1]);return new ReachConfig(o);}
    static long at(String s){return ZonedDateTime.parse(s).toInstant().toEpochMilli();}
    static void reject(JSONObject input)throws Exception{boolean failed=false;try{new ReachConfig(input);}catch(Exception e){failed=true;}check(failed,"invalid plan config rejected: "+input);}
    public static void main(String[] args)throws Exception{
        ReachConfig c=config("mode","interval","hours",.25);long now=at("2026-10-26T03:00:00+08:00"),end=at("2026-10-27T00:00:00+08:00"),contact=now-3600000;List<Long> used=new ArrayList<>();
        long a=ReachPlanRules.choose(c,now,end,contact,80,123,used,t->true);
        check(a>=at("2026-10-26T08:00:00+08:00")&&a<at("2026-10-26T22:00:00+08:00"),"random plan excludes overnight quiet period");
        check(a==ReachPlanRules.choose(c,now,end,contact,80,123,used,t->true),"same input seed is deterministic");used.add(a);
        long b=ReachPlanRules.choose(c,now,end,contact,80,124,used,t->true);check(Math.abs(b-a)>=30*60000,"global minimum planned gap");
        check(ReachPlanRules.choose(c,now,end,contact,80,123,used,t->false)==-1,"no suitable activity window means no task");
        long min=at("2026-10-26T15:00:00+08:00"),max=at("2026-10-26T16:00:00+08:00");long work=ReachPlanRules.choose(c,now,end,contact,80,7,used,t->t>=min&&t<max);check(work>=min&&work<max,"role availability filter drives planned time");
        ReachConfig fixed=config("mode","daily","time","09:00");check(ReachPlanRules.choose(fixed,at("2026-10-26T12:00:00+08:00"),end,contact,80,1,used,t->true)==-1,"late enable does not replay missed fixed time");
        long daily=ReachPlanRules.choose(fixed,now,end,contact,80,1,Collections.emptyList(),t->true);check(daily>=at("2026-10-26T09:00:00+08:00")&&daily<at("2026-10-26T09:15:00+08:00"),"daily mode bounded random jitter");
        ReachConfig recent=config("mode","interval","hours",.25,"recentMinutes",120);long late=ReachPlanRules.choose(recent,now,end,now,80,23,Collections.emptyList(),t->true);check(late>=now+120*60000,"recent conversation grace");
        check(ReachPlanRules.choose(config("trustMin",48,"trustMax",48),now,end,now,50,1,Collections.emptyList(),t->true)==-1,"long relation interval can leave today empty");
        check(ReachPlanRules.nextMorning(c,now)==now+24*3600000,"03:00 schedules next day without a loop");
        check(ReachPlanRules.nextMorning(c,now-1)==now,"before 03:00 schedules current morning");
        ReachConfig tokyo=config("zone","Asia/Tokyo");check(ReachPolicy.local(tokyo,ReachPlanRules.nextMorning(tokyo,now)).getHour()==3,"planner clock uses configured zone");
        check(!ReachPlanRules.suitable(new JSONObject().put("state","asleep"))&&!ReachPlanRules.suitable(new JSONObject().put("state","bath"))&&!ReachPlanRules.suitable(new JSONObject().put("state","bedtime")),"sleep bath and bedtime excluded");
        check(!ReachPlanRules.suitable(new JSONObject().put("state","activity").put("text","在学校上课"))&&!ReachPlanRules.suitable(new JSONObject().put("state","activity").put("text","事务所排练")),"class and rehearsal excluded");
        check(ReachPlanRules.suitable(new JSONObject().put("state","free"))&&ReachPlanRules.suitable(new JSONObject().put("state","activity").put("text","逛街休息")),"free and suitable daily activities allowed");
        ReachConfig selected=config("restrictCharacters",true,"characters",new JSONArray().put("tenka"),"characterLimits",new JSONObject().put("tenka",2));check(ReachPlanRules.characterAllowed(selected,"tenka")&&!ReachPlanRules.characterAllowed(selected,"amana"),"selected character allowlist");check(ReachPlanRules.limit(selected,"tenka")==2&&ReachPlanRules.limit(selected,"amana")==3,"per-role limit with global fallback");
        check(!ReachPlanRules.characterAllowed(config("restrictCharacters",true),"tenka"),"empty restricted list allows nobody");
        reject(new JSONObject().put("characters",new JSONArray().put("tenka").put("tenka")));reject(new JSONObject().put("characterLimits",new JSONObject().put("tenka",0)));reject(new JSONObject().put("planGapMinutes",0));reject(new JSONObject().put("expiryMinutes",1441));reject(new JSONObject().put("notifications","true"));
        check(ReachExecution.normalize("<zh>你好，制作人！</zh>").equals("你好制作人"),"duplicate-content normalization strips tags and punctuation");
        JSONObject backup=CoreTests.backup().put("version",12).put("personal",PersonalData.fresh()).put("stories",new JSONArray()).put("writings",new JSONArray()).put("writingEvents",new JSONArray()).put("schedules",new JSONArray()).put("letterPlans",new JSONArray()).put("diaryAttempts",new JSONArray());
        JSONArray rows=backup.getJSONArray("conversations").getJSONObject(0).getJSONArray("messages");rows.getJSONObject(0).put("unread",false);rows.getJSONObject(1).put("unread",true);BackupValidator.validate(backup,new HashSet<>(Arrays.asList("mano")));check(rows.getJSONObject(1).getBoolean("unread"),"v12 backup preserves assistant unread");
        rows.getJSONObject(0).put("unread",true);boolean rejected=false;try{BackupValidator.validate(backup,new HashSet<>(Arrays.asList("mano")));}catch(Exception e){rejected=true;}check(rejected,"user message cannot carry unread state");rows.getJSONObject(0).put("unread",false);rows.getJSONObject(1).put("unread","true");rejected=false;try{BackupValidator.validate(backup,new HashSet<>(Arrays.asList("mano")));}catch(Exception e){rejected=true;}check(rejected,"string unread cannot bypass backup validation");
        System.out.println("PASS "+checks+" daily plan rules; no API or Android scheduler execution.");
    }
}
