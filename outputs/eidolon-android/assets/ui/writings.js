'use strict';
let writingPending=false,writingCharacter='',writingReader=null,writingReaderEpoch=0,writingSecretPending=0;
document.querySelector('#modal').addEventListener('close',()=>{if(!document.querySelector('#modal').open){writingReader=null;writingReaderEpoch++;}});
const writingFilters={letter:{unread:false,month:'',query:''},diary:{unread:false,month:'',query:''}};
function writingRefresh(){const at=document.querySelector('#app>.content')?.scrollTop||0,target=page;render();requestAnimationFrame(()=>{if(page===target){const area=document.querySelector('#app>.content');if(area)area.scrollTop=at;}});}
function mailbox(){writingList('letter');}
function diary(){writingList('diary');}
function writingList(kind){
 header(kind==='letter'?'她的来信':'她的日记',true);
 const area=content('page writing-page'),all=state.writings||[];
 const who=choiceField(area,'选择角色',[['','全部角色'],...state.characters.map(c=>[c.id,c.name])],writingCharacter);
 who.onchange=()=>{writingCharacter=who.value;render();};
 const filter=writingFilters[kind],records=all.filter(e=>e.kind===kind&&(!writingCharacter||e.character===writingCharacter));
 const months=[...new Set(records.map(e=>e.date.slice(0,7)))].sort().reverse();if(filter.month&&!months.includes(filter.month))filter.month='';
 const month=choiceField(area,'记录月份',[['','全部月份'],...months.map(m=>[m,m.replace('-',' 年 ')+' 月'])],filter.month);month.onchange=()=>{filter.month=month.value;render();};
 const unread=el('label','check'),unreadInput=el('input');unreadInput.type='checkbox';unreadInput.checked=filter.unread;unreadInput.onchange=()=>{filter.unread=unreadInput.checked;render();};unread.append(unreadInput,document.createTextNode('只看未读'));area.append(unread);
 const search=field(area,'搜索可见内容',filter.query);search.placeholder='输入内容或日期';
 const summary=el('p','muted'),list=el('div','writing-results');
 const actions=el('div','actions');actions.append(btn(kind==='letter'?'请她写封信':'请她记下今天',()=>writingCompose(kind),'pill primary'),btn('自动写作设置',()=>navigate('writingSettings'),'pill'));area.append(actions);
 area.append(summary,list);
 const fill=()=>{list.replaceChildren();const query=filter.query.trim().toLowerCase(),entries=records.filter(e=>(!filter.unread||!e.read||e.hiddenUnread)&&(!filter.month||e.date.startsWith(filter.month))&&(!query||(e.date+' '+(state.characters.find(c=>c.id===e.character)?.name||'')+' '+e.text.replace(/<[^>]*>/g,' ')).toLowerCase().includes(query)));
 summary.textContent='共 '+records.length+' 篇 · 未读 '+records.filter(e=>!e.locked&&(!e.read||e.hiddenUnread)).length+' 篇 · 当前显示 '+entries.length+' 篇';
 if(!entries.length)list.append(el('div','empty',records.length?'没有符合筛选条件的记录。':kind==='letter'?'信箱暂时空着。\n聊过的角色可以写信，也可以开启自动来信。':'还没有日记。\n开启后，喜欢的礼物、关系升档和特别日子会成为日记的依据。'));
 for(const row of entries){
  const c=state.characters.find(c=>c.id===row.character);if(!c)continue;
  const card=btn('',()=>writingOpen(row),'writing-card');card.style.setProperty('--writing-color',/^#[a-f\d]{6}$/i.test(c.color||'')?c.color:'#a8bdd6');
  const title=el('div','writing-card-title');title.append(pic(c),el('strong','',c.name),el('small','',row.date));if((!row.read||row.hiddenUnread)&&!row.locked)title.append(el('span','unread-dot'));
  const raw=row.text.replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim();
  card.append(title,el('p','',row.locked?'日记本暂时合上了。关系回到信任后可以看开头。':raw.slice(0,90)+(raw.length>90?'…':'')));
  if(row.excerpt)card.append(el('small','','目前可读开头 · 亲近后可读全文'));if(row.hiddenAvailable)card.append(el('small','',row.hiddenUnread?'藏起的一页 · 未读':'藏起的一页 · 已读'));else if(row.hasHiddenPage)card.append(el('small','','她还藏着一页 · 亲密时开放'));list.append(card);
 }
 };search.oninput=()=>{filter.query=search.value;fill();};fill();
 area.append(el('p','notice',kind==='letter'?'来信保存在手机内，未读状态会随加密备份保存。':'日记来自真实事件及对话。信任时开放开头，亲近后开放正文，亲密时可另行翻开隐藏页；冷却期合上日记。关闭好感时只看正文。隐藏页不参与列表搜索。'));
}
function writingCompose(kind){
 writingReader=null;writingReaderEpoch++;
 modal.replaceChildren(el('h2','',kind==='letter'?'请她写封信':'记下今天'));
 const selected=writingCharacter||state.current||state.characters.find(c=>c.lastActivity>0)?.id||state.characters[0].id;
 const who=choiceField(modal,'角色',state.characters.map(c=>[c.id,c.name]),selected);
 modal.append(el('p','notice','会调用已配置的后台模型，未单独配置时使用聊天模型。每位角色每天同类记录最多一篇，每天最多三封来信。'),btn('开始写',async()=>{
  if(writingPending)return;writingPending=true;modal.replaceChildren(el('h2','','她正在写…'),el('p','muted','完成后会保存到本机。'),btn('停止',async()=>{await api('cancel');modal.close();},'pill wide'));
  try{state=await api('writingGenerate',{kind,character:who.value});writingCharacter=who.value;modal.close();page=kind==='letter'?'mailbox':'diary';render();toast('已经写好并保存');}
  catch(e){modal.close();toast(e.message);}finally{writingPending=false;}
 },'pill primary wide'),btn('取消',()=>modal.close(),'pill wide'));
 modal.showModal();
}
async function writingOpen(row){
 if(row.locked){toast('目前的关系暂时不能翻阅这篇日记');return;}
 const token=++writingReaderEpoch,target=page,next=await api('writingRead',{kind:row.kind,character:row.character,date:row.date});if(token!==writingReaderEpoch||page!==target)return;state=next;
 row=(state.writings||[]).find(e=>e.kind===row.kind&&e.character===row.character&&e.date===row.date);writingRefresh();
 if(!row||row.locked){toast('记录已更新，目前无法翻阅');return;}
 writingRenderReader(row);modal.showModal();
}
function writingRenderReader(row){
 const c=state.characters.find(c=>c.id===row.character);writingReader={kind:row.kind,character:row.character,date:row.date,secret:false};modal.replaceChildren(el('h2','',c.name+' · '+row.date));
 const text=el('article','writing-reader');messageText(text,row.text);modal.append(text);
 if(row.excerpt)modal.append(el('p','notice','目前只能看到开头。关系进入亲近后可读正文。'));
 if(row.hiddenAvailable)modal.append(btn('翻开隐藏页',()=>writingSecretOpen(row),'pill wide'));
 else if(row.hasHiddenPage)modal.append(el('p','notice','她还藏着一页没说出口的心事。关系达到亲密后可以翻开。'));
 modal.append(btn('标为未读并关闭',async()=>{state=await api('writingRead',{kind:row.kind,character:row.character,date:row.date,read:false});modal.close();writingRefresh();toast('已标为未读');},'pill wide'),btn('关闭',()=>modal.close(),'pill wide'));
}
async function writingSecretOpen(row){
 const token=++writingReaderEpoch,target=page;writingSecretPending=token;let result;try{result=await api('diarySecret',{character:row.character,date:row.date});}finally{if(writingSecretPending===token)writingSecretPending=0;}if(token!==writingReaderEpoch||page!==target||!modal.open)return;state=result.snapshot;
 const latest=(state.writings||[]).find(e=>e.kind==='diary'&&e.character===row.character&&e.date===row.date);
 if(!latest?.hiddenAvailable){writingValidateReader();toast('目前不能翻开这一页');return;}
 writingRefresh();writingReader={kind:'diary',character:row.character,date:row.date,secret:true};const c=state.characters.find(c=>c.id===row.character);
 modal.replaceChildren(el('h2','',c.name+' · 藏起的一页'),el('p','muted',row.date));const text=el('article','writing-reader secret-reader');messageText(text,result.text);modal.append(text);
 modal.append(btn('返回日记',()=>writingOpen(latest),'pill wide'),btn('关闭',()=>modal.close(),'pill wide'));
}
function writingValidateReader(){
 if(!writingReader||!modal.open)return;if(!['mailbox','diary'].includes(page)){modal.close();writingReader=null;writingReaderEpoch++;return;}
 const row=(state.writings||[]).find(e=>e.kind===writingReader.kind&&e.character===writingReader.character&&e.date===writingReader.date);
 if(!row||row.locked||(writingReader.secret||writingSecretPending===writingReaderEpoch)&&!row.hiddenAvailable){modal.replaceChildren(el('h2','','日记的这一页暂时合上了'),el('p','notice','关系或记录已更新，请重新打开日记查看当前可读的内容。'),btn('关闭',()=>modal.close(),'pill wide'));writingReader=null;writingReaderEpoch++;return;}
 if(!writingReader.secret)writingRenderReader(row);
}

function writingSettings(){
 header('她的日子 · 写作设置',true);const area=content(),options=state.writingOptions||{};area.append(btn('查看信件与日记安排',()=>navigate('writingPlans'),'pill wide'),el('p','notice','每日按档案时区在凌晨03:00检查本机安排。日记最早于记录次日03:00后整理，不预写未发生的事情；系统可能延后执行。查看安排不调用模型。'));
 const check=(title,checked)=>{const label=el('label','check'),input=el('input');input.type='checkbox';input.checked=checked;label.append(input,document.createTextNode(title));area.append(label);return input;};
 const letters=check('自动来信',options.letters),notes=check('自动日记',options.diary);
 area.append(el('p','notice','自动来信面向最近 30 天有过对话、未结束当前会话且不在关系冷却期的角色。开启好感时需达到信任。普通来信相隔至少五天，关系越亲近越容易收到；每天全体最多三封（包括手动信及已暂存的信）。'));
 area.append(el('p','notice','自动日记记录喜欢的礼物、关系进入亲近/亲密、约定与纪念日，以及聊天中值得记下的一刻。通常次日起写，仅补最近三天；每位角色每天的自动日记最多尝试两次，失败后至少隔一小时。手动记下今天不使用此自动次数。'));
 area.append(el('p','notice','同时开启自动日记与自动记忆时，原有后台记忆分析可额外提取一条重要时刻，不新增独立模型请求。需要有用户原话依据，回复正式送达后才保存，不加减好感。隐藏页与正文一次生成，格式不完整时不保存。'));
 area.append(el('p','notice','自动来信会先在本机决定是否来信及时间，写好后暂存，不提前显示或计为未读。亲近后可在前一晚准备制作人生日信、零点送达；当天才开启时改为 08:00–11:00。角色生日及节日信在 19:00–22:00，普通信在 09:00–21:00（按档案时区）。关闭好感时也可有生日与节日来信。'));
 area.append(el('p','notice','生成和送达分别保存为本机任务；已写好的信送达不调用模型、不需要联网。自动来信写作失败每隔至少五分钟重试，总共最多三次；超过原定送达一小时仍未写好就放弃，不补写错过的来信。Android 省电、待机或强制停止可能使任务延后，不能保证零点准时。'));
 area.append(el('p','notice','自动写作使用你配置的模型 API，缺译文时可能额外补译。关闭自动来信会暂停生成及投放，已暂存的信仍保留；重新开启后可能收到此前已写好的信。手动请她写信仍立即保存，已经提前写好的当天来信不会重复生成。加密备份包含来信计划及暂存正文，旧版 APK 无法导入新版备份。'));
 if(options.letterError)area.append(el('p','danger','最近一次来信任务未完成：'+options.letterError));
 if(options.error)area.append(el('p','danger','最近一次自动写作未完成：'+options.error));
 area.append(btn('保存设置',async()=>{state=await api('writingSettings',{letters:letters.checked,diary:notes.checked});toast('已保存');render();},'pill primary wide'));
}
