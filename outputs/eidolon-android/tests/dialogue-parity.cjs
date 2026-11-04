const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const rows=JSON.parse(fs.readFileSync(process.argv[2],'utf8')),scope=vm.createContext({});vm.runInContext(fs.readFileSync(path.resolve(__dirname,'../assets/ui/dialogue-text.js'),'utf8'),scope);
for(const row of rows){scope.input=row.input;const parts=JSON.parse(vm.runInContext('JSON.stringify(dialogueParts(input))',scope));assert.deepStrictEqual(parts,row.parts,row.input);assert.equal(vm.runInContext('dialogueNeedsChinese(input)',scope),row.needsChinese,row.input);}
console.log('PASS native/browser bilingual parity across '+rows.length+' normal, malformed, quoted, unfinished-code and safe HTML fixtures');
