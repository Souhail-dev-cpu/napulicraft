# OnePlus 15 e iQOO 15 — riferimenti dell'utente

Il OnePlus 15 riprende l'ultima fotografia identificata dall'utente: nero opaco, marchio centrale discreto, due lenti nella capsula sinistra, lente e flash a destra, display blu e scritta `Never Settle`. L'ultimo messaggio corregge l'identificazione della precedente immagine nera: il OnePlus 16 conserva il riferimento argento con `16 AREA` e spirale dorata.

L'iQOO 15 riprende la fotografia dell'utente con finitura perlata, venature grigie e rosa, marchio iQOO in basso, gruppo ottico nero in alto a sinistra e display rosso-arancio. Le icone della Cina non sono presenti nei materiali della mod.

Modalità: **imagegen integrato**, con le immagini fornite dall'utente come riferimenti. I tre output sono stati copiati nel progetto, conservando gli originali generati. Scocche, loghi, cornici, ottiche e lettering sono composti dal generatore Java; le geometrie 3D dal generatore JavaScript.

La scritta `Never Settle` compare nella texture fisica e nell'anteprima del OnePlus 15. La Home interattiva usa lo sfondo pulito, con orologio e applicazioni disegnati dall'interfaccia.

## OnePlus 15 — sfondo blu

Asset salvato: [oneplus_15_wallpaper.png](../src/main/resources/assets/napuli/textures/phone/oneplus_15_wallpaper.png).

Prompt finale:

> Use case: product-mockup. Asset type: flat full-bleed smartphone wallpaper texture for a Minecraft handset. Input image is a visual reference of the black OnePlus 15. Recreate ONLY the blue abstract wallpaper inside its front display. Portrait 9:20. A large smoothly shaded blue glass-like oval/crescent curves down the screen from the upper-right edge, with a luminous icy blue thin rim sweeping around the left side and curling into a dark midnight-blue inner arc in the lower half. Deep indigo background, pale lavender-blue light in the middle/lower-right, and the softly lit deep-blue three-dimensional rounded surfaces seen in the reference. Match the reference artwork composition and colors closely. NO handset, NO bezel, NO camera hole, NO logos, NO text (the Never Settle lettering will be added separately), NO numbers, NO UI, NO red badges, NO China icon, NO flags, NO stars, NO watermark or border. Flat rectangular wallpaper only.

## iQOO 15 — sfondo rosso-arancio

Asset salvato: [iqoo_15_wallpaper.png](../src/main/resources/assets/napuli/textures/phone/iqoo_15_wallpaper.png).

Prompt finale:

> Use case: product-mockup. Asset type: flat full-bleed portrait smartphone wallpaper texture for a Minecraft handset. Input image is the user's iQOO 15 front/back product photo; use as a visual reference only. Recreate ONLY the abstract wallpaper inside the front display. Portrait 9:20. A glowing translucent rounded red-orange glass form fills the upper half, with dark burgundy upper corners and a soft bright peach-orange glow towards the center. A thin horizontally stretched bright elliptical rim sits around 44 percent down. Immediately beneath it is a second red rounded glass dome, fading through deep burgundy and charcoal into softly luminous cool blue-gray at the bottom. Symmetrical sculptural hourglass/stacked rounded glass volumes and polished gradients, closely matching the supplied display. NO phone, NO bezel, NO punch hole, NO text, NO logos, NO numbers, NO interface, NO flags, NO stars, NO circular China badge, NO watermark or white border. Wallpaper alone, edge to edge.

## iQOO 15 — materiale perlato

Asset salvato: [iqoo_15_marble.png](../src/main/resources/assets/napuli/textures/phone/iqoo_15_marble.png).

Prompt finale:

> Use case: product-mockup. Asset type: seamless-looking flat full-bleed smartphone rear-cover material texture, portrait 9:20, for a Minecraft model. The input image shows the user's iQOO 15. Recreate ONLY the pearlescent marble/brushed flowing material on the back panel, extrapolated to a full rectangular canvas. Predominantly white, pearl silver and very light gray, with broad flowing diagonal cloudy brush strokes running from lower-left to upper-right, subtle dusty pink, muted salmon and warm gray veins, and silky layered translucent wisps matching the reference. Soft natural sheen, finely detailed high quality matte-glass finish. The material must remain mostly pale silver-white with restrained pink veins. NO phone outline, NO cameras, NO camera module, NO logo or lettering (added separately), NO text, NO numbers, NO flag, NO stars, NO China badge, NO border or watermark. This is just a material surface, no objects and no product background.

## Rigenerazione

`node tools/generate_phones.mjs` e `java tools/GenPhoneTextures.java` rigenerano modelli, texture derivate e anteprime senza avviare Minecraft. Le anteprime fronte/retro sono `.gradle/phone-client/screenshots/oneplus-15-riferimento.png` e `.gradle/phone-client/screenshots/iqoo-15-riferimento.png`.

Texture derivate: `textures/phone/{oneplus_15,iqoo_15}_{front,rear,viewfinder}.png`, `textures/phone/oneplus_15_product_front.png` e `textures/item/phones/{oneplus_15,iqoo_15}_{back,edge,camera,face}.png`.

