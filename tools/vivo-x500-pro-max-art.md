# vivo X500 Pro Max — riferimento visivo dell'utente

Il modello segue l'immagine fornita dall'utente: scocca grigio scuro, logo vivo verticale in basso a sinistra, grande isola fotografica circolare nera con i quattro elementi ottici visibili, marchio ZEISS centrale e flash separato nell'angolo superiore sinistro.

La geometria del modulo è circolare, composta da fasce 3D con UV continui; il flash ha una propria superficie. Scocca, gruppo ottico, flash e marchi sono disegnati da `GenPhoneTextures.java`; la geometria degli oggetti viene prodotta da `generate_phones.mjs`.

## Sfondo

Modalità usata: **imagegen integrato**, con la fotografia dell'utente come riferimento visivo. L'immagine generata è stata copiata nel progetto; non è un file scaricato dal produttore.

Asset sorgente: [vivo_x500_pro_max_wallpaper.png](../src/main/resources/assets/napuli/textures/phone/vivo_x500_pro_max_wallpaper.png).

Asset derivati: `textures/phone/vivo_x500_pro_max_front.png`, `textures/phone/vivo_x500_pro_max_viewfinder.png`, `textures/phone/vivo_x500_pro_max_rear.png` e `textures/item/phones/vivo_x500_pro_max_face.png`. Il generatore produce un confronto fronte/retro in `.gradle/phone-client/screenshots/vivo-x500-pro-max-riferimento.png`.

L'orologio e i testi superiori della Home usano un colore scuro per mantenere il contrasto sullo sfondo dorato chiaro.

Prompt finale:

> Use case: product-mockup. Asset type: a flat, full-bleed AMOLED smartphone wallpaper texture for a Minecraft handset. Input image 1 is the user's vivo X500 Pro Max front/back product photograph; use it only as visual reference. Recreate just the abstract wallpaper visible inside the front display, not the handset, cameras, branding or white photo background. Portrait approximately 9:20. Match its warm polished champagne-gold aesthetic: a broad softly lit convex golden surface fills the upper half, creamy pale-gold glow at the upper left, deeper amber toward the right, and an elegant bright crescent turning inward around the upper-right/middle area. Below the midpoint, a thin illuminated diagonal curved ridge sweeps from left upward toward right, crossing over rounded overlapping surfaces. The lower half transitions from a soft ivory-gold highlight on the left into smoky taupe, muted lavender and very dark brown-charcoal on the lower right. Large smooth sculptural glass/metal-like forms with subtle luminous gradients, like the supplied photograph. No galaxies, stars, spirals or glitter. No words, icons, numbers, clock, phone, bezel, punch hole, logo, watermark, white border or camera. The entire rectangular canvas must be the wallpaper alone, high-detail and smooth.
