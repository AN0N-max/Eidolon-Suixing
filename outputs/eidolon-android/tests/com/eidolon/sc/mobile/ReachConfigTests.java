package com.eidolon.sc.mobile;
import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONObject;

public final class ReachConfigTests {
    static int count;
    static void check(boolean result,String name){if(!result)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    interface Action{void run()throws Exception;}
    static void rejects(Action action,String name)throws Exception{try{action.run();}catch(Exception expected){check(true,name);return;}throw new AssertionError(name);}
    static long at(String time){return java.time.Instant.parse(time).toEpochMilli();}
    static ReachConfig config(Object... pairs)throws Exception{JSONObject o=new JSONObject();for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]);return new ReachConfig(o);}
    static SharedPreferences prefs(Map<String,Object> values,boolean fail){return (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(ReachConfigTests.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,m,args)->{
        String name=m.getName();if(name.equals("edit")){Map<String,Object> pending=new HashMap<>();Set<String> removals=new HashSet<>();return java.lang.reflect.Proxy.newProxyInstance(ReachConfigTests.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(editor,em,ea)->{
            if(em.getName().startsWith("put")){pending.put((String)ea[0],ea[1]);return editor;}if(em.getName().equals("remove")){removals.add((String)ea[0]);return editor;}
            if(em.getName().equals("commit")||em.getName().equals("apply")){if(!fail){for(String key:removals)values.remove(key);values.putAll(pending);}return em.getName().equals("commit")?!fail:null;}return editor;});}
        if(name.equals("getAll"))return new HashMap<>(values);if(name.equals("contains"))return values.containsKey(args[0]);if(name.startsWith("get"))return values.getOrDefault(args[0],args[1]);return null;
    });}
    public static void main(String[] args)throws Exception{
        ReachConfig defaults=new ReachConfig(null);
        check(defaults.integer("dailyLimit")==3&&defaults.integer("contactDays")==7&&defaults.integer("minAffinity")==40,"default reach eligibility preserves prior values");
        check(defaults.integer("attemptLimit")==10&&defaults.integer("backgroundMinutes")==15,"new request budget and Android polling floor have explicit defaults");
        Map<String,Object> legacy=new HashMap<>();legacy.put("reachMode","interval");legacy.put("reachHours",9);legacy.put("reachSound",false);legacy.put("reachWidth",380);
        ReachConfig migrated=ReachConfig.read(prefs(legacy,false));check(migrated.text("mode").equals("interval")&&migrated.number("hours")==9&&!migrated.flag("sound")&&migrated.integer("width")==380,"old local settings migrate without enabling or resetting features");
        ReachConfig custom=migrated.updated(new JSONObject().put("hours",.25).put("contactDays",90).put("popupSeconds",12.5));legacy.put(ReachConfig.KEY,custom.json().toString());ReachConfig restored=ReachConfig.read(prefs(legacy,false));
        check(restored.number("hours")==.25&&restored.integer("contactDays")==90&&restored.number("popupSeconds")==12.5&&!restored.flag("sound"),"fractional interval and popup duration survive persistence with omitted settings preserved");
        rejects(()->config("dailyLimit",1.5),"fractional quota rejected without truncation");rejects(()->config("backgroundMinutes",14),"periodic background work cannot request below fifteen minutes");
        rejects(()->config("hours",0),"zero-interval request loops rejected");rejects(()->config("trustMin",40,"trustMax",20),"inverted relation interval rejected");rejects(()->config("quietStart","24:00"),"invalid clock time rejected");
        rejects(()->config("quietStart","08:00","quietEnd","08:00"),"ambiguous full-day quiet interval rejected");rejects(()->config("zone","not-a-zone"),"invalid timezone rejected");rejects(()->config("sound","false"),"string cannot masquerade as boolean setting");
        rejects(()->config("memoryLimit",21),"unbounded memory context rejected");rejects(()->config("popupSeconds",61),"oversized popup duration rejected");
        long noon=at("2026-10-22T04:00:00Z");
        check(!ReachPolicy.allowed(defaults,39,noon-1000,noon,false,false)&&ReachPolicy.allowed(config("minAffinity",30),39,noon-1000,noon,false,false),"custom minimum affinity controls proactive eligibility");
        check(!ReachPolicy.allowed(defaults,90,noon-20*24*ReachPolicy.HOUR,noon,false,false)&&ReachPolicy.allowed(config("contactDays",30),90,noon-20*24*ReachPolicy.HOUR,noon,false,false),"contact activity window is configurable");
        check(!ReachPolicy.allowed(config("minAffinity",0),100,noon-1000,noon,true,false)&&!ReachPolicy.allowed(defaults,100,noon-1000,noon,false,true),"configuration cannot reopen ended or cooling conversations");
        check(!ReachPolicy.allowed(defaults,100,noon+1,noon,false,false),"clock rollback cannot make future contact eligible");
        ReachConfig intervals=config("quietEnabled",false,"trustMin",1,"trustMax",2,"closeMin",3,"closeMax",4,"intimateMin",5,"intimateMax",6);
        check(ReachPolicy.due(intervals,50,noon,0,0)==noon+ReachPolicy.HOUR&&ReachPolicy.due(intervals,70,noon,1,0)==noon+4*ReachPolicy.HOUR&&ReachPolicy.due(intervals,90,noon,0,0)==noon+5*ReachPolicy.HOUR,"each relationship range drives the shared runtime policy");
        check(ReachPolicy.due(config("mode","interval","hours",.25),90,noon,0,0)==noon+15*60000,"fractional fixed hours schedule exactly fifteen minutes");
        long night=at("2026-10-22T15:00:00Z");check(ReachPolicy.outsideQuiet(defaults,night,0)==at("2026-10-23T00:00:00Z"),"overnight quiet interval advances to next morning");
        ReachConfig dayQuiet=config("quietStart","11:00","quietEnd","13:00","spreadMinutes",0);check(ReachPolicy.quiet(dayQuiet,noon)&&ReachPolicy.outsideQuiet(dayQuiet,noon,0)==at("2026-10-22T05:00:00Z"),"same-day quiet interval advances without an extra day");
        check(!ReachPolicy.quiet(dayQuiet,at("2026-10-22T05:00:00Z")),"quiet end boundary is open for delivery");
        ReachConfig narrow=config("quietStart","08:01","quietEnd","08:00","spreadMinutes",240);long shifted=ReachPolicy.outsideQuiet(narrow,night,1);check(!ReachPolicy.quiet(narrow,shifted)&&shifted<at("2026-10-23T00:01:00Z"),"large wake spread never crosses back into quiet time");
        ReachConfig daily=config("mode","daily","time","23:00");check(ReachPolicy.due(daily,90,noon,0,0)==night&&!ReachPolicy.quiet(daily,night),"legacy daily time remains exempt from app quiet hours by default");
        long planned=ReachPolicy.plan(daily,90,noon,night+5000,0,0,0);check(planned==night,"first daily poll after target time does not postpone to tomorrow");
        check(ReachPolicy.plan(daily,90,noon,night+6000,planned,1,1)==planned,"repeated daily checks reuse persisted plan instead of rerandomizing");
        check(ReachPolicy.plan(daily,90,noon,night+6000,night+15*60000,1,1)==night+15*60000,"daily failure retry time survives the next scheduler poll");
        check(ReachPolicy.due(daily.updated(new JSONObject().put("dailyRespectsQuiet",true).put("spreadMinutes",0)),90,noon,0,0)==at("2026-10-23T00:00:00Z"),"daily time optionally observes configured quiet interval");
        ReachConfig tokyo=config("zone","Asia/Tokyo","mode","daily","time","13:30");check(ReachPolicy.due(tokyo,90,noon,0,0)==at("2026-10-22T04:30:00Z"),"daily clock uses selected timezone");
        check(ReachPolicy.dayStart(tokyo,noon)==at("2026-10-21T15:00:00Z"),"daily sent quota boundary uses selected timezone");
        ReachConfig ny=config("zone","America/New_York","mode","daily","time","09:00");check(ReachPolicy.due(ny,90,at("2026-03-07T17:00:00Z"),0,0)==at("2026-03-08T13:00:00Z"),"daily scheduling respects DST changes");
        JSONObject budget=ReachBudget.state("{\"day\":\"2026-10-23\",\"used\":4}","2026-10-22");check(budget.getInt("used")==4&&budget.getString("day").equals("2026-10-23"),"request budget cannot refill on clock rollback");
        check(ReachBudget.state(budget.toString(),"2026-10-24").getInt("used")==0,"new local date resets request budget");
        Map<String,Object> ledger=new HashMap<>();SharedPreferences p=prefs(ledger,false);ReachConfig two=config("attemptLimit",2);
        check(ReachBudget.reserve(p,two,noon)&&ReachBudget.reserve(p,two,noon)&&!ReachBudget.reserve(p,two,noon),"requests reserve persisted capacity before a third call can begin");
        check(ReachBudget.used(p,two,noon)==2&&ReachBudget.used(p,config("attemptLimit",1),noon)==2,"changing settings does not refill spent request capacity");
        rejects(()->ReachBudget.reserve(prefs(new HashMap<>(),true),two,noon),"failed durable reservation prevents a model call");
        ledger.put("reachRequestBudget","broken-json");check(ReachBudget.used(p,two,noon)==2,"corrupt request ledger fails closed instead of making calls");
        System.out.println("Passed "+count+" proactive settings checks. Fake preferences only; no API or Android runtime.");
    }
}
