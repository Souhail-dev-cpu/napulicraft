# Napoli ampliata · versione 32

L'atlante comprende **50 mete, di cui 45 nuove**, collegate alla città precedente. È una ricostruzione
Minecraft con scala adattata: nomi, quartieri, orientamento e collegamenti sono riferiti a Napoli;
le distanze, gli isolati secondari e i dettagli architettonici sono sintetizzati per il gioco.

## Dimensioni

Il perimetro precedente comprende città, centro sportivo e tutte le pendici del Vesuvio:
`x [-544,480)`, `z [-272,272)`, cioè **1.024 × 544 = 557.056 blocchi quadrati**.
Il nuovo perimetro è `x [-2048,2048)`, `z [-816,544)`:
**4.096 × 1.360 = 5.570.560 blocchi quadrati**, esattamente **10 volte l'area**.
Nord corrisponde a `-Z`, est a `+X`.

Le nuove zone vengono costruite nei chunk esplorati, con un massimo di quattro chunk per tick.
Non vengono precaricati tutti i quartieri all'avvio. Colline, costa, mare, passeggiate, strade,
isolati e giardini occupano l'espansione: l'aumento non consiste nello spostare soltanto il confine.

## Luoghi

| Zona | Aggiunte principali |
| --- | --- |
| Vomero | Piazza Vanvitelli, Castel Sant'Elmo con pianta a stella, Certosa di San Martino con chiostro, Villa Floridiana |
| Arenella | Piazza degli Artisti e Piazza Medaglie d'Oro |
| Chiaia | Piazza Amedeo, Piazza dei Martiri, Piazza Vittoria, Villa Comunale e Piazza Sannazaro |
| Posillipo | Palazzo Donn'Anna, Marechiaro, Parco Virgiliano e teatro archeologico del Pausilypon |
| Fuorigrotta | Stadio Diego Armando Maradona con campo e tribune, Mostra d'Oltremare e Piazzale Tecchio |
| Bagnoli | Pontile nord, Città della Scienza con planetario, panorama di Nisida |
| Soccavo e Pianura | Piazza Giovanni XXIII e Piazza San Giorgio |
| Zona nord | Parco Ciro Esposito e Corso Secondigliano |
| Stella e Sanità | MANN, Piazza Cavour, Basilica di Santa Maria della Sanità e Cimitero delle Fontanelle |
| Capodimonte e Foria | Catacombe di San Gennaro, Museo e Real Bosco di Capodimonte, Orto Botanico, Piazza Carlo III e Real Albergo dei Poveri |
| San Lorenzo e Vicaria | Duomo, Porta Capuana, Piazza Garibaldi con Napoli Centrale e Piazza Nazionale |
| Poggioreale | Centro Direzionale con torri e percorsi pedonali |
| Mercato | Piazza Mercato e Piazza del Carmine con basilica e campanile |
| Napoli est | Piazza Vincenzo Aprea a Ponticelli, Piazza De Franchis a Barra, Piazza Pacichelli a San Giovanni a Teduccio e Museo Ferroviario di Pietrarsa |

Il centro precedente, le proprietà, i posti di lavoro e il campo delle attività sportive rimangono
alle coordinate salvate. Lo Stadio Maradona è una nuova ricostruzione a ovest, a Fuorigrotta;
il campo precedente continua a ospitare le attività e il torneo dei quartieri.

## Come orientarsi

- `/napoli luoghi` oppure `/napoli mappa`: apre l'atlante con pagine di destinazioni.
- Seleziona una meta: compaiono nome, distanza e direzione durante il cammino.
- `/napoli meta maradona`, `/napoli meta capodimonte`, `/napoli meta garibaldi`: sceglie direttamente una destinazione.
- `/napoli annulla`: interrompe le indicazioni.

Le indicazioni sono salvate con il giocatore; consultare una meta non carica quel quartiere e non
teletrasporta il giocatore. I collegamenti principali hanno nomi reali, fra cui Via Foria, Via Posillipo,
Via Coroglio, Corso Garibaldi, Corso Meridionale e Corso San Giovanni a Teduccio.

