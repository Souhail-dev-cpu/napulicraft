import fs from 'node:fs';
const assets='src/main/resources/assets/napuli/',data='src/main/resources/data/napuli/';
const models=[['oneplus_16','OnePlus 16','amethyst_shard'],['vivo_x500_pro_max','vivo X500 Pro Max','orange_dye'],['iqoo_15','iQOO 15','white_dye'],['iqoo_16','iQOO 16','blue_dye'],['oneplus_15','OnePlus 15','yellow_dye']];
const write=(file,value)=>{fs.mkdirSync(file.slice(0,file.lastIndexOf('/')),{recursive:true});fs.writeFileSync(file,JSON.stringify(value,null,2)+'\n');};
function box(from,to,texture){return {from,to,faces:Object.fromEntries(['north','south','east','west','up','down'].map(side=>[side,{uv:[0,0,16,16],texture}]))};}
for(const [id,name,accent] of models){
 const body=box([4.4,.5,7.5],[11.6,15.5,8.2],'#back');
 body.faces.north={uv:[0,0,16,16],texture:'#front'};body.faces.south={uv:[0,0,16,16],texture:'#back'};
 for(const side of ['east','west','up','down'])body.faces[side]={uv:[0,0,1,16],texture:'#edge'};
 const elements=[body,box([11.6,10.3,7.65],[11.78,12.1,8.03],'#edge'),box([4.22,10,7.65],[4.4,12.5,8.03],'#edge')];
 // Raised camera glass matches each manufacturer's rear camera layout.
 if(id==='oneplus_16'){
   // Rounded silver island with continuous UVs, matching the supplied camera close-up.
   const x0=4.85,x1=9.55,y0=10.85,y1=15.12,r=.55,strips=32;
   for(let row=0;row<strips;row++){
     const low=y0+row*(y1-y0)/strips,high=y0+(row+1)*(y1-y0)/strips,mid=(low+high)/2;
     const d=Math.max(0,r-Math.min(mid-y0,y1-mid)),inset=r-Math.sqrt(r*r-d*d);
     const camera=box([x0+inset,low,8.2],[x1-inset,high,8.62],'#edge');
     camera.faces.south={uv:[inset/(x1-x0)*16,16-(row+1)/strips*16,(1-inset/(x1-x0))*16,16-row/strips*16],texture:'#camera'};
     elements.push(camera);
   }
 }
 else if(id==='oneplus_15'){
   const camera=box([4.85,10.85,8.2],[9.55,15.12,8.62],'#edge');
   camera.faces.south={uv:[0,0,16,16],texture:'#camera'};
   elements.push(camera);
 }
 else if(id==='iqoo_16'){
   const camera=box([4.86,10.55,8.2],[9.62,15.05,8.58],'#edge');
   camera.faces.south={uv:[0,0,16,16],texture:'#camera'};
   elements.push(camera);
 }
 else if(id==='vivo_x500_pro_max'){
   const cx=8,cy=12.35,rx=2.7,ry=2.8,strips=24;
   for(let row=0;row<strips;row++){
     const low=cy-ry+row*2*ry/strips,high=cy-ry+(row+1)*2*ry/strips;
     const half=rx*Math.sqrt(1-(((low+high)/2-cy)/ry)**2);
     const camera=box([cx-half,low,8.2],[cx+half,high,8.7],'#edge');
     camera.faces.south={uv:[(rx-half)/rx*8,16-(row+1)/strips*16,(rx+half)/rx*8,16-row/strips*16],texture:'#camera'};
     elements.push(camera);
   }
   const flash=box([5,14.3,8.2],[5.6,14.9,8.34],'#edge');flash.faces.south={uv:[0,0,16,16],texture:'#flash'};elements.push(flash);
 }
 else if(id==='iqoo_15'){
   const camera=box([4.85,10.7,8.2],[9.6,15.12,8.65],'#edge');
   camera.faces.south={uv:[0,0,16,16],texture:'#camera'};
   elements.push(camera);
 }
 write(assets+'models/item/'+id+'.json',{ambientocclusion:false,gui_light:'front',textures:{front:'napuli:item/phones/'+id+'_face',back:'napuli:item/phones/'+id+'_back',edge:'napuli:item/phones/'+id+'_edge',camera:'napuli:item/phones/'+id+'_camera',particle:'napuli:item/phones/'+id+'_back',...(id==='vivo_x500_pro_max'?{flash:'napuli:item/phones/'+id+'_flash'}:{})},elements,display:{gui:{rotation:[18,25,-12],translation:[0,0,0],scale:[.93,.93,.93]},firstperson_righthand:{rotation:[0,180,0],translation:[0,2,0],scale:[.68,.68,.68]},firstperson_lefthand:{rotation:[0,180,0],translation:[0,2,0],scale:[.68,.68,.68]},thirdperson_righthand:{rotation:[0,180,0],translation:[0,1,1],scale:[.65,.65,.65]},ground:{translation:[0,2,0],scale:[.5,.5,.5]},fixed:{rotation:[0,180,0]}}});
 write(assets+'items/'+id+'.json',{model:{type:'minecraft:model',model:'napuli:item/'+id}});
 const ingredients=['minecraft:glass_pane','minecraft:copper_ingot','minecraft:iron_ingot','minecraft:redstone','minecraft:quartz','minecraft:'+accent];
 write(data+'recipe/'+id+'.json',{type:'minecraft:crafting_shapeless',category:'equipment',ingredients,result:{id:'napuli:'+id,count:1}});
 write(data+'advancement/recipes/phones/'+id+'.json',{parent:'minecraft:recipes/root',criteria:{has_part:{trigger:'minecraft:inventory_changed',conditions:{items:[{items:'minecraft:redstone'}]}}},requirements:[['has_part']],rewards:{recipes:['napuli:'+id]}});
}
write(assets+'items/caricatore_usb_c.json',{model:{type:'minecraft:model',model:'napuli:item/caricatore_usb_c'}});
write(assets+'models/item/caricatore_usb_c.json',{parent:'minecraft:item/generated',textures:{layer0:'napuli:item/phones/caricatore_usb_c'}});
write(data+'recipe/caricatore_usb_c.json',{type:'minecraft:crafting_shapeless',category:'equipment',ingredients:['minecraft:copper_ingot','minecraft:iron_ingot','minecraft:string','minecraft:redstone'],result:{id:'napuli:caricatore_usb_c',count:1}});
write(data+'advancement/recipes/phones/caricatore_usb_c.json',{parent:'minecraft:recipes/root',criteria:{has_part:{trigger:'minecraft:inventory_changed',conditions:{items:[{items:'minecraft:redstone'}]}}},requirements:[['has_part']],rewards:{recipes:['napuli:caricatore_usb_c']}});
for(const lang of ['it_it','en_us','nap_it']){
 const file=assets+'lang/'+lang+'.json';let text=fs.readFileSync(file,'utf8');const original=JSON.parse(text);const entries=Object.fromEntries(models.map(([id,name])=>['item.napuli.'+id,name]));
 entries['item.napuli.caricatore_usb_c']=lang==='en_us'?'USB-C Charger':'Caricatore USB-C';entries['key.napuli.phone']=lang==='en_us'?'Open phone':'Apri il telefono';entries['key.category.napuli.phone']='Telefono';
 const fresh=Object.entries(entries).filter(([key])=>!Object.hasOwn(original,key));
 if(fresh.length)text=text.replace(/\s*\}\s*$/,',\n'+fresh.map(([k,v])=>'\t'+JSON.stringify(k)+': '+JSON.stringify(v)).join(',\n')+'\n}\n');
 fs.writeFileSync(file,text);
}
console.log('Generated five 3D handsets, charger, recipes and item names. Run java tools/GenPhoneTextures.java for textures.');
