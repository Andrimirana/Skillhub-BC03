# Rapport technique d'onboarding — SkillHub 

**Pour** : développeur·se junior nouvellement recruté·e  ·  **Date** : 8 mai 2026  ·  **Version** : 1.0

Bienvenue !  tu dois pouvoir cloner, lancer la stack, comprendre l'archi et faire ta première PR. N'hésite pas à demander : tout le monde a été junior un jour.

---

## 1. Le projet contexte

**SkillHub** met en relation **formateurs** et **apprenants** autour de formations en ligne. Bachelor CDA Full Stack

| Acteur     | Ce qu'il fait                                              |
| ---------- | ---------------------------------------------------------- |
| Visiteur   | Voir le catalogue, détail d'une formation                  |
| Apprenant  | S'inscrire (max **5 actives**), suivre sa progression      |
| Formateur  | CRUD ses propres formations et leurs modules               |


---

## 2. Architecture

Le projet comporte **4 microservices** + un frontend, lancés en local natif (PHP CLI, Maven, npm).

```
                       ┌──────────────────┐
                       │   Frontend React │ :5183
                       │     (Vite)       │
                       └────────┬─────────┘
                                │ Bearer JWT
        ┌───────────────────────┼─────────────────────┐
        ▼               ▼               ▼             ▼
   ┌─────────┐   ┌──────────┐   ┌────────────┐   ┌────────┐
   │  Auth   │   │ Catalog  │   │Inscription │   │ Audio  │
   │ Spring  │   │ Laravel  │   │  Laravel   │   │  PHP   │
   │ :8011   │   │  :8012   │   │   :8013    │   │ :8014  │
   └────┬────┘   └────┬─────┘   └─────┬──────┘   └───┬────┘
        │             │               │               │
        │  POST       │  GET          │               │
        │  /validate  │  /formations  │               │
        │  -token     │               │               │
        └─────────────┴───────────────┴───────────────┘
                          │
                  ┌───────┴────────┐
                  ▼                ▼
              ┌───────┐       ┌─────────┐
              │ MySQL │       │ MongoDB │
              │ (4 DB)│       │ (logs)  │
              └───────┘       └─────────┘
```

| Service       | Rôle                                                          | Tech              |
| ------------- | ------------------------------------------------------------- | ----------------- |
| **auth**      | Émet et valide les JWT (HMAC-SHA256 + JWT HS256)              | Spring Boot 3 / Java 17 |
| **catalog**   | CRUD formations & modules (catalogue public + espace formateur) | Laravel 13 / PHP  |
| **inscription** | Inscriptions apprenants (limite 5 actives)                  | Laravel 13 / PHP  |
| **audio**     | Stockage et chiffrement (AES-256) des fichiers audio des formations | PHP             |
| **frontend**  | UI React (Vite, React Router, dark mode)                      | React 19          |

**Principes clés** :
1. Chaque microservice est **indépendant** (sa BDD, ses tests, son cycle de déploiement).
2. **Auth est le seul à émettre des JWT.** Les autres valident chaque requête via `POST /api/validate-token` sur Auth.
3. **MongoDB** = audit trail uniquement (jamais de donnée métier).
4. Communication inter-services via `localhost:<port>` (Auth 8011, Catalog 8012, Inscription 8013).

---

## 3. Stack technique

| Couche      | Tech                          | Version       |
| ----------- | ----------------------------- | ------------- |
| Frontend    | React, Vite, React Router     | React 19      |
| Auth        | Spring Boot, JJWT, Spring Security | 3.x / **Java 17** |
| Catalog     | Laravel, Eloquent             | 13 / **PHP 8.4** |
| Inscription | Laravel, Eloquent             | 13 / **PHP 8.4** |
| Audio       | PHP natif + AES-256-GCM       | **PHP 8.4**   |
| BDD         | MySQL 8 + MongoDB 7           | -             |
| Orchestration | Exécution native (PHP CLI, Maven, npm) | -      |
| Tests       | JUnit 5/JaCoCo + PHPUnit/PCov | -             |
| CI/CD       | GitHub Actions + SonarCloud   | -             |

---

## 4. Comprendre le flux d'authentification (HMAC + JWT)

C'est **le point spécifique de SkillHub** que tu dois maîtriser avant de toucher au code. Le mot de passe ne circule **jamais** sur le réseau, même chiffré.

