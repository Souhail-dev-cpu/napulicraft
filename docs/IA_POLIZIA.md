# NPC, inseguimenti e vandalismo

I residenti di Napulicraft pianificano anche percorsi verticali sulle **scale a pioli e sui rampicanti**.
Aprono le porte di legno lungo il percorso, superano gradini alti un blocco e possono aprire una botola di legno
all'uscita della scala. Il movimento sulla scala e il passaggio al pianerottolo avvengono fisicamente, senza teletrasporto.
Questa navigazione è disponibile anche ai residenti che seguono un giocatore, come gli aiutanti delle quest.

## Carabinieri più reattivi

Terminato un inseguimento, gli agenti riprendono subito a pattugliare dalla posizione raggiunta: la vecchia area di appartenenza non li blocca più lontano dalla caserma. Le pattuglie sono richiamabili anche dal telefono tramite **112**.

I cittadini camminano più velocemente, anche nei salvataggi esistenti. La navigazione conserva i percorsi parziali utili verso destinazioni lontane e prova deviazioni laterali quando il tragitto diretto è bloccato, evitando ricalcoli continui dello stesso cammino.

Le nuove pattuglie lavorano **in coppia** presso piazze, fermate, monumenti e botteghe dei quartieri, oltre alla caserma e al centro. Percorrono punti successivi della propria zona e vi rientrano dopo un inseguimento. Si attivano nelle aree già caricate vicino ai giocatori, fino a **32 agenti aggiuntivi attivi**: il limite è configurabile con `policePatrolLimit` fra 0 e 64 in `config/napulicraft-city.json`. Gli agenti del censimento e le presenze preesistenti rimangono distinti. Le pattuglie temporanee vengono ricreate per posto e numero, senza duplicazioni nei salvataggi e senza caricare quartieri lontani.

La centrale distribuisce gli agenti disponibili fra i ricercati entro **80 blocchi dall'ultima segnalazione**. Una squadra comprende fino a tre agenti per un reato lieve o cinque per i più gravi. Con più sospetti, la distribuzione assegna prima un agente a ciascuno; quelli in eccesso continuano a pattugliare. I rinforzi tengono conto degli agenti già vicini.

Gli avvistamenti entro **64 blocchi** sono condivisi via radio. Il caposquadra si avvicina al sospetto e i colleghi cercano posizioni sui due lati, anticipandone brevemente la corsa quando il terreno è percorribile. Dopo la perdita del contatto visivo raggiungono l'ultima posizione osservata e cercano nelle vicinanze. Il percorso viene rivalutato ogni 6 tick, circa 0,3 secondi, così può cambiare strada se il giocatore si sposta o piazza un ostacolo.
Prima prova un percorso praticabile, usando passaggi, porte e scale. Per arrestare qualcuno deve avvicinarsi,
vederlo e avere libero anche lo spazio fra i due corpi: un muro, anche basso, impedisce la cattura attraverso i blocchi.

Se resta bloccato da una **barricata piazzata da un giocatore**, può estrarre un **piccone di ferro** e aprire un varco:

- Di norma inizia a valutare lo scavo dopo circa un secondo senza avanzare; riconosce anche un'uscita barricata sopra una scala.
- Scava solo ostacoli vicini, entro tre blocchi, necessari per proseguire l'inseguimento.
- Mostra l'animazione del piccone e le crepe sul blocco; la velocità di scavo e i drop corrispondono al piccone di ferro.
- Rimane aggrappato alla scala mentre scava un'uscita bloccata.
- Finito lo scavo, perso il sospetto o terminato l'inseguimento, riprende l'equipaggiamento normale.

Le strutture originali della città non vengono perforate: lo scavo riguarda i nuovi blocchi piazzati dai giocatori,
la cui provenienza viene salvata nel mondo. Il piccone non rimuove contenitori con inventario, scale a pioli,
blocchi indistruttibili o blocchi che richiedono un attrezzo superiore al ferro. Le porte di legno vengono aperte.
Lo scavo rispetta la regola del server che consente o impedisce ai mob di danneggiare i blocchi.

