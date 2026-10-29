package com.eidolon.sc.mobile;
import java.time.*;
import org.json.*;
public final class WritingMorningTests {
 static int n;static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);n++;System.out.println("PASS "+text);}
 public static void main(String[] args)throws Exception{
  ZoneId zone=ZoneId.of("Asia/Shanghai");ZonedDateTime before=ZonedDateTime.of(2026,10,27,2,59,0,0,zone),three=before.plusMinutes(1),after=three.plusHours(1);String day="2026-10-26";
  check(WritingMorningRules.nextCheck(before,"","")==three.toInstant().toEpochMilli(),"before three waits for local 03:00");
  check(WritingMorningRules.nextCheck(three,"","")==three.toInstant().toEpochMilli()+1000,"missing morning check is queued after 03:00");
  check(WritingMorningRules.nextCheck(after,"2026-10-27",zone.getId())==ZonedDateTime.of(2026,10,28,3,0,0,0,zone).toInstant().toEpochMilli(),"completed date checkpoint schedules next local morning");
  check(WritingMorningRules.nextCheck(after,"2026-10-27","Asia/Tokyo")==after.toInstant().toEpochMilli()+1000,"profile timezone change receives fresh local check");
  check(WritingMorningRules.diaryAt(day,zone)==three.toInstant().toEpochMilli(),"diary belongs to previous day and first becomes due at next morning three");
  check(WritingMorningRules.diaryState(day,before.toLocalDate(),before.toInstant().toEpochMilli(),zone,false,false,null).equals("planned"),"previous-day diary is not generated before three");
  check(WritingMorningRules.diaryState(day,three.toLocalDate(),three.toInstant().toEpochMilli(),zone,false,false,null).equals("pending"),"previous-day event is available at three");
  check(WritingMorningRules.diaryState("2026-10-27",three.toLocalDate(),three.toInstant().toEpochMilli(),zone,false,false,null).equals("planned"),"today events wait until tomorrow rather than becoming past experiences");
  check(WritingMorningRules.diaryState(day,three.toLocalDate(),three.toInstant().toEpochMilli(),zone,true,true,null).equals("completed"),"saved diary is never scheduled for another generation");
  check(WritingMorningRules.diaryState(day,three.toLocalDate(),three.toInstant().toEpochMilli(),zone,false,true,null).equals("blocked"),"relationship restriction remains in force");
  check(WritingMorningRules.diaryState("2026-10-23",three.toLocalDate(),three.toInstant().toEpochMilli(),zone,false,false,null).equals("expired"),"older than three days is not caught up");
  JSONObject attempt=new JSONObject().put("tries",1).put("at",three.toInstant().toEpochMilli());check(WritingMorningRules.diaryState(day,three.toLocalDate(),three.plusMinutes(30).toInstant().toEpochMilli(),zone,false,false,attempt).equals("retry"),"failure keeps at least an hour delay");
  check(WritingMorningRules.diaryState(day,after.toLocalDate(),after.toInstant().toEpochMilli(),zone,false,false,attempt).equals("pending"),"one retry becomes available after full hour");
  attempt.put("tries",2);check(WritingMorningRules.diaryState(day,after.toLocalDate(),after.toInstant().toEpochMilli(),zone,false,false,attempt).equals("failed"),"two-attempt cap not reset by checking plan");
  ZoneId dst=ZoneId.of("America/New_York");ZonedDateTime transition=ZonedDateTime.of(2026,11,1,1,30,0,0,dst);check(Instant.ofEpochMilli(WritingMorningRules.nextCheck(transition,"","")).atZone(dst).getHour()==3,"03:00 follows local calendar across DST");
  System.out.println("PASS "+n+" morning writing rules; no JobScheduler or API execution.");
 }
}
