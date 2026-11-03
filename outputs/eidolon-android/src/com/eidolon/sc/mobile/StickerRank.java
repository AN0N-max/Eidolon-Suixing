package com.eidolon.sc.mobile;
import org.json.*;

/** Local candidate ordering: topic relevance first; personal series preferences are a bounded bias. */
final class StickerRank {
    static double weight(JSONObject row,JSONObject profile){
        JSONArray series=profile.optJSONArray("preferredSeries");String value=MediaData.series(row);
        if(series!=null&&!value.isEmpty())for(int i=0;i<series.length();i++)if(value.equals(series.optString(i)))return 1+.6/(i+1);
        return 1;
    }
    static double score(JSONObject row,JSONObject profile,String query,String context){
        String semantics=MediaData.semantics(row)+" "+row.optString("category");
        double topic=Recall.score(query,semantics);
        // A favourite brand cannot manufacture a match for an unrelated topic.
        if(topic<=0)return 0;
        double intent=Recall.score(query,row.optString("name")+" "+row.optString("intent"));
        double emotion=Recall.score(String.valueOf(profile.optJSONArray("emotions")),row.optString("emotion"));
        double category=MediaData.contains(profile.optJSONArray("preferred"),row.optString("category"))?.1:0;
        double background=.05*Recall.score(context,semantics);
        return (3*topic+2*intent+.1*emotion+category+background)*weight(row,profile);
    }
    private StickerRank(){}
}
