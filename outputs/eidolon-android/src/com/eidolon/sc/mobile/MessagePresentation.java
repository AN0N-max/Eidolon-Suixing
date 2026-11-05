package com.eidolon.sc.mobile;
import android.content.Context;import org.json.*;import java.util.*;

/** Read compatibility only: original text/content rows are never rewritten. */
final class MessagePresentation {
 static JSONObject row(Context c,ChatStore s,String who,String session,ChatStore.Message m)throws Exception{
  JSONArray blocks=MediaData.copy(m.blocks);String text=m.text;
  if(m.role.equals("assistant")){
   JSONArray pool=new JSONArray();boolean legacy=text.contains("<sticker_id>")||text.matches("(?s).*\\bs-[a-f0-9]{64}\\b.*");
   if(legacy){String query="";for(ChatStore.Message prior:s.historyBefore(session,m.id,12))if(prior.role.equals("user"))query=prior.text;
    try{pool=StickerEngine.candidates(s,who,query,WritingEngine.character(WritingEngine.characters(c),who),new JSONObject(),m.sentAt>0?m.sentAt:System.currentTimeMillis(),m.id);}catch(Exception ignored){}
   }
   JSONArray safe=new JSONArray();if(blocks.length()==0&&legacy){try{blocks=MediaReply.parse(text,pool,new JSONObject(),false,false).blocks;}catch(Exception ignored){}}
   for(int i=0;i<blocks.length();i++){JSONObject b=new JSONObject(blocks.getJSONObject(i).toString());if(b.optString("type").equals("text")){JSONArray found=new JSONArray();b.put("text",ReplyText.clean(MediaReply.embedded(b.optString("text"),pool,found)));if(found.length()>0)safe.put(found.getJSONObject(0));if(b.getString("text").isEmpty())continue;b.put("dialogue",DialogueText.presentation(b.getString("text")));}
    else if(b.optString("type").equals("sticker")){try{if(!MediaStore.file(c,b.getString("asset"),false).isFile())continue;MediaStore.asset(s,b.getString("asset"));}catch(Exception missing){continue;}}
    safe.put(b);
   }blocks=safe;text=ReplyText.clean(text);if(m.blocks.length()>0&&blocks.length()==0)text="";
  }
  return new JSONObject().put("id",m.id).put("role",m.role).put("text",text).put("sentAt",m.sentAt).put("generatedAt",m.generatedAt).put("timeZone",m.zone).put("content",blocks).put("dialogue",m.role.equals("assistant")?DialogueText.presentation(text):JSONObject.NULL);
 }
}
