# Lavori, carriere e risorse della città

Questa guida descrive gli otto contratti del giocatore e il loro collegamento con magazzini, botteghe e mestieri degli abitanti. Le 85 professioni dell'anagrafe NPC restano un catalogo distinto: alcune hanno una filiera materiale più approfondita delle altre.

## Iniziare un mestiere

1. Apri `/lavoro`, attiva `/lavoro segui` e raggiungi il collocamento durante l'orario di apertura.
2. Scegli un contratto e raggiungi il responsabile della timbratura.
3. All'inizio del primo turno ricevi l'attrezzo assegnato. Lascia libero uno slot nell'inventario.
4. Segui la freccia: passa dalla materia prima alla preparazione e infine alla mansione quando cambia il contenuto dell'inventario.
5. Completa la mansione, accumula le ore lavorate e torna al responsabile per riscuotere stipendio ed esperienza.

Il turno ordinario va dalle 08:30 alle 16:30. Un'ora di lavoro equivale a 1.000 tick, circa 50 secondi a 20 TPS. Il tempo viene conteggiato nelle condizioni previste dal contratto e vicino ai luoghi dell'attività; stare lontano dal lavoro non produce ore. Una mansione completata e una paga già riscossa non possono essere riscosse nuovamente nello stesso giorno.

## Gli otto contratti

| Mestiere | Attrezzatura iniziale | Percorso della mansione |
| --- | --- | --- |
| Falegname | Ascia di ferro | Raggiungi gli alberi indicati, abbatti e raccogli i tronchi di quercia, trasformali al banco e consegna le assi |
| Fornaio | Zappa di ferro | Raccogli il grano del campo, prepara il pane al banco e consegnalo |
| Mercante | Zappa di ferro | Raccogli il grano e portalo al destinatario |
| Netturbino | Pennello | Pulisci le aree indicate seguendo l'avanzamento della mansione |
| Giardiniere | Pala di ferro | Ritira un alberello presso il vivaio al banco e consegnalo al responsabile |
| Pescatore | Canna da pesca | Raggiungi il pontile, pesca un merluzzo e consegnalo |
| Facchino | Stivali di ferro | Ritira il pacco assegnato al deposito e portalo al destinatario |
| Corriere | Stivali di ferro | Ritira il pacco nominativo al deposito e completa la consegna |

Il falegname deve **rompere e raccogliere realmente il legno**: il puntatore non assegna tronchi a distanza. Al banco, un tronco diventa quattro assi; tre unità di grano diventano un pane. Puoi usare il tasto destro oppure `/lavoro risorse` restando vicino alla postazione. Il vivaio usa una scorta condivisa di alberelli.

Il pacco è un oggetto nominativo associato a giocatore, contratto e giornata. Un altro giocatore non può consegnarlo per te. Se lo perdi, il deposito ne offre una sostituzione a 3 €; una consegna consuma il pacco.

## Alberi e coltivazioni rinnovabili

L'area delle risorse si trova a ovest del quartiere artigiano, intorno a x = −565 e z = 15–55. Comprende **sei querce gestite**, un campo di grano e il banco di preparazione. La freccia del contratto indica la risorsa pertinente: non occorre ricordare le coordinate.

Le querce tagliate ricrescono dalla notte successiva utile, a partire dalle 19:00 del giorno Minecraft. Un taglio notturno viene recuperato la notte seguente. Il rinnovo riguarda solo queste sei querce; non modifica tutti gli alberi vanilla. Le colture raccolte possono essere ripristinate durante la notte consumando semi del magazzino.

La ricrescita attende se un giocatore occupa il tronco o se costruzioni e contenitori interferiscono con l'albero. Non cancella blocchi registrati come piazzati dai giocatori. Le scadenze sono salvate: se l'area resta scarica, il rinnovo viene valutato quando torna disponibile.

## Banco carriera presso il lavoro

Ogni contratto ha un **banco da forgiatura con cartello CARRIERA** vicino al proprio responsabile. Le postazioni vengono collocate quando il luogo viene caricato e visitato; rispettano strutture e blocchi dei giocatori.

Il tasto destro apre il riepilogo di rango, XP, turni completati, guadagni e livello dell'attrezzo. Puoi usare anche `/lavoro carriera`. Promozioni, riparazioni e potenziamenti richiedono di raggiungere fisicamente il banco del mestiere selezionato.

