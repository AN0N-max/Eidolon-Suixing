package com.eidolon.sc.mobile;

import java.time.*;
import java.util.*;
import org.json.*;

public class DiaryTests {
    static int count;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    static void rejects(ScheduleTests.Action f,String name)throws Exception{try{f.run();}catch(Exception e){check(true,name);return;}throw new AssertionError(name);}
    static JSONObject copy(JSONObject o)throws Exception{return new JSONObject(o.toString());}
    static JSONObject block(String ja,String zh)throws Exception{return new JSONObject().put("ja",ja).put("zh",zh);}
    static JSONObject generated()throws Exception{return new JSONObject().put("body",new JSONArray().put(block("今日のこと。","今天的正文。"))).put("secret",new JSONArray().put(block("内緒のこと。","不能提前泄露的私密正文。")));}
    static JSONObject row()throws Exception{JSONObject parsed=DiaryData.generated(generated().toString());return parsed.put("kind","diary").put("character","mano").put("date","2026-10-23").put("read",false);}
    public static void main(String[] args)throws Exception{
        JSONObject r=row();WritingData.validate(r);check(true,"structured diary with separate hidden page validates");
        check(!r.getString("text").contains("私密")&&r.getString("secret").contains("私密"),"generation stores secret separately from regular body");
        check(DiaryData.generated("```json\n"+generated()+"\n```").getString("text").contains("<ja>"),"fenced JSON preserves bilingual paragraph rendering");
        rejects(()->DiaryData.generated("<ja>public</ja><zh>※private</zh>"),"tagged or unstructured output cannot accidentally become public diary");
        JSONObject noSecret=generated();noSecret.remove("secret");rejects(()->DiaryData.generated(noSecret.toString()),"missing separation field rejected instead of treating all text as public");
        JSONObject noChinese=generated();noChinese.getJSONArray("secret").getJSONObject(0).put("zh","");rejects(()->DiaryData.generated(noChinese.toString()),"incomplete hidden-page translation rejected");
        JSONObject marked=generated();marked.getJSONArray("body").getJSONObject(0).put("ja","※秘密");rejects(()->DiaryData.generated(marked.toString()),"hidden marker inside a regular paragraph is rejected");
        JSONObject tag=generated();tag.getJSONArray("body").getJSONObject(0).put("zh","<secret>私密</secret>");rejects(()->DiaryData.generated(tag.toString()),"embedded tags cannot change diary partition boundaries");
        check(DiaryData.generated(generated().put("secret",new JSONArray()).toString()).getString("secret").isEmpty(),"diary may legitimately have no hidden page");
        for(int score:new int[]{0,20,39,40,64,65,84,85,100}){
            JSONObject shown=WritingData.visible(r,score,false,true);check(!shown.has("secret")&&!shown.toString().contains("私密"),"snapshot never exports hidden body at score "+score);
        }
        check(WritingData.visible(r,39).optBoolean("locked")&&WritingData.visible(r,39).getString("text").isEmpty(),"distant diary is closed");
        JSONObject opening=WritingData.visible(r,40);check(opening.optBoolean("excerpt")&&!opening.has("hasHiddenPage"),"trust stage gets only opening without a hidden-page hint");
        JSONObject close=WritingData.visible(r,65);check(close.getString("text").equals(r.getString("text"))&&close.optBoolean("hasHiddenPage")&&!close.optBoolean("hiddenAvailable"),"close stage reads body and learns only that another page exists");
        JSONObject intimate=WritingData.visible(r,85);check(intimate.optBoolean("hiddenAvailable")&&intimate.optBoolean("hiddenUnread"),"intimate stage can request the private page separately");
        check(!WritingData.visible(copy(r).put("secretRead",true),85).optBoolean("hiddenUnread"),"private-page read status is independent from regular-body read status");
        check(WritingData.visible(r,90,true,true).optBoolean("locked"),"cooldown closes both body and private-page access");
        JSONObject off=WritingData.visible(r,1,true,false);check(off.getString("text").equals(r.getString("text"))&&!off.has("hasHiddenPage")&&!off.has("hiddenAvailable"),"affinity-off mode reveals regular body without unlocking secret");
        check(DiaryData.shown(84,false,true).equals("full")&&DiaryData.shown(85,false,true).equals("secret"),"private-page threshold is exactly intimate 85");
        rejects(()->WritingData.validate(copy(r).put("kind","letter")),"letters cannot carry a hidden diary page");
        rejects(()->WritingData.validate(copy(r).put("secretRead","yes")),"invalid private read-state type rejected");
        rejects(()->WritingData.validate(copy(r).put("secret",new String(new char[12000]).replace('\0','x'))),"body and secret share the existing twelve-thousand-character bound");
        JSONObject personal=PersonalData.fresh();personal.getJSONObject("producer").put("timeZone","Asia/Tokyo");String user="你不用一直逞强，我会认真听你说的。";
        JSONObject analysis=new JSONObject().put("diaryMoment",new JSONObject().put("what","真乃第一次听到制作人说愿意认真听她说。 ").put("evidence",user));
        long sent=Instant.parse("2026-10-22T15:30:00Z").toEpochMilli();String before=personal.toString();JSONObject moment=DiaryData.moment(analysis,user,sent,personal);
        check(moment.getString("date").equals("2026-10-23")&&moment.getString("text").contains(user),"moment date uses original user timestamp and producer zone, retaining quoted evidence");
        check(personal.toString().equals(before),"noting a moment never changes affinity or personal counters");
        check(DiaryData.moment(null,user,sent,personal)==null,"ordinary unanalysed turn has no moment");
        check(DiaryData.moment(new JSONObject().put("diaryMoment",JSONObject.NULL),user,sent,personal)==null,"model may explicitly find no important event");
        JSONObject invented=copy(analysis);invented.getJSONObject("diaryMoment").put("evidence","昨天我们一起去看海");check(DiaryData.moment(invented,user,sent,personal)==null,"invented or history-only quote cannot become a current turn moment");
        invented.getJSONObject("diaryMoment").put("evidence","你");check(DiaryData.moment(invented,user,sent,personal)==null,"single-character slice is insufficient evidence");
        LocalDate today=LocalDate.parse("2026-10-24");long now=Instant.parse("2026-10-24T02:00:00Z").toEpochMilli();
        check(DiaryData.mayTry("2026-10-21",today,now,null),"oldest permitted diary is three producer days old");
        check(!DiaryData.mayTry("2026-10-20",today,now,null)&&!DiaryData.mayTry("2026-10-24",today,now,null)&&!DiaryData.mayTry("2026-10-25",today,now,null),"four-day-old, same-day and future automatic diaries are excluded");
        JSONObject attempt=new JSONObject().put("tries",1).put("at",now-DiaryData.RETRY+1);
        check(!DiaryData.mayTry("2026-10-23",today,now,attempt),"failed diary waits a full hour before retry");
        attempt.put("at",now-DiaryData.RETRY);check(DiaryData.mayTry("2026-10-23",today,now,attempt),"one-hour boundary allows second attempt");
        attempt.put("tries",2);check(!DiaryData.mayTry("2026-10-23",today,now,attempt),"two persisted attempts stop automatic requests");
        attempt.put("tries",1).put("at",now+1);boolean rollback=!DiaryData.mayTry("2026-10-23",today,now,attempt);attempt.put("at",Long.MAX_VALUE);check(rollback&&!DiaryData.mayTry("2026-10-23",today,now,attempt),"clock rollback or overflowing future timestamp cannot accelerate a retry");
        Set<String> known=new HashSet<>(Arrays.asList("mano"));JSONObject backup=new JSONObject().put("format","eidolon-android-chats").put("version",10).put("personal",personal).put("conversations",new JSONArray()).put("stories",new JSONArray()).put("writings",new JSONArray().put(copy(r).put("secretRead",true))).put("writingEvents",new JSONArray()).put("schedules",new JSONArray()).put("letterPlans",new JSONArray()).put("diaryAttempts",new JSONArray().put(new JSONObject().put("character","mano").put("date","2026-10-23").put("tries",1).put("at",now).put("error","offline failure")));
        BackupValidator.validate(backup,known);check(true,"v10 backup validates private pages and independent attempt ledger");
        JSONObject restored=new JSONObject(new String(BackupCrypto.decrypt(BackupCrypto.encrypt(backup.toString().getBytes("UTF-8"),"diary-private".toCharArray()),"diary-private".toCharArray()),"UTF-8"));
        check(restored.getJSONArray("writings").getJSONObject(0).getString("secret").equals(r.getString("secret"))&&restored.getJSONArray("writings").getJSONObject(0).getBoolean("secretRead"),"encrypted backup retains hidden body and its read flag");
        JSONObject invalid=copy(backup);invalid.getJSONArray("diaryAttempts").getJSONObject(0).put("tries",3);rejects(()->BackupValidator.validate(invalid,known),"excess retry budget rejected before restore");
        JSONObject duplicate=copy(backup);duplicate.getJSONArray("diaryAttempts").put(duplicate.getJSONArray("diaryAttempts").getJSONObject(0));rejects(()->BackupValidator.validate(duplicate,known),"duplicate diary attempt keys rejected");
        JSONObject old=copy(backup).put("version",9);old.remove("diaryAttempts");old.getJSONArray("writings").getJSONObject(0).remove("secret");old.getJSONArray("writings").getJSONObject(0).remove("secretRead");BackupValidator.validate(old,known);check(true,"v9 backup without private-page fields remains valid");
        System.out.println("Passed "+count+" diary checks. No API or Android runtime.");
    }
}
