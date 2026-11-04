package com.eidolon.sc.mobile;
/** Draw once per native call. UI reconstruction only reads the stored deadline. */
final class CallDialWait {
 final long deadline;
 CallDialWait(long now,int draw){if(draw<0||draw>3000)throw new IllegalArgumentException("wait draw");deadline=now+2000+draw;}
 boolean due(long now){return now>=deadline;}
}
