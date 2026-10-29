const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../assets'),characters=JSON.parse(fs.readFileSync(path.join(root,'characters.json'),'utf8')).map(c=>({...c,lastActivity:0,preview:''})),who=characters[0].id;
 const data={characters,personal:{producer:{},affinity:{},memories:[]},chatAppearance:{},writingOptions:{letters:false,diary:false},writings:[
  {kind:'letter',character:who,date:'2026-10-23',text:'<ja>おはよう</ja><zh>早晨的来信</zh>',read:false},
  {kind:'letter',character:who,date:'2026-09-20',text:'<zh>九月的旧信</zh>',read:true},
  {kind:'diary',character:who,date:'2026-10-22',text:'旧快照私密全文',read:false}],reachOptions:{}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}});page.setDefaultTimeout(10000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{const url=new URL(route.request().url());if(url.origin!=='https://eidolon.local')return route.abort();await route.fulfill({path:path.join(root,url.pathname)});});
  await page.addInitScript(data=>{window.testAddArrivedLetter=()=>data.writings.push({kind:'letter',character:data.characters[0].id,date:'2026-10-21',text:'后台送达的来信',read:false});window.testRemoveArrivedLetter=()=>{data.writings=data.writings.filter(r=>r.date!=='2026-10-21');};window.testCalls=[];window.Native={post(raw){const q=JSON.parse(raw);window.testCalls.push(q.action);setTimeout(()=>{let result=data;if(q.action==='writingSettings'){data.writingOptions.letters=q.args.letters;data.writingOptions.diary=q.args.diary;}if(q.action==='themeGet')result={dark:false};if(q.action==='voiceStatus')result={state:'idle',text:''};if(q.action==='scheduleStatus')result={enabled:false};if(q.action==='writingRead'){const row=data.writings.find(e=>e.kind===q.args.kind&&e.character===q.args.character&&e.date===q.args.date);row.read=q.args.read!==false;if(row.kind==='diary'){row.text='仅摘要';row.excerpt=true;}}window.nativeReply(q.id,structuredClone(result),null);},0);}};},data);
  await page.goto('https://eidolon.local/ui/index.html');await page.getByRole('button',{name:'设置',exact:true}).click();await page.getByRole('button',{name:/^生活日常/}).click();await page.getByRole('button',{name:/她的来信/}).click();
  await page.locator('.writing-card').first().waitFor();assert.equal(await page.locator('.writing-card').count(),2);
  await page.evaluate(async()=>{testAddArrivedLetter();await nativeEvent('writingsRefresh','');});assert.equal(await page.locator('.writing-card').count(),3);assert((await page.locator('.writing-results').textContent()).includes('后台送达的来信'));await page.evaluate(async()=>{testRemoveArrivedLetter();await nativeEvent('writingsRefresh','');});assert.equal(await page.locator('.writing-card').count(),2);
  await page.getByLabel('记录月份').click();await page.getByRole('button',{name:'2026 年 09 月',exact:true}).click();assert.equal(await page.locator('.writing-card').count(),1);assert((await page.locator('.writing-card').textContent()).includes('九月'));
  await page.getByLabel('记录月份').click();await page.getByRole('button',{name:'全部月份',exact:true}).click();await page.getByLabel('搜索可见内容').fill('早晨');assert.equal(await page.locator('.writing-card').count(),1);
  await page.getByLabel('搜索可见内容').fill('');await page.getByLabel('只看未读').check();assert.equal(await page.locator('.writing-card').count(),1);
  await page.locator('.writing-card').click();await page.locator('dialog[open]').waitFor();assert.equal(await page.locator('.writing-card').count(),0);
  await page.getByRole('button',{name:'标为未读并关闭',exact:true}).click();await page.locator('.writing-card').waitFor();assert.equal(await page.locator('.writing-card').count(),1);
  await page.getByRole('button',{name:'自动写作设置',exact:true}).click();await page.getByRole('heading',{name:'她的日子 · 写作设置',exact:true}).waitFor();
  const settingsText=await page.locator('#app>.content').textContent();assert(settingsText.includes('零点送达'));assert(settingsText.includes('不提前显示或计为未读'));assert(settingsText.includes('最多三次'));assert(settingsText.includes('已暂存的信仍保留'));
  await page.getByLabel('自动来信',{exact:true}).check();await page.getByRole('button',{name:'保存设置',exact:true}).click();await page.getByLabel('自动来信',{exact:true}).waitFor();assert(await page.getByLabel('自动来信',{exact:true}).isChecked());assert(!(await page.evaluate(()=>testCalls.some(x=>['writingGenerate','send','give'].includes(x)))));
  await page.screenshot({path:path.resolve(__dirname,'../../ui-0.9.19-writing-settings.png')});
  await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('heading',{name:'她的来信',exact:true}).waitFor();
  await page.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('button',{name:/她的日记/}).click();await page.locator('.writing-card').click();await page.locator('dialog[open]').waitFor();assert((await page.locator('.writing-reader').textContent()).includes('仅摘要'));assert(!(await page.locator('.writing-reader').textContent()).includes('旧快照私密全文'));
  assert.deepEqual(errors,[]);console.log('PASS writing month/search/unread filters; read/unread bridge; refreshed diary visibility; letter timing settings persist without generation; silent resume picks up background arrival; no API or device.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
