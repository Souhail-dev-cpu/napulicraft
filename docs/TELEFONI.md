# Telefoni

## Pizza, carabinieri e taxi

Apri **Chiamate → Servizi cittadini**. Puoi anche comporre **081 555 0101** per la pizzeria, **112** per i carabinieri e **081 555 0202** per il taxi. I servizi operano a Napoli e richiedono un telefono carico con modalità aereo disattivata.

- **Pizza a domicilio:** un ordine alla volta, consegna prevista dopo **20 minuti dell'orologio di gioco** (334 tick, circa 17 secondi reali a 20 TPS). Il fattorino raggiunge il giocatore e chiede **8 €**. Usa il tasto destro su di lui per pagare e ricevere una pizza. Senza denaro o spazio nell'inventario, conserva l'ordine senza addebiti. L'ordine e la scadenza restano dopo un riavvio.
- **Carabinieri:** il 112 invia una pattuglia alla posizione del chiamante senza attribuirgli un reato. La pattuglia usa i normali percorsi e può intervenire sui reati rilevati. Occorre attendere un minuto reale di simulazione prima di richiamare.
- **Taxi:** scegli una destinazione dall'elenco del telefono. La prenotazione indica punto di incontro e tariffa, da **6 a 100 €** secondo la distanza. Raggiungi il taxi e usa il tasto destro per salire e pagare. Il veicolo si muove fisicamente e lascia il passeggero vicino alla destinazione. **Shift** interrompe il viaggio; risalire nello stesso taxi non costa una seconda tariffa. La prenotazione conserva un pagamento già effettuato dopo un riavvio. I taxi non ancora utilizzati scadono dopo due minuti reali di simulazione.

I servizi richiedono un punto raggiungibile; per il taxi esci vicino alla strada. Dal menu puoi annullare la pizza o il taxi. Annullare una corsa già iniziata non rimborsa la tariffa. Gli attori dei servizi sono temporanei; gli ordini e le prenotazioni sono salvati separatamente per evitare duplicazioni.

Porta un telefono nella barra rapida, nello zaino o nella mano secondaria. **Tasto destro con il telefono in mano**, **P** o **`/telefono`** apre lo schermo. P si può riassegnare nei comandi di Minecraft.

I cinque oggetti hanno scocche 3D sottili, materiali e gruppi fotocamera diversi. Le interfacce ricreano in Minecraft l'aspetto di **ColorOS** e **OriginOS**: cornice arrotondata, fotocamera frontale, orologio, widget, icone e applicazioni. Sono interfacce della mod, non esecuzioni di Android o delle applicazioni del produttore.

Il OnePlus 16 segue il riferimento visivo fornito dall'utente: scocca argento, capsula verticale delle fotocamere, incisioni e scritta luminosa `16 AREA`, con sfondo scuro e spirale dorata. [Asset e prompt dello sfondo](../tools/oneplus-16-art.md).

Anche l'iQOO 16 segue il riferimento dell'utente: finitura grigio-viola, marchio discreto sul retro, tre lenti a triangolo e sfondo scuro con curve blu-viola. [Asset e prompt dello sfondo](../tools/iqoo-16-art.md).

Il vivo X500 Pro Max riprende il riferimento dell'utente con scocca grigio scuro, grande modulo ottico circolare nero, flash separato, marchio vivo verticale e sfondo dorato. [Asset e prompt dello sfondo](../tools/vivo-x500-pro-max-art.md).

Il OnePlus 15 segue l'ultimo riferimento dell'utente: scocca nera, marchio inciso, capsula verticale delle fotocamere e sfondo blu con scritta `Never Settle` sul modello fisico. La Home usa lo stesso sfondo senza lettering, per lasciare leggibili orologio e applicazioni. L'iQOO 15 ha scocca perlata con venature grigie e rosa, modulo nero in alto a sinistra e sfondo rosso-arancio. Entrambi escludono le icone della Cina presenti nelle fotografie. [Asset e prompt](../tools/phone-15-reference-art.md).

## Numeri personali e scambio di contatti

Ogni giocatore riceve al primo accesso un **numero casuale univoco +39**, salvato nel mondo e associato al proprio UUID. I numeri generati hanno dieci cifre dopo +39 e iniziano per 3. Lo stesso numero resta dopo morte, logout, cambio del dispositivo e riavvio. I precedenti numeri progressivi `081…` vengono convertiti automaticamente senza eliminare le conversazioni.

Leggi il tuo numero in **Contatti → Il mio numero**, copialo negli appunti oppure usa **`/telefono mio`**. Sono numeri interni al server Minecraft.

La rubrica è personale: non espone l'elenco di tutti i numeri del server. Per aggiungere qualcuno, premi **+** in Contatti e inserisci il suo numero. Il tastierino accetta numeri con o senza spazi; si può chiamare anche un numero non ancora salvato. Con **+** in Messaggi puoi salvare un numero e iniziare una conversazione.

