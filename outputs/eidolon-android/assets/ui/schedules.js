'use strict';
let scheduleSelected='',scheduleStatusBusy=false,schedulePageEpoch=0,scheduleDisplayTimer=0;
function chatIdentity(c){
 const box=el('div','chat-identity');box.append(el('h1','',c.name));
 const current=state.currentSchedule||{},status=btn(current.text||'',()=>{scheduleSelected=c.id;navigate('schedule');},'activity-status');
 status.id='schedule-status';status.hidden=!current.enabled;status.setAttribute('aria-label','查看'+c.name+'的今日行程');box.append(status);return box;
}
async function refreshScheduleStatus(){
 if(page!=='chat'||scheduleStatusBusy||document.hidden)return;const who=state?.current;if(!who)return;scheduleStatusBusy=true;
 try{const current=await api('scheduleStatus');if(page!=='chat'||state.current!==who||current.character!==who)return;const button=document.querySelector('#schedule-status');if(button){button.hidden=!current.enabled;button.textContent=current.text;}}
 catch(_){}finally{scheduleStatusBusy=false;}
}
setInterval(refreshScheduleStatus,60000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden){refreshScheduleStatus();if(page==='schedule'&&state)render();}});
function schedulePage(){
 const epoch=++schedulePageEpoch;clearTimeout(scheduleDisplayTimer);
 header('她今天的安排',true);const area=content('page schedule-page');
 if(!scheduleSelected||!state.characters.some(c=>c.id===scheduleSelected))scheduleSelected=state.current||state.characters.find(c=>c.lastActivity>0)?.id||state.characters[0].id;
 const who=choiceField(area,'角色',state.characters.map(c=>[c.id,c.name]),scheduleSelected);who.onchange=()=>{scheduleSelected=who.value;render();};
 area.append(btn('行程与作息设置',()=>navigate('scheduleSettings'),'pill wide'));
 const details=el('section','schedule-details');details.append(el('p','muted','正在读取今日安排…'));area.append(details);
 api('scheduleGet',{character:scheduleSelected}).then(data=>{if(page!=='schedule'||scheduleSelected!==data.character||epoch!==schedulePageEpoch||!details.isConnected)return;details.replaceChildren();
  if(data.nextDisplayAt){const delay=Math.max(100,Math.min(86400100,data.nextDisplayAt-(data.now||Date.now())+100));scheduleDisplayTimer=setTimeout(()=>{if(page==='schedule'&&epoch===schedulePageEpoch)render();},delay);}
  details.append(el('p','muted',data.date+' · '+data.timeZone));if(data.showingPrevious)details.append(el('p','notice','03:00 前继续显示上一日安排，03:00 后切换到当天行程。'));
  if(data.habit||data.bathHabit){const habits=el('details','sleep-habit');habits.append(el('summary','','她的作息习惯'));if(data.habit)habits.append(el('p','muted',data.habit));if(data.bathHabit)habits.append(el('p','muted',data.bathHabit));details.append(habits);}
  if(!data.enabled)details.append(el('p','notice','行程尚未开启。开启后，她会按自己的安排和作息聊天。'));
  else details.append(el('div','schedule-current',data.text));
  if(data.restEnabled&&data.sleepWindow){const night=data.sleepWindow;details.append(el('p','schedule-rest','本夜预计入睡 '+night.startText+' · 起床 '+night.endText),el('p','muted',night.estimated?'昨日日程缺失，本夜暂按这名角色已有日程估计。':'本夜作息来源：'+night.sourceDate+' 的日程。'));}
  if(data.plan){
   if(data.restEnabled)details.append(el('p','schedule-rest',(data.showingPrevious?'上一日安排：起床 ':'今日日程：起床 ')+data.plan.wake+' · 入睡 '+data.plan.sleep+(data.plan.sleep<'12:00'?'（次日）':'')));
   if(data.restEnabled&&data.plan.bath){const bath=data.plan.bath,[bh,bm]=bath.at.split(':').map(Number),start=bh*60+bm,end=start+bath.minutes,clock=n=>String(Math.floor((n%1440)/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0'),next=start<720;details.append(el('p','schedule-rest',(bath.kind==='shower'?'淋浴':'泡澡')+' '+bath.at+(next?'（次日）':'')+'–'+clock(end)+(next||end>=1440?'（次日）':'')+' · 约 '+bath.minutes+' 分钟'));}
   for(const slot of data.plan.slots){const card=el('div','schedule-slot');card.append(el('time','',slot.start+'–'+slot.end),el('strong','',slot.activity));if(slot.place)card.append(el('small','',slot.place));details.append(card);}
   if(data.plan.twists?.length)details.append(el('p','notice',(data.showingPrevious?'上一日的小变化：':'今天的小变化：')+data.plan.twists.join('；')));
   details.append(el('p','notice',data.showingPrevious?'这是上一日的大致安排，保留显示至03:00。':'这是她今天的大致安排，可能临时变化。没有安排的时间可以自由活动。'));
  }else{
   details.append(el('p','muted',data.error?'这一天暂时没排好：'+data.error:data.showingPrevious?'上一日没有已保存的行程，03:00 后切换新一天。':'今天还没有生成行程。'));
   const generate=btn(data.error?'重新安排行程':'生成今天行程',()=>scheduleCompose(data.character),'pill primary wide');generate.disabled=!data.enabled||!!data.showingPrevious;details.append(generate);
  }
 }).catch(e=>{if(page==='schedule'&&epoch===schedulePageEpoch&&details.isConnected)details.replaceChildren(el('p','danger',e.message));});
}
function scheduleCompose(who){
 const c=state.characters.find(c=>c.id===who);modal.replaceChildren(el('h2','','请'+c.name+'安排今天'),el('p','notice','会调用后台模型，未单独配置时使用聊天模型。仅在输出被长度上限截断时自动扩大额度重试一次，可能增加用量。今天成功生成后会保存在本机，不反复重写。'));
 modal.append(btn('开始安排',async()=>{
  if(writingPending)return;writingPending=true;modal.replaceChildren(el('h2','','她正在安排今天…'),el('p','muted','完成后会保存到手机。'),btn('停止',async()=>{await api('cancel');modal.close();},'pill wide'));
  try{state=await api('scheduleGenerate',{character:who});modal.close();page='schedule';scheduleSelected=who;render();toast('今天的行程已保存');}
  catch(e){modal.close();toast(e.message);if(page==='schedule')render();}finally{writingPending=false;}
 },'pill primary wide'),btn('取消',()=>modal.close(),'pill wide'));modal.showModal();
}
function scheduleSettings(){
 header('行程与作息设置',true);const area=content(),options=state.scheduleOptions||{};
 const check=(title,checked)=>{const row=el('label','check'),input=el('input');input.type='checkbox';input.checked=checked;row.append(input,document.createTextNode(title));area.append(row);return input;};
 const role=choiceField(area,'角色独立时区',state.characters.map(c=>[c.id,c.name]),state.current||state.characters[0].id),zone=field(area,'角色时区（留空沿用现有档案）','','text'),zoneNote=el('p','muted');area.append(zoneNote);let zoneEpoch=0;
 const loadZone=async()=>{const epoch=++zoneEpoch;zone.disabled=true;try{const d=await api('roleTimeZoneGet',{character:role.value});if(epoch!==zoneEpoch||!zone.isConnected)return;zone.value=d.override||'';zoneNote.textContent='当前有效时区：'+d.effective+'。已保存日程按所选时区的当地钟表时间解释，不把时区变更当作已发生的经历。';}catch(e){toast(e.message);}finally{if(epoch===zoneEpoch)zone.disabled=false;}};role.onchange=loadZone;loadZone();area.append(btn('保存角色时区',async()=>{const d=await api('roleTimeZoneSave',{character:role.value,zone:zone.value});zoneNote.textContent='当前有效时区：'+d.effective;toast('角色时区已保存');},'pill wide'));
 const temp=el('details');temp.append(el('summary','','临时实际状态（手动记录）'));const activity=field(temp,'当前活动（留空清除覆盖）','','text'),duration=field(temp,'有效分钟数',30,'number');duration.min=1;duration.max=1440;temp.append(btn('保存临时状态',async()=>{await api('temporalOverrideSave',{character:role.value,name:activity.value,minutes:Number(duration.value)});toast('临时状态已保存；到期自动失效');},'pill'),el('p','notice','这是你明确记录的实际状态，优先于聊天中的计划参考；到期后只重新查询当前计划，不补造已完成经历。不会单凭模型台词创建生活事件，也不改写已有待投放回答。'));area.append(temp);
 const enabled=check('开启角色行程',options.enabled),rest=check('开启睡觉、起床与洗澡状态',options.rest!==false);
 const limit=choiceField(area,'每天自动安排角色数量上限',[['1','1 位'],['3','3 位'],['5','5 位'],['10','10 位'],['28','28 位']],String(options.limit||3));
 area.append(el('p','notice','按角色有效时区（默认沿用档案），每天 03:00 后为最近七天聊过的角色安排，优先最近聊天的角色。每位每天成功生成一次，手机后台运行可能延迟。行程页在03:00前保留上一日安排，03:00后切换当天，不删除历史记录。'));
 area.append(el('p','notice','开启作息后，睡着时仍会回复，语气可以困倦；连续聊醒后不再反复提睡觉。每天安排行程时会本地随机生成睡前泡澡/淋浴时段，正在洗澡时聊天状态会提示；在洗澡期间收到的消息会等到预计结束后再投放回复（需同时开启延迟回复）。她睡觉或洗澡时会暂停主动搭话。洗澡仅作日常安排参考，不描写过程或身体。行程关闭时，作息效果也会关闭。'));
 area.append(el('p','notice','每天生成行程会调用后台模型，日常读取状态不调用模型。同一角色当天自动失败后不会反复尝试，可以去行程页手动重试。'));
 area.append(btn('保存设置',async()=>{state=await api('scheduleSettings',{enabled:enabled.checked,rest:rest.checked,limit:Number(limit.value)});toast('已保存');render();},'pill primary wide'));
}
