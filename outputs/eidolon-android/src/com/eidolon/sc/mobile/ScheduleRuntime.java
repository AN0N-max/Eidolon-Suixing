package com.eidolon.sc.mobile;

import android.content.*;
import java.time.*;
import org.json.*;

final class ScheduleRuntime {
    static JSONObject state(Context context,ChatStore store,JSONObject personal,String who,long stamp)throws Exception{
        SharedPreferences p=context.getSharedPreferences("settings",0);ZonedDateTime at=Instant.ofEpochMilli(stamp).atZone(TemporalRuntime.zone(context,personal,who));String day=at.toLocalDate().toString();
        JSONObject row=ScheduleStore.entry(store,who,day),today=row==null?null:row.optJSONObject("plan"),yesterday=ScheduleStore.plan(store,who,at.toLocalDate().minusDays(1).toString());
        boolean enabled=p.getBoolean("hibiSchedules",false),rest=p.getBoolean("hibiRest",true);String key=ScheduleData.sleepKey(today,yesterday,at);
        long first=0,last=0;if(key.equals(p.getString("wakeDay/"+who,""))){first=p.getLong("wakeFirst/"+who,0);last=p.getLong("wakeLast/"+who,0);}
        JSONObject status=enabled?ScheduleData.status(today,yesterday,at,rest,first,last):new JSONObject().put("state","off").put("text","");
        JSONObject window=enabled&&rest?ScheduleData.sleepWindow(today,yesterday,at):null;if(window!=null){status.put("sleepWindow",window).put("wakeAt",window.getLong("end"));}
        status.put("enabled",enabled).put("restEnabled",rest).put("date",day).put("character",who).put("plan",today==null?JSONObject.NULL:today).put("error",row==null?"":row.optString("error")).put("timeZone",at.getZone().getId());
        return status;
    }
    static JSONObject view(Context c,ChatStore store,JSONObject personal,String who,long stamp)throws Exception{
        ZonedDateTime now=Instant.ofEpochMilli(stamp).atZone(TemporalRuntime.zone(c,personal,who));String day=DailyScheduleClock.displayDay(now).toString();
        JSONObject status=state(c,store,personal,who,stamp),row=ScheduleStore.entry(store,who,day);boolean previous=!day.equals(now.toLocalDate().toString());
        status.put("date",day).put("calendarDate",now.toLocalDate().toString()).put("showingPrevious",previous).put("nextDisplayAt",DailyScheduleClock.nextBoundary(now)).put("now",stamp)
            .put("plan",row==null||row.optJSONObject("plan")==null?JSONObject.NULL:row.getJSONObject("plan")).put("error",row==null?"":row.optString("error"));
        if(previous&&status.optString("state").equals("no_plan"))status.put("text","当前保留上一日安排，03:00 后切换新一天");
        return status;
    }
    static String prompt(Context c,ChatStore store,JSONObject personal,String who)throws Exception{
        return prompt(c,store,personal,who,System.currentTimeMillis());
    }
    static String prompt(Context c,ChatStore store,JSONObject personal,String who,long stamp)throws Exception{
        JSONObject status=state(c,store,personal,who,stamp);String habit="";try{JSONObject profile=WritingEngine.asset(c,"routines.json").getJSONObject("profiles").optJSONObject(who);if(profile!=null)habit=profile.optString("sleep");}catch(Exception ignored){}return describe(status,habit);
    }
    static String describe(JSONObject status,String habit)throws Exception{
        if(!status.getBoolean("enabled"))return "";
        String mode=status.getString("state");if(mode.equals("no_plan"))return "\n角色今天尚无明确行程，不知道此刻是否睡着，不统一按午夜推断入睡。"+(habit.isEmpty()?"":"角色平时作息习惯供参考（不是当前事实）："+habit);
        String message="\n角色在消息发送时的生活状态："+status.getString("text")+"。时间按"+status.getString("timeZone")+"计算。不同角色使用自己的日程，不能统一按午夜判断睡觉。这是应用生成的大致安排，只用于现在的行为，不作为原作剧情或已完成经历的证据。";
        JSONObject window=status.optJSONObject("sleepWindow");if(window!=null){java.time.format.DateTimeFormatter f=java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");ZoneId zone=ZoneId.of(status.getString("timeZone"));message+="\n本夜预计入睡："+Instant.ofEpochMilli(window.getLong("start")).atZone(zone).format(f)+"；预计起床："+Instant.ofEpochMilli(window.getLong("end")).atZone(zone).format(f)+"。"+(window.optBoolean("estimated")?"昨日日程缺失，本夜时间暂按这名角色已有日程估计，不当作已经发生的事实。":"本夜延续对应日期的日程，不把今天凌晨的入睡时刻误读成已经过了昨晚的入睡时刻。");}
        if(mode.equals("bath"))message+="制作人的这条消息是在你洗澡时发来的；回复时你已经洗完了，可以自然地先说一句刚洗完或头发还没干，再接着聊天。只表达日常状态，不描述洗澡过程或身体。";
        else if(mode.equals("asleep"))message+="制作人这条消息刚把你叫醒，可以稍显困倦，但照常回答，不声称已经等了很久。";
        else if(mode.equals("woken"))message+="刚被叫醒不久，语气可以稍显困倦，不要每条回复重复说自己被吵醒。";
        else if(mode.equals("awake"))message+="你已经聊醒了，正常交流，不再反复说睡觉或被叫醒。";
        else if(mode.equals("bedtime"))message+="离睡觉较近，可以自然流露一点困意，尊重制作人是否继续聊天。";
        if(mode.equals("bedtime")||mode.equals("free")||mode.equals("activity"))message+="现在没有处于睡眠状态，不要声称已经睡着、刚被叫醒或已经睡了一觉；可以按活动及角色习惯继续交流。";
        if(!habit.isEmpty())message+="\n这名角色平时的作息习惯（在日程未说明时参考，不能覆盖上面的当前状态）："+habit;
        JSONObject plan=status.optJSONObject("plan");if(plan!=null){message+="\n今天原本的大致安排（不证明实际完成）："+ScheduleData.summary(plan)+"。没有覆盖的时间是自由安排，不强行编造新任务。";JSONObject bath=plan.optJSONObject("bath");if(bath!=null)message+="睡前原本还计划约在"+bath.optString("at")+"洗澡，时长约"+bath.optInt("minutes")+"分钟；这只是计划，不表示一定照做。";}
        return message;
    }
    static void contact(Context context,ChatStore store,JSONObject personal,String who,long stamp)throws Exception{
        SharedPreferences p=context.getSharedPreferences("settings",0);if(!p.getBoolean("hibiSchedules",false)||!p.getBoolean("hibiRest",true))return;
        ZonedDateTime at=Instant.ofEpochMilli(stamp).atZone(TemporalRuntime.zone(context,personal,who));JSONObject today=ScheduleStore.plan(store,who,at.toLocalDate().toString()),yesterday=ScheduleStore.plan(store,who,at.toLocalDate().minusDays(1).toString());
        if(!ScheduleData.asleepAt(today,yesterday,at))return;
        String key=ScheduleData.sleepKey(today,yesterday,at);long first=p.getLong("wakeFirst/"+who,0),last=p.getLong("wakeLast/"+who,0);
        if(!key.equals(p.getString("wakeDay/"+who,""))||first<=0||stamp<last||stamp-last>=ScheduleData.HALF_HOUR)first=stamp;
        p.edit().putString("wakeDay/"+who,key).putLong("wakeFirst/"+who,first).putLong("wakeLast/"+who,stamp).apply();
    }
    static String diaryContext(ChatStore s,String who,String date)throws Exception{JSONObject plan=ScheduleStore.plan(s,who,date);return plan==null?"":"\n那天的原计划仅供背景，不代表确实完成："+ScheduleData.summary(plan);}
}
