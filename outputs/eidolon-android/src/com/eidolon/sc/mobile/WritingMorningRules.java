package com.eidolon.sc.mobile;
import java.time.*;
import org.json.JSONObject;
final class WritingMorningRules {
    static long nextCheck(ZonedDateTime now,String checkedDay,String checkedZone){ZonedDateTime start=DailyScheduleClock.boundary(now.toLocalDate(),now.getZone());if(!now.isBefore(start)&&(!now.toLocalDate().toString().equals(checkedDay)||!now.getZone().getId().equals(checkedZone)))return now.toInstant().toEpochMilli()+1000;return DailyScheduleClock.nextBoundary(now);}
    static long diaryAt(String day,ZoneId zone){return DailyScheduleClock.boundary(LocalDate.parse(day).plusDays(1),zone).toInstant().toEpochMilli();}
    static long nextDiary(String day,ZoneId zone,JSONObject attempt){return Math.max(diaryAt(day,zone),attempt==null?0:attempt.optLong("at")+DiaryData.RETRY);}
    static String diaryState(String day,LocalDate today,long now,ZoneId zone,boolean saved,boolean blocked,JSONObject attempt){if(saved)return "completed";if(LocalDate.parse(day).isBefore(today.minusDays(3)))return "expired";if(blocked)return "blocked";if(now<diaryAt(day,zone))return "planned";if(attempt!=null&&attempt.optInt("tries")>=2)return "failed";if(attempt!=null&&now<nextDiary(day,zone,attempt))return "retry";return "pending";}
    private WritingMorningRules(){}
}
