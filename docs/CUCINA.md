# Cucina napoletana

Tutti gli alimenti richiesti sono disponibili in sopravvivenza, nel ricettario e nella scheda creativa di Napulicraft.
Le ricette non richiedono reputazione o quest. Gli ingredienti nuovi si ottengono da risorse vanilla rinnovabili.
La [lista completa delle 71 ricette](CUCINA_RICETTE.md) indica quantità, ingredienti e postazioni.

## Preparazione

La crafting table prepara ingredienti, impasti e pietanze crude. La fornace cuoce una preparazione cruda in
10 secondi usando il normale combustibile vanilla. Restano disponibili anche le ricette originali di pizza e caffè.

Le quattro postazioni si possono costruire, piazzare, raccogliere e trasportare. Con il tasto destro si apre
un ricettario a pagine: scegli una pietanza, controlla gli ingredienti e premi **Prepara**.

| Postazione | Utilizzo | Ricetta della postazione |
| --- | --- | --- |
| Forno a Legna Portatile | Cuoce pizze, dolci e piatti pronti da cuocere | Fornace + 2 mattoni + lingotto di ferro |
| Tavolo da Pizzaiolo | Ingredienti, impasti, preparazioni crude e insalate | Crafting table + pietra liscia + ciotola |
| Banca della Frittura | Produce olio e cuoce le preparazioni fritte | Calderone + fornace + lingotto di ferro |
| Banca del Caffè | Prepara chicchi, rum da cucina, caffè, limoncello e acqua di cocomero | Crafting table + lingotto di rame + bottiglia + cacao |

Le cotture nelle postazioni consumano **una unità di carbone, carbonella, tronco o assi** per porzione.
La produzione di ingredienti e bevande non richiede combustibile. Le postazioni usano i primi 36 slot dello zaino;
secchi e bottiglie degli ingredienti vengono restituiti. Se mancano ingredienti, combustibile o spazio per risultato
e contenitori, non viene consumato nulla. Anche altri giocatori possono usare la stessa postazione.

## Alimenti ed effetti

I tempi sono in secondi di gioco: con un server rallentato durano più a lungo in tempo reale.
Gli alimenti speciali si possono consumare anche a fame piena; le porzioni vengono consumate normalmente.

| Alimento | Effetto |
| --- | --- |
| Pizza | 10 punti fame; mantiene la ricetta originale e aggiunge la cottura da impasto, pomodoro e mozzarella |
| Pizza a Portafoglio | 7 punti fame; si mangia in 0,6 s continuando a correre senza rallentamento d'uso |
| Borsa del Fritto | Contiene inizialmente 3 porzioni di Fritto misto; tasto destro per estrarne una. Rigenera 1 porzione al minuto, fino a 8, mentre è nello zaino |
| Sfogliatella Riccia | Fortuna I–II per una durata casuale di 30–90 s |
| Sfogliatella Frolla | Rara; Fortuna III per 120 s |
| Babà al Rum | Velocità II e Salto II per 15 s; poi Nausea per 10 s e Lentezza per 5 s |
| Pastiera Napoletana | Cura 4 cuori e dà Rigenerazione II per 20 s |
| Zeppole di San Giuseppe | Fortuna I per 90 s |
| Struffoli | Cura 2 cuori e dà Rigenerazione I per 10 s a chi mangia e ai compagni di squadra entro 16 blocchi |
| Roccocò | Resistenza I per 60 s |
| Mustaccioli | Resistenza II per 45 s; applica Debolezza per 30 s ai mostri entro 12 blocchi, escludendo i Munacielli addomesticati |
| Casatiello | Forza I per 60 s |
| Lasagne di Carnevale | 14 punti fame, sazietà alta; Lentezza I per 20 s |
| Chiacchiere | Si mangiano in 0,4 s continuando a correre; Velocità II e Salto I per 20 s |
| Spaghetti alle Vongole | Grazia del Delfino per 60 s e Respirazione Acquatica per 30 s |
| Baccalà alla Napoletana | Cura 4 cuori, rimuove gli effetti negativi e dà Rigenerazione I per 10 s |
| Capitone Fritto | Raro; Forza III, Velocità III e Resistenza II per 20 s. Rischio del 35% di sazietà estrema dopo il bonus: Lentezza III 20 s, Debolezza I 10 s e aumento della barra |
| Minestra Maritata | Rigenerazione I per 90 s |
| Insalata di Rinforzo | Resistenza I per 60 s |
| Salsiccia e Friarielli | Forza I per 60 s |
| Gattò di Patate | Cura 3 cuori e dà Rigenerazione I per 10 s; 11 punti fame e sazietà alta |
| Sartù di Riso | Sblocca la Tavoletta dell'Enigma craftabile |
| Caffè Napoletano | Rimuove Fatica da scavo e Lentezza; mantiene Velocità I e Scavo rapido II per 90 s |
| Limoncello | Fortuna I per 45 s. Tre bicchieri nel periodo di un minuto causano Nausea 20 s, Lentezza 10 s e Salto II 10 s: ubriachezza comica |
| Acqua di Cocomero | Abbassa il calore di 60; impedisce di accumularlo per 2 minuti, spegne le fiamme e dà Resistenza al Fuoco per 30 s |

