package com.eidolon.sc.mobile;

import java.time.*;
import java.util.*;
import org.json.*;

public class LetterPlanTests {
    static int count;
    static final Set<String> known=new HashSet<>(Arrays.asList("mano"));
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    static void rejects(ScheduleTests.Action f,String name)throws Exception{try{f.run();}catch(Exception expected){check(true,name);return;}throw new AssertionError(name);}
    static class Draw extends Random {final int value;final List<Integer> bounds=new ArrayList<>();Draw(int value){this.value=value;}@Override public int nextInt(int bound){bounds.add(bound);return Math.floorMod(value,bound);}}
    static JSONObject calendar()throws Exception{return new JSONObject().put("birthdays",new JSONArray().put(new JSONObject().put("character","mano").put("date","03-01"))).put("holidays",new JSONArray().put(new JSONObject().put("name","元旦").put("date","01-01")));}
    static ZonedDateTime at(String value){return ZonedDateTime.parse(value+"+08:00[Asia/Shanghai]");}
    static JSONObject plan(JSONObject data,JSONObject calendar,double score,boolean affinity,String last,String time,Draw draw)throws Exception{
        ZonedDateTime now=at(time);return LetterPlanData.plan("mano",now.toLocalDate(),now,calendar,data,score,affinity,last,draw);
    }
    static JSONObject copy(JSONObject r)throws Exception{return new JSONObject(r.toString());}
    public static void main(String[] args)throws Exception{
        JSONObject personal=PersonalData.fresh(),cal=calendar();personal.getJSONObject("producer").put("timeZone","Asia/Shanghai").put("birthMonth",10).put("birthDay",24);
        ZonedDateTime eve=at("2026-10-23T20:00:00");
        JSONObject birthday=LetterPlanData.plan("mano",eve.toLocalDate().plusDays(1),eve,cal,personal,65,true,"",new Draw(0));
        check(birthday.getString("occasion").equals("producer_birthday"),"close character plans producer birthday the evening before");
        check(birthday.getLong("deliverAt")==at("2026-10-24T00:00:00").toInstant().toEpochMilli(),"birthday letter is held for producer-local midnight");
        check(birthday.getLong("writeAt")==at("2026-10-23T21:00:00").toInstant().toEpochMilli(),"birthday generation starts three hours before delivery");
        LetterPlanData.validate(birthday,known);check(true,"pending birthday record validates");
        check(LetterPlanData.plan("mano",eve.toLocalDate().plusDays(1),eve,cal,personal,50,true,"",new Draw(0))==null,"trust stage cannot plan producer birthday early");
        check(LetterPlanData.plan("mano",eve.toLocalDate().plusDays(2),eve,cal,personal,85,true,"",new Draw(0))==null,"only tomorrow's producer birthday is preplanned");
        check(LetterPlanData.plan("mano",eve.toLocalDate().plusDays(1),eve,cal,personal,10,false,"",new Draw(0)).getString("occasion").equals("producer_birthday"),"affinity-off mode permits birthday letters without score gate");
        JSONObject morning=plan(personal,cal,85,true,"","2026-10-24T07:00:00",new Draw(0));
        check(morning.getLong("deliverAt")==at("2026-10-24T08:00:00").toInstant().toEpochMilli(),"birthday planned after midnight uses morning fallback");
        check(plan(personal,cal,85,true,"","2026-10-24T11:00:00",new Draw(0)).getString("state").equals("none"),"missed birthday morning is not silently made up");
        JSONObject own=plan(personal,cal,50,true,"2026-02-28","2026-03-01T10:00:00",new Draw(0));
        check(own.getString("occasion").equals("her_birthday")&&own.getLong("deliverAt")==at("2026-03-01T19:00:00").toInstant().toEpochMilli(),"own birthday bypasses ordinary gap and arrives in the evening");
        check(own.getLong("writeAt")==at("2026-03-01T18:00:00").toInstant().toEpochMilli(),"own birthday is written one hour ahead");
        check(plan(personal,cal,39,true,"","2026-03-01T10:00:00",new Draw(0)).getString("state").equals("none"),"low relationship cannot receive automatic birthday mail");
        check(plan(personal,cal,65,true,"","2026-01-01T12:00:00",new Draw(0)).getString("occasion").equals("holiday"),"holiday letter requires close relation");
        check(!plan(personal,cal,50,true,"","2026-01-01T12:00:00",new Draw(0)).getString("occasion").equals("holiday"),"trust stage holiday uses ordinary chance instead");
        JSONObject ordinary=plan(personal,cal,50,true,"","2026-10-23T08:00:00",new Draw(0));
        check(ordinary.getString("occasion").equals("ordinary")&&ordinary.getLong("deliverAt")==at("2026-10-23T09:00:00").toInstant().toEpochMilli(),"ordinary letter arrives within 09 to 21 window");
        check(ordinary.getLong("writeAt")==at("2026-10-23T08:30:00").toInstant().toEpochMilli(),"ordinary letter is written thirty minutes ahead");
        JSONObject late=plan(personal,cal,85,true,"","2026-10-23T20:59:00",new Draw(994));
        check(late.getString("state").equals("pending")&&late.getLong("deliverAt")<at("2026-10-23T21:00:00").toInstant().toEpochMilli(),"late planning draws only within the remaining delivery window");
        check(plan(personal,cal,85,true,"","2026-10-23T21:00:00",new Draw(0)).getString("state").equals("none"),"ordinary letter window is not extended after 21");
        check(plan(personal,cal,85,true,"2026-10-19","2026-10-23T08:00:00",new Draw(0)).getString("state").equals("none"),"ordinary mail observes five calendar days between letters");
        check(plan(personal,cal,85,true,"2026-10-18","2026-10-23T08:00:00",new Draw(0)).getString("state").equals("pending"),"ordinary mail may be drawn after five days");
        check(plan(personal,cal,85,true,"","2026-10-23T08:00:00",new Draw(1)).getString("state").equals("none"),"failed chance is a persisted no-letter plan");
        for(int score:new int[]{50,65,85}){Draw draw=new Draw(0);plan(personal,cal,score,true,"","2026-10-23T08:00:00",draw);check(draw.bounds.get(0)==(score==85?7:score==65?12:20),"Hibi one-in-N chance for score "+score);}
        JSONObject leap=copy(personal);leap.getJSONObject("producer").put("birthMonth",2).put("birthDay",29);
        check(LetterPlanData.producerBirthday(leap,LocalDate.parse("2027-02-28"))&&!LetterPlanData.producerBirthday(leap,LocalDate.parse("2028-02-28")),"leap birthday falls back only in non-leap years");
        long due=ordinary.getLong("deliverAt");check(!LetterPlanData.expired(ordinary,due+LetterPlanData.HOUR)&&LetterPlanData.expired(ordinary,due+LetterPlanData.HOUR+1),"unwritten letter expires after one hour rather than a later-day backfill");
        check(LetterPlanData.expired(copy(ordinary).put("attempts",3),due),"three attempts exhaust automatic generation budget");
        JSONObject ready=copy(birthday).put("state","ready").put("text","<ja>お誕生日おめでとう</ja><zh>生日快乐</zh>");LetterPlanData.validate(ready,known);
        JSONObject backup=new JSONObject().put("format","eidolon-android-chats").put("version",9).put("personal",personal).put("conversations",new JSONArray()).put("stories",new JSONArray()).put("writings",new JSONArray()).put("writingEvents",new JSONArray()).put("schedules",new JSONArray()).put("letterPlans",new JSONArray().put(ready));
        BackupValidator.validate(backup,known);check(true,"v9 backup validates staged letters separately from delivered writings");
        String decoded=new String(BackupCrypto.decrypt(BackupCrypto.encrypt(backup.toString().getBytes("UTF-8"),"letters-backup".toCharArray()),"letters-backup".toCharArray()),"UTF-8");
        check(new JSONObject(decoded).getJSONArray("letterPlans").getJSONObject(0).getString("text").equals(ready.getString("text")),"encrypted backup retains staged body and delivery schedule");
        rejects(()->LetterPlanData.validate(copy(ready).put("character","unknown"),known),"unknown staged recipient rejected");
        rejects(()->LetterPlanData.validate(copy(ready).put("text",""),known),"empty ready letter rejected");
        rejects(()->LetterPlanData.validate(copy(ready).put("state","delivered"),known),"delivered plans cannot keep a second copy of the body");
        rejects(()->LetterPlanData.validate(copy(ready).put("deliverAt",due),known),"delivery instant must match stored producer-local day");
        rejects(()->LetterPlanData.validate(copy(ready).put("writeAt",ready.getLong("deliverAt")+1),known),"generation cannot start after planned delivery");
        rejects(()->LetterPlanData.validate(copy(ready).put("attempts",4),known),"unbounded retries rejected on restore");
        rejects(()->LetterPlanData.validate(copy(ready).put("zone","invalid-zone"),known),"invalid saved timezone rejected");
        JSONObject duplicate=copy(backup);duplicate.getJSONArray("letterPlans").put(ready);rejects(()->BackupValidator.validate(duplicate,known),"duplicate letter plans rejected before database changes");
        JSONObject legacy=copy(backup).put("version",8);legacy.remove("letterPlans");BackupValidator.validate(legacy,known);check(true,"old v8 backup remains importable without letter plans");
        System.out.println("Passed "+count+" letter planning checks. No API, Android runtime or live data.");
    }
}
