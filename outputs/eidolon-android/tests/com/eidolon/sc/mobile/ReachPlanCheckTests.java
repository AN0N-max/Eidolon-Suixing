package com.eidolon.sc.mobile;
import org.json.*;
import java.time.*;
import java.util.*;
public final class ReachPlanCheckTests {
    static int n;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
    static long at(String s){return ZonedDateTime.parse(s).toInstant().toEpochMilli();}
    public static void main(String[] args)throws Exception{
        ReachConfig c=new ReachConfig(new JSONObject().put("mode","interval").put("hours",1).put("recentMinutes",5));
        long now=at("2026-10-27T09:28:00+08:00"),end=at("2026-10-28T00:00:00+08:00"),contact=now-2*3600000L;
        check(ReachPlanCheckRules.next(c,now,0)==now+15*60000L,"empty day keeps a 15-minute check instead of waiting until tomorrow");
        check(ReachPlanCheckRules.next(c,now,now-60000)==now+30000,"overdue task wakes in 30 seconds, not at expiry");
        check(ReachPlanCheckRules.next(c,now,now+60000)==now+60000,"near task takes precedence");
        check(ReachPlanCheckRules.next(c,now,now+3600000)==now+15*60000L,"distant task permits periodic reconciliation");
        long dawn=at("2026-10-27T02:59:00+08:00");check(ReachPlanCheckRules.next(c,dawn,0)==dawn+60000,"03:00 check is retained");
        ReachConfig slow=c.updated(new JSONObject().put("backgroundMinutes",45));check(ReachPlanCheckRules.next(slow,now,0)==now+45*60000L,"configured background interval honored");
        check(ReachPlanCheckRules.overdue(now-1,now)&&ReachPlanCheckRules.overdue(now,now)&&!ReachPlanCheckRules.overdue(now+1,now)&&!ReachPlanCheckRules.overdue(0,now),"expired and absent wake times are distinct");
        check(ReachPlanCheckRules.active("PENDING")&&ReachPlanCheckRules.active("RUNNING")&&!ReachPlanCheckRules.active("FAILED")&&!ReachPlanCheckRules.active("COMPLETED"),"active counts exclude history");
        ReachConfig fixed=c.updated(new JSONObject().put("mode","daily").put("time","08:00"));
        check(ReachPlanRules.unavailable(fixed,now,end,contact,80,7,Collections.emptyList(),t->true).contains("固定时间已过"),"missed fixed time diagnosed");
        ReachConfig longGap=c.updated(new JSONObject().put("hours",48));check(ReachPlanRules.unavailable(longGap,now,end,contact,80,7,Collections.emptyList(),t->true).contains("间隔尚未结束"),"interval past midnight diagnosed");
        check(ReachPlanRules.unavailable(c,now,end,contact,80,7,Collections.emptyList(),t->false).contains("忙碌时段"),"busy schedule diagnosed independently");
        List<Long> occupied=new ArrayList<>();for(long stamp=now;stamp<end;stamp+=15*60000L)occupied.add(stamp);
        check(ReachPlanRules.unavailable(c,now,end,contact,80,7,occupied,t->true).contains("最小间距冲突"),"allocation spacing diagnosed independently");
        long first=ReachPlanRules.choose(c,now,end,contact,80,7,Collections.emptyList(),t->true);
        check(first>=now+5*60000L&&first<end,"available character produces a candidate on an empty day");
        check(first==ReachPlanRules.choose(c,now,end,contact,80,7,Collections.emptyList(),t->true),"refresh does not re-roll candidate");
        System.out.println("PASS "+n+" local planner recovery rules; no Android scheduler or API execution.");
    }
}
