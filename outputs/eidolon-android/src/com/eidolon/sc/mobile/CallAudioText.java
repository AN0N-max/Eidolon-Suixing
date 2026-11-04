package com.eidolon.sc.mobile;
/** In Chinese-only mode the Chinese text is speech, rather than a bilingual subtitle. */
final class CallAudioText {
 static String forVoice(String answer,boolean bilingual)throws Exception{
  if(bilingual){FishClient.speech(answer);return answer;}
  String text=DialogueText.language(ReplyText.clean(answer),"zh",false);
  if(text.isEmpty())throw new Exception("没有可播放的中文台词");return text;
 }
 private CallAudioText(){}
}
