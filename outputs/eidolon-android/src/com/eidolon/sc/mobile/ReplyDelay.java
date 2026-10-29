package com.eidolon.sc.mobile;

import android.content.SharedPreferences;
import java.time.*;
import java.security.SecureRandom;
import org.json.JSONObject;

/** Local-only delay selection. It never calls a model or guesses a missing schedule. */
final class ReplyDelay {
    static final long SECOND=1000L, MINUTE=60*SECOND, MAX=24*60*60*1000L;
    static String category(JSONObject status){
        String state=status==null?"no_plan":status.optString("state","no_plan");
        if(state.equals("asleep")||state.equals("woken")||state.equals("awake")||state.equals("bedtime")||state.equals("bath"))return "rest";
        if(!state.equals("activity"))return "default";
        String text=status.optString("text","").toLowerCase(java.util.Locale.ROOT);
        if(text.matches(".*(吃饭|午餐|晚餐|早餐|用餐|便当|吃东西|meal|lunch|dinner|breakfast).*"))return "meal";
        if(text.matches(".*(排练|工作|课程|上课|录音|训练|拍摄|演出|会议|练习|rehearsal|work|class|lesson|practice).*"))return "work";
        return "default";
    }
    static long delay(SharedPreferences p,String category,double unit){
        String key=category.equals("free")?"rest":category;
        int min=p.getInt("replyDelay."+key+"Min",category.equals("meal")?30:category.equals("work")?120:5);
        int max=p.getInt("replyDelay."+key+"Max",category.equals("meal")?120:category.equals("work")?480:25);
        min=Math.max(0,Math.min(24*60*60,min));max=Math.max(min,Math.min(24*60*60,max));
        return (min+(long)Math.floor((max-min)*Math.max(0,Math.min(.999999,unit))))*SECOND;
    }
    static long wakeAt(JSONObject plan,JSONObject personal,long sentAt){
        if(plan==null)return -1;
        try{
            String wake=plan.getString("wake");int h=Integer.parseInt(wake.substring(0,2)),m=Integer.parseInt(wake.substring(3));
            ZoneId zone=ScheduleData.zone(personal);ZonedDateTime sent=Instant.ofEpochMilli(sentAt).atZone(zone);
            ZonedDateTime due=sent.toLocalDate().atTime(h,m).atZone(zone);if(!due.isAfter(sent))due=due.plusDays(1);return due.toInstant().toEpochMilli();
        }catch(Exception e){return -1;}
    }
    static long choose(SharedPreferences p,JSONObject status,JSONObject today,JSONObject personal,long sentAt){
        String category=category(status);
        if(status!=null&&status.optString("state").equals("asleep")&&p.getString("replyDelay.sleep","wake").equals("wake")){
            long expected=status.optLong("wakeAt",-1);if(expected>sentAt)return expected;
            JSONObject plan=status==null?null:status.optJSONObject("plan");long wake=wakeAt(plan,personal,sentAt);if(wake>sentAt)return wake;
        }
        long due=sentAt+delay(p,category,new SecureRandom().nextDouble());return respectBath(due,status,sentAt);
    }
    static long respectBath(long due,JSONObject status,long sentAt){return status!=null&&status.optString("state").equals("bath")?Math.max(due,status.optLong("bathEndsAt",sentAt)):due;}
    static long clamp(int value){return Math.max(0,Math.min(24*60*60,value));}
    private ReplyDelay(){}
}