Gli Struffoli usano le **squadre vanilla dello scoreboard**, configurabili con `/team`; curano chi mangia anche
senza squadra. I giocatori vicini che appartengono ad altre squadre non ricevono la cura.
Ciotole e bottiglie delle porzioni vengono restituite dopo il consumo.
La Borsa conserva contenuto e progresso di rigenerazione nel salvataggio; non si ricarica istantaneamente durante il logout.

La Tavoletta dell'Enigma si costruisce con **mattoni di pietra + redstone + libro**. Dopo aver mangiato un Sartù,
usala e risolvi i tre passaggi della ricetta della pizza. La ricompensa è di **3 smeraldi una sola volta per giocatore**;
un tentativo sbagliato si può ripetere. Sblocco e ricompensa riscossa si conservano dopo morte e riavvio.

## Reputazione da Pizzaiolo

Preparare pizze e pizze a portafoglio nella crafting table, nella fornace o nelle postazioni incrementa un conteggio
personale persistente. Comprare o ricevere una pizza non assegna reputazione.

| Pizze preparate | Aiutanti nelle quest |
| --- | --- |
| 5 | 1 NPC |
| 15 | 2 NPC |
| 30 | 3 NPC |

Quando accetti una missione dai clan, i pizzaioli arrivano per 2 minuti, ti danno provviste e difendono il giocatore
che ha iniziato la quest dagli aggressori. Il supporto ha una pausa di 3 minuti per impedire evocazioni ripetute
annullando le missioni. La reputazione sopravvive a morte e riavvio ed è visibile nel nuovo pannello.

## Fame Napoletana e calore

La barra aggiuntiva **Fame Napoletana** rappresenta la sazietà eccessiva, da 0 a 60, e funziona in tutte le dimensioni.
I fritti consecutivi la fanno salire progressivamente più in fretta; un altro cibo interrompe la serie.
Da 16 in poi aumenta gradualmente il peso e diminuisce la velocità; da 50 applica una breve Lentezza II e avvisa
della sazietà estrema. Digerisci 1 punto ogni 10 secondi. La normale barra di fame continua a funzionare.

La barra **Calore** sale nel Nether, nei biomi caldi al sole e quando bruci. Ombra, biomi freschi, acqua e pioggia la
riducono. Da 80 dà Debolezza finché non ti rinfreschi. L'acqua di cocomero offre un modo rapido per abbassarla.
Il pannello mostra anche quante pizze hai preparato; il tasto **H** lo nasconde insieme al pannello della città.

## Munaciello burlone e addomesticabile

Il Munaciello prende **un singolo oggetto** e prova a nasconderlo nell'ultima chest che hai effettivamente aperto,
se è caricata e si trova entro 64 blocchi. Deposita anche 1–3 **Monete del Munaciello**: tasto destro su una moneta
per aggiungere 1 € al tuo portafoglio. Ricevi le coordinate della chest in chat.

Se non trova una chest adatta o è piena, entro 40 secondi restituisce l'oggetto al proprietario; se è offline lo
lascia a terra. Anche un cambio a difficoltà Pacifica restituisce prima il bottino.

Tienigli in mano un dolce e dagli **tre porzioni con il tasto destro**: si fermerà per riceverle e diventerà tuo amico.
Vanno bene i nuovi dolci, biscotti vanilla e torta di zucca. Da addomesticato ti segue, non ruba e non scompare
con la distanza o in Pacifica. Altri giocatori non possono cambiarne il proprietario; altri dolci del proprietario
gli curano la salute. Proprietario, fiducia e bottino vengono salvati.

## Verifica e risorse

`./gradlew build` esegue i test server di ricette, cottura effettiva, inventari atomici, effetti, squadre, progressione,
borsa e Munaciello. `./gradlew runKitchentest` usa un client reale per menu e conferme, consumo in corsa,
effetti ritardati, enigma e addomesticamento; salva immagini in `run-autotest/screenshots`.

`python tools/generate_kitchen.py` rigenera catalogo, ricette, ricettario, modelli e traduzioni.
`java tools/GenKitchenTextures.java` rigenera le texture pixel art nello stile delle icone già presenti nella mod.
