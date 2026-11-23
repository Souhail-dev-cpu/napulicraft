# Quartieri abitati e trasporti

L'espansione comprende **19 quartieri abitati**, con **38 case assegnabili**, **19 botteghe** e fino a
**76 nuovi residenti**. Ogni famiglia iniziale ha due adulti e due bambini. La seconda casa del quartiere
resta disponibile alla crescita della popolazione. Identità, parentela, proprietà, necessità, denaro,
impiego e posizione appartengono al censimento persistente della città.

Ogni quartiere ha un fornaio e un secondo lavoratore: insegnante, medico, netturbino o corriere.
Il fornaio raggiunge la propria bottega, preleva ingredienti e produce pane; il corriere serve le
abitazioni della zona e il netturbino raccoglie i rifiuti. Scuola e ambulatorio scelgono il professionista
di quartiere più vicino, con presenza, turno e salute effettivi. In assenza di personale locale resta
disponibile il servizio del nucleo originale. I bambini vanno a scuola; pasti, riposo, igiene, faccende,
visite e incontri usano gli stessi sistemi dei residenti originali.

Le case seguono la quota del terreno e hanno frigorifero, cucina, bagno, letti per adulti e bambini,
lavatrice, studio e televisione. La capienza ordinaria rimane di cinque NPC e giocatori complessivi;
gli inviti autorizzati continuano a usare i posti ospite. I locali commerciali non vengono contati come
case. I vecchi passanti decorativi dell'espansione vengono rimossi quando si caricano: i nuovi abitanti
hanno identità e routine reali.

## Popolazione e partite esistenti

In `config/napulicraft-city.json`, `districtPopulation` limita le aggiunte dei quartieri; `maximumPopulation`
rimane il tetto complessivo. I valori predefiniti sono rispettivamente 76 e 384, ma i 19 nuclei iniziali
richiedono solo 76 aggiunte. Un file di configurazione esistente conserva il proprio tetto: con 120
residenti e un massimo di 160 vengono aggiunte al più 40 persone, distribuendo prima i negozianti e
poi gli altri adulti. Per aggiungere tutte le famiglie occorre spazio per almeno 196 residenti iniziali.
Il sistema non ricrea abitanti deceduti e non duplica quelli già registrati.

Case, arredi e infrastrutture vengono completati nei chunk esplorati, con lavoro limitato per tick.
Non viene caricata l'intera città per mantenere attivi gli abitanti lontani. La simulazione conserva
percorsi, tempi e produzione anche fuori campo. I blocchi piazzati dai giocatori restano protetti;
le case nei chunk modificati dal giocatore non vengono arredate automaticamente.

## Acquisti

`/negozio` apre la bottega di quartiere più vicina, con indirizzo, stato del negoziante, disponibilità
e collegamento alla fermata più vicina. Si può anche usare il cartello **BOTTEGA** all'ingresso.

Ogni bottega vende pane, caffè, mele, pesce cotto, torce e attrezzi. Ha scorte, produzione e cassa
separate. Una vendita consegna un oggetto reale, scala le scorte e trasferisce il pagamento alla cassa.
Il negoziante deve essere presente al banco, in turno e in condizioni di lavorare. Fiducia e precedenti
rapporti influiscono sul prezzo. Una consegna giornaliera consuma denaro della bottega e può saltare
durante uno sciopero. Esaurire un prodotto impedisce ulteriori acquisti.

Comandi diretti: `/negozio apri <quartiere>` e `/negozio compra <quartiere> <articolo>`.
Gli identificativi dei quartieri corrispondono alla loro prima meta nell'atlante, per esempio `duomo`,
`vanvitelli`, `artisti`, `amedeo`, `donnanna`, `nisida`, `mostra`, `soccavo`, `pianura`, `scampia`,
`secondigliano`, `mann`, `san_gennaro`, `garibaldi`, `direzionale`, `mercato`, `ponticelli`, `barra`
e `san_giovanni`.

## Collegamenti utilizzabili

| Linea | Collegamento nel gioco |
| --- | --- |
| M1 | Scampia, Medaglie d'Oro, Vanvitelli, Museo, Dante, Municipio, Duomo, Garibaldi, Centro Direzionale |
| M6 | Mostra, Augusto, Lala, Mergellina, Arco Mirelli, San Pasquale, Chiaia, Municipio |
| B1 | Arenella, Soccavo, Pianura, stadio e Fuorigrotta |
| B2 | Mergellina, Posillipo e Bagnoli |
| B3 | Sanità, Capodimonte, Secondigliano e Scampia |
| B4 | Foria, San Lorenzo, Vicaria e Centrale |
| B5 | Centrale, Centro Direzionale, Ponticelli, Barra e San Giovanni |
| B6 | Vomero, San Martino, Floridiana e Chiaia |
| N7 | Collegamento notturno Secondigliano, Centrale, Municipio e Mercato |
| T1 | Tram fra Municipio, Mercato, Carmine, San Giovanni e Pietrarsa |

