'use strict';
const viewPositions=new Map(),chatWindows=new Map();let mountedView=null,positionRestore=null,positionEpoch=0;
function viewPositionKey(){if(page==='chat')return 'chat/'+state.session;if(page==='notebook')return page+'/'+notebookWho;if(page==='rolePrompts')return page+'/'+rolePromptWho;if(page==='mailbox'||page==='diary')return page+'/'+writingCharacter;if(page==='schedule')return page+'/'+scheduleSelected;if(page==='promises')return page+'/'+promiseCharacter;return page;}
function saveViewPosition(){
 const v=mountedView;if(!v?.node.isConnected||positionRestore?.key===v.key)return;const n=v.node,r={top:n.scrollTop,bottom:n.scrollHeight-n.clientHeight-n.scrollTop<64};
 if(v.chat){const top=n.getBoundingClientRect().top;for(const item of n.querySelectorAll('[data-message-id]'))if(item.getBoundingClientRect().bottom>top){r.anchor=Number(item.dataset.messageId);r.offset=item.getBoundingClientRect().top-top;break;}}
 viewPositions.set(v.key,r);
}
function stopPositionRestore(){if(!positionRestore)return;positionRestore.observer.disconnect();clearTimeout(positionRestore.timer);positionRestore=null;}
function mountViewPosition(){
 stopPositionRestore();const node=document.querySelector('#app>.content');if(!node){mountedView=null;return;}
 const key=viewPositionKey(),chat=page==='chat',saved=viewPositions.get(key),epoch=++positionEpoch;mountedView={key,node,chat};
 const restore=()=>{if(epoch!==positionEpoch||!node.isConnected)return;if(chat&&(!saved||saved.bottom))node.scrollTop=node.scrollHeight;else if(saved){const anchor=saved.anchor&&node.querySelector('[data-message-id="'+saved.anchor+'"]');if(anchor)node.scrollTop+=anchor.getBoundingClientRect().top-node.getBoundingClientRect().top-(saved.offset||0);else node.scrollTop=saved.top;}if(positionRestore)positionRestore.lastTop=node.scrollTop;updateLatestControl();};
 const observer=new MutationObserver(()=>requestAnimationFrame(restore));positionRestore={key,observer,timer:setTimeout(()=>{if(positionRestore?.key===key){stopPositionRestore();saveViewPosition();}},2500)};observer.observe(node,{childList:true,subtree:true});
 const interact=()=>{stopPositionRestore();};for(const type of ['wheel','touchstart','pointerdown','keydown'])node.addEventListener(type,interact,{passive:true});
 node.addEventListener('scroll',()=>{if(positionRestore&&Math.abs(node.scrollTop-positionRestore.lastTop)>1)stopPositionRestore();saveViewPosition();updateLatestControl();},{passive:true});restore();requestAnimationFrame(restore);
}
function hydrateChatWindow(){
 const fresh=state.messages||[],prior=chatWindows.get(state.session)||[];state.messages=fresh.length?[...prior.filter(m=>m.id<fresh[0].id),...fresh]:[];rememberChatWindow();
}
function rememberChatWindow(){if(!state?.session)return;chatWindows.delete(state.session);chatWindows.set(state.session,[...(state.messages||[])]);let size=0;for(const rows of chatWindows.values())for(const row of rows)size+=row.text?.length||0;while(chatWindows.size>12||size>4000000){const oldest=chatWindows.keys().next().value,rows=chatWindows.get(oldest);chatWindows.delete(oldest);for(const row of rows)size-=row.text?.length||0;}}
function updateLatestControl(){const n=document.querySelector('#messages'),b=document.querySelector('.chat-latest');if(n&&b){b.hidden=n.scrollHeight-n.clientHeight-n.scrollTop<100;const count=state?.unread?.characters?.[state.current]||0;b.dataset.hasUnread=String(count>0);b.setAttribute('aria-description',count?'该角色有'+count+'条未读消息':'回到会话底部');}}
function jumpToLatest(){stopPositionRestore();const n=document.querySelector('#messages');if(n){n.scrollTop=n.scrollHeight;saveViewPosition();updateLatestControl();queueVisibleRead();}}
