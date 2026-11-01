package com.eidolon.sc.mobile;
import java.time.*;import java.util.*;import org.json.*;
public class TimeReferenceTests {
 static void check(boolean b,String n){if(!b)throw new AssertionError(n);System.out.println("PASS "+n);}
 static long at(String s){return Instant.parse(s).toEpochMilli();}
 static ChatStore.Message user(String s,String time){return new ChatStore.Message(1,"user",s,at(time),"Asia/Shanghai",0);}
 static JSONObject fact(ChatStore.Message m,Clock c,ZoneId z)throws Exception{return RecentTimeFacts.build(Collections.singletonList(m),c,z).getJSONArray("topics").getJSONObject(0);}
 public static void main(String[] args)throws Exception{
  ZoneId z=ZoneId.of("Asia/Shanghai");Clock clock=Clock.fixed(Instant.parse("2026-10-31T01:00:00Z"),ZoneOffset.UTC);
  JSONObject yesterday=fact(user("被朋友叫去帮忙","2026-10-30T12:00:00Z"),clock,z);check(yesterday.getInt("mentionedDaysBeforeNow")==1&&yesterday.isNull("eventDate")&&!yesterday.getBoolean("recentSameDay"),"yesterday message answered today preserves mention date without guessing event completion");
  ChatStore.Message delayed=new ChatStore.Message(2,"assistant","<zh>今天练习了</zh>",at("2026-10-30T16:00:30Z"),z.getId(),at("2026-10-30T15:59:30Z"));check(MessageTime.context(Collections.singletonList(delayed)).contains("2026-10-30 23:59:30"),"one-minute midnight delivery retains original formation time");
  JSONObject relative=fact(user("昨天帮朋友搬东西，已经完成了","2026-10-31T00:59:00Z"),clock,z);check(relative.getString("eventDate").equals("2026-10-30")&&relative.getString("completion").equals("explicitly_reported_completed"),"today mentioning yesterday resolves event date from source words");
  Clock midnight=Clock.fixed(Instant.parse("2026-10-30T16:01:00Z"),ZoneOffset.UTC);check(!fact(user("今天被叫去帮忙","2026-10-30T15:59:30Z"),midnight,z).getBoolean("recentSameDay"),"continuing across midnight cannot promote yesterday to same-day just now");
  check(fact(user("刚刚做完了","2026-10-31T00:59:00Z"),clock,z).getBoolean("recentSameDay"),"continuous same-day conversation retains recent anchor");
  ZoneId ny=ZoneId.of("America/New_York");JSONObject other=fact(user("昨天出去了一趟","2026-10-31T01:00:00Z"),clock,ny);check(other.getString("mentionedOn").equals("2026-10-30")&&other.getString("eventDate").equals("2026-10-29"),"role timezone determines dates independently of stored device timezone");
  ChatStore.Message unknown=new ChatStore.Message("user","昨天的事情");check(fact(unknown,clock,z).isNull("eventDate"),"legacy missing timestamp never defaults to today");
  check(fact(user("昨天没做完，今天还没结束","2026-10-31T00:59:00Z"),clock,z).isNull("eventDate"),"conflicting dates remain unresolved");
  JSONObject request=ModelClient.conversationRequest("fake","role",Collections.singletonList(delayed),Collections.singletonList(user("昨天帮忙的事","2026-10-31T00:59:00Z")),clock,z);
  String system=request.getJSONArray("messages").getJSONObject(0).getString("content");check(system.contains("近期话题的时间依据")&&system.contains("2026-10-30")&&system.contains("writtenAt"),"final actual request includes dated facts and delivery/formation references");
  JSONObject diary=new JSONObject().put("kind","diary").put("text","<ja>日記だよ</ja><zh>今天提到‘あんた’，有点开心。</zh>");String opening=WritingData.visible(diary,50).getString("text");check(opening.startsWith("<zh>")&&!BilingualReply.needsChinese(opening),"Chinese diary opening with quoted Japanese remains marked Chinese");
 }
}