### Étape 1 — Login (le client signe sa requête)

```
Client                                              Auth (Spring Boot)
  │                                                       │
  │  1. Génère un nonce aléatoire + timestamp UTC         │
  │  2. Calcule :                                         │
  │     hmac = HMAC_SHA256(                               │
  │              clé   = motDePasse,                      │
  │              data  = "email:nonce:timestamp"          │
  │            )                                          │
  │                                                       │
  │  3. POST /api/auth/login                              │
  │     { email, nonce, timestamp, hmac }                 │
  ├──────────────────────────────────────────────────────►│
  │                                                       │
  │                  4. Vérifie timestamp ± 5 min         │
  │                  5. Vérifie nonce non déjà vu (DB)    │
  │                  6. Récupère le passwordEncrypted     │
  │                     du user, le déchiffre AES-256-GCM │
  │                  7. Recalcule HMAC, compare           │
  │                  8. Si OK → émet JWT HS256 (15 min)   │
  │                                                       │
  │  9. { accessToken, jwt, expiresAt }                   │
  │◄──────────────────────────────────────────────────────│
```

### Étape 2 — Requêtes authentifiées (le client utilise le JWT)

```
Client                  Catalog/Inscription              Auth
  │                            │                          │
  │  GET /api/formations       │                          │
  │  Authorization: Bearer JWT │                          │
  ├───────────────────────────►│                          │
  │                            │  POST /api/validate-token│
  │                            │  Authorization: Bearer JWT│
  │                            ├─────────────────────────►│
  │                            │                          │
  │                            │   { valid: true,         │
  │                            │     userId, role }       │
  │                            │◄─────────────────────────│
  │                            │                          │
  │  200 [...]                 │                          │
  │◄───────────────────────────│                          │
```

### À retenir

- **Pourquoi HMAC pour le login ?** Empêche un attaquant qui sniffe le réseau de rejouer la requête (nonce + timestamp) ou de récupérer le mot de passe (jamais transmis).
- **Pourquoi JWT pour les requêtes suivantes ?** Stateless — chaque microservice peut valider sans session partagée.
- **Pourquoi délégation à Auth pour la validation ?** Un seul endroit qui connaît `JWT_SECRET` → rotation possible sans toucher Catalog/Inscription.
- **Anti-rejeu** : nonce unique stocké en base (`auth_nonces`) + fenêtre timestamp de 5 min.

---

## 5. Pré-requis

| Outil           | Version requise   | Vérification             |
| --------------- | ----------------- | ------------------------ |
| **Java JDK**    | **17** (Temurin)  | `java -version`          |
| **PHP CLI**     | **8.4**           | `php --version`          |
| **Node.js**     | 18 LTS+           | `node --version`         |
| **Git**         | 2.30+             | `git --version`          |
| **MySQL**       | 8.0+              | `mysql --version`        |
| **MongoDB**     | 7+ (optionnel)    | `mongod --version`       |
| **Composer**    | 2.6+ (PHP)        | `composer --version`     |
| Maven Wrapper   | (fourni dans repo) | `./mvnw -v`             |

>  PHP, Java et Composer doivent être installés en local. Chaque microservice se lance directement (`php artisan serve`, `mvnw spring-boot:run`, `npm run dev`).

---

## 6. Lancer le projet localement (10 min)

### 6.1. Cloner et configurer les `.env`

```powershell
git clone https://github.com/depôt-github :  non mentionné à cause de la confidentialité de l'examen

cd Skillhub-BC03

Copy-Item .env.example .env
Copy-Item services/catalog/.env.example     services/catalog/.env
Copy-Item services/inscription/.env.example services/inscription/.env
Copy-Item services/audio/.env.example       services/audio/.env
```


### 6.2. Démarrer la stack

```powershell
# Auth (Spring Boot — port 8011)
cd services/auth
.\mvnw spring-boot:run

# Catalog (Laravel — port 8012)
cd services/catalog
composer install
php artisan migrate:fresh --seed
php artisan serve --port=8012

# Inscription (Laravel — port 8013)
cd services/inscription
composer install
php artisan migrate:fresh --seed
php artisan serve --port=8013

# Frontend (port 5183)
cd frontend
npm install
npm run dev
```

### 6.3. Vérifier

