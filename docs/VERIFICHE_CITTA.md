# Verifiche della città e del multiplayer

Le verifiche usano mondi dedicati separati dai salvataggi di gioco. La suite del server, le prove grafiche e l'audit della mappa controllano aspetti differenti: il superamento di una suite non sostituisce le altre.

## Suite del server

```powershell
.\gradlew.bat --no-daemon build
```

La build esegue 178 GameTest. Le prove aggiunte comprendono proprietà degli attrezzi, assegnazione unica del kit, salvataggio della carriera, requisiti di promozione e potenziamento, saldo del fondo, trasformazione del legno, pacchi nominativi, scorte, rinnovo notturno, preferenze stabili, progressi dell'introduzione e riattivazione delle linee guaste. Le prove geometriche verificano anche pontile e collegamenti fra strada e banchine della metro, conservando i blocchi personalizzati.

Le regressioni di economia controllano l'eredità con decessi simultanei in entrambi gli ordini, il saldo dei mutui con rate arretrate e la corrispondenza fra preventivo e ricavo, nonché le trasformazioni con inventario pieno, ingredienti nella mano secondaria e rifiuto senza perdite quando manca spazio. La prova dei download musicali usa file temporanei reali, verifica nomi duplicati con estensioni maiuscole o miste e impone un timeout per intercettare eventuali cicli infiniti.

Dieci ulteriori regressioni verificano che la revoca di permessi o inviti chiuda subito il contenitore aperto, che l'aggiunta di diritti non interrompa una sessione autorizzata e che i giocatori estranei restino indisturbati. Vendite e stipendi vengono provati con portafoglio pieno, spazio insufficiente e capienza esatta, controllando proprietà, conti, fondo e cronologia. Le cooperative verificano annullamento da parte del nuovo capogruppo, rimborso unico ai partecipanti originali e rifiuto di consegne o premi non spettanti. Lo storico viene provato con 0, 511 e 1.305 progetti: tutte le pagine passano dal codec di rete, i comandi di navigazione vengono analizzati dal dispatcher e un vecchio premio resta riscuotibile una sola volta. Il codec rifiuta anche pacchetti sovradimensionati prima di scrivere dati.

## Ciclo di lavoro con client grafico

```powershell
.\gradlew.bat --no-daemon runWorkCareerTest
```

Il client usa la normale distruzione dei blocchi per tagliare due tronchi con l'ascia, raccoglie gli oggetti caduti, prepara le assi, consegna, attende il tempo lavorato e riscuote. Controlla il rifiuto della seconda paga, la presenza fisica degli otto banchi carriera, la promozione, il potenziamento e la ricrescita al cambio della notte. La preparazione dei requisiti di rango è controllata dal test: non simula numerose giornate di avanzamento manuale.

Le schermate vengono salvate in `run-autotest/screenshots/lavoro-*.png`. Il marcatore finale richiesto è `[WORKTEST] ALL PASSED`.

Le ulteriori prove della suite verificano anche il rientro dei materiali quando un altro manutentore conclude prima una riparazione, la ripartizione di una vendita senza creare moneta, 32 arrivi con posizioni riservate distinte e la distribuzione degli alunni su 24 posti in aula. Se l'intorno è pieno, la materializzazione viene rimandata.

## Audit fisico della mappa

```powershell
.\gradlew.bat --no-daemon runCityAudit
```

L'audit comprende 4.322 accessi e collegamenti: case registrate, palazzi generati, botteghe, incroci asciutti della griglia, mete dell'atlante e accessi alla metro. La ricerca usa i blocchi effettivi, il sostegno sotto i piedi, lo spazio per la testa, porte e scale. Lungo la costa considera la strada superiore delle case sul dislivello e il piano effettivo del lungomare.

Il rapporto viene scritto in `.gradle/city-audit/napulicraft/reports/accessibilita.csv`. Le ricerche sono limitate a un intorno locale e a 8.192 nodi; non costituiscono una prova di ogni possibile percorso globale o di ogni successiva modifica del giocatore. L'audit carica e aggiorna molte migliaia di chunk ed è una condizione di carico straordinaria.

