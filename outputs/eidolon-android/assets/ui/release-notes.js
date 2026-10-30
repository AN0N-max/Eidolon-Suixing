'use strict';
let releaseNotesCache=null;
async function releaseNotesPage(){
 header('更新公告',true);
 const area=content('page release-notes'),loading=el('p','notice','正在读取本机更新公告…');area.append(loading);
 const active=()=>page==='releaseNotes'&&area.isConnected;
 try{
  const notes=releaseNotesCache||await api('releaseNotes');
  if(!active())return;
  releaseNotesCache=notes;area.replaceChildren();
  const releases=notes.releases||[];
  if(!releases.length){area.append(el('p','notice','此安装包暂无更新公告。'));return;}
  area.append(el('p','notice','当前版本 '+releases[0].version+' · 公告随安装包保存，可离线查看。'));
  releases.forEach((release,index)=>{
   const card=el('details','release-note');card.open=index===0;
   const heading=el('summary');heading.append(el('strong','','v'+release.version+' · '+release.title));if(index===0)heading.append(el('small','muted','当前版本'));card.append(heading);
   for(const section of release.sections){card.append(el('h2','',section.title));const list=el('ul');for(const item of section.items)list.append(el('li','',item));card.append(list);}
   area.append(card);
  });
 }catch(e){if(active()){area.replaceChildren(el('p','danger','更新公告读取失败，请重试。'),btn('重新读取',()=>{if(active())render();},'pill'));}}
}