Per scambiarsi i contatti, entrambi i giocatori devono avere un telefono carico, modalità aereo spenta e trovarsi **entro 8 blocchi** nella stessa dimensione. Apri **Contatti → Scambia numero → Vicini** e scegli il giocatore. Il destinatario apre **Richieste** e accetta: i due numeri vengono aggiunti a entrambe le rubriche. La richiesta scade dopo 60 secondi. Puoi anche usare **`/telefono scambia NomeGiocatore`**; il destinatario risponde con **`/telefono accetta NomeMittente`**.

Solo gli **OP con livello di permessi almeno 2** possono personalizzare un numero:

```text
/telefono numero NomeGiocatore +39 666 666 666
```

Il giocatore deve essere online. Il comando accetta **9 o 10 cifre dopo +39**, verifica il formato e impedisce duplicati; l'aggiornamento appare anche nelle rubriche che hanno già quel contatto. Il permesso viene verificato sul server. Non è presente un'azione di rete che permetta ai normali giocatori di cambiare il proprio numero.

## Interfacce

ColorOS usa un orologio orizzontale, schede traslucide e un dock arrotondato. OriginOS usa l'orologio disposto in verticale e widget modulari separati per meteo e batteria. Entrambe le Home hanno una griglia a quattro colonne, badge per messaggi e richieste, centro notifiche e accesso rapido alla chiamata in corso. Le scelte visive seguono i riferimenti ufficiali di [ColorOS](https://www.oppo.com/ph/coloros16/) e [OriginOS](https://www.vivo.com/pg/originos).

**Esc** torna alla schermata precedente; dalla Home chiude il telefono. La barra inferiore torna alla Home. Le bozze di chat vengono conservate mentre passi fra le app; il testo inviato viene svuotato dopo la conferma del server. Gli errori vengono mostrati nel telefono.

## Applicazioni

| App | Funzione |
| --- | --- |
| Mappe | Mostra posizione, coordinate e terreno dei chunk già ricevuti. Cerca monumenti, fermate, botteghe, casa e lavoro a Napoli. Selezionando una meta attivi il percorso pedonale e il tracker della città. Istruzioni e posizione si aggiornano mentre cammini; non vengono caricati chunk per esplorare la mappa. |
| Contatti | Rubrica personale fino a 128 contatti, aggiunta tramite numero, schede dei contatti, copia del proprio numero e scambio con accettazione. La ricerca accetta nomi e numeri, anche con spazi. |
| Messaggi | Conversazioni private, bozze per interlocutore, testo a capo, orari, invio della propria posizione e ricevute di lettura: un segno indica invio, due segni indicano lettura. I messaggi per destinatari offline sono conservati. Ogni account mantiene gli ultimi 64 messaggi complessivi; si può eliminare la propria copia di una conversazione. |
| Telefono | Tastierino per comporre un numero, rubrica e registro delle ultime 32 chiamate (concluse, annullate, rifiutate o senza risposta). Le chiamate richiedono due giocatori online con telefono carico e modalità aereo spenta. Il destinatario deve accettare; le chiamate senza risposta scadono dopo 30 secondi. Controlli separati per microfono e ascolto dell'audio. |
| Fotocamera | Mirino sul mondo e scatto di uno screenshot reale. La foto viene salvata nella cartella `screenshots` dell'istanza, con nome `telefono-<tempo>.png`. |
| Foto | Galleria delle ultime 24 fotografie del telefono, miniature, visualizzazione della foto selezionata e accesso alla cartella. Le immagini vengono caricate in secondo piano. |
| Note | Fino a 16 note personali di 256 caratteri, modificabili ed eliminabili, salvate nell'account nel mondo. Restano disponibili in modalità aereo. |
| Calcolatrice | Somma, sottrazione, moltiplicazione, divisione, decimali, percentuali, cambio di segno e precedenza delle operazioni. Funziona offline. |
| Notifiche | Messaggi non letti, richieste di contatto e controlli rapidi di modalità aereo, tema e suoneria. |
| Impostazioni | Numero personale, modalità aereo, aspetto chiaro/scuro, suoneria silenziosa, batteria e ricarica. |
| Info | Modello, sistema di riferimento, CPU, GPU, RAM, memoria, schermo, batteria, ricarica e fotocamere. |

Il microfono **parte spento**. Dopo aver risposto, premi **Attiva microfono** per inviare la voce. La mod usa i dispositivi audio predefiniti del sistema: se non sono disponibili, mostra un messaggio nel telefono. L'audio usa la connessione del server Minecraft, con campioni mono a 16 kHz, esclusivamente fra i due partecipanti della chiamata accettata. Non servono servizi telefonici esterni. La chiusura dell'interfaccia lascia proseguire una chiamata; terminarla, uscire dal server, perdere il telefono o attivare la modalità aereo interrompe l'audio.

