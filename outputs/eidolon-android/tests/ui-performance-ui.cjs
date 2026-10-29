const fs=require('fs'),path=require('path'),assert=require('assert');
const project=path.resolve(__dirname,'../../..');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=project+'/outputs/eidolon-android/assets',characters=JSON.parse(fs.readFileSync(root+'/characters.json','utf8')).map(c=>({...c,lastActivity:0,preview:''}));
 const data={characters,personal:{producer:{timeZone:'Asia/Shanghai'},profile:'',affinity:{},memories:[],heartScenes:{enabled:false}},config:{url:'https://example.invalid',model:'local-fixture',hasKey:false},features:{autoMemory:true,affinity:true,hibiHearts:true,story:true},chatAppearance:{},replyOptions:{},reachOptions:{},unread:{total:0,characters:{}},writings:[],writingUnread:{letter:0,diary:0},initialRoute:'roster'};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:860}}),errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>{const u=new URL(r.request().url());return u.origin==='https://eidolon.local'?r.fulfill({path:path.join(root,u.pathname)}):r.abort();});
  await page.addInitScript(data=>{
   window.fixture=data;window.calls=[];window.delay=350;window.failedRead=false;
   window.Native={post(raw){const q=JSON.parse(raw);calls.push(q);let out={},error=null,wait=0;
    if(q.action==='snapshot'){
     wait=window.delay;out=q.args.page==='bootstrap'?{...data,_uiPatch:true}:{_uiPatch:true,personal:data.personal,features:data.features,unread:data.unread,writingUnread:data.writingUnread};
     if(q.args.page==='stats')out.stats={turns:12,characters:2,sessions:3,words:120};
     if(window.failedRead){error='模拟读取失败';window.failedRead=false;}
    }
    if(q.action==='features'){data.features={...data.features,...q.args};out={_uiPatch:true,features:data.features};}
    if(q.action==='themeGet')out={dark:false};if(q.action==='voiceStatus')out={state:'idle'};if(q.action==='scheduleStatus')out={enabled:false};
    setTimeout(()=>nativeReply(q.id,structuredClone(out),error),wait);
   }};
  },data);
  await page.goto('https://eidolon.local/ui/index.html');await page.locator('.roster-row').first().waitFor();
  const before=await page.evaluate(()=>calls.filter(q=>q.action==='snapshot').length);
  await page.getByRole('button',{name:'设置',exact:true}).click();await page.getByRole('button',{name:/^聊天与关系/}).click();
  assert.equal(await page.evaluate(()=>calls.filter(q=>q.action==='snapshot').length),before,'directories perform zero snapshot reads');
  await page.getByRole('button',{name:/^记忆与关系设置/}).click();await page.getByRole('heading',{name:'正在打开…',exact:true}).waitFor();
  await page.getByRole('button',{name:'设置',exact:true}).click();await page.waitForTimeout(450);assert(await page.getByRole('heading',{name:'设置',exact:true}).isVisible(),'late leaf response cannot reopen a page');
  await page.getByRole('button',{name:/^聊天与关系/}).click();await page.getByRole('button',{name:/^记忆与关系设置/}).click();await page.getByRole('checkbox',{name:'自动提取用户长期记忆',exact:true}).waitFor();
  await page.getByRole('checkbox',{name:'自动提取用户长期记忆',exact:true}).uncheck();
  const count=await page.evaluate(()=>calls.filter(q=>q.action==='snapshot').length);
  await page.evaluate(()=>Promise.all([nativeEvent('inboxRefresh',''),nativeEvent('writingsRefresh',''),nativeEvent('inboxRefresh','')]));
  assert.equal(await page.evaluate(()=>calls.filter(q=>q.action==='snapshot').length),count+1,'resume refreshes coalesce');
  assert.equal(await page.getByRole('checkbox',{name:'自动提取用户长期记忆',exact:true}).isChecked(),false,'background refresh preserves unsaved checkbox');
  assert.equal(await page.evaluate(()=>calls.filter(q=>q.action==='snapshot').at(-1).args.page),'badges');
  await page.getByRole('button',{name:'保存设置',exact:true}).click();assert.equal(await page.evaluate(()=>state.features.autoMemory),false,'partial mutation reply merges immediately');assert.equal(await page.evaluate(()=>state.characters.length),characters.length,'partial reply preserves catalog');
  await page.getByRole('button',{name:'设置',exact:true}).click();await page.getByRole('button',{name:/^数据与应用/}).click();await page.evaluate(()=>failedRead=true);await page.getByRole('button',{name:/^使用统计/}).click();await page.getByRole('button',{name:'重新读取',exact:true}).waitFor();await page.getByRole('button',{name:'重新读取',exact:true}).click();await page.getByRole('heading',{name:'使用统计',exact:true}).waitFor();assert((await page.locator('.stats').textContent()).includes('12'));
  const timings=await page.evaluate(()=>uiPerformance.filter(e=>e.stage==='navigate'));assert(timings.some(t=>t.detail.startsWith('settings')));assert(timings.filter(t=>t.detail.startsWith('settings')).every(t=>t.ms<350),'directories are not gated by the mocked 350ms reader');
  await page.getByRole('button',{name:'设置',exact:true}).click();await page.evaluate(()=>applyTheme({dark:true}));await page.setViewportSize({width:320,height:740});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.resolve(__dirname,'../../ui-1.8-settings.png')});
  assert.deepEqual(errors,[]);assert(!(await page.evaluate(()=>calls.some(q=>['send','judgeTest','writingGenerate','modelList'].includes(q.action)))));
  console.log('PASS mock performance UI: zero-read directories, 350ms delayed leaf, cancelled navigation, merged refreshes, unsaved input preserved, partial save merge, read failure/retry; timings '+JSON.stringify(timings)+'. Host/mock only, not phone timing.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
