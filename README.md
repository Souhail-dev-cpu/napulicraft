# Napulicraft

Minecraft in napulitano: 'o Munaciello che t'arrobba, 'o Munaciello gigante, Napoli intera (Galleria Umberto I,
Piazza del Plebiscito, Castel dell'Ovo, Borgo Marinari, 'o Vesuvio), 'e carabiniere, 'o motorino cu 'a radio stile GTA…
e 'a musica **ca sciglie tu**.

*Minecraft in Neapolitan: a meme mod with a whole Naples to explore, a thieving ghost monk, carabinieri, scooters with a
GTA-style radio, and the soundtrack **you** choose.*

> Fabric · Minecraft 26.3 · Java 25 · requires [Fabric API](https://modrinth.com/mod/fabric-api) on client and server.

## No songs included — bring your own

The mod ships **without any music**. Every player picks their own:

1. Start the game once, then put your audio files (`.mp3`, `.ogg`, `.wav`) in
   `.minecraft/napulicraft/canzoni/` (in-game: press **J** → **📂 Apri cartella**).
2. Every **sub-folder** becomes a **radio station** for the scooter (`canzoni/Radio d'o Vico/*.mp3`).
3. Press **J** to open the **Canzoni** screen and choose, for every situation (dying, fights, the giant, love,
   night, diamonds…), which song plays and from which second (`-5s` / `+5s`, click the title to listen).
   Leave it on **Casuale** and a random song from your folder is used.
4. **⬇ Scarica link**: paste a *direct* link to an audio file and it is downloaded into the folder.
   Streaming sites (YouTube, Spotify, SoundCloud…) are not supported.

Only use music you have the right to use. Free sources: [Free Music Archive](https://freemusicarchive.org),
[Jamendo](https://www.jamendo.com), [Internet Archive](https://archive.org/details/audio),
[Pixabay Music](https://pixabay.com/music/), or your own recordings.

Choices are saved in `.minecraft/config/napulicraft-canzoni.json`. You can also add stations by hand there:

```json
{
  "situazioni": { "lite": { "canzone": "my song.mp3", "da": 61 } },
  "stazioni": [ { "nome": "Radio Core 'e Mamma", "dj": "Assuntina", "colore": "#F06292",
                  "canzoni": ["song a.mp3", "song b.mp3"] } ]
}
```

## Controls

| Key | What |
| --- | --- |
| **R** (hold, on the scooter) | Radio wheel: point at a station, release R. Tap R = next station |
| **J** | Canzoni: pick your songs |

## Giorgia Meloni and the weekly tax

A fictional Giorgia Meloni NPC, with blonde hair, a black suit and a white shirt, visits each Survival/Adventure
player every **3 Minecraft days** (72,000 day-clock ticks, including nights skipped by sleeping).
She appears about **50 blocks away**, finds a safe walking route and approaches you with a **weekly tax** dialog.
If she cannot reach you within **2 minutes** (2,400 server ticks), she disappears and sends you to prison for tax
evasion. A barricade does not prevent her visit: she can appear safely outside it while attempting to reach you.
The approach deadline survives a collector respawn, logout, death or dimension change. Reaching you and opening
the dialog cancels it; skipped nights and changes to the day clock do not shorten these two minutes.

- **Pay with items:** up to **15 individual items in total**, preferring building blocks, then ores and other materials/consumables.
  Weapons, armor, tools, equipment and portable containers are protected, including the mod's weapons and ciabatta.
- **Pagamento partita iva:** pay **30% of your current wallet balance**, rounded up to whole euros (100 € → 30 €;
  101 € → 31 €). This option leaves your inventory intact. The dialog shows the amount before you confirm;
  if your balance changes enough to change the tax, you must review the new amount before paying.
- **No taxable materials and no money:** confirm the exemption. If only one payment method is available, use that one.
- **Refuse:** **90 seconds in the Napoli prison for tax evasion**, in Adventure mode. Your equipment stays with you;
  your previous game mode is restored when the sentence ends.

Closing the dialog leaves the request pending; right-click her to reopen it. The next visit is due three days after
payment, exemption or refusal. Pending taxes and prison sentences survive death and server restarts; changing
dimensions does not escape a sentence. Creative and Spectator players are exempt from visits.

Operators can use **`/napuli meloni`** to trigger a visit immediately while in Survival/Adventure mode.
The opt-in integration test **`./gradlew runTaxtest`** creates a disposable world, checks the schedule, approach,
inventory protection, both payment methods, rounding, zero balance, approach timeout, refusal and release,
saves screenshots in `run-autotest/screenshots`, and exits.

## Building

Every push to GitHub (on any branch or tag) runs the **Build and publish beta** workflow:
it builds the mod with Java 25 and publishes the installable JAR in a new
[GitHub pre-release](https://github.com/Souhail-dev-cpu/napulicraft/releases).
The beta version includes the workflow run number and attempt, for example
`1.1.0-beta.42.1`, in the JAR filename, mod metadata, and release tag (`v1.1.0-beta.42.1`).
Re-running a workflow creates a new beta, and a failed build does not publish a release.
The workflow can also be started manually from **Actions → Build and publish beta → Run workflow**.
It uses the automatic `GITHUB_TOKEN`; no extra secrets or manually created tags are needed.

To build locally:

```
./gradlew build        # jar in build/libs
./gradlew runClient    # dev client
```

Dev play-tests (screenshots in `run-autotest/screenshots`): `runMaptest`, `runRadiotest`, `runPolicetest`,
`runSpawntest`, `runCitytest`.

## Licence

Code and textures: [MIT](LICENSE). The mod bundles [JLayer](https://github.com/umjammer/jlayer) 1.0.1 (LGPL-2.1)
to decode MP3 files. It's a parody/meme mod: names of real places and institutions are used for fun.
