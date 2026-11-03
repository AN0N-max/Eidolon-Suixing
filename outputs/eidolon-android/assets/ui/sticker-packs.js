'use strict';
let stickerPackPending=null,stickerPackBusy=false;
async function stickerPackChoose(){try{await api('stickerPackChoose');modal.close();}catch(e){toast(e.message);}}
function stickerPackImage(item){const img=el('img','chat-media-image');img.src=item.preview;img.alt=item.name;img.loading='lazy';img.decoding='async';img.onerror=()=>{img.alt='预览不可用，请重新选择包';};return img;}
async function stickerPackShow(info){
 if(stickerPackPending&&stickerPackPending.token!==info.token)api('stickerPackCancel',{token:stickerPackPending.token}).catch(()=>{});
 stickerPackPending=info;mediaLibraryEpoch++;modal.replaceChildren(el('h2','','导入表情包整包'),el('h3','',info.name),el('p','notice',info.description||''),el('p','',info.items.length+' 张表情 · '+info.profiles+' 份角色偏好'));
 modal.append(el('p','notice','确认后合并进现有资源库。同图默认跳过，已有聊天、收藏、权限和使用次数保留。动图按静态图导入，不含声音。'));
 const update=el('input');update.type='checkbox';const profiles=el('input');profiles.type='checkbox';profiles.checked=info.profiles>0;profiles.disabled=!info.profiles;
 for(const [input,label]of [[update,'更新同图语义（保留原权限、收藏及使用统计）'],[profiles,'补充尚未设置的角色偏好（保留已设置角色）']]){const line=el('label','check');line.append(input,document.createTextNode(label));modal.append(line);}
 const error=el('p','notice');error.setAttribute('role','alert');error.hidden=true;
 const confirm=btn('确认合并导入',async()=>{
  if(stickerPackBusy)return;stickerPackBusy=true;confirm.disabled=true;error.hidden=true;
  try{const result=await api('stickerPackCommit',{token:info.token,updateExisting:update.checked,profiles:profiles.checked});if(stickerPackPending?.token===info.token)stickerPackPending=null;await mediaLibrary();toast('已导入 '+result.added+' 张，跳过 '+result.skipped+' 张，更新语义 '+result.updated+' 张；补充 '+result.profiles+' 份角色偏好');}
  catch(e){error.textContent=e.message;error.hidden=false;}
  finally{stickerPackBusy=false;confirm.disabled=false;}
 },'pill primary wide');
 modal.append(error,confirm,btn('取消导入',async()=>{if(stickerPackBusy)return;try{await api('stickerPackCancel',{token:info.token});if(stickerPackPending?.token===info.token)stickerPackPending=null;await mediaLibrary();}catch(e){toast(e.message);}},'pill wide'));
 const grid=el('div','media-grid');modal.append(grid);
 for(const item of info.items){const card=el('article','card');card.append(stickerPackImage(item),el('strong','',item.name),el('p','muted',item.category||'未分类'));const details=el('details'),summary=el('summary','','语义与使用场景');details.append(summary);for(const [key,label]of [['description','描述'],['emotion','情绪'],['intent','使用意图'],['tone','语气'],['ocr','图片文字'],['meme','梗意']])if(item[key])details.append(el('p','',label+'：'+item[key]));if(item.characters?.length)details.append(el('p','muted','限定角色：'+item.characters.map(id=>state.characters.find(c=>c.id===id)?.name||id).join('、')));card.append(details);grid.append(card);}
 if(!modal.open)modal.showModal();
}
