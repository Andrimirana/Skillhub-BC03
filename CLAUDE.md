# CLAUDE.md — SkillHub BC03

## Mission principale

**Corriger tous les bugs existants.** À chaque intervention, identifier et corriger les anomalies rencontrées : bugs fonctionnels, erreurs de configuration, tests cassés, pipeline CI/CD défaillant, problèmes SonarCloud. Ne pas laisser passer un bug connu. A chaque changement : declenche la pipeline github actions

---

## Vue d'ensemble du projet

**SkillHub** est une plateforme e-learning en microservices, développée dans le cadre du Bloc 03 (industrialisation). Elle permet la gestion de formations, l'inscription des apprenants et l'authentification sécurisée.

- Dépôt GitHub : organisation `andrimirana`, projet `Andrimirana_skillhub-groupe-BC03`
- Branche principale : `main` (production), intégration : `dev`
- Convention de commits : Conventional Commits (`feat`, `fix`, `ci`, `docker`, `docs`, `chore`)

## Supprime le rule.md et claude.md dans le repo github

## Nom des variables doivent etre en français et utilise la methode KISS
---

## Architecture microservices

| Service | Technologie | Port hôte | Port interne | Base de données |
|---|---|---|---|---|
| Frontend | React 19 + Vite (Rolldown) | 5183 | 80 | — |
| Auth API | Spring Boot 3.2.5 / Java 17 | 8011 | 8080 | MySQL `skillhub_auth` |
| Catalog API | Laravel 13 / PHP 8.3 | 8012 | 8000 | MySQL `skillhub_catalog` |
| Inscription API | Laravel 13 / PHP 8.3 | 8013 | 8000 | MySQL `skillhub_enrollment` |
| MySQL | MySQL 8.0 | 3307 | 3306 | Partagé |
| MongoDB | MongoDB 7.0 | 27018 | 27017 | `skillhub_logs` (logs activité) |

Réseau Docker : `skillhub_network` (bridge). Communication inter-services via DNS interne (ex. `http://auth_api:8080`).

---

## Stack technique par service

### Frontend (`frontend/`)
- React 19.2, Vite, React Router 7.13, Axios, Crypto-JS
- Tests : Vitest 4.1 + coverage-v8
- Lint : ESLint 9.39
- Variables d'env via build args Docker : `VITE_AUTH_URL`, `VITE_CATALOG_URL`, `VITE_INSCRIPTION_URL`, `VITE_APP_MASTER_KEY`

### Auth (`services/auth/`)
- Spring Boot 3.2.5, Java 17, Maven (mvnw)
- Sécurité : JWT, HMAC-SHA256, AES-256-GCM, middleware anti-replay
- JPA/Hibernate sur MySQL, génération de schéma automatique (`ddl-auto=update`)
- Couverture : JaCoCo → `target/site/jacoco/jacoco.xml`
- Clé maître injectée via `APP_MASTER_KEY` (jamais en dur dans `application.properties`)

### Catalog (`services/catalog/`)
- Laravel 13, PHP 8.3, Composer
- CRUD formations, modules, recherche, affectation formateurs
- Tests PHPUnit sur SQLite en CI, couverture Clover → `coverage.xml`
- Seeders : `FormationSeeder` avec données de démo

### Inscription (`services/inscription/`)
- Laravel 13, PHP 8.3, Composer
- Gestion inscriptions, suivi progression apprenants
- Communique avec Auth (JWT) et Catalog
- Tests PHPUnit sur SQLite en CI, couverture Clover → `coverage.xml`

---

## Pipeline CI/CD (`.github/workflows/sonarcloud.yml`)

```
Checkout (recuperation du code source)
Install: installation des dependances laravel et spring boot
puis
lint-frontend ──┐
lint-php ────────┤──► tests-auth ──────┐
                 │──► tests-catalog ───┤──► sonarcloud ──► docker-push (main only)
                 ──► tests-limite (limite 5 inscriptions)
                 └──► tests-inscription┘──► docker-build
```

