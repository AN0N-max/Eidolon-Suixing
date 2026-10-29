package com.eidolon.sc.mobile;
import android.app.job.*;import org.json.*;
public final class ReachJob extends JobService {
 private volatile boolean stopped;
 @Override public boolean onStartJob(JobParameters params){stopped=false;new Thread(()->{try{JSONObject m=ReachOut.run(this,true);if(m!=null&&!stopped){boolean shown=OverlayBanner.show(this,m);ReachOut.notify(this,m,shown);}}catch(Exception e){android.util.Log.w("EidolonReach","Reach-out failed",e);}finally{jobFinished(params,false);}},"eidolon-reach").start();return true;}
 @Override public boolean onStopJob(JobParameters params){stopped=true;ReachOut.cancel();return false;}
}
