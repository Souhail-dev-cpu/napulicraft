import fs from 'node:fs';
const assets='src/main/resources/assets/napuli/',data='src/main/resources/data/';
const write=(path,value)=>{fs.mkdirSync(path.slice(0,path.lastIndexOf('/')),{recursive:true});fs.writeFileSync(path,JSON.stringify(value,null,2)+'\n');};
const materials=['intonaco_ocra','intonaco_corallo','maiolica_napoletana','coppi_napoletani','lastre_coppi','scale_coppi'];
const texture=id=>'napuli:block/'+(id==='lastre_coppi'||id==='scale_coppi'?'coppi_napoletani':id);
for(const id of materials){
 write(assets+'items/'+id+'.json',{model:{type:'minecraft:model',model:'napuli:block/'+id}});
 if(id==='lastre_coppi'){
  const textures={bottom:texture(id),top:texture(id),side:texture(id)};
  write(assets+'models/block/'+id+'.json',{parent:'minecraft:block/slab',textures});
  write(assets+'models/block/'+id+'_top.json',{parent:'minecraft:block/slab_top',textures});
  write(assets+'blockstates/'+id+'.json',{variants:{'type=bottom':{model:'napuli:block/'+id},'type=top':{model:'napuli:block/'+id+'_top'},'type=double':{model:'napuli:block/coppi_napoletani'}}});
 }else if(id==='scale_coppi'){
  for(const [suffix,parent] of [['','stairs'],['_inner','inner_stairs'],['_outer','outer_stairs']])write(assets+'models/block/'+id+suffix+'.json',{parent:'minecraft:block/'+parent,textures:{bottom:texture(id),top:texture(id),side:texture(id)}});
  // Same 40 orientations as Minecraft 26.3 brick stairs; no machine-local cache is needed.
  const variants={};
  for(const [facing,rotation] of [['east',0],['north',270],['south',90],['west',180]])for(const half of ['bottom','top'])for(const shape of ['inner_left','inner_right','outer_left','outer_right','straight']){
   const top=half==='top',left=shape.endsWith('_left'),right=shape.endsWith('_right');
   const y=(rotation+(top?(right?90:0):(left?270:0)))%360;
   const value={model:'napuli:block/'+id+(shape.startsWith('inner')?'_inner':shape.startsWith('outer')?'_outer':'')};
   if(top||y)value.uvlock=true;if(top)value.x=180;if(y)value.y=y;
   variants[`facing=${facing},half=${half},shape=${shape}`]=value;
  }
  write(assets+'blockstates/'+id+'.json',{variants});
 }else if(id==='maiolica_napoletana'){
  // Ceramic on the walking surface, a neutral underside for the room below.
  write(assets+'models/block/'+id+'.json',{parent:'minecraft:block/cube_bottom_top',textures:{top:texture(id),bottom:'minecraft:block/spruce_planks',side:'minecraft:block/smooth_sandstone'}});
  write(assets+'blockstates/'+id+'.json',{variants:{'':{model:'napuli:block/'+id}}});
 }else{
  write(assets+'models/block/'+id+'.json',{parent:'minecraft:block/cube_all',textures:{all:texture(id)}});
  write(assets+'blockstates/'+id+'.json',{variants:{'':{model:'napuli:block/'+id}}});
 }
 const functions=id==='lastre_coppi'?[{function:'minecraft:set_count',count:2,conditions:[{condition:'minecraft:block_state_property',block:'napuli:'+id,properties:{type:'double'}}]},{function:'minecraft:explosion_decay'}]:[{function:'minecraft:explosion_decay'}];
 write(data+'napuli/loot_table/blocks/'+id+'.json',{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'napuli:'+id,functions}]}]});
}
for(const [id,dye] of [['intonaco_ocra','yellow_dye'],['intonaco_corallo','red_dye']])write(data+'napuli/recipe/'+id+'.json',{type:'minecraft:crafting_shapeless',category:'building',ingredients:['minecraft:terracotta','minecraft:white_dye','minecraft:'+dye],result:{id:'napuli:'+id,count:3}});
write(data+'napuli/recipe/maiolica_napoletana.json',{type:'minecraft:crafting_shaped',category:'building',pattern:['BW','WB'],key:{B:'minecraft:blue_dye',W:'minecraft:terracotta'},result:{id:'napuli:maiolica_napoletana',count:4}});
write(data+'napuli/recipe/coppi_napoletani.json',{type:'minecraft:crafting_shaped',category:'building',pattern:['BB','BB'],key:{B:'minecraft:brick'},result:{id:'napuli:coppi_napoletani',count:4}});
write(data+'napuli/recipe/lastre_coppi.json',{type:'minecraft:crafting_shaped',category:'building',pattern:['CCC'],key:{C:'napuli:coppi_napoletani'},result:{id:'napuli:lastre_coppi',count:6}});
write(data+'napuli/recipe/scale_coppi.json',{type:'minecraft:crafting_shaped',category:'building',pattern:['C  ','CC ','CCC'],key:{C:'napuli:coppi_napoletani'},result:{id:'napuli:scale_coppi',count:4}});
write(data+'minecraft/tags/block/mineable/pickaxe.json',{replace:false,values:materials.map(id=>'napuli:'+id)});
const labels=[['Intonaco ocra','Ochre plaster',"Intonaco ocra"],['Intonaco corallo','Coral plaster',"Intonaco curallo"],['Maiolica napoletana','Neapolitan majolica',"Maiolica napulitana"],['Coppi napoletani','Neapolitan roof tiles',"Cuoppe napulitane"],['Lastra di coppi','Roof tile slab',"Lastra 'e cuoppe"],['Scale di coppi','Roof tile stairs',"Scale 'e cuoppe"]];
for(const [language,index] of [['it_it',0],['en_us',1],['nap_it',2]]){
 const path=assets+'lang/'+language+'.json';let source=fs.readFileSync(path,'utf8');const dictionary=JSON.parse(source);const extra={};
 for(const [i,id] of materials.entries()){const key='block.napuli.'+id;if(!Object.hasOwn(dictionary,key))extra[key]=labels[i][index];}
 const rows=Object.entries(extra).map(([key,value])=>'  '+JSON.stringify(key)+': '+JSON.stringify(value)).join(',\n');
 if(rows){source=source.replace(/\s*}\s*$/,',\n'+rows+'\n}\n');JSON.parse(source);fs.writeFileSync(path,source);}
}
console.log('Sei materiali: modelli, varianti, ricette, raccolta, traduzioni e strumenti aggiornati.');
