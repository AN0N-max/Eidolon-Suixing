package com.eidolon.sc.mobile;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

/** Hibi anniversaries: first day counts as one; each hundred days and whole years. */
final class Anniversaries {
    static ZoneId zone(JSONObject personal){JSONObject p=personal.optJSONObject("producer");String id=p==null?"":p.optString("timeZone");return id.isEmpty()?ZoneId.systemDefault():ZoneId.of(id);}
    static void validate(JSONObject personal,Set<String> known)throws Exception{
        if(!personal.has("firstMeetings"))return;JSONObject rows=personal.getJSONObject("firstMeetings");if(rows.length()>100)throw new Exception("认识日期记录过多");
        for(Iterator<String> keys=rows.keys();keys.hasNext();){String who=keys.next();if(!who.matches("[a-z0-9_-]{1,80}")||(known!=null&&!known.contains(who)))throw new Exception("认识日期含未知角色");JSONObject row=rows.getJSONObject(who);String source=row.getString("source");if(source.equals("automatic")){long at=row.getLong("at");if(at<=0||at>253402214400000L)throw new Exception("首次认识时间无效");}else if(source.equals("manual")){LocalDate day=LocalDate.parse(row.getString("date"));if(day.getYear()<1900||day.getYear()>9999)throw new Exception("认识日期超出范围");}else throw new Exception("认识日期来源无效");}
    }
    static LocalDate start(JSONObject personal,String who){JSONObject rows=personal.optJSONObject("firstMeetings"),row=rows==null?null:rows.optJSONObject(who);if(row==null)return null;return row.optString("source").equals("automatic")?Instant.ofEpochMilli(row.optLong("at")).atZone(zone(personal)).toLocalDate():LocalDate.parse(row.optString("date"));}
    static boolean record(JSONObject personal,String who,long at,boolean hasHistory)throws Exception{
        JSONObject rows=personal.optJSONObject("firstMeetings");if(hasHistory||(rows!=null&&rows.has(who)))return false;
        if(rows==null){rows=new JSONObject();personal.put("firstMeetings",rows);}rows.put(who,new JSONObject().put("source","automatic").put("at",at));return true;
    }
    static void set(JSONObject personal,String who,String date,LocalDate today)throws Exception{
        LocalDate parsed=LocalDate.parse(date);if(parsed.getYear()<1900||parsed.isAfter(today))throw new Exception("认识日期应在 1900 年至今天之间");JSONObject rows=personal.optJSONObject("firstMeetings");if(rows==null){rows=new JSONObject();personal.put("firstMeetings",rows);}rows.put(who,new JSONObject().put("source","manual").put("date",parsed.toString()));
    }
    static long days(LocalDate start,LocalDate day){return start==null||day.isBefore(start)?0:ChronoUnit.DAYS.between(start,day)+1;}
    static JSONObject occurrence(LocalDate start,LocalDate day)throws Exception{
        long days=days(start,day);if(days==0)return null;int years=day.getYear()-start.getYear();boolean anniversary=years>0&&day.equals(start.withYear(day.getYear()));if(days%100!=0&&!anniversary)return null;
        return new JSONObject().put("days",days).put("years",anniversary?years:0).put("label",days%100==0?"认识第 "+days+" 天":"认识 "+years+" 周年");
    }
    static String lines(JSONObject personal,String who,LocalDate today)throws Exception{
        JSONObject event=occurrence(start(personal,who),today);if(event==null)return "";String text="";if(event.getLong("days")%100==0)text+="今天是你和制作人认识的第 "+event.getLong("days")+" 天。";if(event.getInt("years")>0)text+="今天是你和制作人认识的第 "+event.getInt("years")+" 周年。";return text;
    }
    static JSONObject summary(JSONObject personal,LocalDate today)throws Exception{
        JSONObject out=new JSONObject(),rows=personal.optJSONObject("firstMeetings");if(rows==null)return out;for(Iterator<String> keys=rows.keys();keys.hasNext();){String who=keys.next();LocalDate date=start(personal,who);out.put(who,new JSONObject().put("date",date.toString()).put("source",rows.getJSONObject(who).getString("source")).put("days",days(date,today)));}return out;
    }
    private Anniversaries(){}
}
