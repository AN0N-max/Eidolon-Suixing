package com.eidolon.sc.mobile;

import java.io.*;
import java.util.*;
import org.json.*;

/** Offline fake SSE responses: these checks never open a connection. */
public class ScheduleGenerationTests {
    static int count;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    static String read(String text,String reason)throws Exception{
        String thinking="data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"not a schedule\"}}]}\n\n";
        String data=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("content",text)).put("finish_reason",reason))).toString();
        return new ModelClient().readEvents(new BufferedReader(new StringReader(thinking+"data: "+data+"\n\ndata: [DONE]\n\n")),ignored->{});
    }
    static void rejects(ScheduleTests.Action action,String name)throws Exception{try{action.run();}catch(Exception expected){check(true,name);return;}throw new AssertionError(name);}
    public static void main(String[] args)throws Exception{
        String plan=ScheduleTests.plan().toString();JSONObject facts=new JSONObject().put("character","绯田美琴").put("date","2026-10-23").put("sleepHabit","按角色作息");
        List<JSONObject> requests=new ArrayList<>();
        JSONObject result=ScheduleGeneration.generate("test-model","保留作息和年龄要求",facts,r->{requests.add(r);return read(plan,"stop");},()->{});
        check(result.getJSONArray("slots").length()==3&&requests.size()==1,"complete schedule succeeds in one request despite separate reasoning delta");
        check(requests.get(0).getInt("max_tokens")==4096,"schedule starts with 4096 rather than restrictive 1500 token budget");
        check(!requests.get(0).has("enable_thinking")&&!requests.get(0).has("reasoning_effort"),"generic compatible APIs receive no vendor-specific thinking parameters");
        check(requests.get(0).getJSONArray("messages").getJSONObject(0).getString("content").contains("午夜入睡写 00:00"),"every attempt specifies clock format and unambiguous midnight");
        JSONObject variant=ScheduleTests.plan().put("wake","7:00").put("sleep","24:00");
        variant.getJSONArray("slots").getJSONObject(0).put("start","９：００").put("end","10:00:00");
        variant.getJSONArray("slots").getJSONObject(2).put("end","24:00:00");
        callsForVariant(variant,facts);
        for(String invalid:new String[]{"25:00","07:60","07:00:30","7:0","明早07:00","2026-11-03T07:00","07:00 PM","NaN"}){
            int[] invalidCalls={0};
            try{ScheduleGeneration.generate("m","rules",facts,r->{invalidCalls[0]++;return ScheduleTests.plan().put("wake",invalid).toString();},()->{});throw new AssertionError("invalid clock accepted: "+invalid);}
            catch(Exception expected){check(expected.getMessage().contains("起床")&&invalidCalls[0]==1,"rejects ambiguous or invalid wake without a paid retry: "+invalid);}
        }
        JSONObject invalidStart=ScheduleTests.plan();invalidStart.getJSONArray("slots").getJSONObject(2).put("start","24:00");
        try{ScheduleData.normalize(invalidStart);throw new AssertionError("24:00 start accepted");}catch(Exception expected){check(expected.getMessage().contains("第3段开始"),"time errors identify the exact slot and boundary");}
        JSONObject bath=ScheduleTests.plan().put("bath",new JSONObject().put("at","２１：３０：００").put("minutes",30).put("kind","tub"));
        check(ScheduleData.normalize(bath).getJSONObject("bath").getString("at").equals("21:30"),"bath times use the same canonical parser");
        requests.clear();
        JSONObject recovered=ScheduleGeneration.generate("test-model","保留作息和年龄要求",facts,r->{requests.add(r);return read(requests.size()==1?"{\"wake\":":plan,requests.size()==1?"length":"stop");},()->{});
        check(recovered.getString("wake").equals("07:00")&&requests.size()==2,"provider truncation recovers once with a complete fresh schedule");
        check(requests.get(1).getInt("max_tokens")==8192,"only length retry expands output budget to 8192");
        JSONArray second=requests.get(1).getJSONArray("messages");
        check(second.length()==2&&second.getJSONObject(0).getString("content").contains("保留作息和年龄要求")&&second.getJSONObject(1).getString("content").equals(facts.toString()),"retry retains original persona, date, rules and facts without partial output");
        check(second.getJSONObject(0).getString("content").contains("只排三段"),"retry requests a compact plan");
        int[] calls={0};
        try{ScheduleGeneration.generate("test-model","rules",facts,r->{calls[0]++;return read(plan,"length");},()->{});throw new AssertionError("truncated completion accepted");}
        catch(IOException expected){check(calls[0]==2&&expected.getMessage().contains("后台与判断模型")&&!expected.getMessage().contains("缩短问题"),"two length failures stop with schedule-specific model-setting advice");}
        calls[0]=0;rejects(()->ScheduleGeneration.generate("m","rules",facts,r->{calls[0]++;throw new IOException("HTTP 429");},()->{}),"network and quota errors propagate");
        check(calls[0]==1,"network and quota errors do not automatically multiply requests");
        calls[0]=0;rejects(()->ScheduleGeneration.generate("m","rules",facts,r->{calls[0]++;return read("{\"wake\":","stop");},()->{}),"incomplete JSON never becomes a schedule even after normal finish");
        check(calls[0]==1,"malformed JSON does not trigger the length retry");
        boolean[] stopped={false};calls[0]=0;
        rejects(()->ScheduleGeneration.generate("m","rules",facts,r->{calls[0]++;stopped[0]=true;return read("","length");},()->{if(stopped[0])throw new IOException("已停止");}),"cancellation or changed data epoch blocks retry after truncation");
        check(calls[0]==1,"cancelled job cannot make a second model request");
        calls[0]=0;rejects(()->ScheduleGeneration.generate("m","rules",facts,r->{calls[0]++;return plan;},()->{throw new IOException("已停止");}),"cancel before start prevents generation");
        check(calls[0]==0,"stopped task opens no connection");
        stopped[0]=false;rejects(()->ScheduleGeneration.generate("m","rules",facts,r->{stopped[0]=true;return plan;},()->{if(stopped[0])throw new IOException("已停止");}),"cancelled successful response cannot become a saved plan");
        System.out.println("Passed "+count+" schedule generation checks. Fake responses only; no API or Android runtime.");
    }
    static void callsForVariant(JSONObject variant,JSONObject facts)throws Exception{
        int[] calls={0};JSONObject value=ScheduleGeneration.generate("m","rules",facts,r->{calls[0]++;return read(variant.toString(),"stop");},()->{});
        check(calls[0]==1&&value.getString("wake").equals("07:00")&&value.getString("sleep").equals("00:00"),"unpadded wake and midnight bedtime recover locally in one request");
        check(value.getJSONArray("slots").getJSONObject(0).getString("start").equals("09:00")&&value.getJSONArray("slots").getJSONObject(0).getString("end").equals("10:00"),"full-width clocks and zero seconds save as canonical HH:mm");
        check(value.getJSONArray("slots").getJSONObject(2).getString("end").equals("24:00"),"midnight slot end preserves day boundary");
        check(ScheduleData.normalize(value).toString().equals(value.toString()),"clock normalization is stable on repeated save and backup validation");
        check(variant.getString("sleep").equals("24:00"),"normalization does not mutate source records");
        JSONObject row=new JSONObject().put("character","test").put("date","2026-11-03").put("plan",variant);
        rejects(()->ScheduleData.validateEntry(row,Collections.singleton("test")),"raw noncanonical clocks cannot bypass normalization through backup restore");
        ScheduleData.validateEntry(row.put("plan",value),Collections.singleton("test"));
        check(true,"normalized model clocks remain valid v17 backup rows");
    }
}
