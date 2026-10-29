package com.eidolon.sc.mobile;
import android.content.*;
import org.json.*;
import java.time.*;
import java.util.*;

/** Read-only view over existing letter plans, event dates and durable diary attempts. */
final class WritingPlanView {
    static JSONObject get(Context c,ChatStore store)throws Exception{
        android.content.SharedPreferences p=c.getSharedPreferences("settings",0);JSONObject personal=store.personal();ZoneId zone=ScheduleData.zone(personal);long stamp=System.currentTimeMillis();LocalDate today=Instant.ofEpochMilli(stamp).atZone(zone).toLocalDate();JSONArray rows=new JSONArray(),letters=LetterPlanStore.all(store);
        for(int i=0;i<letters.length();i++){JSONObject r=letters.getJSONObject(i);LocalDate date=LocalDate.parse(r.getString("date"));if(date.isBefore(today.minusDays(3))||date.isAfter(today.plusDays(1)))continue;
            String state=r.getString("state");if(WritingStore.exists(store,"letter",r.getString("character"),r.getString("date")))state="completed";else if(state.equals("pending")&&LetterPlanData.expired(r,stamp))state="skipped";else if(state.equals("pending")&&LetterPlanStore.key(r).equals(LettersEngine.inflight))state="generating";
            rows.put(new JSONObject().put("kind","letter").put("character",r.getString("character")).put("date",r.getString("date")).put("zone",r.getString("zone")).put("state",state).put("writeAt",r.getLong("nextAttempt")).put("deliverAt",r.getLong("deliverAt")).put("attempts",r.getInt("attempts")).put("occasion",r.getString("occasion")).put("error",r.getString("error")).put("enabled",p.getBoolean("hibiLetters",false)));
        }
        JSONArray events=WritingStore.events(store);Set<String> seen=new HashSet<>();for(int i=0;i<events.length();i++){JSONObject event=events.getJSONObject(i);String day=event.getString("date"),who=event.getString("character");LocalDate date=LocalDate.parse(day);if(date.isBefore(today.minusDays(3))||date.isAfter(today)||!seen.add(who+"/"+day))continue;
            JSONObject attempt=DiaryStore.attempt(store,who,day);boolean saved=WritingStore.exists(store,"diary",who,day),cooling=Progression.relation(personal,who,stamp).optBoolean("cooling");boolean blocked=DiaryData.shown(personal.getJSONObject("affinity").optDouble(who,50),cooling,p.getBoolean("affinity",true)).equals("closed");
            String state=WritingMorningRules.diaryState(day,today,stamp,zone,saved,blocked,attempt);if((who+"/"+day).equals(WritingEngine.diaryInflight))state="generating";
            rows.put(new JSONObject().put("kind","diary").put("character",who).put("date",day).put("zone",zone.getId()).put("state",state).put("writeAt",WritingMorningRules.nextDiary(day,zone,attempt)).put("attempts",attempt==null?0:attempt.optInt("tries")).put("error",diaryError(store,who,day)).put("enabled",p.getBoolean("hibiDiary",false)));
        }
        return new JSONObject().put("date",today.toString()).put("zone",zone.getId()).put("checkedAt",p.getLong("writingMorningAt",0)).put("checkedDay",p.getString("writingMorningDay","")).put("checkedZone",p.getString("writingMorningZone","")).put("nextCheck",p.getLong("writingMorningNext",0)).put("error",p.getString("writingMorningError","")).put("letters",p.getBoolean("hibiLetters",false)).put("diary",p.getBoolean("hibiDiary",false)).put("rows",rows);
    }
    private static String diaryError(ChatStore s,String who,String day)throws Exception{try(android.database.Cursor c=s.getReadableDatabase().rawQuery("SELECT error FROM diary_attempts WHERE id=?",new String[]{who+"/"+day})){return c.moveToFirst()?c.getString(0):"";}}
    private WritingPlanView(){}
}
