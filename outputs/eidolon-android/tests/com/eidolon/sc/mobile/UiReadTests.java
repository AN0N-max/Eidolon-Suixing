package com.eidolon.sc.mobile;
import org.json.*;
public final class UiReadTests {
    static int n;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);n++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        for(String page:new String[]{"settings","settings-life","profile","model","features","reachSettings","badges"}){
            UiReadScope s=new UiReadScope(page);check(!s.chat&&!s.writings&&!s.stats&&!s.roster,"light page excludes chat, bodies, statistics and roster: "+page);
        }
        check(new UiReadScope("bootstrap").roster&&!new UiReadScope("bootstrap").chat,"ordinary cold start reads roster without chat history");
        check(new UiReadScope("chat").chat&&!new UiReadScope("chat").roster,"chat reads its conversation without querying all character previews");
        check(new UiReadScope("mailbox").writings&&new UiReadScope("diary").writings&&!new UiReadScope("writingSettings").writings,"only reader pages request writing bodies");
        check(new UiReadScope("stats").stats&&!new UiReadScope("gifts").stats,"usage aggregate only requested by usage page");
        check(UiReadScope.directory("settings")&&UiReadScope.directory("settings-reminders")&&!UiReadScope.directory("reachSettings"),"directory routes distinguished from editable settings");
        check(UiReadScope.mutation("open")&&UiReadScope.mutation("features")&&UiReadScope.mutation("chatRead")&&!UiReadScope.mutation("voiceStatus")&&!UiReadScope.mutation("visiblePage"),"data revisions fence selections, saves and reads without voice-poll churn");
        for(String kind:new String[]{"letter","diary"})for(int score:new int[]{20,40,65,85})for(boolean affinity:new boolean[]{false,true})for(boolean cooling:new boolean[]{false,true})for(boolean read:new boolean[]{false,true})for(boolean secretRead:new boolean[]{false,true}){
            JSONObject original=new JSONObject().put("kind",kind).put("character","test").put("date","2026-10-27").put("text","<zh>公开正文</zh>").put("read",read).put("secret","隐藏内容").put("secretRead",secretRead);
            JSONObject shown=WritingData.visible(original,score,cooling,affinity);boolean expected=!shown.optBoolean("locked")&&(!shown.optBoolean("read")||shown.optBoolean("hiddenUnread"));
            String mode=DiaryData.shown(score,cooling,affinity);
            if(UiReadScope.writingUnread(kind,read,true,secretRead,mode,affinity)!=expected)throw new AssertionError("metadata unread differs from public visibility rules");
        }
        check(true,"128 unread combinations match production diary visibility without reading bodies");
        System.out.println("PASS "+n+" UI data scope checks; no Android runtime or API calls.");
    }
}
