package com.eidolon.sc.mobile;
import org.json.*;import java.util.*;import java.time.*;

/** Production payload/selection/backup semantics with synthetic resources. No network or Android image codec. */
public final class MediaTests {
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
    interface Action{void run()throws Exception;}
    static boolean rejects(Action a){try{a.run();return false;}catch(Exception e){return true;}}
    static JSONObject entry(String id)throws Exception{return new JSONObject().put("id","s-"+id).put("asset",id).put("name","开心").put("category","快乐").put("emotion","开心").put("description","笑着点头赞同，不代表真实行动").put("enabled",true).put("characters",new JSONArray());}
    static JSONObject reply(JSONArray rows)throws Exception{return new JSONObject().put("eidolon_chat",1).put("messages",rows).put("mentions",new JSONArray());}
    public static void main(String[] args)throws Exception{
        String hash=MediaData.hash("synthetic-image".getBytes("UTF-8"));check(MediaData.hash(hash)&&hash.equals(MediaData.hash("synthetic-image".getBytes("UTF-8"))),"content identity deduplicates the same asset for every role");
        int[] edge=MediaData.fitSize(4097,250),large=MediaData.fitSize(4000,4000);check(edge[0]<=4096&&(long)large[0]*large[1]<=6000000,"decoder rounding and rotated image dimensions still meet backup pixel bounds");
        check(!MediaData.hash("../private")&&rejects(()->MediaData.validate(new JSONArray().put(new JSONObject().put("type","image").put("asset","../../secret")),null)),"media references cannot become arbitrary paths or URLs");
        JSONObject sticker=entry(hash);JSONArray pool=new JSONArray().put(sticker);JSONObject snapshot=new JSONObject().put("now",1000);
        MediaReply.Result only=MediaReply.parse(reply(new JSONArray().put(new JSONObject().put("sticker_id",sticker.getString("id")))).toString(),pool,snapshot,true,false);
        check(only.blocks.length()==1&&only.blocks.getJSONObject(0).getString("type").equals("sticker")&&only.text.contains("表情包"),"AI can send only a validated sticker without fabricated spoken text");
        JSONObject text=new JSONObject().put("ja","よかったね").put("zh","太好了");
        MediaReply.Result before=MediaReply.parse(reply(new JSONArray().put(text).put(new JSONObject().put("sticker_id",sticker.getString("id")))).toString(),pool,snapshot,true,false);
        MediaReply.Result after=MediaReply.parse(reply(new JSONArray().put(new JSONObject().put("sticker_id",sticker.getString("id"))).put(text)).toString(),pool,snapshot,true,false);
        check(before.blocks.getJSONObject(0).getString("type").equals("text")&&after.blocks.getJSONObject(0).getString("type").equals("sticker"),"ordered text-before-sticker and sticker-before-text stay distinct");
        check(rejects(()->MediaReply.parse(reply(new JSONArray().put(new JSONObject().put("sticker_id","s-unknown"))).toString(),pool,snapshot,true,false)),"unknown or noncandidate sticker cannot be sent");
        check(rejects(()->MediaReply.parse(reply(new JSONArray().put(new JSONObject().put("sticker_id",sticker.getString("id"))).put(new JSONObject().put("sticker_id",sticker.getString("id")))).toString(),pool,snapshot,true,false)),"one reply cannot spam duplicate stickers");
        check(MediaReply.parse("<ja>うん</ja><zh>嗯</zh>",new JSONArray(),snapshot,true,false).blocks.length()==0,"legacy plain bilingual response remains usable without candidates");
        check(MediaReply.parse(reply(new JSONArray().put(text)).toString(),pool,snapshot,true,false).blocks.length()==1,"role may ignore available stickers and send text only");
        check(rejects(()->MediaReply.parse(reply(new JSONArray().put(text).put(text)).toString(),pool,snapshot,true,true))&&MediaReply.parse(reply(new JSONArray().put(text).put(new JSONObject().put("sticker_id",sticker.getString("id")))).toString(),pool,snapshot,true,true).blocks.length()==2,"proactive keeps one text bubble with optional sticker instead of increasing unsolicited text volume");
        JSONObject pref=new JSONObject().put("blocked",new JSONArray().put("快乐"));check(!MediaData.allowed(sticker,pref,"mano")&&MediaData.allowed(sticker,new JSONObject(),"hiori"),"the same sticker has different eligibility for different role preferences");
        sticker.put("characters",new JSONArray().put("mano"));check(MediaData.allowed(sticker,new JSONObject(),"mano")&&!MediaData.allowed(sticker,new JSONObject(),"hiori"),"resource role allowlist is enforced independently of preferences");
        sticker.put("enabled",false);check(!MediaData.allowed(sticker,new JSONObject(),"mano"),"disabled resource cannot be selected");sticker.put("enabled",true).put("characters",new JSONArray());
        JSONObject frozen=MediaData.snapshot(sticker);sticker.put("description","edited later");check(!frozen.getString("description").equals(sticker.getString("description")),"sent sticker semantics are snapshots, later edits do not rewrite history");
        JSONArray user=new JSONArray().put(frozen);check(MediaData.context(user).contains("表情意图需结合聊天")&&MediaData.knownSticker(frozen),"known sticker supplies semantics without another visual request");
        JSONObject knownPhoto=new JSONObject(frozen.toString()).put("type","image").put("known_semantics",true);check(MediaData.knownSticker(knownPhoto)&&MediaData.context(new JSONArray().put(knownPhoto)).contains("笑着点头"),"image matching an existing sticker reuses its semantic snapshot without automatic enrollment or visual retry");
        JSONArray images=new JSONArray().put(new JSONObject().put("type","image").put("asset",hash));check(MediaData.context(images).contains("不猜测图片内容"),"unrecognized image remains unknown instead of becoming a fabricated scene");
        JSONArray typed=MediaContext.imageContent("请看图",Collections.singletonList("data:image/png;base64,AA=="));check(typed.getJSONObject(0).getString("type").equals("text")&&typed.getJSONObject(1).getString("type").equals("image_url"),"native compatible vision request uses typed image content rather than base64 text");
        JSONObject observation=MediaData.observation("{\"scene\":\"可能是游戏画面\",\"ocr\":\"开始\",\"meme\":\"\"}");check(observation.getString("uncertainty").contains("可能出错")&&observation.getString("ocr").equals("开始"),"visual OCR and uncertainty are retained without assuming a meme or identity");
        check(!MediaData.cacheKey(hash,"https://a.example","vision-a").equals(MediaData.cacheKey(hash,"https://b.example","vision-a"))&&!MediaData.cacheKey(hash,"https://a.example","vision-a").equals(MediaData.cacheKey(hash,"https://a.example","vision-b")),"analysis cache is scoped to image, provider, model and protocol version");
        check(rejects(()->MediaData.observation("{\"emotion\":\"happy\"}")),"empty visual observations cannot pollute the cache");
        List<ChatStore.Message> batch=new ArrayList<>();for(int i=0;i<7;i++){String id=MediaData.hash(("image-"+i).getBytes("UTF-8"));batch.add(new ChatStore.Message(i+1,"user","[图片]",1000,"Asia/Shanghai",0,new JSONArray().put(new JSONObject().put("type","image").put("asset",id))));}
        Set<String> selected=MediaContext.newestImages(batch);check(selected.size()==4&&selected.contains(batch.get(6).blocks.getJSONObject(0).getString("asset"))&&!selected.contains(batch.get(0).blocks.getJSONObject(0).getString("asset")),"merged waiting batch processes only newest four unknown images");
        ChatStore.Message yesterday=new ChatStore.Message(90,"user","昨天的截图",Instant.parse("2026-11-01T15:59:00Z").toEpochMilli(),"Asia/Shanghai",0,images);
        JSONObject request=ModelClient.conversationRequest("fake","角色A的人设",Collections.emptyList(),Collections.singletonList(yesterday),Clock.fixed(Instant.parse("2026-11-02T01:00:00Z"),ZoneOffset.UTC),ZoneId.of("Asia/Shanghai"));
        check(request.getJSONArray("messages").getJSONObject(1).getString("content").contains(hash)&&request.getJSONArray("messages").getJSONObject(0).getString("content").contains("2026-11-01"),"actual final request retains attachment identity plus original message date across midnight");
        check(StickerEngine.prompt(new JSONArray(),new JSONObject()).isEmpty(),"no candidate means no invented resource payload");
        MediaData.validate(before.blocks,Collections.singleton(hash));check(rejects(()->MediaData.validate(before.blocks,Collections.emptySet())),"backup/history content requires a known immutable asset");
        byte[] synthetic={1,2,3};String id=MediaData.hash(synthetic);JSONObject media=new JSONObject().put("assets",new JSONArray().put(new JSONObject().put("id",id).put("mime","image/png").put("bytes",3).put("width",1).put("height",1).put("data",java.util.Base64.getEncoder().encodeToString(synthetic)))).put("stickers",new JSONArray()).put("profiles",new JSONArray());
        check(MediaStore.validateBackup(new JSONObject().put("media",media),Collections.singleton("mano")).contains(id),"bounded media archive validates content hashes before restore");
        media.getJSONArray("assets").getJSONObject(0).put("data","BAUG");check(rejects(()->MediaStore.validateBackup(new JSONObject().put("media",media),Collections.singleton("mano"))),"corrupted backup bytes cannot silently replace an image");
        check(new JSONArray(before.blocks.toString()).getJSONObject(0).getString("type").equals("text")&&new JSONArray(after.blocks.toString()).getJSONObject(0).getString("type").equals("sticker"),"serialized message contents preserve mixed ordering on restart");
    }
}
