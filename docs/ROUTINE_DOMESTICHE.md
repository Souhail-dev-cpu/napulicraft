# Mobili e stanze usati dai residenti

I residenti del censimento ora eseguono le azioni domestiche presso i mobili realmente presenti. La scelta dei punti d'uso controlla i chunk già caricati, lo spazio per muoversi, la distanza, il piano e gli ostacoli davanti al mobile. Non cambia la disposizione salvata delle case e non modifica alcuna texture dei telefoni.

- **Bagno:** un residente alla volta, con attesa nel soggiorno. La prenotazione inizia quando il residente è vicino al bagno sul piano corretto. Chi ha bisogno del WC si siede, poi raggiunge il lavabo per lavarsi le mani; la doccia soddisfa l'igiene, senza sostituire l'uso del WC. Il recupero richiede tempo presso il mobile. Le porte esistenti del bagno vengono chiuse durante l'uso.
- **Camere:** nei palazzi i residenti preferiscono camere dei piani superiori, con letti diversi per adulti e bambini. Un letto occupato o incompleto viene scartato. Al cambio di attività il residente si sveglia prima di mettersi in cammino.
- **Studio:** le scrivanie dei piani superiori sono disponibili insieme a quella principale. Il residente usa un posto libero, si siede quando è presente una sedia associata, tiene un libro e mostra il gesto di scrittura. Una scrivania rimossa smette di essere utilizzabile.
- **Soggiorno:** chi trova il divano occupato può usare un'altra poltrona o seduta effettiva della casa. Le prenotazioni contano anche prima che l'animazione del passeggero raggiunga la sedia. TV e riposo richiedono una seduta, invece di recuperare i bisogni mentre si è ancora in cammino.
- **Spostamenti:** i tragitti fra piani usano la scala a pioli e i pianerottoli realmente presenti. Non spostano istantaneamente gli NPC fra le stanze.

Le sessioni e le prenotazioni sono temporanee: un cambio di attività, un mobile rimosso, l'uscita dalla simulazione fisica o la scadenza della prenotazione libera le risorse. La simulazione dei cittadini lontani continua senza caricare quartieri aggiuntivi.

## Verifiche ripetibili

`gradlew build` include le regressioni dei mobili e degli ingressi. `gradlew runHomeFurnitureTest` crea un salvataggio temporaneo e osserva un residente che sale a letto, torna al bagno, studia al piano superiore e condivide il soggiorno con un secondo residente su una seduta diversa. Le immagini sono in `.gradle/home-furniture-client/screenshots`.

`gradlew runLivingCityTest` verifica anche la compatibilità con pasti, chiavi, ospiti, mutui e servizi già esistenti.

Verifica del 27 novembre 2026: **234 GameTest server superati**, build senza daemon riuscita, prova `runHomeFurnitureTest` completata con gli NPC fisici e prova `runLivingCityTest` completata. La prova domestica osserva realmente salita, discesa, uso del bagno, studio seduto e due sedute occupate separatamente; sposta soltanto la telecamera del giocatore. Le brevi pause degli attori servono a fotografare le azioni già verificate.

Log locali: `verifica-routine-sedute.log` per build e routine fisiche; `verifica-routine-finale.log` per le interazioni esistenti. Le **57 texture dei telefoni** sono state confrontate con SHA-256 nei sorgenti e nel JAR: tutte identiche.

## Errore della build di release sugli ingressi

Il log della beta 44.1 mostrava tre test con aspettative precedenti alla rifinitura dei palazzi. I nuovi basamenti e portali possono avere materiali diversi. Le prove ora verificano la chiusura fisica del telaio; la fixture del vecchio ingresso ricrea esplicitamente la muratura originale. La riparazione riconosce anche il portale con zoccolo in andesite e telaio in ardesia, conservando le personalizzazioni registrate e lo stato della porta. La riparazione delle facciate ripristina il materiale del portale anziché copiare quello del basamento adiacente.
