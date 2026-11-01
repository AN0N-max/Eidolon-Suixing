'use strict';
let callLayer=null,callTimer=null,callShown=false,callBusy=false,callLast={state:'idle'};
const callNames={idle:'未通话',connecting:'正在打开识别服务',listening:'正在听你说话',recognizing:'正在识别',thinking:'她正在组织回答',speaking:'她正在说话',committing:'正在保存本轮通话',muted:'麦克风已静音',failed:'本轮已暂停',ended:'通话已结束',ringing:'有来电',missed:'未接来电',rejected:'已拒接',cancelled:'已取消'};
async function callDial(){
 try{const live=await api('callStatus');if(!['idle','ended','missed','rejected','cancelled'].includes(live.state)){callShown=true;callRender(live);return;}}catch(e){}
 modal.replaceChildren(el('h2','','应用内语音通话'),el('p','notice','自动轮流说话：你说完后识别，她回答时暂停聆听。可手动打断，不是自然全双工。单次最多30分钟或60轮，费用由你的接口提供商计费。'),el('p','notice','音频发到你选定的系统识别服务、阿里云百炼或HTTPS识别接口；文字发到现有聊天模型，角色语音发到 Fish Audio 合成。默认不保存原始录音。识别文字保存在本机聊天，但本版不会据此自动新增长期事实或约定。'),el('p','notice','同角色有待回复文字时须先处理；通话期间该角色暂停文字发送及主动搭话，其他角色仍可使用。'),btn('开始拨打',async()=>{try{await api('callStart');modal.close();callShown=true;callPoll();}catch(e){toast(e.message);}},'pill primary wide'),btn('识别服务与主动来电设置',callSettings,'pill wide'),btn('通话记录',callHistoryPage,'pill wide'),btn('取消',()=>modal.close(),'pill wide'));modal.showModal();
}
async function callSettings(){try{const c=await api('callConfig');modal.replaceChildren(el('h2','','电话设置'));
 const provider=el('select','field');provider.setAttribute('aria-label','语音识别提供商');for(const [id,name] of [['system','手机系统识别'],['bailian','阿里云百炼（流式识别）'],['http','兼容HTTPS转录接口']]){const o=el('option','',name);o.value=id;provider.append(o);}provider.value=c.provider||(c.url?'http':'system');modal.append(el('p','','语音识别提供商'),provider);
 const detail=el('div');modal.append(detail);let url,model,key,region,bailianModel;
 const refresh=()=>{detail.replaceChildren();url=model=key=region=bailianModel=null;
  if(provider.value==='system'){detail.append(el('p','notice','使用手机已安装的系统识别服务；是否联网及音频接收方由该服务决定。'));return;}
  if(provider.value==='bailian'){
   detail.append(el('p','notice','16k单声道声音通过加密WebSocket直接发往百炼，显示临时文字，并由服务句尾结果结束一轮。需开通所填实时识别模型；不是自然全双工。识别密钥独立保存，不沿用聊天密钥。'));
   region=el('select','field');region.setAttribute('aria-label','百炼密钥地域');for(const [id,name] of [['beijing','中国内地 · 北京'],['singapore','国际 · 新加坡']]){const o=el('option','',name);o.value=id;region.append(o);}region.value=c.region||'beijing';detail.append(el('p','','百炼密钥地域'),region);
   bailianModel=field(detail,'百炼实时识别模型',c.bailianModel||'qwen-audio-3.1-asr-flash-streaming');key=field(detail,c.hasBailianKey?'百炼识别密钥（已保存，留空保留）':'百炼识别密钥','','password');
   detail.append(el('p','notice','默认模型沿用上游。若账号未开通，请在百炼控制台开通，或填写账号可用且支持DashScope recognition协议的模型。地域须与密钥匹配；不自动切换地域或重试。采音时会持续上传声音，静音超时/挂断即停止，可能按服务实际时长计费。'));
  }else{detail.append(el('p','notice','配置兼容 multipart /audio/transcriptions 的完整HTTPS地址。整轮声音在本机静音检测后上传，不是流式识别。'));url=field(detail,'识别HTTPS完整地址',c.url||'');model=field(detail,'识别模型',c.model||'');key=field(detail,c.hasKey?'HTTPS识别密钥（已保存，留空保留）':'HTTPS识别密钥','','password');}
 };provider.onchange=refresh;refresh();
 const incoming=el('input');incoming.type='checkbox';incoming.checked=!!c.incoming;const label=el('label','check');label.append(incoming,document.createTextNode('允许角色主动来电（默认关闭，每日最多一次）'));modal.append(label,el('p','notice','来电机会沿用主动搭话的角色范围、关系、作息与免打扰。接听前不采音、不生成回答；拒接不扣关系。系统通知被关闭或后台未获执行机会时，不承诺准时来电。'));
 modal.append(btn('保存电话设置',async()=>{try{await api('callConfig',{provider:provider.value,url:url?.value||'',model:model?.value||'',key:key?.value||'',region:region?.value||'beijing',bailianModel:bailianModel?.value||'',incoming:incoming.checked});if(key)key.value='';toast('电话设置已保存');modal.close();}catch(e){toast(e.message);}},'pill primary wide'),btn('返回',()=>{modal.close();callDial();},'pill wide'));if(!modal.open)modal.showModal();}catch(e){toast(e.message);}}
