'use strict';
let worldUiEpoch=0,worldMountedPage='',worldPoll=0,worldRead=0,worldRefresh=0,profileLocalDraft=null;
const socialDrafts=new Map(),socialComposing=new Set();
function socialBeforeRender(){
 ++worldUiEpoch;clearInterval(worldPoll);clearInterval(worldRead);clearTimeout(worldRefresh);
 if(worldMountedPage==='citySettings')api('cityCancel').catch(()=>{});worldMountedPage=page;
}
function worldLive(epoch,node){return epoch===worldUiEpoch&&node.isConnected;}
function worldCheck(parent,title,checked){const row=el('label','check'),input=el('input');input.type='checkbox';input.checked=!!checked;row.append(input,document.createTextNode(title));parent.append(row);return input;}
function worldPair(parent,pair){if(!state.hideJapanese)parent.append(el('p','social-japanese',pair.ja));parent.append(el('p','social-chinese',pair.zh));}
function worldTime(at){const date=new Date(at);return Number.isNaN(date.getTime())?'':date.toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function worldPerson(id){return state.characters.find(c=>c.id===id)||{id,name:id};}
function worldCityName(city){return [...new Set([city.name,city.adm2,city.adm1,city.country].filter(Boolean))].join(' · ');}
function profileFingerprint(){const p=state.personal.producer||{};return JSON.stringify([p.address||'',p.gender||'',p.timeZone||'',p.birthMonth||0,p.birthDay||0,p.anniversaryMonth||0,p.anniversaryDay||0,state.personal.profile||'']);}
function profileCityEntry(parent,beforeLeave){
 const epoch=worldUiEpoch,section=el('section','profile-city');section.append(el('div','form-label','所在城市'));
 const open=btn('未设置 · 选择所在城市',()=>{beforeLeave();return navigate('citySettings');},'field city-profile-choice');section.append(open,el('p','notice','可搜索城市或主动使用当前位置查找。保存城市中心，不改变时区。'));parent.append(section);
 api('weatherGet').then(data=>{if(worldLive(epoch,section)&&data.city)open.textContent=worldCityName(data.city);}).catch(()=>{});
}
function socialUpdateDot(unseen){state.socialStatus={...(state.socialStatus||{}),unseen:!!unseen};document.querySelectorAll('[data-social-dot]').forEach(n=>n.hidden=!unseen);}
function socialEntry(){const button=btn('',()=>navigate('social'),'pill social-entry'),dot=el('span','social-dot');dot.dataset.socialDot='';dot.hidden=!state.socialStatus?.unseen;button.append(document.createTextNode('朋友圈'),dot);return button;}
function worldSources(parent){const row=el('div','social-sources');row.append(el('small','muted','天气与地点来源：'));for(const [source,title]of [['meteo','Open-Meteo'],['qweather','和风天气'],['osm','© OpenStreetMap contributors']])row.append(btn(title,()=>api('socialSourceOpen',{source}),'text-button'));parent.append(row);}
function worldWeatherText(weather){if(!weather)return '暂无新鲜天气数据';let s=(weather.kind||'')+' '+weather.temperature+'℃';if(Number.isFinite(weather.high)&&Number.isFinite(weather.low))s+=' · '+weather.low+'～'+weather.high+'℃';if(weather.later)s+=' · 稍后可能有'+weather.later;return s;}
function worldWeatherCard(parent,title,weather,error){const card=el('section','card world-weather-card');card.append(el('strong','',title),el('p','',worldWeatherText(weather)));if(weather?.fetchedAt)card.append(el('small','muted',(weather.source||'')+' · 更新于 '+worldTime(weather.fetchedAt)));if(error&&!weather)card.append(el('p','muted',error));if(weather?.warnings?.length)for(const warning of weather.warnings)card.append(el('p','notice',warning));parent.append(card);}

function socialPage(){
 const epoch=worldUiEpoch;header('朋友圈',true);const area=content('page social-page'),tools=el('div','actions');
 const refresh=btn('刷新',()=>load(false),'pill');tools.append(refresh,btn('朋友圈设置',()=>navigate('socialSettings'),'pill'));area.append(tools);
 const notice=el('div'),list=el('div','social-posts'),tail=el('div');area.append(notice,list,tail);let rows=[],hasMore=false,loading=false,ackBusy=false,ackRetry=0,mutation=0;
 const seenPosts=new Set(),seenReplies=new Set(),busyPosts=new Set();
 const marker=(kind,id)=>{const n=el('span','social-read-marker');n.dataset.readKind=kind;n.dataset.readId=id;n.setAttribute('aria-hidden','true');return n;};
 const replacePost=p=>{const at=rows.findIndex(r=>r.id===p.id);if(at>=0)rows[at]=p;const old=[...list.children].find(n=>n.dataset.post===p.id);if(old)old.replaceWith(card(p));};
 async function changePost(id,change){if(!worldLive(epoch,area))return;if(busyPosts.has(id))throw Error('正在保存这条动态，请稍候');busyPosts.add(id);mutation++;try{const value=await change();if(worldLive(epoch,list))replacePost(value);}finally{busyPosts.delete(id);}}
 function card(p){
  const post=el('article','social-post');post.dataset.post=p.id;const person=worldPerson(p.character),head=el('div','social-post-head');
   if(person.avatar)head.append(pic(person));const name=el('div');name.append(el('strong','',person.name));head.append(name);post.append(head);
  const text=el('div','social-body');worldPair(text,p);post.append(text);
  const metadata=el('div','social-meta-row'),parts=el('div','social-meta'),actions=el('div','actions social-post-actions');actions.hidden=true;parts.append(el('time','',worldTime(p.published)));
  if(p.place)parts.append(document.createTextNode(' · '),btn(p.place.name,()=>api('socialMapOpen',{id:p.id}),'social-place'));
  if(p.weather)parts.append(document.createTextNode(' · '),el('span','social-weather','东京 '+worldWeatherText(p.weather)));
  const more=btn('⋯',()=>{actions.hidden=!actions.hidden;},'social-more');more.setAttribute('aria-label','动态操作');metadata.append(parts,more);post.append(metadata,marker('post',p.id));
  actions.append(btn(p.liked?'♥ 已赞':'♡ 点赞',()=>changePost(p.id,()=>api('socialLike',{id:p.id,liked:!p.liked})),'pill'+(p.liked?' active':'')));post.append(actions);
  const circle=p.circle||[],likes=circle.filter(c=>c.kind==='like').map(c=>worldPerson(c.character).name),reactions=el('div','social-reactions');if(p.liked)likes.unshift('你');if(likes.length)reactions.append(el('p','social-likes','♥ '+likes.join('、')));post.append(reactions);
  for(const c of circle.filter(c=>c.kind==='comment')){const comment=el('section','social-circle-comment');comment.append(el('strong','',worldPerson(c.character).name));worldPair(comment,c);reactions.append(comment);}
  if(p.comment){
   const mine=el('section','social-user-comment');mine.append(el('strong','','我的评论'),el('p','',p.comment.text));
   mine.append(btn('删除评论',()=>confirmBox('删除这条评论？','相关回复也会隐藏，尚未完成的回复会取消。',()=>changePost(p.id,async()=>{await api('socialDeleteComment',{id:p.comment.id});return {...p,comment:null};})), 'text-button'));reactions.append(mine);
   if(p.comment.reply){const reply=el('section','social-author-reply');reply.append(el('strong','',person.name+' 回复你'));worldPair(reply,p.comment.reply);reply.append(marker('reply',p.comment.id));reactions.append(reply);}
  }else{
   const form=el('div','social-comment-form'),input=field(form,'写评论',socialDrafts.get(p.id)||'','area'),counter=el('small','muted');input.maxLength=280;input.rows=2;
   form.hidden=!socialComposing.has(p.id)&&!input.value;
   actions.append(btn('评论',()=>{form.hidden=false;actions.hidden=true;socialComposing.add(p.id);input.focus();},'pill'));
   const send=btn('发送评论',()=>changePost(p.id,async()=>{const sent=input.value,result=await api('socialComment',{id:p.id,text:sent});if(socialDrafts.get(p.id)===sent){socialDrafts.delete(p.id);socialComposing.delete(p.id);}return result;}),'pill');
   input.oninput=()=>{socialDrafts.set(p.id,input.value);const count=Array.from(input.value.trim()).length;counter.textContent=count+'/140';send.disabled=count===0||count>140;};input.oninput();form.append(counter,send,btn('收起',()=>{form.hidden=true;socialComposing.delete(p.id);},'text-button'));post.append(form);
  }return post;
 }
 async function load(more){
  if(loading)return;loading=true;refresh.disabled=true;const since=mutation;try{
   const data=await api('socialGet',{offset:more?rows.length:0});if(!worldLive(epoch,area)||since!==mutation)return;socialUpdateDot(data.unseen);notice.replaceChildren();
   if(!data.enabled){list.replaceChildren();tail.replaceChildren();notice.append(el('p','notice','朋友圈尚未开启。可以在总设置中选择参与角色和频率。'));return;}
   const oldTop=area.scrollTop;rows=more?[...rows,...data.posts]:data.posts;hasMore=data.hasMore;list.replaceChildren(...rows.map(card));tail.replaceChildren();
   if(!rows.length)list.append(el('p','empty','还没有已发布的动态。角色会按已开启的规则分享日常。'));
   if(hasMore)tail.append(btn('更早的动态',()=>load(true),'pill wide'));if(more)area.scrollTop=oldTop;
  }catch(e){if(worldLive(epoch,area)){notice.replaceChildren(el('p','danger',e.message),btn('重新读取',()=>load(false),'pill'));}}
  finally{loading=false;if(worldLive(epoch,refresh))refresh.disabled=false;}
 }
 function applyPoll(data){
  if(!data.enabled){rows=[];list.replaceChildren();tail.replaceChildren();notice.replaceChildren(el('p','notice','朋友圈已关闭，动态保留在本机。'));return;}
  const fresh=new Map(data.posts.map(p=>[p.id,p])),known=new Set(rows.map(p=>p.id)),newPosts=data.posts.filter(p=>!known.has(p.id));
  if(rows.length&&newPosts.length===data.posts.length&&data.hasMore){notice.replaceChildren(btn('有较多新动态，点击查看最新',()=>load(false),'pill wide'));return;}
  notice.replaceChildren();if(!known.size){hasMore=data.hasMore;tail.replaceChildren();if(hasMore)tail.append(btn('更早的动态',()=>load(true),'pill wide'));}
  const bounds=area.getBoundingClientRect(),editing=document.activeElement?.closest('.social-post'),anchor=editing&&list.contains(editing)?editing:[...list.children].find(n=>n.dataset.post&&n.getBoundingClientRect().bottom>bounds.top),anchorTop=anchor?.getBoundingClientRect().top,atTop=area.scrollTop<8&&!list.contains(document.activeElement);
  for(const p of rows){const value=fresh.get(p.id),node=[...list.children].find(n=>n.dataset.post===p.id);if(value&&JSON.stringify(value)!==JSON.stringify(p)&&!busyPosts.has(p.id)&&!node?.contains(document.activeElement))replacePost(value);}
  if(newPosts.length){list.querySelector('.empty')?.remove();const nodes=document.createDocumentFragment();for(const p of newPosts)nodes.append(card(p));list.prepend(nodes);rows=[...newPosts,...rows];}
  if(atTop)area.scrollTop=0;else if(anchor?.isConnected)area.scrollTop+=anchor.getBoundingClientRect().top-anchorTop;else if(anchor){const next=[...list.children].find(n=>n.dataset.post===anchor.dataset.post);if(next)area.scrollTop+=next.getBoundingClientRect().top-anchorTop;}
 }
 function unobscured(){return page==='social'&&!document.hidden&&!modal.open&&!(typeof callShown!=='undefined'&&callShown);}
 worldRead=setInterval(async()=>{
  if(!worldLive(epoch,area)||!unobscured()||ackBusy||Date.now()<ackRetry)return;const bounds=area.getBoundingClientRect(),posts=[],replies=[];
  for(const node of list.querySelectorAll('.social-read-marker')){const rect=node.getBoundingClientRect(),id=node.dataset.readId,set=node.dataset.readKind==='post'?seenPosts:seenReplies;if(!set.has(id)&&rect.top>=Math.max(0,bounds.top)&&rect.bottom<=Math.min(innerHeight,bounds.bottom))(node.dataset.readKind==='post'?posts:replies).push(id);}
  if(!posts.length&&!replies.length)return;ackBusy=true;try{const data=await api('socialRead',{posts:posts.slice(0,30),replies:replies.slice(0,30)});if(worldLive(epoch,area)){posts.slice(0,30).forEach(id=>seenPosts.add(id));replies.slice(0,30).forEach(id=>seenReplies.add(id));socialUpdateDot(data.unseen);}}catch(_){ackRetry=Date.now()+15000;}finally{ackBusy=false;}
 },750);
 worldPoll=setInterval(async()=>{
  if(!worldLive(epoch,area)||!unobscured()||loading)return;const since=mutation;try{const data=await api('socialGet',{offset:0});if(!worldLive(epoch,area)||since!==mutation)return;socialUpdateDot(data.unseen);applyPoll(data);}catch(_){}
 },60000);load(false);
}

function socialSettingsPage(){
 const epoch=worldUiEpoch;header('朋友圈设置',true);const area=content('page'),body=el('section');body.append(el('p','muted','正在读取设置…'));area.append(body);
 api('socialSettingsGet').then(data=>{
  if(!worldLive(epoch,body))return;body.replaceChildren();const cfg=data.config||{},enabled=worldCheck(body,'开启朋友圈',cfg.enabled);
  const perDay=choiceField(body,'每天正文请求上限',Array.from({length:10},(_,i)=>[String(i+1),(i+1)+' 次']),String(cfg.perDay||3));
  const replies=field(body,'每天评论回复请求上限',cfg.repliesPerDay??10,'number');replies.min=0;replies.max=30;
  const keep=choiceField(body,'动态保留时间',[['30','30 天'],['90','90 天'],['0','一直保留']],String(cfg.keepDays??30));
  const map=choiceField(body,'地点打开方式',[['osm','OpenStreetMap'],['google','Google 地图'],['amap','高德地图']],cfg.map||'osm');
  body.append(el('h2','','参与角色'));const roles=el('div','social-roles'),inputs=new Map();
  for(const person of state.characters)inputs.set(person.id,worldCheck(roles,person.name,cfg.allCharacters||!cfg.characters||(cfg.characters||[]).includes(person.id)));
  const select=el('div','actions');select.append(btn('全选',()=>{inputs.forEach(input=>input.checked=true);},'pill'),btn('清空选择',()=>{inputs.forEach(input=>input.checked=false);},'pill'));body.append(select,roles);
  body.append(el('p','notice','按东京日期每天安排，默认最多3次正文请求、每位每天最多1条，动态间隔至少1小时。只有近期聊过、关系达到信任且未冷却的参与角色可能发布；生日优先。失败及中断也计入当天请求额度，不自动重发。'));
  body.append(el('p','notice','点赞和评论不增加好感、不算聊天回合、不自动写长期记忆。每条同时保留一条你的评论，最多140字；她按关系亲近程度稍后回复。关闭后隐藏动态，保留期限仍有效。'));
  body.append(btn('保存设置',async()=>{await api('socialSettingsSave',{revision:cfg.revision||0,enabled:enabled.checked,perDay:Number(perDay.value),repliesPerDay:Number(replies.value),keepDays:Number(keep.value),map:map.value,allCharacters:false,characters:[...inputs].filter(([,input])=>input.checked).map(([id])=>id)});if(worldLive(epoch,body)){toast('朋友圈设置已保存');render();}},'pill primary wide'));
  const details=el('details','social-status');details.append(el('summary','','今日安排与请求记录'),el('p','muted',(data.day||'')+' · 正文 '+(data.postRequests||0)+'/'+(cfg.perDay||3)+' · 回复 '+(data.replyRequests||0)+'/'+(cfg.repliesPerDay??10)+' · 已发布 '+(data.published||0)));
  const labels={PENDING:'等待',RUNNING:'正在生成',PUBLISHED:'已发布',FAILED:'失败，未自动重试',SKIPPED:'已跳过',CANCELLED:'已取消'};
  for(const task of data.tasks||[]){const row=el('div','social-task');row.append(el('strong','',worldPerson(task.character).name+' · '+(task.kind==='post'?'动态':'评论回复')),el('p','muted',(labels[task.state]||task.state)+' · '+worldTime(task.due)));if(task.error)row.append(el('p','muted',task.error));details.append(row);}
  details.append(btn('检查自动安排',async()=>{await api('socialCheck');toast('已提交检查，将按已开启规则执行到期任务');},'pill'));body.append(details);
  const manual=el('section','card'),who=choiceField(manual,'手动安排一条动态',state.characters.map(c=>[c.id,c.name]),state.current||state.characters[0].id);
  const generate=btn('请她发一条动态',()=>confirmBox('请'+worldPerson(who.value).name+'发条动态？','会调用现有聊天模型，消耗一次正文请求额度。可能需要等待当前聊天或动态间隔；失败不自动重发。',async()=>{await api('socialGenerate',{character:who.value,confirmed:true});toast('已排入动态任务，状态可在今日记录查看');}), 'pill wide');generate.disabled=!cfg.enabled;manual.append(generate);body.append(manual);worldSources(body);
 }).catch(e=>{if(worldLive(epoch,body))body.replaceChildren(el('p','danger',e.message),btn('重新读取',()=>render(),'pill'));});
}

function weatherSettingsPage(){
 const epoch=worldUiEpoch;header('天气与城市服务',true);const area=content('page'),body=el('section');body.append(el('p','muted','正在读取设置…'));area.append(body);
 api('weatherGet').then(data=>{
  if(!worldLive(epoch,body))return;body.replaceChildren();const cfg=data.config||{},enabled=worldCheck(body,'在聊天和动态中参考天气',cfg.enabled),tokyo=worldCheck(body,'东京天气（Open-Meteo）',cfg.tokyo!==false),user=worldCheck(body,'所在城市天气与预警（和风）',cfg.user!==false);
  if(data.credentialCleanupWarning)body.append(el('p','danger',data.credentialCleanupWarning));
  body.append(btn('选择所在城市',()=>navigate('citySettings'),'pill wide'));
  const host=field(body,'和风 API Host',cfg.host||'');host.placeholder='你的专属域名.qweatherapi.com';
  const auth=choiceField(body,'和风认证方式',[['jwt','JWT / Ed25519'],['key','API Key']],cfg.auth||'jwt');
  const jwtBox=el('section'),kid=field(jwtBox,'凭据 ID（kid）',cfg.kid||''),developer=field(jwtBox,'开发者 ID（iss）',cfg.developer||''),project=field(jwtBox,'项目 ID（sub）',cfg.project||''),pem=field(jwtBox,'Ed25519 PKCS8 私钥（留空保留）','','area');pem.rows=4;pem.autocomplete='off';pem.spellcheck=false;
  const keyBox=el('section'),key=field(keyBox,'API Key（留空保留）','','password');key.autocomplete='off';const updateAuth=()=>{jwtBox.hidden=auth.value!=='jwt';keyBox.hidden=auth.value!=='key';};auth.onchange=updateAuth;updateAuth();body.append(jwtBox,keyBox);
  body.append(el('p','muted',(cfg.hasPrivateKey?'已保存私钥。':'未保存私钥。')+(cfg.hasApiKey?'已保存 API Key。':'未保存 API Key。')));const clear=worldCheck(body,'清除已保存的和风凭据',false);
  body.append(el('p','notice','搜索城市及城市天气会请求和风服务；东京天气来自Open-Meteo。凭据保存在本机加密区，页面不回显。更换服务域名须重新填写或清除凭据。读取此页面不请求天气。'));
  body.append(btn('保存设置',async()=>{await api('weatherSave',{revision:cfg.revision||0,enabled:enabled.checked,tokyo:tokyo.checked,user:user.checked,host:host.value,auth:auth.value,kid:kid.value,developer:developer.value,project:project.value,privateKey:pem.value,apiKey:key.value,clearCredentials:clear.checked});pem.value='';key.value='';if(worldLive(epoch,body)){toast('天气设置已保存');render();}},'pill primary wide'));
  const cached=el('div');body.append(cached);const show=d=>{cached.replaceChildren();worldWeatherCard(cached,'东京',d.tokyo,d.tokyoError);worldWeatherCard(cached,d.city?.name||'所在城市',d.there,d.userError);};show(data);
  body.append(btn('更新天气',async()=>{const d=await api('weatherRefresh');if(!worldLive(epoch,body))return;show(d);toast('已请求更新，缺失项目暂不提供');let checks=0;const poll=async()=>{if(!worldLive(epoch,body)||++checks>10)return;try{const next=await api('weatherGet');if(worldLive(epoch,body))show(next);}catch(_){}if(worldLive(epoch,body))worldRefresh=setTimeout(poll,2500);};worldRefresh=setTimeout(poll,2500);},'pill wide'));worldSources(body);
 }).catch(e=>{if(worldLive(epoch,body))body.replaceChildren(el('p','danger',e.message),btn('重新读取',()=>render(),'pill'));});
}

function citySettingsPage(){
 const epoch=worldUiEpoch;header('所在城市',true);const area=content('page'),current=el('section','card'),results=el('div','city-results'),message=el('p','muted');area.append(current);
 const query=field(area,'搜索城市名','','search'),actions=el('div','actions');query.placeholder='例如 上海、東京';let sequence=0;
 const showCurrent=data=>{current.replaceChildren(el('strong','','当前所在城市'));if(data.city){const c=data.city;current.append(el('p','',worldCityName(c)),el('small','muted','城市中心：'+c.latitude+', '+c.longitude+(c.zone?' · '+c.zone:'')));worldWeatherCard(current,c.name,data.there,data.userError);}else current.append(el('p','muted','尚未选择所在城市'));};
 const choices=(data,ticket)=>{if(!worldLive(epoch,area)||ticket!==sequence)return;results.replaceChildren();message.textContent=data.cities?.length?'选择一个城市，再确认保存。':'没有找到城市，试试省市名或其他写法。';for(const city of data.cities||[]){const card=el('section','card city-result');card.append(el('strong','',city.name),el('p','muted',[city.adm2,city.adm1,city.country,city.zone].filter(Boolean).join(' · ')),btn('设为所在城市',async()=>{const saved=await api('citySave',{city,revision:data.revision});if(worldLive(epoch,area)&&ticket===sequence){showCurrent(saved);results.replaceChildren();message.textContent='所在城市已保存';toast('城市已保存');}},'pill'));results.append(card);}};
 const search=btn('搜索',async()=>{const ticket=++sequence;message.textContent='正在查找城市…';results.replaceChildren();try{const data=await api('citySearch',{query:query.value});choices(data,ticket);}catch(e){if(worldLive(epoch,area)&&ticket===sequence)message.textContent=e.message;}},'pill primary');
 const locate=btn('使用当前位置查找',async()=>{const ticket=++sequence;message.textContent='请允许大致位置，正在查找附近城市…';results.replaceChildren();try{const data=await api('cityLocate');choices({cities:data.city?[data.city]:[],revision:data.revision},ticket);}catch(e){if(worldLive(epoch,area)&&ticket===sequence)message.textContent=e.message;}},'pill');
 actions.append(search,locate);area.append(actions,message,results);query.oninput=()=>{sequence++;results.replaceChildren();message.textContent='';api('cityCancel').catch(()=>{});};query.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();search.click();}};
 area.append(el('p','notice','位置只在你主动查找时使用；只保存所选城市及城市中心坐标，不保存或追踪设备原始坐标。所在城市不会自动改变角色或你的时区。'));
 area.append(btn('清除所在城市',()=>confirmBox('清除所在城市？','将停止提供这个城市的天气参考，天气服务配置保留。',async()=>{sequence++;await api('cityCancel');const data=await api('cityClear');if(worldLive(epoch,area)){showCurrent(data);results.replaceChildren();toast('城市已清除');}}),'pill wide'),btn('天气与城市服务设置',()=>navigate('weatherSettings'),'pill wide'));worldSources(area);
 api('weatherGet').then(data=>{if(worldLive(epoch,area))showCurrent(data);}).catch(e=>{if(worldLive(epoch,current))current.replaceChildren(el('p','danger',e.message));});
}
