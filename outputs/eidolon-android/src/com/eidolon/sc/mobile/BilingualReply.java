package com.eidolon.sc.mobile;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects a missing Chinese translation without changing the original Japanese reply. */
final class BilingualReply {
    private static final Pattern JA=Pattern.compile("(?s)<ja>(.*?)</ja>");
    private static final Pattern ZH=Pattern.compile("(?s)<zh>(.*?)</zh>");
    private static final Pattern KANA=Pattern.compile("[\\u3040-\\u30ff]");
    private static final Pattern HAN=Pattern.compile("[\\u3400-\\u9fff]");

    static String japanese(String answer) {
        answer=ReplyText.clean(answer);
        Matcher matcher=JA.matcher(answer);StringBuilder result=new StringBuilder();
        while(matcher.find())if(!matcher.group(1).trim().isEmpty()){
            if(result.length()>0)result.append('\n');result.append(matcher.group(1).trim());
        }
        if(result.length()>0)return result.toString();
        return KANA.matcher(answer).find()?answer.replaceAll("(?s)</?(?:ja|zh)>","").trim():"";
    }
    static boolean needsChinese(String answer) {
        if(japanese(answer).isEmpty())return false;
        Matcher matcher=ZH.matcher(answer);
        while(matcher.find())if(!matcher.group(1).trim().isEmpty())return false;
        return true;
    }
    static String withChinese(String original,String translated) throws IOException {
        original=ReplyText.clean(original);translated=ReplyText.clean(translated);
        if(!needsChinese(original))throw new IOException("这条消息已有中文，无需修复");
        String chinese=translated.trim().replaceAll("(?s)</?(?:ja|zh)>","").trim();
        if(chinese.isEmpty()||!HAN.matcher(chinese).find()||KANA.matcher(chinese).find()||chinese.length()>12000)
            throw new IOException("模型未返回有效的简体中文，请稍后重试");
        String base=original.replaceAll("(?s)<zh>\\s*</zh>","").trim();
        if(!JA.matcher(base).find())base="<ja>"+base+"</ja>";
        return base+"<zh>"+chinese+"</zh>";
    }
}