async function callHistoryPage(){try{const rows=await api('callHistory');modal.replaceChildren(el('h2','','本机通话记录'));for(const r of rows)modal.append(el('p','',`${r.name||r.character} · ${new Date(r.connectedAt||r.createdAt||0).toLocaleString()}${r.connectedAt?'':'（未接通）'} · ${r.durationSeconds||0} 秒 · ${r.reason||callNames[r.state]||r.state}`));modal.append(btn('关闭',()=>modal.close(),'pill wide'));if(!modal.open)modal.showModal();}catch(e){toast(e.message);}}
async function callPoll(){if(callBusy)return;callBusy=true;try{const c=await api('callStatus');if(!c||typeof c.state!=='string')return;callLast=c;const live=!['idle','ended','missed','rejected','cancelled'].includes(c.state);if(live||callShown){callRender(c);if(!callTimer)callTimer=setInterval(callPoll,800);}else if(callTimer){clearInterval(callTimer);callTimer=null;}}catch(e){toast('通话状态暂未读取成功');}finally{callBusy=false;}}
function callRender(c){
 if(!callLayer){callLayer=el('dialog','call-layer');callLayer.style.cssText='position:fixed;inset:auto 12px 90px;max-height:75vh;overflow:auto;z-index:80;width:calc(100% - 24px);max-width:600px;border:1px solid #8197b3;border-radius:24px;padding:20px;background:var(--card,#202b39);color:inherit';document.body.append(callLayer);}
 callLayer.replaceChildren(el('h2','',c.name||'语音通话'),el('p','',callNames[c.state]||c.state),el('p','muted',`${Math.floor((c.durationSeconds||0)/60)}:${String((c.durationSeconds||0)%60).padStart(2,'0')} · 自动轮流说话`));
 if(c.userText)callLayer.append(el('p','',`你：${c.userText}`));if(c.answer){const sub=el('article','call-subtitles');messageText(sub,c.answer);callLayer.append(sub);}if(c.error)callLayer.append(el('p','danger',c.error));else if(c.reason)callLayer.append(el('p','notice',c.reason));
 const action=(label,operation)=>btn(label,async()=>{try{const next=await api('callControl',{operation,callId:c.id});callRender(next);}catch(e){toast(e.message);}},'pill');
 if(c.state==='ringing')callLayer.append(btn('接听',async()=>{try{await api('callAnswer',{callId:c.id});callShown=true;callPoll();}catch(e){toast(e.message);}},'pill primary'),btn('拒接',async()=>{try{callRender(await api('callReject',{callId:c.id}));}catch(e){toast(e.message);}},'pill'));
 if(!['idle','ended','ringing','missed','rejected'].includes(c.state))callLayer.append(action(c.muted?'开启麦克风':'麦克风静音','mute'),action(c.speaker?'切回听筒/耳机':'扬声器','speaker'),action('打断/重新聆听','interrupt'),action('挂断','hangup'));
 const l=Object.values(c.latency||{}).some(v=>v!==null)?c.latency:c.lastLatency||{};if(Object.values(l).some(v=>v!==null))callLayer.append(el('p','muted',`本轮设备计时（毫秒）：识别 ${l.recognitionMs??'未测'} · 识别到模型首个内容片段 ${l.recognitionToFirstModelMs??'未测'} · 模型首片段到首音 ${l.modelToFirstAudioMs??'未测'} · 停讲到首音 ${l.endSpeechToAudioMs??'未测'}`));
 callLayer.append(btn(['idle','ended','missed','rejected','cancelled'].includes(c.state)?'关闭':'收起通话界面',()=>{callShown=false;callLayer.close();},'pill wide'));if(!callLayer.open&&(callShown||c.state==='ringing'))callLayer.show();
}
document.addEventListener('DOMContentLoaded',()=>{setTimeout(callPoll,1000);});
