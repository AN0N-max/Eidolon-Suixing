'use strict';
let mediaLibraryEpoch=0,mediaSending=false;
function mediaUrl(id,thumb=true){return /^[a-f0-9]{64}$/.test(id)?'/media/'+id+'/'+(thumb?'thumb.webp':'image.png'):'';}
function mediaImage(block,large=false){const img=el('img','chat-media-image');img.alt=block.type==='sticker'?(block.name||'表情包'):'聊天图片';img.loading='lazy';img.decoding='async';img.src=mediaUrl(block.asset,!large);img.onerror=()=>{const note=el('span','muted','图片暂不可用');img.replaceWith(note);};return img;}
function mediaLarge(block){const dialog=el('dialog','media-large'),image=mediaImage(block,true);dialog.append(image,btn('关闭',()=>{dialog.close();dialog.remove();},'pill wide'));document.body.append(dialog);dialog.addEventListener('cancel',()=>dialog.remove());dialog.showModal();}
function mediaBubble(message){if(!message.content?.length)return bubble(message.text,message.role==='user',false,message.id);const group=document.createDocumentFragment(),own=message.role==='user';message.content.forEach((block,index)=>{const last=index===message.content.length-1;
 if(block.type==='text'){group.append(bubble(block.text,own,false,last?message.id:0));return;}
 const row=el('div','message'+(own?' own':'')),column=el('div','message-column'),box=el('div','bubble media-bubble'+(block.type==='sticker'?' sticker-bubble':' photo-bubble'));if(!own){row.append(pic(current()));column.append(el('div','message-name',current().name));if(last)row.dataset.messageId=message.id;}const open=btn('',()=>mediaLarge(block),'media-image-button');open.setAttribute('aria-label',block.type==='sticker'?'查看表情包':'查看图片');open.append(mediaImage(block));box.append(open);if(block.type==='sticker')box.setAttribute('aria-label',block.name||'表情包');column.append(box);row.append(column);if(own)row.append(producerAvatar());group.append(row);
 });return group;}
async function mediaPick(kind){try{mediaCloseTray();await api('mediaPick',{kind});if(modal.open)modal.close();}catch(e){toast(e.message);}}
async function mediaImported(info){if(info.kind==='sticker'){toast('已导入 '+info.items.length+' 张，可到设置编辑语义');if(['stickerLibrary','stickerEditor'].includes(page)){if(info.items.length===1)await mediaEditor(info.items[0]);else render();}}else if(page==='chat'&&state?.session===info.session&&!callShown){await mediaPreview(info.items);}else toast('图片草稿已保留，请回到原会话发送');}
async function mediaPhotos(){return mediaChoosePhotos();}
async function mediaPreview(input){return mediaShowPhotoPreview(input);}
async function mediaLibrary(){if(modal.open)modal.close();mediaCloseTray();if(page==='stickerLibrary'){render();return;}return navigate('stickerLibrary');}
async function mediaEditor(entry){mediaEditorEntry=structuredClone(entry);if(modal.open)modal.close();return navigate('stickerEditor');}
async function mediaRoleSettings(selected){if(typeof selected==='string')mediaPreferenceWho=selected;if(modal.open)modal.close();if(page==='stickerPreferences'){render();return;}return navigate('stickerPreferences');}
async function mediaVisionSettings(){if(modal.open)modal.close();return navigate('mediaVision');}
