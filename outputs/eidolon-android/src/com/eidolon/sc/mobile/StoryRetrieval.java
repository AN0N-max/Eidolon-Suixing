package com.eidolon.sc.mobile;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared local retrieval policy for Activity and recovered background replies. */
final class StoryRetrieval {
    final int candidates,terms,passages,chars;
    StoryRetrieval(JSONObject options)throws Exception{
        candidates=options.optInt("candidates",120);terms=options.optInt("terms",24);
        passages=options.optInt("passages",2);chars=options.optInt("chars",900);
        if(candidates<30||candidates>2000||terms<8||terms>64||passages<1||passages>8||chars<300||chars>6000)
            throw new java.io.IOException("候选剧情应为 30–2000 条，关键词 8–64 个，引用 1–8 段，每段 300–6000 字");
        if((long)passages*chars>24000)throw new java.io.IOException("引用段数 × 每段字数不能超过 24000 字，请减少其中一项");
    }
    static StoryRetrieval read(SharedPreferences prefs)throws Exception{
        return new StoryRetrieval(new JSONObject().put("candidates",prefs.getInt("storyRetrieval.candidates",120))
            .put("terms",prefs.getInt("storyRetrieval.terms",24)).put("passages",prefs.getInt("storyRetrieval.passages",2))
            .put("chars",prefs.getInt("storyRetrieval.chars",900)));
    }
    JSONObject json()throws Exception{return new JSONObject().put("candidates",candidates).put("terms",terms).put("passages",passages).put("chars",chars);}
    static JSONArray search(ChatStore store,SharedPreferences prefs,String query,String who)throws Exception{
        StoryRetrieval policy=read(prefs);
        return store.searchStories(query,who,policy.passages,policy.candidates,policy.chars,policy.terms);
    }
}
