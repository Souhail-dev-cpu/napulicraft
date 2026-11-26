# Telefoni

Porta un telefono nella barra rapida, nello zaino o nella mano secondaria. **Tasto destro con il telefono in mano**, **P** o **`/telefono`** apre lo schermo. P si può riassegnare nei comandi di Minecraft.

I cinque oggetti hanno scocche 3D sottili, materiali e gruppi fotocamera diversi. Le interfacce ricreano in Minecraft l'aspetto di **ColorOS** e **OriginOS**: cornice arrotondata, fotocamera frontale, orologio, widget, icone e applicazioni. Sono interfacce della mod, non esecuzioni di Android o delle applicazioni del produttore.

Il OnePlus 16 segue il riferimento visivo fornito dall'utente: scocca argento, capsula verticale delle fotocamere, incisioni e scritta luminosa `16 AREA`, con sfondo scuro e spirale dorata. [Asset e prompt dello sfondo](../tools/oneplus-16-art.md).

Anche l'iQOO 16 segue il riferimento dell'utente: finitura grigio-viola, marchio discreto sul retro, tre lenti a triangolo e sfondo scuro con curve blu-viola. [Asset e prompt dello sfondo](../tools/iqoo-16-art.md).

Il vivo X500 Pro Max riprende il riferimento dell'utente con scocca grigio scuro, grande modulo ottico circolare nero, flash separato, marchio vivo verticale e sfondo dorato. [Asset e prompt dello sfondo](../tools/vivo-x500-pro-max-art.md).

Il OnePlus 15 segue l'ultimo riferimento dell'utente: scocca nera, marchio inciso, capsula verticale delle fotocamere e sfondo blu con scritta `Never Settle` sul modello fisico. La Home usa lo stesso sfondo senza lettering, per lasciare leggibili orologio e applicazioni. L'iQOO 15 ha scocca perlata con venature grigie e rosa, modulo nero in alto a sinistra e sfondo rosso-arancio. Entrambi escludono le icone della Cina presenti nelle fotografie. [Asset e prompt](../tools/phone-15-reference-art.md).

## Applicazioni

| App | Funzione |
| --- | --- |
| Mappe | Mostra posizione, coordinate e terreno dei chunk già ricevuti. Cerca monumenti, fermate, botteghe, casa e lavoro a Napoli. Selezionando una meta attivi il percorso pedonale e il tracker della città. Istruzioni e posizione si aggiornano mentre cammini; non vengono caricati chunk per esplorare la mappa. |
| Contatti | Rubrica dei giocatori che hanno aperto almeno una volta un telefono in quel mondo. Ogni account ha un numero stabile; la ricerca accetta nomi e numeri. |
| Messaggi | Conversazioni private con destinatari online o già presenti in rubrica. Anche i messaggi destinati a un account offline vengono conservati. Ogni account mantiene gli ultimi 64 messaggi complessivi, inclusi gli inviati. |
| Telefono | Chiamate fra due giocatori online con un telefono carico e modalità aereo spenta. Il destinatario sente una suoneria e deve accettare; le chiamate senza risposta scadono dopo 30 secondi. Puoi terminare, aprire la chat e attivare/disattivare il microfono. |
| Fotocamera | Mirino sul mondo e scatto di uno screenshot reale. La foto viene salvata nella cartella `screenshots` dell'istanza, con nome `telefono-<tempo>.png`. |
| Foto | Indica dove sono salvate le immagini e apre la cartella delle foto. |
| Impostazioni | Modalità aereo, aspetto chiaro/scuro salvato sul client e ricarica. |
| Info | Modello, sistema di riferimento, CPU, GPU, RAM, memoria, schermo, batteria, ricarica e fotocamere. |

Il microfono **parte spento**. Dopo aver risposto, premi **Attiva microfono** per inviare la voce. La mod usa i dispositivi audio predefiniti del sistema: se non sono disponibili, mostra un messaggio nel telefono. L'audio usa la connessione del server Minecraft, con campioni mono a 16 kHz, esclusivamente fra i due partecipanti della chiamata accettata. Non servono servizi telefonici esterni. La chiusura dell'interfaccia lascia proseguire una chiamata; terminarla, uscire dal server, perdere il telefono o attivare la modalità aereo interrompe l'audio.

Le conversazioni e il numero appartengono all'account nel mondo. Rubare o cedere il telefono trasferisce il dispositivo e la batteria; non trasferisce i messaggi personali del proprietario precedente.

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
