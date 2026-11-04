const fs=require('fs'),path=require('path'),assert=require('assert');
const base=path.resolve(__dirname,'../../..');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../assets');
 const characters=JSON.parse(fs.readFileSync(root+'/characters.json','utf8')).map(c=>({...c,lastActivity:0,preview:''}));
 const data={characters,personal:{producer:{timeZone:'Asia/Shanghai'},affinity:{},relations:{},memories:[]},config:{},features:{},writings:[],reachOptions:{},replyOptions:{},unread:{total:0,characters:{}},socialStatus:{enabled:true,unseen:true}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>{const u=new URL(r.request().url());return u.origin==='https://eidolon.local'?r.fulfill({path:path.join(root,u.pathname)}):r.abort();});
  await page.addInitScript(snapshot=>{
   window.calls=[];window.cityWait=false;window.readIds=[];window.replyReadIds=[];window.socialWait=false;
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>{if(ms===60000&&String(fn).includes('applyPoll'))window.actualSocialPoll=fn;if(ms===750&&String(fn).includes('socialRead')){window.actualSocialRead=fn;return interval(()=>{if(window.worldReadEnabled!==false)fn();},ms,...args);}return interval(fn,ms,...args);};
   window.socialConfig={enabled:true,perDay:3,repliesPerDay:10,keepDays:30,map:'osm',characters:[],allCharacters:true,revision:1};
   window.weatherFixture={config:{enabled:true,tokyo:true,user:true,host:'test.qweatherapi.com',auth:'jwt',kid:'fixture-kid',developer:'fixture-developer',project:'fixture-project',hasPrivateKey:true,hasApiKey:false,revision:1},city:{id:'101020100',name:'上海',adm1:'上海',adm2:'上海',country:'中国',latitude:31.23,longitude:121.47,zone:'Asia/Shanghai'},tokyo:{kind:'雨',temperature:18,high:20,low:15,source:'Open-Meteo',fetchedAt:Date.now()},there:{kind:'多云',temperature:22,source:'QWeather',fetchedAt:Date.now()}};
   window.posts=Array.from({length:50},(_,i)=>({id:'post-'+i,character:snapshot.characters[i%snapshot.characters.length].id,day:'2026-11-04',published:Date.now()-i*3600000,ja:'今日はゆっくり過ごしてるよ。',zh:'今天慢慢地过日子，练习结束后休息一下。'+(i===0?'<img src=x onerror="window.injected=true">':''),liked:false,place:i===0?{name:'公园',lat:35.6,lon:139.6}:null,weather:i===0?{kind:'雨',temperature:18}:null,circle:i===0?[{character:snapshot.characters[1].id,kind:'like'},{character:snapshot.characters[2].id,kind:'comment',ja:'いいね',zh:'真好呀'}]:[],comment:null}));
   const send=(id,out,error)=>setTimeout(()=>nativeReply(id,structuredClone(out),error||null),0);
   window.Native={post(raw){const q=JSON.parse(raw);calls.push(q);let out=snapshot,error=null;
    if(q.action==='themeGet')out={dark:false};if(q.action==='voiceStatus')out={state:'idle'};if(q.action==='scheduleStatus')out={enabled:false};
    if(q.action==='socialGet'){out={enabled:socialConfig.enabled,posts:posts.slice(q.args.offset,q.args.offset+30),hasMore:q.args.offset+30<posts.length,unseen:readIds.length<posts.length};if(socialWait){const frozen=structuredClone(out);window.resolveSocial=()=>send(q.id,frozen);return;}}
    if(q.action==='socialRead'){readIds.push(...q.args.posts);replyReadIds.push(...q.args.replies);out={unseen:readIds.length<posts.length};}
    if(q.action==='socialLike'){out=posts.find(p=>p.id===q.args.id);out.liked=q.args.liked;}
    if(q.action==='socialComment'){out=posts.find(p=>p.id===q.args.id);out.comment={id:'comment-'+out.id,post:out.id,text:q.args.text};}
    if(q.action==='socialDeleteComment'){const p=posts.find(p=>p.comment?.id===q.args.id);if(p)p.comment=null;out={};}
    if(q.action==='socialSettingsGet'||q.action==='socialCheck'||q.action==='socialGenerate')out={config:socialConfig,day:'2026-11-04',postRequests:1,replyRequests:0,published:1,tasks:[{character:snapshot.characters[0].id,kind:'post',state:'FAILED',due:Date.now(),error:'准备动态资料时本地内容不可用，尚未调用聊天模型；未自动重试',diagnostic:{category:'local_data',stage:'prepare_places',requestAttempted:false,field:'lat',location:'PlacePool.all:12',code:'local_data'}}]};
    if(q.action==='socialDiagnose')out={ok:false,requestAttempted:false,stage:'prepare_places',category:'local_data',field:'lat',location:'PlacePool.all:12',message:'准备地点字段不完整，未请求模型',warnings:[]};
    if(q.action==='socialSettingsSave'){socialConfig={...q.args,revision:socialConfig.revision+1};out={config:socialConfig};}
    if(q.action==='weatherGet'||q.action==='weatherRefresh')out=weatherFixture;
    if(q.action==='weatherSave'){weatherFixture.config={...q.args,hasPrivateKey:true,revision:weatherFixture.config.revision+1};delete weatherFixture.config.privateKey;delete weatherFixture.config.apiKey;out=weatherFixture;}
    if(q.action==='citySearch'){
     out={cities:[weatherFixture.city||{id:'101020100',name:'上海',adm1:'上海',latitude:31.23,longitude:121.47,zone:'Asia/Shanghai'}],revision:weatherFixture.config.revision};
     if(cityWait){window.resolveCity=()=>send(q.id,out);return;}
    }
    if(q.action==='cityLocate'){window.locateId=q.id;window.resolveLocation=()=>send(q.id,{city:weatherFixture.city,revision:weatherFixture.config.revision});return;}
    if(q.action==='cityCancel'){if(window.locateId){send(locateId,null,'城市查找已取消');window.locateId=0;}out={};}
    if(q.action==='citySave'){weatherFixture.city=q.args.city;out=weatherFixture;}
    if(q.action==='cityClear'){weatherFixture.city=null;out=weatherFixture;}
    if(q.action==='socialMapOpen'||q.action==='socialSourceOpen')out={url:'https://www.openstreetmap.org/'};
    send(q.id,out,error);
   }};
  },data);
  await page.goto('https://eidolon.local/ui/index.html');await page.locator('.roster-row').first().waitFor();
  await page.evaluate(()=>navigate('calendar'));await page.getByRole('navigation',{name:'主导航'}).getByRole('button',{name:'朋友圈',exact:true}).click();await page.locator('.social-post').first().waitFor();
  await page.getByText('<img src=x onerror="window.injected=true">',{exact:false}).waitFor();assert.equal(await page.evaluate(()=>!!window.injected),false);assert.equal(await page.locator('.social-post img[src="x"]').count(),0);
  await page.waitForFunction(()=>calls.some(q=>q.action==='socialRead'));
  assert((await page.evaluate(()=>readIds)).every(id=>['post-0','post-1'].includes(id)),'initial view never acknowledges the whole loaded page');
  const first=page.locator('.social-post[data-post="post-0"]');await first.getByRole('button',{name:'动态操作',exact:true}).click();await first.getByRole('button',{name:'♡ 点赞',exact:true}).click();await first.getByText('♥ 你、',{exact:false}).waitFor();
  await first.getByRole('button',{name:'动态操作',exact:true}).click();await first.getByRole('button',{name:'评论',exact:true}).click();await first.getByLabel('写评论').fill('😀'.repeat(141).slice(0,280));assert.equal(await first.getByRole('button',{name:'发送评论',exact:true}).isEnabled(),true,'140 emoji allowed');
  await first.getByLabel('写评论').fill('谢谢你');await page.evaluate(()=>{socialWait=true;window.heldPoll=actualSocialPoll();});await page.waitForFunction(()=>!!window.resolveSocial);
  await first.getByRole('button',{name:'发送评论',exact:true}).click();await first.getByText('我的评论',{exact:true}).waitFor();await page.evaluate(async()=>{socialWait=false;resolveSocial();await heldPoll;});assert.equal(await first.getByText('我的评论',{exact:true}).count(),1,'older reads cannot erase a newer posted comment');
  await page.evaluate(()=>{worldReadEnabled=false;posts[0].comment.reply={ja:'ありがとう',zh:'谢谢你的留言'};posts[0].comment.replyAt=Date.now();return actualSocialPoll();});await first.getByText('谢谢你的留言',{exact:true}).waitFor();
  await page.evaluate(async()=>{confirmBox('回复遮挡','模拟覆盖回复',async()=>{});await actualSocialRead();});assert(!(await page.evaluate(()=>replyReadIds.includes('comment-post-0'))),'covered reply is not marked read');await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();await first.getByText('谢谢你的留言',{exact:true}).scrollIntoViewIfNeeded();await page.evaluate(()=>actualSocialRead());await page.waitForFunction(()=>replyReadIds.includes('comment-post-0'));await page.evaluate(()=>worldReadEnabled=true);
  const second=page.locator('.social-post[data-post="post-1"]');await second.getByRole('button',{name:'动态操作',exact:true}).click();await second.getByRole('button',{name:'评论',exact:true}).click();await second.getByLabel('写评论').fill('未提交的评论草稿');await second.getByLabel('写评论').evaluate(n=>n.setSelectionRange(2,2));const draftTop=await second.getByLabel('写评论').evaluate(n=>n.getBoundingClientRect().top);
  await page.evaluate(()=>{posts[0].circle.push({character:posts[1].character,kind:'comment',ja:'新しい反応',zh:'后来的互动'});posts.unshift({...structuredClone(posts[2]),id:'post-new',published:Date.now(),comment:null});return actualSocialPoll();});assert.equal(await second.getByLabel('写评论').inputValue(),'未提交的评论草稿');assert(await second.getByLabel('写评论').evaluate(n=>document.activeElement===n&&n.selectionStart===2));assert(Math.abs(await second.getByLabel('写评论').evaluate(n=>n.getBoundingClientRect().top)-draftTop)<3,'poll preserves editing position');assert(!(await page.evaluate(()=>readIds.includes('post-new'))),'new rows above viewport remain unread');
  await first.getByRole('button',{name:'删除评论',exact:true}).click();await page.getByRole('dialog').waitFor();
  await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();assert.equal(await first.getByText('我的评论',{exact:true}).count(),1);
  // Opening a dialog freezes viewport acknowledgements even if the underlying list scrolls.
  await page.evaluate(()=>confirmBox('测试遮挡','模拟一次性确认',async()=>{}));const before=await page.evaluate(()=>calls.filter(q=>q.action==='socialRead').length);
  await page.evaluate(()=>document.querySelector('.social-page').scrollTop=100000);await page.waitForTimeout(1000);assert.equal(await page.evaluate(()=>calls.filter(q=>q.action==='socialRead').length),before);
  await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();await page.getByRole('button',{name:'更早的动态',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.social-post').length===51);
  assert(!(await page.evaluate(()=>readIds.includes('post-25'))),'skipped/offscreen middle rows remain unread');
  await page.evaluate(()=>navigate('socialSettings'));await page.getByText('清空选择',{exact:true}).click();await page.getByRole('button',{name:'保存设置',exact:true}).click();
  await page.waitForFunction(()=>calls.some(q=>q.action==='socialSettingsSave'));const saved=await page.evaluate(()=>calls.filter(q=>q.action==='socialSettingsSave').at(-1).args);assert.deepEqual(saved.characters,[]);assert.equal(saved.allCharacters,false);assert.equal(saved.revision,1);
  await page.getByText('今日安排与请求记录',{exact:true}).click();const paidBefore=await page.evaluate(()=>calls.filter(q=>['socialGenerate','socialRetry','weatherRefresh','socialCheck'].includes(q.action)).length);await page.getByRole('button',{name:'检查本地资料',exact:true}).click();await page.getByText('准备地点字段不完整，未请求模型',{exact:false}).waitFor();assert.equal(await page.evaluate(()=>calls.filter(q=>['socialGenerate','socialRetry','weatherRefresh','socialCheck'].includes(q.action)).length),paidBefore);

  await page.getByRole('button',{name:'请她发一条动态',exact:true}).click();await page.getByRole('dialog').waitFor();assert(!(await page.evaluate(()=>calls.some(q=>q.action==='socialGenerate'))));await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();
  await page.evaluate(()=>navigate('weatherSettings'));await page.getByLabel('和风 API Host').waitFor();assert.equal(await page.getByLabel('Ed25519 PKCS8 私钥（留空保留）').inputValue(),'');assert(!(await page.evaluate(()=>calls.some(q=>q.action==='weatherRefresh'))),'viewing settings never fetches weather');
  await page.getByLabel('Ed25519 PKCS8 私钥（留空保留）').fill('not-a-real-key-fixture');await page.getByRole('button',{name:'保存设置',exact:true}).click();await page.waitForFunction(()=>calls.some(q=>q.action==='weatherSave'));await page.getByLabel('Ed25519 PKCS8 私钥（留空保留）').waitFor();assert.equal(await page.getByLabel('Ed25519 PKCS8 私钥（留空保留）').inputValue(),'');assert(!(await page.locator('body').textContent()).includes('not-a-real-key-fixture'));
  await page.evaluate(()=>navigate('citySettings'));await page.getByLabel('搜索城市名').fill('上海');await page.evaluate(()=>cityWait=true);await page.getByRole('button',{name:'搜索',exact:true}).click({noWaitAfter:true});
  await page.waitForFunction(()=>!!window.resolveCity);await page.getByLabel('搜索城市名').fill('东京');await page.evaluate(()=>resolveCity());await page.waitForTimeout(100);assert.equal(await page.locator('.city-result').count(),0,'stale search does not repopulate choices');
  await page.evaluate(()=>cityWait=false);await page.getByLabel('搜索城市名').fill('上海');await page.getByRole('button',{name:'搜索',exact:true}).click();await page.getByRole('button',{name:'设为所在城市',exact:true}).click();await page.waitForFunction(()=>calls.some(q=>q.action==='citySave'));const city=await page.evaluate(()=>calls.find(q=>q.action==='citySave').args.city);assert.equal(city.latitude,31.23);assert.equal(city.longitude,121.47);
  await page.getByRole('button',{name:'使用当前位置查找',exact:true}).click({noWaitAfter:true});await page.waitForFunction(()=>!!window.locateId);await page.evaluate(()=>navigate('weatherSettings'));await page.getByRole('heading',{name:'天气与城市服务',exact:true}).waitFor();await page.evaluate(()=>resolveLocation());await page.waitForTimeout(100);assert.equal(await page.getByRole('heading',{name:'天气与城市服务',exact:true}).count(),1);
  await page.evaluate(()=>navigate('profile'));await page.getByLabel('称呼',{exact:true}).fill('未保存的称呼');await page.getByLabel('补充介绍（可选）').fill('未保存的介绍');await page.locator('.city-profile-choice').click();await page.getByRole('heading',{name:'所在城市',exact:true}).waitFor();await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('heading',{name:'个人档案',exact:true}).waitFor();assert.equal(await page.getByLabel('称呼',{exact:true}).inputValue(),'未保存的称呼');assert.equal(await page.getByLabel('补充介绍（可选）').inputValue(),'未保存的介绍');
  await page.waitForFunction(()=>!document.querySelector('#toast').classList.contains('show'));
  for(const route of ['social','socialSettings','weatherSettings','citySettings']){
   await page.evaluate(route=>navigate(route),route);await page.waitForTimeout(100);if(route==='socialSettings')await page.getByText('今日安排与请求记录',{exact:true}).click();
   for(const size of [{width:412,height:915},{width:915,height:412},{width:320,height:740}]){await page.setViewportSize(size);assert(!(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)),route+' fits '+size.width);if(route==='socialSettings'){const row=page.locator('.social-task').first(),meta=await row.locator('.social-task-meta').boundingBox(),button=await row.getByRole('button',{name:'手动重试',exact:true}).boundingBox();assert(button.y>=meta.y+meta.height,'retry button is below the metadata at '+size.width);}}
   await page.setViewportSize({width:412,height:915});if(route==='socialSettings'){await page.locator('.social-task').first().scrollIntoViewIfNeeded();await page.screenshot({path:path.join(__dirname,'social-diagnostics.png')});}await page.evaluate(()=>document.querySelector('.content').scrollTop=0);await page.screenshot({path:path.join(__dirname,route+'.png')});
  }
  await page.evaluate(()=>{applyTheme({dark:true});navigate('social');});await page.locator('.social-post').first().waitFor();await page.evaluate(()=>document.querySelector('.content').scrollTop=0);await page.screenshot({path:path.join(__dirname,'social-dark.png')});
  assert.deepEqual(errors,[]);assert(!(await page.evaluate(()=>calls.some(q=>['socialGenerate','send','writingGenerate','scheduleGenerate','judgeTest'].includes(q.action)))),'no model action occurred');
  console.log('PASS actual social/settings/weather/city UI: text safety, visible-ID reads, overlay guard, like/comment, pagination, empty role selection, paid confirmation cancel, credential non-echo, no implicit weather fetch, stale city response, explicit city save, cancelled location, 4 pages/3 sizes/dark. Fake native only; no device/provider request.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