- http://localhost:5183 → page d'accueil React
- http://localhost:8011/api/health → `{"status":"UP"}`
- http://localhost:8012/api/formations → liste JSON
- http://localhost:8014 → service audio

>  Ne committe **jamais** un `.env`. Génère un `JWT_SECRET` ≥ 256 bits :
> ```powershell
> [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
> ```

---

## 7. Structure du dépôt

```
frontend/                  # React 19 (Vite)
services/
  auth/                    # Spring Boot 3 / Java 17
    src/main/java/com/example/auth/
      controller/  service/  entity/  repository/  dto/  config/
  catalog/                 # Laravel 13 — formations & modules
  inscription/             # Laravel 13 — inscriptions (limite 5)
  audio/                   # PHP — chiffrement & lecture des fichiers audio
.github/workflows/         # Pipeline CI (sonarcloud.yml)
sonar-project.properties
openapi.yaml               # Contrat OpenAPI 3
```

---

## 8. Commandes du quotidien

| Action                            | Commande                                |
| --------------------------------- | --------------------------------------- |
| Lancer Auth (Spring Boot)         | `cd services/auth && .\mvnw spring-boot:run` |
| Lancer Catalog (Laravel)          | `cd services/catalog && php artisan serve --port=8012` |
| Lancer Inscription (Laravel)      | `cd services/inscription && php artisan serve --port=8013` |
| Frontend en hot-reload            | `cd frontend && npm install && npm run dev` |
| Tests Auth                        | `cd services/auth && .\mvnw verify`     |
| Tests Catalog/Inscription         | `php artisan test --coverage-clover coverage.xml` |
| Vider cache Laravel               | `php artisan cache:clear && php artisan config:clear` |

---

## 9. Contribuer — Workflow Git

**Branches** :
- `main` — production, **protégée** (merge uniquement par PR validée)
- `dev` — branche d'**intégration** (ta cible par défaut pour les PR)
- `feature/<sujet>` — ta branche de travail (ex : `feature/limite-inscription`)
- `fix/<sujet>` — pour les correctifs

**Conventional Commits** (obligatoire) :
```
<type>(<scope>): <résumé impératif court>

types autorisés : feat, fix, chore, docs, test, refactor, ci, style, perf
```

Exemples réels du projet :
- `feat(inscription): limiter à 5 inscriptions actives par apprenant`
- `fix(catalog): corriger typo route {format363ion} -> {formation}`
- `test(inscription): test fonctionnel limite d'inscription`
- `ci: durcir exclusions SonarCloud pour seeders Laravel`

