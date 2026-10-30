package com.eidolon.sc.mobile;

import org.json.*;

public final class PersonalMergeTests {
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        JSONObject base=new JSONObject("{\"affinity\":{\"mano\":50},\"progress\":{\"counts\":{\"turns/mano\":4,\"tier/mano\":1}},\"memories\":[{\"id\":\"old\",\"text\":\"原事实\"}],\"draws\":7}");
        JSONObject generated=new JSONObject("{\"affinity\":{\"mano\":60},\"progress\":{\"counts\":{\"turns/mano\":5,\"tier/mano\":1}},\"memories\":[{\"id\":\"old\",\"text\":\"原事实\",\"supersededBy\":\"new\"},{\"id\":\"new\",\"text\":\"更新事实\"}],\"draws\":7}");
        JSONObject current=new JSONObject("{\"affinity\":{\"mano\":55},\"progress\":{\"counts\":{\"turns/mano\":9,\"tier/mano\":1}},\"memories\":[{\"id\":\"old\",\"text\":\"原事实\"},{\"id\":\"manual\",\"text\":\"等待期间新增\"}],\"draws\":8}");
        JSONObject merged=PersonalMerge.merge(base,generated,current);
        check(merged.getJSONObject("affinity").getInt("mano")==65,"staged affinity change adds to concurrent score");
        check(merged.getJSONObject("progress").getJSONObject("counts").getInt("turns/mano")==10,"staged turn count adds to concurrent count");
        check(merged.getJSONObject("progress").getJSONObject("counts").getInt("tier/mano")==1,"derived tier count keeps concurrent value");
        check(merged.getInt("draws")==8,"unrelated inventory update is preserved");
        check(merged.getJSONArray("memories").length()==3,"generated and concurrent memories are merged");
        check(merged.getJSONArray("memories").getJSONObject(0).optString("supersededBy").equals("new"),"memory supersession applies when baseline record is unchanged");
        check(PersonalMerge.merge(base,generated,base).getJSONObject("affinity").getInt("mano")==60,"staged relationship result applies when no concurrent update exists");
        JSONObject staged=PersonalMerge.stage(base,generated);JSONObject compactMerged=PersonalMerge.merge(staged.getJSONObject("base"),staged.getJSONObject("personal"),current);
        check(LifeHistory.canonical(merged).equals(LifeHistory.canonical(compactMerged)),"compact effects retain concurrent affinity, inventory and memory merge semantics");
        check(!staged.getJSONObject("base").has("draws")&&!staged.getJSONObject("personal").has("draws"),"unchanged data branches are not duplicated in pending effects");
        JSONObject oldEpoch=new JSONObject(base.toString()).put("memoryEpoch",0),newEpoch=new JSONObject(generated.toString()).put("memoryEpoch",0),cleared=new JSONObject(current.toString()).put("memoryEpoch",1).put("memories",new JSONArray());
        JSONObject guarded=PersonalMerge.merge(oldEpoch,newEpoch,cleared);check(guarded.getJSONArray("memories").length()==0&&guarded.getLong("memoryEpoch")==1,"delayed generation cannot recreate memories cleared while waiting");
        JSONObject compactEpoch=PersonalMerge.stage(oldEpoch,newEpoch);check(PersonalMerge.merge(compactEpoch.getJSONObject("base"),compactEpoch.getJSONObject("personal"),cleared).getJSONArray("memories").length()==0,"compaction preserves memory epoch guard");
        JSONObject high=new JSONObject("{\"affinity\":{\"mano\":98}}");JSONObject capped=PersonalMerge.merge(new JSONObject("{\"affinity\":{\"mano\":95}}"),new JSONObject("{\"affinity\":{\"mano\":100}}"),high);
        check(capped.getJSONObject("affinity").getInt("mano")==100,"affinity delta respects score ceiling");
    }
}
