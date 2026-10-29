package com.eidolon.sc.mobile;

import java.time.*;
import java.util.regex.*;

/** Resolve only explicit, bounded dates locally. Never trust an LLM's date arithmetic. */
final class PromiseDate {
    static LocalDate[] resolve(String phrase,LocalDate today)throws Exception{
        String s=phrase.trim();LocalDate first=null,last=null;
        if(s.matches("今天|今晚|今日"))first=today;
        else if(s.matches("明天|明晚|明日"))first=today.plusDays(1);
        else if(s.matches("后天|后天晚上"))first=today.plusDays(2);
        else if(s.matches("本周末|这周末|周末|下周末")){
            first=today.minusDays(today.getDayOfWeek().getValue()-1).plusDays(s.equals("下周末")?12:5);last=first.plusDays(1);
            if(last.isBefore(today))throw new Exception("日期已过去");if(first.isBefore(today))first=today;
        }else if(s.equals("月底前")){first=today;last=today.withDayOfMonth(today.lengthOfMonth());}
        else if(s.matches("月底|这个月底|本月底")){first=today.withDayOfMonth(today.lengthOfMonth());}
        else{
            Matcher week=Pattern.compile("(本|这|下)?(?:周|星期)([一二三四五六日天])").matcher(s);
            Matcher iso=Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}").matcher(s);
            Matcher date=Pattern.compile("(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})[日号]").matcher(s);
            if(week.matches()){
                int dow="一二三四五六日".indexOf(week.group(2).equals("天")?"日":week.group(2))+1;
                first=today.minusDays(today.getDayOfWeek().getValue()-1).plusDays(dow-1+("下".equals(week.group(1))?7:0));
                if(week.group(1)==null&&first.isBefore(today))first=first.plusDays(7);
            }else if(iso.matches())first=LocalDate.parse(s);
            else if(date.matches()){
                first=LocalDate.of(date.group(1)==null?today.getYear():Integer.parseInt(date.group(1)),Integer.parseInt(date.group(2)),Integer.parseInt(date.group(3)));
                if(date.group(1)==null&&first.isBefore(today))first=first.plusYears(1);
            }
        }
        if(first==null)throw new Exception("请使用明确日期，如明天、下周六或 2026-10-25");
        if(last==null)last=first;check(first,last,today);return new LocalDate[]{first,last};
    }
    static void check(LocalDate first,LocalDate last,LocalDate today)throws Exception{
        if(first.isBefore(today)||last.isBefore(first)||last.isAfter(today.plusYears(2))||last.isAfter(first.plusDays(31)))throw new Exception("约定日期应在今天至两年内，范围最多 32 天");
    }
}
