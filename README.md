# Nova UI

Launcher (écran d'accueil) pour Android TV, Google TV et Fire TV. Sans pub, sans tracking, centré sur les apps installées.

- **Direction visuelle** : « Rail » — rail glass à gauche, horloge géante, grille d'apps sans noms.
- **Palette** : saphir `#0F52BA`, océan `#0077BE`, accent jade néon `#00F5A0` (focus uniquement).
- **Typo** : Unbounded (titres), Manrope (texte) — licence OFL, voir `FONTS-OFL-*.txt`.
- **Stack** : Kotlin, Jetpack Compose + Compose for TV, minSdk 22 (Fire OS 5+).

## Obtenir l'APK

Chaque push sur `main` compile l'APK via GitHub Actions et le publie dans **Releases** :
`https://github.com/imadevlx-arch/nova-ui/releases/latest/download/nova-ui.apk`

> Downloader ne peut télécharger que depuis une URL publique : le dépôt (ou un dépôt dédié aux releases) doit être public.

## Signature

- Sans secrets : signature avec `signing/dev.keystore` — **tests uniquement**.
- Release publique : ajouter les secrets `NOVA_KEYSTORE_B64`, `NOVA_KEYSTORE_PASSWORD`, `NOVA_KEY_ALIAS`, `NOVA_KEY_PASSWORD`. Passer de la clé dev à la clé release impose de désinstaller/réinstaller une fois.

## Devenir l'écran d'accueil

- **Android TV / Google TV** : appuyer sur Accueil et choisir Nova UI si le système le propose ; sinon, désactiver le launcher d'origine via ADB.
- **Fire TV** : Amazon bloque le remplacement du launcher. Nova UI s'ouvre comme une app ; l'interception du bouton Accueil est un chantier à part.
