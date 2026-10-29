package com.eidolon.sc.mobile;
import org.json.*;
import java.util.*;
final class ReachFairness {
    static boolean changed(ReachConfig before,ReachConfig after)throws Exception{
        for(String k:new String[]{"attemptLimit","historyLimit","memoryLimit","mode","hours","time","zone","dailyLimit","characterDailyLimit","contactDays","minAffinity","trustMin","trustMax","closeMin","closeMax","intimateMin","intimateMax","quietEnabled","quietStart","quietEnd","dailyRespectsQuiet","spreadMinutes","restrictCharacters","characters","characterLimits","planGapMinutes","recentMinutes","expiryMinutes"})if(!same(before.json().get(k),after.json().get(k)))return true;
        return false;
    }
    static boolean same(Object a,Object b)throws Exception{if(a instanceof JSONObject&&b instanceof JSONObject){JSONObject x=(JSONObject)a,y=(JSONObject)b;if(x.length()!=y.length())return false;for(Iterator<String> i=x.keys();i.hasNext();){String k=i.next();if(!y.has(k)||!same(x.get(k),y.get(k)))return false;}return true;}if(a instanceof JSONArray&&b instanceof JSONArray){JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return false;Set<String> left=new HashSet<>(),right=new HashSet<>();for(int i=0;i<x.length();i++){left.add(x.get(i).toString());right.add(y.get(i).toString());}return left.equals(right);}if(a instanceof Number&&b instanceof Number)return ((Number)a).doubleValue()==((Number)b).doubleValue();return a.equals(b);}
    static void order(List<JSONObject> list,String day){list.sort((a,b)->{int n=Integer.compare(a.optInt("sent"),b.optInt("sent"));if(n!=0)return n;n=Long.compare(a.optLong("lastReach"),b.optLong("lastReach"));if(n!=0)return n;return Long.compare(Integer.toUnsignedLong((day+a.optString("character")).hashCode()),Integer.toUnsignedLong((day+b.optString("character")).hashCode()));});}
    static long deferAt(long now,long expires,long minutes){long next=now+minutes*60000L;return next<expires?next:-1;}
    private ReachFairness(){}
}
