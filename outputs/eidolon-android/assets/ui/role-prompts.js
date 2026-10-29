'use strict';
let rolePromptWho='',rolePromptEpoch=0,rolePromptEditor=null;
function rolePromptDirty(){const r=rolePromptEditor;return !!(r&&(r.sheet.value!==r.saved.sheet||r.facts.value!==r.saved.facts));}
async function rolePromptSave(){
 const r=rolePromptEditor;if(!r)throw Error('角色提示词尚未读取');
 const saved=await api('rolePromptSave',{character:r.saved.character,revision:r.saved.revision,sheet:r.sheet.value,facts:r.facts.value});
 if(rolePromptEditor!==r)return;r.saved=saved;r.sheet.value=saved.sheet;r.facts.value=saved.facts;r.update();setTimeout(()=>{if(rolePromptEditor===r)r.update();},0);toast('已保存，后续生成使用新提示词');
}
async function rolePromptMayLeave(){
 if(!rolePromptDirty())return true;
 return new Promise(resolve=>{let settled=false;const onClose=()=>{if(modal.open||settled)return;settled=true;modal.removeEventListener('close',onClose);resolve(false);};const finish=value=>{if(settled)return;settled=true;modal.removeEventListener('close',onClose);resolve(value);modal.close();};
  modal.replaceChildren(el('h2','','提示词尚未保存'),el('p','notice','保存后切换，或放弃本次编辑。已保存的聊天和关系不会改变。'),btn('保存并继续',async()=>{await rolePromptSave();finish(true);},'pill primary wide'),btn('放弃本次编辑',()=>finish(true),'pill wide'),btn('继续编辑',()=>finish(false),'pill wide'));
  modal.addEventListener('close',onClose);modal.showModal();
 });
}
function rolePromptPage(){
 header('角色提示词',true);const area=content('page role-prompt-page');
 if(!state.characters.some(c=>c.id===rolePromptWho))rolePromptWho=state.current||state.characters.find(c=>c.lastActivity>0)?.id||state.characters[0].id;
 const who=choiceField(area,'要编辑的角色',state.characters.map(c=>[c.id,c.name]),rolePromptWho);
 who.onchange=async()=>{const next=who.value;who.setChoice(rolePromptWho);who.disabled=true;try{if(await rolePromptMayLeave()){rolePromptWho=next;render();}}finally{who.disabled=false;}};
 area.append(el('p','notice','只修改所选角色的人设与补充资料。保存在本机，并随加密备份保存；查看和保存不调用模型。修改用于后续聊天、主动搭话、来信与日记，已有待发送回复不重新生成。'));
 const body=el('section','role-prompt-body');area.append(body);body.append(el('p','muted','正在读取…'));const token=++rolePromptEpoch;rolePromptEditor=null;
 api('rolePromptGet',{character:rolePromptWho}).then(data=>{if(page!=='rolePrompts'||token!==rolePromptEpoch)return;body.replaceChildren();
  const status=el('p','role-prompt-status'),sheet=field(body,'角色设定提示词',data.sheet,'area');body.prepend(status);sheet.classList.add('role-prompt-text');sheet.maxLength=data.limit;sheet.spellcheck=false;
  const sheetCount=el('small','muted');body.append(sheetCount);const details=el('details','role-prompt-facts');details.append(el('summary','','角色补充资料'));body.append(details);
  const facts=field(details,'补充资料内容',data.facts,'area');facts.classList.add('role-prompt-text');facts.maxLength=data.limit;facts.spellcheck=false;const factsCount=el('small','muted');details.append(factsCount);
  const actions=el('div','actions role-prompt-actions'),save=btn('保存角色提示词',rolePromptSave,'pill primary'),original=btn('查看内置原稿',()=>{const r=rolePromptEditor.saved;modal.replaceChildren(el('h2','',data.name+' · 内置原稿'));for(const [title,key] of [['内置角色设定','defaultSheet'],['内置补充资料','defaultFacts']]){const input=field(modal,title,r[key],'area');input.readOnly=true;input.classList.add('role-prompt-text');}modal.append(btn('关闭',()=>modal.close(),'pill wide'));modal.showModal();},'pill');
  const reset=btn('恢复该角色默认提示词',()=>confirmBox('恢复'+data.name+'的默认提示词？','仅恢复角色设定和补充资料，本次未保存的编辑会放弃。聊天、记忆、好感和其他角色提示词保留。',async()=>{const r=rolePromptEditor,saved=await api('rolePromptReset',{character:r.saved.character,revision:r.saved.revision});r.saved=saved;r.sheet.value=saved.sheet;r.facts.value=saved.facts;r.update();toast('已恢复内置默认提示词');}),'pill');
  actions.append(save,original,reset);body.append(actions,el('p','notice','角色设定每项最多20000字，较长内容会增加后续请求的上下文与费用。行程使用精简设定。这里只编辑角色资料；日期、记忆、关系与语言格式等应用规则仍会另行加入。'));
  const r={saved:data,sheet,facts,update(){const dirty=rolePromptDirty();status.textContent=data.name+' · '+(dirty?'有未保存修改':r.saved.modified?'正在使用自定义提示词':'正在使用内置默认提示词');sheetCount.textContent=sheet.value.length+' / '+data.limit+' 字';factsCount.textContent=facts.value.length+' / '+data.limit+' 字';save.disabled=!dirty;reset.disabled=!dirty&&!r.saved.modified;}};rolePromptEditor=r;sheet.oninput=facts.oninput=()=>r.update();r.update();
 }).catch(e=>{if(page==='rolePrompts'&&token===rolePromptEpoch){body.replaceChildren(el('p','danger',e.message),btn('重新读取',()=>render(),'pill'));}});
}
