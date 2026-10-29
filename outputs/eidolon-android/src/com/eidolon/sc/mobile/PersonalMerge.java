package com.eidolon.sc.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Three-way merge staged reply effects onto the latest local profile at delivery time. */
final class PersonalMerge {
    static JSONObject merge(JSONObject base,JSONObject generated,JSONObject current)throws Exception{return (JSONObject)value(base,generated,current,"");}
    private static Object value(Object base,Object generated,Object current,String path)throws Exception{
        if(equal(base,generated))return copy(current);
        if(base instanceof Number&&generated instanceof Number&&current instanceof Number){
            double delta=((Number)generated).doubleValue()-((Number)base).doubleValue(),now=((Number)current).doubleValue();
            if(isAffinity(path))return Math.max(0,Math.min(100,now+delta));
            if(isCounter(path))return Math.max(0,Math.min(1_000_000_000d,now+delta));
        }
        if(base instanceof JSONObject&&generated instanceof JSONObject&&current instanceof JSONObject){
            JSONObject out=new JSONObject(current.toString()),b=(JSONObject)base,g=(JSONObject)generated,c=(JSONObject)current;Set<String> keys=new HashSet<>();b.keys().forEachRemaining(keys::add);g.keys().forEachRemaining(keys::add);
            for(String key:keys){boolean bh=b.has(key),gh=g.has(key),ch=c.has(key);Object bv=bh?b.get(key):JSONObject.NULL,gv=gh?g.get(key):JSONObject.NULL,cv=ch?c.get(key):JSONObject.NULL;
                if(equal(bv,gv))continue;
                String child=path.isEmpty()?key:path+"."+key;
                if(bv instanceof JSONObject&&gv instanceof JSONObject&&cv instanceof JSONObject)out.put(key,value(bv,gv,cv,child));
                else if(bv instanceof JSONArray&&gv instanceof JSONArray&&cv instanceof JSONArray)out.put(key,array((JSONArray)bv,(JSONArray)gv,(JSONArray)cv));
                else if(bv instanceof Number&&gv instanceof Number&&cv instanceof Number&&(isAffinity(child)||isCounter(child)))out.put(key,value(bv,gv,cv,child));
                else if(equal(bv,cv)){if(gh)out.put(key,copy(gv));else out.remove(key);}
            }
            return out;
        }
        if(base instanceof JSONArray&&generated instanceof JSONArray&&current instanceof JSONArray)return array((JSONArray)base,(JSONArray)generated,(JSONArray)current);
        return equal(base,current)?copy(generated):copy(current);
    }
    private static boolean isAffinity(String path){return path.startsWith("affinity.");}
    private static boolean isCounter(String path){if(!path.startsWith("progress.counts."))return false;String key=path.substring("progress.counts.".length());return !key.startsWith("tier/")&&!key.equals("characters/")&&!key.equals("intimate_characters/");}
    private static JSONArray array(JSONArray base,JSONArray generated,JSONArray current)throws Exception{
        if(equal(base,current))return new JSONArray(generated.toString());
        if(hasIds(base)&&hasIds(generated)&&hasIds(current)){
            JSONArray out=new JSONArray(current.toString());Set<String> currentIds=ids(current);
            for(int i=0;i<generated.length();i++){JSONObject next=generated.getJSONObject(i);String id=next.getString("id");JSONObject before=find(base,id),now=find(current,id);
                if(before==null){if(!currentIds.contains(id))out.put(new JSONObject(next.toString()));}
                else if(now!=null&&!equal(before,next)&&equal(before,now)){int index=indexOf(out,id);out.put(index,new JSONObject(next.toString()));}
            }
            return out;
        }
        return new JSONArray(current.toString());
    }
    private static boolean hasIds(JSONArray a){for(int i=0;i<a.length();i++)if(!(a.opt(i) instanceof JSONObject)||!((JSONObject)a.opt(i)).has("id"))return false;return true;}
    private static Set<String> ids(JSONArray a)throws Exception{Set<String>s=new HashSet<>();for(int i=0;i<a.length();i++)s.add(a.getJSONObject(i).getString("id"));return s;}
    private static JSONObject find(JSONArray a,String id)throws Exception{for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);if(id.equals(o.optString("id")))return o;}return null;}
    private static int indexOf(JSONArray a,String id)throws Exception{for(int i=0;i<a.length();i++)if(id.equals(a.getJSONObject(i).optString("id")))return i;return -1;}
    private static Object copy(Object value)throws Exception{if(value instanceof JSONObject)return new JSONObject(value.toString());if(value instanceof JSONArray)return new JSONArray(value.toString());return value;}
    private static boolean equal(Object a,Object b)throws Exception{
        if(a==b)return true;if(a==null||b==null)return false;
        if(a instanceof JSONObject&&b instanceof JSONObject){JSONObject x=(JSONObject)a,y=(JSONObject)b;if(x.length()!=y.length())return false;for(java.util.Iterator<String> i=x.keys();i.hasNext();){String k=i.next();if(!y.has(k)||!equal(x.get(k),y.get(k)))return false;}return true;}
        if(a instanceof JSONArray&&b instanceof JSONArray){JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return false;for(int i=0;i<x.length();i++)if(!equal(x.get(i),y.get(i)))return false;return true;}
        if(a instanceof Number&&b instanceof Number)return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue())==0;
        return a.equals(b);
    }
    private PersonalMerge(){}
}
