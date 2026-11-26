# iQOO 16 — riferimento visivo dell'utente

La finitura e il modulo fotografico seguono l'immagine allegata dall'utente: retro uniforme grigio-viola, marchio `iQOO` discreto nella parte bassa, isola fotografica arrotondata in alto a sinistra e tre lenti disposte a triangolo. La superficie del modulo include il motivo geometrico e le sottili incisioni mostrate nel riferimento.

Scocca, lenti, cornice e marchio sono disegnati da `GenPhoneTextures.java`; la geometria 3D è generata da `generate_phones.mjs`. Lo sfondo viene usato nel display del modello e nella Home interattiva OriginOS della mod.

## Sfondo

Modalità: **imagegen integrato**, usando la fotografia fornita in chat come riferimento. L'output originale è stato copiato nel progetto, senza scaricare immagini dal sito del produttore.

Asset sorgente: [iqoo_16_wallpaper.png](../src/main/resources/assets/napuli/textures/phone/iqoo_16_wallpaper.png).

Asset derivati: `textures/phone/iqoo_16_front.png`, `textures/phone/iqoo_16_viewfinder.png`, `textures/phone/iqoo_16_rear.png` e `textures/item/phones/iqoo_16_face.png`. Il generatore salva anche un confronto fronte/retro in `.gradle/phone-client/screenshots/iqoo-16-riferimento.png`.

Prompt finale:

> Use case: product-mockup. Asset type: a flat full-bleed AMOLED smartphone wallpaper texture for a Minecraft phone. Input image 1 is the user's iQOO 16 front/back product photograph and is a visual reference ONLY. Recreate ONLY the artwork INSIDE the front display, without the handset or product-photo background. Portrait approximately 9:20. A restrained very dark charcoal, graphite, midnight blue and muted slate-violet abstract glass wallpaper. The top has a soft silvery blue-gray glow strongest towards the upper right, fading into deep near-black toward the bottom. A large elegant elongated elliptical loop with a very thin subtle electric-blue/violet rim curves through the middle and lower half, from a rounded lower-left curve to an upper-right curve. Broad overlapping translucent dark curved glass surfaces and one fine diagonal seam run from the lower left toward the upper right. Match the supplied reference's understated, smooth, premium dark geometric composition; avoid a bright neon look. No planet, no stars, no galaxy, no gold, no text, no icons, no clock, no numbers, no phone, no camera hole, no bezel, no border, no watermark. Make the upper third calm and legible for a clock overlay. Realistic polished gradients, soft edge highlights, and rich black levels.
