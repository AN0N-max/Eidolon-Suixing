const fs=require('fs'),path=require('path'),assert=require('assert');
const project=path.resolve(__dirname,'../../..');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=project+'/outputs/eidolon-android/assets',characters=JSON.parse(fs.readFileSync(root+'/characters.json','utf8')).slice(0,2).map(c=>({...c,lastActivity:0,preview:''}));
 const data={characters,personal:{producer:{},affinity:{},memories:[]},config:{},features:{},reachOptions:{},replyOptions:{},writings:[],unread:{total:3,characters:{[characters[0].id]:3}}};
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:880}}),errors=[];page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',r=>{const u=new URL(r.request().url());return u.origin==='https://eidolon.local'?r.fulfill({path:path.join(root,u.pathname)}):r.abort();});
  await page.addInitScript(data=>{window.calls=[];window.badge={unread:3,notifications:true,channelBadge:true,vivo:true,lastAt:0,submittedCount:-1,status:'尚未提交桌面更新'};window.Native={post(raw){const q=JSON.parse(raw);calls.push(q);let out=data;if(q.action==='themeGet')out={dark:false};if(q.action==='voiceStatus')out={state:'idle'};if(q.action==='scheduleStatus')out={enabled:false};if(q.action==='desktopBadgeStatus')out=badge;if(q.action==='desktopBadgeSync'){badge.lastAt=Date.now();badge.submittedCount=badge.notifications?badge.unread:0;badge.status='兼容更新请求已提交，是否显示由系统桌面决定';out=badge;}setTimeout(()=>nativeReply(q.id,structuredClone(out),null),0);}};},data);
  await page.goto('https://eidolon.local/ui/index.html');await page.locator('.roster-row').first().waitFor();await page.getByRole('button',{name:'设置',exact:true}).click();await page.getByRole('button',{name:/^提醒与后台/}).click();await page.getByRole('button',{name:/^桌面未读角标/}).click();await page.getByText('本机未读：3 条',{exact:true}).waitFor();
  assert((await page.locator('.content').textContent()).includes('Vivo／iQOO兼容模式'));await page.getByRole('button',{name:'按当前未读数同步',exact:true}).click();await page.waitForFunction(()=>badge.submittedCount===3);await page.getByText(/最近提交：/).waitFor();
  assert(!(await page.evaluate(()=>calls.some(q=>q.action==='chatRead'))),'badge inspection never marks messages read');assert((await page.locator('.content').textContent()).includes('是否显示由系统桌面决定'));
  await page.evaluate(()=>{badge.unread=0;return refreshDesktopBadge(true);});assert(await page.getByText('本机未读：0 条',{exact:true}).isVisible());assert.equal(await page.evaluate(()=>badge.submittedCount),0);
  await page.evaluate(()=>{badge.notifications=false;badge.channelBadge=false;return refreshDesktopBadge();});assert(await page.getByText('系统通知未允许，请到系统设置开启',{exact:true}).isVisible());await page.getByRole('button',{name:'打开通知与角标设置',exact:true}).click();assert.equal(await page.evaluate(()=>calls.filter(q=>q.action==='desktopBadgeSettings').length),1);
  await page.evaluate(()=>{badge.notifications=true;badge.channelBadge=false;return refreshDesktopBadge();});
  assert((await page.locator('.content').textContent()).includes('这不代表Vivo／iQOO桌面角标总开关已关闭'));
  assert(!(await page.locator('.content').textContent()).includes('角标已关闭，请'));
  await page.evaluate(()=>showReach({character:state.characters[0].id,name:'本地测试',text:'不应创建自绘卡片'}));assert.equal(await page.locator('.reach-popup').count(),0);
  await page.evaluate(()=>navigate('reachSettings'));await page.getByText('系统通知与提示',{exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'预览跨应用弹窗',exact:true}).count(),0);
  assert.equal(await page.getByRole('button',{name:'预览应用内弹窗',exact:true}).count(),0);
  await page.evaluate(()=>navigate('desktopBadge'));await page.getByText('本机未读：0 条',{exact:true}).waitFor();
  await page.evaluate(()=>{badge.vivoBadgePermissionKnown=true;badge.vivoBadgePermission=false;badge.homePackage='com.bbk.launcher2';badge.activeMessageNotifications=[{channel:'reach-message-v2-sv',number:3,enabled:true,badge:true}];return refreshDesktopBadge();});
  await page.getByText('Vivo角标读写权限：系统未授予（与通知总开关不同）',{exact:true}).waitFor();await page.getByText('当前系统消息通知：1 条',{exact:true}).waitFor();
  assert((await page.locator('.content').textContent()).includes('com.bbk.launcher2'));
  await page.evaluate(()=>{badge.vivoBadgePermission=true;return refreshDesktopBadge();});await page.getByText('Vivo角标读写权限：系统已授予',{exact:true}).waitFor();

  await page.evaluate(()=>applyTheme({dark:true}));await page.setViewportSize({width:320,height:740});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:path.resolve(__dirname,'../../ui-1.13-desktop-badge.png')});
  assert.deepEqual(errors,[]);assert(!(await page.evaluate(()=>calls.some(q=>['send','writingGenerate','modelList','judgeTest'].includes(q.action)))));console.log('PASS desktop badge mock UI: settings entry, actual count display, explicit sync and zero clearing, permission/channel diagnostics, settings link, dark/narrow layout, no read acknowledgements or model calls. No real launcher execution.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
