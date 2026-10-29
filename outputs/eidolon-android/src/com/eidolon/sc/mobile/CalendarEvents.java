package com.eidolon.sc.mobile;

import java.time.*;
import org.json.*;

/** Character birthdays and holidays adapted from Eidolon-Hibi's calendar. */
final class CalendarEvents {
    private CalendarEvents() {}

    static String prompt(JSONObject calendar, JSONObject personal, String who) throws Exception {
        JSONObject producer=personal.optJSONObject("producer");
        String zoneName=producer==null?"":producer.optString("timeZone");
        ZoneId zone=zoneName.isEmpty()?ZoneId.systemDefault():ZoneId.of(zoneName);
        LocalDate today=LocalDate.now(zone);
        String day=String.format(java.util.Locale.ROOT,"%02d-%02d",today.getMonthValue(),today.getDayOfMonth());
        StringBuilder note=new StringBuilder();
        note.append(Anniversaries.lines(personal,who,today));
        if(producer!=null&&producer.optInt("anniversaryMonth")==today.getMonthValue()){
            int anniversaryDay=producer.optInt("anniversaryDay");if(anniversaryDay==today.getDayOfMonth()||(today.getMonthValue()==2&&anniversaryDay==29&&!today.isLeapYear()&&today.getDayOfMonth()==28))note.append("今天是制作人自定纪念日。 ");
        }
        JSONArray birthdays=calendar.getJSONArray("birthdays");
        for(int i=0;i<birthdays.length();i++){
            JSONObject birthday=birthdays.getJSONObject(i);
            String date=birthday.getString("date");
            if(date.equals("02-29")&&!today.isLeapYear())date="02-28";
            if(date.equals(day)&&birthday.getString("character").equals(who))note.append("今天是你的生日。 ");
        }
        JSONArray holidays=calendar.getJSONArray("holidays");
        for(int i=0;i<holidays.length();i++){
            JSONObject holiday=holidays.getJSONObject(i);
            String date=holiday.optString("date");
            JSONObject dates=holiday.optJSONObject("dates");
            if(dates!=null)date=dates.optString(String.valueOf(today.getYear()));
            if(date.equals(day))note.append("今天是").append(holiday.getString("name")).append("。 ");
        }
        return note.length()==0?"":"\n本地日历提示："+note+"只在话题合适时自然提起；不要把日历资料当作改写原作剧情的依据。";
    }
}
