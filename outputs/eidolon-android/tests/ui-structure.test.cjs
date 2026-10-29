// Run actual frontend functions with a small DOM and native-bridge mock.
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
class Node {
 constructor(tag){this.tagName=tag;this.children=[];this.attrs={};this.dataset={};this.className='';this._text='';this.style={setProperty(){}};this.disabled=false;this.classList={add:c=>{if(!this.className.split(' ').includes(c))this.className+=' '+c;},remove:c=>{this.className=this.className.split(' ').filter(x=>x!==c).join(' ');},toggle:(c,yes)=>yes?this.classList.add(c):this.classList.remove(c)};}
 append(...nodes){for(const n of nodes){const node=typeof n==='string'?Object.assign(new Node('#text'),{_text:n}):n;node.parent=this;this.children.push(node);}}
 replaceChildren(...nodes){this.children=[];this._text='';this.append(...nodes);}
 setAttribute(k,v){this.attrs[k]=v;}
 showModal(){this.open=true;}
 close(){this.open=false;}
 get childNodes(){return this.children;}
 get lastChild(){return this.children.at(-1);}
 get textContent(){return this._text+this.children.map(x=>x.textContent).join('');}
 set textContent(s){this._text=String(s);this.children=[];}
}
const find=(node,p)=>p(node)?node:node.children.map(c=>find(c,p)).find(Boolean);
const all=(node,p)=>[...(p(node)?[node]:[]),...node.children.flatMap(c=>all(c,p))];
const app=new Node('div'),modal=new Node('dialog'),toast=new Node('div'),html=new Node('html'),calls=[];
const document={documentElement:html,hidden:false,addEventListener(){},querySelector:s=>s==='#app'?app:s==='#modal'?modal:s==='#toast'?toast:s==='#schedule-status'?find(app,n=>n.id==='schedule-status'):null,querySelectorAll:s=>s==='.voice-play'?all(app,n=>n.className.split(' ').includes('voice-play')):[],createElement:tag=>new Node(tag),createElementNS:(ns,tag)=>new Node(tag),createTextNode:s=>Object.assign(new Node('#text'),{_text:s})};
const sandbox={document,console,setTimeout,clearTimeout,setInterval(){},requestAnimationFrame:f=>f(),Date,window:{},Native:{post:s=>calls.push(JSON.parse(s))}};
sandbox.window.Native=sandbox.Native;vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../assets/ui/writings.js'),'utf8'),sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../assets/ui/schedules.js'),'utf8'),sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../assets/ui/promises.js'),'utf8'),sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../assets/ui/app.js'),'utf8'),sandbox);
vm.runInContext("state={characters:[{id:'test',name:'测试角色',avatar:'test.jpg'}],current:'test',config:{bilingual:false},personal:{producer:{}},hideJapanese:false};",sandbox);
const run=s=>vm.runInContext(s,sandbox),reply=(call,value)=>sandbox.window.nativeReply(call.id,value,null);
let passed=0;const check=(ok,name)=>{assert(ok,name);passed++;console.log('PASS '+name);};
(async()=>{
 const text='<ja>こんにちは。</ja><zh>你好。</zh>';
 const bubble=run(`bubble(${JSON.stringify(text)},false,false,1)`),play=find(bubble,n=>n.className.split(' ').includes('voice-play'));
 check(play&&play.attrs['aria-label']==='播放角色语音'&&find(play,n=>n.tagName==='svg'),'voice button accessible idle speaker');
 check(play.parent.className==='message-tools'&&play.parent.parent.className.includes('has-voice-controls'),'voice button remains in bottom-right bubble controls');
 const click=play.onclick(),call=calls.at(-1);check(call.action==='voice'&&call.args.text===text,'voice button retains original message and bridge');reply(call,{});await click;
 sandbox.button=play;sandbox.voiceText=text;
 run("voiceControlButton(button,{state:'playing',text:voiceText})");
 check(play.attrs['aria-label']==='暂停语音'&&play.attrs['aria-pressed']==='true','playing button offers pause');
 run("voiceControlButton(button,{state:'paused',text:voiceText})");
 check(play.attrs['aria-label']==='继续播放语音'&&play.dataset.playback==='paused','paused button offers resume');
 run("voiceControlButton(button,{state:'loading',text:voiceText})");
 check(play.attrs['aria-label']==='暂停语音'&&!play.disabled,'loading can be paused without another generation');
 run("voiceControlButton(button,{state:'playing',text:'another message'})");
 check(play.attrs['aria-label']==='播放角色语音','other messages do not inherit active pause icon');
 run("page='chat';voiceView={state:'playing',text:"+JSON.stringify(text)+"}");
 const pausing=play.onclick(),toggle=calls.at(-1);check(toggle.action==='voiceToggle'&&toggle.args.text===text,'active voice uses dedicated pause/resume route');reply(toggle,{state:'paused',text});await pausing;
 check(run('voiceView.state')==='paused','pause acknowledgement updates actual frontend state');
 run("page='settings';settings()");
 const library=find(app,n=>n.tagName==='button'&&n.textContent.includes('已保存语音'));const opening=library.onclick();const libCall=calls.at(-1);reply(libCall,{});await opening;
 check(libCall.action==='voiceLibrary','saved voice settings still dispatch correctly');
 const night=find(app,n=>n.tagName==='button'&&n.textContent.includes('夜间模式'));const toggling=night.onclick();const themeCall=calls.at(-1);
 check(themeCall.action==='themeSet'&&themeCall.args.dark===true,'night switch persists through native preferences');reply(themeCall,{dark:true});await toggling;
 check(html.attrs['data-theme']==='dark'&&html.style.colorScheme==='dark','native theme result updates document and controls');
 run("applyTheme({dark:false})");check(html.attrs['data-theme']==='light','night mode can be switched off');
 run("page='chat';voiceView={state:'paused',text:'old'}");
 const navigation=run("navigate('settings')"),stop=calls.at(-1);check(stop.action==='voiceStop'&&run('voiceView.state')==='idle','leaving chat sends native stop and clears progress state');
 reply(stop,{});await new Promise(resolve=>setImmediate(resolve));const snap=calls.at(-1);assert(snap.action==='snapshot');reply(snap,run('state'));await navigation;
 check(run('page')==='settings'&&run('voiceView.state')==='idle','navigation completes with idle playback');
 run("page='chat'");const polling=run('refreshVoiceStatus()'),status=calls.at(-1);const resetting=run('resetVoice()'),reset=calls.at(-1);reply(reset,{});await resetting;reply(status,{state:'playing',text:'old'});await polling;
 check(run('voiceView.state')==='idle','stale status cannot restore playback after leaving/reset');
 run("state.hideVoiceIcon=true;state.hideJapanese=false;state.config.bilingual=true;");
 const hidden=run(`bubble(${JSON.stringify(text)},false,false,1)`);
 check(!find(hidden,n=>n.className.split(' ').includes('voice-play'))&&hidden.textContent.includes('你好。'),'hide speaker removes playback control while preserving message');
 const missing=run("bubble('<ja>こんにちは。</ja>',false,false,2)");
 check(!!find(missing,n=>n.className.includes('repair-translation')),'translation repair remains available with speaker hidden');
 run("page='settings';chatDisplay()");
 const checks=all(modal,n=>n.tagName==='input');check(checks.length===2&&checks[0].checked===true&&checks[1].checked===false,'display menu combines independent Japanese and speaker switches');
 checks[0].checked=false;checks[1].checked=true;
 const save=find(modal,n=>n.tagName==='button'&&n.textContent==='保存'),saving=save.onclick(),display=calls.at(-1);
 check(display.action==='displaySettings'&&display.args.hideJapanese===true&&display.args.hideVoiceIcon===false,'both display choices are saved together');
 const next=run('state');next.hideJapanese=true;next.hideVoiceIcon=false;reply(display,next);await saving;
 check(run('state.hideJapanese')&&!run('state.hideVoiceIcon'),'native saved display values update state');
 run("state.currentSchedule={enabled:true,text:'练习 · 事务所'};page='chat';");
 const identity=run('chatIdentity(current())');app.replaceChildren(identity);const statusButton=find(identity,n=>n.id==='schedule-status');
 check(statusButton&&!statusButton.hidden&&statusButton.textContent==='练习 · 事务所','chat header displays current activity independently of voice controls');
 const updating=run('refreshScheduleStatus()'),activity=calls.at(-1);check(activity.action==='scheduleStatus','status refresh uses local route without requesting model generation');reply(activity,{enabled:true,character:'test',text:'准备睡了',state:'bedtime'});await updating;
 check(statusButton.textContent==='准备睡了','clock refresh updates caption without rebuilding chat messages');
 const hiding=run('refreshScheduleStatus()'),off=calls.at(-1);reply(off,{enabled:false,character:'test',text:'',state:'off'});await hiding;
 check(statusButton.hidden,'turning off schedule hides activity caption');
 run("state.promiseOptions={enabled:true,automatic:false,today:'2026-10-23'};state.features={affinity:true};state.gifts=[{id:'book',name:'书'}];state.personal.promises=[];page='promises';render();");
 check(!!find(app,n=>n.textContent==='记一件约定')&&app.textContent.includes('这里还没有约定'),'commitment empty state exposes manual entry');
 const create=find(app,n=>n.tagName==='button'&&n.textContent==='记一件约定');await create.onclick();
 const task=find(modal,n=>n.tagName==='textarea');task.value='给你买一本书';
 const dateInputs=all(modal,n=>n.tagName==='input'&&n.type==='date');dateInputs[0].value='2026-10-24';dateInputs[0].onchange();
 check(dateInputs[1].value==='2026-10-24','start-date editing keeps end date from falling behind');
 const chooser=find(modal,n=>n.attrs['aria-label']==='关联礼物（可选）');await chooser.onclick();
 const book=find(modal,n=>n.tagName==='button'&&n.textContent==='书');await book.onclick();
 check(modal.open&&find(modal,n=>n===task)&&task.value==='给你买一本书'&&chooser.value==='book','nested gift selector restores same editing form and draft');
 const savePromise=find(modal,n=>n.tagName==='button'&&n.textContent==='保存'),savingPromise=savePromise.onclick(),created=calls.at(-1);
 check(created.action==='promiseAction'&&created.args.op==='create'&&created.args.character==='test'&&created.args.giftId==='book'&&created.args.from==='2026-10-24','manual promise sends explicit dates and gift via native route without model request');
 const savedPromise={id:'promise-test',character:'test',task:'给你买一本书',from:'2026-10-24',due:'2026-10-24',state:'active',changes:0,createdAt:1};
 const withPromise=run('state');withPromise.personal.promises=[savedPromise];reply(created,withPromise);await savingPromise;
 check(!modal.open&&app.textContent.includes('给你买一本书')&&app.textContent.includes('已兑现'),'saved commitment renders task and completion control');
 run("promiseSettings()");const promiseChecks=all(app,n=>n.tagName==='input'&&n.type==='checkbox');
 check(promiseChecks.length===2&&promiseChecks[0].checked&&!promiseChecks[1].checked,'feature enable and automatic extraction have independent saved switches');
 const across={...savedPromise,from:'2026-10-31',due:'2026-11-02'};sandbox.across=across;
 check(run("state.personal.promises=[across];calendarPromises(2026,11)")[0].day===1,'calendar includes a date range spanning a month boundary');
 run("state.personal.promises=[across];page='calendar';calendarMonth=new Date(2026,10,1);render()");
 check(app.textContent.includes('给你买一本书')&&!!find(app,n=>n.tagName==='button'&&n.textContent==='查看'),'calendar row opens commitment management');
 console.log('TOTAL '+passed+' PASS; structural mocks, no Android/WebView visual QA');
})().catch(e=>{console.error(e);process.exitCode=1;});
