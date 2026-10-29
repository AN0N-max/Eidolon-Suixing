package com.eidolon.sc.mobile;

import java.time.*;
import java.util.*;
import org.json.*;

/** Local time selection only. No model request or chat mutation. */
final class ReachPlanRules {
    interface Available { boolean at(long time)throws Exception; }
    static final long MINUTE=60000L;
    static boolean characterAllowed(ReachConfig c,String who){
        if(!c.flag("restrictCharacters"))return true;JSONArray ids=c.jsonArray("characters");for(int i=0;i<ids.length();i++)if(who.equals(ids.optString(i)))return true;return false;
    }
    static int limit(ReachConfig c,String who){return c.object("characterLimits").optInt(who,c.integer("characterDailyLimit"));}
    static boolean suitable(JSONObject state){
        String mode=state.optString("state");if(Arrays.asList("asleep","bath","bedtime").contains(mode))return false;
        return !mode.equals("activity")||!state.optString("text").matches("(?s).*(工作|排练|练习|训练|上课|课程|考试|录制|拍摄|演出|直播|开会|通告).*" );
    }
    static boolean spaced(long at,List<Long> occupied,long gap){for(long n:occupied)if(Math.abs(at-n)<gap)return false;return true;}
    static long choose(ReachConfig c,long now,long end,long contact,int score,long seed,List<Long> occupied,Available available)throws Exception{
        Random random=new Random(seed);long first=Math.max(now+5*MINUTE,contact+c.integer("recentMinutes")*MINUTE),last=end;
        if(c.text("mode").equals("daily")){
            ZonedDateTime local=ReachPolicy.local(c,now);long fixed=local.toLocalDate().atTime(LocalTime.parse(c.text("time"))).atZone(local.getZone()).toInstant().toEpochMilli();
            // A fixed time already missed is not replayed when enabling in the evening.
            if(fixed<now)return -1;first=Math.max(first,fixed);last=Math.min(last,fixed+15*MINUTE);
        }else{
            String tier=score>=85?"intimate":score>=65?"close":"trust";
            double hours=c.text("mode").equals("interval")?c.number("hours"):c.number(tier+"Min")+random.nextDouble()*(c.number(tier+"Max")-c.number(tier+"Min"));
            first=Math.max(first,contact+(long)(hours*ReachPolicy.HOUR));
        }
        List<Long> candidates=new ArrayList<>();first=((first+5*MINUTE-1)/(5*MINUTE))*(5*MINUTE);
        for(long at=first;at<last;at+=5*MINUTE)if(!ReachPolicy.quiet(c,at)&&spaced(at,occupied,c.integer("planGapMinutes")*MINUTE)&&available.at(at))candidates.add(at);
        return candidates.isEmpty()?-1:candidates.get(random.nextInt(candidates.size()));
    }
    static String unavailable(ReachConfig c,long now,long end,long contact,int score,long seed,List<Long> occupied,Available available)throws Exception{
        if(c.text("mode").equals("daily")){
            ZonedDateTime local=ReachPolicy.local(c,now);long fixed=local.toLocalDate().atTime(LocalTime.parse(c.text("time"))).atZone(local.getZone()).toInstant().toEpochMilli();
            if(fixed<now)return "今天设定的固定时间已过，今天不补发";
        }
        if(Math.max(now+5*MINUTE,contact+c.integer("recentMinutes")*MINUTE)>=end)return "近期聊天的冷却时间跨到明天，今天不再安排";
        if(!c.text("mode").equals("daily")){
            String tier=score>=85?"intimate":score>=65?"close":"trust";
            double hours=c.text("mode").equals("interval")?c.number("hours"):c.number(tier+"Min")+new Random(seed).nextDouble()*(c.number(tier+"Max")-c.number(tier+"Min"));
            if(contact+(long)(hours*ReachPolicy.HOUR)>=end)return "本次关系或固定间隔尚未结束，最早明天再检查";
        }
        if(choose(c,now,end,contact,score,seed,Collections.emptyList(),available)>=0)return "可用空档与其他主动消息的最小间距冲突";
        if(choose(c,now,end,contact,score,seed,Collections.emptyList(),t->true)>=0)return "间隔满足后的空档均处于睡眠、洗澡或忙碌时段";
        return "间隔满足后已无符合免打扰或固定时间窗口的空档";
    }
    static long nextMorning(ReachConfig c,long now){return DailyScheduleClock.nextBoundary(ReachPolicy.local(c,now));}
    private ReachPlanRules(){}
}
