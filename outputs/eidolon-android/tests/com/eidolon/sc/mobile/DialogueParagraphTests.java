package com.eidolon.sc.mobile;
import java.util.*;import org.json.*;
public class DialogueParagraphTests {
 static int count;static void check(boolean b,String s){if(!b)throw new AssertionError(s);count++;System.out.println("PASS "+s);}
 static List<String> zh(JSONObject p)throws Exception{List<String> out=new ArrayList<>();int prior=-1;StringBuilder current=new StringBuilder();JSONArray a=p.getJSONArray("parts");for(int i=0;i<a.length();i++){JSONObject part=a.getJSONObject(i);if(!part.getString("lang").equals("zh"))continue;int n=part.getInt("paragraph");if(prior!=-1&&prior!=n){out.add(current.toString());current.setLength(0);}prior=n;current.append(part.getString("text"));}if(current.length()>0)out.add(current.toString());return out;}
 public static void main(String[] args)throws Exception{
  String pairs="<ja>前半です。</ja><zh>中文第一段。\n保留行内换行。</zh><ja>後半です。</ja><zh>中文第二段。</zh>";
  check(zh(DialogueText.presentation(pairs)).equals(Arrays.asList("中文第一段。\n保留行内换行。","中文第二段。")),"display metadata preserves paired Chinese paragraphs and in-paragraph newline");
  String nested="<ja><ja>前半です。</ja>後半です。</ja><zh><zh>中文第一段。</zh></zh><ja>次です。</ja><zh>中文第二段。</zh>";
  check(zh(DialogueText.presentation(nested)).size()==2,"duplicate nested tags do not create spurious Chinese paragraphs");
  String inline="<zh>今天和ふゆ说「おはよう」，示例是 \u0060x<ja>code</ja>\u0060，然后继续。\n下一行还是同一段。</zh>";
  List<String> literal=zh(DialogueText.presentation(inline));check(literal.size()==1&&literal.get(0).contains("「おはよう」")&&literal.get(0).contains("\u0060x<ja>code</ja>\u0060"),"names Japanese quotations and inline code remain in one Chinese paragraph");
  String malformed="<ja>前半です。<zh>中文第一段。<ja>次です。<zh>中文第二段。";
  check(zh(DialogueText.presentation(malformed)).size()==2,"controlled incomplete nested pair recovery retains next paragraph");
  String adjacent="<zh>中文第一段。</zh><zh>中文第二段。</zh>";check(zh(DialogueText.presentation(adjacent)).size()==2,"independent Chinese fields preserve source paragraph boundary");
  ChatStore.Message message=new ChatStore.Message(7,"assistant",adjacent,1,"Asia/Shanghai");JSONObject row=MessagePresentation.row(null,null,"mano","s",message);check(row.getLong("id")==7&&zh(row.getJSONObject("dialogue")).size()==2,"actual historical row carries pre-canonical display boundary without changing id");
  JSONObject bubble=MessagePresentation.row(null,null,"mano","s",new ChatStore.Message(8,"assistant",adjacent+ChatEnvelope.BREAK+pairs,1,"Asia/Shanghai"));
  check(bubble.getJSONObject("dialogue").getJSONArray("bubbles").length()==2&&zh(bubble.getJSONObject("dialogue").getJSONArray("bubbles").getJSONObject(0)).size()==2,"each old bubble receives its original paragraph projection");
  check(message.text.equals(adjacent)&&row.getString("text").equals(ReplyText.clean(adjacent)),"display compatibility never rewrites stored original or canonical transport text");
  String speech=FishClient.speech(pairs);check(speech.equals("前半です。\n後半です。"),"Japanese speech remains complete existing track");
  String key=VoiceCache.key("fake",speech,"fake");for(int i=0;i<10;i++){JSONObject p=DialogueText.presentation(pairs);zh(p);check(p.getString("japaneseSpeech").equals(speech)&&VoiceCache.key("fake",FishClient.speech(pairs),"fake").equals(key),"repeated display projection preserves voice cache identity "+i);}
  check(ReplyText.clean(pairs).equals(ReplyText.clean(ReplyText.clean(pairs))),"paragraph metadata retains canonicalization idempotence");
  if(args.length>0){JSONArray fixtures=new JSONArray();for(String raw:new String[]{pairs,nested,inline,malformed,adjacent}){JSONObject r=MessagePresentation.row(null,null,"mano","s",new ChatStore.Message(30,"assistant",raw,1,"Asia/Shanghai"));fixtures.put(new JSONObject().put("raw",raw).put("text",r.getString("text")).put("projection",r.getJSONObject("dialogue")).put("expected",new JSONArray(zh(r.getJSONObject("dialogue")))));}fixtures.put(new JSONObject().put("raw",adjacent+ChatEnvelope.BREAK+pairs).put("text",bubble.getString("text")).put("projection",bubble.getJSONObject("dialogue")).put("history",true));java.nio.file.Files.write(java.nio.file.Paths.get(args[0]),fixtures.toString().getBytes("UTF-8"));}
  System.out.println("PASS "+count+" native paragraph/voice/history cases; no synthesis.");
 }
}
