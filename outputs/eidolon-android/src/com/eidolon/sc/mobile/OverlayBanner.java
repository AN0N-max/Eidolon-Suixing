package com.eidolon.sc.mobile;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A compact chat-style heads-up card visible above other apps when allowed. */
final class OverlayBanner {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View active;
    private static WindowManager owner;
    private static int dp(Context c,float value){return (int)(value*c.getResources().getDisplayMetrics().density+.5f);}
    private static GradientDrawable rounded(Context c,int color,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(c,radius));return shape;
    }
    static boolean allowed(Context c){return Build.VERSION.SDK_INT>=26 && Settings.canDrawOverlays(c);}

    static boolean show(Context context,JSONObject message){
        if(!allowed(context))return false;android.os.PowerManager power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);android.app.KeyguardManager keyguard=(android.app.KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);if(power!=null&&!power.isInteractive()||keyguard!=null&&keyguard.isKeyguardLocked())return false;
        Context app=context.getApplicationContext();
        AtomicBoolean shown=new AtomicBoolean(false);
        CountDownLatch ready=new CountDownLatch(1);
        Runnable render=()->{
            try{
                dismiss();
                SharedPreferences prefs=app.getSharedPreferences("settings",0);
                DisplayMetrics display=app.getResources().getDisplayMetrics();
                int widthDp=Math.max(240,Math.min(420,prefs.getInt("reachWidth",340)));
                int width=Math.min(dp(app,widthDp),display.widthPixels-dp(app,20));
                LinearLayout card=new LinearLayout(app);card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(app,14),dp(app,9),dp(app,9),dp(app,12));
                card.setBackground(rounded(app,Color.WHITE,17));card.setElevation(dp(app,12));

                LinearLayout heading=new LinearLayout(app);heading.setGravity(Gravity.CENTER_VERTICAL);
                ImageView appIcon=new ImageView(app);appIcon.setImageResource(R.drawable.icon);
                appIcon.setBackground(rounded(app,Color.WHITE,4));appIcon.setClipToOutline(true);
                heading.addView(appIcon,new LinearLayout.LayoutParams(dp(app,16),dp(app,16)));
                TextView appName=new TextView(app);appName.setText("Eidolon 随行");appName.setTextSize(11);appName.setTextColor(Color.rgb(114,123,137));appName.setPadding(dp(app,6),0,0,0);
                heading.addView(appName,new LinearLayout.LayoutParams(0,-2,1));
                TextView when=new TextView(app);when.setText("现在");when.setTextSize(11);when.setTextColor(Color.rgb(145,151,160));heading.addView(when);
                card.addView(heading);

                LinearLayout content=new LinearLayout(app);content.setGravity(Gravity.CENTER_VERTICAL);content.setPadding(0,dp(app,7),0,0);
                ImageView face=new ImageView(app);Bitmap avatar=ReachOut.avatar(app,message.optString("character"));
                if(avatar!=null)face.setImageBitmap(avatar);else face.setImageResource(R.drawable.icon);
                face.setScaleType(ImageView.ScaleType.CENTER_CROP);face.setBackground(rounded(app,Color.rgb(244,245,248),11));face.setClipToOutline(true);
                content.addView(face,new LinearLayout.LayoutParams(dp(app,42),dp(app,42)));
                LinearLayout words=new LinearLayout(app);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(dp(app,10),0,0,0);
                TextView name=new TextView(app);name.setText(message.optString("name"));name.setTextColor(Color.rgb(30,35,43));name.setTypeface(null,Typeface.BOLD);name.setTextSize(14);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView preview=new TextView(app);preview.setText(ReachOut.preview(message));preview.setTextColor(Color.rgb(90,97,108));preview.setTextSize(13);preview.setMaxLines(2);preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
                words.addView(name);words.addView(preview);content.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                TextView close=new TextView(app);close.setText("×");close.setTextColor(Color.rgb(138,145,155));close.setTextSize(20);close.setGravity(Gravity.CENTER);close.setContentDescription("关闭消息弹窗");
                content.addView(close,new LinearLayout.LayoutParams(dp(app,34),dp(app,44)));
                close.setOnClickListener(v->dismiss());card.addView(content);

                Intent intent=new Intent(app,WebActivity.class).putExtra("reachCharacter",message.optString("character")).putExtra("reachSession",message.optString("session")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent open=PendingIntent.getActivity(app,message.optString("character").hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                card.setContentDescription(message.optString("name")+"发来消息："+ReachOut.preview(message));
                card.setOnClickListener(v->{if(!message.optString("session").isEmpty())try{open.send();}catch(PendingIntent.CanceledException ignored){}dismiss();});
                final float[] startY={0};
                card.setOnTouchListener((v,event)->{
                    if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startY[0]=event.getRawY();return true;}
                    if(event.getActionMasked()==MotionEvent.ACTION_UP){if(startY[0]-event.getRawY()>dp(app,32))dismiss();else v.performClick();return true;}
                    return true;
                });

                String side=prefs.getString("reachSide","center"),vertical=prefs.getString("reachVertical","top");
                WindowManager.LayoutParams lp=new WindowManager.LayoutParams(width,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);
                lp.gravity=Gravity.TOP|Gravity.LEFT;
                lp.x=side.equals("left")?dp(app,10):side.equals("right")?display.widthPixels-width-dp(app,10):(display.widthPixels-width)/2;
                lp.y=vertical.equals("bottom")?display.heightPixels-dp(app,150):vertical.equals("center")?(display.heightPixels-dp(app,110))/2:dp(app,8);
                WindowManager wm=(WindowManager)app.getSystemService(Context.WINDOW_SERVICE);wm.addView(card,lp);owner=wm;active=card;shown.set(true);ReachAlert.signal(app);
                MAIN.postDelayed(()->{if(active==card)dismiss();},(long)(ReachConfig.read(prefs).number("popupSeconds")*1000));
            }catch(Exception e){android.util.Log.w("EidolonOverlay","Unable to show message banner",e);dismiss();}
            finally{ready.countDown();}
        };
        if(Looper.myLooper()==Looper.getMainLooper())render.run();else{MAIN.post(render);try{ready.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        return shown.get();
    }
    static void dismiss(){if(active!=null&&owner!=null){try{owner.removeView(active);}catch(Exception ignored){}}active=null;owner=null;}
}
