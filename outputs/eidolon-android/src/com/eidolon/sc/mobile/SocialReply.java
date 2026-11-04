package com.eidolon.sc.mobile;
import java.io.IOException;import java.util.*;import org.json.*;
/** Bounded compatibility decoder. Never completes truncated JSON, invents fields or calls a model. */
final class SocialReply {
 static final class Invalid extends IOException {final String code;Invalid(String code,String message){super(message);this.code=code;}}
 static JSONObject object(String raw)throws Exception{
  if(raw==null||raw.trim().isEmpty())throw new Invalid("empty","服务返回为空");if(raw.length()>64000)throw new Invalid("too_large","服务返回超过动态长度上限");
  String text=raw.trim();if(text.startsWith("\uFEFF"))text=text.substring(1).trim();
  if(text.startsWith("```")){int nl=text.indexOf('\n');if(nl<0||!text.endsWith("```"))throw new Invalid("truncated","动态JSON或代码围栏未完整结束");text=text.substring(nl+1,text.length()-3).trim();}
  if(text.startsWith("\"")){try{JSONTokener tokener=new JSONTokener(text);Object v=tokener.nextValue();if(tokener.nextClean()!=0)throw new Invalid("ambiguous","编码的动态JSON后还有其他内容");if(v instanceof String)return objectOnce((String)v);}catch(Invalid e){throw e;}catch(JSONException e){throw new Invalid("invalid_json","动态JSON字符串编码无效");}}
  return objectOnce(text);
 }
 static JSONObject objectOnce(String text)throws Exception{
  ArrayList<String> roots=new ArrayList<>();int begin=-1,depth=0;boolean quoted=false,escaped=false;char root=0;
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(begin<0){if(c=='{'||c=='['){begin=i;root=c;depth=1;}continue;}
   if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;continue;}
   if(c=='"'){quoted=true;continue;}if(c=='{'||c=='['){if(++depth>32)throw new Invalid("depth","动态JSON嵌套过深");}
   else if(c=='}'||c==']'){if(--depth==0){roots.add(text.substring(begin,i+1));begin=-1;}}
  }
  if(begin>=0||quoted)throw new Invalid("truncated","动态JSON在字段或正文结束前被截断");
  if(roots.size()!=1)throw new Invalid(roots.isEmpty()?"not_json":"ambiguous","服务未返回唯一可用的动态JSON");
  Object value;try{value=new JSONTokener(roots.get(0)).nextValue();}catch(JSONException e){throw new Invalid("invalid_json","动态JSON语法无效");}
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;if(a.length()!=1||a.optJSONObject(0)==null)throw new Invalid("array_count","一次动态只能包含一个正文对象");value=a.getJSONObject(0);}
  if(!(value instanceof JSONObject))throw new Invalid("schema","动态正文不是对象");JSONObject o=(JSONObject)value;
  if(o.has("ja")||o.has("zh"))return o;
  JSONObject found=null;for(String key:new String[]{"post","moment","data"}){JSONObject nested=o.optJSONObject(key);if(nested!=null&&(nested.has("ja")||nested.has("zh"))){if(found!=null)throw new Invalid("ambiguous","返回包含多个动态正文，无法安全选择");found=nested;}}
  return found==null?o:found;
 }
 static JSONObject pair(JSONObject o)throws Exception{
  for(String key:new String[]{"ja","zh"}){Object value=o.opt(key);if(!(value instanceof String)||((String)value).trim().isEmpty())throw new Invalid("missing_"+key,key.equals("ja")?"动态缺少日文正文":"动态缺少中文正文");if(((String)value).length()>2000)throw new Invalid("long_"+key,"动态正文超过长度上限");}
  String canonical=DialogueText.fields(ReplyMarkup.visible(o.getString("ja")),ReplyMarkup.visible(o.getString("zh")),true);
  String ja=DialogueText.language(canonical,"ja",false),zh=DialogueText.language(canonical,"zh",false);if(ja.length()>2000||zh.length()>2000)throw new Invalid("normalized_length","规范化后的动态正文过长");if(ja.isEmpty()||zh.isEmpty())throw new Invalid("language_fields","动态双语字段没有对应的可用正文");return new JSONObject().put("ja",ja).put("zh",zh);
 }
 private SocialReply(){}
}
