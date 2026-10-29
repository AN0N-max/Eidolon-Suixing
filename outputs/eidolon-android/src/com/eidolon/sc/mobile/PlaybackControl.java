package com.eidolon.sc.mobile;

/** Session state shared by UI and audio worker. This same monitor protects AudioTrack. */
final class PlaybackControl {
    final String identity;
    private boolean paused, ready, finished, cancelled;
    PlaybackControl(String identity) { this.identity=identity; }
    synchronized boolean reusable(String other) { return identity.equals(other)&&!finished&&!cancelled; }
    synchronized boolean toggle() {
        if(finished||cancelled)return false;
        paused=!paused; notifyAll(); return true;
    }
    synchronized boolean isPaused(){return paused;}
    synchronized void ready(){ready=true;notifyAll();}
    synchronized void finish(){finished=true;paused=false;notifyAll();}
    synchronized void cancel(){cancelled=true;paused=false;notifyAll();}
    synchronized void awaitResumed() throws InterruptedException { while(paused&&!cancelled&&!finished)wait(); }
    synchronized String state(){return cancelled||finished?"idle":paused?"paused":ready?"playing":"loading";}
}
