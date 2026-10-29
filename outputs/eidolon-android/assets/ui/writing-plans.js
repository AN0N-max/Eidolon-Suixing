'use strict';
let writingPlanKind='all',writingPlanToken=0;
function writingPlansPage(){
 header('信件与日记安排',true);const area=content(),kind=choiceField(area,'安排类型',[['all','信件与日记'],['letter','只看信件'],['diary','只看日记']],writingPlanKind);kind.onchange=()=>{writingPlanKind=kind.value;render();};
 area.append(el('p','notice','凌晨03:00按档案时区检查安排，不在这时编造当天经历。信件按原计划提前写、到时投放；日记根据已保存的事件，于记录次日03:00后整理，最多补近三天。Android可能延期。'));
 area.append(btn('刷新安排',()=>render(),'pill'));const body=el('div','writing-plan-list');area.append(body);const token=++writingPlanToken;
 api('writingPlans').then(data=>{if(page!=='writingPlans'||token!==writingPlanToken)return;body.replaceChildren();const time=(at,zone=data.zone)=>at?new Date(at).toLocaleString('zh-CN',{timeZone:zone,hour12:false}):'尚未安排';body.append(el('p','muted',data.date+' · '+data.zone+' · 来信'+(data.letters?'已开启':'已关闭')+' / 日记'+(data.diary?'已开启':'已关闭')));
  body.append(el('p','muted',data.checkedDay===data.date&&data.checkedZone===data.zone?'本日检查：'+time(data.checkedAt):'本日03:00检查尚未完成；已保存的安排仍保留。'),el('p','muted','下次期望系统检查：'+time(data.nextCheck)));if(data.error)body.append(el('p','danger',data.error));
  const names={pending:'待处理',planned:'等待次日整理',retry:'等待重试',generating:'正在整理',ready:'已写好，等待投放',completed:'已保存／已投放',delivered:'已投放',none:'今日未安排来信',skipped:'已跳过',blocked:'关系暂不允许',failed:'尝试次数已用完',expired:'已过补记范围'},active=el('div'),past=el('details','reach-history');past.append(el('summary','','完成、未安排与暂停记录'));let count=0,total=0;
  for(const r of data.rows.filter(r=>writingPlanKind==='all'||r.kind===writingPlanKind)){total++;const c=state.characters.find(c=>c.id===r.character),card=el('section','card white writing-plan-card');card.append(el('strong','',(c?.name||r.character)+' · '+(r.kind==='letter'?'来信':'日记')),el('p','',r.date+' · '+(r.enabled?(names[r.state]||r.state):'自动功能已关闭')));
   if(r.kind==='letter'){if(r.writeAt)card.append(el('small','muted','预计写作／重试：'+time(r.writeAt,r.zone)));if(r.deliverAt)card.append(el('small','muted','计划送达：'+time(r.deliverAt,r.zone)));if(r.state==='none')card.append(el('p','notice','本日没有抽中来信或尚未满足间隔；不会为了填表额外请求。'));}
   else{card.append(el('small','muted','最早整理／重试：'+time(r.writeAt,r.zone)));if(r.state==='blocked')card.append(el('p','notice','沿用日记的关系与冷却条件，不展示正文或隐藏页。'));}
   card.append(el('small','muted','已登记尝试 '+r.attempts+' 次'+(r.kind==='diary'?' / 最多2次':' / 最多3次')));if(r.error)card.append(el('p','danger',r.error));if(r.enabled&&['pending','planned','retry','generating','ready'].includes(r.state)){active.append(card);count++;}else past.append(card);
  }if(!count)active.append(el('p','empty','目前没有待处理安排。没有事件的日记不会强行生成；未满足条件的角色也不会被填入计划。'));body.append(active);if(total>count)body.append(past);body.append(el('p','notice','此表只读已有计划、事件日期和尝试记录，不调用模型，不重新抽取来信，也不读取信件正文或日记隐藏页。'));
 }).catch(e=>{if(page==='writingPlans'&&token===writingPlanToken)body.replaceChildren(el('p','danger',e.message));});
}
