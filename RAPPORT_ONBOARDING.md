# Onboarding SkillHub

**Pour qui** : la nouvelle recrue · **Date** : 8 mai 2026

> Bienvenue dans l'équipe ! L'idée de ce doc, c'est que tu sois autonome :
> tu clones, tu lances la stack, tu fais ta première PR. Si quelque chose coince, demande —
> personne n'a appris ce projet en une heure.

---

## Le projet en 30 secondes

**SkillHub** est une plateforme web où des **formateurs** publient des formations et où
des **apprenants** s'y inscrivent (max 5 actives en même temps). C'est un projet de
Bachelor (Bloc 03 – Cloud, DevOps & Architecture, promo 2025/2026) pour pratiquer
l'archi microservices et la qualité industrielle.

---

## Architecture

Quatre microservices + un frontend, tout en local natif :

```
        ┌──────────────┐
        │  Frontend    │  React 19 / Vite        :5183
        └──────┬───────┘
               │ Bearer JWT
   ┌───────────┼────────────┬─────────────┐
   ▼           ▼            ▼             ▼
┌──────┐  ┌─────────┐  ┌────────────┐  ┌──────┐
│ Auth │  │ Catalog │  │Inscription │  │Audio │
│Spring│  │ Laravel │  │  Laravel   │  │ PHP  │
│:8011 │  │  :8012  │  │   :8013    │  │:8014 │
└───┬──┘  └────┬────┘  └─────┬──────┘  └──┬───┘
    └──────────┴─────────────┴────────────┘
                       │
                  MySQL + MongoDB (logs)
```

| Service       | Rôle                                                     | Tech         |
| ------------- | -------------------------------------------------------- | ------------ |
| **auth**      | Émet les JWT, valide les tokens (HMAC-SHA256 + JWT HS256) | Java 17      |
| **catalog**   | Formations & modules (CRUD)                              | PHP 8.4      |
| **inscription** | Inscriptions apprenants (limite 5)                     | PHP 8.4      |
| **audio**     | Stockage chiffré (AES-256) des fichiers audio            | PHP 8.4      |

**À retenir** : seul **Auth** émet des JWT. Catalog et Inscription valident chaque
requête en appelant `POST /api/validate-token` sur Auth. Pas de session partagée.

---

## Le flux d'auth (le point qui surprend en arrivant)

Le mot de passe **ne circule jamais** sur le réseau. À la connexion, le client calcule :

```
hmac = HMAC_SHA256(clé = motDePasse, données = "email:nonce:timestamp")
```

et envoie `{email, nonce, timestamp, hmac}`. Le serveur reconstitue le HMAC avec
le mot de passe stocké (chiffré AES-256-GCM) et compare. Si OK → JWT signé HS256
valable 15 min, à mettre dans `Authorization: Bearer <jwt>` pour les requêtes suivantes.

Anti-rejeu : nonce unique stocké en base + fenêtre timestamp ±5 min.

---

## Pré-requis

