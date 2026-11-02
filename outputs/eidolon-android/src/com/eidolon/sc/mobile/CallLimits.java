package com.eidolon.sc.mobile;
final class CallLimits {
    static final int MAX_MINUTES=30,MAX_TURNS=60;
    static final long MAX_MILLIS=MAX_MINUTES*60*1000L;
    static boolean recording(String phase,boolean muted,boolean closing){return phase.equals("listening")&&!muted&&!closing;}
}
