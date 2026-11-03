const fs=require('fs'),path=require('path'),assert=require('assert');
const base=process.env.EIDOLON_WORKSPACE||path.resolve(__dirname,'../../..');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../assets');
 const characters=JSON.parse(fs.readFileSync(root+'/characters.json','utf8')).slice(0,2).map(c=>({...c,lastActivity:0,preview:''}));
 const snapshot={characters,personal:{producer:{timeZone:'Asia/Shanghai'},affinity:{},memories:[]},config:{},features:{},writings:[],reachOptions:{},replyOptions:{},unread:{total:0,characters:{}}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>{const u=new URL(r.request().url());return u.origin==='https://eidolon.local'?r.fulfill({path:path.join(root,u.pathname)}):r.abort();});
  await page.addInitScript(snapshot=>{
   window.calls=[];window.fixture='format';
   window.Native={post(raw){const q=JSON.parse(raw);calls.push(q);let out=snapshot,error=null;
    if(q.action==='themeGet')out={dark:false};if(q.action==='voiceStatus')out={state:'idle'};if(q.action==='scheduleStatus')out={enabled:false};
    if(q.action==='scheduleGet'){
     if(fixture==='readError')error='数据库暂时忙碌';
     out={enabled:true,restEnabled:true,character:q.args.character,date:'2026-11-03',timeZone:'Asia/Shanghai',text:'今天的行程尚未安排',plan:null,error:fixture==='format'?'第2段开始时间无效（25:00），应为 HH:mm；例如 07:00。未自动重试。':''};
     if(fixture==='previous'){out.showingPrevious=true;out.error='行程时间应为 HH:mm';}
    }
    setTimeout(()=>nativeReply(q.id,structuredClone(out),error),0);
   }};
  },snapshot);
  await page.goto('https://eidolon.local/ui/index.html');await page.locator('.roster-row').first().waitFor();
  await page.evaluate(()=>navigate('schedule'));
  await page.getByText('这一天的行程生成失败',{exact:true}).waitFor();
  assert((await page.locator('.schedule-details .danger').textContent()).includes('第2段开始'));
  assert((await page.locator('.schedule-details').textContent()).includes('当天不会自动重复请求'));
  await page.getByText('重新安排行程',{exact:true}).click();await page.getByRole('dialog').waitFor();
  assert((await page.getByRole('dialog').textContent()).includes('会调用后台模型'));
  assert(!(await page.evaluate(()=>calls.some(q=>q.action==='scheduleGenerate'))));
  await page.getByRole('button',{name:'取消',exact:true}).click();
  await page.evaluate(()=>{fixture='previous';render();});
  await page.getByText('重新安排行程',{exact:true}).waitFor();assert(await page.getByText('重新安排行程',{exact:true}).isDisabled());
  await page.evaluate(()=>{fixture='readError';render();});await page.getByText('重新读取',{exact:true}).waitFor();
  await page.evaluate(()=>fixture='empty');await page.getByText('重新读取',{exact:true}).click();
  await page.getByText('今天还没有生成行程。',{exact:true}).waitFor();
  await page.evaluate(()=>{fixture='format';render();});await page.getByText('这一天的行程生成失败',{exact:true}).waitFor();
  for(const viewport of [{width:412,height:915},{width:915,height:412},{width:320,height:740}]){
   await page.setViewportSize(viewport);assert(!(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)));
  }
  await page.evaluate(()=>applyTheme({dark:true}));await page.screenshot({path:path.join(process.env.EIDOLON_TEST_OUTPUT||__dirname,'schedule-error-dark.png')});
  assert.deepEqual(errors,[]);assert(!(await page.evaluate(()=>calls.some(q=>['scheduleGenerate','writingGenerate','send','judgeTest'].includes(q.action)))));
  console.log('PASS actual schedule UI/fake bridge: precise failure, paid retry confirmation/cancel, previous-day guard, read retry, empty state, 3 layouts, dark; no model or phone.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
