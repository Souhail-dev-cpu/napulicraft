# Revisione di case, materiali e simulazione — 27 novembre 2026

Questa revisione migliora la simulazione e l'aspetto delle abitazioni. L'obiettivo «10x» è una direzione di qualità: non rappresenta un moltiplicatore di FPS misurato.

## Case e materiali

- Quattro texture originali da 64 × 64 pixel: intonaco ocra, intonaco corallo, coppi e maiolica napoletana. Sei blocchi, includendo lastre e scale, con modelli, ricette, raccolta col piccone e traduzioni italiano/inglese/napoletano.
- Basamenti di tufo, fasce marcapiano, persiane e coperture per le abitazioni del catalogo. I balconi delle case modulari hanno una porta e un pavimento allineato al piano; le scale verso i tetti hanno una botola apribile.
- Stanze del piano terreno definite nei palazzi abitati dell'espansione; porte nei passaggi dei piani superiori. Gli arredi esistenti, i letti e le scale interne vengono conservati.
- Pavimenti di maiolica in cucina e nei bagni. Il costruttore di case usa la quota dell'edificio anche per arredi e balconi.
- Migrazione con marcatore persistente per chunk. Opera sui chunk caricati, esclude quelli con costruzioni registrate dei giocatori e conserva i contenitori. La riparazione degli ingressi riconosce sia le facciate originali sia quelle nuove.

## NPC e codice

- Un residente senza viaggio registrato parte da casa: non risulta già presente al lavoro o ai servizi lontani.
- Gli aggiornamenti dei viaggi in un ciclo condividono una sola copia della mappa persistente. Le decisioni successive leggono subito gli aggiornamenti pendenti.
- Case indicizzate per chunk e identificativo; punti dei mobili e catalogo dei quartieri condivisi. Anche i controlli di proprietà usano un indice spaziale invalidato quando cambia il catalogo salvato.
- Ogni NPC richiede una sola porta sul percorso. Quando cambia passaggio rilascia la coda precedente senza perdere la prenotazione di altre risorse, per esempio il frigorifero.
- Attraversamenti: i pedoni aspettano i veicoli in avvicinamento, senza restare bloccati da taxi fermi o veicoli che si allontanano.
- Familiari addormentati scelgono letti distinti, con preferenza per un altro letto della stessa stanza. L'uscita dalla simulazione fisica libera il letto.
- I controlli di accesso riconoscono i tappeti come superfici percorribili e continuano a richiedere spazio per la testa.
- La navigazione azzera il contatore di stallo quando cambia meta. I materiali vanilla passano da un resolver condiviso che segnala gli identificativi errati invece di generare aria.

## Verifiche

Risultati della revisione su Minecraft 26.3:

| Prova | Esito |
| --- | --- |
| Compilazione e `build` | Superate; JAR `build/libs/napulicraft-1.1.0.jar` |
| Suite server | **222 test richiesti superati** |
| `runCityQualityTest` | Superata: accessi ai mobili nelle 35 proprietà, palazzo a quota 103, stanze superiori e cinque schermate native |
| `runLivingCityTest` | Superata: pasto naturale completo, cinque gesti, mutuo, inviti, permessi espliciti del frigorifero, chiavi, TV, traffico, dialoghi e servizi |
| Varianti delle scale | Tutte le 40 combinazioni corrispondono alle scale di mattoni di Minecraft 26.3 |
| JAR | Verificata la presenza delle nuove classi, texture e ricette |

Comando finale usato per le prove server e grafiche: `gradlew build runLivingCityTest runCityQualityTest`. Le due prove grafiche sono ordinate dopo i GameTest. Su questa macchina Windows è stato impostato un percorso separato per le librerie native del test domestico, per convivere con le altre istanze di Minecraft.

I log locali sono `verifica-citta-finale.log`, `.gradle/city-quality-client/logs/latest.log` e `run-autotest/logs/latest.log`. Le cinque immagini della revisione sono in `.gradle/city-quality-client/screenshots`.

Le **57 texture dei telefoni e relativi accessori** sono state confrontate con SHA-256 sia nei sorgenti sia nel JAR installabile: nessuna modifica.

## Asset riproducibili

L'originale e il prompt completo sono descritti in [Materiali architettonici](../tools/city-materials-art.md). L'importazione usa `tools/ImportCityMaterials.java`; `node tools/generate_city_materials.mjs` ricrea i modelli e i dati dei sei materiali. Le 40 varianti delle scale sono state confrontate con quelle delle scale di mattoni di Minecraft 26.3.

Le migliorie geometriche riguardano le abitazioni censite, non ricostruiscono indiscriminatamente tutti gli edifici e monumenti della mappa. Le modifiche si vedono entrando nei rispettivi quartieri. Le prove automatiche verificano casi concreti e non certificano l'assenza di qualsiasi difetto nell'intero progetto.
