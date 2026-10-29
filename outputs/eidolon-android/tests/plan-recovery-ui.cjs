const fs=require('fs'),path=require('path'),assert=require('assert');
const project=path.resolve(__dirname,'../../..');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=project+'/outputs/eidolon-android/assets',characters=JSON.parse(fs.readFileSync(root+'/characters.json','utf8')).slice(0,3).map(c=>({...c,lastActivity:0,preview:''}));
 const data={characters,personal:{producer:{},affinity:{},memories:[]},config:{},reachOptions:{},replyOptions:{},writings:[],unread:{total:0,characters:{}},features:{}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:880}}),errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>{const u=new URL(r.request().url());return u.origin==='https://eidolon.local'?r.fulfill({path:path.join(root,u.pathname)}):r.abort();});
  await page.addInitScript(data=>{
   window.calls=[];window.testMode='normal';window.modelCalls=0;window.planFixture={enabled:true,date:'2026-10-27',zone:'Asia/Shanghai',tasks:[],assessments:[{character:data.characters[0].id,reason:'本次关系或固定间隔尚未结束，最早明天再检查'},{character:data.characters[1].id,reason:'有可用时段，但尚未入队；请刷新计划查看检查结果'}],checkedAt:1793064480000,checkResult:'本地检查完成，当前 0 项安排',nextWake:1793029200000,wakeOverdue:true};
   window.Native={post(raw){const q=JSON.parse(raw);calls.push(q);let out=data,error=null,delay=0;
    if(q.action==='themeGet')out={dark:false};if(q.action==='voiceStatus')out={state:'idle'};if(q.action==='scheduleStatus')out={enabled:false};
    if(['reachPlanCheck','reachReplan','reachPlans'].includes(q.action)){
     delay=q.action==='reachPlans'?0:300;
     if(window.testMode==='fail'&&q.action==='reachReplan')error='本地排程未完成，请重试或查看最近检查结果（SQLiteException）';
     else if(q.action==='reachReplan'){
      planFixture.tasks=[{id:'scheduled',character:data.characters[1].id,due:1793073600000,expires:1793079000000,status:'PENDING',attempts:0,error:''}];
      planFixture.nextWake=1793065380000;planFixture.wakeOverdue=false;planFixture.checkResult='本地检查完成，当前 1 项安排';planFixture.assessments[1].reason='已有一条待执行或生成中的计划';
     }
     out=structuredClone(planFixture);
    }
    if(['send','judgeTest','writingGenerate','reachTaskRetry','modelList'].includes(q.action))modelCalls++;
    setTimeout(()=>nativeReply(q.id,structuredClone(out),error),delay);
   }};
  },data);
  await page.goto('https://eidolon.local/ui/index.html');await page.locator('.roster-row').first().waitFor();await page.evaluate(()=>navigate('reachPlans'));
  await page.locator('.reach-plan-progress').waitFor();assert(await page.getByRole('button',{name:'刷新计划',exact:true}).isDisabled());
  await page.locator('.plan-summary').waitFor();assert((await page.locator('.plan-summary').textContent()).includes('0 位角色'));assert((await page.locator('.reach-check-status').textContent()).includes('上次约定检查时间已过'));
  await page.locator('.reach-assessments>summary').click();assert((await page.locator('.reach-assessment-list').textContent()).includes('间隔尚未结束'));
  await page.getByRole('button',{name:'重新安排未执行计划',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await page.locator('.reach-plan-progress').waitFor();assert((await page.locator('.reach-plan-progress').textContent()).includes('正在重新安排'));assert.equal(await page.locator('.reach-task:visible').count(),0);
  await page.locator('.reach-task:visible').waitFor();assert((await page.locator('.plan-summary').textContent()).includes('1 位角色'));assert((await page.locator('.reach-check-status').textContent()).includes('当前 1 项安排'));assert(!(await page.locator('.reach-check-status').textContent()).includes('上次约定检查时间已过'));assert.equal(await page.evaluate(()=>calls.filter(q=>q.action==='reachReplan').length),1);
  await page.evaluate(()=>testMode='fail');await page.getByRole('button',{name:'重新安排未执行计划',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await page.locator('.reach-plan-error').waitFor();assert((await page.locator('.reach-plan-error').textContent()).includes('SQLiteException'));assert.equal(await page.locator('.reach-task:visible').count(),1);assert(await page.getByRole('button',{name:'刷新计划',exact:true}).isEnabled());
  await page.getByRole('button',{name:'重新安排未执行计划',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await page.locator('.reach-plan-error').waitFor();assert.equal(await page.locator('.reach-plan-error').count(),1,'repeated failure does not stack error messages');
  await page.evaluate(()=>testMode='normal');await page.getByRole('button',{name:'刷新计划',exact:true}).click();await page.locator('.reach-plan-error').waitFor({state:'detached'});
  await page.evaluate(()=>{navigate('roster').then(()=>navigate('reachPlans')).then(()=>navigate('roster')).then(()=>navigate('reachPlans'));});
  await page.locator('.plan-summary').waitFor();assert.equal(await page.locator('.reach-plan-progress').count(),0);
  await page.evaluate(()=>applyTheme({dark:true}));await page.setViewportSize({width:320,height:740});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.resolve(__dirname,'ui-1.7-plan-recovery.png')});
  assert.equal(await page.evaluate(()=>modelCalls),0);assert.deepEqual(errors,[]);console.log('PASS mock planner UI: stale wake, precise empty-day reason, wait for completed replan, result update, visible failure/retry, navigation during checks, narrow/dark layout; no model calls or Android scheduler execution.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
