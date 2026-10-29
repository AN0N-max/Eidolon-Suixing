package com.eidolon.sc.mobile;
import android.content.Context;
import androidx.work.*;
import org.json.JSONObject;
public final class ReachWorker extends Worker {
    private final ReachExecution execution=new ReachExecution();
    public ReachWorker(Context c,WorkerParameters p){super(c,p);}
    @Override public Result doWork(){Context c=getApplicationContext();try{
        JSONObject message=execution.run(c);if(message!=null)ReplyManager.show(c,message,false);return Result.success();
    }catch(Exception e){android.util.Log.w("EidolonReach","Generation failed; no blind network retry",e);return Result.failure();}
    finally{try(ChatStore s=new ChatStore(c)){ReachScheduler.schedule(c,s,true);}catch(Exception e){android.util.Log.w("EidolonReach","Follow-up schedule failed",e);}}}
    @Override public void onStopped(){execution.stop(getApplicationContext());}
}
