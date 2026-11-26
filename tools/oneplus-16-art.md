# OnePlus 16 — riferimento visivo dell'utente

Il modello riproduce il riferimento fotografico allegato alla conversazione: finitura argento, modulo in alto a sinistra con capsula verticale a due lenti e lente/flash a destra, marchio inciso, motivo a contorni sottili e scritta luminosa `16 AREA` in basso a sinistra.

La scocca, il modulo fotografico, le incisioni e il marchio sono disegnati dal generatore Java `GenPhoneTextures.java`; la geometria è prodotta da `generate_phones.mjs`. La schermata del telefono mantiene le applicazioni interattive della mod.

Il dettaglio successivo della fotocamera fornito dall'utente guida il modulo aggiornato: base argento chiaro con bordo arrotondato, capsula di vetro nero continua con due piccole ottiche a sinistra, lente circolare nera in alto a destra e flash color crema in basso a destra. I riflessi delle ottiche sono discreti; non ci sono anelli argentati separati dentro la capsula. Il rilievo 3D segue il contorno arrotondato con UV continui. Texture del modulo: `textures/item/phones/oneplus_16_camera.png`.

## Sfondo

Modalità usata: **imagegen integrato**, con l'immagine fornita dall'utente come riferimento. L'output originale è stato copiato nel progetto; non è una texture scaricata da un sito del produttore.

Asset sorgente: [oneplus_16_wallpaper.png](../src/main/resources/assets/napuli/textures/phone/oneplus_16_wallpaper.png).

Gli asset derivati sono `textures/phone/oneplus_16_front.png`, `textures/phone/oneplus_16_viewfinder.png` e `textures/item/phones/oneplus_16_face.png`. Il generatore li ricompone a partire dallo sfondo sorgente.

Prompt finale:

> Use case: product-mockup. Asset type: a production smartphone wallpaper texture for a Minecraft handset, flat and full bleed. Input image 1 is a visual reference ONLY: the user's front/back OnePlus 16 picture. Recreate just the artwork visible INSIDE the FRONT DISPLAY, not the handset or product photo. Portrait aspect ratio approximately 9:20. Almost black, deep blue-black negative space over the upper third. A luminous golden and amber accretion-disc spiral sweeps diagonally from the lower left toward the upper right, centered around x=52%, y=58%, with concentric cream-gold elliptical swirling rings, subtle violet/lavender fringes and a thin bright electric-blue outer arc along the upper-left edge of the diagonal disc. The lower part fades into rich black with small wisps of golden dust. Match the supplied reference composition, colors and direction closely. The image must fill the entire rectangular canvas as a clean AMOLED wallpaper. NO phone, NO bezel, NO cameras, NO punch hole, NO words, NO numbers, NO interface, NO text, NO watermarks, NO white border. High detail, refined luminous gradients. Preserve the large dark upper region for the clock overlay.
