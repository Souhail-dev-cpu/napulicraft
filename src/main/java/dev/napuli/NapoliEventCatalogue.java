package dev.napuli;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import dev.napuli.NapoliNeighbourhoodEvents.Quiz;
import dev.napuli.NapoliNeighbourhoodEvents.Scene;
import dev.napuli.NapoliNeighbourhoodEvents.Venue;

final class NapoliEventCatalogue {
    private NapoliEventCatalogue() {}

    static final List<Venue> VENUES = List.of(
            new Venue("plebiscito", 10500, SoundEvents.NOTE_BLOCK_BELL.value(), ParticleTypes.NOTE, List.of(
                    quiz("Tombola sotto il portico", "Quale numero della smorfia è associato alla paura?", Items.PAPER, "90", "47", 1),
                    gift("Girotondo delle luminarie", "Si accendono le prime luci intorno al colonnato.", Items.CANDLE),
                    gift("Mandolini al tramonto", "Un mandolinista fa provare la prima battuta a chi si ferma.", Items.PAPER),
                    trade("Cartoline dal Plebiscito", "Il disegnatore scambia un foglio per una cartolina della piazza.", Items.MAP, Items.PAPER),
                    gift("Caffè sospeso in piazza", "Al banco si lascia un caffè già pagato per chi passa dopo.", Napuli.CAFE),
                    trade("Bandiere ai balconi", "Un gruppo prepara festoni azzurri per la sera.", NapoliMonumentInteriors.material("light_blue_banner").asItem(), Items.LAPIS_LAZULI),
                    quiz("La piazza bendata", "Quale edificio guarda la basilica attraverso la piazza?", Items.BOOK, "Il MANN", "Il Palazzo Reale", 2),
                    gift("Pulcinella sotto le arcate", "Il burattinaio improvvisa un battibecco con un piccione.", Items.COOKIE),
                    gift("Granita di fine passeggiata", "Il chiosco offre un assaggio a chi si ferma dopo la passeggiata.", Items.MELON_SLICE),
                    gift("Collanine del buon augurio", "Una bancarella infila perline rosse e racconta chi le indossava.", Napuli.CORNICELLO))),
            new Venue("mergellina", 9500, SoundEvents.NOTE_BLOCK_FLUTE.value(), ParticleTypes.FISHING, List.of(
                    gift("Serenata di Piedigrotta", "Sul lungomare fanno le prove della serenata.", Items.PAPER),
                    trade("Reti da rammendare", "Il pescatore cerca un filo per chiudere uno strappo nella rete.", Items.COD, Items.STRING),
                    gift("Il primo cuoppo", "Al chiosco gira un assaggio della frittura del giorno.", Items.COOKED_COD),
                    quiz("La scarpetta di Piedigrotta", "Nella leggenda, chi ritrova la scarpetta sulla riva?", Items.RABBIT_HIDE, "Un mugnaio", "Un pescatore", 2),
                    trade("Nodi da marinaio", "Al molo insegnano un nodo a chi porta un pezzo di corda.", Items.LEAD, Items.STRING),
                    gift("Cartolina dal golfo", "Un pittore abbozza il panorama da una panchina.", Items.MAP),
                    trade("Barchette di carta", "Sul muretto piegano le barchette e si sfidano a farle galleggiare.", Items.LAPIS_LAZULI, Items.PAPER),
                    trade("Il secchio dei pescatori", "Per la zuppa del molo manca un merluzzo.", Items.COOKED_SALMON, Items.COD),
                    quiz("La canzone della festa", "Quale festa ha dato il nome alla rassegna storica della canzone napoletana sul lungomare?", Items.PAPER, "Piedigrotta", "La sagra del grano", 1),
                    gift("Limoni sul muretto", "Al chiosco tagliano gli agrumi per le bevande della sera.", Items.HONEY_BOTTLE))),
            new Venue("duomo", 2800, SoundEvents.NOTE_BLOCK_CHIME.value(), ParticleTypes.WAX_ON, List.of(
                    trade("Candele di San Gennaro", "Alla fine della processione si raccolgono candele di ricambio.", Items.POPPY, Items.CANDLE),
                    trade("Pastori da dipingere", "Gli artigiani dei vicoli cercano colore per i mantelli delle statuine.", Items.BRICK, Items.POPPY),
                    quiz("Il patrono", "Chi è il santo patrono celebrato al Duomo di Napoli?", Items.CANDLE, "San Gennaro", "San Petronio", 1),
                    gift("Bottega del presepe", "Un artigiano mostra come abbozza le figure nell'argilla.", Items.CLAY_BALL),
                    gift("Campane sopra i vicoli", "Da un portone arriva il rintocco e il vicolo si ferma un attimo.", Items.CANDLE),
                    trade("Stelle per la grotta", "In bottega ritagliano stelle di carta per il presepe.", Items.GLOW_INK_SAC, Items.PAPER),
                    quiz("La via dei pastori", "Quale via è conosciuta per le botteghe del presepe?", Items.FLOWER_POT, "Via Caracciolo", "San Gregorio Armeno", 2),
                    trade("L'edicola fiorita", "I vicini raccolgono fiori per la piccola edicola del vicolo.", Items.CANDLE, Items.POPPY),
                    gift("Il liutaio del decumano", "Un artigiano fa ascoltare la differenza tra due corde appena montate.", Items.STRING),
                    gift("Suono di festa", "I campanari provano il richiamo prima di aprire il portone.", Items.NOTE_BLOCK))),
            new Venue("mercato", 1400, SoundEvents.NOTE_BLOCK_HAT.value(), ParticleTypes.HAPPY_VILLAGER, List.of(
                    trade("Spesa nel panaro", "Una signora cala il cestino e cerca una mela per la merenda.", Items.COOKIE, Items.APPLE),
                    gift("Banco delle arance", "Il fruttivendolo offre uno spicchio agli ultimi arrivati.", Items.GOLDEN_CARROT),
                    trade("Pane per il vicolo", "Il fornaio raccoglie grano per un'infornata da dividere con il vicinato.", Items.BREAD, Items.WHEAT),
                    quiz("Sfida del venditore", "Che cosa si vende soprattutto a Porta Nolana secondo la tradizione delle feste?", Items.COD, "Libri usati", "Pesce", 2),
                    gift("Ultimo pezzo di pizza", "Il pizzaiolo taglia l'ultima teglia in piccoli assaggi.", Napuli.PIZZA),
                    trade("Calze della Befana", "Al banco riempiono le calze di dolci e cercano stoffa.", Items.COOKIE, NapoliMonumentInteriors.material("white_wool").asItem()),
                    gift("La voce più forte", "I venditori fanno a gara a chi inventa il richiamo più buffo.", Items.PAPER),
                    trade("Mercato dei semi", "Il banco si scambia semi da conservare per l'orto di quartiere.", Items.PUMPKIN_SEEDS, Items.WHEAT_SEEDS),
                    quiz("La moneta nel banco", "Che unità usavano le Due Sicilie prima della lira italiana?", Items.GOLD_NUGGET, "Il ducato", "Il franco svizzero", 1),
                    gift("Fiore per la fruttivendola", "Un fruttivendolo molla il banco un momento per farsi disegnare con un fiore.", Items.ORANGE_TULIP))),
            new Venue("teatro_san_carlo", 9000, SoundEvents.NOTE_BLOCK_HARP.value(), ParticleTypes.NOTE, List.of(
                    gift("Prove dietro le quinte", "Dalla porta laterale si sente l'orchestra accordarsi.", Items.PAPER),
                    trade("Sipario da rammendare", "I costumisti cercano uno scampolo rosso per una prova.", Items.STRING, NapoliMonumentInteriors.material("red_wool").asItem()),
                    quiz("La sala reale", "Quale teatro sorge accanto al Palazzo Reale di Napoli?", Items.BOOK, "La Scala", "Il San Carlo", 2),
                    gift("Il bozzetto del costumista", "Un bozzetto di scena passa di mano in mano sul sagrato.", Items.PAPER),
                    trade("Sedie per la filodrammatica", "La compagnia del quartiere cerca legno per una piccola prova aperta.", Items.BREAD, Items.OAK_PLANKS),
                    gift("Il tenore e il caffè", "Tra due prove il tenore racconta come gli tremava la voce al debutto.", Napuli.CAFE),
                    quiz("L'orchestra invisibile", "Quale famiglia di strumenti comprende il violino?", Items.STRING, "Archi", "Ottoni", 1),
                    trade("Fiori per il corista", "I vicini preparano un mazzetto per il primo saggio del corista.", Items.WHITE_TULIP, Items.DANDELION),
                    gift("Danza sotto il portico", "Una coppia ripassa i passi e invita a battere il tempo.", Items.COOKIE),
                    gift("Abbonamento in miniatura", "Una scenografa piega piccoli programmi da portare a casa.", Items.BOOK))),
            new Venue("vanvitelli", 6400, SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), ParticleTypes.HAPPY_VILLAGER, List.of(
                    quiz("Tombola del Vomero", "Quale numero si associa a Pulcinella nella smorfia napoletana?", Items.PAPER, "12", "75", 2),
                    trade("Il balcone dei gerani", "Nel cortile preparano vasi di riserva per i nuovi vicini.", Items.FLOWER_POT, Items.CLAY_BALL),
                    quiz("Corsa alla funicolare", "Quale mezzo collega vari punti collinari e il Vomero al centro?", Items.RAIL, "La funicolare", "Il traghetto", 1),
                    trade("Libri sulle panchine", "Un gruppo lascia libri da prendere e cerca fogli per i segnalibri.", Items.BOOK, Items.PAPER),
                    gift("Merenda in piazza Vanvitelli", "Dopo la scuola condividono la merenda sulle panchine.", Items.COOKIE),
                    trade("Stendipanni creativo", "Tra due balconi appendono piccoli festoni ricavati da ritagli.", Items.DANDELION, Items.STRING),
                    gift("Foto vecchie del Vomero", "Una vicina mostra un album di quando in piazza passavano poche auto.", Items.PAPER),
                    quiz("Camminata alla Certosa", "Quale castello domina la collina accanto a San Martino?", Items.BOOK, "Castel dell'Ovo", "Castel Sant'Elmo", 2),
                    gift("Il vaso di basilico", "L'ortolano divide le piantine prima di chiudere.", Items.FLOWER_POT),
                    trade("Calzini spaiati", "Al banchetto del riciclo cercano lana per rifare gli occhi di un pupazzo.", Items.STICK, NapoliMonumentInteriors.material("white_wool").asItem()))),
            new Venue("villa_comunale", 5200, SoundEvents.NOTE_BLOCK_FLUTE.value(), ParticleTypes.HAPPY_VILLAGER, List.of(
                    gift("Passeggiata fra le aiuole", "Un giardiniere lascia semi da piantare al prossimo giro.", Items.WHEAT_SEEDS),
                    trade("Picnic in Villa", "Il tavolo comune raccoglie cibo per chi porta un amico.", Items.COOKIE, Items.BREAD),
                    quiz("La Villa e il mare", "Quale promontorio a ovest è legato a Marechiaro e al Parco Virgiliano?", Items.MAP, "Posillipo", "Il Vesuvio", 1),
                    trade("Fiori per la scalinata", "I giardinieri scambiano tulipani con i vicini.", Items.PINK_TULIP, Items.WHITE_TULIP),
                    gift("Soggetti da acquerello", "Gli allievi di disegno si siedono all'ombra ad aspettare la luce giusta.", Items.LAPIS_LAZULI),
                    gift("Il volo del gabbiano", "Il pittore fa passare una piuma raccolta sulla passeggiata.", Items.FEATHER),
                    trade("Una borsa dimenticata", "Un vicino premia chi aiuta a rifare il sacchetto strappato della spesa.", Items.APPLE, Items.STRING),
                    quiz("Villa del passeggio", "A quale quartiere appartiene la Villa Comunale?", Items.PAPER, "Scampia", "Chiaia", 2),
                    gift("Tè in giardino", "Il chiosco condivide una bevanda dolce durante la sosta.", Items.HONEY_BOTTLE),
                    trade("Il piccolo erbario", "I giardinieri fanno un erbario per il doposcuola e cercano una margherita.", Items.PAPER, Items.OXEYE_DAISY))),
            new Venue("maradona", 7800, SoundEvents.NOTE_BLOCK_COW_BELL.value(), ParticleTypes.HAPPY_VILLAGER, List.of(
                    gift("Sciarpe alzate", "I tifosi provano un coro e distribuiscono il colore della squadra.", Items.BLUE_ORCHID),
                    trade("Maglia da rattoppare", "Al banchetto cercano lana azzurra per riparare lo striscione.", NapoliMonumentInteriors.material("light_blue_banner").asItem(), NapoliMonumentInteriors.material("light_blue_wool").asItem()),
                    quiz("Il vecchio nome dello stadio", "Prima di essere intitolato a Maradona, come si chiamava lo stadio?", Items.PAPER, "San Paolo", "San Siro", 1),
                    gift("Merenda del settore", "Un chiosco divide una teglia prima dell'apertura dei cancelli.", Napuli.PIZZA),
                    trade("Coreografia della curva", "Gli organizzatori cercano altra carta per le bandierine.", Items.BLUE_ORCHID, Items.PAPER),
                    gift("Un nastro da capitano", "I ragazzi preparano le squadre e fanno passare il nastro.", Items.POPPY),
                    quiz("Il piede del Diez", "Con quale piede calciava prevalentemente Maradona?", Items.PAPER, "Destro", "Sinistro", 2),
                    gift("Prova del cronometro", "L'allenatore mostra la sua tabella dei tempi dopo la staffetta.", Items.PAPER),
                    trade("Il pallone da cucire", "Un volontario cerca cuoio per una riparazione al pallone.", Items.STRING, Items.LEATHER),
                    gift("Corri con me", "Al traguardo della staffetta offrono una merenda.", Items.BAKED_POTATO))),
            new Venue("pietrarsa", 3500, SoundEvents.NOTE_BLOCK_BELL.value(), ParticleTypes.SMOKE, List.of(
                    gift("Fischio della locomotiva", "Dalla banchina imitano il fischio delle locomotrici a vapore.", Items.PAPER),
                    trade("Volontari in officina", "Gli addetti cercano del carbone da mostrare con la locomotiva.", Items.COPPER_INGOT, Items.COAL),
                    quiz("Binari sul golfo", "Quale città collegava la prima ferrovia partendo da Napoli?", Items.RAIL, "Bari", "Portici", 2),
                    gift("Biglietti del tempo", "Il gruppo del museo stampa riproduzioni di vecchi biglietti.", Items.PAPER),
                    gift("Il modellino del deposito", "Una visita al modellino spiega come entravano le carrozze in officina.", Items.RAIL),
                    trade("Pulizia delle targhe", "Per esporre una targa manca un pezzetto di rame al restauratore.", Items.IRON_NUGGET, Items.COPPER_INGOT),
                    quiz("Vapore o diesel", "Che cosa muoveva le prime locomotive della Napoli–Portici?", Items.COAL, "Il vapore", "Un motore diesel", 1),
                    trade("Il giardino sui binari", "Nel giardino del museo fanno lo scambio degli agrumi in vaso.", Items.FLOWER_POT, Items.OAK_SAPLING),
                    gift("Il quaderno del macchinista", "Un ferroviere in pensione disegna il suo primo treno sui quaderni dei visitatori.", Items.BOOK),
                    gift("Annuncio dalla banchina", "I volontari provano un annuncio come ai tempi della stazione.", Items.PAPER))),
            new Venue("scienza", 10000, SoundEvents.NOTE_BLOCK_CHIME.value(), ParticleTypes.ENCHANT, List.of(
                    gift("Il cielo di Coroglio", "Il Planetario prova una proiezione del cielo sul golfo.", Items.SPYGLASS),
                    trade("Corpi in movimento", "Il laboratorio raccoglie fogli per disegnare lo scheletro umano.", Items.BOOK, Items.PAPER),
                    quiz("L'atlas del corpo", "Come si chiama il museo dedicato al corpo umano a Città della Scienza?", Items.PAPER, "Pietrarsa", "Corporea", 2),
                    trade("Calco di conchiglia", "Al laboratorio serve argilla per un nuovo stampo.", Items.NAUTILUS_SHELL, Items.CLAY_BALL),
                    quiz("Piccole maree", "Quale astro, insieme al Sole, è il principale responsabile delle maree terrestri?", Items.PRISMARINE_SHARD, "La Luna", "Marte", 1),
                    gift("Caccia alle costellazioni", "Una guida mostra dove guardare per distinguere le stelle più luminose.", Items.MAP),
                    trade("Polline al microscopio", "Offrono un preparato da osservare a chi porta un fiore.", Items.HONEYCOMB, Items.DANDELION),
                    gift("Carta del mare", "Al termine della visita consegnano una carta delle specie costiere.", Items.PAPER),
                    trade("Il colore che mancava", "Un banco di esperimenti cerca un pigmento blu per completare le prove.", Items.ALLIUM, Items.LAPIS_LAZULI),
                    quiz("La pupilla al buio", "Cosa fa la pupilla appena la luce diminuisce?", Items.GLOW_INK_SAC, "Si restringe", "Si allarga", 2)))
    );

    private static Scene gift(String title, String description, Item reward) {
        return new Scene(title, description, reward, null, null);
    }

    private static Scene trade(String title, String description, Item reward, Item donation) {
        return new Scene(title, description, reward, donation, null);
    }

    private static Scene quiz(String title, String question, Item reward, String first, String second, int answer) {
        return new Scene(title, question, reward, null, new Quiz(first, second, answer));
    }
}
