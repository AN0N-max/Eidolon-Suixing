package com.eidolon.sc.mobile;
/** Monotonic, bounded per-turn endpointing. A recognizer sentence is only a piece of a turn. */
final class CallTurnEnd {
 static final long MAX_CAPTURE=60000,NO_SPEECH=12000;
 final String mode;final long began;volatile long lastVoice,firstVoice,lastTextAt;volatile String text="";volatile boolean manual;
 CallTurnEnd(String mode,long now){this.mode=validMode(mode);began=now;}
 static String validMode(String mode){return mode.equals("fast")||mode.equals("natural")||mode.equals("manual")?mode:"thinking";}
 long hold(){return mode.equals("fast")?1500:mode.equals("natural")?2500:3500;}
 void voice(long now){if(firstVoice==0)firstVoice=now;lastVoice=now;}
 void text(String value,long now){if(value!=null&&!value.equals(text)){text=value;lastTextAt=now;}}
 void finish(){manual=true;}
 long quiet(long now){return now-Math.max(lastVoice,lastTextAt);}
 boolean unfinished(){return text.trim().matches("(?s).*(因为|我觉得|然后|但是|所以|就是|那个|えっと|だから)[，,、…\\s]*$");}
 boolean finishAt(long now){if(manual)return true;if(mode.equals("manual"))return false;return (lastVoice>0||!text.isEmpty())&&quiet(now)>=hold()+(unfinished()?1500:0);}
 String phase(long now){long silent=quiet(now);return lastVoice==0&&text.isEmpty()||silent<250?"listening":silent<1000?"speech_pause":"waiting_continue";}
 boolean limit(long now){return now-began>=MAX_CAPTURE;}
 /** Noise floor adapts only in non-speech frames; hysteresis avoids losing soft continuations. */
 static final class Activity {double noise=65;int hang;boolean speech(double rms){double high=Math.max(180,noise*3.2),low=Math.max(120,noise*2);boolean voiced=rms>high||hang>0&&rms>low;if(voiced)hang=3;else{hang=Math.max(0,hang-1);noise=.97*noise+.03*Math.min(rms,noise*1.4);}return voiced;}}
}
