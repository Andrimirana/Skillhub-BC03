# Onboarding SkillHub

**Pour qui** : la nouvelle recrue · **Date** : mai 2026

> Bienvenue. Ce doc te rend autonome : tu clones, tu lances la stack, tu fais ta premiere PR.
> Si quelque chose coince, demande.

---

## Le projet en 30 secondes

**SkillHub** est une plateforme web ou des **formateurs** publient des formations
et ou des **apprenants** s'y inscrivent (max 5 actives en meme temps). Projet
Bachelor (Bloc 04 - Cloud, DevOps et Architecture) pour pratiquer l'archi
microservices et la qualite industrielle.

---

## Architecture

Quatre microservices et un frontend, en local natif :

```
        +--------------+
        |  Frontend    |  React 19 / Vite        :5183
        +------+-------+
               | Bearer JWT
   +-----------+------------+-------------+
   v           v            v             v
+------+  +---------+  +------------+  +------+
| Auth |  | Catalog |  |Inscription |  |Audio |
|Spring|  | Laravel |  |  Laravel   |  | PHP  |
|:8011 |  |  :8012  |  |   :8013    |  |:8014 |
+---+--+  +----+----+  +-----+------+  +--+---+
    +----------+-------------+------------+
                       |
                  MySQL + MongoDB (logs)
```

| Service       | Role                                                     | Tech         |
| ------------- | -------------------------------------------------------- | ------------ |
| **auth**      | Emet les JWT, valide les tokens (HMAC-SHA256 + JWT HS256) | Java 17      |
| **catalog**   | Formations, modules, notation, vue formateur             | PHP 8.4      |
| **inscription** | Inscriptions apprenants (limite 5)                     | PHP 8.4      |
| **audio**     | Stockage chiffre AES-256 des fichiers audio              | PHP 8.4      |

Seul **Auth** emet des JWT. Catalog et Inscription valident chaque requete via
`POST /api/validate-token` sur Auth. Pas de session partagee.

---

## Le flux d'auth

Le mot de passe ne circule jamais. A la connexion, le client calcule :

```
hmac = HMAC_SHA256(cle = motDePasse, donnees = "email:nonce:timestamp")
```

et envoie `{email, nonce, timestamp, hmac}`. Le serveur reconstitue le HMAC avec
le mot de passe stocke (chiffre AES-256-GCM) et compare. Si OK, JWT signe HS256
valable 15 min, a placer dans `Authorization: Bearer <jwt>`.

Anti-rejeu : nonce unique en base + fenetre timestamp ±5 min.

---

## Pre-requis

- **Java JDK 17** (Temurin)
- **PHP 8.4** + Composer 2.6+
- **Node.js 18+**
- **Git**, **MySQL 8**, **MongoDB 7** (logs)

---

## Lancer le projet

```powershell
# Copier les .env
Copy-Item services/catalog/.env.example     services/catalog/.env
Copy-Item services/inscription/.env.example services/inscription/.env

# Auth
cd services/auth
$env:APP_MASTER_KEY = "dev-master-key-32-chars-minimum-please"
.\mvnw spring-boot:run                     # port 8011

# Catalog (autre terminal)
cd services/catalog
composer install
php artisan migrate:fresh --seed
php artisan serve --port=8012

# Inscription (autre terminal)
cd services/inscription
composer install
php artisan migrate:fresh
php artisan serve --port=8013

# Frontend (autre terminal)
cd frontend
npm install
npm run dev                                 # port 5183
```

Verifier : http://localhost:8011/api/health  ->  `{"status":"UP"}`.

Genere un `JWT_SECRET` >= 256 bits :
`[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`

Ne committe jamais un `.env`.

Voir [GUIDE_DEMARRAGE_LOCAL.md](GUIDE_DEMARRAGE_LOCAL.md) pour le detail et le depannage.

---

## Structure du depot

```
frontend/                     React 19 (Vite)
services/auth/                Spring Boot 3 / Java 17
services/catalog/             Laravel 13 - formations, modules, notation
services/inscription/         Laravel 13 - inscriptions (limite 5)
services/audio/               PHP - fichiers audio chiffres
.github/workflows/            Pipeline CI (sonarcloud.yml)
sonar-project.properties      Configuration SonarCloud
openapi.yaml                  Contrat OpenAPI 3
```

---

## Lancer les tests