Durante l'apertura di una barricata rimangono valide le regole di fuga: occorrono **20 secondi consecutivi nascosti**, con la visuale interrotta e un riparo fisico vicino oppure un interno. La sola distanza su una strada aperta non fa avanzare il conto alla rovescia. Tornare allo scoperto o essere riavvistati azzera il tempo accumulato. Gli agenti abbandonano uno scavo legato a una segnalazione ormai vecchia e proseguono la ricerca della zona.

## Moto e auto di pattuglia

Per un sospetto distante almeno 24 blocchi, i primi due agenti assegnati possono usare rispettivamente una **moto** e un’**auto** sulle strade adatte. Gli altri colleghi mantengono la copertura a piedi. I conducenti partecipano alla stessa centrale radio, con avvistamenti fino a 96 blocchi; quando perdono la visuale raggiungono soltanto l’ultima posizione comunicata. Non ricevono le nuove coordinate del giocatore nascosto.

I mezzi percorrono fisicamente la carreggiata e si fermano davanti a persone e ostacoli. Non entrano nelle case, non distruggono i muri e non possono arrestare un giocatore direttamente dal sedile: gli agenti scendono vicino alla destinazione e continuano a piedi. Anche un ostacolo persistente porta a scendere e cercare un percorso pedonale. Alla conclusione della ricerca il mezzo viene ritirato e l’agente riprende la pattuglia. Il limite `policeVehicleLimit` è separato da popolazione e mezzi privati: 12 predefiniti, regolabile da 0 a 32. Lo 0 disattiva i mezzi mantenendo la polizia a piedi.

La simulazione non carica strade remote per inseguire il giocatore. Le auto e le moto della polizia non entrano nel garage personale né rilasciano ricevute di proprietà.

Anche gli inseguimenti degli NPC restano attivi fra una decisione e l'altra: l'agente privilegia il sospetto visibile più vicino, si avvicina per arrestarlo e riprende la propria attività quando il caso termina. Le pattuglie chiamate con il 112 possono intervenire anche su questi incidenti.

## Vandalismo

Distruggere un blocco originale di una **casa della città**, inclusi arredi, muri e piani interni, è il nuovo
reato di **Vandalismo**. Sono riconosciute anche le case dei quartieri e di Municipio già presenti nei salvataggi.

Il reato usa le stesse regole del furto: intervengono i carabinieri che possono vedere il giocatore entro 32 blocchi;
in assenza di testimoni diretti può arrivare la normale segnalazione dei residenti. Il nome del reato viene mostrato
nell'avviso e al momento della sanzione.

Per una violazione lieve, se hai almeno 15 €, ricevi una multa di **15 €**; altrimenti si applica la detenzione
standard di **90 secondi**. Reati più gravi già commessi continuano a prevalere.

Rimuovere blocchi piazzati dai giocatori non conta come vandalismo degli arredi originali. Il tracciamento dei
posizionamenti parte da questa versione e persiste nei salvataggi. La miniera delle quest resta utilizzabile legalmente.
Come il sistema di polizia esistente, la legge opera nella dimensione di Napoli.

## Multiplayer e verifica

### Letti privati

Tentare di dormire nel letto di una casa privata senza permesso è **violazione di domicilio**. Il riposo viene impedito e si applicano le normali regole dei testimoni e delle segnalazioni: una violazione lieve può comportare una multa di 15 € o la detenzione se non puoi pagarla. Sono comprese le case originali e quelle dei quartieri. Proprietari, inquilini e ospiti autorizzati all'uso degli arredi possono dormire; il solo permesso di aprire le porte non basta. I letti fuori dalle case private restano utilizzabili, così come l'alloggio dei lavoratori per chi ha un contratto.

Percorsi, scavo, posizionamenti e sanzioni vengono gestiti dal server. Tutti i client vedono gli stessi movimenti,
l'attrezzo impugnato e la distruzione dei blocchi; installa la stessa versione della mod sul server e sui client.

`./gradlew build` controlla percorsi alternativi, porte, salita e discesa delle scale, barricata con drop corretti,
uscite delle scale bloccate, limiti del ferro, regole di scavo e classificazione del vandalismo.
`./gradlew runPoliceaitest` prova dal client vero i posizionamenti e le rotture, il reato, lo scavo, la multa e
l'inseguimento sul tetto. Le immagini vengono salvate in `run-autotest/screenshots`.
