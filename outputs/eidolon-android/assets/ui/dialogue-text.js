'use strict';
// Mirrors the bounded native DialogueText decoder; parity fixtures exercise both implementations.
function dialogueParts(value,base=''){
 const text=String(value||''),out=[],stack=[],re=/```[\s\S]*?(?:```|(?![\s\S]))|`[^`\r\n]*`|^[\t ]*>[^\r\n]*|“[^”\r\n]*”|‘[^’\r\n]*’|「[^」\r\n]*」|『[^』\r\n]*』|<\/?(?:ja|zh)\s*>|&lt;\/?(?:ja|zh)\s*&gt;/gmi;let at=0,m;
 const add=(value,literal=false)=>{if(!value)return;const lang=stack.length?stack[stack.length-1]:base;if(literal){out.push({lang,text:value,literal});return;}for(const line of value.split(/(?<=[\s。！？!?，、；;])/)){const l=lang!=='ja'&&/[\u3040-\u30ff]/.test(line)?'ja':lang,prior=out[out.length-1];if(prior&&!prior.literal&&prior.lang===l)prior.text+=line;else out.push({lang:l,text:line,literal:false});}};
 while((m=re.exec(text))){add(text.slice(at,m.index));const t=m[0];if(!t.startsWith('<')&&!t.toLowerCase().startsWith('&lt;'))add(t,true);else{const tag=t.toLowerCase().replace('&lt;','<').replace('&gt;','>'),lang=tag.includes('ja')?'ja':'zh';if(tag.startsWith('</')){const i=stack.lastIndexOf(lang);if(i>=0)stack.splice(i);}else if(stack.length<64)stack.push(lang);}at=re.lastIndex;}add(text.slice(at));return out;
}
function dialogueNeedsChinese(text){const parts=dialogueParts(text).filter(p=>(!p.literal||/^[“‘「『]/.test(p.text))&&p.text.trim());return parts.some(p=>p.lang==='ja')&&!parts.some(p=>p.lang==='zh'||!p.lang);}
