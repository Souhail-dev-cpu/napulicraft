# Napoli viva

La città usa un'anagrafe sul server: i cittadini continuano a vivere, lavorare, pagare e crescere anche quando
il loro chunk non è caricato. La versione 19 della mappa aggiunge il Rione Partenope a ovest di Mergellina.
L'aggiornamento viene applicato una volta anche ai mondi già esistenti.

## Abitanti e famiglie

Il valore iniziale è **120 cittadini persistenti**, oltre ai personaggi di trama già presenti. Ogni cittadino
ha identità stabile, nome, età, genere, famiglia, indirizzo, professione, denaro, scorte alimentari, personalità,
reputazione, relazioni, memoria degli ultimi eventi e bisogni. Fame, riposo, igiene, divertimento, socialità,
bisogni fisiologici, umore e salute guidano le attività. Tasto destro mostra il profilo.

Le famiglie hanno coppie adulte e figli. Nuove coppie cercano una casa e possono avere figli se hanno spazio,
denaro e scorte. Le nascite rispettano un intervallo di sette giorni e il limite di popolazione. I bambini
crescono, vanno a scuola nei giorni feriali e cercano un impiego da adulti. I neonati restano a casa.
La vita è accelerata: normalmente un giorno Minecraft equivale a un anno; `daysPerYear` permette di rallentarla.
Gli adulti vanno in pensione e alla morte proprietà e denaro passano ai familiari.

Le routine distinguono lavoro, scuola, spesa, cucina, pasti, bagno, TV, socialità, studio, gioco, sonno e cure.
Di sera le famiglie rientrano; medici e infermieri di guardia, autisti notturni e carabinieri hanno turni serali.
Gli NPC sportivi possono restare svegli per festeggiare il Napoli. Meteo, salute e bisogni cambiano le priorità.

## Case e arredi

Ogni casa può contenere **al massimo 5 occupanti complessivi**, sommando NPC e giocatori, bambini inclusi.
Il controllo è sul server e vale anche per alloggi pubblici, case preesistenti e ingressi tramite teletrasporto.
Chi arriva per sesto viene riportato fuori; un posto si libera quando qualcuno esce. Anche le nascite tengono
conto dei residenti e degli ospiti presenti, senza contare due volte gli NPC già materializzati.

Ci sono **35 immobili acquistabili**, con identificativi stabili: i sette indirizzi originari e `viva_1`–`viva_28`.
Le nuove case variano dimensioni, piani, pareti, distribuzione, balconi, terrazzi e arredi. Le famiglie iniziano
nelle corti residenziali e possono trasferirsi quando acquistano una casa. Gli alloggi iniziali non consumano
gli immobili disponibili ai giocatori.

Ogni immobile ha punti d'uso riconosciuti per cucina, bagno, soggiorno, camera matrimoniale, camera dei figli,
studio e lavanderia. Sono presenti crafting, affumicatore, fornace, forno a legna della mod, frigo con
inventario, tavolo, divano, TV, lavabo, WC, doccia, letti matrimoniali e singoli, armadi con inventario e scrivania.
Frigo e armadio usano inventari vanilla; gli alimenti conservano il normale comportamento Minecraft.
I mobili del bagno e la lavatrice recuperano igiene. Gli NPC usano gli stessi punti per le loro routine.

Usa il cartello davanti a una casa per acquistare, affittare o chiedere un mutuo. Proprietà e accesso sono
controllati dal server anche in multiplayer. Le case occupate non possono essere acquistate una seconda volta.

| Comando | Uso |
| --- | --- |
| `/casa` | Elenco degli immobili e coordinate |
| `/casa compra ID` | Acquisto in contanti, davanti al cartello |
| `/casa affitta ID` | Affitto con acconto |
| `/casa mutuo ID` | Acconto del 20%, interesse totale del 10%, 12 rate |
| `/casa paga ID` | Pagamento delle rate e bollette in scadenza |
| `/casa lascia ID` | Chiude un affitto dopo aver saldato gli arretrati |
| `/casa vendi ID` | Rivendita all'80%, saldando prima il debito |
| `/casa vai ID` | Raggiunge l'ingresso della propria casa |

La scadenza finanziaria è ogni sette giorni di gioco. Le rate includono 2 € di bollette; il server prova il
pagamento automatico dal portafoglio. Tre scadenze insolute portano alla chiusura del contratto e al rilascio
della casa per affitti e mutui ancora da estinguere. Gli NPC possono comprare in contanti o aprire un mutuo e usare anche il reddito del partner per pagarlo.