Per una verifica mirata:

```powershell
.\gradlew.bat runCityAudit '-PauditTargets=palazzo_-23_4,metro_M6_sannazaro'
```

In un server di gioco l'operatore può usare `/citta verifica`. Il comando produce il rapporto nella cartella `napulicraft/reports` dell'istanza, procedendo gradualmente.

## Due client grafici con latenza e riconnessione

Sono necessari Node.js e risorse sufficienti per due istanze Minecraft più un server. Apri quattro terminali nella radice del repository:

```powershell
# Terminale 1: server dedicato, solo localhost
.\gradlew.bat --no-daemon runRealMultiplayerServer

# Terminale 2: ritardo 80 ± 25 ms per direzione
node tools/multiplayer_proxy.mjs --latency-ms 80 --jitter-ms 25 --duration 1800

# Terminale 3: primo client grafico
.\gradlew.bat --no-daemon runRealMultiplayerA

# Terminale 4: secondo client grafico
.\gradlew.bat --no-daemon runRealMultiplayerB
```

Il server ascolta su `127.0.0.1:25575`, il proxy su `127.0.0.1:25576`. I due profili usano nomi e directory distinti, si collegano automaticamente e devono renderizzare lo stesso cittadino prima delle azioni commerciali. Queste sono istanze grafiche automatizzate, non giocatori umani.

La sequenza controlla:

1. Due richieste reali per l'ultimo articolo: un solo oggetto, nessuna scorta negativa e un solo addebito.
2. Invito inviato dal client del proprietario e ingresso del sesto occupante autorizzato.
3. Prenotazione e viaggio sulla M6 con pagamento del biglietto.
4. Disconnessione del primo client durante la corsa, riconnessione con la stessa identità e assenza di un secondo addebito.
5. Arrivo di entrambi e permanenza connessa fino a 12.000 tick dal primo ingresso, circa dieci minuti a 20 TPS.

La parte finale visita cinque zone dell'espansione, così vengono registrati anche il caricamento dei quartieri e la ricomparsa dei residenti. Il proxy si verifica separatamente con `node tools/multiplayer_proxy_test.mjs`: tre flussi trasferiscono complessivamente 48 MiB nelle due direzioni e ne confrontano SHA-256. Le scritture ritardate seguono una coda FIFO unica per direzione.

Il server richiede `[REALMP] ALL PASSED`; entrambi i client richiedono `[REALMPCLIENT] ALL PASSED`. Un'avvio senza client termina per scadenza e non vale come prova completata. I mondi di verifica persistono per poter ispezionare i risultati; per una ripetizione da zero archivia esclusivamente `.gradle/real-multiplayer/server/verification-world` a processi spenti.

Le directory sono `.gradle/real-multiplayer/server`, `client-a` e `client-b`. Le schermate dei client finiscono nelle rispettive cartelle `screenshots`; il proxy scrive `.gradle/real-multiplayer/proxy.json`. Il limite di durata del proxy e il parametro Gradle `-PrealMpTicks=...` permettono prove più lunghe. Non alterare il numero di tick per abbreviare una verifica dichiarata prolungata.

## Misure delle prestazioni

Il flag JVM `-Dnapuli.performance=true` abilita i rapporti; le normali sessioni non raccolgono campioni. Il server misura il lavoro fra inizio e fine tick, mantiene una finestra di massimo 6.000 campioni e scrive ogni 200 tick:

`tick,giocatori,residenti,tick_p50_ms,tick_p95_ms,tick_p99_ms,heap_usato_mb,heap_max_mb`

Il file è `napulicraft/reports/server-performance.csv` nella directory dell'istanza. Il client registra ogni 100 tick FPS, posizione e dimensione in `napulicraft/reports/client-performance.csv`. I campioni di memoria descrivono l'heap Java del processo, non tutta la RAM della macchina.