```powershell
# Catalog
cd services/catalog
$env:DB_CONNECTION = "sqlite"
$env:DB_DATABASE = ":memory:"
php artisan test

# Inscription
cd services/inscription
$env:DB_CONNECTION = "sqlite"
$env:DB_DATABASE = ":memory:"
php artisan test

# Auth
cd services/auth
$env:APP_MASTER_KEY = "dev-master-key-32-chars-minimum-please"
.\mvnw verify
```

Couverture : `--coverage-clover coverage.xml` cote PHP, JaCoCo cote Java
(`services/auth/target/site/jacoco/index.html`).

---

## Workflow Git

- `main` : production (protegee)
- `dev` : integration (cible des PR)
- `feature/<sujet>` ou `fix/<sujet>` : branche de travail

Conventional Commits obligatoires :

```
feat(inscription): limiter a 5 inscriptions actives
fix(catalog): corriger 500 sur recherche Unicode
test(auth): couvrir le timestamp expire
```

Cycle d'une PR : branche -> code et tests -> push -> PR vers `dev` -> CI verte -> review -> merge.
Pas de `--no-verify` sur les hooks sans demander.

---

## Quality Gate SonarCloud

Sur chaque PR, le **New Code** (ton diff) doit respecter :

| Condition          | Seuil    |
| ------------------ | -------- |
| Coverage           | >= 80 %  |
| Duplications       | <= 3 %   |
| Ratings (Security/Reliability/Maintainability) | A |
| Hotspots reviewed  | 100 %    |

Si la QG echoue : ajoute des tests, factorise les duplications, corrige le smell.
En dernier recours : `// NOSONAR <regle> - <justification claire>`.

---

## Points d'attention CI

- **`SONAR_TOKEN`** doit exister dans GitHub Secrets sinon le job `sonarcloud` echoue.
- **`sonar-project.properties`** : tout sur une seule ligne pour `sonar.exclusions`,
  un saut de ligne casse le parser Java Properties.
- **Seeders/factories Laravel exclus** de l'analyse (donnees de demo).
- **`sonar.javascript.exclusions=**/*`** : le frontend n'est pas analyse.

---

## Securite - 3 regles d'or

1. Aucun secret en dur. Tout passe par `.env`.
2. Jamais de mot de passe en clair. Stocke AES-256-GCM, transmis en HMAC.
3. Toute requete sur Catalog/Inscription a un JWT valide (sauf `GET /formations` public).
   Ne contourne jamais `ValidateServiceToken`.

---

## Ta premiere mission (sur 2 jours)

**Jour 1 - comprendre** : lance la stack, cree un compte apprenant, inscris-toi
a 5 formations, tente la 6e. Lis `AuthService.java` et `FormationController.php`.
Regarde le rapport JaCoCo.

**Jour 2 - contribuer**. Choisis une mission :

- Enrichir `openapi.yaml` avec un exemple sur `/api/auth/login`
- Ajouter un test "timestamp expire" dans Auth
- Endpoint `GET /api/formations/categories` (liste distincte)
- Extraire `extraireJetonBearer` (duplique entre AuthController et UserController) dans une util

Branche `feature/...`, tests, PR vers `dev`, demande une review.

---

## FAQ - ce qui coince le plus souvent

| Symptome | Cause |
|---|---|
| 401 partout | JWT expire (15 min) ou `JWT_SECRET` different entre services |
| `WeakKeyException` au demarrage Auth | `JWT_SECRET` < 256 bits, regenere-le |
| Port 8011 deja pris | Autre projet dessus, change le port ou stoppe l'autre |
| Coverage "extra steps needed" sur SonarCloud | `coverage.xml` non genere ou exclu via `sonar.exclusions` |
| Job `sonarcloud` echoue "Not authorized" | `SONAR_TOKEN` manquant ou expire dans GitHub Secrets |

---

## Liens utiles

- [README.md](README.md), [GUIDE_DEMARRAGE_LOCAL.md](GUIDE_DEMARRAGE_LOCAL.md), [RAPPORT_QUALITE.md](RAPPORT_QUALITE.md)
- [contributing.md](contributing.md), [openapi.yaml](openapi.yaml)
- JavaDoc HTML : `services/auth/target/site/apidocs/index.html` (genere via `mvn javadoc:javadoc`)

---

> Dernier mot : demande. Une heure perdue a demander t'evite trois jours bloque
> a essayer seul. Bonne arrivee.
