const fs=require('fs'),path=require('path'),assert=require('assert');
const project=path.resolve(__dirname,'../../..');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=project+'/outputs/eidolon-android/assets',characters=JSON.parse(fs.readFileSync(root+'/characters.json','utf8')).slice(0,2).map(c=>({...c,lastActivity:0,preview:''}));
 const data={characters,personal:{producer:{timeZone:'Asia/Shanghai'},affinity:{},memories:[]},config:{},features:{},writings:[],reachOptions:{},replyOptions:{},unread:{total:0,characters:{}}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{const page=await browser.newPage({viewport:{width:412,height:880}}),errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>{const u=new URL(r.request().url());return u.origin==='https://eidolon.local'?r.fulfill({path:path.join(root,u.pathname)}):r.abort();});
  await page.addInitScript(data=>{window.calls=[];window.phase='previous';window.checkpoint=0;window.previousPlan={wake:'08:00',sleep:'01:30',slots:[{start:'19:00',end:'20:00',activity:'前一日的练习安排',place:'事务所'}],twists:[]};window.Native={post(raw){const q=JSON.parse(raw);calls.push(q);let out=data;
   if(q.action==='themeGet')out={dark:false};if(q.action==='voiceStatus')out={state:'idle'};if(q.action==='scheduleStatus')out={enabled:false};
   if(q.action==='scheduleGet'){
    if(!checkpoint)checkpoint=Date.now()+750;const previous=phase==='previous'&&Date.now()<checkpoint;
    out={enabled:true,restEnabled:true,character:q.args.character,date:previous?'2026-10-28':'2026-10-29',timeZone:'Asia/Shanghai',text:'准备睡了',showingPrevious:previous,now:Date.now(),nextDisplayAt:previous?checkpoint:Date.now()+86400000,plan:previous?previousPlan:phase==='ready'?{wake:'08:00',sleep:'23:30',slots:[{start:'09:00',end:'10:00',activity:'新一天的安排'}],twists:[]}:null};
   }
   setTimeout(()=>nativeReply(q.id,structuredClone(out),null),0);
  }};},data);
  await page.goto('https://eidolon.local/ui/index.html');await page.locator('.roster-row').first().waitFor();await page.evaluate(()=>navigate('schedule'));await page.getByText('前一日的练习安排',{exact:true}).waitFor();assert((await page.locator('.schedule-details').textContent()).includes('03:00 前继续显示上一日安排'));
  await page.getByText('今天还没有生成行程。',{exact:true}).waitFor();assert.equal(await page.getByText('前一日的练习安排',{exact:true}).count(),0,'03:00 timer refreshes view without navigation');assert((await page.locator('.schedule-details').textContent()).includes('2026-10-29'));assert.equal(await page.evaluate(()=>previousPlan.slots[0].activity),'前一日的练习安排','previous data was not deleted');
  await page.evaluate(()=>{phase='ready';render();});await page.getByText('新一天的安排',{exact:true}).waitFor();await page.evaluate(()=>applyTheme({dark:true}));await page.setViewportSize({width:320,height:740});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.resolve(__dirname,'../../ui-1.11-schedule.png')});
  assert.deepEqual(errors,[]);assert(!(await page.evaluate(()=>calls.some(q=>['scheduleGenerate','writingGenerate','send','judgeTest'].includes(q.action)))));console.log('PASS mock schedule UI: previous day retained before checkpoint, automatic boundary refresh, current day awaiting generation, old data retained, new plan display, narrow/dark; no model or Android scheduler execution.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
