# Verifiche della città e del multiplayer

Le verifiche usano mondi dedicati separati dai salvataggi di gioco. La suite del server, le prove grafiche e l'audit della mappa controllano aspetti differenti: il superamento di una suite non sostituisce le altre.

## Suite del server

```powershell
.\gradlew.bat --no-daemon build
```

La build esegue 124 GameTest. Le prove aggiunte comprendono proprietà degli attrezzi, assegnazione unica del kit, salvataggio della carriera, requisiti di promozione e potenziamento, saldo del fondo, trasformazione del legno, pacchi nominativi, scorte, rinnovo notturno, preferenze stabili, progressi dell'introduzione e riattivazione delle linee guaste. Le prove geometriche verificano anche pontile e collegamenti fra strada e banchine della metro, conservando i blocchi personalizzati.

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
