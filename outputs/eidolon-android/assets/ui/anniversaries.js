'use strict';
let anniversaryCharacter='';
function anniversaryEntries(year,month){const entries=[];for(const c of state.characters){const start=state.meetings?.[c.id]?.date;if(!start)continue;const [y,m,d]=start.split('-').map(Number),first=Date.UTC(y,m-1,d),annDay=m===2&&d===29&&new Date(Date.UTC(year,2,0)).getUTCDate()===28?28:d;for(let day=1;day<=new Date(year,month,0).getDate();day++){const days=Math.floor((Date.UTC(year,month-1,day)-first)/86400000)+1,years=year-y;if(days<=0)continue;const wholeYear=years>0&&month===m&&day===annDay;if(days%100!==0&&!wholeYear)continue;entries.push({day,character:c.id,title:'✦ '+c.name+' · '+(days%100===0?'认识第 '+days+' 天':'认识 '+years+' 周年')});}}return entries;}
function anniversariesPage(){header('认识纪念日',true);const area=content('page'),selected=state.characters.find(c=>c.id===anniversaryCharacter)||state.characters.find(c=>c.id===state.current)||state.characters[0];anniversaryCharacter=selected.id;
 area.append(el('p','notice','按每位角色的认识日期计算：当天算第 1 天，每满 100 天和每个周年进入日历。2 月 29 日的周年在非闰年按 2 月 28 日处理。'));
 const who=choiceField(area,'角色',state.characters.map(c=>[c.id,c.name]),selected.id);who.onchange=()=>{anniversaryCharacter=who.value;render();};const row=state.meetings?.[selected.id];
 area.append(el('p','',row?(row.days>0?'已认识 '+row.days+' 天':'日期晚于今天，请核对')+' · '+(row.source==='automatic'?'首次互动自动记录':'手动核对日期'):selected.lastActivity>0?'认识日期待核对：旧聊天没有可靠的首次时间，请填写实际认识日期。':'还没有认识日期。首次聊天或送礼时会自动记录。'));
 const date=field(area,'认识日期',row?.date||'','date');date.min='1900-01-01';date.max=state.promiseOptions?.today||'';
 area.append(btn('保存认识日期',async()=>{if(!date.value){toast('请选择认识日期');return;}state=await api('anniversarySet',{character:selected.id,date:date.value});render();toast('认识日期已保存，日历将按新日期计算');},'pill primary wide'));
 if(row)area.append(btn('清除认识日期',()=>confirmBox('清除这位角色的认识日期？','会停止计算她的百日与周年纪念日，聊天与关系记录保留。以后可重新填写。',async()=>{state=await api('anniversarySet',{character:selected.id,op:'clear'});render();}),'pill danger wide'));
 const entries=anniversaryEntries(new Date().getFullYear(),new Date().getMonth()+1).filter(e=>e.character===selected.id);if(entries.length){area.append(el('h3','','手机当前月份的纪念日'));entries.forEach(e=>area.append(el('p','',e.day+' 日 · '+e.title)));}
 area.append(el('p','notice','日期保存在本机并随加密备份保存；删除聊天不会清除已保存的认识日期。自动记录按个人档案时区换算，手动日期按你核对的月日保留。日期只影响纪念日提示，不加减好感。'));
}
