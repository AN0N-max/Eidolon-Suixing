package com.eidolon.sc.mobile;
import org.json.*;

/** Transient, process-owned dial/permission fence. Process death never resumes a dial. */
final class CallLaunch {
    static final CallLaunch CURRENT=new CallLaunch();
    private String id="",character="",session="",incoming="",phase="idle",error="";
    private long started;
    synchronized void begin(String id,String who,String session,String incoming,long now){
        if(!id.matches("[A-Za-z0-9-]{8,80}"))throw new IllegalArgumentException("拨号标识无效");
        if(pending()||phase.equals("active"))throw new IllegalStateException("已有拨号或通话，请先结束");
        this.id=id;character=who;this.session=session;this.incoming=incoming;phase="dialing";error="";started=now;
    }
    synchronized boolean matches(String id){return !this.id.isEmpty()&&this.id.equals(id);}
    synchronized boolean pending(){return phase.equals("dialing")||phase.equals("permission")||phase.equals("connecting");}
    synchronized boolean mayStart(String id){return matches(id)&&pending();}
    synchronized void permission(String id){if(mayStart(id))phase="permission";}
    synchronized void connecting(String id,long now){if(mayStart(id)){phase="connecting";started=now;}}
    synchronized void active(String id){if(mayStart(id))phase="active";}
    synchronized void fail(String id,String error){if(matches(id)){phase="failed";this.error=error;}}
    synchronized void cancel(String id){if(matches(id)){phase="idle";this.id="";error="";}}
    synchronized void timeout(long now){if(phase.equals("connecting")&&now-started>15000)fail(id,"原生通话未能启动，请检查通知权限后重试");}
    synchronized JSONObject view()throws JSONException{return new JSONObject().put("requestId",id).put("character",character).put("session",session).put("incomingCallId",incoming).put("state",phase).put("error",error).put("launch",true).put("showCall",!phase.equals("idle")).put("durationSeconds",0).put("recording",false).put("muted",false);}
    synchronized String id(){return id;}
}
