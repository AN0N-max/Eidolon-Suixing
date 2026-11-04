package com.eidolon.sc.mobile;
import android.content.Context;import java.util.*;
/** One context adapter shared by text, existing calls and proactive messages. */
final class WorldContext {
 static String prompt(Context c,ChatStore s,String who,List<ChatStore.Message> history,long now)throws Exception {
  long last=0;for(ChatStore.Message m:history)if(m.sentAt>0&&m.sentAt<=now)last=Math.max(last,m.sentAt);
  String weather="",social="";
  try{weather=WeatherRuntime.prompt(c,s,history,now,last);}catch(Exception ignored){}
  try{social=SocialStore.context(s,who,now);}catch(Exception ignored){}
  return weather+social;
 }
}
