package com.eidolon.sc.mobile;
import java.time.*;
/** Pure admission and bounded wake-up rules used by the native queue. */
final class ScheduleQueueRules {
 static String intrinsic(ZonedDateTime at,long now,boolean talked,long activity,boolean ended,boolean attempted){
  if(at.isBefore(DailyScheduleClock.boundary(at.toLocalDate(),at.getZone())))return "before_day";
  if(!talked)return "unchatted";if(activity>now+60000)return "clock";if(activity<now-7L*86400000L)return "inactive";
  if(ended)return "closed";return attempted?"attempted":"";
 }
 static boolean live(long request,long current,long data,long currentData,boolean enabled,boolean interrupted){return request==current&&data==currentData&&enabled&&!interrupted;}
 static long retryAt(long now,boolean busy){return now+(busy?300000:60000);}
}
