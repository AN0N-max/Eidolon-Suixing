package com.eidolon.sc.mobile;
import java.io.IOException;import org.json.*;
/** Optional single-call protocol. Old providers may still return plain ja/zh dialogue. */
final class ChatEnvelope {
 static final String BREAK="\n\n<eidolon-bubble/>\n\n";
 static final class Result {final String text;final JSONObject effects;Result(String t,JSONObject e){text=t;effects=e;}}
 static String protocol(boolean proactive){return "\n一次请求完成聊天，不另行请求行为模型。优先只返回JSON：{\"eidolon_chat\":1,\"messages\":[{\"ja\":\"日语（仅中文模式可留空）\",\"zh\":\"中文台词\"}],\"mentions\":[{\"id\":\"本次生活参考中的事件ID\",\"quote\":\"本次确实说出的对应原句\"}],\"new_goal\":null}。messages为1到"+(proactive?1:3)+"条自然气泡，不为凑数量拆句；中文模式只填zh，双语模式每条ja/zh对应。mentions最多2项，只标确实分享的生活事件，无关则空数组。只有角色自然形成新的虚拟个人目标且尚无进行中目标时，new_goal可填{title,interest:practice/creative/outdoors/social/rest,steps:[{title,external:true或false}]}，2至5阶段；等待比赛、考试、真实外部结果的阶段external必须true。不要把用户待办当成角色个人目标，不声称真实设备操作完成。不得输出其他分析或内部字段。若服务不能遵守结构化格式，仍可直接输出原有ja/zh台词。";}
 static Result parse(String raw,JSONObject snapshot,boolean bilingual,boolean proactive)throws Exception{
  String text=raw.trim();if(text.startsWith("```")){int at=text.indexOf('\n');if(at>0&&text.endsWith("```"))text=text.substring(at+1,text.length()-3).trim();}
  if(!text.startsWith("{")){JSONObject legacy=new JSONObject().put("generatedAt",snapshot.optLong("now")).put("validUntil",snapshot.optLong("validUntil")).put("revision",snapshot.optLong("revision",-1)).put("mentions",new JSONArray());if(proactive&&snapshot.optJSONObject("proactive_cue")!=null)legacy.put("cueId",snapshot.getJSONObject("proactive_cue").getString("id"));return new Result(ReplyText.require(text),legacy);}
  JSONObject data;try{data=new JSONObject(text);}catch(Exception e){throw new IOException("角色回复结构不完整，本次未投放，请重试",e);}
  if(data.optInt("eidolon_chat")!=1)throw new IOException("角色回复协议无效，本次未投放");
  JSONArray messages=data.getJSONArray("messages");if(messages.length()<1||messages.length()>(proactive?1:3))throw new IOException("角色回复气泡数量无效");StringBuilder joined=new StringBuilder();
  for(int i=0;i<messages.length();i++){JSONObject m=messages.getJSONObject(i);String ja=m.optString("ja").trim(),zh=m.optString("zh").trim();if(zh.isEmpty()&&ja.isEmpty()||ja.length()>6000||zh.length()>6000||ja.contains("<")||zh.contains("<"))throw new IOException("角色台词为空、过长或格式无效");if(bilingual&&(ja.isEmpty()||zh.isEmpty()))throw new IOException("双语回复缺少对应台词，请重试");if(!bilingual&&zh.isEmpty())throw new IOException("中文回复缺少台词");if(i>0)joined.append(BREAK);if(bilingual)joined.append("<ja>").append(ja).append("</ja>");joined.append("<zh>").append(zh).append("</zh>");}
  String answer=ReplyText.require(joined.toString());JSONArray mentions=new JSONArray(),source=data.optJSONArray("mentions");
  if(source!=null)for(int i=0;i<Math.min(2,source.length());i++){JSONObject m=source.optJSONObject(i);if(m==null)continue;String quote=m.optString("quote");JSONObject event=LifeData.find(snapshot.optJSONArray("events"),m.optString("id"));if(quote.length()>=2&&quote.length()<=500&&answer.contains(quote)&&event!=null)mentions.put(new JSONObject().put("id",m.getString("id")).put("title",event.optString("title")).put("quote",quote));}
  JSONObject effect=new JSONObject().put("revision",snapshot.optLong("revision",-1)).put("mentions",mentions).put("generatedAt",snapshot.optLong("now")).put("validUntil",snapshot.optLong("validUntil"));
  if(snapshot.optJSONObject("behavior")!=null)effect.put("behavior",snapshot.getJSONObject("behavior"));
  if(proactive&&snapshot.optJSONObject("proactive_cue")!=null)effect.put("cueId",snapshot.getJSONObject("proactive_cue").getString("id"));
  if(data.optJSONObject("new_goal")!=null)effect.put("new_goal",data.getJSONObject("new_goal"));return new Result(answer,effect);
 }
 private ChatEnvelope(){}
}
