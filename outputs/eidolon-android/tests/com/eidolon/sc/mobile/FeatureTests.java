package com.eidolon.sc.mobile;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class FeatureTests {
    static int count;
    interface Action{void run()throws Exception;}
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    static void rejects(Action action,String name)throws Exception{try{action.run();}catch(Exception e){check(true,name);return;}throw new AssertionError(name);}
    static JSONObject judged(int level,boolean out,boolean manip)throws Exception{return new JSONObject().put("level",level).put("out_of_line",out).put("manipulation",manip);}
    static JSONObject proposal(String text,String evidence,String... replaces)throws Exception{return new JSONObject().put("text",text).put("evidence",evidence).put("supersedes",new JSONArray(Arrays.asList(replaces)));}
    public static void main(String[] args)throws Exception{
        JSONObject d=PersonalData.fresh();Progression.upgrade(d);long now=System.currentTimeMillis();
        for(int i=0;i<4;i++)Progression.apply(d,"mano","s",judged(4,false,false),now);
        check(d.getJSONObject("affinity").getDouble("mano")==56,"daily positive gains capped at six");
        Progression.apply(d,"mano","s",judged(4,false,true),now);check(d.getJSONObject("affinity").getDouble("mano")==56,"manipulation never increases affinity");
        JSONObject outcome=Progression.apply(d,"mano","s",judged(0,true,false),now);check(d.getJSONObject("affinity").getDouble("mano")==46,"out of line is minus ten, not minus fifteen");check(outcome.getBoolean("warning")&&!outcome.getBoolean("ended"),"hurt threshold warns before ending");
        check(!Progression.apply(d,"mano","s",judged(2,false,false),now).getBoolean("warning"),"warning only occurs when crossing threshold");
        outcome=Progression.apply(d,"mano","s",judged(2,true,false),now);check(outcome.getBoolean("ended"),"second out-of-line ends session");
        check(!Progression.session(d,"new-session").getBoolean("ended"),"new conversation has independent hurt window");
        d.getJSONObject("affinity").put("hiori",0);JSONObject relation=Progression.relation(d,"hiori",now);relation.put("cooling",true).put("at",now-43200000L);Progression.relation(d,"hiori",now);
        check(Math.abs(d.getJSONObject("affinity").getDouble("hiori")-5)<.001&&relation.getBoolean("cooling"),"continuous half-day recovery remains cooling");
        Progression.relation(d,"hiori",now+43200000L);check(!relation.getBoolean("cooling"),"cooling thaws at ten");
        double before=d.getJSONObject("affinity").getDouble("hiori");Progression.relation(d,"hiori",now);check(d.getJSONObject("affinity").getDouble("hiori")==before,"clock rollback does not accelerate recovery");
        JSONObject rewards=PersonalData.fresh().put("draws",7);String day=PersonalData.today();Progression.happened(rewards,"mano",1,0,0,day);check(rewards.getInt("draws")==8,"task reward can exceed natural seven-draw cap");Progression.happened(rewards,"mano",1,0,0,day);check(rewards.getInt("draws")==8,"daily one-turn reward issued once");
        Progression.happened(rewards,"mano",8,0,0,day);check(rewards.getInt("draws")==10,"ten turns awards daily task and first achievement");Progression.unlock(rewards);check(rewards.getInt("draws")==10,"achievement unlock idempotent");
        PersonalData.refresh(rewards,java.time.LocalDate.parse(day).plusDays(1).toString());check(rewards.getInt("draws")==10,"daily refresh preserves bonus balance above seven");check(rewards.getJSONObject("progress").getJSONObject("daily").getInt("turns")==0,"next day clears task display");
        Progression.happened(rewards,"mano",1,0,0,day);check(rewards.getInt("draws")==10,"old day cannot reissue daily reward");PersonalData.validate(rewards);check(true,"reward balance accepted by backup validator");
        JSONObject memory=PersonalData.fresh();JSONArray all=memory.getJSONArray("memories");all.put(new JSONObject().put("id","old").put("text","用户住在北京"));JSONArray candidates=Recall.memories(memory,"住在哪里",20,true);
        int added=Recall.write(memory,new JSONArray().put(proposal("用户现居上海","我搬到上海了","old")),candidates,"我搬到上海了","mano","session");check(added==1&&all.getJSONObject(0).has("supersededBy"),"new evidenced fact supersedes selected prior fact");
        check(Recall.memories(memory,"北京",3,false).length()==0,"superseded memories excluded from retrieval");
        JSONObject newMemory=all.getJSONObject(1);String newId=newMemory.getString("id");check(Recall.prompt(new JSONArray().put(newMemory),"hiori",new JSONArray()).contains("听说"),"cross-character memory marked as hearsay");
        check(Recall.write(memory,new JSONArray().put(proposal("用户喜欢猫","并未说过")),candidates,"我搬到上海了","mano","s")==0,"unquoted invented memory rejected");
        check(Recall.write(memory,new JSONArray().put(proposal("用户喜欢狗","喜欢狗","unknown")),candidates,"我喜欢狗","mano","s")==0,"unknown supersession reference rejected");
        JSONObject reversal=new JSONObject(memory.toString());JSONArray reversalCandidates=Recall.memories(reversal,"搬回北京",20,true);check(Recall.write(reversal,new JSONArray().put(proposal("用户住在北京","我又搬回北京了",newId)),reversalCandidates,"我又搬回北京了","mano","s")==1,"reverting to a former fact creates a fresh active memory without a cycle");
        Recall.delete(memory,newId);check(!all.getJSONObject(0).has("supersededBy")&&memory.getLong("memoryEpoch")==1,"delete replacement restores prior fact and advances epoch");
        JSONObject cycle=new JSONObject(memory.toString());cycle.getJSONArray("memories").getJSONObject(0).put("supersededBy","old");rejects(()->PersonalData.validate(cycle),"cyclic backup memories rejected");
        String analysis="{\"affinity\":{\"levels\":[0,0,0.5,0.5,0],\"out_of_line\":0,\"manipulation\":0},\"query\":\"音乐\",\"memories\":[]}";
        check(Recall.readAnalysis("```json\n"+analysis+"\n```").getJSONObject("judged").getInt("level")==2,"tied judge probabilities are neutral");
        rejects(()->Recall.readAnalysis(analysis.replace("0.5,0.5","0.7,0.5")),"invalid probability sum rejected");
        JSONObject selection=new JSONObject().put("recallIds",new JSONArray().put("forged").put("old"));check(Recall.selected(memory,all,selection,"哪里").length()==1,"semantic memory selection only uses allowed IDs");
        JSONArray chars=new JSONArray().put(new JSONObject().put("id","mano").put("speakers",new JSONArray().put("真乃")));
        String csv="id,name,text,trans\r\n1,真乃,\"公園で,歌う\",\"在公园唱歌\n很开心\"\r\nselect,,wrong,选择\r\ninfo,produce_events/100100101.json,,\r\n";
        JSONObject episode=StoryFiles.parse(csv.getBytes("UTF-8"),"公园.csv",chars);check(episode.getString("id").equals("produce_events/100100101")&&episode.getJSONArray("cast").getString(0).equals("mano"),"CSV info identity and speaker cast preserved");check(episode.getString("text").contains("公園で,歌う")&&!episode.getString("text").contains("wrong"),"quoted multiline CSV and producer choice exclusion");
        JSONObject game=StoryFiles.parse("[{\"speaker\":\"真乃\",\"text\":\"歌の練習\"},{\"select\":[\"選択\"]}]".getBytes("UTF-8"),"produce_events_100100101.json",chars);check(game.getString("text").contains("歌の練習")&&game.getJSONArray("cast").length()==1,"game JSON dialogue imported without choices");
        check(StoryFiles.parse(csv.replace("100100101","700100102").getBytes("UTF-8"),"failure.csv",chars).getBoolean("removed"),"canonical failure route removed");
        check(StoryFiles.parse("[]".getBytes("UTF-8"),"arbitrary.json",chars)==null,"JSON without source script ID skipped");
        rejects(()->StoryFiles.csv("1,\"unclosed"),"broken CSV quoting rejected");rejects(()->StoryFiles.read(new ByteArrayInputStream(new byte[20]),10),"oversized stream bounded");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("../../公园.csv"));zip.write(csv.getBytes("UTF-8"));zip.closeEntry();}
        check(StoryFiles.load(new ByteArrayInputStream(bytes.toByteArray()),"stories.zip",chars).length()==1,"ZIP content parsed without filesystem extraction");
        check(Recall.score("公园 唱歌","真乃：在公园唱歌")>Recall.score("公园 唱歌","晚餐吃面包"),"Chinese bigram retrieval favors relevant passage");
        JSONObject restored=CoreTests.backup().put("version",3).put("personal",rewards).put("stories",new JSONArray().put(episode));BackupValidator.validate(restored,new HashSet<>(Arrays.asList("mano")));check(true,"v3 backup includes validated stories and progression");
        long noon=java.time.Instant.parse("2026-10-22T04:00:00Z").toEpochMilli();
        check(ReachPolicy.delay(90,0)==3*ReachPolicy.HOUR&&ReachPolicy.delay(70,1)==16*ReachPolicy.HOUR&&ReachPolicy.delay(50,0)==20*ReachPolicy.HOUR,"reach tier intervals");
        check(!ReachPolicy.allowed(39,noon-1000,noon,false,false)&&!ReachPolicy.allowed(90,noon-ReachPolicy.WINDOW-1,noon,false,false),"cold and expired contact never reach out");
        check(!ReachPolicy.allowed(90,noon-1000,noon,true,false)&&!ReachPolicy.allowed(90,noon-1000,noon,false,true),"ended and cooling blocked");
        long night=java.time.Instant.parse("2026-10-22T15:00:00Z").toEpochMilli();check(!ReachPolicy.allowed(90,noon,night,false,false)&&ReachPolicy.clock(ReachPolicy.morning(night,0)).get(java.util.Calendar.HOUR_OF_DAY)==8,"quiet hours defer to morning");
        check(ReachPolicy.due("daily",12,"13:30",90,noon)==java.time.Instant.parse("2026-10-22T05:30:00Z").toEpochMilli(),"daily reach scheduled for selected Beijing time");
        check(ReachPolicy.due("daily",12,"11:30",90,noon)==java.time.Instant.parse("2026-10-23T03:30:00Z").toEpochMilli(),"elapsed daily reach waits until tomorrow");
        check(ReachPolicy.due("interval",2,"12:00",90,noon)==noon+2*ReachPolicy.HOUR,"custom reach interval is applied");
        JSONObject v4=new JSONObject(restored.toString()).put("version",4);JSONArray msgs=v4.getJSONArray("conversations").getJSONObject(0).getJSONArray("messages");msgs.put(new JSONObject().put("role","assistant").put("text","主动搭话").put("reachOut",true));BackupValidator.validate(v4,new HashSet<>(Arrays.asList("mano")));check(true,"v4 standalone reach-out backup valid");
        msgs.getJSONObject(msgs.length()-1).put("role","user");rejects(()->BackupValidator.validate(v4,new HashSet<>(Arrays.asList("mano"))),"reach-out cannot masquerade as user");
        java.util.List<ChatStore.Message> proactiveHistory=Arrays.asList(new ChatStore.Message("user","hello"),new ChatStore.Message("assistant","reply"),new ChatStore.Message("assistant","reach"));JSONArray requested=ModelClient.request("m","s",proactiveHistory,"answer").getJSONArray("messages");check(requested.length()==5&&requested.getJSONObject(3).getString("content").equals("reach"),"context preserves unsolicited assistant message with original turn");
        check(BilingualReply.needsChinese("<ja>おはようございます。</ja>")&&BilingualReply.needsChinese("おはようございます。"),"missing Chinese detected in tagged and plain Japanese");
        check(!BilingualReply.needsChinese("<ja>おはようございます。</ja><zh>早上好。</zh>")&&!BilingualReply.needsChinese("早上好。"),"complete bilingual and Chinese-only replies need no repair");
        String repaired=BilingualReply.withChinese("<ja>おはようございます。</ja><zh> </zh>","<zh>早上好。</zh>");check(repaired.equals("<ja>おはようございます。</ja><zh>早上好。</zh>"),"repair preserves Japanese and replaces empty Chinese");
        rejects(()->BilingualReply.withChinese("<ja>おはようございます。</ja>","おはようございます。"),"Japanese-only repair rejected");
        JSONObject giftEvent=GiftRecord.create("cake","蛋糕","真乃",50,56,false,noon);
        check(giftEvent.getString("note").equals("真乃有点开心")&&GiftRecord.parse(giftEvent.toString()).getString("giftName").equals("蛋糕"),"gift records use original affinity cue and retain gift identity");
        JSONObject milestone=GiftRecord.create("cake","蛋糕","真乃",63,69,false,noon);
        check(milestone.getString("note").contains("亲近")&&GiftRecord.create("cake","蛋糕","真乃",99,100,false,noon).getString("note").equals("真乃有点开心"),"gift cue distinguishes tier change from ordinary happiness");
        JSONObject v5=new JSONObject(restored.toString()).put("version",5);
        v5.getJSONArray("conversations").getJSONObject(0).getJSONArray("messages").put(new JSONObject().put("role","gift").put("text",giftEvent.toString()));
        BackupValidator.validate(v5,new HashSet<>(Arrays.asList("mano")));check(true,"v5 backup retains gift event after complete turn");
        JSONObject forged=new JSONObject(v5.toString());forged.getJSONArray("conversations").getJSONObject(0).getJSONArray("messages").getJSONObject(2).put("text","{\"kind\":\"gift\",\"giftId\":\"/bad\"}");
        rejects(()->BackupValidator.validate(forged,new HashSet<>(Arrays.asList("mano"))),"forged gift event rejected on restore");
        JSONArray giftContext=ModelClient.request("m","s",Arrays.asList(new ChatStore.Message("user","你好"),new ChatStore.Message("assistant","你好"),new ChatStore.Message("gift",giftEvent.toString())),"后来呢").getJSONArray("messages");
        check(giftContext.getJSONObject(3).getString("role").equals("user")&&giftContext.getJSONObject(3).getString("content").contains("蛋糕"),"gift event becomes safe chat context instead of invalid API role");
        JSONObject producer=ProducerProfile.empty().put("address","小林さん").put("gender","female").put("timeZone","Asia/Shanghai").put("birthMonth",2).put("birthDay",29);
        ProducerProfile.validate(producer);check(ProducerProfile.prompt(PersonalData.fresh().put("producer",producer)).contains("小林さん"),"producer profile reaches model prompt");
        rejects(()->ProducerProfile.validate(new JSONObject(producer.toString()).put("birthDay",30)),"invalid birthday rejected");
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));producer.put("anniversaryMonth",today.getMonthValue()).put("anniversaryDay",today.getDayOfMonth());
        ProducerProfile.validate(producer);check(ProducerProfile.prompt(PersonalData.fresh().put("producer",producer)).contains("自定纪念日"),"producer anniversary reaches local model context");
        JSONObject calendar=new JSONObject("{\"birthdays\":[],\"holidays\":[]}");check(CalendarEvents.prompt(calendar,PersonalData.fresh().put("producer",producer),"mano").contains("自定纪念日"),"anniversary appears in local calendar prompt");
        rejects(()->ProducerProfile.validate(new JSONObject(producer.toString()).put("anniversaryDay",0)),"anniversary requires month and day together");
        JSONObject writing=new JSONObject().put("kind","diary").put("character","mano").put("date","2026-10-23").put("text","<ja>手紙。</ja><zh>今天的礼物让我很开心。</zh>").put("read",true);
        JSONObject v6=new JSONObject(v5.toString()).put("version",6).put("writings",new JSONArray().put(writing)).put("writingEvents",new JSONArray().put(new JSONObject().put("character","mano").put("date","2026-10-23").put("text","收到礼物")));
        BackupValidator.validate(v6,new HashSet<>(Arrays.asList("mano")));check(true,"v6 preserves bilingual writings and pending events with old chat data");
        JSONObject roundTrip=new JSONObject(new String(BackupCrypto.decrypt(BackupCrypto.encrypt(v6.toString().getBytes("UTF-8"),"backup-test".toCharArray()),"backup-test".toCharArray()),"UTF-8"));
        check(roundTrip.getJSONArray("writings").getJSONObject(0).getBoolean("read")&&roundTrip.getJSONArray("writingEvents").length()==1,"encrypted v6 round trip retains unread state and diary source");
        JSONObject badWriting=new JSONObject(v6.toString());badWriting.getJSONArray("writings").put(writing);rejects(()->BackupValidator.validate(badWriting,new HashSet<>(Arrays.asList("mano"))),"duplicate daily writings rejected before restore");
        JSONObject unknownWriting=new JSONObject(v6.toString());unknownWriting.getJSONArray("writings").getJSONObject(0).put("character","unknown");rejects(()->BackupValidator.validate(unknownWriting,new HashSet<>(Arrays.asList("mano"))),"unknown writing character rejected");
        check(WritingData.visible(writing,20).getString("text").isEmpty()&&WritingData.visible(writing,20).getBoolean("locked"),"locked diary content never reaches WebView snapshot");
        check(WritingData.visible(writing,50).getBoolean("excerpt")&&!WritingData.visible(writing,50).getString("text").contains("<ja>"),"trust-level diary exposes only Chinese excerpt");
        check(WritingData.visible(writing,65).getString("text").equals(writing.getString("text"))&&writing.getString("text").contains("<ja>"),"close relationship reveals original without mutating stored text");
        StoryRetrieval defaults=new StoryRetrieval(new JSONObject());
        check(defaults.candidates==120&&defaults.terms==24&&defaults.passages==2&&defaults.chars==900,"retrieval defaults preserve prior breadth and passage depth");
        StoryRetrieval expanded=new StoryRetrieval(new JSONObject().put("candidates",800).put("terms",48).put("passages",4).put("chars",1500));
        check(new StoryRetrieval(expanded.json()).chars==1500&&expanded.passages==4&&expanded.candidates==800,"retrieval settings retain independently chosen breadth and depth");
        rejects(()->new StoryRetrieval(new JSONObject().put("candidates",2001)),"retrieval rejects excessive local candidate scan");
        rejects(()->new StoryRetrieval(new JSONObject().put("terms",0)),"retrieval rejects empty keyword budget");
        rejects(()->new StoryRetrieval(new JSONObject().put("passages",8).put("chars",6000)),"retrieval rejects excessive model context budget");
        String longStory=String.join("",Collections.nCopies(800,"真乃在公园练习唱歌。"));
        check(StoryFiles.excerpt(longStory,"公园",900).length()==900&&StoryFiles.excerpt(longStory,"公园",1500).length()==1500,"retrieval depth changes actual source excerpt length");
        System.out.println("Passed "+count+" feature checks. No live API or Android runtime used.");
    }
}
