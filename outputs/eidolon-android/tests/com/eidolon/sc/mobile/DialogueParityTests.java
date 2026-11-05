package com.eidolon.sc.mobile;
import org.json.*;
public class DialogueParityTests {
 public static void main(String[] args)throws Exception{
  JSONArray result=new JSONArray();for(String text:new String[]{"<ja>こんにちは</ja><zh>你好</zh>","</ja>中文<ja>こんにちは<zh>翻译","<zh>中文<ja>おはよう</ja>译文</zh>","<ja><ja>こんにちは</ja></ja><zh>中文</zh>","<zh>今天提到‘あんた’，有点开心。</zh>","```xml\n<ja>literal</ja>\n<br>","`<ja>literal</ja>`\n> <zh>quoted</zh>","&LT;JA&GT;こんにちは&LT;/JA&GT;","<zh>こんにちは</zh>","<img src=x onerror=alert(1)>\n中文", "<zh>段落一\n\n段落二</zh>","<ja>こんにちは。你好，ふゆ。おやすみ。</ja><zh>中文</zh>","<ja>今日は元気です。</ja><zh>ふゆ今天说「おはよう」和‘あんた’。</zh>","<ja>前半です。</ja>後半です。<zh>中文</zh>","<ja>前半です。</ja\n<zh>中文</zh","<zh>ふゆ</zh>","<ja>今天提到‘あんた’，有点开心。</ja>","<ja>こんにちは。你好です。</ja>"}){
   JSONArray parts=new JSONArray();for(DialogueText.Part p:DialogueText.parse(text))parts.put(new JSONObject().put("lang",p.lang).put("text",p.text).put("literal",p.literal));result.put(new JSONObject().put("input",text).put("parts",parts).put("needsChinese",DialogueText.needsChinese(text)));
  }java.nio.file.Files.write(java.nio.file.Paths.get(args[0]),result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
}