Numero, rubrica, conversazioni, note e cronologia chiamate appartengono all'account nel mondo. Rubare o cedere il telefono trasferisce il dispositivo e la batteria; non trasferisce i dati personali del proprietario precedente. Le foto restano nella cartella locale del client.

## Batteria e ricarica

Ogni dispositivo conserva la carica nel proprio oggetto. A parità di consumo, le capacità maggiori durano di più. In sopravvivenza, ogni minuto trascorso con il telefono nell'inventario consuma 1 mAh in standby, 30 mAh con lo schermo aperto oppure 80 mAh durante una chiamata. Un messaggio inviato consuma 1 mAh. Logout e riavvio non ricaricano il dispositivo. In creativa non c'è consumo periodico.

Costruisci il **Caricatore USB-C** con **rame + ferro + filo + redstone**. Portalo nello zaino e usalo con il tasto destro, oppure scegli **Ricarica** nelle impostazioni del telefono. Una redstone aggiunge fino a 2000 mAh; un telefono già pieno non consuma risorse. In creativa non serve la redstone.

## Preparazione dei telefoni

Tutti sono presenti nella scheda creativa **Napulicraft**. In sopravvivenza usa la crafting table: **pannello di vetro + rame + ferro + redstone + quarzo + componente del modello**. Raccogliendo redstone sblocchi le ricette nel ricettario vanilla.

| Modello | Componente |
| --- | --- |
| OnePlus 16 | Scheggia di ametista |
| vivo X500 Pro Max | Colorante arancione |
| iQOO 15 | Colorante bianco |
| iQOO 16 | Colorante blu |
| OnePlus 15 | Colorante giallo |

## Hardware di riferimento

Le configurazioni sono **16 GB / 512 GB**, riferite alle versioni cinesi per mantenere coerenti i sistemi ColorOS e OriginOS. Le frequenze massime dichiarate per alcuni giochi non rappresentano necessariamente la frequenza in tutte le applicazioni. Questi dati descrivono il modello riprodotto; non cambiano CPU, RAM o frequenza di rendering del computer.

| Modello | CPU | Schermo | Batteria | Ricarica dichiarata | Sistema |
| --- | --- | --- | --- | --- | --- |
| OnePlus 16 | Snapdragon 8 Elite Gen 6 | 6,78″ · 2772×1272 · fino a 185 Hz nei giochi | 9000 mAh | 100 W | ColorOS 17 |
| vivo X500 Pro Max | Dimensity 9600 Pro | 6,85″ · 3168×1440 · 144 Hz | 8000 mAh | 90 W / 40 W wireless | OriginOS 7 |
| iQOO 15 | Snapdragon 8 Elite Gen 5 | 6,85″ · 3168×1440 · 144 Hz | 7000 mAh | 100 W / 40 W wireless | OriginOS 6 |
| iQOO 16 | Snapdragon 8 Elite Gen 6 + Q4 | 6,85″ · 3168×1440 · 165 Hz | 8400 mAh | 100 W / 40 W wireless | OriginOS 7 |
| OnePlus 15 | Snapdragon 8 Elite Gen 5 | 6,78″ · 2772×1272 · fino a 165 Hz nei giochi | 7300 mAh | 120 W / 50 W wireless | ColorOS 16 |

Fonti ufficiali consultate il 26 novembre 2026: [OnePlus 16](https://www.oneplus.com/cn/16/specs), [vivo X500 Pro Max](https://www.vivo.com.cn/vivo/param/x500promax), [iQOO 15](https://www.vivo.com.cn/vivo/param/iQOO15), [iQOO 16](https://www.vivo.com.cn/vivo/param/iqoo16), [OnePlus 15](https://www.oneplus.com/cn/15).

## Verifica

`./gradlew build` include le regressioni di ricette, messaggi privati, persistenza, numeri, token scaduti, limite della cronologia, chiamate con consenso, destinatario dell'audio, saturazione dei pacchetti, modalità aereo, perdita del telefono e ricarica.

`./gradlew runPhoneTest` avvia un client reale e un secondo giocatore sul server integrato. Prova i cinque modelli, le schede hardware, quattro dimensioni della GUI, messaggi e risposte, chiamate in entrambe le direzioni, instradamento della voce, GPS, ricerca, ricarica, modalità aereo e fotografia del mondo. Durante l'automazione il microfono rimane spento: il percorso dei pacchetti vocali viene verificato con campioni sintetici, senza registrare l'ambiente. Screenshot e risultato sono in `.gradle/phone-client/`; la prova richiede il marcatore `[PHONETEST] ALL PASSED`.

Per rigenerare modelli e materiali: `node tools/generate_phones.mjs`, `java tools/GenPhoneTextures.java` e `node tools/update_city_translations.mjs`.
