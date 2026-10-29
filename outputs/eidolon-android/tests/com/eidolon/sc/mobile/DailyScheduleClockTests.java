package com.eidolon.sc.mobile;
import java.time.*;
public final class DailyScheduleClockTests {
 static int n;static void check(boolean value,String text){if(!value)throw new AssertionError(text);n++;System.out.println("PASS "+text);}
 static ZonedDateTime at(String value){return ZonedDateTime.parse(value);}
 public static void main(String[] args){
  for(String time:new String[]{"00:00:00","01:30:00","02:59:59"}){ZonedDateTime z=at("2026-10-29T"+time+"+08:00[Asia/Shanghai]");check(DailyScheduleClock.displayDay(z).toString().equals("2026-10-28"),"previous schedule retained at "+time);check(DailyScheduleClock.nextBoundary(z)==at("2026-10-29T03:00:00+08:00").toInstant().toEpochMilli(),"next view boundary is local three");}
  ZonedDateTime three=at("2026-10-29T03:00:00+08:00[Asia/Shanghai]");check(DailyScheduleClock.displayDay(three).toString().equals("2026-10-29"),"exactly 03:00 switches to calendar day");
  check(DailyScheduleClock.nextBoundary(three)==three.plusDays(1).toInstant().toEpochMilli(),"boundary does not create a refresh loop");
  check(DailyScheduleClock.displayDay(at("2027-01-01T02:00:00+08:00")).toString().equals("2026-12-31"),"year rollover retains previous date");
  check(DailyScheduleClock.displayDay(at("2028-03-01T02:00:00+08:00")).toString().equals("2028-02-29"),"leap day handled");
  Instant instant=Instant.parse("2026-10-28T18:30:00Z");check(DailyScheduleClock.displayDay(instant.atZone(ZoneId.of("Asia/Shanghai"))).toString().equals("2026-10-28")&&DailyScheduleClock.displayDay(instant.atZone(ZoneId.of("Asia/Tokyo"))).toString().equals("2026-10-29"),"profile timezone controls display date");
  ZonedDateTime gap=ZonedDateTime.of(2026,3,29,4,30,0,0,ZoneId.of("Europe/Helsinki"));ZonedDateTime next=Instant.ofEpochMilli(DailyScheduleClock.nextBoundary(gap)).atZone(gap.getZone());check(next.getHour()==3&&next.toLocalDate().equals(gap.toLocalDate().plusDays(1)),"DST gap does not move tomorrow checkpoint to four");
  System.out.println("PASS "+n+" 03:00 schedule view boundaries; no Android scheduler execution.");
 }
}
