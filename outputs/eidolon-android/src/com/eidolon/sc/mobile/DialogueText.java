package com.eidolon.sc.mobile;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Shared read-only projection for display, translation and speech. Only reserved tags are decoded.
 * Script alone cannot decide language: Chinese often contains Japanese names and quotations. */
final class DialogueText {
 static final Pattern TOKEN=Pattern.compile("(?ms)```.*?(?:```|\\z)|`[^`\\r\\n]*`|^[\\t ]*>[^\\r\\n]*|“[^”\\r\\n]*”|‘[^’\\r\\n]*’|「[^」\\r\\n]*」|『[^』\\r\\n]*』|(?i:</?(?:ja|zh)(?:\\s*>|(?=\\r?\\n|\\z))|&lt;/?(?:ja|zh)\\s*&gt;)");
 static final Pattern KANA=Pattern.compile("[\\u3040-\\u30ff]");
 // Narrow evidence for known field contamination. Ambiguous prose remains visible.
 static final Pattern CHINESE=Pattern.compile("[这说吗么没们让别给觉还样呢呀啦哦呜咱您]|你好|今天|明天|昨天|中文|翻译|译文|准备|睡觉|因为|已经|现在|有点|辛苦|不用|一起|但是|所以|什么|怎么|可以|喜欢");
 static final Pattern JAPANESE=Pattern.compile("こんにちは|こんばんは|おはよう|おやすみ|ありがとう|ごめん|です|でした|ます|ました|ません|だよ|だね|だろ|だっ|して|から|けど|じゃ|って|よね|かな[。！？!?]?$");
 static final class Part {final String lang,text;final boolean literal;Part(String l,String t,boolean q){lang=l;text=t;literal=q;}}
 static List<Part> parse(String value){return parse(value,"");}
 static List<Part> parse(String value,String base){
  String text=value==null?"":value;List<Part> out=new ArrayList<>();ArrayList<String> stack=new ArrayList<>();Matcher m=TOKEN.matcher(text);int at=0;
  while(m.find()){
   add(out,stack.isEmpty()?base:stack.get(stack.size()-1),text.substring(at,m.start()),false);
   String token=m.group();if(!token.startsWith("<")&&!token.toLowerCase(Locale.ROOT).startsWith("&lt;"))add(out,stack.isEmpty()?base:stack.get(stack.size()-1),token,true);
   else {String t=token.toLowerCase(Locale.ROOT).replace("&lt;","<").replace("&gt;",">");String lang=t.contains("ja")?"ja":"zh";
    if(t.startsWith("</")){int i=stack.lastIndexOf(lang);if(i>=0)while(stack.size()>i)stack.remove(stack.size()-1);}
    else if(stack.size()<64)stack.add(lang);
   }at=m.end();
  }
  add(out,stack.isEmpty()?base:stack.get(stack.size()-1),text.substring(at),false);return out;
 }
 static void add(List<Part> out,String lang,String value,boolean literal){
  if(value.isEmpty())return;if(literal){if(!out.isEmpty()&&CHINESE.matcher(out.get(out.size()-1).text).find()&&lang.equals("ja"))lang=out.get(out.size()-1).lang;out.add(new Part(lang,value,true));return;}
  // Recover clauses at sentence boundaries, never split names/quotes by the presence of kana.
  for(String clause:value.split("(?<=[。！？!?\\n])",-1)){
   if(clause.isEmpty())continue;boolean zh=CHINESE.matcher(clause).find(),ja=KANA.matcher(clause).find()&&JAPANESE.matcher(clause).find();String l=lang;
   if(zh&&ja)l="";else if(zh&&lang.equals("ja"))l="zh";else if(ja&&!lang.equals("ja"))l="ja";
   if(!out.isEmpty()){Part prior=out.get(out.size()-1);if(!prior.literal&&prior.lang.equals(l)){out.set(out.size()-1,new Part(l,prior.text+clause,false));continue;}}
   out.add(new Part(l,clause,false));
  }
 }
 static String canonical(String text){return encode(parse(text));}
 private static String encode(List<Part> parts){StringBuilder b=new StringBuilder(),run=new StringBuilder();String lang=null;for(Part p:parts){if(lang!=null&&!lang.equals(p.lang)){emit(b,lang,run.toString());run.setLength(0);}lang=p.lang;run.append(p.text);}if(lang!=null)emit(b,lang,run.toString());return b.toString().trim();}
 private static void emit(StringBuilder b,String lang,String value){if(lang.isEmpty())b.append(value);else if(!value.trim().isEmpty())b.append('<').append(lang).append('>').append(value).append("</").append(lang).append('>');}
 static String fields(String ja,String zh,boolean bilingual){List<Part> all=new ArrayList<>();if(bilingual)all.addAll(parse(ja,"ja"));all.addAll(parse(zh,"zh"));return encode(all);}
 static boolean speakable(Part p){return !p.literal||p.text.matches("(?s)^[“‘「『].*");}
 static String language(String text,String lang,boolean literals){StringBuilder b=new StringBuilder();String prior=null;for(Part p:parse(text)){
  boolean selected=(literals||speakable(p))&&(p.lang.equals(lang)||lang.equals("zh")&&p.lang.isEmpty()&&!KANA.matcher(p.text).find());
  if(selected){if(b.length()>0&&prior!=null&&!prior.equals(p.lang))b.append('\n');b.append(p.text);}prior=p.lang;
 }return b.toString().trim();}
 static boolean needsChinese(String text){return !language(text,"ja",false).isEmpty()&&language(text,"zh",false).isEmpty();}
 static String spoken(String text){String ja=language(text,"ja",false);StringBuilder plain=new StringBuilder();if(ja.isEmpty()&&!Pattern.compile("(?i)<(?:ja|zh)(?:\\s*>|$)|&lt;(?:ja|zh)\\s*&gt;").matcher(text).find())for(Part p:parse(text))if(speakable(p)&&p.lang.isEmpty()&&!(CHINESE.matcher(p.text).find()&&JAPANESE.matcher(p.text).find()))plain.append(p.text);String value=ja.isEmpty()?plain.toString():ja;return ReplyMarkup.speech(value.replace(ChatEnvelope.BREAK,"\n\n")).replaceAll("(?i)\\[(?:softly|whisper|whispering|laughing|sighing)\\]", "").replaceAll("(?i)</?(?:ja|zh|eidolon-bubble)\\s*/?>","").replaceAll("<[^>]*>","").trim();}
 static JSONObject presentation(String text)throws Exception{JSONArray parts=new JSONArray();for(Part p:parse(text))parts.put(new JSONObject().put("lang",p.lang).put("text",p.text).put("literal",p.literal));String speech=spoken(text);boolean ambiguous=false;for(Part p:parse(text))if(!p.literal&&p.lang.isEmpty()&&CHINESE.matcher(p.text).find()&&JAPANESE.matcher(p.text).find()&&KANA.matcher(p.text).find())ambiguous=true;return new JSONObject().put("parts",parts).put("needsChinese",needsChinese(text)).put("japaneseSpeech",speech).put("speechState",speech.isEmpty()?"missing_japanese":ambiguous?"partial_japanese":"ready");}
 private DialogueText(){}
}
