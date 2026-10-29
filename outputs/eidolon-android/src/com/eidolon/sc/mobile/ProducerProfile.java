package com.eidolon.sc.mobile;

import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The original site's producer-profile fields, kept in local personal data. */
final class ProducerProfile {
    static JSONObject empty()throws Exception{return new JSONObject().put("address","").put("gender","").put("timeZone","").put("birthMonth",0).put("birthDay",0).put("anniversaryMonth",0).put("anniversaryDay",0);}
    static void validate(JSONObject p)throws Exception{
        String address=p.optString("address"),gender=p.optString("gender"),zone=p.optString("timeZone");
        if(address.length()>80||address.contains("\n")||address.contains("\r")||
            !(gender.isEmpty()||gender.equals("male")||gender.equals("female"))||zone.length()>80)throw new Exception("制作人档案格式不正确");
        if(!zone.isEmpty())try{ZoneId.of(zone);}catch(Exception e){throw new Exception("时区无效");}
        int month=p.optInt("birthMonth"),day=p.optInt("birthDay");
        if((month==0)!=(day==0))throw new Exception("生日请同时选择月和日");
        if(month!=0)try{MonthDay.of(month,day);}catch(Exception e){throw new Exception("生日日期无效");}
        int anniversaryMonth=p.optInt("anniversaryMonth"),anniversaryDay=p.optInt("anniversaryDay");
        if((anniversaryMonth==0)!=(anniversaryDay==0))throw new Exception("自定纪念日请同时选择月和日");
        if(anniversaryMonth!=0)try{MonthDay.of(anniversaryMonth,anniversaryDay);}catch(Exception e){throw new Exception("自定纪念日无效");}
    }
    static String prompt(JSONObject personal)throws Exception{
        JSONObject p=personal.optJSONObject("producer");if(p==null)p=empty();validate(p);
        ZoneId zone=p.optString("timeZone").isEmpty()?ZoneId.systemDefault():ZoneId.of(p.getString("timeZone"));
        ZonedDateTime now=ZonedDateTime.now(zone);StringBuilder result=new StringBuilder("\n制作人档案（只影响当下对待制作人的方式，不改写原作剧情）：");
        result.append("称呼：").append(p.optString("address").isEmpty()?"沿用角色原有习惯":p.getString("address"));
        result.append("；性别：").append(p.optString("gender").equals("female")?"女性":p.optString("gender").equals("male")?"男性":"沿用原作设定");
        result.append("；制作人所在时区：").append(zone.getId()).append("；当前时间：").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.ROOT)));
        int month=p.optInt("birthMonth"),day=p.optInt("birthDay");
        if(month>0){result.append("；生日：").append(month).append("月").append(day).append("日（不含年份）");
            int thisYearDay=month==2&&day==29&&!now.toLocalDate().isLeapYear()?28:day;
            if(now.getMonthValue()==month&&now.getDayOfMonth()==thisYearDay)result.append("。今天是制作人的生日，请自然送上祝福，不要推测年龄");
        }else result.append("；生日未设置，不要推测生日");
        int anniversaryMonth=p.optInt("anniversaryMonth"),anniversaryDay=p.optInt("anniversaryDay");
        if(anniversaryMonth>0)result.append("；制作人自定纪念日：").append(anniversaryMonth).append("月").append(anniversaryDay).append("日（每年重复）");
        return result.toString();
    }
}
