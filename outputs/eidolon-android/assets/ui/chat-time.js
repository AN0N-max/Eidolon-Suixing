'use strict';
function cleanReplyText(value){
 const stamp='\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T\\s]+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*(?:Z|[+-]\\d{2}:?\\d{2}))?',time='(?:'+stamp+'|发送时间未知（旧记录）|时间未知|未知)';
 return String(value||'').replace(new RegExp('(?:【|\\[)\\s*(?:消息发送时间|发送时间)\\s*[:：]\\s*'+time+'(?:\\s*[;；][^】\\]]{0,180})?\\s*(?:】|\\])','gi'),'').replace(new RegExp('^[\\t ]*(?:\\*\\*)?(?:消息发送时间|发送时间|当前回复生成时间)\\s*[:：]\\s*'+time+'(?:[\\t ]*[;；][^\\r\\n]{0,180})?(?:\\*\\*)?[\\t ]*(?:\\r?\\n|$)','gmi'),'').replace(/<(ja|zh)>\s*<\/\1>/gs,'').trim();
}
function chatClockParts(at,zone){const rows=new Intl.DateTimeFormat('en-CA',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(at)),p={};for(const r of rows)p[r.type]=r.value;p.dayKey=p.year+'-'+p.month+'-'+p.day;return p;}
function chatTimeLabel(at,zone,now=Date.now()){
 if(!Number.isFinite(at)||at<=0)return '较早的聊天 · 时间未记录';
 const p=chatClockParts(at,zone),today=chatClockParts(now,zone),clock=p.hour+':'+p.minute;
 if(p.dayKey===today.dayKey)return clock;
 if(Date.parse(today.dayKey+'T00:00:00Z')-Date.parse(p.dayKey+'T00:00:00Z')===86400000)return '昨天 '+clock;
 return (p.year===today.year?'':p.year+'年')+Number(p.month)+'月'+Number(p.day)+'日 '+clock;
}
function chatMessageTime(m){const at=Number(m?.sentAt);if(Number.isFinite(at)&&at>0)return at;if(m?.role==='gift')try{const saved=Number(JSON.parse(m.text).at);if(Number.isFinite(saved)&&saved>0)return saved;}catch(_){}return 0;}
function chatShowTime(m,previous,zone){if(!previous)return true;const at=chatMessageTime(m),before=chatMessageTime(previous);if(!at)return !!before;if(!before)return true;return at<before||at-before>=300000||chatClockParts(at,zone).dayKey!==chatClockParts(before,zone).dayKey;}
function chatDisplayZone(){const wanted=state?.personal?.producer?.timeZone||Intl.DateTimeFormat().resolvedOptions().timeZone||'Asia/Shanghai';try{chatClockParts(Date.now(),wanted);return wanted;}catch(_){return 'Asia/Shanghai';}}
function appendChatHistory(parent,messages){
 const zone=chatDisplayZone();let previous=null;for(const m of messages){if(chatShowTime(m,previous,zone)){const at=chatMessageTime(m),n=el('time','chat-time',chatTimeLabel(at,zone));n.dataset.chatAt=String(at);n.dataset.timeZone=zone;if(at){n.dateTime=new Date(at).toISOString();n.title=new Date(at).toLocaleString('zh-CN',{timeZone:zone,hour12:false})+' · '+zone;}else n.classList.add('chat-time-unknown');parent.append(n);}parent.append(m.role==='gift'?giftEvent(m.text):bubble(m.text,m.role==='user',false,m.id));previous=m;}
}
function updateChatTimeLabels(){for(const n of document.querySelectorAll('.chat-time[data-chat-at]'))n.textContent=chatTimeLabel(Number(n.dataset.chatAt),n.dataset.timeZone);}
window.addEventListener('focus',updateChatTimeLabels);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)updateChatTimeLabels();});
