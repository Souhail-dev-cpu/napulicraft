// Adds item models, recipes, sound events and the new texts. Run from D:\NapuliMod: node tools/addTexts.js
const fs = require("fs");
const R = "src/main/resources/";
const w = (p, o) => { fs.mkdirSync(require("path").dirname(R + p), { recursive: true }); fs.writeFileSync(R + p, JSON.stringify(o, null, "\t") + "\n"); };
const r = p => JSON.parse(fs.readFileSync(R + p, "utf8"));

for (const n of ["pizza", "cafe", "ciabatta", "motorino"]) {
	w(`assets/napuli/items/${n}.json`, { model: { type: "minecraft:model", model: `napuli:item/${n}` } });
	w(`assets/napuli/models/item/${n}.json`, { parent: "minecraft:item/generated", textures: { layer0: `napuli:item/${n}` } });
}
w("data/napuli/recipe/pizza.json", { type: "minecraft:crafting_shapeless", category: "misc", ingredients: ["minecraft:bread", "minecraft:beetroot", "minecraft:beetroot", "minecraft:milk_bucket"], result: { id: "napuli:pizza", count: 2 } });
w("data/napuli/recipe/cafe.json", { type: "minecraft:crafting_shapeless", category: "misc", ingredients: ["minecraft:cocoa_beans", "minecraft:sugar", "minecraft:glass_bottle"], result: { id: "napuli:cafe", count: 1 } });
w("data/napuli/recipe/ciabatta.json", { type: "minecraft:crafting_shapeless", category: "misc", ingredients: ["minecraft:leather", "minecraft:blue_dye"], result: { id: "napuli:ciabatta", count: 2 } });
w("data/napuli/recipe/motorino.json", { type: "minecraft:crafting_shaped", category: "misc", pattern: ["I  ", "IBI", "M M"], key: { I: "minecraft:iron_ingot", B: "minecraft:light_blue_dye", M: "minecraft:minecart" }, result: { id: "napuli:motorino", count: 1 } });

const events = ["radio", "notte", "morte", "ospedale", "furto", "lite", "amore", "diamanti", "risveglio", "nether", "vittoria", "gigante", "urlo"];
const s = {};
for (const e of events) s[e] = { subtitle: e === "urlo" ? "subtitles.napuli.urlo" : "subtitles.napuli.canzone", sounds: [] };
s.risata = { subtitle: "subtitles.napuli.risata", sounds: [{ name: "entity.witch.celebrate", type: "event" }] };
w("assets/napuli/sounds.json", s);

const nap = {
	"entity.napuli.motorino": "Motorino", "entity.napuli.ciabatta": "Ciabatta volante",
	"item.napuli.pizza": "Pizza Margherita", "item.napuli.cafe": "Tazzulella 'e cafè", "item.napuli.ciabatta": "Ciabatta 'e Mammà", "item.napuli.motorino": "Motorino",
	"subtitles.napuli.canzone": "Na canzone 'a luntano",
	"napuli.motorino.tre": "Tre 'ncopp' 'o motorino! Napule è chesta.",
	"napuli.vigile.multa": "VIGILE: Uè! Addò sta 'o casco?! Multa: n'ismeraldo.",
	"napuli.vigile.perdono": "VIGILE: Uè, senza casco?! Va buò, pe' stavota... ma nun te fà vedé cchiù!",
	"napuli.casco.risate": "'E paesane te guardano e ridono: «Ma chi è chisto c' 'o casco 'ncapa?!»",
	"napuli.mamma.title": "MAMMÀ", "napuli.mamma.chiama": "Guagliò, è pronto a tavola! Tuorne a casa!",
	"napuli.mamma.chat": "MAMMÀ: GUAGLIÒÒÒ! È PRONTO! Si nun tuorne mo' mo', vide che te succede...",
	"napuli.mamma.brava": "MAMMÀ: Bravo guaglione mio! Tiene, t'aggio fatto 'a pizza.",
	"napuli.ciabatta.title": "'A CIABATTA!", "napuli.ciabatta.sub": "Mammà t'aveva chiammato...",
	"napuli.paesano.botte": "PAESANO: Uè uè uè! Ma che faje?! Mo' chiammo a mammà toja!",
	"napuli.paesano.0": "PAESANO: Uè guagliò, che vvuò?", "napuli.paesano.1": "PAESANO: Accattate quaccosa, nun fà 'o sparagnino!",
	"napuli.paesano.2": "PAESANO: Mo' te faccio nu prezzo buono... sulo pe' te.", "napuli.paesano.3": "PAESANO: 'O vuò nu cafè?",
	"napuli.paesano.4": "PAESANO: Hai visto 'o Munaciello? Statte accorto.", "napuli.paesano.5": "PAESANO: Ma tu 'e chi si' figlio?",
	"napuli.paesano.6": "PAESANO: Jamme, ca 'a vita è na sola!", "napuli.paesano.7": "PAESANO: Uè, 'o motorino tujo è bello assaje. Quanto vuò?"
};
for (const f of ["nap_it", "it_it", "en_us"]) {
	const j = r(`assets/napuli/lang/${f}.json`);
	Object.assign(j, nap);
	delete j["subtitles.napuli.neomelodica"];
	w(`assets/napuli/lang/${f}.json`, j);
}
console.log("texts done");
