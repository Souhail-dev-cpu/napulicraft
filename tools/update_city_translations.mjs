import fs from 'node:fs';
const rows=fs.readFileSync(new URL('./city-ui-translations.txt',import.meta.url),'utf8').trim();
const dict=Object.fromEntries(rows.split(/\r?\n/).filter(Boolean).map(line=>{const [key,...values]=line.split('|');if(values.length!==3)throw new Error('Invalid translation '+key);return ['napuli.ui.'+key,values];}));
for(const [i,lang] of ['it_it','en_us','nap_it'].entries()){
const file='src/main/resources/assets/napuli/lang/'+lang+'.json';let text=fs.readFileSync(file,'utf8');const original=JSON.parse(text);const additions={};
for(const [key,values] of Object.entries(dict)){if(Object.hasOwn(original,key)) { const escaped=key.replace(/[.*+?^$\{\}()|[\]\\]/g,'\\$&');text=text.replace(new RegExp('("'+escaped+'"\\s*:\\s*)"(?:[^"\\\\]|\\\\.)*"'),(_,prefix)=>prefix+JSON.stringify(values[i]));}else additions[key]=values[i];}
const added=Object.entries(additions).map(([k,v])=>'  '+JSON.stringify(k)+': '+JSON.stringify(v)).join(',\n');if(added)text=text.replace(/\s*\}\s*$/,',\n'+added+'\n}\n');JSON.parse(text);fs.writeFileSync(file,text);
}
console.log(Object.keys(dict).length+' city UI translations synchronized in three languages');