I nuovi palazzi hanno ingressi, piani, scale a pioli, cucina, letti e arredi e sono accessibili come alloggi
pubblici, con un limite di cinque occupanti ordinari. Compravendita e inviti continuano a usare le proprietà
del catalogo precedente. I controlli sulle case usano un indice per chunk, per evitare di esaminare
tutti i palazzi a ogni ingresso. Vicino alle mete compaiono passanti, turisti o pescatori, con identificativi
persistenti e un numero massimo di due per luogo.

## Salvataggi

Il nucleo precedente è escluso dalle scritture dell'espansione. Ogni chunk costruito riceve una revisione
persistente e non viene ricostruito quando si torna nella zona. I chunk che contengono costruzioni del
giocatore o strutture preesistenti vengono conservati. La costruzione di un settore non scrive nei chunk
vicini: un monumento attraversato da un confine viene completato con le stesse regole su entrambi i lati.

## Fonti dei luoghi

L'espansione ora comprende anche famiglie persistenti, case arredate, botteghe con scorte locali e
collegamenti utilizzabili da NPC e giocatori. Comandi, configurazione e prove sono descritti nella
[guida ai quartieri e ai trasporti](QUARTIERI_E_TRASPORTI.md).

Nomenclatura e collocazione per quartiere consultate il **23 novembre 2026**:

- [Comune di Napoli, municipalità e mappa dei quartieri, deliberazione 2026](https://static-www.comune.napoli.it/wp-content/uploads/2026/01/DELIBERAZIONE-DI-CONSIGLIO-COMUNALE-N.-1-DEL-29-GENNAIO-2026.pdf).
- [Ministero della Cultura, Museo e Real Bosco di Capodimonte](https://cultura.gov.it/ente/museo-e-real-bosco-di-capodimonte).
- [Musei nazionali del Vomero, Castel Sant'Elmo e Certosa di San Martino](https://museinazionalivomero.cultura.gov.it/).
- [Città della Scienza, mappa del complesso](https://static-www.cittadellascienza.it/wp-content/uploads/2025/03/MAPPA_Citta_della_Scienza_2024-2025.pdf).
- [Comune di Napoli, stradario: Piazza De Franchis a Barra](https://www.comune.napoli.it/flex/files/1/6/f/D.919b788d6c7614a0838f/ALL_F.pdf).
- [Comune di Napoli, Piazza Vincenzo Aprea e Corso Ponticelli, novembre 2026](https://static-www.comune.napoli.it/wp-content/uploads/2026/11/allegato1794234889.pdf).
- [Diocesi di Pozzuoli, San Giorgio Martire, Piazza San Giorgio a Pianura](https://chieseitaliane.chiesacattolica.it/chieseitaliane/AccessoEsterno.do?codice=81070&mode=daPortaleOA&tipo=auto).

## Verifiche ripetibili

`./gradlew build` comprende controlli su rapporto delle aree, destinazioni, connessione della rete,
orientamento dei quartieri, colline, costa, generazione dei monumenti, modifiche del giocatore e capienza
delle nuove case. `./gradlew runAtlasTest` crea un mondo temporaneo, visita otto nuove zone sul client,
salva schermate `atlas-*.png`, apre l'atlante e controlla uno scaricamento e ricaricamento reale dei chunk.
Il task fallisce se non compare il marcatore `[ATLASTEST] ALL PASSED`.

Verifica del **23 novembre 2026**: build riuscita e **76 GameTest superati**, compresi gli arrivi
percorribili delle 45 nuove mete. Il client ha completato il giro di otto zone, aperto l'atlante,
selezionato una destinazione e conservato una modifica del giocatore dopo un vero scaricamento e
ricaricamento del quartiere. I log sono `.gradle/expansion-build.log` e `.gradle/expansion-client.log`.

Quando si modifica la geometria del generatore, usare un mondo GameTest nuovo: i chunk costruiti
nel mondo temporaneo `build/run/gameTest/world` conservano intenzionalmente i propri blocchi fra
esecuzioni. Il vecchio mondo di prova può essere archiviato prima di ripetere il comando.
