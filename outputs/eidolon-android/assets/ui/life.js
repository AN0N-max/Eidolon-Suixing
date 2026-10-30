'use strict';
let lifeWho='',lifeLoadEpoch=0;
const lifeStatuses={planned:'计划中',in_progress:'正在进行（虚拟模拟）',completed:'已完成（虚拟经历）',missed:'错过，未记作经历',paused:'临时状态覆盖中',open:'尚未解决',awaiting_outcome:'等待明确结果',cancelled:'已取消',uncertain:'待核对'};
function lifeTime(at,zone){if(!at)return '时间未知';try{return new Intl.DateTimeFormat('zh-CN',{timeZone:zone||undefined,month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(new Date(at));}catch{return '时间未知';}}
async function lifeStatePage(){
 header('角色生活状态',true);const area=content();if(!state.characters.length)return;
 if(!state.characters.some(c=>c.id===lifeWho))lifeWho=state.characters[0].id;
 const select=choiceField(area,'查看角色',state.characters.map(c=>[c.id,c.name]),lifeWho),body=el('div','life-state-body');area.append(body);
 const load=async()=>{const who=lifeWho,token=++lifeLoadEpoch;body.replaceChildren(el('p','notice','正在读取本机生活档案…'));try{const data=await api('lifeGet',{character:who});if(token!==lifeLoadEpoch||page!=='lifeState'||!body.isConnected||who!==lifeWho)return;show(data);}catch(e){if(token===lifeLoadEpoch&&body.isConnected)body.replaceChildren(el('p','danger',e.message),btn('重试',load,'pill'));}};
 const show=data=>{
  body.replaceChildren();body.append(el('p','notice','这是角色的虚拟生活，不代表现实操作或原作剧情。已错过的计划不会自动变成经历；等待外部结果的阶段需要明确确认。'));
  const controls=el('section','card');body.append(controls);
  const check=(title,value)=>{const label=el('label','check'),input=el('input');input.type='checkbox';input.checked=value;label.append(input,document.createTextNode(title));controls.append(label);return input;};
  const enabled=check('启用生活连续性',data.enabled),evolution=check('允许缓慢兴趣与信心变化',data.evolution),eventOnly=check('主动消息只分享已有生活事件',data.eventOnlyReach);
  const save=async(operation,extra={})=>{try{const next=await api('lifeEdit',{character:lifeWho,revision:data.revision,operation,...extra});if(page==='lifeState'&&body.isConnected&&next.character===lifeWho)show(next);toast('生活记录已保存');}catch(e){toast(e.message);await load();}};
  controls.append(btn('保存生活设置',()=>save('settings',{enabled:enabled.checked,evolution:evolution.checked,eventOnlyReach:eventOnly.checked}),'pill wide'));
  if(!data.enabled){body.append(el('p','notice','生活状态已暂停。普通聊天、原有记忆、日程和通知继续使用原有功能。'));return;}
  const mood=data.emotion,p=data.profile;body.append(el('h2','','当前状态'),el('p','',`${mood.label} · 情绪强度 ${Math.round(Math.abs(mood.valence)*100)} · 模拟精力 ${Math.round(mood.energy)}/100`),el('p','muted',`最近观察：${lifeTime(data.observedAt,data.zone)} · ${data.zone||'沿用角色时区'}`));
  if(data.clockRollback)body.append(el('p','notice','检测到设备时间回退，已暂停推进以免倒置事件。确认设备时间正确后可重新校准；来自未来的已发生记录会标为待核对。'),btn('按当前设备时间重新校准',()=>save('calibrate'),'pill'));
  if(data.actual)body.append(el('p','',data.actual.name+' · '+lifeStatuses.in_progress));else body.append(el('p','muted','当前没有已开始的虚拟活动，不能仅凭计划断言正在做什么。'));
  body.append(el('h2','','长期目标'));
  if(!data.goals.length)body.append(el('p','notice','尚无长期目标。开启行程后，下一次日程生成可结合人设与近期经历制定目标；聊天也可自然形成目标，不为此额外请求模型。'));
  if(!data.goals.some(g=>g.status==='active'))body.append(btn('记录虚拟个人目标',()=>{modal.replaceChildren(el('h2','','记录角色的个人目标'));const title=field(modal,'目标名称',''),interest=choiceField(modal,'关联兴趣',[['practice','练习'],['creative','创作'],['outdoors','户外'],['social','交流'],['rest','休息']],'practice'),steps=[1,2,3].map(i=>field(modal,'阶段 '+i,'')),label=el('label','check'),external=el('input');external.type='checkbox';external.checked=true;label.append(external,document.createTextNode('最后阶段需要明确外部结果'));modal.append(label,el('p','notice','填写虚拟角色自己的目标，不代表执行现实操作。'),btn('保存目标',async()=>{await save('addGoal',{goal:{title:title.value,interest:interest.value,steps:steps.map((s,i)=>({title:s.value,external:i===2&&external.checked}))}});modal.close();},'pill wide'),btn('取消',()=>modal.close(),'pill wide'));modal.showModal();},'pill'));
  for(const g of data.goals){const card=el('section','card');card.append(el('strong','',g.title),el('p','muted',`${g.progress}/${g.steps.length} 阶段 · ${g.status==='active'?'进行中':g.status==='cancelled'?'已取消':'已完成'}`));g.steps.forEach((s,i)=>card.append(el('p','',`${i<g.progress?'✓':'○'} ${s.title}${s.external?'（外部结果需确认）':''}`)));if(g.status==='active')card.append(btn('取消目标',()=>save('cancel',{eventId:g.id}),'pill'));body.append(card);}
  body.append(el('h2','','事件与因果记录'));
  const events=[...data.events].sort((a,b)=>(b.at||b.start)-(a.at||a.start));if(!events.length)body.append(el('p','notice','尚无事件。已有日程会按实际观察和明确规则推进，不补造过去的活动。'));
  for(const event of events){const card=el('details','life-event');card.append(el('summary','',event.title+' · '+(lifeStatuses[event.status]||event.status)));const parent=data.events.find(e=>e.id===event.parent);card.append(el('p','muted',lifeTime(event.at||event.start,data.zone)+' · 来源 '+event.source));if(parent)card.append(el('p','',`前因：${parent.title}`));if(event.mentionedAt)card.append(el('p','muted','已分享：'+lifeTime(event.mentionedAt,data.zone)));if(event.resolvedBy)card.append(el('p','muted','后续活动已推进，此问题记为得到处理。'));
   if(event.status==='awaiting_outcome'||(event.kind==='issue'&&event.status==='open'))card.append(btn('记录已确认的结果',()=>confirmBox('确认虚拟事件结果？','只有你已确认该阶段完成时再记录。此操作不代表应用执行了现实世界操作。',()=>save('confirm',{eventId:event.id})),'pill'));
   card.append(btn('校正事件文字',()=>{modal.replaceChildren(el('h2','','校正生活记录'));const title=field(modal,'事件描述',event.title);modal.append(btn('保存校正',async()=>{await save('correct',{eventId:event.id,title:title.value});modal.close();},'pill wide'),btn('取消',()=>modal.close(),'pill wide'));modal.showModal();},'pill'));body.append(card);
  }
  const memory=el('details');memory.append(el('summary','','生活记忆与来源（'+data.memories.length+'）'));for(const m of data.memories)memory.append(el('p','',`${m.layer==='todo'?'待办':m.layer==='semantic'?'稳定变化':'情景'}：${m.text} · ${lifeStatuses[m.status]||m.status} · ${lifeTime(m.at,data.zone)}`));body.append(memory);
  const traits=el('details');traits.append(el('summary','','缓慢变化的人格状态'),el('p','',`模拟信心 ${p.confidence.toFixed(1)} · 基础人设仍来自角色提示词`));for(const log of data.evolutionLog)traits.append(el('p','muted',`${lifeTime(log.at,data.zone)} · ${log.before} → ${log.after} · ${log.reason}`));traits.append(btn('重置可变化特征',()=>save('resetTraits'),'pill'));body.append(traits,btn('刷新生活状态',load,'pill wide'),el('p','notice','关闭页面后不持续调用模型；重新打开或收到消息时按需推进。查看与校正不会请求模型。'));
 };
 select.onchange=()=>{lifeWho=select.value;load();};await load();
}
