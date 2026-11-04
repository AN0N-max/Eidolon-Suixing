package com.eidolon.sc.mobile;

import java.util.*;
import org.json.*;

/** Executes the production streaming adapter with synthetic PCM, fake socket and injectable monotonic time. */
public final class BailianTests {
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); System.out.println("PASS " + name); }
    static String event(String id, String kind, JSONObject sentence) throws Exception {
        JSONObject v = new JSONObject().put("header", new JSONObject().put("task_id", id).put("event", kind));
        if (sentence != null) v.put("payload", new JSONObject().put("output", new JSONObject().put("sentence", sentence)));
        return v.toString();
    }
    static final class Fake implements CallBailianAsr.Listener, CallBailianAsr.Audio, CallBailianAsr.Transport {
        long now = 1000; int finishSends, results, errors, ready, closed, stops, packets, opens, capture, partials;
        boolean disconnectAfterFinal, continuation, normalClose, noAck, unconfirmed, manual, longSpeech, cancelledSocket, silence, taskFailure, authentication, backlog, cancelRead, noFinal, disconnect, ignoreStart, finishFinal;
        String task, recognized = "", error = ""; CallBailianAsr.Events events; CallBailianAsr asr;
        CallBailianAsr adapter() { return asr = new CallBailianAsr(() -> now, () -> {capture++; return this;}, this, 20); }
        public CallBailianAsr.Socket connect(String endpoint, String key, CallBailianAsr.Events e) {
            opens++; events = e;
            if (authentication) e.failure(401); else e.open();
            return new CallBailianAsr.Socket() {
                public boolean text(String s) {
                    try {
                        JSONObject h = new JSONObject(s).getJSONObject("header"); task = h.getString("task_id");
                        if (h.getString("action").equals("run-task")) {
                            if (taskFailure) e.text(new JSONObject().put("header", new JSONObject().put("task_id", task).put("event", "task-failed").put("error_code", "ModelNotEnabled").put("error_message", "private-key-must-not-escape")).toString());
                            else if (!ignoreStart) e.text(event(task,"task-started",null));
                        } else {finishSends++;if(finishFinal)e.text(event(task,"result-generated",new JSONObject().put("text","完成长句").put("sentence_end",true)));if(!noAck)e.text(event(task,"task-finished",null));}
                        return true;
                    } catch(Exception ex) { throw new RuntimeException(ex); }
                }
                public boolean audio(byte[] b) {
                    packets++;
                    try {
                        if (disconnect||disconnectAfterFinal&&packets==4) { e.failure(0); return true; }
                        if (silence) return true;
                        e.text(event("older-task","result-generated",new JSONObject().put("text","不得采用").put("sentence_end",true)));
                        if(packets<3)e.text(event(task,"result-generated",new JSONObject().put("sentence_id",1).put("text","昨天的事").put("sentence_end",false)));
                        if(continuation&&packets==20)e.text(event(task,"result-generated",new JSONObject().put("sentence_id",2).put("text","第二句话").put("sentence_end",true)));
                        if (packets == 3 && !noFinal) {
                            String finalEvent=event(task,"result-generated",new JSONObject().put("text","昨天的事已经结束了").put("sentence_end",true));
                            e.text(finalEvent); e.text(finalEvent); // Normal close after final must not discard a valid sentence.
                        }
                    } catch(Exception ex) { throw new RuntimeException(ex); }
                    return true;
                }
                public long queued() { return backlog ? 64001 : 0; }
                public void cancel() { cancelledSocket=true; }public void close(){normalClose=true;}
            };
        }
        public void start() {}
        public int read(byte[] target) {
            now+=100; Arrays.fill(target,(byte)0); if (!silence&&(packets<3||continuation&&packets>=18&&packets<=20||longSpeech||finishFinal)) for(int i=0;i<target.length;i+=2){target[i]=(byte)0xe8;target[i+1]=3;}
            if(cancelRead) asr.cancel();if(manual&&packets==4)asr.finish(); return target.length;
        }
        public void stop() { stops++; }
        public void close() { closed++; }
        public void ready() { ready++; }
        public void partial(String text) { partials++; }
        public void ended(long first,long last) { if(first>last)throw new AssertionError("invalid speech anchor"); }
        public void result(String text) { results++;recognized=text; }
        public void error(String text) { errors++;error=text; }public void unconfirmed(String text,String message){unconfirmed=true;recognized=text;error=message;}
        void run() { adapter().run("beijing",BailianAsrProtocol.DEFAULT_MODEL,"fake-only-key",this); }
    }
    public static void main(String[] args) throws Exception {
        BailianAsrProtocol p=new BailianAsrProtocol();JSONObject start=new JSONObject(p.start(BailianAsrProtocol.DEFAULT_MODEL));
        check(start.getJSONObject("header").getString("streaming").equals("duplex") && start.getJSONObject("payload").getJSONObject("parameters").getInt("sample_rate")==16000,"DashScope run-task uses 16k PCM recognition and task identity");
        check(start.getJSONObject("payload").getJSONObject("parameters").getString("vad_model").equals("near_meeting_16k"),"upstream near-field VAD and language parameters retained");
        check(!p.accept(event("another","task-started",null))&&!p.started,"foreign task events cannot advance recognition");
        p.accept(event(p.taskId,"task-started",null));p.accept(event(p.taskId,"result-generated",new JSONObject().put("text","昨天").put("sentence_end",false)));
        check(p.text.equals("昨天")&&p.finalText.isEmpty(),"partial transcription updates subtitles without generating a reply");
        check(!p.accept(event(p.taskId,"result-generated",new JSONObject().put("heartbeat",true).put("text","心跳").put("sentence_end",true))),"heartbeat is not a final utterance");
        check(!p.accept(event(p.taskId,"result-generated",new JSONObject().put("text","……").put("sentence_end",true))),"punctuation and empty cough result cannot create a chat turn");
        String finalEvent=event(p.taskId,"result-generated",new JSONObject().put("text","昨天说过的事").put("sentence_end",true));
        check(p.accept(finalEvent)&&!p.accept(finalEvent)&&!p.finished,"sentence end is not task end, duplicate callbacks ignored");
        p.accept(event(p.taskId,"result-generated",new JSONObject().put("sentence_id",2).put("text","第二句").put("sentence_end",false)));p.accept(event(p.taskId,"result-generated",new JSONObject().put("sentence_id",2).put("text","第二句修订").put("sentence_end",true)));check(p.finalText.equals("昨天说过的事 第二句修订"),"sentence ids merge while partials replace");p.accept(event("old-task","task-finished",null));check(!p.serverFinished,"foreign finish cannot complete task");p.accept(event(p.taskId,"task-finished",null));check(p.serverFinished,"server acknowledgement required");
        check(new JSONObject(p.finish()).getJSONObject("header").getString("task_id").equals(p.taskId),"finish-task retains task identity");
        check(BailianAsrProtocol.endpoint("beijing").contains("dashscope.aliyuncs.com")&&BailianAsrProtocol.endpoint("singapore").contains("dashscope-intl.aliyuncs.com"),"explicit regions map to fixed secure vendor endpoints");
        boolean rejected=false;try{BailianAsrProtocol.endpoint("https://other.example");}catch(Exception expected){rejected=true;}check(rejected,"arbitrary server cannot receive a saved Bailian key");
        Fake happy=new Fake();happy.run();check(happy.results==1&&happy.errors==0&&happy.partials>0&&happy.recognized.equals("昨天的事已经结束了"),"production adapter streams synthetic PCM, shows partials and delivers one final text");
        check(happy.ready==1&&happy.capture==1&&happy.closed==1&&happy.normalClose&&!happy.cancelledSocket&&happy.finishSends==1&&happy.packets>3,"acknowledged turn releases microphone and gracefully closes socket");
        Fake thought=new Fake();thought.continuation=true;thought.run();check(thought.results==1&&thought.recognized.equals("昨天的事已经结束了 第二句话")&&thought.finishSends==1,"thinking pause continues same task and submits merged sentences exactly once");
        Fake ack=new Fake();ack.noAck=true;ack.run();check(ack.results==0&&ack.unconfirmed&&ack.cancelledSocket&&!ack.recognized.isEmpty(),"finish ack timeout preserves final text for manual confirmation without reporting success");
        Fake hand=new Fake();hand.manual=true;hand.run();check(hand.results==1&&hand.finishSends==1,"manual finish submits one recognition task");
        Fake cut=new Fake();cut.disconnectAfterFinal=true;cut.run();check(cut.results==0&&cut.unconfirmed&&!cut.recognized.isEmpty()&&cut.finishSends==0,"connection loss after final words retains draft without a new upload or model submission");
        Fake auth=new Fake();auth.authentication=true;auth.run();check(auth.opens==1&&auth.capture==0&&auth.errors==1&&auth.cancelledSocket,"401 never opens microphone or retries another region");
        Fake task=new Fake();task.taskFailure=true;task.run();check(task.capture==0&&task.errors==1&&task.error.contains("ModelNotEnabled")&&!task.error.contains("private-key"),"task refusal reports safe code without leaking provider text or credentials");
        Fake timeout=new Fake();timeout.ignoreStart=true;timeout.run();check(timeout.capture==0&&timeout.errors==1&&timeout.cancelledSocket,"task-start timeout cleans up without microphone capture");
        timeout.events.text(event(timeout.task,"task-started",null));timeout.events.text(event(timeout.task,"result-generated",new JSONObject().put("text","迟到文字").put("sentence_end",false)));check(timeout.partials==0&&timeout.results==0,"late callbacks after failed task cleanup cannot update subtitles or submit a result");
        Fake cancelled=new Fake();cancelled.cancelRead=true;cancelled.run();check(cancelled.results==0&&cancelled.errors==0&&cancelled.closed==1&&cancelled.cancelledSocket,"hangup during capture cancels and releases with no submitted result");
        Fake before=new Fake();CallBailianAsr adapter=before.adapter();adapter.cancel();adapter.run("beijing",BailianAsrProtocol.DEFAULT_MODEL,"fake",before);check(before.opens==0&&before.capture==0&&before.results==0,"queued old turn stays cancelled before microphone or network starts");
        Fake back=new Fake();back.backlog=true;back.run();check(back.results==0&&back.errors==1&&back.closed==1&&back.cancelledSocket,"bounded send queue rejects slow upload instead of accumulating audio");
        Fake lost=new Fake();lost.disconnect=true;lost.run();check(lost.results==0&&lost.errors==1&&lost.closed==1&&lost.opens==1,"network loss ends the recognition turn without paid automatic retry");
        Fake silence=new Fake();silence.silence=true;silence.noFinal=true;silence.run();check(silence.now<=13200&&silence.results==0&&silence.errors==1&&silence.closed==1,"silence times out in 12 seconds without submitting a chat turn");
        Fake longTurn=new Fake();longTurn.noFinal=true;longTurn.finishFinal=true;longTurn.run();check(longTurn.now<=61100&&longTurn.results==1&&longTurn.closed==1,"60-second capture limit sends finish-task and accepts remaining final sentence");
        Fake empty=new Fake();empty.noFinal=true;empty.run();check(empty.results==0&&empty.errors==1&&empty.closed==1,"no final sentence after bounded finish wait does not create user facts");
        Fake next=new Fake();next.run();check(next.results==1&&!next.recognized.equals("不得采用"),"next task ignores prior task results and completes independently");
    }
}
