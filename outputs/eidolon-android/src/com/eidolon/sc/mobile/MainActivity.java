package com.eidolon.sc.mobile;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int INK=Color.rgb(24,43,69), MUTED=Color.rgb(101,113,129), PAPER=Color.rgb(247,248,251);
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    JSONArray characters; JSONObject current;
    ChatStore store; SharedPreferences prefs;
    LinearLayout root, body, messages; ScrollView scroll;
    EditText input; Button send; TextView status, pending;
    ModelClient client; boolean busy=false, destroyed=false, settings=false;
    VoicePlayer voicePlayer;
    int generation=0;
    String draft="";
    String conversation="";
    boolean fileBusy=false;
    static final int EXPORT_CHATS=71, IMPORT_CHATS=72;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER); getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        prefs=getSharedPreferences("settings",0); store=new ChatStore(this);
        voicePlayer=new VoicePlayer(this);
        try { characters=new JSONArray(asset("characters.json")); }
        catch(Exception e) { new AlertDialog.Builder(this).setMessage("角色资料无法加载，请重新安装应用").setPositiveButton("关闭",(d,w)->finish()).show(); return; }
        showRoster("");
        if(!prefs.getBoolean("notice",false)) new AlertDialog.Builder(this).setTitle("欢迎来到 Eidolon 随行")
            .setMessage("这是非官方 AI 角色扮演应用，回复由模型生成。\n\n聊天记录保存在手机内；发送消息时，当前角色资料及最近对话会发送给你配置的模型服务商，可能产生 API 费用。\n\n本开发版包含文字、礼物、剧情导入检索、自动记忆、聊天好感、任务与成就。辅助分析默认开启，每轮会额外调用模型，可在设置中关闭；完整语音通话和动态立绘仍在迁移中。")
            .setPositiveButton("知道了",(d,w)->prefs.edit().putBoolean("notice",true).apply()).show();
    }
    int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    GradientDrawable background(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    Button button(String name, View.OnClickListener action){Button b=new Button(this);b.setText(name);b.setTextColor(INK);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(action);return b;}
    EditText field(String hint,String value,boolean secret){EditText e=new EditText(this);e.setTextSize(15);e.setTextColor(INK);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setPadding(dp(12),dp(14),dp(12),dp(14));e.setBackground(background(PAPER,12));if(secret){e.setInputType(129);e.setTransformationMethod(PasswordTransformationMethod.getInstance());}return e;}
    void shell(String title,String subtitle){
        if(voicePlayer!=null)voicePlayer.stop();
        root=column();root.setBackgroundColor(PAPER);
        // Insets include gesture bars, display cutouts and the on-screen keyboard on Android 15.
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int bottom=insets.getSystemWindowInsetBottom();
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),bottom);
            return insets;
        });
        setContentView(root);
        LinearLayout header=column();header.setPadding(dp(20),dp(10),dp(20),dp(8));header.setBackgroundColor(Color.WHITE);
        TextView name=label(title,24,INK);name.setTypeface(null,Typeface.BOLD);header.addView(name);
        header.addView(label(subtitle,12,MUTED));root.addView(header);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    }
    void showRoster(String query){
        current=null;settings=false;shell("Eidolon 随行","283 事务所 · 与她说说话");
        LinearLayout controls=column();controls.setPadding(dp(16),dp(10),dp(16),dp(4));
        EditText search=field("搜索名字或组合",query,false);controls.addView(search);body.addView(controls);
        ScrollView listScroll=new ScrollView(this);LinearLayout list=column();listScroll.addView(list);body.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));
        fillRoster(list,query);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){fillRoster(list,s.toString());}public void afterTextChanged(Editable e){}});
        LinearLayout footer=new LinearLayout(this);footer.setPadding(dp(12),0,dp(12),dp(4));
        footer.addView(button("模型设置",v->showSettings()),new LinearLayout.LayoutParams(0,-2,1));
        footer.addView(button("关于与使用说明",v->about()),new LinearLayout.LayoutParams(0,-2,1));body.addView(footer);
    }
    void fillRoster(LinearLayout list,String query){
        list.removeAllViews();String needle=query.toLowerCase(Locale.ROOT).trim();
        for(int i=0;i<characters.length();i++){
            JSONObject c=characters.optJSONObject(i);
            if(!c.optString("search").toLowerCase(Locale.ROOT).contains(needle))continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(12),dp(18),dp(12));row.setBackgroundColor(Color.WHITE);
            ImageView avatar=new ImageView(this);try(InputStream stream=getAssets().open(c.optString("avatar"))){avatar.setImageDrawable(Drawable.createFromStream(stream,null));}catch(Exception ignored){}
            avatar.setContentDescription(c.optString("name"));row.addView(avatar,new LinearLayout.LayoutParams(dp(54),dp(54)));
            LinearLayout info=column();info.setPadding(dp(14),0,0,0);TextView name=label(c.optString("name"),17,INK);name.setTypeface(null,Typeface.BOLD);info.addView(name);
            List<ChatStore.Message> last=store.history(c.optString("id"),1);
            TextView preview=label(last.isEmpty()?c.optString("intro"):display(last.get(0).text),12,MUTED);preview.setMaxLines(2);preview.setEllipsize(TextUtils.TruncateAt.END);info.addView(preview);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            row.setOnClickListener(v->{current=c;conversation=store.latestConversation(c.optString("id"));draft="";showChat();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(1);list.addView(row,lp);
        }
        if(list.getChildCount()==0){TextView empty=label("没有找到这位偶像",16,MUTED);empty.setPadding(dp(24),dp(32),dp(24),0);list.addView(empty);}
    }
    void showChat(){
        settings=false;shell(current.optString("name"),"AI 角色 · 记录仅保存在这台手机");
        LinearLayout toolbar=new LinearLayout(this);
        toolbar.addView(button("‹ 角色",v->{if(!busy)showRoster("");else toast("请先停止当前回复");}),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button("设置",v->{if(!busy)showSettings();else toast("请先停止当前回复");}),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button("会话",v->conversationMenu()),new LinearLayout.LayoutParams(0,-2,1));body.addView(toolbar);
        scroll=new ScrollView(this);scroll.setFillViewport(true);messages=column();messages.setPadding(dp(16),dp(8),dp(16),dp(16));
        int tint=Color.parseColor(current.optString("color","#ffbad6"));messages.setBackgroundColor(Color.rgb((Color.red(tint)+255*9)/10,(Color.green(tint)+255*9)/10,(Color.blue(tint)+255*9)/10));
        scroll.addView(messages);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        List<ChatStore.Message> history=store.sessionHistory(conversation,200);
        if(history.isEmpty()){
            bubble("你好，制作人。\n"+current.optString("intro"),false);
            TextView hint=label("设置模型后发送第一条消息。此介绍是静态提示。",12,MUTED);messages.addView(hint);
        }else{messages.addView(label("显示最近 200 条 · 长按消息复制或播放角色语音",11,MUTED));for(ChatStore.Message m:history)bubble(m.text,m.role.equals("user"));}
        status=label("",12,MUTED);status.setPadding(dp(18),0,dp(18),0);body.addView(status);
        LinearLayout composer=new LinearLayout(this);composer.setPadding(dp(12),dp(6),dp(12),dp(10));composer.setGravity(Gravity.BOTTOM);composer.setBackgroundColor(Color.WHITE);
        input=field("给她发消息…",draft,false);input.setSingleLine(false);input.setMaxLines(5);input.setInputType(131073);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6000)});
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));send=button("发送",v->{if(busy)stop();else send();});composer.addView(send);body.addView(composer);bottom();
    }
    TextView bubble(String value,boolean own){
        LinearLayout row=new LinearLayout(this);row.setGravity(own?Gravity.RIGHT:Gravity.LEFT);
        TextView t=label(own?value:display(value),16,own?Color.WHITE:INK);t.setPadding(dp(16),dp(12),dp(16),dp(12));t.setLineSpacing(dp(4),1);t.setBackground(background(own?INK:Color.WHITE,20));t.setMaxWidth(getResources().getDisplayMetrics().widthPixels-dp(76));
        t.setOnLongClickListener(v->{new AlertDialog.Builder(this).setItems(own?new String[]{"复制"}:new String[]{"复制","播放角色语音","停止语音"},(d,which)->{
            if(which==0){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("消息",t.getText()));toast("已复制");}
            else if(which==1)playVoice(value);
            else{voicePlayer.stop();if(status!=null)status.setText("语音已停止");}
        }).show();return true;});
        row.addView(t);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);messages.addView(row,lp);return t;
    }
    static String display(String value){return value.replace("</ja>","\n").replace("</zh>","\n").replace("<ja>","").replace("<zh>","").trim();}
    void bottom(){if(scroll!=null)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    void send(){
        String user=input.getText().toString().trim();if(user.isEmpty())return;
        String key;try{key=Vault.read(this);}catch(Exception e){toast("密钥读取失败，请重新保存设置");return;}
        String address=prefs.getString("url",""),model=prefs.getString("model","");
        if(key.isEmpty()||address.isEmpty()||model.isEmpty()){draft=user;toast("先填写模型设置即可开始聊天");showSettings();return;}
        final JSONObject request;
        try{
            ModelClient.endpoint(address);current=CharacterPrompts.apply(CharacterPrompts.original(this,current.getString("id")),store.personal());
            String language=prefs.getBoolean("bilingual",true)?"回复使用 <ja>日语台词</ja><zh>对应的简体中文翻译</zh>，可以分成多个短段。":"只用简体中文自然地回复。";
            String system=current.optString("sheet")+"\n\n"+language+"\n你正在进行非官方角色扮演。本版本只有文字聊天，不具备通话、送礼、照片生成或剧情检索工具。不要声称执行了这些功能。不要编造原作经历。";
            String note=prefs.getString("profile","");if(!note.isEmpty())system+="\n制作人主动提供的个人资料：\n"+note;
            system+="\n角色补充资料：\n"+current.optString("facts","");
            request=ModelClient.request(model,system,store.sessionHistory(conversation,40),user);
        }catch(Exception e){toast("接口地址不正确，请在设置中检查");return;}
        String character=current.optString("id"),session=conversation;busy=true;draft=user;send.setText("停止");input.setText("");input.setEnabled(false);status.setText("正在连接模型…");bubble(user,true);pending=bubble("…",false);bottom();
        final int token=++generation;final ModelClient task=new ModelClient();client=task;
        worker.execute(()->{
            try{
                String reply=ReplyText.require(task.chat(address,key,request,text->runOnUiThread(()->{if(!destroyed&&generation==token){pending.setText(display(ReplyText.clean(text)));status.setText("正在回复…");bottom();}})));
                runOnUiThread(()->{
                    if(destroyed||generation!=token)return;
                    try{store.saveTurn(character,session,user,reply);draft="";busy=false;client=null;showChat();}
                    catch(Exception e){busy=false;client=null;showChat();input.setText(user);status.setText("保存失败，请检查手机剩余空间后重试");}
                });
            }catch(Exception e){
                final String message=e instanceof java.net.SocketTimeoutException?"连接超时，请检查网络后重试":e instanceof java.net.UnknownHostException?"无法连接此地址，请检查网络和接口地址":e instanceof javax.net.ssl.SSLException?"安全连接失败，请检查设备时间与服务商证书":e instanceof IOException?e.getMessage():"回复格式不兼容，请确认服务支持 Chat Completions 流式输出";
                runOnUiThread(()->{if(!destroyed&&generation==token){busy=false;client=null;showChat();status.setText(message==null?"连接失败，请重试":message);}});
            }
        });
    }
    void stop(){if(client!=null)client.cancel();generation++;busy=false;client=null;showChat();status.setText("已停止，本次未保存；可以修改后重新发送");}
    void clear(){if(busy){toast("请先停止当前回复");return;}new AlertDialog.Builder(this).setTitle("删除当前会话？").setMessage("只删除当前会话的全部消息，无法撤销。其他会话和角色不受影响。")
        .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{store.deleteConversation(conversation);conversation=store.latestConversation(current.optString("id"));draft="";showChat();}).show();}
    void conversationMenu(){
        if(busy){toast("请先停止当前回复");return;}
        new AlertDialog.Builder(this).setTitle("会话与角色").setItems(new String[]{"新建会话","打开历史会话","删除当前会话","角色资料"},(d,w)->{
            if(w==0){conversation=store.newConversation(current.optString("id"));draft="";showChat();}
            else if(w==1){List<ChatStore.Conversation> all=store.conversations(current.optString("id"));String[] names=new String[all.size()];for(int i=0;i<names.length;i++)names[i]=(all.get(i).id.equals(conversation)?"当前 · ":"")+all.get(i).title;new AlertDialog.Builder(this).setTitle("历史会话").setItems(names,(dialog,which)->{conversation=all.get(which).id;draft="";showChat();}).setNegativeButton("返回",null).show();}
            else if(w==2)clear();
            else{ScrollView sc=new ScrollView(this);TextView text=label(current.optString("sheet")+"\n\n"+current.optString("facts"),15,INK);text.setPadding(dp(16),dp(12),dp(16),dp(12));text.setTextIsSelectable(true);sc.addView(text);new AlertDialog.Builder(this).setTitle(current.optString("name")).setView(sc).setPositiveButton("关闭",null).show();}
        }).show();
    }
    void showSettings(){
        settings=true;shell("模型设置","填好这三项，就能直接在手机上聊天");
        ScrollView sc=new ScrollView(this);LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(24));sc.addView(form);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        form.addView(label("支持 OpenAI 兼容 Chat Completions 接口。\n这里填 API 服务的资料，不是聊天网站的网址。",14,MUTED));
        form.addView(label("1  接口地址",16,INK));EditText url=field("https://你的服务商地址/v1",prefs.getString("url",""),false);url.setInputType(17);form.addView(url);
        form.addView(label("2  API 密钥",16,INK));String saved="";try{saved=Vault.read(this);}catch(Exception e){toast("原密钥无法读取，请重新输入");}
        EditText key=field("粘贴服务商生成的 API Key",saved,true);form.addView(key);form.addView(label("密钥使用 Android 系统密钥库加密保存。",12,MUTED));
        form.addView(label("3  模型名称",16,INK));EditText model=field("填写服务商提供的模型 ID",prefs.getString("model",""),false);form.addView(model);
        LinearLayout presets=new LinearLayout(this);
        presets.addView(button("使用 DeepSeek",v->{url.setText("https://api.deepseek.com");model.setText("deepseek-flash");key.setText("");key.requestFocus();toast("已填入官方配置，请填写 DeepSeek API 密钥");}),new LinearLayout.LayoutParams(0,-2,1));
        presets.addView(button("使用 Gemini",v->{url.setText("https://generativelanguage.googleapis.com/v1beta/openai");model.setText("gemini-3.8-flash");key.setText("");key.requestFocus();toast("已填入官方配置，请填写 Gemini API 密钥");}),new LinearLayout.LayoutParams(0,-2,1));
        form.addView(presets);
        form.addView(label("可点上方按钮自动填入官方地址与示例模型，再粘贴对应密钥。模型名称可以修改。使用中转服务时，填写中转商提供的地址、模型和密钥。切换预设会清空输入框内的密钥，保存后生效。",12,MUTED));
        CheckBox bilingual=new CheckBox(this);bilingual.setText("日语回复 + 中文字幕");bilingual.setChecked(prefs.getBoolean("bilingual",true));form.addView(bilingual);
        form.addView(label("关于你（可选）",16,INK));EditText profile=field("例如：请叫我小优，我喜欢音乐",prefs.getString("profile",""),false);profile.setSingleLine(false);profile.setMaxLines(4);profile.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});form.addView(profile);
        form.addView(label("这段资料会随聊天发给模型，所有角色共用。聊天时会携带最近最多 20 轮上下文；这是上下文记忆，不是原项目的长期记忆检索。",12,MUTED));
        TextView feedback=label("",13,MUTED);form.addView(feedback);
        form.addView(button("保存设置",v->{
            String address=url.getText().toString().trim(),secret=key.getText().toString().trim(),name=model.getText().toString().trim();
            try{ModelClient.endpoint(address);}catch(Exception e){feedback.setText("请输入完整的 HTTPS API 地址，例如 https://服务商地址/v1");return;}
            if(secret.isEmpty()||name.isEmpty()){feedback.setText("请填写 API 密钥和模型名称");return;}
            if(secret.contains("\n")||secret.contains("\r")){feedback.setText("密钥中不能含换行");return;}
            try{Vault.save(this,secret);}catch(Exception e){feedback.setText("密钥保存失败，请检查手机安全设置");return;}
            boolean ok=prefs.edit().putString("url",address).putString("model",name).putString("profile",profile.getText().toString().trim()).putBoolean("bilingual",bilingual.isChecked()).commit();
            if(!ok){feedback.setText("设置保存失败，请检查手机空间");return;}
            toast("已保存，发送消息即可验证连接");if(current!=null)showChat();else showRoster("");
        }));
        form.addView(button("返回",v->{if(current!=null)showChat();else showRoster("");}));
        form.addView(button("聊天备份与恢复",v->backupMenu()));
        form.addView(button("角色语音设置",v->voiceSettings()));
        form.addView(label("本应用不会赠送模型额度。网络请求仅发送到你填写的 HTTPS 服务。卸载会删除本机记录和密钥，请谨慎操作。",12,MUTED));
    }
    void playVoice(String reply){
        if(busy){toast("请等回复完成后再播放");return;}
        if(current==null)return;
        String key;try{key=Vault.read(this,"fish");}catch(Exception e){toast("语音密钥无法读取，请重新保存");return;}
        if(key.isEmpty()){toast("先填写 Fish Audio 密钥即可播放角色语音");voiceSettings();return;}
        String voice=prefs.getString("voice-"+current.optString("id"),current.optString("voice_id"));
        voicePlayer.play(reply,voice,prefs.getString("fish-model","s2.1-pro-free"),key,message->{if(!destroyed){if(status!=null)status.setText(message);toast(message);}});
    }
    void voiceSettings(){
        LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(10));
        form.addView(label("Fish Audio 角色语音",18,INK));
        String secret="";try{secret=Vault.read(this,"fish");}catch(Exception ignored){}
        EditText key=field("粘贴 Fish Audio API 密钥",secret,true);form.addView(key);
        Spinner models=new Spinner(this);models.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,FishClient.MODELS));
        int selected=Arrays.asList(FishClient.MODELS).indexOf(prefs.getString("fish-model","s2.1-pro-free"));models.setSelection(Math.max(0,selected));form.addView(models);
        form.addView(label("默认 s2.1-pro-free；其他模型可能产生费用。每次点播放会调用语音服务，使用该服务的额度和规则。",12,MUTED));
        EditText voice=field("角色音色 ID",current==null?"":prefs.getString("voice-"+current.optString("id"),current.optString("voice_id")),false);
        if(current!=null){form.addView(label(current.optString("name")+"的音色 ID",14,INK));form.addView(voice);}
        form.addView(label("点击聊天气泡内的喇叭播放语音。使用仓库配置的音色；若音色已下架，可填写你有权使用的其他音色 ID。\n\n目前是单条回复播放，不是实时双向通话。",13,MUTED));
        TextView feedback=label("",12,MUTED);form.addView(feedback);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("语音设置").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String entered=key.getText().toString().trim(),voiceId=voice.getText().toString().trim();
            if(entered.contains("\n")||entered.contains("\r")){feedback.setText("密钥不能包含换行");return;}
            if(current!=null&&!voiceId.matches("[a-fA-F0-9]{32}")){feedback.setText("音色 ID 应为 32 位字母数字标识");return;}
            try{Vault.save(this,"fish",entered);}catch(Exception e){feedback.setText("保存密钥失败");return;}
            SharedPreferences.Editor edit=prefs.edit().putString("fish-model",(String)models.getSelectedItem());if(current!=null)edit.putString("voice-"+current.optString("id"),voiceId);
            if(!edit.commit()){feedback.setText("保存失败，请检查手机空间");return;}dialog.dismiss();toast(entered.isEmpty()?"已清除语音密钥":"语音设置已保存");
        }));dialog.show();ThemePrefs.dialog(this,dialog);
    }
    void backupMenu(){
        if(fileBusy){toast("正在处理备份，请稍候");return;}
        new AlertDialog.Builder(this).setTitle("本机数据备份").setMessage("备份会话、礼物、好感度、档案、记忆、任务成就与本机剧情，以密码加密。\n\n不含 API 密钥和模型设置；这是移动版专用格式，不能导入原网站。恢复会追加新会话，不覆盖聊天；新版备份会替换本机礼物、好感度、档案、记忆和成就；剧情按编号合并、优先保留译文。重复恢复会产生副本。\n\n请保存好密码，遗忘后无法恢复。")
            .setNegativeButton("取消",null).setPositiveButton("导出",(d,w)->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,"Eidolon-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(new Date())+".eidobak");startActivityForResult(i,EXPORT_CHATS);})
            .setNeutralButton("恢复",(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,IMPORT_CHATS);}).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if((request!=EXPORT_CHATS&&request!=IMPORT_CHATS)||result!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText password=field("备份密码（至少 8 个字符）","",true);form.addView(password);
        EditText repeat=field("再次输入密码","",true);if(request==EXPORT_CHATS)form.addView(repeat);
        TextView error=label("",12,MUTED);form.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(request==EXPORT_CHATS?"加密导出聊天":"解密并追加恢复").setView(form).setNegativeButton("取消",null).setPositiveButton("确定",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String pass=password.getText().toString();
            if(pass.length()<8){error.setText("密码至少 8 个字符");return;}
            if(request==EXPORT_CHATS&&!pass.equals(repeat.getText().toString())){error.setText("两次密码不一致");return;}
            password.setText("");repeat.setText("");dialog.dismiss();fileBusy=true;toast("正在处理，请稍候…");
            char[] secret=pass.toCharArray();worker.execute(()->{
                String message;
                try{
                    if(request==EXPORT_CHATS){
                        byte[] encrypted=BackupCrypto.encrypt(store.exportChats().toString().getBytes("UTF-8"),secret);
                        try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(encrypted);}
                        message="加密聊天备份已保存";
                    }else{
                        byte[] encrypted;
                        try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                            if(in==null)throw new IOException();byte[] buffer=new byte[8192];int n;
                            while((n=in.read(buffer))!=-1){if(out.size()+n>BackupCrypto.MAX_BYTES)throw new IOException("文件过大");out.write(buffer,0,n);}encrypted=out.toByteArray();
                        }
                        JSONObject restored=new JSONObject(new String(BackupCrypto.decrypt(encrypted,secret),"UTF-8"));
                        Set<String> known=new HashSet<>();for(int i=0;i<characters.length();i++)known.add(characters.optJSONObject(i).optString("id"));
                        int count=store.importChats(restored,known);message="已追加恢复 "+count+" 个会话";
                    }
                }catch(Exception e){message=request==EXPORT_CHATS?"备份失败，请检查存储空间与文件位置；未完成的文件不能用于恢复":"恢复失败：密码错误、文件损坏或格式不兼容；现有会话未改动";}
                finally{Arrays.fill(secret,'\0');}
                final String resultMessage=message;
                runOnUiThread(()->{fileBusy=false;if(!destroyed)new AlertDialog.Builder(this).setMessage(resultMessage).setPositiveButton("知道了",null).show();});
            });
        }));dialog.show();
    }
    void about(){new AlertDialog.Builder(this).setTitle("Eidolon 随行 · 1.14")
        .setMessage("吸收 0.9 功能，沿用 0.8.8 原版的包名和签名。角色资料来自 Sallyn0225/eidolon-sc，日历与写作玩法参考 Eidolon-Hibi。当前包含 "+characters.length()+" 位角色。\n\n已包含：本机聊天、日中双语与显示开关、剧情检索、记忆、好感与礼物、任务成就、主动消息横幅、日历、信箱、事件日记、角色行程与睡眠状态、约定与兑现。\n\n语音沿用 0.9 的流式播放、暂停续播、本地保存管理与夜间模式。自动来信、日记可在设置中开启；后台运行时机由手机系统安排。聊天、信件和日记随加密备份保存；录音文件仍由已保存语音页面管理。\n\n已包含本地每日主动搭话计划、聊天未读、洗澡延迟及可选关键关系剧情。完整双向语音通话与动态立绘尚未接入。\n\n原作角色归 Bandai Namco Entertainment Inc. 所有；这是非官方粉丝作品。代码按 AGPL-3.0-or-later 发布。\n\n来源：https://github.com/Sallyn0225/eidolon-sc\nhttps://github.com/x499806631-commits/Eidolon-Hibi")
        .setPositiveButton("关闭",null).setNeutralButton("开源许可证",(d,w)->{try{ScrollView s=new ScrollView(this);TextView t=label(asset("LICENSE")+"\n\n"+asset("THIRD-PARTY-NOTICES.txt")+"\n\n"+asset("ANDROIDX-LICENSE.txt"),12,INK);t.setPadding(dp(16),dp(16),dp(16),dp(16));s.addView(t);new AlertDialog.Builder(this).setTitle("AGPL-3.0").setView(s).setPositiveButton("关闭",null).show();}catch(Exception ignored){}}).show();}
    String asset(String path)throws Exception{try(InputStream in=getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}}
    @Override public void onBackPressed(){if(busy){new AlertDialog.Builder(this).setMessage("停止这次回复并返回？").setNegativeButton("继续等待",null).setPositiveButton("停止",(d,w)->{stop();showRoster("");}).show();}else if(settings){if(current!=null)showChat();else showRoster("");}else if(current!=null){showRoster("");}else super.onBackPressed();}
    @Override protected void onStop(){if(voicePlayer!=null)voicePlayer.stop();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;if(client!=null)client.cancel();if(voicePlayer!=null)voicePlayer.close();worker.shutdownNow();if(store!=null)store.close();super.onDestroy();}
}

