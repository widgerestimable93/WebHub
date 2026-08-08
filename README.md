# WebHub

Lanceur de Web Apps : une seule interface pour accéder à toutes vos Web Apps (baccPRO, 9PRO, Collection Dorée, OddsLab, etc.) sans installer un APK par application.

100 % HTML5 / CSS3 / JavaScript ES6 natif — aucun framework (pas de React/Angular/Vue), PWA installable, prêt pour GitHub Pages.

## Structure du projet

```
webhub/
├── index.html          # Toute l'application (accueil, visionneuse, paramètres, modale d'ajout)
├── manifest.json        # Manifeste PWA
├── service-worker.js    # Cache de l'app shell WebHub pour le mode hors-ligne
├── css/
│   └── styles.css       # Design tokens + tous les styles
├── js/
│   ├── db.js             # Couche IndexedDB (Web Apps + paramètres)
│   └── app.js            # Logique applicative (rendu, recherche, visionneuse, paramètres)
├── icons/
│   └── icon.svg          # Icône source (voir "Icônes" ci-dessous)
├── README.md
└── LICENSE
```

**Choix d'architecture par rapport au brief initial** : l'accueil, la visionneuse et la page Paramètres sont regroupés dans un seul `index.html` (vues basculées en JavaScript) plutôt que répartis dans un dossier `pages/`. Cela évite tout rechargement complet du navigateur entre les écrans (transitions fluides, exigence du brief) et garantit que l'état de l'application (thème, recherche, Web Apps chargées) est conservé pendant la navigation.

## Installation / démarrage local

Aucune dépendance à installer. Il suffit de servir le dossier en HTTP (les service workers exigent HTTP/HTTPS, pas `file://`) :

```bash
npx serve webhub
# ou
python3 -m http.server --directory webhub 8080
```

Puis ouvrir `http://localhost:8080`.

## Déploiement sur GitHub Pages

1. Pousser le contenu du dossier `webhub/` à la racine d'un dépôt GitHub (ou dans `/docs`).
2. Dans le dépôt : **Settings → Pages → Source**, sélectionner la branche et le dossier.
3. GitHub Pages sert automatiquement en HTTPS, requis pour l'installation PWA et l'enregistrement du service worker.
4. Si le site est publié sur `https://<utilisateur>.github.io/<repo>/`, vérifier que tous les chemins restent relatifs (c'est déjà le cas dans ce projet : `./css/...`, `./js/...`).

## Ajouter une nouvelle Web App

Aucun redéploiement n'est nécessaire : tout se fait depuis l'interface.

1. Cliquer sur le bouton flottant **+**.
2. Renseigner Nom, Description, URL (HTTPS obligatoire), Icône (emoji) et Couleur.
3. Enregistrer : la Web App est stockée dans IndexedDB (navigateur local) et apparaît immédiatement dans la grille.
4. Double-cliquer sur une carte pour la modifier ou la supprimer.

## Isolation des données entre Web Apps

Chaque Web App s'ouvre dans une `<iframe>` pointant vers sa propre URL. WebHub ne stocke jamais les cookies, le localStorage ou le cache d'une Web App : c'est le navigateur qui applique nativement la politique de même origine (*Same-Origin Policy*), qui isole automatiquement les données de chaque origine. Deux Web Apps sur des domaines différents ne peuvent techniquement pas accéder aux données l'une de l'autre.

**Limite technique à connaître sur le bouton « Déconnexion »** : pour ces mêmes raisons de sécurité, une page ne peut pas effacer par JavaScript le localStorage ou les cookies d'une autre origine, même depuis une iframe qu'elle héberge. Le bouton Déconnexion de WebHub recharge donc complètement l'iframe (ce qui réinitialise l'état en mémoire de la Web App), mais un effacement garanti des cookies/localStorage persistants dépend de la Web App elle-même (son propre bouton de déconnexion) ou d'un nettoyage manuel dans les paramètres du navigateur.

**Autre limite à connaître** : certains sites définissent un en-tête `X-Frame-Options` ou une directive CSP `frame-ancestors` qui empêche explicitement d'être affichés dans une iframe. Une Web App concernée refusera de s'afficher dans la visionneuse WebHub ; le bouton « Ouvrir dans un nouvel onglet » du menu de la visionneuse permet alors d'y accéder normalement.

## Création de l'APK Android

WebHub étant une PWA standard, deux approches sont possibles :

- **WebView minimal** : créer un projet Android avec une `WebView` unique pointant vers l'URL GitHub Pages de WebHub. C'est l'approche la plus simple pour obtenir un APK installable.
- **Outils de conversion PWA → APK** : des outils comme [Bubblewrap](https://github.com/GoogleChromeLabs/bubblewrap) (officiel Google, basé sur Trusted Web Activity) permettent de générer un APK signé directement à partir du `manifest.json`.

## Icônes

Le dossier `icons/` contient une icône vectorielle source (`icon.svg`) référencée dans `manifest.json` et `index.html`. Les navigateurs Chromium récents acceptent les icônes SVG dans le manifeste. Pour une compatibilité maximale (notamment l'écran d'accueil iOS, qui n'accepte pas le SVG comme icône), il est recommandé d'exporter `icon.svg` en PNG aux tailles standards (au minimum 192×192 et 512×512) et de les référencer dans `manifest.json` avant publication finale.

## Configuration

- **Thème** : clair, sombre ou automatique (suit les préférences système au premier lancement), togglable depuis l'en-tête ou les Paramètres, persisté dans IndexedDB.
- **Recherche / Favoris / Catégories / Tri** : disponibles depuis l'écran d'accueil.
- **Export / Import** : dans Paramètres → Données, pour sauvegarder ou restaurer la liste des Web Apps (fichier JSON).
- **Vider le cache** : supprime uniquement le cache hors-ligne de WebHub (l'app shell), jamais les données des Web Apps.

## Mises à jour

Le service worker utilise une stratégie « cache d'abord, mise à jour en tâche de fond » pour l'app shell de WebHub : à chaque visite, une nouvelle version des fichiers est récupérée en arrière-plan et prendra effet à la prochaine ouverture. Incrémenter `CACHE_NAME` dans `service-worker.js` force l'invalidation du cache lors d'un déploiement majeur.

## Qualité du code

Code modulaire (séparation stockage / logique applicative / présentation), commenté, sans dépendance externe hors polices Google Fonts (Roboto, Roboto Mono) chargées via CDN.
