const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../assets'),characters=JSON.parse(fs.readFileSync(path.join(root,'characters.json'),'utf8')).map(c=>({...c,lastActivity:1,preview:''})),who=characters[0].id;
 const data={characters,current:who,session:'test',sessionState:{},personal:{producer:{},affinity:{[who]:78},memories:[]},config:{},gifts:[],messages:[{id:1,role:'user',text:'我刚才只是开个玩笑。'},{id:2,role:'assistant',text:'之前那句话让我有点难过。'}],appeal:{messageId:2,available:true,left:3},reachOptions:{}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});try{
  const page=await browser.newPage({viewport:{width:412,height:915}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{const u=new URL(route.request().url());if(u.origin!=='https://eidolon.local')return route.abort();await route.fulfill({path:path.join(root,u.pathname)});});
  await page.addInitScript(data=>{window.testCalls=[];window.Native={post(raw){const q=JSON.parse(raw);testCalls.push(q);setTimeout(()=>{let out=data;if(q.action==='themeGet')out={dark:false};if(q.action==='voiceStatus')out={state:'idle'};if(q.action==='scheduleStatus')out={enabled:false};if(q.action==='appealReply'){data.appeal={messageId:2,available:false,appealed:true,left:2};data.personal.affinity[data.current]=80;data.appealRestored=2;}window.nativeReply(q.id,structuredClone(out),null);},0);}};},data);
  await page.goto('https://eidolon.local/ui/index.html');await page.getByRole('button',{name:'设置',exact:true}).waitFor();await page.evaluate(()=>navigate('chat'));const button=page.getByRole('button',{name:'我不是这个意思',exact:true});await button.waitFor();assert.equal(await button.count(),1);
  await button.click();await page.getByRole('button',{name:'取消',exact:true}).click();assert.equal(await page.evaluate(()=>testCalls.filter(q=>q.action==='appealReply').length),0);
  await button.click();assert((await page.locator('dialog[open]').textContent()).includes('剩余 3 次'));await page.getByRole('button',{name:'确定',exact:true}).click();await page.getByText('已说明误会',{exact:true}).waitFor();assert.equal(await button.count(),0);
  assert.equal(await page.evaluate(()=>testCalls.filter(q=>q.action==='appealReply').length),1);assert.equal(await page.evaluate(()=>testCalls.find(q=>q.action==='appealReply').args.messageId),2);assert(!(await page.evaluate(()=>testCalls.some(q=>['send','give','writingGenerate','repairTranslation'].includes(q.action)))));
  await page.screenshot({path:path.resolve(__dirname,'../../ui-0.9.23-appeal.png')});assert.deepEqual(errors,[]);console.log('PASS latest-message appeal confirmation, cancel without mutation, exact message ID and once-only UI mark; mocked native bridge only.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