L'audit esaustivo, la generazione di nuove zone e il gioco con quartieri già caricati producono carichi diversi. Confronta rapporti ottenuti nelle stesse condizioni e segnala eventuali altri processi di prova contemporanei. Due finestre sullo stesso PC condividono CPU e GPU: gli FPS osservati non sono una stima di due computer separati. Il numero di residenti nel CSV è quello dell'anagrafe, non quello dei corpi NPC contemporaneamente visibili.

Nei profili grafici il limite principale è 120 FPS e l'opzione di inattività è `afk`: Minecraft può ridurre il frame rate della finestra inattiva. Un campione a 30 FPS non dimostra, da solo, una saturazione della GPU.

Le prove con quattro connessioni simulate e riavvio restano disponibili tramite `runCitySoak` e `runDistrictSoak`, come descritto nel [README](../README.md#prove-multiplayer-prolungate).

## Prova da nuovo giocatore del 24 novembre 2026

Client grafico in Sopravvivenza, difficoltà Normale, comandi amministrativi disabilitati, personaggio e mondo nuovi. Il movimento automatico usa i normali input del giocatore; taglio e interazioni passano dal client, lavoro e vendita dai comandi pubblici. Nessun oggetto, denaro, teletrasporto o avanzamento dell'orologio è stato aggiunto dalla prova. È stato effettuato un riavvio per correggere il controllo automatico del taglio e applicare le prime correzioni; il riavvio è escluso dal conteggio.

Il contatore finale registra **900.010 ms di partita**, iniziati alle 21:30:55 e conclusi alle 21:48:10, ora italiana, con il riavvio intermedio. Sono stati provati menu iniziale, conversazione con Teresa, collocamento, assunzione come falegname, attrezzo iniziale, percorso a piedi verso gli alberi, taglio e raccolta, trasformazione in assi, vendita del legname al banco, interazione lavorativa fuori orario e cronologia degli incassi.

| Stato | Inizio | Fine |
|---|---:|---:|
| Portafoglio | 16 € | 30 € |
| Salute | 20/20 | 20/20 |
| Fame vanilla | 20/20 | 20/20 |
| Oggetti | Nessuno | Ascia di ferro e quattro assi |

I sette tronchi venduti hanno prodotto **14 €**, registrati in sette movimenti da 2 €. La paga del turno non è stata riscossa in questa partita: il turno era già chiuso al rientro con il materiale. Il ciclo completo di raccolta, consegna e paga viene verificato dal client dedicato `runWorkCareerTest`.

Tre problemi riprodotti sono stati corretti:

- La conversazione con Teresa non completava il passo introduttivo «Conosci un vicino». Il dialogo ora registra l'incontro dopo i controlli di distanza e validità. Il test grafico controlla rifiuto fuori portata, avanzamento conservando il lavoro scelto e riapertura senza azzeramenti.
- Il navigatore poteva mostrare «Passaggio non disponibile» mentre il giocatore era già arrivato. Ora riconosce l'arrivo vicino solo se la meta è raggiungibile. Un GameTest copre posizione esatta, vicinanza e meta vicina racchiusa da muri.
- Al riavvio il caricatore scartava marcatori dei chunk registrati troppo tardi. Arredamento e raccordo del campo da calcio registrano i loro tipi persistenti all'inizializzazione della mod. Un GameTest cattura lo stato del registro prima di caricare qualsiasi mondo o fixture.

Il salvataggio e le schermate al minuto sono in `.gradle/newplayer-15/`; i log e la telemetria conservano durata, coordinate, inventario e saldo. Il test automatico temporaneo non viene incluso nel JAR distribuito.

Verifica finale: **build riuscita e 175/175 GameTest superati**; `[QOLTEST] ALL PASSED`, compresa la regressione della conversazione, e `[WORKTEST] ALL PASSED`, compresa la paga a tempo. La ricarica di una copia di questo salvataggio ha confermato la conservazione di `home_decor_v3` prima del primo tick e l'assenza di avvisi per tipi di attachment sconosciuti. I log sono `.gradle/newplayer-validation/final-build.log`, `.gradle/newplayer-validation/validation.log` e `.gradle/newplayer-reload/reload.log`.

## Continuazione: turno pagato e acquisto della casa

È stato ripreso lo stesso mondo `nuovo-giocatore-1795552234104`, con lo stesso personaggio, i 30 € guadagnati, l'ascia e le quattro assi. Questa ripresa ha aggiunto **15 minuti e 7,414 secondi effettivi**. La continuazione complessiva, contando anche il tratto precedente all'interruzione manuale, è durata **19 minuti e 18,857 secondi**: è stata prolungata fino all'acquisto. Caricamenti, build e riavvii sono esclusi. Nessuna risorsa, moneta o accelerazione dell'orologio è stata aggiunta dal controllo della prova.

La ricrescita era bloccata perché controllava l'intero volume circostante, incluso il banco pubblico e il terreno che non vengono modificati dall'albero. Ora controlla l'ingombro di tronco e foglie, continuando a rifiutare costruzioni dei giocatori e contenitori. Il controllo impedisce inoltre di creare tronco o foglie dentro il corpo di un giocatore. Tre GameTest coprono banco e pendenza, ostacoli e protezioni, giocatore nella futura chioma e ripresa della crescita dopo il suo allontanamento.

Nel salvataggio reale sono ricomparsi cinque alberi: il sesto era già programmato per la notte successiva. La raccolta ha prodotto 13 tronchi venduti al banco per **26 €**. Il nuovo turno è stato timbrato, un'asse è stata consegnata e sono stati registrati **3 ore e 17 minuti** di servizio prima della chiusura. La riscossione ha accreditato **42 €**, una sola volta; una seconda timbratura ha lasciato invariato il saldo.

Le abitazioni più economiche erano ormai occupate dagli NPC. È stata acquistata con mutuo la prima libera, **Corte dei Musicisti 2 · 661** (`viva_11`, cartello `-442, 64, -141`): prezzo 275 €, anticipo 55 €, debito residuo 242 €, rata base 21 € più 2 € di bollette ogni sette giorni. L'acquisto è passato dal preventivo e dalla conferma del normale menu cittadino, sul posto.

| Movimento della continuazione | Importo | Saldo |
|---|---:|---:|
| Saldo iniziale | — | 30 € |
| Tredici tronchi venduti | +26 € | 56 € |
| Stipendio riscosso | +42 € | 98 € |
| Anticipo del mutuo | −55 € | **43 €** |

Il personaggio conserva salute e fame a 20/20, ascia, tre assi e un alberello. La carriera registra un turno completato, 35 XP e 42 € di paga; la proprietà è intestata al personaggio, con zero arretrati. I dati finali sono in `.gradle/newplayer-15/state.json` e il log della ripresa è `.gradle/newplayer-15/continuation-resumed.log`.

La build finale ha superato **178/178 GameTest**. La prova grafica `runWorkCareerTest` ha superato raccolta nativa, paga a tempo, promozione, attrezzo e ricrescita notturna. Il controllo dei marcatori persistenti viene ora catturato a `SERVER_STARTING`, dopo l'inizializzazione di tutte le mod e prima del caricamento dei mondi, senza dipendere dall'ordine dei loro entrypoint. I log sono `.gradle/continuation-validation/verified-build.log` e `.gradle/continuation-validation/final-validation.log`.

La ricarica di una copia del salvataggio finale, caricando i dati persistenti dello stesso personaggio, ha confermato saldo di 43 €, turno già pagato, carriera con un turno e 42 € di stipendio, proprietà `viva_11` e mutuo residuo di 242 € senza arretrati. Anticipo e stipendio compaiono una sola volta e la cronologia completa riconcilia il saldo iniziale di 16 € con quello finale. Il controllo è registrato con `[CONTINUATION-RELOAD] ALL PASSED` in `.gradle/continuation-reload/reload.log`. Il JAR finale non contiene i controllori temporanei della partita o della ricarica.
