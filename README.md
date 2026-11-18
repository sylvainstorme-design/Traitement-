# Mon traitement (Android)

Application Android avec un **widget d'écran d'accueil** pour cocher ses médicaments du jour, et une **vraie alarme** à chaque heure de prise.

- Widget : le jour, chaque médicament avec dosage et heure, une case à toucher une fois pris.
- Les cases repartent à zéro chaque jour à minuit. Historique des 14 derniers jours dans l'application.
- Alarme à 8h00 et 21h30 (selon la liste) qui sonne même écran éteint, jusqu'à « C'est pris ».
- Liste modifiable dans l'application. Préremplie : RITALINE 60 mg à 8h00, QUETIAPINE 600 mg et TERALITHE 1600 mg à 21h30.

## Installer sur la tablette

1. Sur la tablette, ouvrez https://github.com/sylvainstorme-design/Traitement-/releases/latest/download/MonTraitement.apk
2. Ouvrez le fichier téléchargé. Si Android le demande, autorisez « Installer des applis inconnues » pour votre navigateur, puis revenez et touchez **Installer**.
3. Ouvrez « Mon traitement », acceptez les notifications, puis touchez **Ajouter le widget sur l'écran d'accueil**.

L'application est construite automatiquement par GitHub (onglet Actions) à chaque modification. L'ancienne version web est dans le dossier `web/`.
