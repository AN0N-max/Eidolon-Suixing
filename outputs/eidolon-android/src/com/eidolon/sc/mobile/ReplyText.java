package com.eidolon.sc.mobile;
import java.io.IOException;
import java.util.regex.Pattern;

/** Remove only reserved transport-time annotations, not dates spoken in dialogue. */
final class ReplyText {
    private static final String STAMP="\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T\\s]+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*(?:Z|[+-]\\d{2}:?\\d{2}))?";
    private static final String TIME="(?:"+STAMP+"|发送时间未知（旧记录）|时间未知|未知)";
    private static final Pattern BRACKET=Pattern.compile("(?is)(?:【|\\[)\\s*(?:消息发送时间|发送时间)\\s*[:：]\\s*"+TIME+"(?:\\s*[;；][^】\\]]{0,180})?\\s*(?:】|\\])");
    private static final Pattern LINE=Pattern.compile("(?im)^[\\t ]*(?:\\*\\*)?(?:消息发送时间|发送时间|当前回复生成时间)\\s*[:：]\\s*"+TIME+"(?:[\\t ]*[;；][^\\r\\n]{0,180})?(?:\\*\\*)?[\\t ]*(?:\\r?\\n|$)");
    static String clean(String text){if(text==null)return "";String out=BRACKET.matcher(text).replaceAll("");out=LINE.matcher(out).replaceAll("");return out.replaceAll("(?s)<(ja|zh)>\\s*</\\1>","").trim();}
    static String require(String text)throws IOException{String out=clean(text);if(out.replaceAll("(?s)</?(?:ja|zh)>","").trim().isEmpty())throw new IOException("回复只有内部时间标记，没有可用台词，请重试");return out;}
    static String visible(String text){String out=clean(text);return out.isEmpty()?"（这条旧回复没有可用的角色台词）":out;}
    private ReplyText(){}
}