Bus e tram seguono percorsi creati per Napulicraft; numeri, frequenze, capienza e tariffa sono regole
del gioco. M1 rappresenta una selezione di fermate principali, M6 riprende gli otto nomi della linea
reale. La collocazione degli accessi è adattata alla rete stradale e ai monumenti già costruiti:
questa è una mappa schematica, non una ricostruzione esatta della rete ANM.

I mezzi circolano in entrambe le direzioni; hanno rispettivamente 8, 12 e 16 posti. Le corse lontane
senza passeggeri sono virtuali. Quando qualcuno sale, il veicolo è un'entità reale e mantiene caricati
i settori necessari al viaggio. I ritardi vengono salvati. Pioggia, ostacoli e scioperi influiscono sul
servizio; la linea N7 opera anche di notte. Le banchine della metro hanno gallerie, binari, illuminazione,
scale e ascensore per la superficie.

- `/trasporti`: rete, linee e fermata vicina.
- `/trasporti fermata <id>`: attese, destinazioni a pagine e ascensore.
- `/trasporti cerca <id>`: indicazioni a piedi verso una fermata.
- `/trasporti viaggia <destinazione>`: percorso con eventuali cambi, da una fermata vicina.
- `/trasporti corsa <linea> <destinazione>`: scelta esplicita della linea da prendere.
- `/trasporti ascensore <id>`: passaggio fra superficie e banchina, mentre si è alla stazione.
- `/trasporti annulla`: interrompe la prenotazione o il viaggio.

Il biglietto costa **2 euro di gioco** e viene addebitato solo quando si sale. Il viaggio comprende i
cambi e dà una validità temporanea di 6.000 tick. Un mezzo pieno non addebita un biglietto:
si aspetta la corsa successiva. Destinazione, cambi rimanenti e pagamento vengono salvati; una
riconnessione non addebita nuovamente la corsa. Gli NPC usano lo stesso servizio per gli spostamenti
lunghi e proseguono a piedi dalla fermata di arrivo, tornando in superficie dopo la metro.

## Fonti dei nomi della metropolitana

Consultate il 23 novembre 2026:

- [ANM, Carta della mobilità 2025, linee e stazioni](https://www.anm.it/resource/1768990802000/cartadellamobilita25).
- [ANM, riapertura Piscinola–Centro Direzionale dal 14 settembre 2026](https://www.anm.it/s/notizia-home/a0gR200000Rm0mCIAR/metro-l1-dal-14-settembre-2026-riapertura-completa-piscinolac-direzionale?language=it).
- [Comune di Napoli, stazioni della linea 6](https://static-www.comune.napoli.it/wp-content/uploads/2025/09/Linea_6_Stazioni.pdf).

## Prove ripetibili

`./gradlew build` esegue anche i GameTest di censimento, arredi, scorte separate, percorsi, direzione
di salita, salvataggio dei biglietti e uscita degli NPC dalla metro.

`./gradlew runDistrictSoak -PsoakRun=<nome-nuovo>` crea un server dedicato temporaneo con quattro
connessioni. Verifica tre viaggi reali, un residente che raggiunge la bottega e una vendita, poi
prosegue fino a 24.000 tick. `./gradlew runDistrictSoak -PsoakRun=<stesso-nome> -PsoakPhase=read`
riavvia lo stesso mondo e controlla i dati salvati per altri 1.000 tick. I profili sono client simulati
con protocollo e salvataggi reali, senza intervento di quattro persone.

`./gradlew runDistrictTest` apre un client reale, visita una bottega, apre i dialoghi di negozio e
trasporti e completa un viaggio in metro. Le schermate sono `run-autotest/screenshots/district-*.png`.
Entrambi i task falliscono se manca il proprio marcatore di completamento nel log.

Verifiche del **23 novembre 2026**: build riuscita, **87 GameTest superati** e client reale completato
con dialoghi nativi e viaggio in metro. Il server con quattro connessioni ha completato **24.837 tick**,
verificando viaggi effettivi su M6, B5 e T1, salita e addebito, arrivo alla destinazione, percorso
casa–bottega di un residente e vendita con scorte e cassa reali. La prova usa l'identificativo
`district-20261123-e`; i log sono `.gradle/district-build-final.log`, `.gradle/district-verified.log`
e `.gradle/district-client-final.log`.

Il riavvio dello stesso server ha superato altri **1.000 tick**: portafogli, identità e registri delle
botteghe sono stati ricaricati dal disco e il viaggio interrotto è arrivato a destinazione senza un
secondo addebito. Log: `.gradle/district-restart.log`. Le routine di Bagnoli usano ritrovi sulla
terraferma; Nisida non diventa una destinazione di pendolarismo quotidiano.
