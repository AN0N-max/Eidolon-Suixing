'use strict';
let readPending=false,readTimer=0;
const seenMessages=new Set();
function unreadBadge(count){const n=el('span','unread-count',count>99?'99+':String(count));n.setAttribute('aria-label',count+'条未读消息');return n;}
function writingLink(title,route,kind){const n=btn(title,()=>navigate(route),'pill');n.dataset.writingKind=kind;return n;}
function writingUnread(kind){if(state?.writingUnread)return state.writingUnread[kind]||0;return (state?.writings||[]).filter(e=>e.kind===kind&&!e.locked&&(!e.read||e.hiddenUnread)).length;}
function updateUnreadBadges(){
 document.querySelectorAll('[data-unread-character]').forEach(n=>{n.replaceChildren();const count=state?.unread?.characters?.[n.dataset.unreadCharacter]||0;if(count)n.append(unreadBadge(count));n.closest('.roster-row')?.classList.toggle('has-unread',count>0);});
 document.querySelectorAll('[data-writing-kind]').forEach(n=>{const k=n.dataset.writingKind,count=k==='all'?writingUnread('letter')+writingUnread('diary'):writingUnread(k);n.classList.toggle('has-writing-unread',count>0);});
 const navSettings=document.querySelector('[data-settings-unread]');if(navSettings)navSettings.classList.toggle('has-unread',!!(writingUnread('letter')||writingUnread('diary')));
 updateLatestControl();const chat=document.querySelector('[data-chat-unread]');if(chat){chat.querySelector('.unread-count')?.remove();const count=state?.unread?.total||0;if(count)chat.append(unreadBadge(count));}
}
function queueVisibleRead(){clearTimeout(readTimer);readTimer=setTimeout(markVisibleRead,180);}
let inboxRefreshFlight=null;
function queueInboxRefresh(){if(!inboxRefreshFlight)inboxRefreshFlight=new Promise(resolve=>setTimeout(resolve,60)).then(refreshInbox).finally(()=>{inboxRefreshFlight=null;});return inboxRefreshFlight;}
async function refreshInbox(){
 if(!state)return;updateChatTimeLabels();const target=page,before=target==='chat'?JSON.stringify(state.messages||[]):'',previousUnread=state.unread?.total||0;
 const route=['chat','roster','mailbox','diary'].includes(target)?target:'badges',next=await api('snapshot',{page:route});
 if(page!==target)return;state=next;if(page!=='chat'&&(state.unread?.total||0)>previousUnread)toast('有 '+state.unread.total+' 条未读消息，可在底部对话查看');
 if(page==='roster'||page==='chat'&&before!==JSON.stringify(state.messages||[]))render();else if(['mailbox','diary'].includes(page))writingRefresh();else{updateUnreadBadges();queueVisibleRead();}
 if(page==='desktopBadge')refreshDesktopBadge();writingValidateReader();
}
async function markVisibleRead(){
 if(readPending||page!=='chat'||document.hidden||!document.hasFocus()||modal.open)return;
 const list=document.querySelector('#messages');if(!list)return;const viewport=list.getBoundingClientRect(),session=state.session,ids=[];
 for(const n of list.querySelectorAll('[data-message-id]')){const id=Number(n.dataset.messageId),key=session+'/'+id,rect=n.getBoundingClientRect();if(!seenMessages.has(key)&&Math.min(rect.bottom,viewport.bottom)-Math.max(rect.top,viewport.top)>=Math.min(24,rect.height))ids.push(id);}
 if(!ids.length)return;readPending=true;
 try{const value=await api('chatRead',{session,ids:ids.slice(0,200)});if(value.accepted){ids.slice(0,200).forEach(id=>seenMessages.add(session+'/'+id));state.unread=value.unread;updateUnreadBadges();}}
 catch(_){}finally{readPending=false;}
}
document.addEventListener('visibilitychange',()=>{if(!document.hidden)queueVisibleRead();});
window.addEventListener('focus',queueVisibleRead);
document.querySelector('#modal').addEventListener('close',queueVisibleRead);
function reachPlanTime(stamp,zone){try{return new Date(stamp).toLocaleString('zh-CN',{timeZone:zone,hour12:false});}catch(_){return new Date(stamp).toLocaleString('zh-CN');}}
function reachPlanPage(){
 let reachPlanBusy=false;
 header('今日主动搭话计划',true);const area=content();area.append(el('p','notice','优先给不同角色机会，每人同时保留一条计划；每日上限不要求排满。临时忙碌会在有效期内延后，不直接作废，也不会为延期重新请求模型。'));
 const actions=el('div','actions'),results=el('div','reach-plan-list');
 const refresh=btn('刷新计划',()=>load('reachPlanCheck'),'pill'),replan=btn('重新安排未执行计划',()=>confirmBox('重新安排今天？','重新计算尚未执行的安排，不调用模型。已完成、失败记录和请求次数保留；正在生成时请等待完成。',()=>load('reachReplan')),'pill');
 actions.append(refresh,replan);area.append(actions,results);
 async function load(operation){
  if(reachPlanBusy)return;reachPlanBusy=true;results.querySelector('.reach-plan-error')?.remove();refresh.disabled=true;replan.disabled=true;results.setAttribute('aria-busy','true');
  const progress=el('p','notice reach-plan-progress',operation==='reachReplan'?'正在重新安排，请稍候…':'正在完成本地计划检查…');results.prepend(progress);
  try{const data=await api(operation);if(page!=='reachPlans'||!results.isConnected)return;display(data);}
  catch(e){if(page==='reachPlans'&&results.isConnected){progress.remove();results.prepend(el('p','danger reach-plan-error',e.message||'本地计划检查失败，请重试'));}}
  finally{reachPlanBusy=false;refresh.disabled=false;replan.disabled=false;results.removeAttribute('aria-busy');progress.remove();}
 }
 function display(data){results.replaceChildren();results.append(el('p','muted',data.date+' · '+data.zone+(data.enabled?'':' · 已关闭')));const active=data.tasks.filter(t=>['PENDING','RUNNING'].includes(t.status)),past=data.tasks.filter(t=>!['PENDING','RUNNING'].includes(t.status)),roles=[...new Set(active.map(t=>t.character))];const summary=el('div','plan-summary');summary.append(el('strong','',roles.length+' 位角色'),el('span','',active.length+' 项当前安排'),el('span','',past.filter(t=>t.status==='COMPLETED').length+' 项已完成'));results.append(summary);
  const check=el('div','reach-check-status');if(data.checkedAt)check.append(el('p','muted','最近本地检查：'+reachPlanTime(data.checkedAt,data.zone)));check.append(el('p',data.checkError?'danger':'muted',data.checkError||data.checkResult||'尚未完成本地检查'));if(data.wakeError)check.append(el('p','danger',data.wakeError));
  if(data.nextWake)check.append(el('p',data.wakeOverdue?'danger':'muted',data.wakeOverdue?'上次约定检查时间已过，需刷新核对：'+reachPlanTime(data.nextWake,data.zone):'下次期望系统检查：'+reachPlanTime(data.nextWake,data.zone)));results.append(check);
  if(!active.length)results.append(el('p','notice','当前没有待执行安排。请展开各角色说明查看本次未安排的原因；刷新会完成一次本地检查，不调用模型。'));
  const names={PENDING:'待执行',RUNNING:'生成中',COMPLETED:'已完成',SKIPPED:'已跳过',FAILED:'失败'};
  const renderTask=(task,parent,compact=false)=>{const c=state.characters.find(c=>c.id===task.character),card=el('section','card white reach-task'),head=el('div','reach-role-head');if(c)head.append(pic(c));head.append(el('strong','',c?.name||task.character));card.append(head,el('p','reach-time-row',new Date(task.due).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})+' · '+(names[task.status]||task.status)),el('small','', '有效至 '+new Date(task.expires).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'})+' · 请求 '+task.attempts+' 次'));if(task.error)card.append(el('p','notice',task.error));if(task.status==='FAILED'&&data.enabled)card.append(btn('手动重试',()=>confirmBox('重新请求这条搭话？','上次可能已经计费；重试会再次调用API并占用今日请求预算，每任务最多三次。',async()=>{await api('reachTaskRetry',{id:task.id});render();}),'pill'));parent.append(card);};
  for(const id of roles){const rows=active.filter(t=>t.character===id);for(const row of rows)renderTask(row,results);}
  const assessments=el('details','reach-assessments');assessments.append(el('summary','','查看各角色当前筛选说明'));const why=el('div','reach-assessment-list');for(const row of data.assessments||[]){const c=state.characters.find(c=>c.id===row.character);why.append(el('p','',(c?.name||row.character)+'：'+row.reason));}assessments.append(why);results.append(assessments);
  const history=el('details','reach-history');history.append(el('summary','','执行与失效记录（'+past.length+'）'+(past.some(t=>t.status==='FAILED')?' · 有失败待查看':'')));for(const row of past)renderTask(row,history,true);results.append(history);
 }
 load('reachPlanCheck');
}

function attachOlderLoader(list){
 if((state.messages?.length||0)<200)return;const session=state.session,load=btn('加载更早的消息',async()=>{load.disabled=true;try{const before=state.messages[0]?.id,data=await api('chatOlder',{session,before});if(!list.isConnected||state.session!==session)return;const oldHeight=list.scrollHeight;state.messages=[...data.messages,...state.messages];rememberChatWindow();const history=list.querySelector('.chat-history');history.replaceChildren();appendChatHistory(history,state.messages);if(!data.hasMore)load.remove();list.scrollTop+=list.scrollHeight-oldHeight;queueVisibleRead();}finally{load.disabled=false;}},'pill wide older-messages');list.prepend(load);
}

let desktopBadgeView=null;
function desktopBadgePage(){
 header('桌面未读角标',true);const area=content(),status=el('section','card');desktopBadgeView={area,status};area.append(status);
 area.append(el('p','notice','有新的未读聊天时同步桌面图标；按实际可见消息确认已读，全部读完后清除。Vivo／iQOO会额外提交兼容更新。数字、红点和位置由手机桌面决定，应用不能强制指定。'));
 area.append(el('p','notice','Vivo／iQOO：先在应用通知总设置的提醒方式中勾选桌面角标。允许通知与允许角标是两处开关。当前未读为0时不显示角标属正常情况。'),btn('测试桌面角标',async()=>{await api('desktopBadgeTest');toast('本地测试通知已发出，请返回桌面查看；不写聊天、不调用模型');},'pill wide'),btn('用独立类别测试',async()=>{await api('desktopBadgeTest',{independent:true});toast('独立类别测试通知已发出');},'pill wide'),btn('清除角标测试通知',async()=>{await api('desktopBadgeTestClear');toast('测试通知已清除，真实未读不变');},'pill wide'));
 area.append(btn('修复旧消息类别角标',async()=>{await api('desktopBadgeRepair');await refreshDesktopBadge();toast('已保留提醒偏好并修复旧类别，真实未读不变');},'pill primary wide'));
 area.append(btn('按当前未读数同步',()=>refreshDesktopBadge(true),'pill primary wide'),btn('打开通知与角标设置',()=>api('desktopBadgeSettings'),'pill wide'));
 area.append(el('p','notice','在系统设置中允许Eidolon通知，并开启应用图标角标；若提供“数字／圆点”选项，可选择数字。也检查消息通知类别是否允许显示角标。同步不生成消息、不调用模型，不会将未读清零。'));
 refreshDesktopBadge();
}
async function refreshDesktopBadge(force=false){
 const view=desktopBadgeView;if(page!=='desktopBadge'||!view?.area.isConnected)return;
 const label=el('p','muted',force?'正在同步桌面角标…':'正在读取角标状态…');view.status.replaceChildren(label);
 try{const data=await api(force?'desktopBadgeSync':'desktopBadgeStatus');if(page!=='desktopBadge'||desktopBadgeView!==view||!view.area.isConnected)return;
  view.status.replaceChildren(el('strong','','本机未读：'+(data.unread||0)+' 条'),el('p',data.notifications?'muted':'danger',data.notifications?'系统通知已允许':'系统通知未允许，请到系统设置开启'),el('p','muted','桌面角标总开关：系统未提供可靠查询，请以系统设置为准'),el('p','muted',data.channelBadge?'Android接口报告：未读通知类别允许角标':'Android接口报告：未读通知类别未允许角标；这不代表Vivo／iQOO桌面角标总开关已关闭'));
  view.status.append(el('p','muted',data.vivo?'Vivo／iQOO兼容模式：'+(data.status||'等待同步'):'使用Android标准通知角标'));
  if(data.vivo){view.status.append(el('p','muted','桌面包：'+(data.homePackage||'系统未提供')));if(data.vivoBadgePermissionKnown===true)view.status.append(el('p',data.vivoBadgePermission?'muted':'danger',data.vivoBadgePermission?'Vivo角标读写权限：系统已授予':'Vivo角标读写权限：系统未授予（与通知总开关不同）'));else view.status.append(el('p','muted','系统未公开Vivo角标权限状态，不能据此判断桌面开关。'));}
  if(Array.isArray(data.activeMessageNotifications)){view.status.append(el('p','muted','当前系统消息通知：'+data.activeMessageNotifications.length+' 条'));for(const n of data.activeMessageNotifications)view.status.append(el('small','badge-channel-detail',n.channel+' · 计数 '+n.number+' · '+(n.enabled?'通道启用':'通道禁用')+' · '+(n.badge?'接口允许角标':'接口未允许角标')));}
  if(data.unread===0)view.status.append(el('p','notice','当前没有未读消息，不显示角标属于正常情况。'));
  if(data.channelEnabled===false)view.status.append(el('p','danger','未读通知类别已禁用，请检查系统通知类别。'));
  if(data.vivo&&data.lastAt)view.status.append(el('small','', '最近提交：'+new Date(data.lastAt).toLocaleString('zh-CN')+(data.submittedCount>=0?' · '+data.submittedCount+' 条':'')));
 }catch(e){if(page==='desktopBadge'&&desktopBadgeView===view)view.status.replaceChildren(el('p','danger',e.message));}
}
