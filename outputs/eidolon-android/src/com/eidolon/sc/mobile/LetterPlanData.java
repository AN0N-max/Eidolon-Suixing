package com.eidolon.sc.mobile;

import java.time.*;
import java.util.*;
import org.json.*;

/** Local adaptation of Hibi letters.py: decide once, write ahead, deliver later. No model calls. */
final class LetterPlanData {
    static final long HOUR=3600000L, RETRY=5*60000L;
    static boolean birthday(int month,int day,LocalDate date){
        if(month==2&&day==29&&!date.isLeapYear())day=28;
        return month==date.getMonthValue()&&day==date.getDayOfMonth();
    }
    static boolean producerBirthday(JSONObject data,LocalDate day){JSONObject p=data.optJSONObject("producer");return p!=null&&birthday(p.optInt("birthMonth"),p.optInt("birthDay"),day);}
    static boolean close(double score,boolean affinity){return !affinity||score>=65;}
    static JSONObject plan(String who,LocalDate day,ZonedDateTime now,JSONObject calendar,JSONObject data,double score,boolean affinity,String lastLetter,Random random)throws Exception{
        boolean close=close(score,affinity);LocalDate today=now.toLocalDate();
        if(day.isAfter(today)&&(!day.equals(today.plusDays(1))||!producerBirthday(data,day)||!close))return null;
        JSONObject row=new JSONObject().put("character",who).put("date",day.toString()).put("zone",now.getZone().getId()).put("occasion","").put("about","")
            .put("writeAt",0L).put("deliverAt",0L).put("nextAttempt",0L).put("attempts",0).put("state","none").put("text","").put("error","").put("revision",0L);
        if(affinity&&score<40)return row;
        String occasion="",about="";
        if(producerBirthday(data,day)&&close)occasion="producer_birthday";
        JSONArray birthdays=calendar.getJSONArray("birthdays");
        for(int i=0;occasion.isEmpty()&&i<birthdays.length();i++){JSONObject b=birthdays.getJSONObject(i);String[] parts=b.getString("date").split("-");if(who.equals(b.getString("character"))&&birthday(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),day))occasion="her_birthday";}
        JSONArray holidays=calendar.getJSONArray("holidays");String mmdd=String.format(Locale.ROOT,"%02d-%02d",day.getMonthValue(),day.getDayOfMonth());
        for(int i=0;occasion.isEmpty()&&close&&i<holidays.length();i++){JSONObject h=holidays.getJSONObject(i);String date=h.optJSONObject("dates")==null?h.optString("date"):h.getJSONObject("dates").optString(""+day.getYear());if(mmdd.equals(date)&&!h.has("end")){occasion="holiday";about=h.getString("name")+"。"+h.optString("note");}}
        if(occasion.isEmpty()){
            if(!lastLetter.isEmpty()&&!LocalDate.parse(lastLetter).isBefore(day.minusDays(4)))return row;
            if(random.nextInt(!affinity?20:score>=85?7:score>=65?12:20)!=0)return row;
            occasion="ordinary";
        }
        ZonedDateTime midnight=day.atStartOfDay(now.getZone()),delivery;long lead;
        if(occasion.equals("producer_birthday")&&now.isBefore(midnight)){delivery=midnight;lead=3*HOUR;}
        else{
            int begin=occasion.equals("producer_birthday")?8:occasion.equals("ordinary")?9:19;
            int end=occasion.equals("producer_birthday")?11:occasion.equals("ordinary")?21:22;
            ZonedDateTime start=day.atTime(begin,0).atZone(now.getZone()),finish=day.atTime(end,0).atZone(now.getZone());
            if(now.isAfter(start))start=now;if(!start.isBefore(finish))return row;
            delivery=start.plusNanos(Duration.between(start,finish).toNanos()*random.nextInt(1000)/1000);
            lead=occasion.equals("producer_birthday")?3*HOUR:occasion.equals("ordinary")?HOUR/2:HOUR;
        }
        long due=delivery.toInstant().toEpochMilli(),stamp=now.toInstant().toEpochMilli(),write=Math.max(due-lead,Math.min(stamp,due));
        return row.put("occasion",occasion).put("about",about).put("state","pending").put("writeAt",write).put("deliverAt",due).put("nextAttempt",write);
    }
    static boolean expired(JSONObject row,long now){return now>row.optLong("deliverAt")+HOUR||row.optInt("attempts")>=3;}
    static String reason(JSONObject row){
        String kind=row.optString("occasion");
        return kind.equals("producer_birthday")?"给制作人的生日祝福信。":kind.equals("her_birthday")?"今天是角色自己的生日，可以表达真实感受；没有记录的庆祝活动不要编造。":kind.equals("holiday")?"节日来信："+row.optString("about"):"普通来信，分享最近真实的心情。";
    }
    static void validate(JSONObject r,Set<String> known)throws Exception{
        String who=r.getString("character"),state=r.getString("state"),occasion=r.getString("occasion");LocalDate day=LocalDate.parse(r.getString("date"));ZoneId zone=ZoneId.of(r.getString("zone"));
        if(!known.contains(who)||!Arrays.asList("none","pending","ready","delivered","skipped").contains(state)||!Arrays.asList("","producer_birthday","her_birthday","holiday","ordinary").contains(occasion))throw new Exception("来信计划类型或角色无效");
        String text=r.getString("text");if(r.getString("about").length()>1500||r.getString("error").length()>500||text.length()>12000||r.getInt("attempts")<0||r.getInt("attempts")>3||r.getLong("revision")<0)throw new Exception("来信计划内容无效");
        long write=r.getLong("writeAt"),due=r.getLong("deliverAt"),retry=r.getLong("nextAttempt");
        if(state.equals("none")){if(!occasion.isEmpty()||write!=0||due!=0||retry!=0||!text.isEmpty())throw new Exception("空来信计划无效");}
        else if(occasion.isEmpty()||write<=0||write>due||retry<write||retry>due+HOUR||!Instant.ofEpochMilli(due).atZone(zone).toLocalDate().equals(day))throw new Exception("来信计划时间无效");
        if(state.equals("ready")?text.trim().isEmpty():!text.isEmpty())throw new Exception("来信计划暂存内容无效");
    }
    static void validateBackup(JSONObject data,Set<String> known)throws Exception{
        JSONArray rows=data.getJSONArray("letterPlans");if(rows.length()>1200)throw new Exception("来信计划过多");Set<String> seen=new HashSet<>();int size=0;
        for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);validate(r,known);if(!seen.add(r.getString("character")+"/"+r.getString("date")))throw new Exception("来信计划日期重复");size+=r.toString().length();}
        if(size>2500000)throw new Exception("来信计划备份过大");
    }
}
