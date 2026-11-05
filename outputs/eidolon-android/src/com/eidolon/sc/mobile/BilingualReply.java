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
        return DialogueText.language(ReplyText.clean(answer),"ja",false);
    }
    static boolean needsChinese(String answer) { return DialogueText.needsChinese(ReplyText.clean(answer)); }

    static String withChinese(String original,String translated) throws IOException {
        original=ReplyText.clean(original);translated=ReplyText.clean(translated);
        if(!needsChinese(original))throw new IOException("这条消息已有中文，无需修复");
        String chinese=DialogueText.language(DialogueText.fields("",translated,false),"zh",true);
        if(chinese.isEmpty()||!HAN.matcher(chinese).find()||chinese.length()>12000)
            throw new IOException("模型未返回可确认的中文译文，请稍后重试");
        String base=original;
        if(DialogueText.language(base,"ja",false).isEmpty())throw new IOException("没有可确认的日文原文，未修改消息");
        return base+"<zh>"+chinese+"</zh>";
    }
}
