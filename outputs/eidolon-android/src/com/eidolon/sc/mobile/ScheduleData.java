package com.eidolon.sc.mobile;

import java.time.*;
import java.util.*;
import org.json.*;

/** Pure validation and clock rules for Hibi-style approximate daily schedules. */
final class ScheduleData {
    static final long HALF_HOUR=30*60*1000L;
    static final long MINUTE=60*1000L;
    static ZoneId zone(JSONObject personal){JSONObject p=personal.optJSONObject("producer");String id=p==null?"":p.optString("timeZone");return id.isEmpty()?ZoneId.systemDefault():ZoneId.of(id);}
    static int minute(String clock,boolean end)throws Exception{
        if(end&&clock.equals("24:00"))return 1440;
        if(!clock.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))throw new Exception("行程时间应为 HH:mm");
        return Integer.parseInt(clock.substring(0,2))*60+Integer.parseInt(clock.substring(3));
    }
    static JSONObject parse(String raw)throws Exception{
        String s=raw.trim();if(s.startsWith("```")){int line=s.indexOf('\n');if(line<0||!s.endsWith("```"))throw new Exception("行程 JSON 格式不完整");s=s.substring(line+1,s.length()-3).trim();}
        return normalize(new JSONObject(s));
    }
    static String text(JSONObject o,String field,int max,boolean required)throws Exception{
        Object raw=o.opt(field);if(raw==null&&!required)return "";if(!(raw instanceof String))throw new Exception("行程文本字段错误");String value=((String)raw).trim();if(value.length()>max||(required&&value.isEmpty()))throw new Exception("行程文本为空或过长");return value;
    }
    static JSONObject normalize(JSONObject raw)throws Exception{
        String wake=text(raw,"wake",5,true),sleep=text(raw,"sleep",5,true);int waking=minute(wake,false),sleeping=minute(sleep,false);
        if(waking<240||waking>720||(sleeping>240&&sleeping<1200))throw new Exception("起床需在 04:00–12:00，入睡需在 20:00–04:00");
        JSONArray source=raw.getJSONArray("slots"),slots=new JSONArray();if(source.length()<3||source.length()>6)throw new Exception("行程需要 3–6 段安排");int previous=0;
        for(int i=0;i<source.length();i++){JSONObject slot=source.getJSONObject(i);String start=text(slot,"start",5,true),end=text(slot,"end",5,true);int from=minute(start,false),to=minute(end,true);
            if(from<previous||from>=to||from<waking||(sleeping>=1200&&to>sleeping))throw new Exception("行程有重叠，或安排在睡眠时段");previous=to;
            slots.put(new JSONObject().put("start",start).put("end",end).put("activity",text(slot,"activity",80,true)).put("place",text(slot,"place",60,false)));
        }
        JSONArray input=raw.optJSONArray("twists"),twists=new JSONArray();if(input!=null){if(input.length()>2)throw new Exception("每天最多两个小变数");for(int i=0;i<input.length();i++){Object value=input.get(i);if(!(value instanceof String)||((String)value).trim().isEmpty()||((String)value).length()>120)throw new Exception("行程变数格式无效");twists.put(((String)value).trim());}}
        JSONObject result=new JSONObject().put("wake",wake).put("sleep",sleep).put("slots",slots).put("twists",twists);
        JSONObject bath=raw.optJSONObject("bath");if(bath!=null){String at=text(bath,"at",5,true);int start=minute(at,false),before=Math.floorMod(sleeping-start,1440),minutes=bath.optInt("minutes",-1);if(before<60||before>120||minutes<15||minutes>50||minutes%5!=0)throw new Exception("洗澡时间应在睡前60到120分钟，时长须为5分钟的倍数");String kind=text(bath,"kind",12,false);if(!kind.isEmpty()&&!kind.equals("tub")&&!kind.equals("shower"))throw new Exception("洗澡方式无效");result.put("bath",new JSONObject().put("at",at).put("minutes",minutes).put("kind",kind));}
        return result;
    }
    static JSONObject withBath(JSONObject plan,String bathKind,long seed)throws Exception{
        JSONObject result=new JSONObject(plan.toString());if(result.has("bath")&&!result.isNull("bath"))return normalize(result);
        int sleep=minute(result.getString("sleep"),false);Random random=new Random(seed);int before=60+random.nextInt(13)*5;
        boolean shower="淋浴".equals(bathKind)||"shower".equalsIgnoreCase(bathKind);int minutes=shower?15+random.nextInt(3)*5:30+random.nextInt(5)*5;
        int start=Math.floorMod(sleep-before,1440);String at=String.format(Locale.ROOT,"%02d:%02d",start/60,start%60);
        return normalize(result.put("bath",new JSONObject().put("at",at).put("minutes",minutes).put("kind",shower?"shower":"tub")));
    }
    static void validateEntry(JSONObject row,Set<String> characters)throws Exception{
        String who=row.getString("character");if(!characters.contains(who))throw new Exception("行程角色无效");LocalDate.parse(row.getString("date"));
        if(row.isNull("plan")){if(row.optString("error").isEmpty()||row.optString("error").length()>500)throw new Exception("行程失败记录无效");}
        else normalize(row.getJSONObject("plan"));
    }
    static void validateBackup(JSONObject data,Set<String> characters)throws Exception{
        JSONArray plans=data.getJSONArray("schedules");if(plans.length()>250)throw new Exception("行程备份数量过多");Set<String> seen=new HashSet<>();int size=0;
        for(int i=0;i<plans.length();i++){JSONObject row=plans.getJSONObject(i);validateEntry(row,characters);if(!seen.add(row.getString("character")+"/"+row.getString("date")))throw new Exception("行程日期重复");size+=row.toString().length();}if(size>1000000)throw new Exception("行程备份过大");
    }
    static boolean asleep(JSONObject today,JSONObject yesterday,int now)throws Exception{
        JSONObject known=today!=null?today:yesterday;if(known==null)return false;
        if(now<minute(known.getString("wake"),false)){
            JSONObject previous=yesterday!=null?yesterday:today;int slept=minute(previous.getString("sleep"),false);
            return slept>=1200||now>=slept;
        }
        return today!=null&&minute(today.getString("sleep"),false)>=1200&&now>=minute(today.getString("sleep"),false);
    }
    static JSONObject sleepWindow(JSONObject today,JSONObject yesterday,ZonedDateTime at)throws Exception{
        JSONObject known=today!=null?today:yesterday;if(known==null)return null;
        int now=at.getHour()*60+at.getMinute();boolean early=now<minute(known.getString("wake"),false);
        JSONObject night=early?(yesterday!=null?yesterday:today):today;if(night==null)return null;
        LocalDate source=at.toLocalDate().minusDays(early?1:0);int sleep=minute(night.getString("sleep"),false);
        LocalDate sleepDay=source.plusDays(sleep<720?1:0),wakeDay=source.plusDays(1);
        int wake=minute((early&&today!=null?today:night).getString("wake"),false);
        long start=sleepDay.atTime(sleep/60,sleep%60).atZone(at.getZone()).toInstant().toEpochMilli();
        long end=wakeDay.atTime(wake/60,wake%60).atZone(at.getZone()).toInstant().toEpochMilli();
        return new JSONObject().put("start",start).put("end",end).put("sourceDate",source.toString()).put("estimated",early&&yesterday==null).put("startText",Instant.ofEpochMilli(start).atZone(at.getZone()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))).put("endText",Instant.ofEpochMilli(end).atZone(at.getZone()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")));
    }
    static boolean asleepAt(JSONObject today,JSONObject yesterday,ZonedDateTime at)throws Exception{JSONObject w=sleepWindow(today,yesterday,at);long now=at.toInstant().toEpochMilli();return w!=null&&now>=w.getLong("start")&&now<w.getLong("end");}
    static String sleepKey(JSONObject today,JSONObject yesterday,ZonedDateTime at)throws Exception{
        JSONObject known=today!=null?today:yesterday;if(known==null)return "";int now=at.getHour()*60+at.getMinute();return (now<minute(known.getString("wake"),false)?at.toLocalDate().minusDays(1):at.toLocalDate()).toString();
    }
    static JSONObject status(JSONObject today,JSONObject yesterday,ZonedDateTime at,boolean rest,long first,long last)throws Exception{
        int now=at.getHour()*60+at.getMinute();JSONObject result=new JSONObject().put("state","no_plan").put("text","今天的行程尚未安排");
        if(rest){JSONObject bath=bathAt(today,yesterday,at);if(bath!=null){long began=bath.getLong("start"),ends=bath.getLong("end");if(at.toInstant().toEpochMilli()>=began&&at.toInstant().toEpochMilli()<ends)return result.put("state","bath").put("text","正在洗澡 🛁").put("bathEndsAt",ends).put("bathKind",bath.optString("kind"));}}
        if(rest&&asleepAt(today,yesterday,at)){
            long stamp=at.toInstant().toEpochMilli();boolean contacted=first>0&&last>=first&&stamp>=last&&stamp-last<HALF_HOUR;
            if(!contacted)return result.put("state","asleep").put("text","在睡觉 💤");
            if(stamp-first<HALF_HOUR)return result.put("state","woken").put("text","被你叫醒了");
            return result.put("state","awake").put("text","已经醒了");
        }
        if(rest){JSONObject known=today;if(yesterday!=null){int previous=minute(yesterday.getString("sleep"),false);if(previous<720&&now<previous)known=yesterday;}if(known!=null){int sleeping=minute(known.getString("sleep"),false),ahead=Math.floorMod(sleeping-now,1440);if(ahead>0&&ahead<=60)return result.put("state","bedtime").put("text","准备睡了");}}
        if(today==null)return result;
        JSONArray slots=today.getJSONArray("slots");for(int i=0;i<slots.length();i++){JSONObject slot=slots.getJSONObject(i);if(now>=minute(slot.getString("start"),false)&&now<minute(slot.getString("end"),true))return result.put("state","activity").put("text",slot.getString("activity")+(slot.optString("place").isEmpty()?"":" · "+slot.getString("place"))).put("until",slot.getString("end"));}
        return result.put("state","free").put("text","现在有空");
    }
    static JSONObject bathAt(JSONObject today,JSONObject yesterday,ZonedDateTime at)throws Exception{
        long now=at.toInstant().toEpochMilli();JSONObject[] plans={today,yesterday};for(int i=0;i<plans.length;i++){JSONObject plan=plans[i];if(plan==null)continue;JSONObject bath=plan.optJSONObject("bath");if(bath==null)continue;
            int clock=minute(bath.getString("at"),false);LocalDate day=at.toLocalDate().minusDays(i);if(clock<720)day=day.plusDays(1);
            long start=day.atTime(clock/60,clock%60).atZone(at.getZone()).toInstant().toEpochMilli(),end=start+bath.getInt("minutes")*MINUTE;
            if(now>=start&&now<end)return new JSONObject().put("start",start).put("end",end).put("kind",bath.optString("kind"));
        }return null;
    }
    static String summary(JSONObject plan)throws Exception{StringBuilder s=new StringBuilder();JSONArray slots=plan.getJSONArray("slots");for(int i=0;i<slots.length();i++){JSONObject slot=slots.getJSONObject(i);if(i>0)s.append("；");s.append(slot.getString("start")).append('–').append(slot.getString("end")).append(' ').append(slot.getString("activity"));}return s.toString();}
}
