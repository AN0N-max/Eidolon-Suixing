package com.eidolon.sc.mobile;
import java.time.*;import java.util.*;import org.json.*;
public final class ReachWakeTests {
 static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;System.out.println("PASS "+s);}
 static long t(String s){return ZonedDateTime.parse("2026-11-05T"+s+":00+08:00[Asia/Shanghai]").toInstant().toEpochMilli();}
 public static void main(String[] args)throws Exception{
  ReachConfig c=new ReachConfig(new JSONObject().put("quietEnabled",false).put("planGapMinutes",30));
  check(ReachWake.preserveEarlier(100,500,300)==300,"routine check preserves earlier outstanding wake");
  check(ReachWake.preserveEarlier(100,200,300)==200,"new earlier task advances wake");
  check(ReachWake.preserveEarlier(100,500,99)==500,"delivered/overdue alarm is not kept forever");
  check(ReachWake.preserveEarlier(100,500,0)==500,"missing alarm gets scheduled");
  long shifted=ReachPlanRules.recheckAt(c,t("07:00"),t("07:00"),t("08:30"),0,Collections.emptyList(),at->at>=t("08:00"));
  check(shifted==t("08:00"),"morning sleep moves only unsent task to an available original-window slot");
  check(ReachPlanRules.recheckAt(c,t("09:40"),t("09:50"),t("11:20"),0,Collections.emptyList(),at->at>=t("11:30"))<0,"class until after expiry does not extend deadline");
  check(ReachPlanRules.recheckAt(c,t("09:00"),t("07:00"),t("08:30"),0,Collections.emptyList(),at->true)<0,"opening phone after expiry never revives historical task");
  check(ReachPlanRules.recheckAt(c,t("07:00"),t("07:00"),t("08:30"),1,Collections.emptyList(),at->true)<0,"paid or manually retried opportunity is never automatically retimed");
  long start=ReachPlanRules.recheckAt(c,t("07:12"),t("07:00"),t("08:30"),0,Collections.emptyList(),at->true);
  check(start==t("07:15"),"past scheduled time with valid remaining window uses a fresh future slot");
  check(ReachPlanRules.recheckAt(c,t("07:00"),t("07:05"),t("08:30"),0,Arrays.asList(t("07:30")),at->true)==t("08:00"),"retimed task keeps spacing against other opportunities");
  ReachConfig quiet=new ReachConfig(new JSONObject().put("quietEnabled",true).put("quietStart","23:00").put("quietEnd","08:00"));
  check(ReachPlanRules.recheckAt(quiet,t("07:00"),t("07:00"),t("08:30"),0,Collections.emptyList(),at->true)==t("08:00"),"quiet period remains respected");
  final int[] reads={0};check(ReachPlanRules.recheckAt(c,t("07:00"),t("07:00"),t("08:30"),0,Collections.emptyList(),at->{reads[0]++;return false;})<0&&reads[0]<=18,"slot search is bounded and does not call services");
  for(int offset=0;offset<240;offset++){long now=t("07:00")+offset*60000L,expires=t("11:00"),at=ReachPlanRules.recheckAt(c,now,t("07:00"),expires,0,Collections.emptyList(),v->true);if(at>0&&!(at>=now+30000&&at<expires))throw new AssertionError("window boundary "+offset);}
  check(true,"240 adjacent-minute cases never leave original validity window");
  System.out.println("Reach wake rules checks "+checks);
 }
}
