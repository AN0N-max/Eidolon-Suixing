const fs=require('fs'),path=require('path'),assert=require('assert');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const root=path.resolve(__dirname,'../assets'),chars=JSON.parse(fs.readFileSync(path.join(root,'characters.json'),'utf8')).map(c=>({...c,lastActivity:0,preview:''}));
 const data={characters:chars,current:null,session:'',messages:[],personal:{affinity:{},relations:{},memories:[],promises:[],progress:{counts:{}},giftsSent:0},stats:{turns:0,characters:0,sessions:0,words:0},features:{},writings:[],config:{bilingual:true},reachOptions:{},sessionState:{}};
 const token='aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';let manifest=null;
 if(process.argv[2])manifest=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
 const items=(manifest?.stickers||[{name:'测试·收到',category:'赞同回应',emotion:'安心',description:'点头表示理解',intent:'确认收到',tone:'温和',ocr:'了解',characters:[]}]).map((item,i)=>({...item,preview:'/sticker-pack/'+token+'/'+i+'/thumb.png'}));
 const info={token,name:manifest?.name||'测试整包',description:manifest?.description||'仅测试',profiles:manifest?.profiles.length||1,disableSeries:manifest?.disableSeries||[],disableCount:32,items};
 const output=process.env.EIDOLON_UI_OUTPUT||path.resolve(__dirname,'../../../work/sticker-pack-ui');fs.mkdirSync(output,{recursive:true});
 const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE||undefined,headless:true});
 try{
  const p=await browser.newPage({viewport:{width:412,height:915}}),errors=[];p.setDefaultTimeout(10000);p.on('pageerror',e=>errors.push(e.message));
  await p.route('**/*',async r=>{const u=new URL(r.request().url());assert.equal(u.origin,'https://eidolon.local');
   if(u.pathname.startsWith('/sticker-pack/')){
    const index=Number(u.pathname.split('/')[3]);
    if(manifest){const file=manifest.stickers[index].file.match(/images\/([a-z]+)-(\d+)\.png/);const source=path.resolve(process.env.EIDOLON_STICKER_SOURCES||path.resolve(path.dirname(process.argv[2]),'../sources'),file[1],file[2]+'.png');return r.fulfill({path:source});}
    return r.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jgFEAAAAASUVORK5CYII=','base64')});
   }
   await r.fulfill({path:path.join(root,u.pathname)});
  });
  await p.addInitScript(({data,info})=>{window.packData=data;window.packInfo=info;window.packCalls=[];window.packFailure=false;window.packDelay=0;
   window.Native={post(raw){const q=JSON.parse(raw);packCalls.push(q);let result=packData,error=null;
    if(q.action==='themeGet')result={dark:false};if(q.action==='voiceStatus')result={state:'idle'};if(q.action==='callStatus')result={state:'idle'};if(q.action==='scheduleStatus')result={enabled:false};if(q.action==='mediaLibrary')result=[];
    if(q.action==='stickerPackChoose'){result={};setTimeout(()=>nativeEvent('stickerPackReady',JSON.stringify(packInfo)),50);}
    if(q.action==='stickerPackCancel')result={};if(q.action==='stickerPackCommit'){if(packFailure)error='图片校验失败，请重新选择表情包';else result={added:packInfo.items.length,skipped:0,updated:0,profiles:packInfo.profiles,disabled:q.args.disableExcluded?32:0};}
    setTimeout(()=>nativeReply(q.id,structuredClone(result),error),q.action==='stickerPackCommit'?packDelay:0);
   }};
  },{data,info});
  await p.goto('https://eidolon.local/ui/index.html');await p.waitForFunction(()=>typeof state!=='undefined'&&state?.characters?.length);await p.evaluate(()=>{page='settings-relations';render();});
  await p.getByRole('button',{name:'表情整包导入',exact:false}).click();await p.getByRole('button',{name:'确认合并导入',exact:true}).waitFor();
  assert.equal(await p.locator('#modal article.card').count(),items.length);assert.equal(await p.getByRole('checkbox').nth(0).isChecked(),false);assert.equal(await p.getByRole('checkbox').nth(1).isChecked(),true);assert.equal(await p.evaluate(()=>packCalls.filter(c=>c.action==='stickerPackCommit').length),0);
  await p.locator('#modal article.card details').first().locator('summary').click();await p.getByText('使用意图：'+items[0].intent,{exact:true}).waitFor();
  for(const viewport of [{width:412,height:915},{width:320,height:568},{width:915,height:412}]){
   await p.setViewportSize(viewport);await p.locator('#modal').evaluate(n=>n.scrollTop=0);await p.waitForFunction(()=>Array.from(document.querySelectorAll('#modal img')).slice(0,2).every(i=>i.complete&&i.naturalWidth>0));
   assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
   assert.equal(await p.locator('#modal').evaluate(n=>n.scrollWidth<=n.clientWidth+2),true);
   await p.screenshot({path:path.join(output,`sticker-pack-${viewport.width}x${viewport.height}.png`)});
  }
  await p.getByRole('button',{name:'取消导入',exact:true}).click();await p.getByRole('button',{name:'导入表情整包',exact:true}).waitFor();assert.equal(await p.evaluate(()=>packCalls.filter(c=>c.action==='stickerPackCancel').length),1);assert.equal(await p.evaluate(()=>packCalls.filter(c=>c.action==='stickerPackCommit').length),0);
  await p.getByRole('button',{name:'导入表情整包',exact:true}).click();await p.getByRole('button',{name:'确认合并导入',exact:true}).waitFor();await p.evaluate(()=>packFailure=true);await p.getByRole('button',{name:'确认合并导入',exact:true}).click();await p.getByRole('alert').filter({hasText:'图片校验失败'}).waitFor();assert.equal(await p.locator('#modal[open]').count(),1);assert.equal(await p.evaluate(()=>stickerPackPending!==null),true);
  await p.evaluate(()=>{packFailure=false;packDelay=350;});await p.getByRole('checkbox').nth(0).check();await p.getByRole('checkbox').nth(1).uncheck();
  await p.evaluate(()=>{const b=Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='确认合并导入');b.click();b.click();});await p.getByRole('button',{name:'导入表情整包',exact:true}).waitFor();
  const commits=await p.evaluate(()=>packCalls.filter(c=>c.action==='stickerPackCommit'));assert.equal(commits.length,2);assert.equal(commits[1].args.token,token);assert.equal(commits[1].args.updateExisting,true);assert.equal(commits[1].args.profiles,false);assert.equal(commits[1].args.disableExcluded,!!info.disableSeries.length);assert.equal(await p.evaluate(()=>stickerPackPending),null);assert.equal(await p.evaluate(()=>packCalls.some(c=>c.action==='mediaSend'||c.action==='reset')),false);assert.deepEqual(errors,[]);
  console.log('PASS real package UI with fake native bridge: entry from settings without an open chat, '+items.length+' preview cards, readable semantics, 3 layouts, explicit confirmation, cancellation, retained error preview, duplicate click fencing, optional semantic/profile import. No Android file picker or device DB executed.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
