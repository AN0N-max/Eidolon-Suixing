package com.eidolon.sc.mobile;
import android.app.PendingIntent;
import android.content.*;
/** An ordinary launcher/resume never requests a conversation route. */
final class ChatEntry {
    static final String OPEN="com.eidolon.sc.mobile.OPEN_CONVERSATION";
    static boolean requested(String action,String who,String session){return who!=null&&!who.isEmpty()&&session!=null&&!session.isEmpty()&&(action==null||OPEN.equals(action));}
    static boolean requested(Intent i){return i!=null&&requested(i.getAction(),i.getStringExtra("reachCharacter"),i.getStringExtra("reachSession"));}
    static void consume(Intent i){if(i==null)return;i.removeExtra("reachCharacter");i.removeExtra("reachSession");}
    static PendingIntent pending(Context c,String who,String session){Intent i=new Intent(c,WebActivity.class).setAction(OPEN).putExtra("reachCharacter",who).putExtra("reachSession",session).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(c,who.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private ChatEntry(){}
}
