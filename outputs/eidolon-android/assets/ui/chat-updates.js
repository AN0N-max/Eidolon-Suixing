'use strict';
let keyboardVisible=false,manualOperationScope='writing';
function applyKeyboardNav(){document.documentElement.dataset.ime=String(keyboardVisible);document.documentElement.dataset.hideNav=String(state?.hideNavKeyboard!==false);socialUpdateDot(!!state?.socialStatus?.unseen);}
function keyboardChanged(value){try{const data=typeof value==='string'?JSON.parse(value):value;if(typeof data?.visible!=='boolean')return;keyboardVisible=data.visible;applyKeyboardNav();}catch(_){} }
// Rebuild only message/status subtrees. The same textarea, IME composition and form stay mounted.
function patchChat(){
 const list=document.querySelector('#messages'),form=document.querySelector('.composer');
 if(page!=='chat'||renderedPage!=='chat'||!list||!form||form.dataset.session!==state?.session)return false;
 stopPositionRestore();saveViewPosition();const saved=viewPositions.get('chat/'+state.session);hydrateChatWindow();
 list.replaceChildren();chatTimeline(list);attachOlderLoader(list);let anchor=saved?.anchor&&list.querySelector('[data-message-id="'+saved.anchor+'"]');
 if(!saved||saved.bottom)list.scrollTop=list.scrollHeight;else if(anchor)list.scrollTop+=anchor.getBoundingClientRect().top-list.getBoundingClientRect().top-(saved.offset||0);else list.scrollTop=saved.top;
 const status=document.querySelector('.status-line');if(status)status.textContent=waiting?'正在生成回复…':state.pendingReply?.status==='failed'?'回复失败，可重试':state.pendingReply?.status==='waiting'?'等待回复':'';
 document.querySelector('.chat-tools-dock')?.replaceWith(chatToolsDock());updateVoiceButtons();updateUnreadBadges();applyKeyboardNav();refreshScheduleStatus();queueVisibleRead();saveViewPosition();return true;
}
function voiceChanged(message){refreshVoiceStatus();if(/失败|无法|错误|不足|超时|无效|受限|不支持|没有可|不完整|请检查/.test(message)){voiceFailure={text:requestedVoiceText,error:message};document.querySelectorAll('.voice-play').forEach(b=>{if(b.dataset.voiceText===voiceView.text){b.dataset.playback='failed';b.title=message+'；点击重试';}});updateVoiceButtons();}}
document.addEventListener('DOMContentLoaded',()=>api('keyboardStatus').then(keyboardChanged).catch(()=>{}));
