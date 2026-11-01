package com.eidolon.sc.mobile;
/** Bridges monotonic voice-end timing to the wall-clock source used by message dates. */
final class CallClock {
 static long utteranceAt(long wallNow,long monoNow,long lastVoiceMono){return lastVoiceMono>0&&lastVoiceMono<=monoNow?Math.max(0,wallNow-(monoNow-lastVoiceMono)):wallNow;}
 private CallClock(){}
}