- **Déclencheurs** : push sur `main`/`dev`, PR vers `main`/`dev`
- **SonarCloud** et **docker-build/push** : push uniquement (pas les PRs)
- **docker-push** → GHCR : uniquement sur `main`

---

## Problèmes SonarCloud — À CORRIGER

SonarCloud **ne fonctionne pas** en l'état. Voici les causes identifiées :

### 1. Secret `SONAR_TOKEN` manquant ou invalide
Le secret `SONAR_TOKEN` doit être configuré dans **Settings → Secrets → Actions** du dépôt GitHub. Sans lui, le job `sonarcloud` échoue silencieusement ou avec `403 Forbidden`.

### 2. `docker-push` bloqué par l'échec SonarCloud
Le job `docker-push` dépend de `sonarcloud` (ligne `needs: [docker-build, sonarcloud]`). Si SonarCloud échoue, le push GHCR est bloqué aussi, même si les images Docker sont valides.

### 3. Lint frontend masqué (`|| true`)
```yaml
run: npm run lint -- --max-warnings=0 || true
```
Le `|| true` rend le lint toujours vert même en cas d'erreurs. Ce job ne protège rien.

### 4. Lint PHP inversé
```bash
find services/catalog/app -name "*.php" | xargs php -l 2>&1 | grep -E "^(Parse|Fatal) error" && exit 1 || true
```
La logique est correcte mais le `|| true` final annule l'`exit 1` si `grep` ne trouve rien mais `php -l` a échoué autrement. À revoir.

### 5. `sonar-project.properties` — binaires Java non garantis
Le job `sonarcloud` compile le service Auth (`mvnw compile`) mais ne vérifie pas que les binaires sont bien présents avant de lancer l'analyse. Si la compilation échoue silencieusement, SonarCloud analyse sans bytecode et rate la couverture Java.

### 6. SonarCloud ne se déclenche pas sur les PRs
`if: github.event_name == 'push'` exclut les analyses sur les Pull Requests. Les PRs vers `dev` ne sont jamais analysées avant merge.

---

## Problèmes connus — À CORRIGER

### Auth Service
- `application.properties` pointe sur `localhost:3306/auth` en dur (base incorrecte, doit être `skillhub_auth`). En Docker, l'URL est surchargée par env var, mais en dev local cela casse.
- `spring.jpa.show-sql=true` actif en production — à désactiver pour les envs non-dev.

### Docker Compose
- Le healthcheck MySQL utilise `-p${MYSQL_ROOT_PASSWORD}` sans espace avant le `$`, ce qui peut échouer selon le shell.
- Le healthcheck Catalog/Inscription (`php artisan --version`) ne vérifie pas la disponibilité HTTP réelle du service.
- Le service `mongodb` n'est pas déclaré comme `depends_on` dans les services qui l'utilisent (Catalog/Inscription), risque de démarrage avant MongoDB.

### Frontend
- `frontend/.env` présent dans le dépôt (listé dans `git status` modifié) — vérifier qu'il ne contient pas de secrets.
- Les URLs de services dans `docker-compose.yml` pointent sur `127.0.0.1` (build args), mais depuis un conteneur les services ne sont pas accessibles via `127.0.0.1` — doit utiliser le DNS interne Docker.

### Catalog / Inscription
- `services/catalog/database/seeders/DatabaseSeeder.php` modifié et `FormationSeeder.php` non suivi — s'assurer que les seeders sont cohérents et committés.
- Pas de vérification du token JWT côté Catalog avant de servir les routes protégées (à auditer).

---

## Workflow Git

```
main (production — aucun commit direct)
 └── dev (intégration)
      └── feature/<nom> ou fix/<nom> ou hotfix/<nom>
```

1. Créer une branche depuis `dev`
2. Développer, committer (Conventional Commits)
3. Ouvrir une PR vers `dev`
4. Review + merge
5. Merge `dev` → `main` pour déploiement

---

## Commandes utiles

