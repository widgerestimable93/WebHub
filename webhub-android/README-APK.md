# WebHub — App Android (WebView)

Ce dossier contient un projet Android minimal : une seule Activity avec une WebView plein écran pointant vers `https://widgerestimable93.github.io/WebHub/`. WebHub lui-même (recherche, favoris, thème, visionneuse) tourne entièrement dans cette WebView — ce projet ne fait qu'emballer l'URL en APK installable.

## Étape 1 — Créer le keystore de signature (une seule fois, à conserver pour toujours)

Ne jamais régénérer ce fichier pour une mise à jour : un changement de signature oblige les utilisateurs à désinstaller l'app avant de réinstaller la nouvelle version.

Dans un terminal (Codespace ou local) :

```bash
keytool -genkeypair -v -keystore webhub-release.keystore -alias webhub -keyalg RSA -keysize 2048 -validity 10000
```

Réponds aux questions (nom, organisation, etc. — peu importe le contenu). Choisis un mot de passe de keystore et un mot de passe de clé (ils peuvent être identiques). **Note-les précieusement**, ils ne sont jamais récupérables.

Convertis-le en base64 pour le secret GitHub :

```bash
base64 -w 0 webhub-release.keystore > webhub-release.keystore.b64
cat webhub-release.keystore.b64
```

Copie la sortie (une seule longue ligne).

## Étape 2 — Ajouter les secrets GitHub

Sur le dépôt GitHub (ou un dépôt dédié pour ce projet Android) : **Settings → Secrets and variables → Actions → New repository secret**, et crée :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | le contenu de `webhub-release.keystore.b64` |
| `KEYSTORE_PASSWORD` | le mot de passe du keystore |
| `KEY_ALIAS` | `webhub` (ou l'alias choisi à l'étape 1) |
| `KEY_PASSWORD` | le mot de passe de la clé |

## Étape 3 — Pousser ce projet dans un dépôt GitHub

Ce dossier (`webhub-android/`) doit être poussé dans son propre dépôt (ou un sous-dossier d'un dépôt existant, en adaptant le déclencheur du workflow si besoin) — c'est un projet Android distinct du dépôt `WebHub` qui contient la PWA elle-même.

## Étape 4 — Lancer le build

Le workflow `.github/workflows/build-apk.yml` se déclenche automatiquement à chaque `push` sur `main`, ou manuellement depuis l'onglet **Actions → Build APK → Run workflow**.

Une fois le workflow terminé (icône verte), l'APK signé est disponible dans l'onglet **Actions** du run correspondant, section **Artifacts** → `webhub-release-apk`. Télécharge le zip, il contient `app-release.apk`.

## Installer l'APK sur un téléphone Android

Transférer `app-release.apk` sur le téléphone puis l'ouvrir (autoriser "Installer des apps inconnues" pour la source utilisée si demandé).

## Mettre à jour l'app plus tard

Pas besoin de reconstruire ce projet Android à chaque changement de WebHub : puisque tout le contenu vit dans la WebView, **modifier et republier le dépôt `WebHub` (GitHub Pages) suffit** — l'app Android affichera automatiquement la nouvelle version au prochain lancement. Il ne faut relancer ce build Android que pour changer l'icône, le nom de l'app, ou le comportement natif (téléchargements, permissions, etc.).

## Limites connues

- **`versionCode` / `versionName`** dans `app/build.gradle.kts` doivent être incrémentés manuellement avant chaque nouvelle publication sur le Play Store (pas nécessaire pour une simple installation directe de l'APK).
- **Export JSON depuis les Paramètres de WebHub** : la WebView Android ne peut pas récupérer un fichier `blob:` généré en JavaScript comme le ferait un navigateur complet. Cette fonctionnalité reste limitée à la version web (Chrome, etc.) tant qu'un pont JavaScript dédié n'est pas ajouté à l'app native.
- **Icône** : une icône adaptative vectorielle (API 26+) et des PNG de secours (API 24-25) sont fournis, dérivés du logo WebHub. Remplace `app/src/main/res/drawable/ic_launcher_foreground.xml` et les PNG dans les dossiers `mipmap-*` si tu veux un design différent.
- **Play Store** : publier sur le Play Store nécessite en plus un compte développeur Google (payant, une fois), une fiche store (captures d'écran, description, politique de confidentialité), et généralement un **Android App Bundle** (`gradlew bundleRelease`) plutôt qu'un APK direct.

