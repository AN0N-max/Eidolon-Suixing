'use strict';
// Gesture recognition never captures the pointer or cancels scrolling.
let stickerMeaningDialog=null;
let stickerPress=null;
const stickerSuppression=new WeakMap();
function stickerCancelPress(){if(stickerPress)clearTimeout(stickerPress.timer);stickerPress=null;}
document.addEventListener('pointerdown',e=>{if(stickerPress&&e.pointerId!==stickerPress.id)stickerCancelPress();},{capture:true,passive:true});
document.addEventListener('pointermove',e=>{const p=stickerPress;if(p&&(p.id!==e.pointerId||Math.hypot(e.clientX-p.x,e.clientY-p.y)>10))stickerCancelPress();},{passive:true});
for(const type of ['pointerup','pointercancel'])document.addEventListener(type,stickerCancelPress,{passive:true});
document.addEventListener('scroll',stickerCancelPress,{capture:true,passive:true});document.addEventListener('touchstart',e=>{if(e.touches.length>1)stickerCancelPress();},{capture:true,passive:true});
function stickerBindMeaning(button,block){
 const show=()=>{stickerCancelPress();stickerSuppression.set(button,true);stickerMeaning(block);};
 button.addEventListener('pointerdown',e=>{stickerCancelPress();if(!e.isPrimary||e.button!==0)return;stickerSuppression.delete(button);const p={id:e.pointerId,x:e.clientX,y:e.clientY};stickerPress=p;p.timer=setTimeout(()=>{if(stickerPress===p&&button.isConnected)show();},550);},{passive:true});
 for(const type of ['pointerleave','lostpointercapture'])button.addEventListener(type,stickerCancelPress,{passive:true});
 button.addEventListener('click',e=>{if(stickerSuppression.get(button)){e.preventDefault();e.stopImmediatePropagation();stickerSuppression.delete(button);}},true);
 button.addEventListener('contextmenu',e=>{e.preventDefault();if(stickerSuppression.get(button))return;show();});
 button.addEventListener('keydown',e=>{if(e.key==='F10'&&e.shiftKey||e.key==='ContextMenu'){e.preventDefault();show();}});
 button.setAttribute('aria-description','长按或使用旁边的释义按钮查看含义');
}
async function stickerMeaning(block){
 stickerMeaningDialog?.close();const dialog=el('dialog','sticker-meaning');stickerMeaningDialog=dialog;dialog.dataset.session=state?.session||'';dialog.setAttribute('aria-label','表情释义');const body=el('div');
 dialog.append(el('h2','','表情释义'),body,btn('关闭',()=>dialog.close(),'pill wide'));document.body.append(dialog);body.append(el('p','muted','正在读取释义…'));
 dialog.addEventListener('close',()=>{dialog.remove();if(stickerMeaningDialog===dialog)stickerMeaningDialog=null;queueVisibleRead();});dialog.showModal();
 try{const data=await api('mediaStickerDetail',{id:block.stickerId||block.id||'',asset:block.asset});if(!dialog.open)return;body.replaceChildren();const asset=mediaUrl(block.asset);if(asset){const image=mediaImage({...block,type:'image'});image.classList.add('sticker-meaning-thumb');body.append(image);}
  let count=0;if(data?.found)for(const [key,title]of [['name','名称'],['series','系列'],['description','中文含义'],['meme','梗图含义'],['ocr','图片文字 / 译文'],['translation','文字译文'],['emotion','情绪'],['intent','适用场景'],['tone','语气'],['category','分类']]){const value=typeof data[key]==='string'?data[key].trim():'';if(!value||/^(?:s-)?[a-f0-9]{64}$/i.test(value))continue;const line=el('p','sticker-meaning-field');line.append(el('strong','',title+'：'),el('span','',value));body.append(line);if(!['name','series','category'].includes(key))count++;}
  if(!count)body.append(el('p','muted','暂无释义'+(data?.found?'':'，资源可能已移除')));
 }catch(_){if(dialog.open){body.replaceChildren(el('p','muted','暂无释义，暂时无法读取资源资料'));}}
}
