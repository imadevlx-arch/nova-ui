# Nova Player

Lecteur IPTV pour Fire TV / Android TV. Ne fournit aucun contenu : le client se connecte avec son propre abonnement.

## Installer avec Downloader

```
https://github.com/imadevlx-arch/nova-ui/releases/download/nova-player-latest/nova-player.apk
```
L'URL ne change pas d'une version à l'autre (tag glissant `nova-player-latest`). Raccourcir via un shortener ou un code Downloader (aftv.news) pour la saisie à la télécommande.

## Connexion (le moins de saisie possible)

| Méthode | Pour qui |
|---|---|
| **Code** | Le revendeur crée un code de 8 caractères (backend `backend/activation-worker`). Visible seulement si `NOVA_ACTIVATION_URL` est défini. |
| **Téléphone** | QR code à l'écran → le client colle sur son téléphone le message reçu (WhatsApp, e-mail). Même Wi-Fi requis. |
| **Xtream** | Serveur + identifiant + mot de passe. Un lien complet collé dans « Serveur » suffit. |
| **Lien M3U** | Les liens `get.php?username=…&password=…` passent automatiquement en Xtream (films + séries). |

Le texte collé est analysé : lien get.php / player_api, URL de flux `/live/u/p/1.ts`, ou bloc « Host / Username / Password ».

## Codes d'activation

Créer un code (après déploiement du worker) :
```
curl -X POST https://nova-activation.<compte>.workers.dev/v1/codes \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"server":"http://serveur:8080","username":"client1","password":"xxxx","days":30}'
```