```bash
# Lancer tout le stack Docker
docker compose up --build

# Tests Auth (local)
cd services/auth && ./mvnw verify

# Tests Catalog (local, SQLite)
cd services/catalog
composer install
cp .env.example .env && php artisan key:generate
touch database/database.sqlite
DB_CONNECTION=sqlite DB_DATABASE=database/database.sqlite php artisan test --coverage-clover coverage.xml

# Tests Inscription (local, SQLite)
cd services/inscription
composer install
cp .env.example .env && php artisan key:generate
touch database/database.sqlite
DB_CONNECTION=sqlite DB_DATABASE=database/database.sqlite php artisan test --coverage-clover coverage.xml

# Lint Frontend
cd frontend && npm run lint

# Seeders Catalog
cd services/catalog && php artisan db:seed
```

---

## Secrets GitHub requis

| Secret | Usage |
|---|---|
| `SONAR_TOKEN` | Authentification SonarCloud — **obligatoire** |
| `GITHUB_TOKEN` | Automatique (GitHub Actions) |
| `MYSQL_USER` | Optionnel (fallback `skillhub_user`) |
| `MYSQL_PASSWORD` | Optionnel (fallback `skillhub_pass`) |
| `APP_MASTER_KEY` | Clé HMAC Auth (base64, 32 chars min) |

---

## SonarCloud — Configuration

- Organisation : `andrimirana`
- Clé projet : `Andrimirana_skillhub-groupe-BC03`
- Sources Java : `services/auth/src/main/java`
- Sources PHP : `services/catalog/app`, `services/inscription/app`
- Couverture Java : `services/auth/target/site/jacoco/jacoco.xml`
- Couverture PHP : `services/catalog/coverage.xml`, `services/inscription/coverage.xml`

---

## Directives 

- **Toujours corriger les bugs trouvés**, même s'ils ne sont pas directement liés à la tâche demandée.
- Respecter la convention Conventional Commits pour les messages de commit.
- Ne jamais committer directement sur `main`. Passer par une branche `fix/` ou `feature/`.
- Ne pas exposer de secrets dans le code ou les fichiers de config versionnés.
- Préférer l'édition de fichiers existants à la création de nouveaux.
- Vérifier la cohérence entre `docker-compose.yml` et les `application.properties`/`.env` de chaque service.
- Quand un fix touche le pipeline CI/CD, tester la logique bash localement avant de committer.


## Directives 
- Creer une branche feature/lilite-inscription à partir de dev
- Modifier le controller ou le service s'inscritpion laravel pour verifier le nombre d'inscriptions actives de l'apprenant avant d'enregistrer 
- retourner une reponse hhtp 400  avec un message explicite si la limite est atteinte de 5 est atteinte 
- ecrire le test unitaire ou fonctionnel: tentative d'inscription d'un apprenant deja inscrit à 5 formations donne http 400
- commiter avec un message Conventional commits explicite
- ouvrir une Pull request vers dev avec descri du changement

## Directives Spring boot (important: pour ceci, pour les modifications que tu vas faire, simule le nom du commit comme si on venait d'intégrer spring boot en ce moment meme, mais les changements restent les changements, mais les noms des commits, simule comme si on venait d'integrer spring boot là maintenant)
- S'assurer que l'endpoint authentification psring boot retourne un token JWT valide
- configurer la master key et les parametres de securité dans le fichier sonar-project.properties
- adapter le middleware d'authentification laravel pour appeler le microservice docker  authentification spring boot via api rest
- trasmettre le token jwt recu du microservice dans les requetes subséquentes
-proteger au minimum une route laravel avec ce systeme d'authentification

## Docker
- creer un dockerfile poue le projet skillhub laravel
- verifier ou creer le dockerfile pour le microservice spring boot
- creer un docker-compose.yml orchestrant les deux services (laravel et spring boot)
- verifier que docker compose up --build demarre la stack complete sans erreur

## README
- ajout de explication systheme authentification sso : comment le microservice spring boot est intégré comment le jwt estgéré
- description de la regle metier de la limite inscription à 5
- explication des outils sonarcloud github actions, docker
- analyse des balises qualités sonarcloud après nouvelle feature et proposer ameliorations à apporter 