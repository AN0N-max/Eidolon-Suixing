package com.eidolon.sc.mobile;
import org.json.*;
import java.time.*;

public final class NightScheduleTests {
    static int n;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
    static ZonedDateTime at(String value){return ZonedDateTime.parse("2026-10-24T"+value+":00+08:00[Asia/Shanghai]");}
    static JSONObject plan(String wake,String sleep)throws Exception{return ScheduleTests.plan().put("wake",wake).put("sleep",sleep);}
    static JSONObject status(JSONObject today,JSONObject yesterday,String clock)throws Exception{
        ZonedDateTime now=at(clock);JSONObject result=ScheduleData.status(today,yesterday,now,true,0,0).put("enabled",true).put("timeZone","Asia/Shanghai").put("plan",today==null?JSONObject.NULL:today);
        JSONObject window=ScheduleData.sleepWindow(today,yesterday,now);if(window!=null)result.put("sleepWindow",window).put("wakeAt",window.getLong("end"));return result;
    }
    public static void main(String[] args)throws Exception{
        JSONObject tenka=plan("10:00","01:30");
        check(!ScheduleData.asleep(tenka,null,46),"missing yesterday does not put a 01:30 sleeper to bed at 00:46");
        check(status(tenka,null,"00:46").getString("state").equals("bedtime"),"Tenka at 00:46 is preparing for bed rather than asleep");
        check(!ScheduleData.asleep(tenka,null,89)&&ScheduleData.asleep(tenka,null,90),"late bedtime boundary changes exactly at 01:30");
        check(ScheduleData.asleep(tenka,null,599)&&!ScheduleData.asleep(tenka,null,600),"late sleeper remains asleep until her own 10:00 wake boundary");
        check(!ScheduleData.asleep(plan("09:00","02:00"),null,60)&&ScheduleData.asleep(plan("06:30","22:30"),null,60),"different characters do not share a midnight sleep cutoff");
        JSONObject window=ScheduleData.sleepWindow(tenka,null,at("00:46"));
        check(window.getLong("start")==at("01:30").toInstant().toEpochMilli()&&window.getLong("end")==at("10:00").toInstant().toEpochMilli(),"fallback night timestamps belong to this morning, not tomorrow");
        check(window.getBoolean("estimated")&&window.getString("sourceDate").equals("2026-10-23"),"missing prior plan is explicitly marked as an estimate");
        JSONObject previous=plan("08:00","23:00");JSONObject inherited=status(tenka,previous,"00:46");
        check(inherited.getString("state").equals("asleep")&&!inherited.getJSONObject("sleepWindow").getBoolean("estimated"),"an actual earlier previous-night plan takes precedence over today's later bedtime");
        check(inherited.getJSONObject("sleepWindow").getLong("start")==ZonedDateTime.parse("2026-10-23T23:00:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli(),"previous-night source and displayed sleep start agree");
        check(inherited.getLong("wakeAt")==at("10:00").toInstant().toEpochMilli(),"current day's waking time ends last night's sleep");
        check(status(tenka,plan("08:00","02:00"),"01:45").getString("state").equals("bedtime"),"yesterday's actual late bedtime is not replaced with today's 01:30");
        check(status(null,tenka,"02:00").getLong("wakeAt")==at("10:00").toInstant().toEpochMilli(),"missing today still exposes the known wake timestamp for delayed replies");
        check(ScheduleData.sleepWindow(null,tenka,at("14:00"))==null,"a missing day cannot invent a new evening plan");
        String prompt=ScheduleRuntime.describe(status(tenka,null,"00:46"),"夜猫子，常常1点以后才睡");
        check(prompt.contains("准备睡了")&&prompt.contains("10-24 01:30")&&prompt.contains("不要声称已经睡着")&&prompt.contains("夜猫子"),"chat context uses the same night timestamps and awake state as the page");
        check(!prompt.contains("制作人这条消息刚把你叫醒"),"late-night awake message cannot receive a forced just-woken instruction");
        String missing=ScheduleRuntime.describe(new JSONObject().put("enabled",true).put("state","no_plan"),"夜猫子");check(missing.contains("夜猫子")&&missing.contains("不统一按午夜"),"no-plan context preserves character habit without asserting a current sleep fact");
        check(ScheduleRuntime.describe(new JSONObject().put("enabled",false),"夜猫子").isEmpty(),"disabled schedule does not add sleep instructions");
        System.out.println("Passed "+n+" overnight schedule checks. No API or Android runtime.");
    }
}
