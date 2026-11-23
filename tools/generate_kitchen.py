"""Generate the kitchen catalogue, vanilla recipes, models and translations. No external dependencies."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/napuli'
DATA = RES / 'data/napuli'

def vanilla(name):
    return 'minecraft:' + name

def local(name):
    return 'napuli:' + name

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent='\t' if path.parent.name == 'lang' else 2) + '\n', encoding='utf-8', newline='\n')

# All new ingredients come from renewable vanilla resources; no quest or biome-specific mod loot is required.
ingredients = [
    ('farina', 'Farina', 'Flour', 2, ['wheat'], None),
    ('lievito', 'Lievito', 'Yeast', 2, ['brown_mushroom', 'sugar'], None),
    ('sale', 'Sale', 'Salt', 3, ['kelp'], None),
    ('impasto', 'Impasto', 'Dough', 2, ['napuli:farina', 'napuli:lievito', 'water_bucket'], None),
    ('pomodoro', 'Pomodoro', 'Tomato', 3, ['beetroot', 'sweet_berries'], None),
    ('mozzarella', 'Mozzarella', 'Mozzarella', 3, ['milk_bucket', 'napuli:sale'], None),
    ('ricotta', 'Ricotta', 'Ricotta', 3, ['milk_bucket', 'sugar'], None),
    ('olio', 'Olio', 'Cooking oil', 1, ['wheat_seeds', 'wheat_seeds', 'wheat_seeds', 'glass_bottle'], 'glass_bottle'),
    ('riso', 'Riso', 'Rice', 3, ['wheat_seeds', 'sugar_cane'], None),
    ('pasta', 'Pasta fresca', 'Fresh pasta', 3, ['napuli:farina', 'egg'], None),
    ('friarielli', 'Friarielli', 'Broccoli rabe', 2, ['kelp', 'green_dye'], None),
    ('salsiccia', 'Salsiccia', 'Sausage', 2, ['porkchop', 'sweet_berries'], None),
    ('vongole', 'Vongole', 'Clams', 3, ['cod', 'flint'], None),
    ('baccala', 'Baccalà sotto sale', 'Salt cod', 2, ['cod', 'napuli:sale'], None),
    ('capitone', 'Capitone', 'Eel', 1, ['salmon', 'kelp'], None),
    ('limone', 'Limone', 'Lemon', 2, ['sugar_cane', 'yellow_dye'], None),
    ('rum', 'Rum da cucina', 'Cooking rum', 1, ['sugar', 'red_mushroom', 'water_bucket', 'glass_bottle'], 'glass_bottle'),
    ('chicchi_caffe', 'Chicchi di caffè', 'Coffee beans', 2, ['cocoa_beans'], None),
]

# id, Italian/English name, nutrition, saturation modifier, consume seconds, effect, ingredients, station,
# baked, sweet, fried, serving container, rarity, illustrated silhouette.
foods = [
    ('fritto_misto','Fritto misto','Mixed fritters',5,.35,1.0,'NONE',['potato','napuli:farina','napuli:olio'],'FRY',True,False,True,None,False,'bag'),
    ('pizza_portafoglio','Pizza a Portafoglio','Folded street pizza',7,.65,.6,'NONE',['napuli:impasto','napuli:pomodoro','napuli:mozzarella','paper'],'OVEN',True,False,False,None,False,'fold'),
    ('sfogliatella_riccia','Sfogliatella Riccia','Riccia sfogliatella',5,.5,1.2,'RANDOM_LUCK',['napuli:farina','napuli:ricotta','sugar'],'OVEN',True,True,False,None,False,'shell'),
    ('sfogliatella_frolla','Sfogliatella Frolla','Rare frolla sfogliatella',6,.7,1.2,'RARE_LUCK',['napuli:impasto','napuli:ricotta','honey_bottle','gold_nugget'],'OVEN',True,True,False,None,True,'bun'),
    ('baba_rum','Babà al Rum','Rum baba',5,.5,1.2,'BABA',['napuli:impasto','napuli:rum','sugar'],'OVEN',True,True,False,None,False,'baba'),
    ('pastiera','Pastiera Napoletana','Neapolitan pastiera',9,.8,1.6,'PASTIERA',['wheat','napuli:ricotta','egg','sugar'],'OVEN',True,True,False,None,False,'pie'),
    ('zeppole','Zeppole di San Giuseppe','Saint Joseph fritters',5,.5,1.0,'LUCK',['napuli:impasto','napuli:ricotta','sweet_berries','napuli:olio'],'FRY',True,True,True,None,False,'ring'),
    ('struffoli','Struffoli','Sharing struffoli',6,.55,1.2,'TEAM_HEAL',['napuli:impasto','honey_bottle','napuli:olio'],'FRY',True,True,True,None,False,'balls'),
    ('roccoco','Roccocò','Travel roccoco',5,.8,1.2,'RESISTANCE',['napuli:farina','cocoa_beans','sugar'],'OVEN',True,True,False,None,False,'ring'),
    ('mustaccioli','Mustaccioli','Dungeon mustaccioli',6,.6,1.2,'DUNGEON',['napuli:farina','cocoa_beans','honey_bottle'],'OVEN',True,True,False,None,False,'diamond'),
    ('casatiello','Casatiello','Casatiello',10,.9,1.6,'STRENGTH',['napuli:impasto','napuli:salsiccia','egg','napuli:mozzarella'],'OVEN',True,False,False,None,False,'ring'),
    ('lasagne_carnevale','Lasagne di Carnevale','Carnival lasagne',14,1.0,2.0,'HEAVY',['napuli:pasta','napuli:pomodoro','napuli:ricotta','napuli:salsiccia'],'OVEN',True,False,False,None,False,'tray'),
    ('chiacchiere','Chiacchiere','Quick carnival ribbons',3,.3,.4,'AGILITY',['napuli:farina','sugar','napuli:olio'],'FRY',True,True,True,None,False,'ribbons'),
    ('spaghetti_vongole','Spaghetti alle Vongole','Clam spaghetti',8,.65,1.6,'SEA',['napuli:pasta','napuli:vongole','napuli:olio','bowl'],'OVEN',True,False,False,'bowl',False,'bowl'),
    ('baccala_napoletana','Baccalà alla Napoletana','Neapolitan salt cod',10,.8,1.6,'CLEANSE',['napuli:baccala','napuli:pomodoro','napuli:olio'],'OVEN',True,False,False,None,False,'fish'),
    ('capitone_fritto','Capitone Fritto','Rare fried eel',14,1.0,1.6,'EXTREME',['napuli:capitone','napuli:farina','napuli:olio','gold_nugget'],'FRY',True,False,True,None,True,'fish'),
    ('minestra_maritata','Minestra Maritata','Slow-healing wedding soup',7,.7,1.6,'SOUP',['napuli:friarielli','napuli:salsiccia','carrot','bowl'],'OVEN',True,False,False,'bowl',False,'bowl'),
    ('insalata_rinforzo','Insalata di Rinforzo','Celebration reinforcement salad',6,.6,1.2,'RESISTANCE',['carrot','beetroot','napuli:friarielli','napuli:olio','bowl'],'TABLE',False,False,False,'bowl',False,'bowl'),
    ('salsiccia_friarielli','Salsiccia e Friarielli','Sausage and broccoli rabe',10,.85,1.6,'STRENGTH',['napuli:salsiccia','napuli:friarielli','napuli:olio'],'OVEN',True,False,False,None,False,'plate'),
    ('gatto_patate','Gattò di Patate','Potato gateau',11,1.0,1.6,'GATTO',['potato','potato','napuli:mozzarella','egg'],'OVEN',True,False,False,None,False,'tray'),
    ('sartu_riso','Sartù di Riso','Puzzle rice sartu',9,.8,1.6,'PUZZLE',['napuli:riso','napuli:pomodoro','napuli:mozzarella','egg'],'OVEN',True,False,False,None,False,'baba'),
    ('limoncello','Limoncello','Limoncello',1,.1,.8,'LIMONCELLO',['napuli:limone','napuli:rum','sugar','glass_bottle'],'COFFEE',False,False,False,'glass_bottle',False,'bottle'),
    ('acqua_cocomero','Acqua di Cocomero','Cooling watermelon water',3,.25,.6,'COOLING',['melon_slice','melon_slice','sugar','glass_bottle'],'COFFEE',False,False,False,'glass_bottle',False,'bottle'),
]

def normalize(items):
    return [x if ':' in x else vanilla(x) for x in items]

recipes = []
def recipe(recipe_id, output, count, inputs, station='TABLE', fuel=False, smelt=False):
    inputs = normalize(inputs)
    recipes.append(dict(id=recipe_id, output=output, count=count, ingredients=inputs, station=station, fuel=fuel))
    result = dict(id=output, count=count)
    if smelt:
        value = dict(type='napuli:oil_frying' if station == 'FRY' else 'napuli:wood_baking', category='food', ingredient=inputs[0], result=result, experience=.2, cookingtime=200)
    else:
        value = dict(type='minecraft:crafting_shapeless', category='misc', ingredients=inputs, result=result)
    write(DATA / 'recipe' / (recipe_id + '.json'), value)
    write(DATA / 'advancement' / 'recipes/kitchen' / (recipe_id + '.json'), {
        'parent':'minecraft:recipes/root',
        'criteria':{'has_ingredient':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':inputs[0]}]}}},
        'requirements':[['has_ingredient']], 'rewards':{'recipes':[local(recipe_id)]}})

catalogue = dict(ingredients=[], foods=[], recipes=[], blocks=[
    dict(id='forno_legna_portatile', kind='OVEN', it='Forno a Legna Portatile', en='Portable Wood Oven', texture='bricks'),
    dict(id='tavolo_pizzaiolo', kind='TABLE', it='Tavolo da Pizzaiolo', en='Pizza Maker Table', texture='oak_planks'),
    dict(id='banca_frittura', kind='FRY', it='Banca della Frittura', en='Frying Counter', texture='iron_block'),
    dict(id='banca_caffe', kind='COFFEE', it='Banca del Caffè', en='Coffee Counter', texture='dark_oak_planks'),
    dict(id='enigma_sartu', kind='PUZZLE', it="Tavoletta dell'Enigma", en='Sartu Puzzle Tablet', texture='chiseled_stone_bricks'),
])
for item_id, it, en, count, inputs, container in ingredients:
    catalogue['ingredients'].append(dict(id=item_id,it=it,en=en,container=container))
    recipe(item_id,local(item_id),count,inputs,'FRY' if item_id=='olio' else 'COFFEE' if item_id in ('rum','chicchi_caffe') else 'TABLE')

for item_id, it, en, nutrition, saturation, seconds, effect, inputs, station, baked, sweet, fried, container, rare, shape in foods:
    catalogue['foods'].append(dict(id=item_id,it=it,en=en,nutrition=nutrition,saturation=saturation,seconds=seconds,
        effect=effect,sweet=sweet,fried=fried,container=container,rare=rare,shape=shape))
    if baked:
        raw_id = item_id + '_crudo'
        catalogue['ingredients'].append(dict(id=raw_id,it=it+' · da cuocere',en=en+' · uncooked',container=None))
        recipe(raw_id,local(raw_id),1,inputs)
        recipe(item_id+'_cottura',local(item_id),1,[local(raw_id)],station,True,True)
    else:
        recipe(item_id,local(item_id),1,inputs,station)

catalogue['ingredients'].append(dict(id='pizza_cruda',it='Pizza cruda',en='Uncooked pizza',container=None))
recipe('pizza_cruda',local('pizza_cruda'),1,['napuli:impasto','napuli:pomodoro','napuli:mozzarella'])
recipe('pizza_cottura',local('pizza'),1,['napuli:pizza_cruda'],'OVEN',True,True)
recipe('caffe_banca',local('cafe'),1,['napuli:chicchi_caffe','sugar','glass_bottle'],'COFFEE')
recipe('borsa_fritto',local('borsa_fritto'),1,['leather','paper','string','napuli:fritto_misto'])
recipe('formaggio_piede_cucina',local('formaggio_piede'),1,['napuli:mozzarella','brown_mushroom','napuli:sale'])
recipe('forno_legna_portatile',local('forno_legna_portatile'),1,['furnace','brick','brick','iron_ingot'])
recipe('tavolo_pizzaiolo',local('tavolo_pizzaiolo'),1,['crafting_table','smooth_stone','bowl'])
recipe('banca_frittura',local('banca_frittura'),1,['cauldron','furnace','iron_ingot'])
recipe('banca_caffe',local('banca_caffe'),1,['crafting_table','copper_ingot','glass_bottle','cocoa_beans'])
recipe('enigma_sartu',local('enigma_sartu'),1,['stone_bricks','redstone','book'])
catalogue['recipes'] = recipes
write(ASSETS / 'kitchen.json', catalogue)

art = []
all_items = catalogue['ingredients'] + catalogue['foods'] + [
    dict(id='borsa_fritto',it='Borsa del Fritto',en='Regenerating Fry Bag',shape='bag'),
    dict(id='moneta',it='Moneta del Munaciello',en='Munaciello coin',shape='coin')]
for item in all_items:
    item_id = item['id']
    write(ASSETS / 'items' / (item_id+'.json'), {'model':{'type':'minecraft:model','model':local('item/'+item_id)}})
    write(ASSETS / 'models/item' / (item_id+'.json'), {'parent':'minecraft:item/generated','textures':{'layer0':local('item/'+item_id)}})
    art.append(item_id+'\t'+item.get('shape','ingredient')+'\t'+str(sum(ord(c) for c in item_id)))
for block in catalogue['blocks']:
    item_id = block['id']
    write(ASSETS / 'blockstates' / (item_id+'.json'), {'variants':{'':{'model':local('block/'+item_id)}}})
    write(ASSETS / 'models/block' / (item_id+'.json'), {'parent':'minecraft:block/cube_bottom_top','textures':{
        'bottom':vanilla('block/'+block['texture']), 'top':local('block/'+item_id+'_top'), 'side':local('block/'+item_id+'_side')}})
    write(ASSETS / 'items' / (item_id+'.json'), {'model':{'type':'minecraft:model','model':local('block/'+item_id)}})
    write(DATA / 'loot_table/blocks' / (item_id+'.json'), {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':local(item_id)}],
        'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    art.append(item_id+'\tblock\t'+str(sum(ord(c) for c in item_id)))

for lang in ('it_it','nap_it','en_us'):
    path = ASSETS / 'lang' / (lang+'.json')
    translations = json.loads(path.read_text(encoding='utf-8-sig'))
    for item in all_items:
        translations['item.napuli.'+item['id']] = item['en' if lang=='en_us' else 'it']
    for block in catalogue['blocks']:
        translations['block.napuli.'+block['id']] = block['en' if lang=='en_us' else 'it']
    english = lang == 'en_us'
    effects = {
        'NONE': ('Quick street food.' , 'Cibo di strada rapido.'),
        'RANDOM_LUCK': ('Luck I–II for a random 30–90 seconds.', 'Fortuna I–II per 30–90 secondi casuali.'),
        'RARE_LUCK': ('Luck III for 2 minutes.', 'Fortuna III per 2 minuti.'),
        'LUCK': ('Luck I for 90 seconds.', 'Fortuna I per 90 secondi.'),
        'BABA': ('Speed II and Jump II for 15 s; then Nausea 10 s and Slowness 5 s.', 'Velocità II e Salto II per 15 s; poi Nausea 10 s e Lentezza 5 s.'),
        'PASTIERA': ('Heals 4 hearts; Regeneration II for 20 s.', 'Cura 4 cuori; Rigenerazione II per 20 s.'),
        'TEAM_HEAL': ('Heals 2 hearts and regenerates teammates within 16 blocks (scoreboard teams).', 'Cura 2 cuori e rigenera i compagni entro 16 blocchi (squadre scoreboard).'),
        'RESISTANCE': ('Resistance I for 60 s.', 'Resistenza I per 60 s.'),
        'DUNGEON': ('Resistance II for 45 s; nearby monsters gain Weakness for 30 s.', 'Resistenza II per 45 s; Debolezza ai mostri vicini per 30 s.'),
        'STRENGTH': ('Strength I for 60 s.', 'Forza I per 60 s.'),
        'HEAVY': ('A very filling meal; Slowness I for 20 s.', 'Molto nutriente e saziante; Lentezza I per 20 s.'),
        'AGILITY': ('Speed II and Jump I for 20 s. Eats in 0.4 s.', 'Velocità II e Salto I per 20 s. Si mangia in 0,4 s.'),
        'SEA': ("Dolphin's Grace for 60 s and Water Breathing for 30 s.", 'Grazia del Delfino per 60 s e Respirazione Acquatica per 30 s.'),
        'CLEANSE': ('Heals 4 hearts, removes harmful effects; Regeneration for 10 s.', 'Cura 4 cuori, rimuove gli effetti negativi; Rigenerazione per 10 s.'),
        'EXTREME': ('Strength III, Speed III and Resistance II for 20 s; 35% risk of extreme fullness afterwards.', 'Forza III, Velocità III e Resistenza II per 20 s; rischio del 35% di sazietà estrema dopo il bonus.'),
        'SOUP': ('Regeneration I for 90 s.', 'Rigenerazione I per 90 s.'),
        'GATTO': ('Heals 3 hearts; Regeneration I for 10 s. Very filling.', 'Cura 3 cuori; Rigenerazione I per 10 s. Molto saziante.'),
        'PUZZLE': ('Unlocks the craftable Sartu Puzzle Tablet.', "Sblocca la Tavoletta dell'Enigma, craftabile."),
        'LIMONCELLO': ('Luck I for 45 s; 3 drinks within a minute cause comical drunkenness.', 'Fortuna I per 45 s; 3 bicchieri entro un minuto causano ubriachezza comica.'),
        'COOLING': ('Reduces heat by 60, cools for 2 min; Fire Resistance for 30 s.', 'Riduce il calore di 60, rinfresca per 2 min; Resistenza al Fuoco per 30 s.'),
    }
    for food in catalogue['foods']:
        translations['napuli.food.'+food['id']] = effects[food['effect']][0 if english else 1]
    if lang == 'it_it':
        translations['item.napuli.cafe'] = 'Caffè Napoletano'
    translations.update({
        'napuli.kitchen.hint':'Right-click a kitchen station to see ingredients and prepare recipes.' if english else 'Usa una postazione da cucina per vedere ingredienti e preparare ricette.',
        'napuli.kitchen.next':'Next recipes →' if english else 'Altre ricette →',
        'napuli.kitchen.previous':'← Previous recipes' if english else '← Ricette precedenti',
        'napuli.kitchen.prepare':'Prepare' if english else 'Prepara',
        'napuli.kitchen.back':'← Recipes' if english else '← Ricette',
        'napuli.kitchen.raw':'Cook in a furnace or at the matching kitchen station.' if english else 'Cuoci in fornace o nella postazione da cucina indicata.',
        'napuli.kitchen.fuel':'Uses 1 coal, charcoal, log or plank per cooked serving.' if english else 'Usa 1 carbone, carbonella, tronco o asse per porzione cotta.',
        'napuli.kitchen.missing':'Missing ingredients or fuel. Nothing was consumed.' if english else 'Mancano ingredienti o combustibile. Nulla è stato consumato.',
        'napuli.kitchen.full':'Free some inventory space before cooking.' if english else "Libera spazio nell'inventario prima di cucinare.",
        'napuli.kitchen.done':'Prepared: %s ×%s' if english else 'Preparato: %s ×%s',
        'napuli.kitchen.bag':'Fritters in the bag: %s/8. One serving regenerates every minute in your inventory.' if english else "Porzioni nella borsa: %s/8. Si rigenera una porzione al minuto nell'inventario.",
        'napuli.kitchen.bag_empty':'The bag is empty: wait for a serving to regenerate.' if english else 'Borsa vuota: attendi che si rigeneri una porzione.',
        'napuli.kitchen.coin':'+1 € in your wallet.' if english else '+1 € nel portafoglio.',
        'napuli.kitchen.chef':'Pizza reputation: %s pizzas · %s helpers available for quests.' if english else 'Reputazione da Pizzaiolo: %s pizze · %s aiutanti disponibili nelle quest.',
        'napuli.kitchen.help':'Your cooking earned help from %s NPCs for this quest!' if english else 'La tua cucina ti ha fatto guadagnare %s aiutanti per questa quest!',
        'napuli.kitchen.extreme':'Extreme fullness! Take a break from fried food.' if english else 'Sazio estremo! Fai una pausa dai fritti.',
        'napuli.kitchen.drunk':'Comical drunkenness! Maybe stop at one limoncello next time...' if english else "Ubriachezza comica! Magari fermati a un limoncello la prossima volta...",
        'napuli.kitchen.hot':'You are overheating. Find shade or drink watermelon water.' if english else 'Troppo caldo! Cerca ombra o bevi acqua di cocomero.',
        'napuli.kitchen.puzzle_ready':'Sartu eaten: you can now solve a Sartu Puzzle Tablet!' if english else "Sartù mangiato: ora puoi risolvere una Tavoletta dell'Enigma!",
        'napuli.kitchen.puzzle_locked':'Eat Sartu di Riso to unlock this puzzle.' if english else 'Mangia il Sartù di Riso per sbloccare questo enigma.',
        'napuli.kitchen.puzzle_done':'Puzzle solved: 3 emeralds! You have already claimed this reward.' if english else 'Enigma risolto: 3 smeraldi! Hai già riscosso questa ricompensa.',
        'napuli.kitchen.puzzle_question':'Choose the next step in the pizza recipe (%s/3).' if english else 'Scegli il prossimo passaggio della ricetta della pizza (%s/3).',
        'napuli.kitchen.puzzle_wrong':'Wrong order! Try again: flour, dough, pizza.' if english else 'Ordine sbagliato! Riprova: farina, impasto, pizza.',
        'napuli.kitchen.hunger':'Neapolitan fullness' if english else 'Fame Napoletana',
        'napuli.kitchen.heat':'Heat' if english else 'Calore',
        'napuli.kitchen.pizzas':'Pizzas: %s' if english else 'Pizze: %s',
        'napuli.munaciello.sweet':'The Munaciello trusts you: %s/3 treats.' if english else 'Il Munaciello si fida: %s/3 dolci.',
        'napuli.munaciello.tamed':'The Munaciello is now your friend!' if english else 'Il Munaciello ora è tuo amico!',
        'napuli.munaciello.hidden':'He hid your items in the last chest you opened, at %s, %s, %s, and left coins!' if english else "Ha nascosto gli oggetti nell'ultima chest che hai aperto, a %s, %s, %s, e ha lasciato monete!",
        'napuli.munaciello.returned':'No suitable chest: the Munaciello returned the items.' if english else 'Nessuna chest disponibile: il Munaciello ha restituito gli oggetti.',
    })
    write(path,translations)
(ROOT/'tools/kitchen-art.tsv').write_text('\n'.join(art)+'\n',encoding='utf-8',newline='\n')

lines=['# Ricette della cucina napoletana','', 'Tutte disponibili senza quest. `+` indica ingredienti nella crafting table; le preparazioni crude vanno nel forno a legna o nella friggitoria (10 secondi).', '', '| Risultato | Ingredienti | Postazione alternativa |','| --- | --- | --- |']
for r in recipes:
    names = [x.removeprefix('minecraft:').removeprefix('napuli:').replace('_',' ') for x in r['ingredients']]
    station = 'TABLE' if r['id'] == 'olio' else r['station']
    lines.append('| '+r['output'].removeprefix('napuli:').replace('_',' ')+f" ×{r['count']} | "+' + '.join(names)+((' + tronchi' if station == 'OVEN' else ' + olio') if r['fuel'] else '')+' | '+station+' |')
path=ROOT/'docs/CUCINA_RICETTE.md'
path.parent.mkdir(exist_ok=True)
path.write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
print(f"Generated {len(all_items)} items, {len(catalogue['blocks'])} blocks and {len(recipes)} recipes.")
