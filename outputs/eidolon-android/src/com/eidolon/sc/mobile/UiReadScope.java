package com.eidolon.sc.mobile;
import java.util.*;
/** Page data dependencies. Directories have no data barrier. */
final class UiReadScope {
    final String page;final boolean full,roster,chat,writings,stats,reach;
    UiReadScope(String page){this.page=page;full="full".equals(page);roster=full||"bootstrap".equals(page)||"roster".equals(page)||"relationshipPanel".equals(page)||"anniversaries".equals(page);chat=full||"chat".equals(page);writings=full||"mailbox".equals(page)||"diary".equals(page);stats=full||"stats".equals(page);reach=full||"reachSettings".equals(page);}
    static boolean directory(String page){return "settings".equals(page)||page.startsWith("settings-");}
    static boolean writingUnread(String kind,boolean read,boolean secret,boolean secretRead,String shown,boolean affinity){return !"diary".equals(kind)?!read:!"closed".equals(shown)&&(!read||affinity&&"secret".equals(shown)&&secret&&!secretRead);}
    static boolean mutation(String action){return Arrays.asList("open","reachOpen","send","cancel","chatRead","appealReply","anniversarySet","promiseSettings","promiseAction","scheduleSettings","writingSettings","writingRead","replyDelaySettings","producerAvatarClear","diarySecret","replyNow","pendingCancel","pendingRetry","displaySettings","chatAppearance","rolePromptSave","rolePromptReset","notebookSave","notebookDelete","notebookAccept","reachSettings","reachTaskRetry","modelService","storyDelete","storyRetrievalSettings","features","settings","profile","memoryClear","memorySave","memoryDelete","draw","give","reset").contains(action);}
    private UiReadScope(){this("full");}
}
