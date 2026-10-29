package com.eidolon.sc.mobile;
import java.util.*;
import java.time.*;
import org.json.*;
public final class TimelineTests {
    static int n;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
    static long at(String s){return Instant.parse(s).toEpochMilli();}
    public static void main(String[] args)throws Exception{
        long stamp=at("2026-10-24T02:00:00Z");
        check(MessageTime.dateFrom("昨天一起看了电影",stamp,"Asia/Shanghai").equals("2026-10-23"),"yesterday anchors to source message, not summary date");
        check(MessageTime.dateFrom("前天去散步",stamp,"Asia/Shanghai").equals("2026-10-22"),"two days ago");check(MessageTime.dateFrom("明天上课",stamp,"Asia/Shanghai").equals("2026-10-25"),"future date retained as future");
        check(MessageTime.dateFrom("2026年10月20日见面",0,"Asia/Shanghai").equals("2026-10-20"),"explicit date survives unknown legacy message time");
        check(MessageTime.dateFrom("昨天见面",0,"Asia/Shanghai").isEmpty(),"unknown old send time never guessed as today");check(MessageTime.dateFrom("昨天说今天去",stamp,"Asia/Shanghai").isEmpty(),"multiple relative anchors remain uncertain");check(MessageTime.dateFrom("10月23日见过",0,"Asia/Shanghai").isEmpty(),"missing year and reference time remain unknown");
        long midnight=at("2026-10-23T16:30:00Z");check(MessageTime.dateFrom("今天散步",midnight,"Asia/Shanghai").equals("2026-10-24")&&MessageTime.dateFrom("今天散步",midnight,"America/Los_Angeles").equals("2026-10-23"),"message timezone drives relative dates");
        ChatStore.Message message=new ChatStore.Message(12,"user","昨天去看电影",stamp,"Asia/Shanghai");check(MessageTime.context(Arrays.asList(message,new ChatStore.Message("assistant","旧聊天"))).contains("2026-10-24")&&MessageTime.context(Collections.singletonList(new ChatStore.Message("assistant","旧聊天"))).contains("未知")&&MessageTime.content(message).equals("昨天去看电影"),"known and unknown times stay in system reference, dialogue stays unprefixed");
        JSONArray raw=new JSONArray().put(new JSONObject().put("title","看电影").put("text","制作人提到昨天一起看电影").put("status","occurred").put("evidence","昨天去看电影").put("eventDate","2026-10-26"));JSONArray proposals=MemoryNotebook.proposals(raw,Collections.singletonList(message),"rinze","s");
        check(proposals.length()==1&&proposals.getJSONObject(0).getString("eventDate").equals("2026-10-23"),"model-supplied date cannot override verified source anchor");check(!proposals.getJSONObject(0).getBoolean("confirmed"),"automatic extraction starts unconfirmed");
        raw.getJSONObject(0).put("evidence","角色凭空编造的约会");check(MemoryNotebook.proposals(raw,Collections.singletonList(message),"rinze","s").length()==0,"unsupported evidence rejected");check(MemoryNotebook.proposals(raw,Collections.singletonList(new ChatStore.Message(12,"assistant","角色凭空编造的约会",stamp,"Asia/Shanghai")),"rinze","s").length()==0,"assistant-only inventions not stored as user events");
        check(!MemoryNotebook.hash(Collections.singletonList(message)).equals(MemoryNotebook.hash(Collections.singletonList(new ChatStore.Message(12,"user","修正过的聊天",stamp,"Asia/Shanghai")))),"source text edits invalidate old summary hash");
        ReachConfig base=new ReachConfig(null);check(!ReachFairness.changed(base,base.updated(new JSONObject().put("width",400).put("sound",false).put("notifications",false).put("privacy",true).put("popupSeconds",12))),"popup and notification changes preserve existing plan epoch");check(!ReachFairness.changed(base,base.updated(new JSONObject().put("hours",12.0))),"saving numerically identical config does not invalidate plans");check(ReachFairness.changed(base,base.updated(new JSONObject().put("contactDays",30))),"actual scheduling rules require new epoch");
        List<JSONObject> candidates=new ArrayList<>();candidates.add(new JSONObject().put("character","rinze").put("sent",2).put("lastReach",100));candidates.add(new JSONObject().put("character","tenka").put("sent",0).put("lastReach",200));candidates.add(new JSONObject().put("character","mano").put("sent",0).put("lastReach",50));ReachFairness.order(candidates,"2026-10-26");check(candidates.get(0).getString("character").equals("mano")&&candidates.get(2).getString("character").equals("rinze"),"least served then longest waiting roles get priority");
        check(ReachFairness.deferAt(1000,200000,2)==121000,"temporary busy yields a later attempt before expiry");check(ReachFairness.deferAt(1000,10000,2)<0,"no unbounded retries after deadline");
        JSONObject note=MemoryNotebook.blank("rinze","s","event").put("title","未来约定").put("text","计划一起散步").put("eventDate","2026-10-28").put("status","planned");check(MemoryNotebook.validate(note).getString("status").equals("planned"),"planned event is not coerced into occurred");
        note.put("eventDate","2026-02-31");boolean rejected=false;try{MemoryNotebook.validate(note);}catch(Exception e){rejected=true;}check(rejected,"invalid calendar date rejected");
        JSONObject preview=NotebookEngine.parse("```json\n{\"summary\":\"10月23日看电影。\",\"events\":[]}\n```","rinze","s",Collections.singletonList(message),7);
        check(preview.getJSONArray("entries").getJSONObject(0).getString("eventDate").isEmpty()&&preview.getLong("epoch")==7,"summary preview keeps unknown overall event date and source epoch");check(preview.getJSONArray("entries").getJSONObject(0).getJSONArray("evidence").getJSONObject(0).getLong("messageId")==12,"summary preview retains exact source message");
        boolean malformed=false;try{NotebookEngine.parse("{\"summary\":", "rinze","s",Collections.singletonList(message),7);}catch(Exception e){malformed=true;}check(malformed,"truncated summary rejected before acceptance");
        check(MessageTime.dateFrom("昨天约好下周去旅行",stamp,"Asia/Shanghai").isEmpty(),"ambiguous agreement date does not become trip date");
        JSONArray negative=new JSONArray().put(new JSONObject().put("title","约会").put("text","昨天约会").put("status","occurred").put("evidence","昨天没去约会"));check(MemoryNotebook.proposals(negative,Collections.singletonList(new ChatStore.Message(13,"user","昨天没去约会",stamp,"Asia/Shanghai")),"rinze","s").length()==0,"negated event is not automatically recorded as occurred");
        System.out.println("PASS "+n+" dated memory and fair scheduling checks; no Android runtime or real API.");
    }
}