| Promozione | XP complessivi | Turni pagati | Costo |
| --- | ---: | ---: | ---: |
| Apprendista → Qualificato | 60 | 2 | 20 € |
| Qualificato → Specialista | 180 | 4 | 40 € |
| Specialista → Maestro | 400 | 6 | 60 € |

Ogni turno pagato assegna 20 XP più 5 XP per ora lavorata. Ogni rango aggiunge 4 € alla paga oraria di base. Esperienza e attrezzatura sono separate per mestiere e persistono dopo riavvio e morte del personaggio.

| Potenziamento | Rango richiesto | Materiale | Costo |
| --- | --- | --- | ---: |
| Livello 1 → 2 | Qualificato | 1 diamante | 30 € |
| Livello 2 → 3 | Specialista | 1 lingotto di netherite | 90 € |

Asce, zappe, pale e stivali passano da ferro a diamante e netherite. Canna e pennello mantengono il proprio tipo e ricevono maggiore durata; il pennello potenziato accelera la pulizia. Un aggiornamento sostituisce l'attrezzo assegnato, conserva gli incantesimi e mantiene proporzionalmente l'usura.

Riparare costa 3, 6 o 9 € secondo il livello. Sostituire un attrezzo perso costa 6, 14 o 22 €: la vecchia assegnazione perde validità per i potenziamenti. Il kit iniziale viene consegnato una sola volta per mestiere.

## Filiera economica e mestieri NPC

Le consegne entrano nelle scorte pertinenti: assi per gli artigiani e le riparazioni, pane e grano per l'alimentazione, alberelli per il verde e pesce per le vendite. Il falegname può rifornire ulteriormente il magazzino con `/lavoro rifornisci`: un tronco reale vale 2 €, finché ci sono capienza e fondi.

Gli NPC svolgono azioni materiali: il falegname consuma legname per produrre assi; il cameriere prenota pane disponibile e raggiunge il cliente; il giardiniere consuma semi e pianta nelle aiuole; il manutentore usa ricambi e assi. Le lavorazioni dipendono da presenza, orario, materiali e destinatari validi.

Il fondo lavori parte da 50.000 € e finanzia stipendi e riparazioni pubbliche. Tariffe delle postazioni, una quota delle vendite, trasporti e tasse lo alimentano. Una bottega ripartisce il pagamento fra la propria cassa e il fondo; non accredita due volte il denaro. Se il fondo non basta, il compenso resta da riscuotere.

Questi flussi collegano le attività del giocatore alle scorte e ai servizi. La simulazione conserva anche i sistemi preesistenti di retribuzione e rifornimento degli NPC: non rappresenta un mercato economico senza fonti esterne di moneta o materiali.

## Guasti e introduzione giocabile

`/problemi` elenca gli interventi cittadini disponibili. Un mezzo guasto ferma realmente la propria linea; ripararlo vicino al punto indicato richiede due lingotti di ferro e paga 20 € dal fondo. La riparazione riattiva il servizio e può essere completata una sola volta. Anche i manutentori NPC possono intervenire usando ricambi.

`/inizio` propone quattro normali attività: parlare con un vicino, trovare lavoro, salire su un mezzo pubblico e comprare il pranzo. `/inizio segui` attiva la guida; `/inizio pausa` la sospende. I progressi vengono salvati anche se compi queste azioni prima di attivare la guida.

## Comandi di riferimento

| Comando | Funzione |
| --- | --- |
| `/lavoro` · `/lavoro segui` | Stato del contratto e indicazione del collocamento |
| `/lavoro risorse` | Preparazione o ritiro presso banco e deposito |
| `/lavoro carriera` | Menu del banco carriera |
| `/lavoro rango` | Richiede la promozione disponibile |
| `/lavoro potenzia` | Potenzia l'attrezzo assegnato |
| `/lavoro ripara` | Ripara l'attrezzo assegnato |
| `/lavoro attrezzi` | Sostituisce l'attrezzo perso |
| `/lavoro rifornisci` | Vende un tronco al magazzino degli artigiani |
| `/problemi` | Mostra gli interventi pubblici |
| `/inizio` | Apre l'introduzione giocabile |

Per i controlli tecnici, le prove multiplayer e i rapporti sulle prestazioni consulta [VERIFICHE_CITTA.md](VERIFICHE_CITTA.md).