## Professioni ed economia

Il catalogo comprende **85 professioni** in dieci categorie con luoghi di lavoro, strumenti, paga, turni e
posti limitati. Ci sono ristorazione, commercio, servizi, pubblico, trasporti, cultura e media, edilizia,
uffici, sanità e sicurezza. I lavoratori guadagnano esperienza, ottengono promozioni se ci sono posti e
possono essere licenziati per assenze ripetute. I carabinieri hanno otto gradi, da recluta a comandante.
I contratti giocabili allo sportello `/lavoro` restano gli otto incarichi cittadini con mansioni concrete.

Gli stipendi alimentano il portafoglio; spese quotidiane, cibo, utenze e casa lo riducono. I prezzi dei prodotti
delle nuove botteghe e delle scorte degli NPC reagiscono al mercato e ai festeggiamenti.

- **Venditore d'aria di Napoli**: vende un'ampolla consumabile, con Fortuna per 20 secondi.
- **Influencer adulta dai capelli rosa**: vende acqua da bagno personale fittizia. Bere una bottiglia
  concede **Speed I per 30 secondi** e **Fame I per 30 secondi**, con consumo della bottiglia.

Tasto destro apre l'offerta. Gli acquisti richiedono presenza del venditore, vicinanza, turno aperto e denaro.
Gli articoli sono disponibili anche nella scheda creativa della mod.

## TV, calendario ed eventi

Clic sulla TV accende o spegne; Shift + clic cambia canale. Sport, notizie e meteo hanno una schermata dedicata.
Il canale sportivo mostra una **partita fittizia animata**, con campo, giocatori, pallone e risultato.
Il calendario predefinito propone Napoli–Juventus ogni sette giorni, dalle 13000 alle 15000 unità del giorno.
Risultati e avversari sono configurabili e non dipendono dal calendario calcistico reale.

Le vittorie attivano festeggiamenti serali, richiami «FORZA NAPOLI!», suoni di esultanza, clacson dei motorini
vicini, effetti pirotecnici e maggiore spesa. Le sconfitte hanno una reazione più tranquilla.
Il calendario cittadino alterna mercati, concerti, feste, beneficenza, scioperi, assemblee, esercitazioni,
allerte meteo, lavori e inaugurazioni. `/citta` mostra giorno, popolazione, professioni e reputazione.

## Traffico e prestazioni

Auto, bus e tram seguono tragitti automatici nel Rione Partenope. Si fermano davanti ai pedoni e agli altri
mezzi, rispettano le fermate e sospendono il servizio durante gli scioperi. Tasto destro permette di salire;
Shift fa scendere. I motorini esistenti restano utilizzabili con i normali controlli della mod.

La simulazione usa tre distanze: vicina, intermedia e lontana. Le decisioni di navigazione avvengono ogni
40, 160 o 600 tick; oltre 128 blocchi un cittadino può tornare alla simulazione dei dati. Bisogni e inventari
si aggiornano normalmente ogni 600 tick (30 secondi). I dati degli abitanti lontani si aggiornano senza
richiedere chunk o calcolare percorsi. I salti del calendario vengono elaborati in gruppi limitati.

## Configurazione e verifiche

Il file server `config/napulicraft-city.json` viene creato al primo avvio. Modifica e riavvia per applicare:

```json
{
  "population": 120,
  "maximumPopulation": 160,
  "needsIntervalTicks": 600,
  "daysPerYear": 1,
  "retirementAge": 70,
  "maximumAge": 95,
  "fixtures": [{
    "opponent": "Juventus",
    "everyDays": 7,
    "dayOffset": 0,
    "kickoff": 13000,
    "fullTime": 15000,
    "napoliGoals": -1,
    "opponentGoals": -1
  }]
}
```

`-1` sceglie il risultato dal calendario simulato. Il limite massimo dei cittadini può essere portato fino
a 512; il server limita automaticamente valori e intervalli invalidi.

`./gradlew build` include i GameTest per anagrafe, salvataggi, eredità, crescita, routine, cataloghi e rate.
`./gradlew runLivingCityTest` crea un mondo usa e getta e verifica case generate, punti d'uso raggiungibili,
mutui, pagamenti unici, TV sul client, traffico ed effetti della bottiglia. Salva schermate in
`run-autotest/screenshots/living-city-*.png` e fallisce se la verifica non termina correttamente.
