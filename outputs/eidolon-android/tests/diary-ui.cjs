const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../assets'),characters=JSON.parse(fs.readFileSync(path.join(root,'characters.json'),'utf8')).map(c=>({...c,lastActivity:0,preview:''})),who=characters[0].id;
 const data={characters,personal:{producer:{},affinity:{[who]:65},memories:[]},features:{affinity:true},reachOptions:{},chatAppearance:{},writings:[{kind:'diary',character:who,date:'2026-10-23',text:'<ja>今日のこと。</ja><zh>可读开头。正文后段仍然是公开的日记。</zh>',read:false,hasHiddenPage:true}]};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',async route=>{const u=new URL(route.request().url());if(u.origin!=='https://eidolon.local')return route.abort();await route.fulfill({path:path.join(root,u.pathname)});});
 await page.addInitScript(data=>{
  const full=data.writings[0].text,secret='<ja>内緒の気持ち。</ja><zh>藏起的心事仅在点击后出现。</zh>';let secretRead=false;
  window.testSecretDelay=0;window.testSecretReplies=0;window.testCalls=[];window.testVisibility=mode=>{const row=data.writings[0];for(const k of ['locked','excerpt','hasHiddenPage','hiddenAvailable','hiddenUnread'])delete row[k];row.text=full;row.shown='full';
   if(mode==='intimate'){row.shown='secret';row.hasHiddenPage=true;row.hiddenAvailable=true;row.hiddenUnread=!secretRead;}
   if(mode==='close')row.hasHiddenPage=true;
   if(mode==='opening'){row.text='可读开头…';row.excerpt=true;row.shown='opening';}
   if(mode==='closed'){row.text='';row.locked=true;row.shown='closed';}
  };
  window.Native={post(raw){const q=JSON.parse(raw);testCalls.push(q.action);setTimeout(()=>{let result=data,error=null;
   if(q.action==='themeGet')result={dark:false};if(q.action==='voiceStatus')result={state:'idle'};if(q.action==='scheduleStatus')result={enabled:false};
   if(q.action==='writingRead'){data.writings[0].read=q.args.read!==false;if(!data.writings[0].read){secretRead=false;if(data.writings[0].hiddenAvailable)data.writings[0].hiddenUnread=true;}}
   if(q.action==='diarySecret'){if(!data.writings[0].hiddenAvailable)error='目前不能翻开隐藏页';else{secretRead=true;data.writings[0].hiddenUnread=false;data.writings[0].read=true;result={text:secret,snapshot:data};}}
   nativeReply(q.id,structuredClone(result),error);if(q.action==='diarySecret')window.testSecretReplies++;
  },q.action==='diarySecret'?window.testSecretDelay:0);}};
 },data);
 await page.goto('https://eidolon.local/ui/index.html');await page.getByRole('button',{name:'设置',exact:true}).click();await page.getByRole('button',{name:/^生活日常/}).click();await page.getByRole('button',{name:/^她的日记/}).click();await page.locator('.writing-card').waitFor();
 assert(!(await page.evaluate(()=>JSON.stringify(state))).includes('藏起的心事'));assert((await page.locator('.writing-card').textContent()).includes('亲密时开放'));
 await page.locator('.writing-card').click();await page.locator('dialog[open] .writing-reader').waitFor();assert((await page.locator('#modal').textContent()).includes('她还藏着一页'));assert.equal(await page.getByRole('button',{name:'翻开隐藏页',exact:true}).count(),0);await page.getByRole('button',{name:'关闭',exact:true}).click();
 await page.evaluate(async()=>{testVisibility('intimate');await nativeEvent('writingsRefresh','');});await page.getByLabel('只看未读').check();assert.equal(await page.locator('.writing-card').count(),1);
 await page.getByLabel('搜索可见内容').fill('藏起的心事');assert.equal(await page.locator('.writing-card').count(),0);await page.getByLabel('搜索可见内容').fill('');
 await page.locator('.writing-card').click();await page.locator('dialog[open] .writing-reader').waitFor();await page.getByRole('button',{name:'翻开隐藏页',exact:true}).waitFor();assert((await page.evaluate(()=>testCalls.filter(x=>x==='diarySecret').length))===0);
 await page.getByRole('button',{name:'翻开隐藏页',exact:true}).click();await page.locator('.secret-reader').waitFor();assert((await page.locator('.secret-reader').textContent()).includes('藏起的心事'));assert(!(await page.evaluate(()=>JSON.stringify(state))).includes('藏起的心事'));assert.equal(await page.locator('.writing-card').count(),0);
 await page.screenshot({path:path.resolve(__dirname,'../../ui-0.9.20-diary-secret.png')});
 await page.evaluate(async()=>{testVisibility('close');await nativeEvent('writingsRefresh','');});assert.equal(await page.locator('.secret-reader').count(),0);assert(!(await page.locator('#modal').textContent()).includes('藏起的心事'));assert((await page.locator('#modal').textContent()).includes('暂时合上'));
 await page.getByRole('button',{name:'关闭',exact:true}).click();await page.getByLabel('只看未读').uncheck();await page.locator('.writing-card').click();await page.locator('dialog[open] .writing-reader').waitFor();assert((await page.locator('.writing-reader').textContent()).includes('正文后段'));
 await page.evaluate(async()=>{testVisibility('opening');await nativeEvent('writingsRefresh','');});assert(!(await page.locator('.writing-reader').textContent()).includes('正文后段'));assert((await page.locator('.writing-reader').textContent()).includes('可读开头'));
 await page.evaluate(async()=>{testVisibility('closed');await nativeEvent('writingsRefresh','');});assert.equal(await page.locator('.writing-reader').count(),0);await page.getByRole('button',{name:'关闭',exact:true}).click();
 await page.evaluate(async()=>{testVisibility('off');await nativeEvent('writingsRefresh','');});await page.locator('.writing-card').click();await page.locator('dialog[open] .writing-reader').waitFor();assert.equal(await page.getByRole('button',{name:'翻开隐藏页',exact:true}).count(),0);assert(!(await page.locator('#modal').textContent()).includes('还藏着一页'));
 const denied=await page.evaluate(async()=>{try{await api('diarySecret',{character:state.characters[0].id,date:'2026-10-23'});return false;}catch(_){return true;}});assert(denied);
 await page.getByRole('button',{name:'关闭',exact:true}).click();await page.evaluate(async()=>{testVisibility('intimate');testSecretDelay=500;await nativeEvent('writingsRefresh','');});
 await page.locator('.writing-card').click();await page.locator('dialog[open] .writing-reader').waitFor();const replies=await page.evaluate(()=>testSecretReplies);await page.getByRole('button',{name:'翻开隐藏页',exact:true}).click();await page.getByRole('button',{name:'关闭',exact:true}).click();await page.waitForFunction(()=>writingReader===null);await page.locator('.writing-card').click();await page.locator('dialog[open] .writing-reader').waitFor();await page.waitForFunction(n=>testSecretReplies>n,replies);assert.equal(await page.locator('.secret-reader').count(),0);assert(!(await page.locator('#modal').textContent()).includes('藏起的心事'));assert((await page.locator('.writing-reader').textContent()).includes('可读开头'));
 assert.deepEqual(errors,[]);assert(!(await page.evaluate(()=>testCalls.some(x=>['writingGenerate','send','give'].includes(x)))));
 console.log('PASS private-page hints/access, explicit native fetch, hidden unread/search isolation, live relock, body downgrade and stale private-fetch fencing; no API or device.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
