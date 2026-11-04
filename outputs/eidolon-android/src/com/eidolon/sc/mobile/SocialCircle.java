package com.eidolon.sc.mobile;
import java.util.*;import org.json.*;
/** Same birthday-first, unit/related/occasional stranger draw as original moment_draws. */
final class SocialCircle {
 static List<String> draw(Collection<String> mates,Collection<String> related,Collection<String> others,Set<String> birthdays,boolean birthday,Random random){
  int size=(birthday?8:4)+random.nextInt(3);List<String> out=new ArrayList<>();LinkedHashSet<String> known=new LinkedHashSet<>(mates);known.addAll(related);
  for(String who:known)if(birthdays.contains(who)&&out.size()<size)out.add(who);
  List<String> strangers=new ArrayList<>(new LinkedHashSet<>(others));strangers.removeAll(known);
  if(!strangers.isEmpty()&&out.size()<size&&random.nextDouble()<.25)out.add(strangers.remove(random.nextInt(strangers.size())));
  List<String> left=new ArrayList<>(known);left.removeAll(out);
  while(!left.isEmpty()&&out.size()<size){double total=0;for(String who:left)total+=mates.contains(who)?3:1;double pick=random.nextDouble()*total;int at=left.size()-1;for(int i=0;i<left.size();i++){pick-=mates.contains(left.get(i))?3:1;if(pick<0){at=i;break;}}out.add(left.remove(at));}
  while(!strangers.isEmpty()&&out.size()<size)out.add(strangers.remove(random.nextInt(strangers.size())));return out;
 }
 static JSONArray material(JSONArray chars,JSONObject profiles,String who,boolean birthday,long now,Random random)throws Exception{
  JSONObject self=WritingEngine.character(chars,who),profile=profiles.optJSONObject(who);JSONArray input=profile==null?null:profile.optJSONArray("mates");
  Set<String> mates=new LinkedHashSet<>(),related=new LinkedHashSet<>(),birthdays=new HashSet<>(),others=new LinkedHashSet<>();if(input!=null)for(int i=0;i<input.length();i++)mates.add(input.getString(i));
  String facts=self.optString("facts"),section="";int from=facts.indexOf("人物关系");if(from>=0){int end=facts.indexOf("\n## ",from);section=facts.substring(from,end<0?facts.length():end);}
  String day=SocialData.day(now);
  for(int i=0;i<chars.length();i++){
   JSONObject p=chars.getJSONObject(i);String id=p.getString("id");if(id.equals(who)||id.equals("hazuki"))continue;others.add(id);JSONObject meta=profiles.optJSONObject(id);
   if(meta!=null){if(meta.optString("birthday").equals(day.substring(5)))birthdays.add(id);JSONArray aliases=meta.optJSONArray("aliases");if(aliases!=null)for(int a=0;a<aliases.length();a++)if(!aliases.getString(a).isEmpty()&&section.contains(aliases.getString(a)))related.add(id);}
  }
  mates.retainAll(others);related.retainAll(others);JSONArray out=new JSONArray();for(String id:draw(mates,related,others,birthdays,birthday,random)){JSONObject p=WritingEngine.character(chars,id);out.put(new JSONObject().put("character",id).put("name",p.getString("name")).put("sameUnit",mates.contains(id)).put("persona",MediaData.cut(p.optString("sheet"),1100)));}return out;
 }
}
