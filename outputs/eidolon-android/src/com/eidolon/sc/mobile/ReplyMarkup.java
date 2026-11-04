package com.eidolon.sc.mobile;
import java.util.regex.*;
import java.util.function.Function;

/** Only reserved transport syntax. Never evaluates HTML; quoted/code examples stay literal. */
final class ReplyMarkup {
 private static final Pattern EXAMPLE=Pattern.compile("(?ms)```.*?(?:```|\\z)|`[^`\\r\\n]*`|^[\\t ]*>[^\\r\\n]*");
 private static final Pattern CONTROL=Pattern.compile("(?is)<sticker_id>\\s*(.{0,2048}?)\\s*</sticker_id>|<sticker_id>\\s*([^<>\\s]{1,200})|\\b(s-[a-f0-9]{64})\\b");
 static String part(String text,Function<String,String> sticker){
  Matcher m=CONTROL.matcher(text);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(sticker.apply(m.group(1)!=null?m.group(1).trim():m.group(2)!=null?m.group(2):m.group(3))));m.appendTail(out);
  return out.toString().replaceAll("(?i)</?sticker_id>","").replaceAll("(?i)<br[\\t ]*/?>|&lt;br[\\t ]*/?&gt;","\n").replace("\r\n","\n").replace('\r','\n').replaceAll("\\n(?:[\\t ]*\\n){2,}","\n\n");
 }
 static String prose(String text,Function<String,String> plain){if(text==null)return "";Matcher m=EXAMPLE.matcher(text);StringBuilder out=new StringBuilder();int at=0;while(m.find()){out.append(plain.apply(text.substring(at,m.start()))).append(m.group());at=m.end();}return out.append(plain.apply(text.substring(at))).toString();}
 static String transform(String text,Function<String,String> sticker){return prose(text,s->part(s,sticker)).trim();}
 static String visible(String text){return transform(text,id->"");}
 static String speech(String text){return part(text==null?"":text,id->"").replaceAll("\\bs-[a-f0-9]{64}\\b","").trim();}
 private ReplyMarkup(){}
}
