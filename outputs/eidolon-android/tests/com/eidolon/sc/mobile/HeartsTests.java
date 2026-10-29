package com.eidolon.sc.mobile;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class HeartsTests {
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        JSONObject root=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8")),rows=root.getJSONObject("profiles");int count=0,minor=0,adult=0;
        for(Iterator<String> it=rows.keys();it.hasNext();){String who=it.next();JSONObject h=rows.getJSONObject(who);HeartsData.validate(h);count++;
            check(!HeartsData.judge(h).isEmpty(),who+" judge data");
            for(double score:new double[]{0,20,40,65,85,100})for(String gender:Arrays.asList("male","female")){String reply=HeartsData.reply(h,score,gender);check(reply.contains(h.getJSONObject("tiers").getString(HeartsData.tier(score))),who+" tier branch");if(h.getInt("age")<18&&score>=65)check(reply.contains("不是恋爱"),who+" minor trust boundary");}
            if(h.getInt("age")<18)minor++;else adult++;
        }
        check(count==28&&minor>0&&adult>0,"all 28 verified profiles loaded");
        JSONObject h=rows.getJSONObject("chiyuki");String before=h.toString();
        check(HeartsData.reply(h,85,"female").contains(h.getJSONObject("romance").getString("intimate_female")),"adult female profile");
        check(HeartsData.reply(h,65,"").contains(h.getJSONObject("romance").getString("close_male")),"default producer profile");
        check(!HeartsData.reply(h,40,"female").contains(h.getJSONObject("romance").getString("intimate_female")),"trust cannot use intimate romance");
        JSONObject questions=JevClient.questions(true,true),q=HeartsData.questions(h,questions);check(!questions.getJSONObject("affinity").getString("instructions").contains("特别让她开心"),"question template not mutated");
        for(String key:Arrays.asList("out_of_line","manipulation","needs_retrieval","recall","about_producer"))check(q.getJSONObject(key).toString().equals(questions.getJSONObject(key).toString()),"other judge question unchanged: "+key);
        check(q.getJSONObject("affinity").getJSONArray("criteria").toString().equals(questions.getJSONObject("affinity").getJSONArray("criteria").toString()),"five level criteria unchanged");check(before.equals(h.toString()),"reference data never mutated");
        JSONObject injected=new JSONObject(rows.getJSONObject("mano").toString()).put("romance",new JSONObject().put("intimate_female","invalid romance marker"));boolean rejected=false;try{HeartsData.validate(injected);}catch(Exception e){rejected=true;}check(rejected,"minor romance rejected at load");check(!HeartsData.reply(injected,100,"female").contains("invalid romance marker"),"minor runtime guard ignores injected romance");
        check(HeartsData.reply(null,100,"female").isEmpty()&&HeartsData.judge(null).isEmpty(),"unknown character keeps generic rules");
        System.out.println("PASS Hibi hearts: 28 validated profiles, all tier/gender branches, minor runtime/load guards, immutable judge schema and unknown-character fallback. No API used.");
    }
}
