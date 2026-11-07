package com.eidolon.sc.mobile;
import java.io.IOException;import java.util.*;import java.util.regex.*;import org.json.*;

/** Check quoted correspondence and explicit clock facts; never judge translation by length. */
final class BilingualAlignment {
 static Set<String> clocks(String text){Set<String> out=new HashSet<>();Matcher m=Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])[:：]([0-5]\\d)(?!\\d)").matcher(text);while(m.find())out.add(Integer.parseInt(m.group(1))+":"+m.group(2));return out;}
 static String compact(String text){return text.replaceAll("[\\p{P}\\p{Z}\\s]","");}
 static void require(String ja,String zh,JSONArray alignment)throws Exception{
  Set<String> j=clocks(ja),c=clocks(zh);if(!j.isEmpty()&&!c.isEmpty()&&!j.equals(c))throw new IOException("日中台词的明确时刻不一致，未自动重新生成");
  if(alignment==null)return;if(alignment.length()<1||alignment.length()>12)throw new IOException("双语对应数量无效");
  StringBuilder coveredJa=new StringBuilder(),coveredZh=new StringBuilder();int priorJa=0,priorZh=0;
  for(int i=0;i<alignment.length();i++){JSONObject unit=alignment.getJSONObject(i);String a=unit.optString("ja"),b=unit.optString("zh");int aj=ja.indexOf(a,priorJa),bz=zh.indexOf(b,priorZh);if(a.trim().isEmpty()||b.trim().isEmpty()||aj<0||bz<0)throw new IOException("双语对应引用与正文不符");coveredJa.append(ja,priorJa,aj);coveredZh.append(zh,priorZh,bz);priorJa=aj+a.length();priorZh=bz+b.length();require(a,b,null);}
  coveredJa.append(ja.substring(priorJa));coveredZh.append(zh.substring(priorZh));if(!compact(coveredJa.toString()).isEmpty()||!compact(coveredZh.toString()).isEmpty())throw new IOException("双语正文包含未对应的扩写，未自动重新生成");
 }
 private BilingualAlignment(){}
}
