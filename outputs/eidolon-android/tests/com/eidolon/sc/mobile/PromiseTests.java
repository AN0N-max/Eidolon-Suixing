package com.eidolon.sc.mobile;

import org.json.*;
import java.time.*;
import java.util.*;

public class PromiseTests {
    static int count;
    interface Action{void run()throws Exception;}
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);count++;System.out.println("PASS "+text);}
    static void rejects(Action task,String title)throws Exception{boolean rejected=false;try{task.run();}catch(Exception e){rejected=true;}check(rejected,title);}
    static JSONObject fresh()throws Exception{return PersonalData.fresh();}
    static JSONObject row(JSONObject d,String task,LocalDate date)throws Exception{return PromiseData.create(d,"mano",task,date,date,"手动记录",false,"",new JSONArray());}
    static double score(JSONObject d)throws Exception{return d.getJSONObject("affinity").optDouble("mano",50);}
    static JSONObject suggestion(String op,String id,String task,String when,String evidence)throws Exception{return new JSONObject().put("op",op).put("id",id).put("task",task).put("when",when).put("evidence",evidence);}
    public static void main(String[] args)throws Exception{
        LocalDate friday=LocalDate.of(2026,10,23);
        check(PromiseDate.resolve("明天",friday)[0].equals(friday.plusDays(1)),"relative day resolved locally");
        check(PromiseDate.resolve("下周一",friday)[0].equals(LocalDate.of(2026,10,26)),"next week calculation follows producer week");
        check(PromiseDate.resolve("周末",friday)[1].equals(LocalDate.of(2026,10,25)),"weekend retains two-day range");
        check(PromiseDate.resolve("周末",friday.plusDays(2))[0].equals(friday.plusDays(2)),"Sunday weekend range starts today instead of past Saturday");
        check(PromiseDate.resolve("月底前",friday)[0].equals(friday)&&PromiseDate.resolve("月底前",friday)[1].getDayOfMonth()==31,"before month end retains an interval");
        check(PromiseDate.resolve("1月3日",friday)[0].equals(LocalDate.of(2027,1,3)),"month/day crosses year locally");
        rejects(()->PromiseDate.resolve("2026-02-30",friday),"invalid calendar date rejected");
        rejects(()->PromiseDate.resolve("本周一",friday),"past day not silently rescheduled");
        rejects(()->PromiseDate.resolve("有空的时候",friday),"ambiguous future wording not invented");
        rejects(()->PromiseDate.resolve("2030-01-01",friday),"unbounded future date rejected");
        rejects(()->PromiseDate.check(friday,friday.plusDays(32),friday),"oversized date range rejected");
        JSONObject d=fresh();LocalDate today=WritingData.today(d);JSONArray events=new JSONArray();
        JSONObject p=row(d,"一起散步",today.plusDays(1));String id=p.getString("id");
        check(PromiseData.list(d).length()==1,"manual commitment stored in personal data");
        row(d,"一起散步",today.plusDays(1));check(PromiseData.list(d).length()==1,"duplicate task/date is idempotent");
        JSONObject other=PromiseData.create(d,"hiori","一起散步",today.plusDays(1),today.plusDays(1),"",false,"",events);check(!other.getString("id").equals(id),"identical commitments remain isolated by character");
        check(PromiseData.cost(p,"move",today)==0,"first advance reschedule has no penalty");
        PromiseData.change(d,p,"move",today.plusDays(2),today.plusDays(2),today,true,events);
        check(p.getInt("changes")==1&&score(d)==50,"reschedule changes dates without first advance penalty");
        check(!PromiseData.change(d,p,"move",today.plusDays(2),today.plusDays(2),today,true,events)&&p.getInt("changes")==1,"same-date save never counts as another reschedule");
        PromiseData.change(d,p,"move",today.plusDays(3),today.plusDays(3),today,true,events);check(score(d)==49,"repeated advance reschedule deducts one");
        PromiseData.change(d,p,"complete",null,null,today,true,events);check(score(d)>50.99&&score(d)<51.01,"completion awards two");
        double saved=score(d);check(!PromiseData.change(d,p,"complete",null,null,today,true,events)&&score(d)==saved,"repeated completion cannot award twice");
        check(PromiseData.context(d,"mano").length()==0,"finished task removed from active model context");
        JSONObject late=fresh(),lp=row(late,"陪你去公园",today);JSONObject cue=PromiseData.cue(late,"mano",today.plusDays(1),13);
        check(cue!=null&&cue.getString("key").equals("complainedDay")&&score(late)==50&&!lp.has("complainedDay"),"reading overdue cue never charges or consumes it");
        check(!PromiseData.acknowledge(late,cue,"今天吃得好吗？",true,events)&&!lp.has("complainedDay"),"unmentioned task cannot consume reminder or change score");
        check(!PromiseData.acknowledge(late,cue,"陪你去公园，很开心！",true,events),"task mention without disappointment is not an overdue complaint");
        check(PromiseData.acknowledge(late,cue,"陪你去公园，这件事还没做，有些失落呢。",true,events)&&Math.abs(score(late)-47)<.01,"actual saved complaint deducts three once");
        saved=score(late);check(!PromiseData.acknowledge(late,cue,"陪你去公园，还没做呢。",true,events)&&score(late)==saved,"duplicate complaint never deducts again");
        check(PromiseData.cue(late,"mano",today.plusDays(2),13)==null,"complaint is not repeated on following days");
        PromiseData.change(late,lp,"complete",null,null,today.plusDays(2),true,events);check(Math.abs(score(late)-48)<.01,"completion after complaint awards only one");
        JSONObject floor=fresh();Progression.relation(floor,"mano",System.currentTimeMillis());floor.getJSONObject("affinity").put("mano",11);PromiseData.adjust(floor,"mano",-3,true);check(score(floor)==10,"promise setback has floor ten");
        JSONObject cool=Progression.relation(floor,"mano",System.currentTimeMillis());cool.put("cooling",true);floor.getJSONObject("affinity").put("mano",2);PromiseData.adjust(floor,"mano",2,true);check(score(floor)<2.01&&cool.getBoolean("cooling"),"commitments cannot thaw an existing cooldown");
        JSONObject off=fresh();JSONObject op=row(off,"明天散步",today);PromiseData.change(off,op,"cancel",null,null,today,false,events);check(score(off)==50&&op.getString("state").equals("cancelled"),"disabled affinity still updates status without score effects");
        JSONObject dates=fresh();JSONObject dp=row(dates,"看电影",today.plusDays(1));check(PromiseData.cue(dates,"mano",today,10).optString("key").equals("remindedDay"),"day-before reminder");
        check(PromiseData.cue(dates,"mano",today.plusDays(1),11)==null,"due-day question waits until afternoon");
        JSONObject due=PromiseData.cue(dates,"mano",today.plusDays(1),12);check(due.getString("key").equals("askedDay"),"due-day afternoon question");
        PromiseData.change(dates,dp,"move",today.plusDays(3),today.plusDays(3),today,false,events);check(!PromiseData.acknowledge(dates,due,"看电影，今天怎么安排？",true,events),"stale cue cannot mark a rescheduled commitment");
        check(PromiseData.cue(dates,"mano",today.plusDays(7),12)==null,"long overdue commitments no longer trigger automatic complaints");
        JSONObject extraction=fresh();JSONArray gifts=new JSONArray().put(new JSONObject().put("id","book").put("name","书"));
        String words="我明天给你买书";JSONArray proposals=new JSONArray().put(suggestion("create","","给你买书","明天",words));
        PromiseData.extract(extraction,"mano",proposals,words,today,gifts,true,events);check(PromiseData.list(extraction).length()==1&&PromiseData.list(extraction).getJSONObject(0).getString("giftId").equals("book"),"literal dated commitment accepted and bound to catalog gift");
        PromiseData.extract(extraction,"mano",proposals,"我没有这么说过",today,gifts,true,events);check(PromiseData.list(extraction).length()==1,"invented model evidence rejected");
        JSONObject negative=fresh();PromiseData.extract(negative,"mano",new JSONArray().put(suggestion("create","","给你买书","明天","明天给你买书")),"我不会明天给你买书",today,gifts,true,events);check(PromiseData.list(negative).length()==0,"sliced evidence cannot hide user negation");
        PromiseData.extract(negative,"mano",new JSONArray().put(suggestion("create","","给你买书","下个月","我下个月给你买书")),"我下个月给你买书",today,gifts,true,events);check(PromiseData.list(negative).length()==0,"ambiguous model date never persisted");
        PromiseData.extract(negative,"mano",new JSONArray().put(suggestion("create","","陪你去书店买书","明天","我明天陪你去书店买书")),"我明天陪你去书店买书",today,gifts,true,events);check(PromiseData.list(negative).getJSONObject(0).optString("giftId").isEmpty(),"outing is not fulfilled by an unrelated catalog book");
        PromiseData.gift(extraction,"hiori","book",today,true,events);check(PromiseData.active(PromiseData.list(extraction).getJSONObject(0)),"gift for another character cannot fulfill commitment");
        PromiseData.gift(extraction,"mano","book",today,true,events);check(PromiseData.list(extraction).getJSONObject(0).getString("state").equals("completed"),"matching gift fulfills tomorrow commitment");
        saved=score(extraction);PromiseData.gift(extraction,"mano","book",today,true,events);check(saved==score(extraction),"repeated gift cannot repay completed promise");
        JSONObject jury=fresh();for(int i=0;i<5;i++)row(jury,"散步 "+i,today);row(jury,"以后看电影",today.plusDays(2));
        JSONArray eligible=PromiseData.eligible(jury,"mano",today);check(eligible.length()==3,"judgement limited to three eligible promises per turn");
        JSONObject answers=new JSONObject().put("promise_0",new JSONObject().put("noul",.79)).put("promise_1",new JSONObject().put("noul",.8)).put("promise_2",new JSONObject().put("noul","Infinity"));
        PromiseData.fulfilled(jury,"mano",eligible,answers,"已经完成散步了",today,true,events);check(PromiseData.active(eligible.getJSONObject(0))&&!PromiseData.active(eligible.getJSONObject(1))&&PromiseData.active(eligible.getJSONObject(2)),"fulfillment threshold 0.8 and invalid probability isolation");
        PromiseData.fulfilled(jury,"mano",eligible,new JSONObject().put("promise_0",new JSONObject().put("noul",1)),"还没完成呢",today,true,events);check(PromiseData.active(eligible.getJSONObject(0)),"completion wording guard rejects negative statement despite bad model answer");
        JSONObject questions=PromiseData.questions(eligible,new JSONObject());check(questions.length()==3&&questions.getJSONObject("promise_0").has("criteria")&&questions.getJSONObject("promise_0").has("instructions"),"additional questions use actual Jev/Bailian System One schema");
        JSONObject chats=fresh();JSONObject cp=row(chats,"今天来找你聊天",today);JSONArray prior=new JSONArray(PromiseData.list(chats).toString());PromiseData.chatTurn(chats,"mano",prior,today,true,events);check(!PromiseData.active(cp)&&score(chats)==52,"subsequent saved chat fulfills an explicit chatting commitment");
        JSONObject phone=row(chats,"今天和你聊天并打电话",today);check(!PromiseData.chatOnly(phone),"a chat turn cannot fulfill a phone-call commitment");
        JSONObject cancelled=fresh();JSONObject can=row(cancelled,"今天来找你聊天",today);JSONArray beforeCancel=new JSONArray(PromiseData.list(cancelled).toString());
        PromiseData.extract(cancelled,"mano",new JSONArray().put(suggestion("cancel",can.getString("id"),"","","取消约定")),"取消约定",today,gifts,false,events);
        PromiseData.chatTurn(cancelled,"mano",beforeCancel,today,true,events);check(can.getString("state").equals("cancelled")&&score(cancelled)==50,"explicit cancellation takes priority over automatic chat fulfillment");
        JSONObject question=fresh();JSONObject q=row(question,"散步",today);
        PromiseData.extract(question,"mano",new JSONArray().put(suggestion("cancel",q.getString("id"),"","","取消约定")),"要不要取消约定？",today,gifts,true,events);check(PromiseData.active(q),"question cannot cancel a commitment even if evidence slices out punctuation");
        PromiseData.extract(question,"mano",new JSONArray().put(suggestion("cancel",q.getString("id"),"","","取消约定")),"不要取消约定",today,gifts,true,events);check(PromiseData.active(q),"negative cancellation cannot be accepted");
        check(!PromiseData.mayReportDone("我已经计划好明天去完成")&&PromiseData.mayReportDone("我已经把书买好了"),"cheap local wording gate avoids unnecessary fulfillment API checks");
        check(!PromiseData.commitment("我明天不陪你聊天")&&!PromiseData.commitment("我没说明天送你礼物"),"negative action and denied promise cannot pass local commitment guard");
        JSONObject story=fresh();String storyWords="我明天给你讲书里的故事";PromiseData.extract(story,"mano",new JSONArray().put(suggestion("create","","给你讲书里的故事","明天",storyWords)),storyWords,today,gifts,true,events);
        check(PromiseData.list(story).length()==1&&PromiseData.list(story).getJSONObject(0).optString("giftId").isEmpty(),"telling a book story is not mistaken for gifting the book");
        JSONObject backup=new JSONObject().put("format","eidolon-android-chats").put("version",8).put("personal",extraction).put("conversations",new JSONArray()).put("stories",new JSONArray()).put("writings",new JSONArray()).put("writingEvents",new JSONArray()).put("schedules",new JSONArray());
        BackupValidator.validate(backup,Collections.singleton("mano"));check(new JSONObject(backup.toString()).getJSONObject("personal").getJSONArray("promises").length()==1,"v8 backup round-trips promises with evidence and terminal status");
        rejects(()->BackupValidator.validate(backup,Collections.singleton("hiori")),"backup promise with unknown character rejected");
        JSONObject old=new JSONObject(backup.toString()).put("version",7);old.getJSONObject("personal").remove("promises");BackupValidator.validate(old,Collections.singleton("mano"));check(true,"v7 backup without commitments remains compatible");
        JSONObject invalid=new JSONObject(extraction.toString());invalid.getJSONArray("promises").getJSONObject(0).put("state","invented");rejects(()->PromiseData.validate(invalid,null),"invalid imported status rejected");
        JSONObject duplicate=new JSONObject(extraction.toString());duplicate.getJSONArray("promises").put(new JSONObject(duplicate.getJSONArray("promises").getJSONObject(0).toString()));rejects(()->PromiseData.validate(duplicate,null),"duplicate imported promise IDs rejected");
        System.out.println("TOTAL "+count+" promise checks passed; no device or live API used.");
    }
}
