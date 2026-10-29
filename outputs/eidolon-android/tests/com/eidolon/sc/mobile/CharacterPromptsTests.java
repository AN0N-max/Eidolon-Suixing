package com.eidolon.sc.mobile;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class CharacterPromptsTests {
    static int n;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);n++;}
    interface Attempt{void run()throws Exception;}
    static void reject(Attempt action,String name)throws Exception{boolean bad=false;try{action.run();}catch(Exception expected){bad=true;}check(bad,name);}
    static JSONObject request(String who,long revision,String sheet,String facts)throws Exception{return new JSONObject().put("character",who).put("revision",revision).put("sheet",sheet).put("facts",facts);}
    public static void main(String[] args)throws Exception{
        JSONArray catalog=new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));JSONObject a=catalog.getJSONObject(0),b=catalog.getJSONObject(1),fresh=PersonalData.fresh();String who=a.getString("id");
        check(!CharacterPrompts.view(a,fresh).getBoolean("modified")&&CharacterPrompts.view(a,fresh).getLong("revision")==0,"legacy profile uses exact built-in defaults");
        String original=a.toString();JSONObject edited=CharacterPrompts.change(fresh,a,request(who,0,"自定义人设\n保留语气","自定义补充资料"),false,1000);
        check(CharacterPrompts.apply(a,edited).getString("sheet").equals("自定义人设\n保留语气"),"effective role sheet uses saved override");
        check(CharacterPrompts.apply(a,edited).getString("facts").equals("自定义补充资料"),"effective role facts use saved override");
        check(original.equals(a.toString())&&!fresh.has("rolePrompts"),"asset object and input personal data remain unchanged");
        check(CharacterPrompts.apply(b,edited).getString("sheet").equals(b.getString("sheet")),"other role stays on its own prompt");
        JSONObject reloaded=new JSONObject(edited.toString());PersonalData.validate(reloaded);check(CharacterPrompts.view(a,reloaded).getBoolean("modified"),"serialized personal backup preserves role overrides");
        reject(()->CharacterPrompts.change(edited,a,request(who,0,"stale",""),false,2000),"stale editor cannot overwrite a newer save");
        JSONObject same=CharacterPrompts.change(edited,a,request(who,1,"自定义人设\n保留语气","自定义补充资料"),false,2000);check(CharacterPrompts.view(a,same).getLong("revision")==1,"identical save does not change revision");
        JSONObject other=CharacterPrompts.change(edited,b,request(b.getString("id"),0,"B的设定","B的资料"),false,2000);
        JSONObject reset=CharacterPrompts.change(other,a,new JSONObject().put("revision",1),true,3000);
        check(!CharacterPrompts.view(a,reset).getBoolean("modified")&&CharacterPrompts.apply(a,reset).getString("facts").equals(a.getString("facts")),"reset returns both fields to defaults");
        check(CharacterPrompts.apply(b,reset).getString("sheet").equals("B的设定"),"reset one role retains another role override");
        check(CharacterPrompts.view(a,reset).getLong("revision")==2,"reset retains a new revision to reject old editors");
        reject(()->CharacterPrompts.change(reset,a,request(who,1,"old-after-reset",""),false,4000),"pre-reset editor remains stale after defaults restored");
        reject(()->CharacterPrompts.change(fresh,a,request(who,0," \n ",""),false,1000),"empty character sheet rejected");
        String tooLong=String.join("",Collections.nCopies(20001,"字"));reject(()->CharacterPrompts.change(fresh,a,request(who,0,tooLong,""),false,1000),"oversized character sheet rejected");
        reject(()->CharacterPrompts.change(fresh,a,request(who,0,"正常",tooLong),false,1000),"oversized supplemental facts rejected");
        JSONObject clearFacts=CharacterPrompts.change(fresh,a,request(who,0,a.getString("sheet"),""),false,1000);check(CharacterPrompts.apply(a,clearFacts).getString("facts").isEmpty(),"explicit empty supplemental facts are respected");
        reject(()->CharacterPrompts.validate(edited,Collections.singleton(b.getString("id"))),"backup rejects unknown character override");
        JSONObject malformed=new JSONObject(edited.toString());malformed.getJSONObject("rolePrompts").getJSONObject(who).put("sheet",17);reject(()->PersonalData.validate(malformed),"backup rejects non-text prompt values");
        JSONObject generated=new JSONObject(fresh.toString()).put("giftsSent",1),merged=PersonalMerge.merge(fresh,generated,edited);check(CharacterPrompts.apply(a,merged).getString("sheet").startsWith("自定义人设")&&merged.getInt("giftsSent")==1,"staged reply effects preserve prompt edited during waiting");
        check(CharacterPrompts.apply(a,edited).getString("id").equals(who)&&CharacterPrompts.apply(a,edited).getString("name").equals(a.getString("name")),"prompt editing does not rewrite role identity");
        for(int i=0;i<catalog.length();i++){JSONObject c=catalog.getJSONObject(i);check(c.getString("sheet").length()<=CharacterPrompts.LIMIT&&c.getString("facts").length()<=CharacterPrompts.LIMIT,"built-in prompt fits editor: "+c.getString("id"));}
        System.out.println("PASS "+n+" character prompt checks; pure JVM, no API or Android persistence execution.");
    }
}
