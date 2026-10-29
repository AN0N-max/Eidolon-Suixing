package com.eidolon.sc.mobile;

import java.io.*;
import java.util.*;
import org.json.*;

public class CoreTests {
    interface Action { void run() throws Exception; }
    static int count;
    static void check(boolean condition,String name) { if(!condition)throw new AssertionError(name);count++;System.out.println("PASS "+name); }
    static void rejects(Action action,String name)throws Exception {try{action.run();}catch(Exception expected){check(true,name);return;}throw new AssertionError(name);}
    static String event(String text)throws Exception{return "data: "+new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",text)))).toString()+"\n\n";}
    static String read(String stream)throws Exception{return new ModelClient().readEvents(new BufferedReader(new StringReader(stream)),s->{});}
    static JSONObject backup()throws Exception{return new JSONObject().put("format","eidolon-android-chats").put("version",1).put("conversations",new JSONArray().put(new JSONObject().put("character","mano").put("title","你好").put("messages",new JSONArray().put(new JSONObject().put("role","user").put("text","你好🌸")).put(new JSONObject().put("role","assistant").put("text","こんにちは")))));}
    public static void main(String[] args)throws Exception{
        check(ModelClient.endpoint("https://api.deepseek.com/").equals("https://api.deepseek.com/chat/completions"),"DeepSeek endpoint");
        check(ModelClient.endpoint("https://generativelanguage.googleapis.com/v1beta/openai/").equals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"),"Gemini endpoint");
        check(ModelClient.endpoint("https://example.com/v1/chat/completions").equals("https://example.com/v1/chat/completions"),"full endpoint not duplicated");
        rejects(()->ModelClient.endpoint("http://example.com"),"reject cleartext API");
        rejects(()->ModelClient.endpoint("https://user:pass@example.com"),"reject credentials in URL");
        rejects(()->ModelClient.endpoint("https://example.com?key=secret"),"reject query secrets");
        check(read(": keepalive\n\n"+event("你好")+event("🌸")+"data: [DONE]\n\n").equals("你好🌸"),"Unicode chunks and keepalive");
        check(read(event("回答")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n").equals("回答"),"finish_reason without DONE");
        check(read("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"private thinking\"}}]}\n\n"+event("answer")+"data: [DONE]\n\n").equals("answer"),"reasoning not exposed");
        rejects(()->read(event("partial")),"reject interrupted stream");
        rejects(()->read("data: [DONE]\n\n"),"reject empty answer");
        rejects(()->read(event("partial")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n"),"reject truncated reply");
        rejects(()->read("data: {\"error\":{\"message\":\"sensitive response\"}}\n\n"),"handle service error event");
        ModelClient cancelled=new ModelClient();cancelled.cancel();rejects(()->cancelled.readEvents(new BufferedReader(new StringReader(event("late")+"data: [DONE]\n\n")),s->{}),"cancelled reply not accepted");
        List<ChatStore.Message> history=new ArrayList<>();history.add(new ChatStore.Message("user",new String(new char[25000]).replace('\0','x')));history.add(new ChatStore.Message("assistant","old"));history.add(new ChatStore.Message("user","recent"));history.add(new ChatStore.Message("assistant","reply"));
        JSONArray request=ModelClient.request("model","persona",history,"new").getJSONArray("messages");
        check(request.length()==4 && request.getJSONObject(1).getString("content").equals("recent"),"bounded context drops whole old turns");
        char[] password="正确密码-1234".toCharArray();byte[] plain=backup().toString().getBytes("UTF-8");byte[] cipher=BackupCrypto.encrypt(plain,password);
        check(Arrays.equals(plain,BackupCrypto.decrypt(cipher,password)),"encrypted backup round trip");
        check(!Arrays.equals(cipher,BackupCrypto.encrypt(plain,password)),"random salt and IV");
        rejects(()->BackupCrypto.decrypt(cipher,"wrong-password".toCharArray()),"wrong password rejected");
        byte[] changed=cipher.clone();changed[changed.length-1]^=1;rejects(()->BackupCrypto.decrypt(changed,password),"tampered backup rejected");
        rejects(()->BackupCrypto.decrypt(Arrays.copyOf(cipher,40),password),"truncated backup rejected");
        rejects(()->BackupCrypto.encrypt(plain,"short".toCharArray()),"short password rejected");
        Set<String> known=new HashSet<>(Arrays.asList("mano"));BackupValidator.validate(backup(),known);check(true,"valid backup schema");
        JSONObject unknown=backup();unknown.getJSONArray("conversations").getJSONObject(0).put("character","unknown");rejects(()->BackupValidator.validate(unknown,known),"unknown character rejected");
        JSONObject odd=backup();odd.getJSONArray("conversations").getJSONObject(0).getJSONArray("messages").remove(1);rejects(()->BackupValidator.validate(odd,known),"incomplete turn rejected");
        JSONObject role=backup();role.getJSONArray("conversations").getJSONObject(0).getJSONArray("messages").getJSONObject(0).put("role","system");rejects(()->BackupValidator.validate(role,known),"system injection in backup rejected");
        JSONObject version=backup().put("version",99);rejects(()->BackupValidator.validate(version,known),"unknown backup version rejected");
        check(FishClient.speech("<ja>[softly]こんにちは</ja><zh>你好</zh><ja>元気ですか？</ja><zh>你好吗？</zh>").equals("こんにちは\n元気ですか？"),"voice only includes Japanese segments");
        check(FishClient.speech("<ja><whisper>内緒</whisper></ja><zh>秘密</zh>").equals("内緒"),"voice strips presentation tags");
        rejects(()->FishClient.speech("<zh>仅有翻译</zh>"),"voice rejects subtitle-only reply");
        JSONObject tts=FishClient.request("<ja>こんにちは</ja>","a1150f3d14ac46fb8ea59c7803cd8357","s2.1-pro-free");
        check(tts.getString("reference_id").equals("a1150f3d14ac46fb8ea59c7803cd8357")&&tts.getString("format").equals("pcm")&&tts.getInt("sample_rate")==44100,"0.9 streaming voice request preserves character identity and PCM rate");
        rejects(()->FishClient.request("hello","bad-id","s2.1-pro-free"),"voice rejects malformed voice ID");
        rejects(()->FishClient.request("hello","a1150f3d14ac46fb8ea59c7803cd8357","typo"),"unknown model cannot fall back to paid tier");
        JSONObject personal=PersonalData.fresh().put("day","2026-10-01");
        PersonalData.refresh(personal,"2026-10-20");check(personal.getInt("draws")==7,"daily draws capped after long absence");
        personal.put("draws",0);PersonalData.refresh(personal,"2026-10-19");check(personal.getInt("draws")==0,"clock rollback grants no draws");
        PersonalData.refresh(personal,"2026-10-21");check(personal.getInt("draws")==1,"next day grants one draw");
        JSONArray catalog=new JSONArray();for(String rarity:Arrays.asList("common","uncommon","rare"))catalog.put(new JSONObject().put("id",rarity).put("rarity",rarity));
        JSONArray drawn=PersonalData.draw(personal,catalog,false,new java.security.SecureRandom());check(drawn.length()==1&&personal.getInt("draws")==0&&personal.getJSONObject("inventory").getInt(drawn.getString(0))==1,"draw consumes chance and credits inventory");
        rejects(()->PersonalData.draw(personal,catalog,false,new java.security.SecureRandom()),"cannot draw with empty allowance");
        JSONObject gift=new JSONObject().put("id","cake").put("rarity","common"),ch=new JSONObject().put("id","mano").put("liked_gifts",new JSONArray().put("cake"));
        personal.getJSONObject("inventory").put("cake",2);
        check(PersonalData.give(personal,gift,ch)==6&&personal.getJSONObject("inventory").getInt("cake")==1,"liked gift bonus and exact inventory deduction");
        personal.getJSONObject("affinity").put("mano",99);check(PersonalData.give(personal,gift,ch)==1&&personal.getJSONObject("affinity").getInt("mano")==100,"affinity caps at 100");
        rejects(()->PersonalData.give(personal,gift,ch),"cannot give unowned gift");
        personal.getJSONObject("inventory").put("reset-card",1);personal.getJSONObject("affinity").put("hiori",10);PersonalData.reset(personal);
        check(personal.getJSONObject("affinity").getInt("hiori")==50&&personal.getJSONObject("affinity").getInt("mano")==100,"reset restores low scores without decreasing others");
        check(personal.getJSONObject("inventory").getInt("reset-card")==0,"reset card consumed");
        rejects(()->PersonalData.reset(personal),"cannot reuse consumed card");
        JSONObject v2=backup().put("version",2).put("personal",personal);BackupValidator.validate(v2,known);check(true,"v2 includes valid personal data");
        JSONObject invalid=new JSONObject(v2.toString());invalid.getJSONObject("personal").put("draws",-1);rejects(()->BackupValidator.validate(invalid,known),"v2 rejects invalid inventory state before restore");
        System.out.println("Passed "+count+" core checks. No live API used.");
    }
}
