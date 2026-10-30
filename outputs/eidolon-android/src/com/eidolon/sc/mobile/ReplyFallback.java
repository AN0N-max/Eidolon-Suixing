package com.eidolon.sc.mobile;
import android.content.Context;
/** Compatibility entry points for JobScheduler and existing ownership checks. */
final class ReplyFallback {
 static boolean generating(){return ConversationEngine.generating();}
 static void stop(String id,long revision){ConversationEngine.stop(id,revision);}
 static void generate(Context c,ChatStore s,ChatStore.PendingReply p){ConversationEngine.generate(c,s,p);}
 static boolean requeueOrphan(ChatStore s,ChatStore.PendingReply p){return ConversationEngine.requeueOrphan(s,p);}
 static boolean owned(String id,long revision,Thread owner){return ConversationEngine.owned(id,revision,owner);}
 private ReplyFallback(){}
}
