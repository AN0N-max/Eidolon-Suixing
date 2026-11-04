package com.eidolon.sc.mobile;
import java.time.*;import java.util.*;import org.json.*;

/** Shared native rules, fake model responses and cancellation; no Android scheduler/API. */
public final class ScheduleQueueTests {
 static int count;static void check(boolean b,String s){if(!b)throw new AssertionError(s);count++;System.out.println("PASS "+s);}
 static String reason(ZonedDateTime at,boolean talked,long activity,boolean ended,boolean attempted){return ScheduleQueueRules.intrinsic(at,at.toInstant().toEpochMilli(),talked,activity,ended,attempted);}
 public static void main(String[] args)throws Exception {
  ZonedDateTime at=ZonedDateTime.parse("2026-11-04T03:00:00+08:00[Asia/Shanghai]");long now=at.toInstant().toEpochMilli();
  check(reason(at.minusSeconds(1),true,now,false,false).equals("before_day"),"no automatic call before each role's03:00");
  check(reason(at,true,now,false,false).isEmpty(),"eligibility starts exactly at03:00");
  check(reason(at,false,now,false,false).equals("unchatted"),"uncontacted role excluded");
  check(reason(at,true,now-7L*86400000-1,false,false).equals("inactive"),"seven-day activity window enforced");
  check(reason(at,true,now-7L*86400000,false,false).isEmpty(),"activity window boundary inclusive");
  check(reason(at,true,now+60001,false,false).equals("clock"),"future activity does not fabricate a current contact");
  check(reason(at,true,now,true,false).equals("closed"),"ended conversation not auto bypassed");
  check(reason(at,true,now,false,true).equals("attempted"),"any prior attempt prevents automatic paid replay");
  check(reason(at.withZoneSameInstant(ZoneId.of("Asia/Tokyo")),true,now,false,false).isEmpty()&&reason(at.withZoneSameInstant(ZoneId.of("UTC")),true,now,false,false).isEmpty(),"independent zones retain their own day checkpoints");
  ZonedDateTime utcBefore=ZonedDateTime.parse("2026-11-04T02:59:59Z");check(reason(utcBefore,true,utcBefore.toInstant().toEpochMilli(),false,false).equals("before_day"),"a different zone does not inherit the producer's03:00");
  Set<String> attempted=new LinkedHashSet<>();int requests=0;
  for(int job=0;job<28;job++){
   String selected=null;for(int i=0;i<28;i++){String role="role-"+i;if(ScheduleQueueRules.intrinsic(at,now,true,now-i*1000,false,attempted.contains(role)).isEmpty()){selected=role;break;}}
   check(selected!=null,"remaining eligible role selected in bounded step "+job);attempted.add(selected);requests++;
   // Some failures/unknown results are still attempts: none may starve the next role.
  }
  check(requests==28&&attempted.size()==28,"configured28-role queue can visit all28 exactly once");
  check(ScheduleQueueRules.intrinsic(at,now,true,now,false,attempted.contains("role-0")).equals("attempted"),"the failed first role is not retried on the next queue wake");
  check(ScheduleQueueRules.retryAt(now,false)-now==60000&&ScheduleQueueRules.retryAt(now,true)-now==300000,"remaining work uses one-minute checks; real activity backs off five minutes");
  check(ScheduleQueueRules.live(1,1,2,2,true,false),"current enabled request remains valid");
  check(!ScheduleQueueRules.live(1,2,2,2,true,false),"late permission/UI cancellation cannot restart old schedule call");
  check(!ScheduleQueueRules.live(1,1,2,3,true,false)&&!ScheduleQueueRules.live(1,1,2,2,false,false)&&!ScheduleQueueRules.live(1,1,2,2,true,true),"restore, switch off and OS stop each invalidate the request");
  ModelClient fake=new ModelClient();long start=ScheduleEngine.cancellation.get();ScheduleEngine.active=fake;ScheduleEngine.cancel();check(fake.cancelled&&ScheduleEngine.cancellation.get()==start+1,"actual native cancellation stops owned client and advances identity");ScheduleEngine.active=null;
  final int[] calls={0};try{ScheduleGeneration.generate("fixture","rules",new JSONObject(),request->{calls[0]++;return ScheduleTests.plan().toString();},()->{if(!ScheduleQueueRules.live(start,ScheduleEngine.cancellation.get(),2,2,true,false))throw new Exception("cancelled");});throw new AssertionError("late call accepted");}catch(Exception expected){check(calls[0]==0,"cancellation before fake transport opens no connection");}
  JSONObject tagged=ScheduleTests.plan();tagged.getJSONArray("slots").getJSONObject(0).put("kind","work");tagged.getJSONArray("slots").getJSONObject(1).put("kind","school");JSONObject normalized=ScheduleData.normalize(tagged);
  check(normalized.getJSONArray("slots").getJSONObject(0).getString("kind").equals("work")&&normalized.getJSONArray("slots").getJSONObject(1).getString("kind").equals("school"),"optional category survives canonical schedule validation");
  check(ScheduleData.withBath(normalized,"泡澡",1).getJSONArray("slots").getJSONObject(0).getString("kind").equals("work"),"existing local bath enrichment preserves slot category");
  tagged.getJSONArray("slots").getJSONObject(0).put("kind","execute-command");try{ScheduleData.normalize(tagged);throw new AssertionError("unknown kind accepted");}catch(Exception expected){check(true,"unrecognized slot categories rejected");}
  check(SocialData.budgetDay("2026-11-03","2026-11-04").equals("2026-11-04")&&SocialData.budgetDay("2026-11-05","2026-11-04").equals("2026-11-05"),"clock rollback cannot reopen an earlier social request budget");
  System.out.println("Passed "+count+" queue/category/cancellation checks. Fake rules only; no Android job or paid model executed.");
 }
}
