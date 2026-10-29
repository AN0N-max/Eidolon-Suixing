package com.eidolon.sc.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

final class ThemePrefs {
    static boolean dark(Context context) { return context.getSharedPreferences("appearance",0).getBoolean("night",false); }
    static JSONObject get(Context context) throws Exception { return new JSONObject().put("dark",dark(context)); }
    static JSONObject set(Context context, JSONObject args) throws Exception {
        if(!context.getSharedPreferences("appearance",0).edit().putBoolean("night",args.getBoolean("dark")).commit())
            throw new Exception("夜间模式保存失败");
        apply(context); return get(context);
    }
    static void beforeCreate(Context context) {
        if(context instanceof Activity)((Activity)context).setTheme(dark(context)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
    }
    static void apply(Context context) {
        if(!(context instanceof Activity))return;
        Activity activity=(Activity)context;
        beforeCreate(context);
        boolean night=dark(context);
        Window window=activity.getWindow();
        int background=Color.rgb(night?17:255,night?22:255,night?29:255);
        window.setStatusBarColor(background);window.setNavigationBarColor(background);
        View decor=window.getDecorView();
        int flags=decor.getSystemUiVisibility();
        int light=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(night?flags&~light:flags|light);
        decor.setBackgroundColor(background);
        View content=activity.findViewById(android.R.id.content);
        if(content instanceof ViewGroup){
            ViewGroup group=(ViewGroup)content;
            if(group.getChildCount()>0){
                View root=group.getChildAt(0);root.setBackgroundColor(background);
                if(root instanceof ViewGroup){ViewGroup inner=(ViewGroup)root;for(int i=0;i<inner.getChildCount();i++)
                    if(inner.getChildAt(i) instanceof android.webkit.WebView)inner.getChildAt(i).setBackgroundColor(background);}
            }
        }
        if(android.os.Build.VERSION.SDK_INT>=30){
            android.view.WindowInsetsController controller=window.getInsetsController();
            if(controller!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(night?0:mask,mask);}
        }
    }
    static Context dialogContext(Context context) {
        return new ContextThemeWrapper(context,dark(context)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
    }
    static void dialog(Context context, AlertDialog dialog) {
        if(!dark(context)||dialog.getWindow()==null)return;
        paint(dialog.getWindow().getDecorView());
    }
    private static void paint(View view) {
        int background=Color.rgb(24,31,40);
        if(view instanceof TextView){
            TextView text=(TextView)view;text.setTextColor(Color.rgb(225,231,240));text.setHintTextColor(Color.rgb(153,166,184));
        }
        if(view instanceof EditText){view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(76,104,140)));}
        else if(view instanceof ViewGroup && !(view instanceof Spinner))view.setBackgroundColor(background);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)paint(group.getChildAt(i));}
    }
}
