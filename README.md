# Mon traitement, application autonome

Ce dossier contient toute l'application. Elle ne dépend pas de Claude et fonctionne sans internet une fois installée.

| Fichier | Rôle |
|---|---|
| index.html | L'application complète (écran du jour, cases à cocher, historique, liste des médicaments, alarme sonore) |
| manifest.json | Nom et icône quand on l'ajoute à l'écran d'accueil |
| sw.js | Permet de l'utiliser sans internet |
| icon.svg, icon-192.png, icon-512.png | Icône de l'application |

Vos médicaments sont déjà dans la liste : RITALINE 60 mg à 8h00, QUETIAPINE 600 mg et TERALITHE 1600 mg à 21h30.

## 1. Mettre l'application en ligne (une seule fois)

Pour être installée sur un téléphone, l'application doit avoir une adresse web en https. Le plus simple et gratuit : **GitHub Pages**.

1. Connectez-vous sur https://github.com
2. Créez un dépôt (bouton « New ») nommé par exemple `traitement`, en « Public ».
3. Cliquez sur « uploading an existing file » et glissez tous les fichiers de ce dossier (sauf ce LISEZMOI si vous voulez). Validez avec « Commit changes ».
4. Allez dans **Settings → Pages**, choisissez la branche `main` puis « Save ».
5. Après une ou deux minutes, l'adresse s'affiche, du type `https://votre-nom.github.io/traitement/`.

Le dépôt est public : n'importe qui ayant l'adresse peut voir la page et la liste de médicaments par défaut. Ce que vous cochez reste uniquement sur votre appareil.

## 2. L'installer sur l'écran d'accueil

**iPhone / iPad (Safari)** : ouvrez l'adresse, bouton Partager, « Sur l'écran d'accueil », Ajouter.

**Android (Chrome)** : ouvrez l'adresse, menu ⋮, « Installer l'application » ou « Ajouter à l'écran d'accueil ».

## 3. Utilisation

- Touchez la grande case quand le médicament est pris. L'heure est notée.
- Touchez « Alarme sonore : désactivée » une fois pour l'activer. À l'heure de prise, l'application sonne jusqu'à ce que vous cochiez ou que vous touchiez « Arrêter l'alarme ».
- Les cases repartent à zéro chaque jour. L'onglet Historique garde les jours précédents.

## Limites

- **Pas de synchronisation** entre téléphone et tablette dans cette version : chaque appareil garde ses propres cases. Cochez toujours sur le même appareil pour éviter une double prise. (La version sur Claude, elle, est synchronisée.)
- L'alarme sonne seulement si l'application est ouverte à l'écran. Gardez des alarmes dans l'appli Horloge.
- Ce n'est pas un widget d'écran d'accueil : il faudrait une application publiée sur l'App Store ou le Play Store.
