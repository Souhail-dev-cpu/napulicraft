# NPC, inseguimenti e vandalismo

I residenti di Napulicraft pianificano anche percorsi verticali sulle **scale a pioli e sui rampicanti**.
Aprono le porte di legno lungo il percorso, superano gradini alti un blocco e possono aprire una botola di legno
all'uscita della scala. Il movimento sulla scala e il passaggio al pianerottolo avvengono fisicamente, senza teletrasporto.
Questa navigazione è disponibile anche ai residenti che seguono un giocatore, come gli aiutanti delle quest.

## Carabinieri più reattivi

Ogni carabiniere si concentra sul ricercato più vicino entro 48 blocchi. Il percorso viene rivalutato ogni
6 tick, circa 0,3 secondi, così può cambiare strada se il giocatore si sposta o piazza un ostacolo.
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

Il ricercato continua a essere inseguito mentre gli agenti aprono una barricata individuata. Restano le normali
regole di fuga dopo 20 secondi senza contatto e le sanzioni già presenti per i reati più gravi.

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

Percorsi, scavo, posizionamenti e sanzioni vengono gestiti dal server. Tutti i client vedono gli stessi movimenti,
l'attrezzo impugnato e la distruzione dei blocchi; installa la stessa versione della mod sul server e sui client.

`./gradlew build` controlla percorsi alternativi, porte, salita e discesa delle scale, barricata con drop corretti,
uscite delle scale bloccate, limiti del ferro, regole di scavo e classificazione del vandalismo.
`./gradlew runPoliceaitest` prova dal client vero i posizionamenti e le rotture, il reato, lo scavo, la multa e
l'inseguimento sul tetto. Le immagini vengono salvate in `run-autotest/screenshots`.