**Cycle d'une PR** :
1. `git checkout dev && git pull`
2. `git checkout -b feature/ma-fonctionnalite`
3. Code + **tests** (une PR sans test n'est pas mergée)
4. `git commit` (un commit = une intention claire)
5. `git push -u origin feature/ma-fonctionnalite`
6. Ouvrir une **Pull Request** vers `dev` (jamais directement vers `main`)
7. Attendre la CI verte (~10 min) — voir [GitHub Actions](https://github.com/Andrimirana/Skillhub-BC03/actions)
8. Demander une review à un·e collègue
9. Après merge, supprimer la branche

**Lancer les tests avant de pousser** :
```powershell
# Auth
cd services/auth && ./mvnw verify

# Catalog
cd services/catalog
php artisan test --coverage-clover coverage.xml

# Inscription
cd services/inscription
php artisan test --coverage-clover coverage.xml
```

>  Pas de `--no-verify` sur les hooks Git sans demander.

---

## 10. Quality Gate SonarCloud

Sur **chaque PR**, le **New Code** (= ton diff vers `dev`) doit respecter :

| Condition          | Seuil    |
| ------------------ | -------- |
| Coverage           | ≥ 80%    |
| Duplications       | ≤ 3%     |
| Security Rating    | A        |
| Reliability Rating | A        |
| Maintainability    | A        |
| Hotspots reviewed  | 100%     |

Dashboard : https://sonarcloud.io/project/overview?id=skillhub-bc03

**Si ça échoue** : ajoute des tests (coverage), factorise (duplications), corrige le smell (rating). En dernier recours : `// NOSONAR <règle> — <justification claire>`.

---

## 11. Points d'attention — CI/CD et SonarCloud

###  `SONAR_TOKEN` dans les secrets GitHub

Le job **`sonarcloud`** du workflow [.github/workflows/sonarcloud.yml](.github/workflows/sonarcloud.yml) utilise deux secrets GitHub :

```yaml
env:
  GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}     # fourni automatiquement
  SONAR_TOKEN:  ${{ secrets.SONAR_TOKEN }}      # à configurer manuellement
```

- **`SONAR_TOKEN`** se génère sur https://sonarcloud.io → *My Account → Security → Generate Tokens*
- À ajouter dans **GitHub → Settings → Secrets and variables → Actions → New repository secret**
- Si le token est absent ou expiré → le job `sonarcloud` échoue avec `Not authorized`. Pas de panique, c'est un problème de secret, pas de ton code.
- **Ne JAMAIS** committer la valeur du token, même temporairement.

### Configuration de `sonar-project.properties`
Le fichier [sonar-project.properties](sonar-project.properties) à la racine pilote toute l'analyse. Quand tu interviens dessus, comprends les 5 propriétés clés :

| Propriété                   | Rôle                                                      |
| --------------------------- | --------------------------------------------------------- |
| `sonar.sources`             | Dossiers à analyser (Java auth + PHP catalog/inscription) |
| `sonar.exclusions`          | Fichiers exclus de **toute** analyse                      |
| `sonar.cpd.exclusions`      | Fichiers exclus **uniquement** de la détection de duplication |
| `sonar.cpd.minimumTokens`   | Seuil de duplication (300 = ~50 lignes)                   |
| `sonar.coverage.jacoco.xmlReportPaths` / `sonar.php.coverage.reportPaths` | Chemins des rapports de couverture |

**Pièges à éviter** :

-  **Toutes les exclusions sont sur une SEULE ligne**, séparées par des virgules. Un saut de ligne casse le parser Java Properties.
-  **`sonar.exclusions` ≠ `sonar.test.exclusions`** : le premier exclut les fichiers source ; pour exclure les tests, il faut explicitement `sonar.test.exclusions`.
-  Les seeders/factories Laravel sont **explicitement exclus** (ils contiennent des données de démo qui produisent de la duplication artificielle).
-  **`sonar.javascript.exclusions=**/*` est volontaire** : le frontend React n'est pas analysé par SonarCloud (pas de rapport de couverture JS configuré).

###  Lecture du dashboard SonarCloud

- **Branche `dev`** : analyse globale, doit être verte
- **Branches `feature/*`** : QG appliqué sur le **New Code** uniquement
- **Quality Gate "Not computed"** : généralement la *New Code Definition* n'est pas configurée → *Project Settings → New Code → Number of days = 30*

---

## 12. Sécurité — 3 règles d'or

1. **Aucun secret en dur.** Tout passe par `.env` + `@Value` / `getenv()`.
2. **Aucun mot de passe en clair, jamais.** Stocké AES-256-GCM, transmis en HMAC.
3. **Toute requête sur Catalog/Inscription a un JWT valide** (sauf `GET /formations` public). Ne contourne jamais `ValidateServiceToken`.

Si tu trouves une faille : pas de mot "exploit" dans un commit, parle à un senior.

---

## 13. Première mission (2 jours)

**Jour 1 — Comprendre** :
- Lancer la stack, créer un compte, s'inscrire à 5 formations puis tenter la 6ᵉ
- Lire `AuthService.java` et `FormationController.php`
- Lancer les tests des 3 services, ouvrir le rapport JaCoCo

**Jour 2 — Contribuer** (choisis une) :
-  Enrichir `openapi.yaml` avec exemples sur `/api/auth/login`
-  Ajouter un test "timestamp expiré > 5 min" dans Auth
-  Endpoint `GET /api/formations/categories` (liste distincte)
-  Extraire `extractBearerToken` (dupliqué `AuthController` / `UserController`) dans une util

→ Branche `feature/`, tests, PR vers `dev`, demande de review.



**Référents** :

| Sujet                  | Qui                |
| ---------------------- | ------------------ |
| Onboarding, accès      | Tech Lead          |
| Architecture, design   | Tech Lead / Senior |
| CI/CD, SonarCloud      | DevOps référent    |
| Sécurité, crypto       | Senior Auth        |
| Frontend, UX           | Senior Frontend    |
| Métier, règles         | Product Owner      |




