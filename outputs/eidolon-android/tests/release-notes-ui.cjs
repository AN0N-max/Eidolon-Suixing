const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../assets'),notes=JSON.parse(fs.readFileSync(path.join(root,'update-notices.json'),'utf8'));
 const chars=JSON.parse(fs.readFileSync(path.join(root,'characters.json'),'utf8')).map(c=>({...c,lastActivity:0,preview:''}));
 const data={characters:chars,personal:{affinity:{},relations:{},promises:[],progress:{counts:{}},memories:[],giftsSent:0},stats:{turns:0,characters:0,sessions:0,words:0},features:{},writings:[],config:{},reachOptions:{}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const page=await browser.newPage({viewport:{width:375,height:812}}),errors=[],requests=[];page.setDefaultTimeout(8000);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{requests.push(route.request().url());const url=new URL(route.request().url());assert.equal(url.origin,'https://eidolon.local');await route.fulfill({path:path.join(root,url.pathname)});});
  await page.addInitScript(({data,notes})=>{
   window.testCalls=[];window.noteWait=false;window.noteFail=false;
   window.Native={post(raw){const q=JSON.parse(raw);testCalls.push(q.action);const send=()=>{let value=data,error=null;if(q.action==='themeGet')value={dark:false};if(q.action==='voiceStatus')value={state:'idle'};if(q.action==='scheduleStatus')value={enabled:false};if(q.action==='releaseNotes'){value=notes;if(window.noteFail)error='read failed';}window.nativeReply(q.id,structuredClone(value),error);};if(q.action==='releaseNotes'&&window.noteWait)window.resolveNotes=send;else setTimeout(send,0);}};
  },{data,notes});
  await page.goto('https://eidolon.local/ui/index.html');
  await page.getByRole('button',{name:'设置',exact:true}).click();await page.getByRole('button',{name:/^数据与应用/}).click();
  const rows=await page.locator('.setting').allTextContents();assert(rows.findIndex(t=>t.includes('更新公告'))<rows.findIndex(t=>t.includes('关于 Eidolon')));
  const snapshots=await page.evaluate(()=>testCalls.filter(a=>a==='snapshot').length);
  await page.getByRole('button',{name:/^更新公告/}).click();await page.locator('.release-note').first().waitFor();
  assert.equal(await page.locator('.release-note').count(),notes.releases.length);assert.equal(await page.locator('.release-note[open]').count(),1);
  assert((await page.locator('.release-note[open]').textContent()).includes(notes.releases[0].version));assert.equal(await page.evaluate(()=>testCalls.filter(a=>a==='snapshot').length),snapshots);
  await page.locator('.release-note').nth(1).locator('summary').click();assert.equal(await page.locator('.release-note[open]').count(),2);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.screenshot({path:path.resolve(__dirname,'../../ui-1.14.1-update-notices.png')});
  await page.evaluate(()=>document.documentElement.dataset.theme='dark');await page.screenshot({path:path.resolve(__dirname,'../../ui-1.14.1-update-notices-dark.png')});
  await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('heading',{name:'数据与应用',exact:true}).waitFor();
  // The bridge can finish after leaving. It must not replace another page.
  await page.evaluate(()=>{releaseNotesCache=null;window.noteWait=true;});await page.getByRole('button',{name:/^更新公告/}).click();await page.getByText('正在读取本机更新公告…',{exact:true}).waitFor();
  await page.getByRole('button',{name:'返回',exact:true}).click();await page.evaluate(()=>{window.noteWait=false;window.resolveNotes();});await page.getByRole('heading',{name:'数据与应用',exact:true}).waitFor();assert.equal(await page.locator('.release-note').count(),0);
  await page.evaluate(()=>window.noteFail=true);await page.getByRole('button',{name:/^更新公告/}).click();await page.getByRole('button',{name:'重新读取',exact:true}).waitFor();
  await page.evaluate(()=>window.noteFail=false);await page.getByRole('button',{name:'重新读取',exact:true}).click();await page.locator('.release-note').first().waitFor();
  // All release text goes through textContent, not HTML.
  await page.evaluate(()=>{releaseNotesCache.releases[0].sections[0].items.push('<img src=x onerror="window.injected=true">');render();});
  await page.getByText('<img src=x onerror="window.injected=true">',{exact:true}).waitFor();assert.equal(await page.locator('.release-note img').count(),0);assert.equal(await page.evaluate(()=>!!window.injected),false);
  await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:/^使用统计/}).click();await page.getByRole('heading',{name:'使用统计',exact:true}).waitFor();
  await page.getByRole('button',{name:'更新公告',exact:true}).click();await page.locator('.release-note').first().waitFor();await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('heading',{name:'使用统计',exact:true}).waitFor();
  assert.deepEqual(errors,[]);assert(!(await page.evaluate(()=>testCalls.some(a=>['send','give','features','writingGenerate'].includes(a)))));assert(requests.every(u=>u.startsWith('https://eidolon.local/')));
  console.log('PASS offline update notices: settings/statistics entry, version history, local-only read, navigation, late reply, failure/retry, text safety, narrow and dark layouts; no model call.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
