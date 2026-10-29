package com.eidolon.sc.mobile;
import android.content.Context;
import org.json.JSONObject;
/** Compatibility shim: message presentation is owned by Android notifications. */
final class OverlayBanner {
 static boolean allowed(Context c){return false;}
 static boolean show(Context c,JSONObject message){return false;}
 private OverlayBanner(){}
}
