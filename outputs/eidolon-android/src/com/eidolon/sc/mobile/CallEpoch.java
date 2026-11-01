package com.eidolon.sc.mobile;
/** Every asynchronous result is fenced by both call identity and turn generation. */
final class CallEpoch {
 final String callId;private long version,audioVersion=-1,committedVersion=-1;private boolean ended;
 CallEpoch(String id){callId=id;}
 synchronized long next(){return ++version;}
 synchronized boolean current(String id,long turn){return !ended&&callId.equals(id)&&version==turn;}
 synchronized void audioStarted(String id,long turn){if(current(id,turn))audioVersion=turn;}
 synchronized boolean commitOnce(String id,long turn){if(!current(id,turn)||audioVersion!=turn||committedVersion==turn)return false;committedVersion=turn;return true;}
 synchronized void end(){ended=true;++version;}
}
