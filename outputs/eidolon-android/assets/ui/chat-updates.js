'use strict';
let keyboardVisible=false,manualOperationScope='writing';
function applyKeyboardNav(){document.documentElement.dataset.ime=String(keyboardVisible);document.documentElement.dataset.hideNav=String(state?.hideNavKeyboard!==false);socialUpdateDot(!!state?.socialStatus?.unseen);}
function keyboardChanged(value){try{const data=typeof value==='string'?JSON.parse(value):value;if(typeof data?.visible!=='boolean')return;chatLayoutChanging();keyboardVisible=data.visible;applyKeyboardNav();queueChatLayout();}catch(_){} }
// Geometry comes from the real native-resized WebView. Never subtract IME height in CSS/JS.
let chatLayout=null,chatLayoutFrame=0;
function chatLayoutSnapshot(node){const r={top:node.scrollTop,bottom:node.scrollHeight-node.clientHeight-node.scrollTop<64};const top=node.getBoundingClientRect().top;for(const item of node.querySelectorAll('[data-layout-message-id],[data-message-id]'))if(item.getBoundingClientRect().bottom>top){r.anchor=item.dataset.layoutMessageId||item.dataset.messageId;r.offset=item.getBoundingClientRect().top-top;break;}return r;}
function chatLayoutChanging(){if(chatLayout?.node.isConnected&&!chatLayout.pending){chatLayout.pending=true;stopPositionRestore();}}
function queueChatLayout(){
 const layout=chatLayout;if(!layout||!layout.node.isConnected||page!=='chat')return;layout.pending=true;cancelAnimationFrame(chatLayoutFrame);
 chatLayoutFrame=requestAnimationFrame(()=>{if(chatLayout!==layout||!layout.node.isConnected)return;const n=layout.node,s=layout.saved;
  if(s.bottom)n.scrollTop=n.scrollHeight;else{const anchor=s.anchor&&n.querySelector('[data-layout-message-id="'+s.anchor+'"],[data-message-id="'+s.anchor+'"]');if(anchor)n.scrollTop+=anchor.getBoundingClientRect().top-n.getBoundingClientRect().top-(s.offset||0);else n.scrollTop=s.top;}
  layout.height=n.clientHeight;layout.width=n.clientWidth;layout.scrollHeight=n.scrollHeight;layout.saved=chatLayoutSnapshot(n);viewPositions.set('chat/'+state.session,layout.saved);updateLatestControl();queueVisibleRead();
  chatLayoutFrame=requestAnimationFrame(()=>{if(chatLayout!==layout)return;if(n.clientHeight!==layout.height||n.clientWidth!==layout.width||n.scrollHeight!==layout.scrollHeight){queueChatLayout();return;}layout.pending=false;});
 });
}
function rememberChatLayout(){const l=chatLayout;if(l?.node.isConnected&&!l.pending){l.saved=chatLayoutSnapshot(l.node);l.height=l.node.clientHeight;l.width=l.node.clientWidth;}}
function mountChatLayout(node){
 chatLayout?.observer.disconnect();cancelAnimationFrame(chatLayoutFrame);chatLayout=null;if(page!=='chat')return;
 const layout={node,saved:chatLayoutSnapshot(node),height:node.clientHeight,width:node.clientWidth,pending:false};chatLayout=layout;
 layout.scrollHeight=node.scrollHeight;layout.observer=new ResizeObserver(()=>{if(node.clientHeight!==layout.height||node.clientWidth!==layout.width||node.scrollHeight!==layout.scrollHeight)queueChatLayout();});for(const target of [node,node.querySelector('.chat-history'),document.querySelector('.composer'),document.querySelector('.chat-tools-dock'),document.querySelector('.bottom-nav')])if(target)layout.observer.observe(target);
 node.addEventListener('scroll',()=>{if(chatLayout!==layout)return;if(node.clientHeight!==layout.height||node.clientWidth!==layout.width){queueChatLayout();return;}if(!layout.pending)rememberChatLayout();},{passive:true});
 const manual=()=>{if(chatLayout!==layout)return;cancelAnimationFrame(chatLayoutFrame);layout.pending=false;rememberChatLayout();};for(const type of ['wheel','touchstart','pointerdown','keydown'])node.addEventListener(type,manual,{passive:true});
 node.addEventListener('load',()=>queueChatLayout(),true);
}
// Rebuild only message/status subtrees. The same textarea, IME composition and form stay mounted.
function patchChat(){
 const list=document.querySelector('#messages'),form=document.querySelector('.composer');
 if(page!=='chat'||renderedPage!=='chat'||!list||!form||form.dataset.session!==state?.session)return false;
 stopPositionRestore();const saved=chatLayout?.pending?chatLayout.saved:chatLayoutSnapshot(list);viewPositions.set('chat/'+state.session,saved);hydrateChatWindow();
 list.replaceChildren();chatTimeline(list);attachOlderLoader(list);let anchor=saved?.anchor&&list.querySelector('[data-layout-message-id="'+saved.anchor+'"],[data-message-id="'+saved.anchor+'"]');
 if(!saved||saved.bottom)list.scrollTop=list.scrollHeight;else if(anchor)list.scrollTop+=anchor.getBoundingClientRect().top-list.getBoundingClientRect().top-(saved.offset||0);else list.scrollTop=saved.top;
 const status=document.querySelector('.status-line');if(status)status.textContent=waiting?'正在生成回复…':state.pendingReply?.status==='failed'?'回复失败，可重试':state.pendingReply?.status==='waiting'?'等待回复':'';
 document.querySelector('.chat-tools-dock')?.replaceWith(chatToolsDock());updateVoiceButtons();updateUnreadBadges();applyKeyboardNav();refreshScheduleStatus();queueVisibleRead();if(chatLayout){chatLayout.saved=saved;queueChatLayout();}saveViewPosition();return true;
}
function voiceChanged(message){refreshVoiceStatus();if(/失败|无法|错误|不足|超时|无效|受限|不支持|没有可|不完整|请检查/.test(message)){voiceFailure={text:requestedVoiceText,error:message};document.querySelectorAll('.voice-play').forEach(b=>{if(b.dataset.voiceText===voiceView.text){b.dataset.playback='failed';b.title=message+'；点击重试';}});updateVoiceButtons();}}
document.addEventListener('DOMContentLoaded',()=>api('keyboardStatus').then(keyboardChanged).catch(()=>{}));
