// Carabinieri, the new girls and their dialogues. Run from D:\NapuliMod: node tools/addTexts2.js
const fs = require("fs");
const R = "src/main/resources/assets/napuli/lang/";
const nap = {
	"napuli.resident.carabiniere": "Carabiniere",
	"napuli.resident.carmela": "Carmela 'a pizzaiola",
	"napuli.resident.giusy": "Giusy",
	"napuli.resident.sofia": "Sofia",
	"napuli.cc.title": "CARABINIERI!",
	"napuli.cc.seen.theft": "T'hanno visto arrubbà. Curre!",
	"napuli.cc.seen.pizzo": "T'hanno visto piglià 'o pizzo. Curre!",
	"napuli.cc.seen.shooting": "Hanno sentuto 'e botte. Curre!",
	"napuli.cc.seen.assault": "Hai miso 'e mmane 'ncuollo a nu carabiniere?!",
	"napuli.cc.called": "Na vecchia 'a fenesta ha chiammato 'o 112...",
	"napuli.cc.worse": "Mo è peggio: 'e Carabinieri te vonno purtà dint'!",
	"napuli.cc.lost.title": "L'HAI SEMMENATE",
	"napuli.cc.lost": "Pe' stavota è jiuta bona.",
	"napuli.cc.fine.title": "MULTA",
	"napuli.cc.fine": "Documenti, prego. E pava.",
	"napuli.cc.fine.chat": "CARABINIERE: Furto 'e poco conto. Multa: %s euro. Nun ce fà vedé cchiù.",
	"napuli.cc.jail.title": "ARRESTATO",
	"napuli.cc.jail": "Jamme, dint' 'a caserma.",
	"napuli.cc.jail.chat": "CARABINIERE: Staje dint' p' %s secondi. Armi sequestrate: %s.",
	"napuli.cc.jail.left": "Dint' 'a cella: ancora %s secondi",
	"napuli.cc.free.title": "LIBERO",
	"napuli.cc.free": "Fà 'o bravo, mo.",
	"napuli.cc.assault": "Nun se tocca 'o carabiniere!",
	"napuli.cc.talk.wanted": "CARABINIERE: Fermo llà! Mani addò 'e pozzo vedé!",
	"napuli.cc.talk.0": "CARABINIERE: Buongiorno. Documenti, prego.",
	"napuli.cc.talk.1": "CARABINIERE: Tutto a posto? Circolare, circolare.",
	"napuli.cc.talk.2": "CARABINIERE: Stasera ce sta 'a partita. Niente casino, eh.",
	"napuli.cc.talk.3": "CARABINIERE: 'O casco 'ncopp' 'o motorino, mi raccomando.",
	"napuli.dialog.carmela.hello": "Uè! Trase, 'o furno è caldo. Na margherita?",
	"napuli.dialog.carmela.flirt": "Pe' te 'a pizza 'a faccio a forma 'e core.",
	"napuli.dialog.carmela.together": "T'aggio stipato 'a fetta cchiù bella. Assiettate.",
	"napuli.dialog.carmela.approach": "Carmela: %s! È asciuta 'a pizza fritta, curre!",
	"napuli.dialog.carmela.invite": "Dimane chiudo 'a pizzeria. Ce ne jammo a Mergellina, io e te?",
	"napuli.dialog.reply.listen_carmela": "'A pizzeria era 'e nonno. 'O furno l'ha fatto isso, pret' 'e Vesuvio.",
	"napuli.dialog.reply.joke_carmela": "Ahah! Statte zitto, ca me faje abbrucià 'a margherita!",
	"napuli.dialog.giusy.hello": "Uè, bello! Hai sentuto l'urdema canzona mia? È nu successo!",
	"napuli.dialog.giusy.flirt": "Si me guarde accussì, te dedico 'o prossimo pezzo.",
	"napuli.dialog.giusy.together": "Ammore! Stasera canto 'ncopp' 'o lungomare. Viene?",
	"napuli.dialog.giusy.approach": "Giusy: %s, ammore, sientete chesta!",
	"napuli.dialog.giusy.invite": "Vieni cu mme a 'o matrimonio addò canto sabato? Te faccio ballà.",
	"napuli.dialog.reply.listen_giusy": "Canto a 'e matrimoni e 'e comunioni. Nu juorno me sentono a Sanremo, vide.",
	"napuli.dialog.reply.joke_giusy": "AHAHAH! Si cchiù scemo 'e me! Mi piaci, mi piaci assaje.",
	"napuli.dialog.sofia.hello": "Ciao... scusa, sto studiando. Federico II, esame 'e storia 'e Napule.",
	"napuli.dialog.sofia.flirt": "Sai che cu tte me scordo pure 'e date 'e Masaniello?",
	"napuli.dialog.sofia.together": "Aggio pigliato 30! Te l'aggio vuluto dicere a te pe' primmo.",
	"napuli.dialog.sofia.approach": "Sofia: %s, me faje compagnia 'n biblioteca?",
	"napuli.dialog.sofia.invite": "Ce sta na mostra 'a Galleria stasera. Ce vulisse venì cu mme?",
	"napuli.dialog.reply.listen_sofia": "Napule è sotto 'e piere nuoste: grotte, cisterne, strade antiche. Me fa impazzì.",
	"napuli.dialog.reply.joke_sofia": "Ahah, carina... ma nun me distraì, ca tengo l'esame!"
};
const it = {
	"napuli.resident.carabiniere": "Carabiniere", "napuli.resident.carmela": "Carmela la pizzaiola", "napuli.resident.giusy": "Giusy", "napuli.resident.sofia": "Sofia",
	"napuli.cc.title": "CARABINIERI!", "napuli.cc.seen.theft": "Ti hanno visto rubare. Scappa!", "napuli.cc.seen.pizzo": "Ti hanno visto prendere il pizzo. Scappa!",
	"napuli.cc.seen.shooting": "Hanno sentito gli spari. Scappa!", "napuli.cc.seen.assault": "Hai messo le mani addosso a un carabiniere?!",
	"napuli.cc.called": "Una vecchia dalla finestra ha chiamato il 112...", "napuli.cc.worse": "Adesso è peggio: ti vogliono portare dentro!",
	"napuli.cc.lost.title": "LI HAI SEMINATI", "napuli.cc.lost": "Per stavolta è andata bene.", "napuli.cc.fine.title": "MULTA", "napuli.cc.fine": "Documenti, prego. E paga.",
	"napuli.cc.fine.chat": "CARABINIERE: Furto di poco conto. Multa: %s euro.", "napuli.cc.jail.title": "ARRESTATO", "napuli.cc.jail": "Andiamo, in caserma.",
	"napuli.cc.jail.chat": "CARABINIERE: Resti dentro per %s secondi. Armi sequestrate: %s.", "napuli.cc.jail.left": "In cella: ancora %s secondi",
	"napuli.cc.free.title": "LIBERO", "napuli.cc.free": "Fai il bravo, adesso.", "napuli.cc.assault": "Non si tocca un carabiniere!",
	"napuli.cc.talk.wanted": "CARABINIERE: Fermo lì! Mani in vista!", "napuli.cc.talk.0": "CARABINIERE: Buongiorno. Documenti, prego.",
	"napuli.cc.talk.1": "CARABINIERE: Tutto a posto? Circolare.", "napuli.cc.talk.2": "CARABINIERE: Stasera c'è la partita. Niente casino.",
	"napuli.cc.talk.3": "CARABINIERE: Il casco sul motorino, mi raccomando."
};
const en = {
	"napuli.resident.carabiniere": "Carabiniere", "napuli.resident.carmela": "Carmela the pizza maker", "napuli.resident.giusy": "Giusy", "napuli.resident.sofia": "Sofia",
	"napuli.cc.title": "CARABINIERI!", "napuli.cc.seen.theft": "They saw you stealing. Run!", "napuli.cc.seen.pizzo": "They saw you collecting the pizzo. Run!",
	"napuli.cc.seen.shooting": "They heard the shots. Run!", "napuli.cc.seen.assault": "You laid hands on a carabiniere?!",
	"napuli.cc.called": "An old lady at the window called 112...", "napuli.cc.worse": "Now it's worse: they want to lock you up!",
	"napuli.cc.lost.title": "YOU LOST THEM", "napuli.cc.lost": "You got away this time.", "napuli.cc.fine.title": "FINE", "napuli.cc.fine": "Papers, please. And pay.",
	"napuli.cc.fine.chat": "CARABINIERE: Petty theft. Fine: %s euros.", "napuli.cc.jail.title": "ARRESTED", "napuli.cc.jail": "Come on, to the station.",
	"napuli.cc.jail.chat": "CARABINIERE: You stay in for %s seconds. Weapons confiscated: %s.", "napuli.cc.jail.left": "In the cell: %s seconds left",
	"napuli.cc.free.title": "FREE", "napuli.cc.free": "Behave, now.", "napuli.cc.assault": "Don't touch a carabiniere!",
	"napuli.cc.talk.wanted": "CARABINIERE: Stop right there! Hands where I can see them!", "napuli.cc.talk.0": "CARABINIERE: Good morning. Papers, please.",
	"napuli.cc.talk.1": "CARABINIERE: All good? Move along.", "napuli.cc.talk.2": "CARABINIERE: The match is on tonight. No trouble.",
	"napuli.cc.talk.3": "CARABINIERE: Helmet on the scooter, please."
};
for (const [file, add] of [["nap_it", nap], ["it_it", Object.assign({}, nap, it)], ["en_us", Object.assign({}, nap, en)]]) {
	const j = JSON.parse(fs.readFileSync(R + file + ".json", "utf8"));
	Object.assign(j, add);
	fs.writeFileSync(R + file + ".json", JSON.stringify(j, null, "\t") + "\n");
}
console.log("texts2 done");
