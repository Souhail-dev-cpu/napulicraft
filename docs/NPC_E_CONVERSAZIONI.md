# Residenti e conversazioni

La popolazione predefinita comprende 576 cittadini: 120 nel nucleo e 456 nei 19 quartieri aggiuntivi. Ogni quartiere ha sei famiglie di quattro persone, con abitazioni registrate e legami familiari. I quattro nuovi lotti per quartiere vengono assegnati dopo tutti i lotti della versione precedente: nessun indirizzo esistente viene spostato per fare spazio ai nuovi abitanti.

Il vecchio preset completo `120 / 76 / 384` viene aggiornato una sola volta. Le configurazioni personalizzate restano valide. La crescita del censimento non azzera saldi, separazioni, indirizzi modificati o decessi. Le case già assegnate a un altro proprietario e le costruzioni registrate dei giocatori vengono rispettate. I nuovi alloggi possono essere arredati anche nei chunk visitati in precedenza, senza rifare l'arredamento delle vecchie abitazioni.

La simulazione distingue il censimento dagli NPC fisicamente presenti. Gli arrivi sono limitati a otto al secondo, con 96 residenti visibili nei dintorni di ciascun giocatore e 256 complessivi come valori predefiniti. I cittadini lontani continuano i propri viaggi virtuali. Le code di arrivo e gli osservatori ruotano, evitando che le prime identità monopolizzino l'aggiornamento. Gli indici delle famiglie e degli indirizzi vengono rinnovati quando cambia il censimento.

Gli incontri in strada richiedono visibilità e vicinanza. Gli interlocutori si guardano, salutano e si fermano per una breve scena; una chiacchierata completata migliora socialità e divertimento. Gli appuntamenti non trattengono chi deve lavorare, studiare, dormire o soddisfare un bisogno urgente. Se il compagno si allontana o cambia programma, l'incontro si interrompe. Gli adulti e i bambini hanno scambi diversi. Una conversazione con il giocatore libera il compagno precedente.

Il clic destro apre un dialogo con campo di testo e argomenti suggeriti. Le domande vengono interpretate localmente in italiano, con risposte basate sulla simulazione: salute, programmi, lavoro, famiglia, preferenze, aiuto, quartiere e indicazioni. «Perché?» e «Raccontami di più» mantengono l'argomento precedente. La memoria persistente è distinta per cittadino e giocatore. I mestieri conservano la traduzione del client nelle nuvolette e nel dialogo.

Le risposte dirette sostituiscono immediatamente le vecchie nuvolette dello stesso interlocutore. Le battute ambientali continuano a scorrere in ordine; una risposta richiesta dal giocatore non deve attendere che finiscano quelle precedenti.

Esempi: `/parla`, `/parla "Nome Cognome" Come stai?`, oppure scrivere «Hai visto Ciro Romano?» nel dialogo. Gli avvistamenti sono personali, datati e limitati: il cittadino non conosce la posizione attuale di chi non ha incontrato. **Servizi e favori** riapre negozi e incarichi. Le conversazioni richiedono sei blocchi di distanza, visibilità e un interlocutore vivo; chi dorme rimanda al mattino.

## Verifiche

`gradlew build` esegue le regressioni server, incluse migrazione dei vecchi mondi, famiglie distinte, memoria privata, argomenti italiani, serializzazione del campo di testo e rotazione degli osservatori.

`gradlew runNpcLifeTest` crea un mondo temporaneo, controlla il censimento ampliato, prepara una strada con 24 residenti entro un limite di 24, osserva movimenti autonomi dopo le chiacchiere iniziali e invia una domanda premendo il pulsante del dialogo nativo. Verifica anche la continuazione dell'argomento e il rifiuto di comandi a distanza. Immagini e log si trovano in `.gradle/npc-life-client`.

Le texture dei telefoni non sono coinvolte in queste modifiche.

Verifica del 27 novembre 2026: **245 GameTest server superati**, `build runNpcLifeTest` completato con successo. La prova grafica ha verificato anche che la risposta più recente sostituisca immediatamente le vecchie nuvolette. Le **57 texture dei telefoni** sono identiche agli originali sia nei sorgenti sia nel JAR, confrontate con SHA-256. Log completo: `verifica-npc-priorita.log`.
