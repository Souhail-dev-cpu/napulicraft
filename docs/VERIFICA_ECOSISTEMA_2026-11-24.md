# Verifica dell'ecosistema cittadino — 24 novembre 2026

## Esito delle verifiche

| Verifica | Risultato |
| --- | --- |
| Build e GameTest del codice finale | 124 prove richieste superate |
| Audit fisico della città | 4.322 controlli, zero accessi bloccati |
| Client grafico del ciclo lavorativo | Raccolta nativa, oggetti raccolti, trasformazione, consegna, tempo lavorato, paga unica, otto banchi, promozione, potenziamento e ricrescita superati |
| Multiplayer grafico prolungato | 12.060 tick, due processi client indipendenti e un server dedicato |
| Rete della sessione finale | 80 ± 25 ms configurati per direzione; tre connessioni, compresa la riconnessione; zero errori di connessione nel proxy |
| Integrità del proxy | Tre flussi, 24 MiB per direzione, SHA-256 identico |

La sessione multiplayer finale si è svolta dalle 02:22 alle 02:32, con orario locale Europe/Rome. Ha verificato la presenza dello stesso abitante nei due mondi client, richieste simultanee per l'ultima mela, un solo oggetto e un solo pagamento, invito del sesto occupante, viaggio sulla M6, riconnessione durante la corsa e nessun secondo addebito. Ha visitato anche Duomo, Palazzo Donn'Anna, Pianura, Catacombe di San Gennaro e Piazza Vincenzo Aprea.

I client sono grafici e automatizzati. La prova non rappresenta una sessione con molti giocatori umani o con macchine fisiche separate.

## Prestazioni osservate

Macchina di prova: Ryzen 5 3600X, 12 thread, 32 GB RAM; JDK Zulu 26. Server con heap massimo di 3.072 MiB, client con 1.536 MiB ciascuno, finestre 854 × 480, distanza di rendering 4 e simulazione client 5. Il server usa distanza visiva e simulazione 3. Server e due client condividono CPU e GPU dello stesso PC. La misura comprende la generazione di nuove zone e descrive questa sessione, senza dimostrare le prestazioni su macchine separate.

| Misura | Valore |
| --- | ---: |
| Ultimo campione server | Tick 12.600 del processo, due giocatori |
| Tempo di lavoro del tick, p50 | 1,025 ms |
| Tempo di lavoro del tick, p95 | 12,160 ms |
| Tempo di lavoro del tick, p99 | 135,992 ms |
| Picco di heap server campionato | 1.774 MiB |
| Heap server nell'ultimo campione | 1.017 MiB |
| Client A, FPS p10 / p50 / p90 | 30 / 30 / 30 |
| Client B, FPS p10 / p50 / p90 | 29 / 30 / 30 |

I percentili del server nell'ultimo campione riguardano la finestra mobile di massimo 6.000 tick. Gli FPS provengono dai campioni periodici della sola esecuzione finale: il limite principale è 120 FPS e l'opzione di inattività `afk` è attiva. Il valore di 30 FPS è quindi influenzato dal limite della finestra inattiva. Il p99 supera il budget di 50 ms del tick a 20 TPS: sono presenti picchi durante il caricamento e la generazione delle zone. Questi picchi restano un punto da ottimizzare; il solo p50 non descrive la fluidità della sessione.

Nella ripetizione con tutte le correzioni attive, popolazione iniziale e finale sono entrambe di 196 identità. Il log della sessione non registra decessi. La prima prova aveva invece evidenziato sette morti per sovraffollamento, descritte sotto. Il conteggio dell'anagrafe non indica quanti NPC erano contemporaneamente renderizzati. Questi risultati non dimostrano la tenuta con 512 residenti o decine di client concentrati nello stesso luogo.

## Problemi trovati e correzioni

- Le scale della metro erano chiuse ai livelli delle banchine; la stazione di Municipio ora apre su entrambi i livelli serviti.
- L'accesso costiero di Sannazaro aveva pavimentazione e acqua nel vano della scala; ingresso, collegamento sotterraneo e cartello interattivo sono stati corretti.
- I percorsi del pontile di Bagnoli usavano la quota del terreno sottostante; ora usano il piano effettivo del pontile.
- Le case sul dislivello della costa venivano controllate partendo dal lungomare basso; la verifica segue la strada superiore usata dalla rete pedonale.
- Il proxy sperimentale poteva riordinare segmenti TCP sotto carico. La versione finale usa una coda FIFO e ha superato sia il confronto SHA-256 sia la sessione grafica senza errori di protocollo.
- Durante la prova sono morti sette abitanti per danni da sovraffollamento dopo la materializzazione nello stesso punto della scuola. La correzione successiva distribuisce gli alunni su 24 posizioni, riserva gli arrivi simultanei e rimanda la comparsa quando manca spazio libero. Le nuove prove verificano 32 arrivi separati e la distribuzione in aula. La sessione grafica prolungata è stata ripetuta con questa correzione attiva: tutti i 196 abitanti sono rimasti presenti e non risultano decessi. La build finale supera anche i 124 GameTest.
- Un manutentore con materiali già prenotati poteva essere dirottato da un nuovo guasto o restare bloccato dopo l'intervento di un altro lavoratore. Ora conserva la destinazione originaria oppure restituisce i materiali una sola volta.

L'audit locale non dimostra ogni percorso globale possibile né l'accessibilità dopo modifiche arbitrarie del giocatore. I blocchi personali vengono conservati e possono richiedere una verifica manuale.

## Rapporti e riproduzione

I file locali dell'esecuzione sono:

- `.gradle/ecosystem-build-crowds-final.log`: build e 124 GameTest.
- `.gradle/ecosystem-access-complete.log`: audit completo.
- `.gradle/city-audit/napulicraft/reports/accessibilita.csv`: dettaglio dei 4.322 controlli.
- `.gradle/ecosystem-mp-server-after-fixes.log`: sequenza multiplayer e marcatore finale.
- `.gradle/ecosystem-mp-client-a-after-fixes.log` e `ecosystem-mp-client-b-after-fixes.log`: esiti dei due client.
- `.gradle/real-multiplayer/proxy.json`: condizioni e contatori del proxy.
- `.gradle/real-multiplayer/*/napulicraft/reports/`: campioni di memoria, tempi e FPS; per ripetizioni nella stessa directory separare le sessioni quando il contatore dei tick riparte.
- `.gradle/ecosystem-work-after-crowds.log`: ripetizione della prova grafica dei lavori dopo la correzione degli arrivi.

La [guida alle verifiche](VERIFICHE_CITTA.md) contiene i comandi per riprodurre le prove. La [guida ai lavori](LAVORI_E_CARRIERE.md) descrive il comportamento disponibile in gioco.

Le serie della sola ripetizione finale sono state estratte in `.gradle/verifica-finale/`, con riepilogo `misure.json`. Il JAR finale è `build/libs/napulicraft-1.1.0.jar`, SHA-256 `C6F9BDC92E225B7E2A561DD584B57FAA9C59AC82057383CC8761EDF001EA53CC`.
