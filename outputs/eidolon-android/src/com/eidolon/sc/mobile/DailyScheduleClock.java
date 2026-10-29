package com.eidolon.sc.mobile;
import java.time.*;
/** Daily automatic checkpoint and schedule-view boundary; stored event dates remain calendar dates. */
final class DailyScheduleClock {
    static final int HOUR=3;
    static ZonedDateTime boundary(LocalDate day,ZoneId zone){return day.atTime(HOUR,0).atZone(zone);}
    static LocalDate displayDay(ZonedDateTime now){return now.isBefore(boundary(now.toLocalDate(),now.getZone()))?now.toLocalDate().minusDays(1):now.toLocalDate();}
    static long nextBoundary(ZonedDateTime now){LocalDate day=now.toLocalDate();ZonedDateTime next=boundary(day,now.getZone());if(!next.isAfter(now))next=boundary(day.plusDays(1),now.getZone());return next.toInstant().toEpochMilli();}
    private DailyScheduleClock(){}
}
