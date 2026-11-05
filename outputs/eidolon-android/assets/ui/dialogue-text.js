'use strict';
const dialogueChinese=/[这说吗么没们让别给觉还样呢呀啦哦呜咱您]|你好|今天|明天|昨天|中文|翻译|译文|准备|睡觉|因为|已经|现在|有点|辛苦|不用|一起|但是|所以|什么|怎么|可以|喜欢/;
const dialogueJapanese=/こんにちは|こんばんは|おはよう|おやすみ|ありがとう|ごめん|です|でした|ます|ました|ません|だよ|だね|だろ|だっ|して|から|けど|じゃ|って|よね|かな[。！？!?]?$/;
function dialogueParts(value,base=''){
 const text=String(value||''),out=[],stack=[],re=/```[\s\S]*?(?:```|(?![\s\S]))|`[^`\r\n]*`|^[\t ]*>[^\r\n]*|“[^”\r\n]*”|‘[^’\r\n]*’|「[^」\r\n]*」|『[^』\r\n]*』|<\/?(?:ja|zh)(?:\s*>|(?=\r?\n|(?![\s\S])))|&lt;\/?(?:ja|zh)\s*&gt;/gmi;let at=0,m;
 const add=(value,literal=false)=>{if(!value)return;const lang=stack.length?stack[stack.length-1]:base;if(literal){const prior=out[out.length-1],l=lang==='ja'&&prior&&dialogueChinese.test(prior.text)?prior.lang:lang;out.push({lang:l,text:value,literal});return;}for(const clause of value.split(/(?<=[。！？!?\n])/)){if(!clause)continue;const zh=dialogueChinese.test(clause),ja=/[\u3040-\u30ff]/.test(clause)&&dialogueJapanese.test(clause);let l=lang;if(zh&&ja)l='';else if(zh&&lang==='ja')l='zh';else if(ja&&lang!=='ja')l='ja';const prior=out[out.length-1];if(prior&&!prior.literal&&prior.lang===l)prior.text+=clause;else out.push({lang:l,text:clause,literal:false});}};
 while((m=re.exec(text))){add(text.slice(at,m.index));const t=m[0];if(!t.startsWith('<')&&!t.toLowerCase().startsWith('&lt;'))add(t,true);else{const tag=t.toLowerCase().replace('&lt;','<').replace('&gt;','>'),lang=tag.includes('ja')?'ja':'zh';if(tag.startsWith('</')){const i=stack.lastIndexOf(lang);if(i>=0)stack.splice(i);}else if(stack.length<64)stack.push(lang);}at=re.lastIndex;}add(text.slice(at));return out;
}
function dialogueNeedsChinese(text){const parts=dialogueParts(text).filter(p=>(!p.literal||/^[“‘「『]/.test(p.text))&&p.text.trim());return parts.some(p=>p.lang==='ja')&&!parts.some(p=>p.lang==='zh'||!p.lang&&!/[\u3040-\u30ff]/.test(p.text));}
function dialogueProjection(text){const parts=dialogueParts(text);return {parts,needsChinese:dialogueNeedsChinese(text)};}
