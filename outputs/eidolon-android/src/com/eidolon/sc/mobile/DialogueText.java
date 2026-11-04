package com.eidolon.sc.mobile;

import java.util.*;
import java.util.regex.*;

/** Bounded decoder for the two reserved language tags. Never parses arbitrary HTML.
 * Code and explicit quotation lines are literal; canonical transport remains compatible with old rows. */
final class DialogueText {
 static final Pattern TOKEN=Pattern.compile("(?ms)```.*?(?:```|\\z)|`[^`\\r\\n]*`|^[\\t ]*>[^\\r\\n]*|“[^”\\r\\n]*”|‘[^’\\r\\n]*’|「[^」\\r\\n]*」|『[^』\\r\\n]*』|(?i:</?(?:ja|zh)\\s*>|&lt;/?(?:ja|zh)\\s*&gt;)");
 static final Pattern KANA=Pattern.compile("[\\u3040-\\u30ff]");
 static final class Part {final String lang,text;final boolean literal;Part(String l,String t,boolean q){lang=l;text=t;literal=q;}}
 static List<Part> parse(String text){return parse(text,"");}
 static List<Part> parse(String text,String base){
  List<Part> out=new ArrayList<>();ArrayList<String> stack=new ArrayList<>();Matcher m=TOKEN.matcher(text==null?"":text);int at=0;
  while(m.find()){
   add(out,stack.isEmpty()?base:stack.get(stack.size()-1),text.substring(at,m.start()),false);
   String token=m.group();if(!token.startsWith("<")&&!token.toLowerCase(Locale.ROOT).startsWith("&lt;"))add(out,stack.isEmpty()?base:stack.get(stack.size()-1),token,true);
   else {String t=token.toLowerCase(Locale.ROOT).replace("&lt;","<").replace("&gt;",">");String lang=t.contains("ja")?"ja":"zh";
    if(t.startsWith("</")){int i=stack.lastIndexOf(lang);if(i>=0)while(stack.size()>i)stack.remove(stack.size()-1);}
    else if(stack.size()<64)stack.add(lang);
   }at=m.end();
  }
  if(text!=null)add(out,stack.isEmpty()?base:stack.get(stack.size()-1),text.substring(at),false);return out;
 }
 static void add(List<Part> out,String lang,String value,boolean literal){
  if(value.isEmpty())return;if(literal){out.add(new Part(lang,value,true));return;}
  // A kana-bearing unlabelled/mislabelled paragraph is ambiguous: hide it with Japanese,
  // rather than leaking it through a Chinese field. Do not delete or rewrite its words.
  String[] lines=value.split("(?<=[\\s。！？!?，、；;])",-1);for(String line:lines){if(line.isEmpty())continue;String l=!lang.equals("ja")&&KANA.matcher(line).find()?"ja":lang;
   if(!out.isEmpty()){Part prior=out.get(out.size()-1);if(!prior.literal&&prior.lang.equals(l)){out.set(out.size()-1,new Part(l,prior.text+line,false));continue;}}
   out.add(new Part(l,line,false));
  }
 }
 static String canonical(String text){StringBuilder b=new StringBuilder();for(Part p:parse(text)){if(p.lang.isEmpty())b.append(p.text);else if(!p.text.trim().isEmpty())b.append('<').append(p.lang).append('>').append(p.text.trim()).append("</").append(p.lang).append('>');}return b.toString().trim();}
 static String fields(String ja,String zh,boolean bilingual){StringBuilder b=new StringBuilder();if(bilingual)appendFields(b,parse(ja,"ja"));appendFields(b,parse(zh,"zh"));return canonical(b.toString());}
 private static void appendFields(StringBuilder b,List<Part> parts){for(Part p:parts){if(!p.text.trim().isEmpty())b.append('<').append(p.lang.isEmpty()?"zh":p.lang).append('>').append(p.text).append("</").append(p.lang.isEmpty()?"zh":p.lang).append('>');}}
 static String language(String text,String lang,boolean literals){StringBuilder b=new StringBuilder();for(Part p:parse(text))if((literals||!p.literal||p.text.matches("(?s)^[“‘「『].*"))&&(p.lang.equals(lang)||lang.equals("zh")&&p.lang.isEmpty()&&!KANA.matcher(p.text).find())){if(b.length()>0)b.append('\n');b.append(p.text.trim());}return b.toString().trim();}
 static boolean needsChinese(String text){return !language(text,"ja",false).isEmpty()&&language(text,"zh",false).isEmpty();}
 static String spoken(String text){String ja=language(text,"ja",false);StringBuilder plain=new StringBuilder();if(ja.isEmpty())for(Part p:parse(text))if(!p.literal&&p.lang.isEmpty())plain.append(p.text);String value=ja.isEmpty()?plain.toString():ja;return ReplyMarkup.speech(value).replaceAll("<[^>]*>","").replaceAll("\\[[^\\]]*\\]", "").replaceAll("(?i)</?(?:ja|zh)\\s*>","").trim();}
 private DialogueText(){}
}
