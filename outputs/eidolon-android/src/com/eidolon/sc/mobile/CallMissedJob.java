package com.eidolon.sc.mobile;
import android.app.job.*;
public final class CallMissedJob extends JobService {
 @Override public boolean onStartJob(JobParameters p){new Thread(()->{try(ChatStore s=new ChatStore(this)){CallIncoming.expire(this,s,System.currentTimeMillis());}catch(Exception ignored){}finally{jobFinished(p,false);}},"eidolon-call-expiry").start();return true;}
 @Override public boolean onStopJob(JobParameters p){return true;}
}