- **Java JDK 17** (Temurin)
- **PHP 8.4** + Composer 2.6+
- **Node.js 18+**
- **Git**, **MySQL 8** (MongoDB 7 si tu veux les logs d'activité)

---

## Lancer le projet (10 min)

```powershell
git clone https://github.com/Andrimirana/Skillhub-BC03.git
cd Skillhub-BC03

# Copier les .env
Copy-Item .env.example .env
Copy-Item services/catalog/.env.example     services/catalog/.env
Copy-Item services/inscription/.env.example services/inscription/.env
Copy-Item services/audio/.env.example       services/audio/.env

# Lancer chaque service dans son propre terminal
cd services/auth         && .\mvnw spring-boot:run                   # port 8011
cd services/catalog      && composer install && php artisan serve --port=8012
cd services/inscription  && composer install && php artisan serve --port=8013
cd frontend              && npm install && npm run dev               # port 5183
```

**Vérifier que ça tourne** : http://localhost:8011/api/health → `{"status":"UP"}`

> Génère un `JWT_SECRET` ≥ 256 bits (sinon Spring Boot refuse de démarrer) :
> `[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`
> Et **ne committe jamais** un `.env`.

---

## Structure du dépôt

```
frontend/                     React 19 (Vite)
services/auth/                Spring Boot 3 / Java 17
services/catalog/             Laravel 13 — formations & modules
services/inscription/         Laravel 13 — inscriptions (limite 5)
services/audio/               PHP — fichiers audio chiffrés
.github/workflows/            Pipeline CI (sonarcloud.yml)
sonar-project.properties      Configuration SonarCloud
openapi.yaml                  Contrat OpenAPI 3
```

---

## Lancer les tests

```powershell
# Catalog (38 tests)
cd services/catalog
$env:DB_CONNECTION="sqlite"
$env:DB_DATABASE="database/database.sqlite"
php artisan test

# Inscription (19 tests + 1 skip MongoDB local)
cd services/inscription
$env:DB_CONNECTION="sqlite"
$env:DB_DATABASE="database/database.sqlite"
php artisan test

# Auth (Java)
cd services/auth
.\mvnw verify
```

Pour la couverture : ajoute `--coverage-clover coverage.xml` aux commandes PHP.
La couverture JaCoCo est dans `services/auth/target/site/jacoco/index.html`.

---

## Workflow Git

- `main` → production (protégée)
- `dev` → intégration (ta cible par défaut pour les PR)
- `feature/<sujet>` ou `fix/<sujet>` → ta branche de travail

**Conventional Commits** obligatoires :

```
feat(inscription): limiter à 5 inscriptions actives
fix(catalog): corriger 500 sur recherche Unicode
test(auth): couvrir le timestamp expiré
```

Cycle d'une PR : branche → code + **tests** → push → PR vers `dev` → CI verte (~10 min) → review → merge.
Pas de `--no-verify` sur les hooks sans demander.

---

## Quality Gate SonarCloud

Sur chaque PR, le **New Code** (= ton diff) doit respecter :

| Condition          | Seuil    |
| ------------------ | -------- |
| Coverage           | ≥ 80 %   |
| Duplications       | ≤ 3 %    |
| Security/Reliability/Maintainability Rating | A |
| Hotspots reviewed  | 100 %    |

Dashboard : https://sonarcloud.io/project/overview?id=skillhub-bc03

Si ça échoue : ajoute des tests (coverage), factorise (duplications), corrige le smell.
En dernier recours : `// NOSONAR <règle> — <justification claire>`.

---

## Points d'attention sur la CI

- **`SONAR_TOKEN`** doit exister dans GitHub Secrets (sinon le job `sonarcloud`
  échoue avec « Not authorized »). Le générer sur SonarCloud → My Account → Security.
- **`sonar-project.properties`** : tout est sur une seule ligne pour `sonar.exclusions`,
  un saut de ligne casse le parser Java Properties.
- **Les seeders/factories Laravel sont exclus de l'analyse** — ce sont des données
  de démo qui produisent de la duplication artificielle.
- **`sonar.javascript.exclusions=**/*`** : le frontend n'est volontairement pas
  analysé par SonarCloud.

---

## Sécurité — 3 règles d'or

1. **Aucun secret en dur.** Tout passe par `.env`.
2. **Jamais de mot de passe en clair.** Stocké AES-256-GCM, transmis en HMAC.
3. **Toute requête sur Catalog/Inscription a un JWT valide** (sauf `GET /formations` public).
   Ne contourne jamais `ValidateServiceToken`.

Si tu trouves une faille : pas de mot « exploit » dans un commit, parle à un senior.

---

## Ta première mission (sur 2 jours)

**Jour 1 — comprendre** : lance la stack, crée un compte apprenant, inscris-toi à 5 formations,
tente la 6ᵉ. Lis `AuthService.java` et `FormationController.php`. Regarde le rapport JaCoCo.

**Jour 2 — contribuer**, choisis une mission :

- 🟢 Enrichir `openapi.yaml` avec un exemple sur `/api/auth/login`
- 🟡 Ajouter un test « timestamp expiré » dans Auth
- 🟠 Endpoint `GET /api/formations/categories` (liste distincte)
- 🔴 Extraire `extraireJetonBearer` (dupliqué entre `AuthController` et `UserController`) dans une util

→ Branche `feature/`, tests, PR vers `dev`, demande une review.

---

## FAQ — ce qui coince le plus souvent

| Symptôme | Cause |
|---|---|
| 401 partout | JWT expiré (15 min) ou `JWT_SECRET` qui diffère entre services |
| `WeakKeyException` au démarrage Auth | `JWT_SECRET` < 256 bits — regénère-le |
| Port 8011 déjà pris | Un autre projet tourne dessus, change le port ou stoppe l'autre |
| Coverage « extra steps needed » sur SonarCloud | `coverage.xml` non généré ou fichier exclu via `sonar.exclusions` |
| Job `sonarcloud` échoue avec « Not authorized » | `SONAR_TOKEN` manquant ou expiré dans GitHub Secrets |

---

## Liens utiles

- Dépôt : https://github.com/Andrimirana/Skillhub-BC03
- SonarCloud : https://sonarcloud.io/project/overview?id=skillhub-bc03
- Actions : https://github.com/Andrimirana/Skillhub-BC03/actions
- [README.md](README.md), [PHPDOC.md](PHPDOC.md), [services/auth/JAVADOC.md](services/auth/JAVADOC.md), [openapi.yaml](openapi.yaml)

---

> Dernier mot : **demande**. Une heure perdue à demander t'évite trois jours bloqué·e
> à essayer seul·e. Tout le monde dans l'équipe préfère répondre que voir une personne
> patauger en silence. Bonne arrivée !
