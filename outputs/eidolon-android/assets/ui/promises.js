'use strict';
let promiseCharacter='',promiseFilter='active';
const promiseStates={active:'进行中',completed:'已兑现',cancelled:'已取消'};
function promiseDates(p){return p.from===p.due?p.from:p.from+' 至 '+p.due;}
function promiseCost(p,op){
 if(!state.features?.affinity)return '好感评估已关闭，本次不改变好感。';
 const today=state.promiseOptions.today,advance=today<p.from;
 const n=op==='complete'?(p.complainedDay?1:2):op==='move'?(advance&&!p.changes?0:-1):(advance?-1:-2);
 return '本次好感变化：'+(n>0?'+':'')+n+'（达到上限或处于冷却时，以实际变化为准）。';
}
function promisesPage(){
 header('约定与兑现',true);const area=content('page promise-page'),options=state.promiseOptions||{};
 const controls=el('div','actions');controls.append(btn('记一件约定',()=>promiseEdit(null),'pill primary'),btn('约定设置',()=>navigate('promiseSettings'),'pill'));area.append(controls);
 if(!options.enabled)area.append(el('p','notice','约定功能尚未开启。已有记录会保留；开启后可新增、修改，并在聊天中自然提起。'));
 const who=choiceField(area,'角色',[['','全部角色'],...state.characters.map(c=>[c.id,c.name])],promiseCharacter);who.onchange=()=>{promiseCharacter=who.value;render();};
 const filter=choiceField(area,'查看范围',[['active','进行中'],['closed','已结束'],['all','全部']],promiseFilter);filter.onchange=()=>{promiseFilter=filter.value;render();};
 const rows=(state.personal.promises||[]).filter(p=>(!promiseCharacter||p.character===promiseCharacter)&&(promiseFilter==='all'||(promiseFilter==='active'?p.state==='active':p.state!=='active'))).sort((a,b)=>a.due.localeCompare(b.due)||a.createdAt-b.createdAt);
 if(!rows.length)area.append(el('p','empty','这里还没有约定。可以手动记录，也可以在设置中开启聊天自动识别。'));
 for(const p of rows){
  const c=state.characters.find(c=>c.id===p.character);if(!c)continue;const card=el('section','card white promise-card');
  card.append(el('small','muted',c.name+' · '+(promiseStates[p.state]||p.state)),el('h2','',p.task),el('p','promise-date',promiseDates(p)));
  if(p.state==='active'&&p.due<options.today)card.append(el('small','danger','日期已过，可改期、取消或确认完成。'));
  if(p.complainedDay)card.append(el('small','muted','她曾在 '+p.complainedDay+' 提起未兑现的事。'));
  if(p.automatic){const evidence=el('details');evidence.append(el('summary','','查看记录依据'),el('p','muted',p.evidence));card.append(evidence);}
  const actions=el('div','actions');
  if(p.state==='active'){
   actions.append(btn('已兑现',()=>promiseConfirm(p,'complete'),'pill primary'),btn('改期',()=>promiseEdit(p),'pill'),btn('取消约定',()=>promiseConfirm(p,'cancel'),'pill danger'));
   for(const b of actions.children)b.disabled=!options.enabled;
  }else actions.append(btn('删除记录',()=>promiseConfirm(p,'delete'),'pill'));
  card.append(actions);area.append(card);
 }
 area.append(el('p','notice','约定、日期与状态保存在手机内，随加密备份保存。模型可能误判，自动记录后可以来这里核对。'));
}
function promiseEdit(row){
 if(!state.promiseOptions?.enabled){toast('请先在约定设置中开启功能');return;}
 modal.replaceChildren(el('h2','',row?'为约定改期':'记一件约定'));
 const who=row?null:choiceField(modal,'角色',state.characters.map(c=>[c.id,c.name]),promiseCharacter||state.current||state.characters[0].id);
 const task=row?null:field(modal,'约定做什么','','area');if(task)task.maxLength=160;else modal.append(el('p','',row.task));
 const first=field(modal,'开始日期',row?.from||state.promiseOptions.today,'date'),last=field(modal,'截止日期',row?.due||state.promiseOptions.today,'date');
 first.min=state.promiseOptions.today;last.min=state.promiseOptions.today;
 first.onchange=()=>{if(last.value<first.value)last.value=first.value;};
 const gift=row?null:choiceField(modal,'关联礼物（可选）',[['','不关联'],...(state.gifts||[]).filter(g=>g.id!=='reset-card').map(g=>[g.id,g.name])],'');
 modal.append(el('p','notice',row?promiseCost(row,'move')+' 提前首次改期不扣好感；当天、逾期或再次改期扣 1。':'关联礼物后，在约定开始前一天至截止后三天内送出该礼物，会自动标记兑现。新增记录不调用模型。'));
 modal.append(btn('保存',async()=>{
  const args={op:row?'move':'create',character:row?row.character:who.value,from:first.value,due:last.value};
  if(row)args.promiseId=row.id;else{args.task=task.value.trim();args.giftId=gift.value;}
  state=await api('promiseAction',args);modal.close();render();toast('约定已保存');
 },'pill primary wide'),btn('返回',()=>modal.close(),'pill wide'));modal.showModal();
}
function promiseConfirm(p,op){
 const names={complete:'确认这件事已经兑现？',cancel:'取消这件约定？',delete:'删除这条已结束记录？'};
 confirmBox(names[op],p.task+'。'+(op==='delete'?'只删除记录，已有好感变化不会撤销。':promiseCost(p,op)),async()=>{
  state=await api('promiseAction',{op,character:p.character,promiseId:p.id});render();toast(op==='complete'?'已记为兑现':op==='cancel'?'已取消':'记录已删除');
 });
}
function promiseSettings(){
 header('约定设置',true);const area=content(),options=state.promiseOptions||{};
 const check=(title,value)=>{const row=el('label','check'),input=el('input');input.type='checkbox';input.checked=!!value;row.append(input,document.createTextNode(title));area.append(row);return input;};
 const enabled=check('开启约定与兑现',options.enabled),automatic=check('从聊天中自动识别新约定、改期与取消',options.automatic);
 area.append(el('p','notice','自动识别复用后台分析请求；关闭自动记忆时，开启此项会增加一次后台请求。只记录有明确日期、你亲口答应的事；日期不明确时请手动记录。'));
 area.append(el('p','notice','约定期间及截止后三天，当你明确说已完成时，每轮最多检查三项是否兑现，复用判断模型请求；判断模型失败不影响聊天。也可随时手动确认。默认不开启，开启后可能增加接口消耗。'));
 area.append(el('p','notice','明确约好来陪她聊天，会在约定日内下一轮成功聊天后自动记为兑现。她可在聊天或已开启的主动搭话中提醒。只有回复确实提起原约定时才记录提醒；首次表达逾期失落扣 3，兑现加 2，已表达失落后兑现加 1。提前取消扣 1，其余取消扣 2。关闭好感评估或处于关系冷却时均不加减。'));
 area.append(el('p','notice','约定不会另建准点闹钟或强制唤醒手机；主动消息仍遵守原有时间、睡眠与次数限制。删除历史不撤回已发生的关系变化。'));
 area.append(btn('保存设置',async()=>{state=await api('promiseSettings',{enabled:enabled.checked,automatic:automatic.checked});toast('已保存');render();},'pill primary wide'));
}
function calendarPromises(year,month){
 const prefix=year+'-'+String(month).padStart(2,'0')+'-',entries=[];
 for(const p of state.personal.promises||[]){if(p.state==='cancelled')continue;
  const c=state.characters.find(c=>c.id===p.character);if(!c)continue;
  const start=prefix+'01',end=prefix+String(new Date(year,month,0).getDate());if(p.due<start||p.from>end)continue;
  const shown=p.from<start?start:p.from;entries.push({day:Number(shown.slice(-2)),title:(p.state==='completed'?'✓ ':'📌 ')+c.name+' · '+p.task,note:promiseDates(p)+' · '+promiseStates[p.state],promise:p});
 }return entries;
}
