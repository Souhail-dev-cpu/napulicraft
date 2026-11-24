# Napulicraft

**Una Napoli da esplorare e abitare in Minecraft, con cittadini persistenti, vita di quartiere e un'identità napoletana.**

Napulicraft è una mod Fabric che unisce esplorazione, simulazione cittadina e contenuti umoristici. La città comprende monumenti e quartieri ispirati a Napoli, abitazioni arredate, negozi con scorte, lavori, servizi pubblici e trasporti utilizzabili. Gli NPC hanno famiglia, bisogni, occupazione, memoria e routine che proseguono nella simulazione anche quando sono lontani dai giocatori.

Il progetto conserva il proprio carattere parodistico: Munaciello, motorini con radio personalizzabile, cucina napoletana e situazioni comiche convivono con economia, proprietà e conseguenze delle azioni del giocatore.

[Scarica le beta](https://github.com/Souhail-dev-cpu/napulicraft/releases) · [Segnala un problema](https://github.com/Souhail-dev-cpu/napulicraft/issues) · [Codice sorgente](https://github.com/Souhail-dev-cpu/napulicraft)

## Indice

- [Requisiti e installazione](#requisiti-e-installazione)
- [Primi passi](#primi-passi)
- [Menu cittadino e comodità](#menu-cittadino-e-comodità)
- [Funzionalità principali](#funzionalità-principali)
- [La città e i suoi quartieri](#la-città-e-i-suoi-quartieri)
- [Come funzionano gli NPC](#come-funzionano-gli-npc)
- [Case, proprietà e ospitalità](#case-proprietà-e-ospitalità)
- [Economia, negozi e lavoro](#economia-negozi-e-lavoro)
- [Bus, tram, metropolitana e motorini](#bus-tram-metropolitana-e-motorini)
- [Cucina napoletana](#cucina-napoletana)
- [Calcio, televisione ed eventi](#calcio-televisione-ed-eventi)
- [Missioni, folklore e personaggi](#missioni-folklore-e-personaggi)
- [Carabinieri, reati e tasse](#carabinieri-reati-e-tasse)
- [Musica e radio personalizzabili](#musica-e-radio-personalizzabili)
- [Comandi e controlli](#comandi-e-controlli)
- [Configurazione del server](#configurazione-del-server)
- [Multiplayer e salvataggi](#multiplayer-e-salvataggi)
- [Compilazione e verifiche](#compilazione-e-verifiche)
- [Documentazione e licenza](#documentazione-e-licenza)

## Requisiti e installazione

| Componente | Versione richiesta |
| --- | --- |
| Minecraft | **26.3** |
| Java | **25 o successiva** |
| Fabric Loader | **0.20.1 o successiva** |
| Fabric API | Compatibile con Minecraft 26.3; dipendenza di sviluppo: **0.166.2+26.3** |
| Napulicraft | Lo stesso JAR sul server e su tutti i client |

1. Installa Minecraft 26.3 con Fabric Loader.
2. Scarica il JAR di Napulicraft dalle [release](https://github.com/Souhail-dev-cpu/napulicraft/releases) e [Fabric API](https://modrinth.com/mod/fabric-api) per la stessa versione di Minecraft.
3. Copia entrambi i JAR nella cartella `mods` dell'istanza di gioco.
4. Avvia Minecraft con il profilo Fabric.

Per un server dedicato, installa le stesse mod nella sua cartella `mods`. È supportato anche il multiplayer tramite **Apri alla LAN**. Le beta sono versioni preliminari: il numero completo della versione deve coincidere fra server e giocatori.

## Primi passi

Al primo ingresso il giocatore viene accompagnato automaticamente nella dimensione di Napoli. Puoi usare `/napoli` per raggiungerla e `/napoli esci` per tornare al punto di provenienza registrato. I normali comandi di esplorazione non richiedono privilegi da operatore.

1. Apri `/napoli luoghi`, scegli una meta e segui distanza e direzione durante il cammino.
2. Parla con i cittadini con il **tasto destro**: consulta le informazioni e le azioni disponibili, conosci le loro necessità e costruisci rapporti nel tempo.
3. Usa `/lavoro` e `/lavoro segui` per consultare gli incarichi e raggiungere lo sportello del collocamento.
4. Consulta `/casa` e i cartelli degli immobili per acquistare, affittare o richiedere un mutuo.
5. Apri `/negozio` per una bottega di quartiere e `/trasporti` per la rete cittadina.
6. Premi **J** per aggiungere la tua musica e configurare la radio.

Per un'introduzione giocabile usa **`/inizio`**: conosci un vicino, scegli un lavoro, prendi un mezzo pubblico e compra il pranzo. La guida è facoltativa, si sospende con `/inizio pausa` e conserva le attività completate.

## Menu cittadino e comodità

Premi **O** per aprire il menu cittadino; il tasto è modificabile nei controlli di Minecraft. Sono disponibili anche **`/compagno`** e **`/citta menu`**. Dal menu raggiungi casa, lavoro, portafoglio, botteghe, trasporti, ricettario, guida e inviti.

- **Atlante:** cerca per nome, quartiere o servizio, anche senza accenti. Le destinazioni comprendono luoghi, fermate, botteghe, il lavoro e le case di cui sei proprietario o inquilino. **☆ / ★** aggiunge o rimuove fino a 32 preferiti, conservati dopo morte e riavvio. La scheda di un luogo propone indicazioni a piedi, la fermata vicina e un itinerario con le linee attualmente disponibili.
- **Navigatore:** il tracker indica le svolte lungo le strade, gli ingressi, le scale e le banchine della metro. Cerca passaggi effettivamente percorribili nei chunk caricati, controllando sostegno e spazio per la testa; ricalcola se ti allontani dal percorso o trova un ostacolo. Le zone lontane vengono esplorate normalmente durante il cammino. Se non trova un passaggio lo segnala. Le vecchie mete salvate rimangono compatibili.
- **Obiettivi e carriera:** scegli fra automatico, lavoro, missioni, primi passi, meta fissata, lista della spesa, attività del diario o nessun obiettivo. La scelta è persistente. Il lavoro mostra ore, minuti, paga maturata e materiali; un turno già pagato indica **«Turno concluso: torna domani»**. La schermata Carriera mostra XP, turni completati, requisiti e costo della prossima promozione, paga oraria, potenziamenti e riparazioni. Le operazioni continuano a richiedere il banco fisico.
- **Casa:** una schermata raccoglie proprietà, affitto, mutuo, debito, bollette, scadenze, chiavi, ospiti e cene. Acquisto, affitto, mutuo, pagamento, vendita e chiusura dell’affitto propongono un preventivo prima della conferma, con costo immediato e condizioni periodiche. Il preventivo scade dopo 60 secondi ed è utilizzabile una sola volta; se cambiano proprietà o conti occorre richiederne uno nuovo. Acquisto, affitto, mutuo e vendita conservano il requisito di presenza al cartello.
- **Ricettario:** cerca piatti e postazioni, consulta quantità richieste, ingredienti nell’inventario e combustibile. Fissa una ricetta per aggiornare la lista della spesa nell’HUD durante la raccolta. Gli ingredienti ripetuti vengono conteggiati separatamente.
- **Trasporti:** consulta direzione, attesa stimata, disservizi e posti del mezzo quando è presente. Il pannello aggiorna le partenze ogni cinque secondi e usa l’orologio effettivo delle corse, compresi i ritardi. In modalità obiettivo automatico, il viaggio mostra prossima fermata, destinazione di discesa e cambi.
- **Incontriamoci:** fissa una meta e invita un giocatore online a Napoli. Il destinatario può accettare o rifiutare entro due minuti di gioco; il mittente può annullare l’invito. Gruppi, membri, mete e arrivi sono conservati nel salvataggio del mondo. Puoi uscire dal gruppo; se esce il capogruppo, il ruolo passa a un membro rimasto. Il capogruppo può fissare una nuova meta e condividerla con tutti: chi è disconnesso la riceve al rientro. Massimo 32 membri; gli inviti scaduti non tornano validi dopo un riavvio.
- **Diario:** raccoglie mansioni lavorative, missioni di quartiere, incarichi locali, consegne, cittadini che stai aiutando, pagamenti della casa e premi del campionato da ritirare. Seleziona una voce per seguirne le indicazioni; il diario legge i progressi effettivi e rimuove le attività concluse. I punti degli incarichi locali restano rintracciabili anche quando il personaggio non è caricato.
- **Spese:** consulta gli ultimi 100 movimenti del portafoglio, con causale, giorno, ora, importo effettivo e saldo successivo. Acquisti, stipendi, trasporti, multe, tasse, casa e premi sono distinti. La cronologia persiste dopo morte e riavvio e inizia dall’aggiornamento: i movimenti precedenti non vengono ricostruiti.
- **Lingue:** menu cittadino, diario, carriera, casa, gruppi, cronologia, ricettario, trasporti e istruzioni del navigatore usano componenti tradotti dal client in italiano, inglese e napoletano. Anche su server dedicato ogni giocatore conserva la propria lingua. Le traduzioni sono mantenute in `tools/city-ui-translations.txt` e sincronizzate con `node tools/update_city_translations.mjs`.
- **Opzioni:** attiva separatamente pannello cittadino, cucina e tracker, scegli modalità compatta e dimensione del testo, oppure disattiva le notifiche. Le preferenze locali sono salvate in `config/napulicraft-interface.json`. **H** continua a mostrare o nascondere tutti i pannelli.
- **Aiuto:** i primi incontri con botteghe, fermate e postazioni di cucina mostrano un consiglio una sola volta. Dal menu puoi rileggerli, disattivarli e riprendere `/inizio`. Le notifiche uguali vengono raggruppate con un contatore.

Acquisti e prenotazioni spiegano il motivo di un rifiuto, per esempio denaro mancante, inventario pieno, bottega chiusa, fermata lontana o viaggio già prenotato.

## Funzionalità principali

| Sistema | Contenuti |
| --- | --- |
| Città | Area di 4.096 × 1.360 blocchi, dieci volte l'area precedente; atlante con 50 mete, di cui 45 nuove |
| Popolazione | 120 cittadini iniziali nel nucleo e fino a 76 aggiuntivi nell'espansione, secondo la configurazione |
| Quartieri abitati | 19 quartieri dell'espansione con 38 case per le famiglie e 19 botteghe |
| NPC | Identità persistenti, età, famiglie, bisogni, personalità, memoria, relazioni, gesti e routine |
| Abitare | 35 immobili acquistabili o affittabili, mutui, bollette, chiavi, campanelli, ospiti e cene |
| Lavoro | 85 professioni NPC, otto contratti giocabili, kit iniziali, raccolta guidata, risorse rinnovabili, quattro ranghi e tre livelli di attrezzatura |
| Trasporti | Dieci linee di bus, tram e metro con fermate, passeggeri, capienza, biglietti e cambi |
| Cucina | 71 ricette, quattro postazioni, ingredienti rinnovabili, effetti alimentari e progressione da pizzaiolo |
| Attività | Calcio giocabile, torneo dei quartieri, TV, eventi, missioni e obiettivi personali degli abitanti |
| Contenuti caratteristici | Motorini a tre posti, radio locale, Munaciello, personaggi parodistici e sistema di polizia |

Le **38 case dell'espansione sono alloggi per le famiglie**, distinti dai **35 immobili del catalogo acquistabile** tramite `/casa`.

## La città e i suoi quartieri

Napoli si trova nella dimensione dedicata `napuli:napoli`. L'area ampliata misura **5.570.560 blocchi quadrati**, esattamente dieci volte quella precedente, e collega il nucleo originario alle nuove destinazioni.

Il centro conserva luoghi come Galleria Umberto I, Piazza del Plebiscito, Castel dell'Ovo, Borgo Marinari, Spaccanapoli, Mergellina e le pendici del Vesuvio. L'espansione aggiunge, fra gli altri:

| Zona | Luoghi rappresentati |
| --- | --- |
| Vomero e Arenella | Piazza Vanvitelli, Castel Sant'Elmo, Certosa di San Martino, Villa Floridiana, Piazza degli Artisti e Piazza Medaglie d'Oro |
| Chiaia | Piazza Amedeo, Piazza dei Martiri, Piazza Vittoria, Villa Comunale e Piazza Sannazaro |
| Posillipo | Palazzo Donn'Anna, Marechiaro, Parco Virgiliano e Pausilypon |
| Fuorigrotta | Stadio Diego Armando Maradona, Mostra d'Oltremare e Piazzale Tecchio |
| Bagnoli | Pontile nord, Città della Scienza e panorama di Nisida |
| Soccavo e Pianura | Piazza Giovanni XXIII e Piazza San Giorgio |
| Scampia e Secondigliano | Parco Ciro Esposito e Corso Secondigliano |
| Stella, Sanità e Capodimonte | MANN, Piazza Cavour, Basilica di Santa Maria della Sanità, Fontanelle, Catacombe di San Gennaro, Museo e Real Bosco di Capodimonte |
| Foria, San Lorenzo e Vicaria | Orto Botanico, Piazza Carlo III, Real Albergo dei Poveri, Duomo, Porta Capuana, Piazza Garibaldi, Napoli Centrale e Piazza Nazionale |
| Poggioreale e Mercato | Centro Direzionale, Piazza Mercato e Piazza del Carmine |
| Napoli est | Piazze di Ponticelli, Barra e San Giovanni a Teduccio e Museo Ferroviario di Pietrarsa |

La ricostruzione adatta scala, distanze e architetture a Minecraft. Nomi e riferimenti geografici seguono Napoli, mentre isolati e collegamenti sono sintetizzati per il gioco. L'atlante offre indicazioni a piedi e conserva la meta scelta: consultare un luogo non teletrasporta il giocatore.

I nuovi settori vengono generati nei chunk esplorati, con un massimo di quattro chunk per tick. Gli edifici hanno interni accessibili. Le strade collinari raccordano le quote con gradini continui e fondazioni, mantenendo le altezze degli edifici. Gli ingressi residenziali hanno porte inserite in vani con muratura completa ai lati e sopra.

L'[atlante della città](docs/MAPPA_NAPOLI.md) raccoglie dimensioni, luoghi e fonti geografiche.

Le botteghe e le case dei quartieri ricevono palette, tende, piante e dettagli diversi secondo costa, colline, centro storico e zona orientale. Il paesaggio sonoro locale segue orari e presenza dei giocatori. I percorsi distinguono il livello del terreno dal piano del pontile e del lungomare; gli accessi alla metro hanno aperture verso tutte le banchine servite.

Gli operatori possono avviare **`/citta verifica`**: controlla nei chunk reali gli accessi di case, botteghe, incroci, luoghi dell'atlante e metro. Il rapporto `napulicraft/reports/accessibilita.csv` distingue passaggi validi, blocchi e personalizzazioni. L'audit completo carica molte zone: eseguilo durante una finestra di manutenzione.

## Come funzionano gli NPC

### Identità e simulazione persistente

I cittadini appartengono a un'anagrafe gestita dal server. Ogni abitante conserva identità, nome, età, famiglia, indirizzo, lavoro, denaro, provviste, personalità, reputazione, relazioni ed eventi personali.

Vicino ai giocatori, l'abitante è un'entità che cammina, usa gli ambienti e svolge le proprie attività. Lontano dai giocatori continua attraverso i dati: posizione, viaggio, tempi, bisogni e progressi vengono conservati senza tenere caricata l'intera città. Quando torna visibile riprende dalla situazione registrata. L'identità viene mantenuta evitando di creare un secondo corpo dello stesso cittadino.

La configurazione predefinita permette **196 residenti iniziali**: 120 nel nucleo cittadino e fino a 76 nei quartieri aggiuntivi, oltre ai personaggi specifici delle attività e della trama. Il totale effettivo dipende dal limite configurato e può cambiare con nascite e decessi.

### Bisogni, orari e spostamenti

Fame, riposo, igiene, socialità, divertimento, bisogni fisiologici, salute e umore influenzano le priorità. Abitudini individuali introducono differenze negli orari; pioggia, malattia, stanchezza e impegni possono cambiare il programma.

Una giornata può comprendere scuola o lavoro, spesa, preparazione dei pasti, faccende, incontri, TV e sonno. Medici, infermieri, autisti e carabinieri possono avere turni serali. Gli abitanti usano percorsi a piedi e trasporto pubblico; se un mezzo tarda troppo o è pieno possono proseguire a piedi.

La navigazione comprende porte di legno, gradini, scale a pioli, rampicanti e alcune botole. Porte, postazioni e fermate gestiscono attese e prenotazioni; le sedute hanno un singolo occupante. Gli NPC presenti aspettano i veicoli prima di attraversare.

### Attività domestiche e mestieri visibili

Un pasto segue una sequenza concreta: **frigo → preparazione → cottura → apparecchiatura → consumo → lavaggio**. Il residente raggiunge le postazioni e usa ingredienti e risorse disponibili. La fame migliora quando mangia; lavare i piatti richiede acqua. Se una postazione è occupata, può aspettare o dedicarsi a un'altra fase compatibile.

Gesti, oggetti impugnati, suoni, particelle ed espressioni rendono riconoscibili le azioni. Stanchezza e umore influenzano postura e movimento. Spesa, pacchi, piatti, bucato e rifiuti lasciano tracce persistenti; quando piove i panni bagnati vengono ritirati.

Anche alcuni lavori hanno fasi materiali: il fornaio trasforma ingredienti in pane, il barista serve clienti consumando caffè, il corriere consegna pacchi, il netturbino porta i rifiuti al bidone, il manutentore usa ricambi e il medico consuma medicine. Materiali prenotati e fasi completate vengono salvati per evitare prodotti o compensi duplicati.

Il falegname lavora legname del magazzino per produrre assi; il cameriere raggiunge il cliente con una porzione prenotata; il giardiniere pianta nelle aiuole consumando semi. Le riparazioni domestiche usano anche le assi prodotte. I manutentori possono rimettere in servizio mezzi guasti della rete pubblica.

### Memoria, dialoghi e reputazione

I cittadini ricordano incontri in giorni diversi, acquisti, favori, aggressioni e promesse. Un cliente abituale può essere riconosciuto; la fiducia modifica saluti, disponibilità commerciale e prezzi. Ripetere clic nello stesso giorno non costruisce da solo un'amicizia.

Le conversazioni contestuali tengono conto di bisogni, turno, scorte e obiettivi. Un testimone può riferire dove ha visto una persona, indicando quando: la sua conoscenza deriva da avvistamenti effettivi. Le notizie passano fra persone vicine, con una fonte e una credibilità che diminuisce nei passaggi. `/quartiere voci` mostra ciò che è stato raccontato al giocatore.

Le preferenze personali restano legate all'identità: pietanza preferita, saluto, bottega abituale, ricerca di tranquillità e amici fidati influenzano dialoghi e tempo libero. Quando un acquisto è possibile, lo spuntino preferito consuma denaro e scorte reali; gli incontri con gli amici richiedono relazioni già costruite.

Le battute compaiono in **nuvolette sopra la testa di chi parla**. Seguono il personaggio, si orientano verso la camera, vanno a capo e scompaiono automaticamente; i testi lunghi sono suddivisi in pagine. Le conversazioni raggiungono i destinatari vicini. Avvisi di sistema e menu mantengono le proprie interfacce.

### Famiglie, relazioni e obiettivi

Le famiglie possono formare coppie, avere figli e cercare un'abitazione adatta. Le nascite rispettano disponibilità economica, provviste, posti in casa e limite di popolazione. I neonati rimangono a casa; i bambini crescono, frequentano la scuola e cercano lavoro da adulti. La scala iniziale è di **sette giorni Minecraft per un anno**, configurabile dal server.

Amicizie e relazioni richiedono incontri ripetuti e affinità. Compleanni, traslochi, lutti e separazioni lasciano ricordi; pensionamento ed eredità fanno parte del ciclo di vita. Le faccende familiari vengono assegnate secondo capacità e disponibilità.

Alcuni abitanti vogliono riparare un forno, trovare un impiego o risparmiare per una casa. Puoi accettare un aiuto dal dialogo e seguirlo con `/abitanti`. Consegnare materiali, accompagnare una persona al municipio o contribuire al risparmio produce conseguenze effettive; gli impegni hanno condizioni e, in alcuni casi, scadenze.

### Quartieri e servizi

Scene brevi coinvolgono cittadini esistenti: anziani al bar, conversazioni fra vicini, panari dai balconi, genitori con bambini e richieste di silenzio. Orario, meteo, età e presenza dei partecipanti determinano quando possono verificarsi.

Scuola, ambulatorio e municipio dipendono da orari, personale disponibile e risorse. Una visita richiede un medico e medicine; la scuola richiede un insegnante e presenza dell'alunno; il collocamento richiede un impiegato. `/servizi` mostra la situazione.

Nei 19 quartieri aggiuntivi le famiglie iniziali comprendono due adulti e due bambini. Ogni quartiere ha un fornaio e un secondo lavoratore fra insegnante, medico, netturbino e corriere. La seconda casa offre spazio alla crescita delle famiglie. Le routine usano gli stessi sistemi del nucleo cittadino.

## Case, proprietà e ospitalità

Il catalogo comprende **35 immobili acquistabili o affittabili**, con identificativi stabili. Le famiglie hanno anche alloggi iniziali; l'espansione aggiunge altre 38 case assegnabili agli abitanti.

Le case includono punti d'uso per cucina, bagno, soggiorno, camere, studio e lavanderia. Secondo il modello sono presenti crafting, fornace, affumicatore, forno a legna, frigo e armadi con inventario, tavolo, divano, TV, lavabo, WC, doccia, letti e scrivania. Frigoriferi e armadi usano inventari vanilla; gli alimenti conservano il normale comportamento Minecraft.

Dal cartello puoi comprare, affittare o richiedere un mutuo. Il mutuo richiede un **acconto del 20%**, applica un **interesse totale del 10%** e prevede **12 rate**. Le scadenze sono ogni sette giorni di gioco; le rate comprendono 2 € di bollette e il server tenta il pagamento automatico. Tre scadenze insolute portano alla chiusura di un affitto o di un mutuo non estinto. La rivendita avviene all'80% del prezzo, dopo il saldo del debito.

### Cinque occupanti ordinari, con ospiti autorizzati

Ogni casa ha **cinque posti ordinari complessivi per NPC e giocatori, bambini inclusi**. Il proprietario o l'inquilino può invitare amici e NPC: **gli ospiti autorizzati possono entrare e restare anche oltre il limite di cinque**, senza consumare i posti dei residenti.

Gli inviti si gestiscono dal cartello, con **Ospiti e inviti**, o tramite `/casa ospiti ID`. Sono salvati con il mondo e rimangono validi fino alla revoca; vendita, cambio di proprietario o fine dell'affitto li annullano. Un invito permette l'ospitalità e l'uso degli ambienti, mentre proprietà, costruzione, vendita e gestione degli inviti rimangono al titolare.

Il campanello permette di chiedere accesso. Le chiavi funzionano soltanto per chi possiede ancora un'autorizzazione valida: conservarne una copia non aggira una revoca. Gli NPC invitati possono venire nel tempo libero serale. Un abitante senza posto o invito aspetta all'ingresso e può proporre un incontro al bar.

### Cene organizzate

Scegli **Organizza una cena** dal cartello oppure usa `/cena organizza ID MINUTI`, con un anticipo da 1 a 30 minuti. Invita giocatori o NPC, raccogli le conferme e aggiungi pane alle porzioni condivise con `/cena cibo ID QUANTITÀ`.

Gli NPC valutano salute, fiducia e impegni prima di accettare e possono portare una razione dalla propria dispensa. Consumare una porzione richiede la presenza in casa. Il menu mostra invitati, arrivi e cibo; al termine gli ospiti tornano alle proprie attività. Il padrone può annullare la cena e ritirare gli avanzi una sola volta.

La cena concede agli ospiti confermati un accesso temporaneo anche oltre i cinque posti. Alla fine scade quel permesso; eventuali inviti permanenti rimangono validi.

## Economia, negozi e lavoro

Il denaro del giocatore è gestito dal server e consultabile con `/portafoglio`. Stipendi, acquisti, casa, trasporti, tasse e premi utilizzano il sistema economico della città.

### Botteghe e filiera alimentare

Le **19 botteghe dell'espansione** hanno scorte e cassa separate. Vendono pane, caffè, mele, pesce cotto, torce e attrezzi. Una vendita consegna oggetti reali, riduce le disponibilità e trasferisce il pagamento alla bottega. Servono un negoziante presente, un turno aperto e denaro sufficiente.

Produzione, ingredienti e consegne incidono sulle scorte. Il forno e il mercato del nucleo cittadino condividono una filiera in cui il grano diventa farina e la farina diventa pane; il mercato compra dal forno. Scioperi, guasti o lavoratori assenti possono rallentare il servizio. Fiducia, reputazione e mercato modificano i prezzi previsti dai rispettivi sistemi commerciali.

Quando manca grano puoi accettare una consegna dal dialogo del commerciante. Gli incarichi per forno e mercato richiedono otto grano e pagano 12 €, ridotti a 6 € dopo cinque minuti. La consegna entra nelle scorte e può essere riscossa una sola volta.

### Professioni NPC e contratti del giocatore

Il catalogo degli NPC comprende **85 professioni** in ristorazione, commercio, servizi, trasporti, cultura e media, edilizia, uffici, sanità, pubblico e sicurezza. Ogni impiego prevede luogo di lavoro, strumenti, paga, turni e posti disponibili. Presenze, esperienza, reputazione, salute e riposo contribuiscono alle promozioni; le assenze ripetute possono portare al licenziamento. I carabinieri hanno otto gradi.

Il giocatore dispone di **otto contratti cittadini**: fornaio, facchino, mercante, netturbino, giardiniere, pescatore, corriere e falegname. `/lavoro` mostra stato e offerte; il collocamento e i punti di lavoro guidano attraverso mansioni e pagamento.

Il primo turno assegna l'attrezzatura del mestiere. La freccia segue **raccolta → preparazione → consegna → timbratura**: per il falegname indica gli alberi, poi il banco per trasformare i tronchi in assi e infine il destinatario. Sei querce dell'area artigiana ricrescono durante la notte; il campo viene ripristinato usando semi. La ricrescita rispetta costruzioni e contenitori dei giocatori.

Presso ciascun contratto trovi un **banco CARRIERA**: esperienza e turni pagati consentono di passare da Apprendista a Qualificato, Specialista e Maestro. Sono disponibili potenziamenti, riparazioni e sostituzioni dell'attrezzo. I progressi sono separati per mestiere e persistono nei salvataggi. Stipendi e interventi pubblici usano un fondo finanziato anche da tariffe, vendite e tasse; le consegne riforniscono le scorte pertinenti.

Consulta la [guida ai lavori e alle carriere](docs/LAVORI_E_CARRIERE.md) per materiali, requisiti, costi e comandi.

## Bus, tram, metropolitana e motorini

### Rete pubblica

Dieci linee collegano il centro e i nuovi quartieri:

| Linea | Collegamenti principali nel gioco |
| --- | --- |
| M1 | Scampia, Medaglie d'Oro, Vanvitelli, Museo, Dante, Municipio, Duomo, Garibaldi e Centro Direzionale |
| M6 | Mostra, Augusto, Lala, Mergellina, Arco Mirelli, San Pasquale, Chiaia e Municipio |
| B1 | Arenella, Soccavo, Pianura e Fuorigrotta |
| B2 | Mergellina, Posillipo e Bagnoli |
| B3 | Sanità, Capodimonte, Secondigliano e Scampia |
| B4 | Foria, San Lorenzo, Vicaria e Centrale |
| B5 | Centrale, Centro Direzionale, Ponticelli, Barra e San Giovanni |
| B6 | Vomero, San Martino, Floridiana e Chiaia |
| N7 | Collegamento notturno fra Secondigliano, Centrale, Municipio e Mercato |
| T1 | Tram fra Municipio, Mercato, Carmine, San Giovanni e Pietrarsa |

Bus, tram e metro hanno rispettivamente **8, 12 e 16 posti**. Le fermate distinguono direzioni, attese e code. Pioggia, ostacoli, ritardi e scioperi influiscono sul servizio. Le stazioni della metropolitana comprendono banchine, gallerie, binari, illuminazione, scale e ascensori.

Il biglietto costa **2 € di gioco**, si paga alla salita e vale 6.000 tick, includendo i cambi del viaggio. Se il mezzo è pieno non viene addebitato. Destinazione, pagamento e cambi restanti vengono salvati per riprendere dopo una riconnessione senza pagare nuovamente la stessa corsa.

Usa `/trasporti`, il cartello della fermata o `/trasporti viaggia DESTINAZIONE` da una fermata vicina. Anche gli NPC usano la rete per i tragitti lunghi e percorrono a piedi l'ultimo tratto.

`/problemi` mostra gli interventi pubblici. Un guasto ferma realmente la linea interessata; ripararlo richiede di raggiungere il punto e consegnare due lingotti di ferro. Il fondo lavori paga 20 € e il servizio riparte. Un intervento già concluso non può essere riscosso nuovamente.

Le linee sono un adattamento per Napulicraft: M1 seleziona alcune fermate principali e M6 riprende gli otto nomi della linea reale. Percorsi di superficie, frequenze, capienze e tariffe appartengono alla simulazione. La [guida ai trasporti](docs/QUARTIERI_E_TRASPORTI.md) riporta dettagli e riferimenti.

### Motorino a tre posti

Il motorino può ospitare **tre giocatori**. Il primo è il conducente; il secondo e il terzo occupano i posti successivi. Il conducente accelera con **W**, arretra con **S** e orienta il mezzo con il mouse. **Shift** fa scendere.

Se il conducente scende o si disconnette, il passeggero successivo prende il comando e mantiene la stazione radio selezionata. Solo il conducente può cambiare radio. Un quarto giocatore non può salire.

**Shift + tasto destro** raccoglie un motorino vuoto. Un mezzo occupato non può essere raccolto; se l'inventario è pieno rimane nel mondo. La radio si spegne quando il mezzo resta vuoto.

## Cucina napoletana

La cucina comprende **71 ricette** disponibili in sopravvivenza, nel ricettario e nella scheda creativa. Gli ingredienti aggiuntivi si ricavano da risorse vanilla rinnovabili; le ricette sono accessibili senza completare missioni o raggiungere una reputazione.

| Postazione | Funzione |
| --- | --- |
| Forno a Legna Portatile | Cottura di pizze, dolci e preparazioni, con tronchi come combustibile |
| Tavolo da Pizzaiolo | Ingredienti, impasti, preparazioni crude e insalate |
| Banca della Frittura | Cottura dei fritti con olio |
| Banca del Caffè | Chicchi, caffè, rum da cucina, limoncello e acqua di cocomero |

Le postazioni si possono costruire, piazzare, usare e trasportare. Fra le pietanze ci sono pizza margherita e a portafoglio, fritto misto, sfogliatelle, babà, pastiera, zeppole, struffoli, roccocò, mustaccioli, casatiello, lasagne, chiacchiere, spaghetti alle vongole, baccalà, capitone, minestra maritata, insalata di rinforzo, salsiccia e friarielli, gattò e sartù.

Gli alimenti hanno effetti differenti: cure, rigenerazione, fortuna, resistenza e bonus temporanei, talvolta seguiti da penalità. Gli struffoli curano anche i compagni della stessa squadra vanilla vicini. La pizza a portafoglio e le chiacchiere si consumano rapidamente senza interrompere la corsa.

La **Borsa del Fritto** contiene porzioni e ne rigenera una al minuto fino al limite previsto. Il **Sartù di Riso** sblocca un enigma con ricompensa unica. Preparare pizze aumenta una reputazione personale: alle soglie di 5, 15 e 30 pizze si ottiene il supporto di uno, due o tre aiutanti durante le missioni dei clan.

L'interfaccia mostra **Fame Napoletana**, che rappresenta la sazietà eccessiva, e **Calore**. Troppi fritti appesantiscono il personaggio; sole, ambienti caldi e fuoco fanno salire la temperatura. Acqua di cocomero, ombra e acqua aiutano a rinfrescarsi. **H** mostra o nasconde il pannello insieme a quello cittadino.

La [guida alla cucina](docs/CUCINA.md) e il [ricettario completo](docs/CUCINA_RICETTE.md) indicano ingredienti, quantità, effetti e durate.

## Calcio, televisione ed eventi

Il centro sportivo permette di **giocare o assistere alle partite**, scegliere un ruolo, allenarsi in palestra e sviluppare la carriera calcistica. Passaggi, tiri, effetto, pallonetti, richieste di palla, intercetti e roulette hanno comandi dedicati e un'interfaccia con stamina e azioni disponibili.

Il **torneo dei quartieri** coinvolge Partenope, Mergellina, Centro storico e Fuorigrotta, con sei partite in tre giornate, classifica e premi. I giocatori possono occupare entrambi gli schieramenti. Risultati, partecipazione e premi sono persistenti; una partita rimasta senza partecipanti può essere ripresa dopo il riavvio. `/calcio campionato` apre il torneo.

Le televisioni si accendono con il tasto destro; **Shift + tasto destro** cambia canale. Sport, notizie e meteo hanno schermate dedicate. La trasmissione sportiva mostra una partita fittizia animata con campo, giocatori, pallone e risultato. Il calendario predefinito propone Napoli–Juventus ogni sette giorni di gioco: avversario, orario e risultato sono configurabili e indipendenti dal calcio reale.

Vittorie e sconfitte influenzano umore e atmosfera del quartiere. I festeggiamenti possono includere richiami, clacson ed effetti pirotecnici. Il calendario propone anche mercati, concerti, feste, beneficenza, scioperi, assemblee, esercitazioni, allerte meteo, lavori e inaugurazioni.

Suoni domestici, serrande, traffico nelle ore di punta, TV e finestre illuminate seguono il momento della giornata. Le luci si spengono a orari differenti, accompagnando le routine degli abitanti.

## Missioni, folklore e personaggi

### Missioni e fazioni

Le **Lanterne dei Quartieri Spagnoli** e le **Maree di Borgo Marinari** sono fazioni fittizie con reputazione e incarichi. Dialoghi e missioni comprendono rifornimenti, ricognizioni, attività minerarie e altri obiettivi di quartiere. Le scelte influenzano rapporti e reazioni locali. Napoli sotterranea offre un percorso dedicato, rintracciabile con `/napoli sotterranea`.

Sono presenti equipaggiamenti e oggetti arcade, fra cui pistola, coltello, tirapugni, manganello, casco, bomba carta e la Ciabatta 'e Mammà. Alcune azioni attirano l'attenzione delle forze dell'ordine.

### Munaciello e Munaciello gigante

Il **Munaciello** può comparire al buio e sottrarre un singolo oggetto. Cerca di nasconderlo nell'ultima cassa effettivamente aperta dal giocatore, se vicina, caricata e disponibile; lascia anche Monete del Munaciello convertibili in euro. Se non trova un deposito adatto restituisce il bottino secondo le regole della mod.

Puoi renderlo amichevole offrendogli **tre dolci** con il tasto destro. Da addomesticato segue il proprietario, smette di rubare e può essere curato con altri dolci. Proprietario e fiducia vengono salvati.

Il **Munaciello gigante** appartiene agli incontri notturni: osserva il giocatore, reagisce allo sguardo e può inseguirlo. Interrompere il contatto visivo o raggiungere una zona illuminata aiuta a interrompere la caccia; all'alba si allontana.

### Commercianti e contenuti umoristici

**Peppe Fetish**, personaggio adulto di fantasia, vende su Spaccanapoli vicino a `(-4, 64, -125)`:

- **Calzini sporchi, 6 €:** riutilizzabili, richiedono 1,2 secondi d'uso e applicano nausea per 15 secondi e rigenerazione per 10, con una pausa di cinque secondi fra gli utilizzi.
- **Formaggio odor piede, 10 €:** consumabile anche a fame piena; concede Velocità II per 10 secondi e Forza V per tre, poi Lentezza I per 20 secondi e Debolezza I per sei. Le penalità in attesa sono salvate con il giocatore.

Altri venditori offrono l'**Ampolla d'aria di Napoli**, che concede fortuna, e l'**Acqua da bagno dell'influencer**, oggetto fittizio che applica Velocità I e Fame I per 30 secondi. Acquisti e disponibilità dipendono dalle condizioni del venditore e dal portafoglio.

I richiami di **Mammà**, la ciabatta volante e le scene sui motorini completano i contenuti comici della mod.

## Carabinieri, reati e tasse

### Reati e inseguimenti

Furto, aggressione, spari, rapina, pizzo e vandalismo possono attivare segnalazioni e inseguimenti. Testimoni e carabinieri presenti influiscono sull'intervento. Il sistema opera nella dimensione di Napoli e può applicare multe o detenzione secondo il reato e la situazione del giocatore.

Gli agenti rivalutano il percorso, usano porte, scale e passaggi disponibili e devono raggiungere il sospetto con un contatto libero per arrestarlo. Se una barricata del giocatore impedisce l'inseguimento, possono usare un piccone di ferro per aprire un varco vicino, mostrando animazione, crepe e drop. Lo scavo rispetta la regola di danneggiamento dei blocchi da parte dei mob e i limiti dell'attrezzo; contenitori e strutture originali della città sono esclusi da questo comportamento.

Distruggere muri o arredi originali delle case può costituire **vandalismo**. I blocchi piazzati dai giocatori sono tracciati separatamente: rimuoverli non equivale a danneggiare gli arredi originali. È presente anche `/colpo`, per tentativi di rapina presso i bersagli previsti, con attesa, ricompensa e conseguenze penali.

La [guida a polizia e navigazione](docs/IA_POLIZIA.md) descrive regole, limiti e verifiche.

### Visita fiscale parodistica

Un NPC fittizio ispirato a **Giorgia Meloni** visita i giocatori in Sopravvivenza o Avventura ogni **tre giorni Minecraft**. Il dialogo la presenta come tassa settimanale; l'intervallo effettivo della mod è di tre giorni.

Puoi pagare con un massimo di **15 oggetti tassabili**, oppure versare il **30% del portafoglio**, arrotondato all'euro superiore. Armi, armature, strumenti, equipaggiamento e contenitori portatili sono protetti dal prelievo. Se non possiedi né materiali tassabili né denaro è disponibile l'esenzione.

Il rifiuto comporta **90 secondi di detenzione**; anche impedire alla riscossora di raggiungerti entro due minuti può portare alla detenzione per evasione. Chiudere il dialogo lascia la richiesta in sospeso. Pagamenti pendenti e pene persistono attraverso morte e riavvio; Creativa e Spettatore sono esenti dalle visite.

## Musica e radio personalizzabili

La mod **non include canzoni**. Ogni giocatore può usare i propri file `.mp3`, `.ogg` e `.wav`:

1. Avvia il gioco almeno una volta.
2. Premi **J → Apri cartella**, oppure apri `napulicraft/canzoni/` nella cartella dell'istanza Minecraft.
3. Copia i brani e organizza eventuali sottocartelle: ognuna diventa una stazione radio, per esempio `canzoni/Radio del Vico/`.
4. Dalla schermata **Canzoni**, associa i brani alle situazioni di gioco e scegli il secondo di partenza. **Casuale** seleziona un file disponibile.

La schermata può scaricare un file da un **collegamento diretto a un audio**. I collegamenti alle pagine di servizi come YouTube, Spotify e SoundCloud non sono supportati. Usa brani che hai il diritto di utilizzare.

Sul motorino, tieni premuto **R** per aprire la ruota delle stazioni e rilascia per scegliere; un tocco passa alla stazione successiva. Le scelte sono salvate in `config/napulicraft-canzoni.json` nella stessa istanza.

In multiplayer viene sincronizzata la stazione scelta, mentre i file audio rimangono locali a ciascun giocatore.

## Comandi e controlli

### Comandi principali

Le azioni sono disponibili anche tramite dialoghi e cartelli. Gli identificativi si consultano nei menu o con i suggerimenti dei comandi; `UUID` indica l'identificativo di un cittadino.

| Comando | Funzione |
| --- | --- |
| `/napoli` · `/napoli esci` | Entra in città o torna al punto di provenienza |
| `/napoli luoghi` · `/napoli mappa` | Apre l'atlante |
| `/napoli meta ID` · `/napoli annulla` | Imposta o interrompe una destinazione a piedi |
| `/napoli sotterranea` | Attiva o disattiva le indicazioni per il percorso sotterraneo |
| `/citta` · `/portafoglio` | Mostra lo stato della città o il denaro disponibile |
| `/lavoro` · `/lavoro segui` | Consulta l'impiego e segue le indicazioni per il collocamento |
| `/casa` | Elenca immobili, identificativi e coordinate |
| `/casa compra ID` · `/casa affitta ID` · `/casa mutuo ID` | Avvia un contratto presso il cartello dell'immobile |
| `/casa paga ID` · `/casa lascia ID` · `/casa vendi ID` | Gestisce pagamenti, affitto e rivendita |
| `/casa vai ID` | Raggiunge l'ingresso della propria casa |
| `/casa ospiti ID` | Gestisce ospiti e inviti |
| `/casa invita ID NOME` · `/casa revoca ID NOME` · `/casa fineospiti ID` | Invita un giocatore, revoca un invito o conclude le ospitalità |
| `/casa suona ID` · `/casa chiave ID` | Suona il campanello vicino all'ingresso o ritira una chiave autorizzata |
| `/cena organizza ID MINUTI` · `/cena invita ID NOME_O_UUID` | Programma una cena e invita partecipanti |
| `/cena si ID` · `/cena no ID` | Risponde all'invito |
| `/cena cibo ID QUANTITÀ` · `/cena mangia ID` | Aggiunge pane alle porzioni o consuma il proprio pasto |
| `/cena annulla ID` · `/cena avanzi ID` | Annulla la cena o recupera gli avanzi |
| `/negozio` · `/negozio apri QUARTIERE` | Apre la bottega più vicina o quella scelta |
| `/negozio compra QUARTIERE ARTICOLO` | Acquista un articolo disponibile |
| `/quartiere incarico forno` · `/quartiere incarico mercato` | Accetta una consegna disponibile |
| `/quartiere consegna forno` · `/quartiere consegna mercato` | Consegna il grano alla bottega |
| `/quartiere voci` | Consulta le notizie raccontate al giocatore |
| `/parla UUID stai` · `/parla UUID piani` · `/parla UUID chiuso` | Chiede stato, programmi o motivo della chiusura |
| `/parla UUID visto ALTRO_UUID` | Chiede un avvistamento effettivo |
| `/abitanti` | Mostra gli obiettivi personali che stai aiutando a completare |
| `/abitanti accetta UUID` · `/abitanti completa UUID` · `/abitanti contribuisci UUID` | Accetta un aiuto, lo completa o versa 5 € al risparmio familiare |
| `/servizi` · `/servizi trasporti` | Consulta servizi pubblici e informazioni sul servizio locale |
| `/trasporti` · `/trasporti linea ID` · `/trasporti fermata ID` | Mostra rete, linea o fermata |
| `/trasporti cerca ID` · `/trasporti ascensore ID` | Segue una fermata o usa l'ascensore della stazione vicina |
| `/trasporti viaggia DESTINAZIONE` · `/trasporti corsa LINEA DESTINAZIONE` | Prenota un viaggio, anche con una linea specifica |
| `/trasporti annulla` | Interrompe una prenotazione o un viaggio |
| `/calcio` · `/calcio gioca` · `/calcio guarda` | Apre il profilo sportivo, partecipa o assiste |
| `/calcio campionato` · `/calcio quartiere NOME` | Consulta il torneo e sceglie la squadra |
| `/calcio partita ID` · `/calcio premi` | Entra in una partita disponibile o ritira premi pendenti |
| `/calcio nuova_stagione` | Apre la stagione successiva quando il campionato è concluso |
| `/colpo` | Avvia un tentativo presso un bersaglio previsto |

I comandi **`/napuli ...`** sono riservati agli operatori e servono a richiamare contenuti o verificare il gioco. Fra questi: `/napuli motorino`, `/napuli munaciello`, `/napuli gigante`, `/napuli peppe`, `/napuli meloni` e `/napuli libera`.

### Tasti predefiniti

I tasti aggiuntivi sono modificabili nelle impostazioni dei comandi di Minecraft.

| Tasto o azione | Funzione |
| --- | --- |
| Tasto destro su NPC, cartello o postazione | Interazione e menu disponibili |
| J | Gestione delle canzoni |
| O | Menu cittadino, ricerca, preferiti e impostazioni HUD |
| R sul motorino | Tocco: stazione successiva; pressione prolungata: ruota della radio, per il conducente |
| H | Mostra o nasconde i pannelli della città e della cucina |
| W / S e mouse sul motorino | Avanzamento, retromarcia e direzione |
| Shift | Scende dal mezzo |
| Shift + tasto destro sul motorino vuoto | Raccoglie il mezzo |
| Tasto destro / Shift + tasto destro sulla TV | Accende o spegne / cambia canale |
| G / V durante il calcio | Passaggio / tiro |
| K / M durante il calcio | Tiro a effetto / pallonetto |
| B / N / U durante il calcio | Richiesta di palla / intercetto / roulette |

## Configurazione del server

Al primo avvio viene creato `config/napulicraft-city.json`. Modifica il file a server spento e riavvia per applicare le impostazioni. Nelle nuove installazioni i valori predefiniti sono:

```json
{
  "population": 120,
  "maximumPopulation": 384,
  "districtPopulation": 76,
  "needsIntervalTicks": 600,
  "daysPerYear": 7,
  "retirementAge": 70,
  "maximumAge": 95,
  "fixtures": [
    {
      "opponent": "Juventus",
      "everyDays": 7,
      "dayOffset": 0,
      "kickoff": 13000,
      "fullTime": 15000,
      "napoliGoals": -1,
      "opponentGoals": -1
    }
  ]
}
```

| Campo | Significato |
| --- | --- |
| `population` | Popolazione iniziale del nucleo cittadino |
| `maximumPopulation` | Tetto complessivo dei cittadini, regolabile fino a 512 |
| `districtPopulation` | Limite delle aggiunte dei quartieri; i nuclei iniziali completi richiedono 76 posti |
| `needsIntervalTicks` | Intervallo di aggiornamento dei bisogni, normalmente 600 tick |
| `daysPerYear` | Giorni Minecraft per un anno di età |
| `retirementAge` / `maximumAge` | Soglie per pensionamento e fine del ciclo di vita |
| `fixtures` | Calendario delle partite televisive simulate |

Nel calendario, `napoliGoals` e `opponentGoals` a `-1` lasciano scegliere il risultato alla simulazione. Gli orari sono unità del giorno Minecraft. A 20 tick al secondo, 600 tick corrispondono a 30 secondi; su un server rallentato i tempi reali aumentano.

Le configurazioni già presenti mantengono i propri valori. Un massimo di 160 con 120 cittadini esistenti consente al più 40 aggiunte nei quartieri; per tutte le famiglie iniziali serve spazio per almeno 196 residenti. Cambiare la scala dell'età conserva età e legami già registrati. Il server limita automaticamente i valori fuori intervallo.

## Multiplayer e salvataggi

Il **server è responsabile della simulazione**: identità, inventari, portafogli, negozi, permessi, case, biglietti, sanzioni e risultati sportivi vengono validati e salvati sul server. I client gestiscono visualizzazione, interfacce, nuvolette e audio locale. Installa la stessa versione della mod e Fabric API su tutti i partecipanti.

La simulazione usa distanze diverse per contenere il lavoro di navigazione. Gli abitanti lontani continuano attraverso i dati senza caricare i loro quartieri; le corse senza passeggeri possono essere virtuali. Durante un viaggio con passeggeri, il mezzo diventa un'entità e mantiene caricati i settori necessari.

Gli aggiornamenti dei salvataggi vengono applicati per revisione. Il nucleo precedente mantiene le coordinate; i nuovi quartieri si completano durante l'esplorazione. Le migrazioni previste correggono vecchi vani porta e dislivelli delle strade quando i settori vengono ricaricati, senza ricostruire ogni volta la zona. Le riparazioni riconoscono le strutture generate e saltano le personalizzazioni incompatibili; i blocchi tracciati come piazzati dai giocatori sono protetti.

Proprietà, inviti, progressi familiari, lavoro e molti stati intermedi delle attività persistono dopo il riavvio. I biglietti conservano il pagamento del viaggio e i premi riscossi vengono registrati per impedirne la duplicazione.

## Compilazione e verifiche

Il repository include il Gradle Wrapper. Per compilare occorre un **JDK 25 o successivo**; versione base e dipendenze sono in [gradle.properties](gradle.properties).

Su Windows, da PowerShell:

```powershell
.\gradlew.bat --no-daemon build
.\gradlew.bat runClient
```

Su Linux e macOS:

```sh
bash ./gradlew --no-daemon build
bash ./gradlew runClient
```

Il JAR installabile viene generato in `build/libs/napulicraft-<versione>.jar`. Il parametro `-Pversion=...` imposta una versione di compilazione.

### Test automatici e client

`./gradlew runQolTest` esegue una prova grafica in un mondo usa e getta: menu cittadino, ricerca, preferiti, navigazione, ricettario, partenze, carriera, diario, acquisto di una casa tramite preventivo, protezione dal doppio pagamento, cronologia, preferenze e cambio fra tre lingue. Le schermate sono in `.gradle/qol-client/screenshots/`; il risultato richiesto è `[QOLTEST] ALL PASSED`. `build` include anche GameTest per percorsi attorno a muri e dislivelli, preservazione dei chunk lontani, migrazione delle mete, persistenza dei gruppi, uscita e successione del capogruppo, scadenza dei preventivi, turni conclusi e importi della cronologia.

`build` include i **GameTest del server**. La verifica del codice del 24 novembre 2026 ha superato **142 test**, comprendendo ingressi, strade, capienza, ospiti, carriere, scorte, alberi rinnovabili, guasti, pontile e accessi costieri alla metro. Le prove grafiche e prolungate si avviano separatamente.

Il [rapporto dell'ecosistema cittadino](docs/VERIFICA_ECOSISTEMA_2026-11-24.md) riporta gli esiti effettivi, le misure, i problemi trovati e i limiti delle prove.

| Task Gradle | Ambito |
| --- | --- |
| `runGameTest` | Suite automatica del server |
| `runWorkCareerTest` | Client grafico: raccolta nativa, preparazione, paga a tempo, otto banchi, promozione, potenziamento e ricrescita |
| `runCityAudit` | Controllo fisico di tutti gli accessi censiti, con rapporto CSV |
| `runRealMultiplayerServer` | Server isolato per due client grafici, acquisti simultanei, ospiti, metro e riconnessione |
| `runRealMultiplayerA` / `runRealMultiplayerB` | Due processi grafici distinti, sincronizzazione degli NPC e registrazione FPS |
| `runLivingCityTest` | Case, pasti, gesti, inviti, chiavi, campanelli, servizi, TV e interazioni sul client |
| `runAtlasTest` | Destinazioni dell'espansione, atlante e ricaricamento dei chunk |
| `runDistrictTest` | Botteghe, menu dei trasporti e viaggio in metro |
| `runSpeechTest` | Nuvolette, parlante, movimento, pagine, distanza e scadenza |
| `runEntranceTest` | Porte, muri, migrazione degli ingressi e conservazione delle modifiche |
| `runStreetTest` | Attraversamento senza salti, fondazioni e riparazione dopo un vero ricaricamento dei chunk |
| `runPoliceaitest` | Navigazione degli agenti, vandalismo, barricata, multa e inseguimento sul tetto |
| `runMotorinotest` | Tre passeggeri, sincronizzazione, radio e cambio di conducente |
| `runKitchentest` | Interfacce di cucina, effetti, enigma e addomesticamento |
| `runFootballTest` | Menu e interazioni calcistiche |
| `runPeppetest` / `runTaxtest` | Commerciante, consumabili e visita fiscale |

I task client creano mondi temporanei e salvano schermate in `run-autotest/screenshots`. Su Windows usa, per esempio, `.\gradlew.bat runStreetTest`. I task di verifica con un marcatore finale controllano che il log ne confermi il completamento.

### Prove multiplayer prolungate

Sono disponibili anche prove con **due client grafici indipendenti** dietro un proxy locale con latenza e jitter, oltre alle quattro connessioni simulate descritte sotto. Le istruzioni, i rapporti e le condizioni di misura sono in [Verifiche della città](docs/VERIFICHE_CITTA.md). Le misure di server, memoria e FPS sono facoltative e si attivano con `-Dnapuli.performance=true`; le esecuzioni di verifica le abilitano automaticamente.

Sono disponibili prove con **server dedicato e quattro connessioni locali simulate**, protocollo e salvataggi reali. Per ciascuna coppia di esecuzioni usa lo stesso identificativo; per ripetere da zero scegline uno nuovo.

```sh
# Simulazione cittadina, poi verifica dopo il riavvio
bash ./gradlew runCitySoak -PsoakRun=citta-1 -PsoakPhase=write
bash ./gradlew runCitySoak -PsoakRun=citta-1 -PsoakPhase=read

# Quartieri, botteghe e trasporto pubblico, poi riavvio
bash ./gradlew runDistrictSoak -PsoakRun=quartieri-1 -PsoakPhase=write
bash ./gradlew runDistrictSoak -PsoakRun=quartieri-1 -PsoakPhase=read
```

Su Windows sostituisci `bash ./gradlew` con `.\gradlew.bat`. I mondi di prova rimangono in `.gradle/soak/<identificativo>`.

Le verifiche documentate comprendono **168.000 tick** di simulazione cittadina e **10.500 tick** dopo il riavvio, oltre a una prova dei quartieri di **24.837 tick** seguita da altri **1.000 tick**. Sono stati controllati ospiti oltre la capienza, pasti e avanzi, UUID, inventari, scuola e cure, mestieri, partite, premi, viaggi su M6/B5/T1 e ripresa senza un secondo addebito. Si tratta di automazione con quattro profili connessi; non equivale a una sessione manuale di quattro persone su Internet.

### Build e pubblicazione delle beta

Il workflow [Build and publish beta](.github/workflows/beta-release.yml) compila con Java 25 a ogni push ed è avviabile manualmente da GitHub Actions. Dopo una build riuscita pubblica il JAR in una pre-release.

La versione include numero di esecuzione e tentativo, per esempio `1.1.0-beta.42.1`. Una build fallita non pubblica una nuova release. Prove client e prove multiplayer prolungate rimangono task separati dalla build ordinaria.

## Documentazione e licenza

| Guida | Contenuti |
| --- | --- |
| [Napoli viva](docs/NAPOLI_VIVA.md) | Simulazione, famiglie, case, memoria, attività e verifiche approfondite |
| [Mappa di Napoli](docs/MAPPA_NAPOLI.md) | Atlante, dimensioni, luoghi, generazione e fonti geografiche |
| [Quartieri e trasporti](docs/QUARTIERI_E_TRASPORTI.md) | Nuovi residenti, botteghe, linee, biglietti e prove multiplayer |
| [Cucina](docs/CUCINA.md) | Postazioni, alimenti, effetti, progressione e Munaciello |
| [Ricettario completo](docs/CUCINA_RICETTE.md) | Le 71 ricette con ingredienti e quantità |
| [NPC e polizia](docs/IA_POLIZIA.md) | Navigazione, inseguimenti, barricata e vandalismo |

Codice e texture del progetto sono distribuiti con [licenza MIT](LICENSE). Il JAR include **JLayer 1.0.1**, distribuito con licenza **LGPL-2.1**, per decodificare i file MP3.

Napulicraft è un progetto indipendente a carattere parodistico. Luoghi reali, istituzioni e personaggi pubblici sono rappresentati in un contesto di finzione; calendario sportivo, attività, economia e trasporti seguono le regole della mod.
