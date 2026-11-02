package com.eidolon.sc.mobile;
import org.json.*;import java.util.*;
/** Production launch/permission guards and UI-facing audio truth, no Android capture or provider. */
public final class CallUiTests {
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception{
  CallLaunch launch=new CallLaunch();launch.begin("attempt-a-123","mano","s","",100);check(launch.pending()&&launch.mayStart("attempt-a-123"),"direct dial owns one launch identity");
  boolean rejected=false;try{launch.begin("attempt-b-123","hiori","other","",101);}catch(IllegalStateException good){rejected=true;}check(rejected,"duplicate taps cannot create a second permission or connection launch");
  launch.permission("attempt-a-123");check(launch.view().getString("state").equals("permission")&&!launch.view().getBoolean("recording"),"permission wait does not claim microphone capture or connection");
  launch.timeout(10000000);check(launch.view().getString("state").equals("permission"),"permission dialog has no fake timed connection progress");
  launch.cancel("attempt-a-123");check(!launch.mayStart("attempt-a-123")&&!launch.view().getBoolean("showCall"),"leaving the page invalidates a future permission result");
  launch.active("attempt-a-123");launch.connecting("attempt-a-123",200);check(launch.view().getString("state").equals("idle"),"late start callbacks cannot revive a cancelled dial");
  launch.begin("attempt-b-123","hiori","other","incoming-1",300);launch.fail("attempt-a-123","old error");check(launch.mayStart("attempt-b-123")&&launch.view().getString("incomingCallId").equals("incoming-1"),"old attempt error cannot poison a new incoming acceptance");
  launch.connecting("attempt-b-123",500);launch.timeout(15500);check(launch.pending(),"connecting timeout respects its measured boundary");launch.timeout(15501);check(launch.view().getString("state").equals("failed")&&!launch.pending(),"failed native startup becomes an explicit error without an automatic retry");
  launch.cancel("attempt-b-123");launch.begin("attempt-c-123","mano","s","",20000);launch.active("attempt-c-123");check(!launch.mayStart("attempt-c-123")&&launch.view().getString("state").equals("active"),"active service cannot be started again by a repeated permission callback");
  check(CallLimits.MAX_MINUTES==30&&CallLimits.MAX_TURNS==60&&CallLimits.MAX_MILLIS==1800000L,"screen limits and native timer share existing 30 minute / 60 turn limits");
  check(CallLimits.recording("listening",false,false),"microphone indicator is active only when native recognizer is ready");
  for(String phase:new String[]{"dialing","permission","connecting","recognizing","thinking","synthesizing","speaking","paused","failed","ended"})check(!CallLimits.recording(phase,false,false),"no false recording indicator in "+phase);
  check(!CallLimits.recording("listening",true,false)&&!CallLimits.recording("listening",false,true),"mute and hangup turn off capture indication");
  JSONObject row=new JSONObject().put("id","call-ui").put("character","mano").put("session","s").put("state","paused").put("connectedAt",1).put("endedAt",0).put("turnId",1).put("pending","");CallStore.validateBackup(new JSONObject().put("calls",new JSONArray().put(row)),Collections.singleton("mano"));check(CallStore.live(row),"manual-next-turn pause remains a real call and a valid recoverable metadata state");
 }
}
