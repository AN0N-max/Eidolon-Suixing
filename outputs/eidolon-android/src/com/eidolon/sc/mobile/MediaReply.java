package com.eidolon.sc.mobile;
import org.json.*;import java.io.IOException;

/** Validate ordered text/sticker outputs before staging; IDs must belong to the retrieved pool. */
final class MediaReply {
    static final class Result {final String text;final JSONObject effects;final JSONArray blocks;Result(String t,JSONObject l,JSONArray b){text=t;effects=l;blocks=b;}}
    static Result parse(String raw,JSONArray pool,JSONObject snapshot,boolean bilingual,boolean proactive)throws Exception{try{return decode(raw,pool,snapshot,bilingual,proactive);}catch(JSONException e){throw new IOException("多模态回复结构不完整，本轮未投放，请手动重试");}}
    private static Result decode(String raw,JSONArray pool,JSONObject snapshot,boolean bilingual,boolean proactive)throws Exception{
        String value=raw.trim();if(value.startsWith("```")){int at=value.indexOf('\n');if(at>0&&value.endsWith("```"))value=value.substring(at+1,value.length()-3).trim();}
        if(!value.startsWith("{")){
            JSONArray extras=new JSONArray();String text=embedded(raw,pool,extras);ChatEnvelope.Result legacy=ChatEnvelope.parse(text.replaceAll("(?s)</?(?:ja|zh)>","").trim().isEmpty()&&extras.length()>0?"[表情包]":text,snapshot,bilingual,proactive);
            if(extras.length()==0)return new Result(legacy.text,legacy.effects,new JSONArray());JSONArray blocks=new JSONArray();if(!text.replaceAll("(?s)</?(?:ja|zh)>","").trim().isEmpty())blocks.put(new JSONObject().put("type","text").put("text",legacy.text));blocks.put(extras.getJSONObject(0));return new Result(body(blocks),legacy.effects,blocks);
        }
        JSONObject json=new JSONObject(value);if(json.optInt("eidolon_chat")!=1)throw new IOException("多模态回复协议无效");JSONArray messages=json.getJSONArray("messages"),texts=new JSONArray(),blocks=new JSONArray();int stickers=0;
        if(messages.length()<1||messages.length()>4)throw new IOException("回复内容块数量无效");
        for(int i=0;i<messages.length();i++){
            JSONObject message=new JSONObject(messages.getJSONObject(i).toString());JSONArray extras=new JSONArray();for(String lang:new String[]{"ja","zh"})if(message.has(lang))message.put(lang,embedded(message.getString(lang),pool,extras));
            if(message.has("sticker_id")){
                String id=message.getString("sticker_id");JSONObject candidate=null;for(int j=0;j<pool.length();j++)if(pool.getJSONObject(j).getString("id").equals(id))candidate=pool.getJSONObject(j);
                if(candidate==null)continue;if(++stickers>1)throw new IOException("同一回复不能重复发送多张表情包");blocks.put(MediaData.snapshot(candidate));
            }else{
                JSONObject single=new JSONObject(json.toString()).put("messages",new JSONArray().put(message));ChatEnvelope.Result parsed=ChatEnvelope.parse(single.toString(),snapshot,bilingual,false);texts.put(message);blocks.put(new JSONObject().put("type","text").put("text",parsed.text));if(extras.length()>0&&stickers==0){blocks.put(extras.getJSONObject(0));stickers++;}
            }
        }
        if(proactive&&(texts.length()>1||blocks.length()>2))throw new IOException("主动消息只能有一段文字和可选的一张表情包");
        if(blocks.length()==0)throw new IOException("回复未包含可用文字或允许的表情包");JSONObject sanitized=new JSONObject(json.toString()).put("messages",texts.length()==0?new JSONArray().put(new JSONObject().put("ja","スタンプ").put("zh","表情包")):texts);
        ChatEnvelope.Result parsed=ChatEnvelope.parse(sanitized.toString(),snapshot,bilingual,false);
        if(proactive&&snapshot.optJSONObject("proactive_cue")!=null)parsed.effects.put("cueId",snapshot.getJSONObject("proactive_cue").getString("id"));
        return new Result(body(blocks),parsed.effects,blocks);
    }
    static String embedded(String text,JSONArray pool,JSONArray found){return ReplyMarkup.transform(text,id->{try{for(int i=0;i<pool.length();i++){JSONObject candidate=pool.getJSONObject(i);if(candidate.getString("id").equals(id)&&found.length()==0){found.put(MediaData.snapshot(candidate));break;}}}catch(Exception ignored){}return "";});}
    static String body(JSONArray blocks)throws Exception{StringBuilder out=new StringBuilder();for(int i=0;i<blocks.length();i++){if(i>0)out.append(ChatEnvelope.BREAK);JSONObject b=blocks.getJSONObject(i);out.append(b.optString("type").equals("text")?b.getString("text"):"[表情包："+b.optString("name")+"]");}return out.toString();}
}
