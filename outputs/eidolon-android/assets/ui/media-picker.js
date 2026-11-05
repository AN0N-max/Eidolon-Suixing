'use strict';
// Chat-only controls. Catalogue editing and provider configuration live in Settings.
let mediaTray=null,mediaTrayEpoch=0,mediaPreviewState=null;
function mediaObscuresChat(){return !!mediaTray||!!document.querySelector('.media-photo-preview[open],.media-large[open],.sticker-meaning[open]');}
function mediaCloseTray(focus=false){
 mediaTrayEpoch++;document.querySelector('#sticker-tray')?.remove();mediaTray=null;app.classList.remove('sticker-tray-open');
 const toggle=document.querySelector('[aria-label="表情包"]');toggle?.setAttribute('aria-expanded','false');toggle?.classList.remove('selected');
 if(focus)document.querySelector('.composer textarea')?.focus();queueVisibleRead();
}
function mediaCloseTransient(){stickerMeaningDialog?.close();mediaCloseTray();mediaPreviewState?.dialog.close();}
function mediaBeforeRender(){
 if(stickerMeaningDialog&&(page!=='chat'||state?.session!==stickerMeaningDialog.dataset.session||callShown))stickerMeaningDialog.close();
 if(mediaTray&&(page!=='chat'||state?.session!==mediaTray.session||callShown))mediaCloseTray();
 if(mediaPreviewState&&(page!=='chat'||state?.session!==mediaPreviewState.session||callShown))mediaPreviewState.dialog.close();
}
function mediaRestoreComposer(){if(mediaTray&&page==='chat')mediaMountTray();}
async function mediaToggleTray(){
 if(mediaTray){mediaCloseTray(true);return;}if(page!=='chat'||!state?.session||callShown)return;
 document.querySelector('.composer textarea')?.blur();mediaTray={session:state.session,rows:[],loaded:false,query:'',tab:'all',error:''};
 mediaMountTray();const tray=mediaTray,epoch=++mediaTrayEpoch;
 try{
  for(let offset=0;offset<2040;offset+=60){
   const rows=await api('mediaLibrary',{offset});if(epoch!==mediaTrayEpoch||mediaTray!==tray||state?.session!==tray.session||page!=='chat')return;
   tray.rows.push(...rows.filter(e=>e.enabled!==false&&!e.deleted));if(rows.length<60)break;
  }
  tray.loaded=true;mediaMountTray();
 }catch(e){if(epoch===mediaTrayEpoch&&mediaTray===tray){tray.error=e.message;mediaMountTray();}}
}
function mediaMountTray(){
 const tray=mediaTray;if(!tray||page!=='chat'||state?.session!==tray.session)return;
 document.querySelector('#sticker-tray')?.remove();const panel=el('section','sticker-tray');panel.id='sticker-tray';panel.setAttribute('aria-label','选择表情包');
 const top=el('div','sticker-tray-top'),search=el('input','sticker-search');search.placeholder='搜索表情';search.setAttribute('aria-label','搜索要发送的表情');search.value=tray.query;
 top.append(search,el('small','muted','点击发送 · 长按查看释义'),ib('close','收起表情面板',()=>mediaCloseTray()));panel.append(top);
 const grid=el('div','sticker-send-grid');grid.setAttribute('aria-label','表情图片');panel.append(grid);
 const tabs=el('div','sticker-series-tabs');tabs.setAttribute('role','tablist');tabs.setAttribute('aria-label','表情分类');panel.append(tabs);
 const series=[...new Set(tray.rows.map(r=>r.series).filter(Boolean))];
 const options=[['all','全部'],['favorites','收藏'],['recent','最近'],...series.map(s=>['series:'+s,s])];if(!options.some(([id])=>id===tray.tab))tray.tab='all';
 function fill(){
  grid.replaceChildren();if(tray.error){grid.append(el('p','empty',tray.error),btn('重新读取',async()=>{mediaCloseTray();await mediaToggleTray();},'pill'));return;}
  if(!tray.loaded){grid.append(el('p','empty','正在读取表情…'));return;}
  const query=tray.query.trim().toLowerCase();let rows=tray.rows.filter(r=>(tray.tab==='all'||tray.tab==='favorites'&&r.favorite||tray.tab==='recent'&&r.lastUsed>0||tray.tab==='series:'+r.series)&&(!query||[r.name,r.series,r.category,r.emotion,r.intent,r.ocr].join(' ').toLowerCase().includes(query)));
  if(tray.tab==='recent')rows=rows.slice().sort((a,b)=>(b.lastUsed||0)-(a.lastUsed||0));
  for(const row of rows){const b=btn('',()=>mediaSendSticker(row,tray.session),'sticker-send-item');b.setAttribute('aria-label','发送表情：'+row.name);b.title=row.name;b.append(mediaImage({type:'sticker',asset:row.asset,name:row.name}));const block={type:'sticker',asset:row.asset,stickerId:row.id,name:row.name};stickerBindMeaning(b,block);const cell=el('div','sticker-send-cell'),info=btn('释义',()=>stickerMeaning(block),'sticker-meaning-button');info.setAttribute('aria-label','查看表情释义：'+row.name);cell.append(b,info);grid.append(cell);}
  if(!rows.length)grid.append(el('p','empty',tray.rows.length?'这里还没有表情':'还没有表情。可在设置 → 聊天与关系中导入。'));
 }
 for(const [id,title]of options){const b=btn('',()=>{tray.tab=id;for(const n of tabs.children)n.setAttribute('aria-selected',String(n===b));fill();},'sticker-series-tab');b.setAttribute('role','tab');b.setAttribute('aria-selected',String(id===tray.tab));b.setAttribute('aria-label',title);
  const first=id.startsWith('series:')?tray.rows.find(r=>r.series===title):null;if(first)b.append(mediaImage({type:'sticker',asset:first.asset,name:title}));else b.append(icon(id==='favorites'?'heart':id==='recent'?'history':'smile'));b.append(el('span','',title));tabs.append(b);
 }
 search.oninput=()=>{tray.query=search.value;fill();};fill();const nav=app.querySelector('.bottom-nav');if(nav)app.insertBefore(panel,nav);else app.append(panel);app.classList.add('sticker-tray-open');
 const toggle=document.querySelector('[aria-label="表情包"]');toggle?.setAttribute('aria-expanded','true');toggle?.classList.add('selected');
}
async function mediaSendSticker(entry,session){
 if(mediaSending||page!=='chat'||state?.session!==session||callShown)return;mediaSending=true;document.querySelector('#sticker-tray')?.setAttribute('aria-busy','true');
 try{const next=await api('mediaSend',{session,text:'',content:[{type:'sticker',asset:entry.asset,stickerId:entry.id}]});if(page==='chat'&&state?.session===session){state=next;render();}}
 catch(e){toast(e.message);}finally{mediaSending=false;document.querySelector('#sticker-tray')?.removeAttribute('aria-busy');}
}
async function mediaChoosePhotos(){
 if(page!=='chat'||!state?.session||callShown)return;mediaCloseTray();const session=state.session;
 const blocks=await api('mediaDraft',{session});if(page!=='chat'||state?.session!==session)return;
 if(blocks.length)return mediaPreview(blocks);await mediaPick('image');
}
async function mediaShowPhotoPreview(input){
 if(page!=='chat'||!state?.session||callShown)return;const session=state.session,blocks=structuredClone(input||await api('mediaDraft',{session}));
 if(page!=='chat'||state?.session!==session||!blocks.length)return;mediaCloseTray();mediaPreviewState?.dialog.close();
 const dialog=el('dialog','media-photo-preview');dialog.setAttribute('aria-label','发送图片预览');const model={dialog,session,blocks,selected:new Set(blocks.map((_,i)=>i)),focus:null};mediaPreviewState=model;
 const head=el('div','photo-preview-head');head.append(ib('close','取消图片发送',()=>dialog.close()),el('strong','','已选图片'),btn('重新选择',async()=>{dialog.close();if(page==='chat'&&state?.session===session)await mediaPick('image');},'photo-plain'));
 const grid=el('div','photo-selected-grid'),large=el('div','photo-selected-large');large.hidden=true;const caption=el('input','photo-caption');caption.placeholder='附带文字（可选）';caption.setAttribute('aria-label','图片附带文字');caption.value=draft||'';caption.maxLength=6000;
 const footer=el('div','photo-preview-footer'),preview=btn('预览',()=>{const index=[...model.selected][0];if(index!==undefined)showLarge(index);},'photo-plain'),send=btn('',async()=>{
  if(mediaSending||page!=='chat'||state?.session!==session||!model.selected.size)return;mediaSending=true;send.disabled=true;
  try{const chosen=blocks.filter((_,i)=>model.selected.has(i)),next=await api('mediaSend',{session,text:caption.value,content:chosen});if(mediaPreviewState===model){dialog.close();if(page==='chat'&&state?.session===session){draft='';state=next;render();}}}
  catch(e){toast(e.message);}finally{mediaSending=false;send.disabled=!model.selected.size;}
 },'photo-send');
 const clear=btn('清空草稿',async()=>{await api('mediaDraft',{session,clear:true});dialog.close();},'photo-plain');footer.append(preview,clear,send);
 function update(){send.textContent='发送（'+model.selected.size+'）';send.disabled=!model.selected.size||mediaSending;preview.disabled=!model.selected.size;for(const b of grid.querySelectorAll('.photo-select-check')){const index=Number(b.dataset.index);b.setAttribute('aria-checked',String(model.selected.has(index)));b.textContent=model.selected.has(index)?String([...model.selected].indexOf(index)+1):'';}}
 function showLarge(index){model.focus=index;grid.hidden=true;large.hidden=false;large.replaceChildren();const back=btn('返回已选图片',()=>{grid.hidden=false;large.hidden=true;model.focus=null;},'photo-plain');large.append(back,mediaImage(blocks[index],true));}
 blocks.forEach((b,i)=>{const card=el('div','photo-selected-item'),open=btn('',()=>showLarge(i),'photo-selected-open');open.setAttribute('aria-label','预览第'+(i+1)+'张图片');open.append(mediaImage(b));const check=btn('',()=>{if(model.selected.has(i))model.selected.delete(i);else model.selected.add(i);update();},'photo-select-check');check.dataset.index=i;check.setAttribute('role','checkbox');check.setAttribute('aria-label','选择第'+(i+1)+'张图片');card.append(open,check);grid.append(card);});
 dialog.append(head,grid,large,caption,footer,el('p','photo-local-note','最多4张 · 按现有图片质量处理 · 返回保留草稿'));document.body.append(dialog);update();
 model.back=()=>{if(model.focus!==null){grid.hidden=false;large.hidden=true;model.focus=null;}else dialog.close();};
 dialog.addEventListener('close',()=>{if(mediaPreviewState===model)mediaPreviewState=null;dialog.remove();queueVisibleRead();});dialog.addEventListener('cancel',e=>{e.preventDefault();if(!mediaSending)model.back();});dialog.showModal();
}
