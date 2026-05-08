# SkillHub - Bloc 04 - Cloud, DevOps et Architecture

## Demarrage rapide

Les microservices tournent en natif. Pre-requis : PHP 8.4, Java 17, Node 18+.

```powershell
# Service Auth (Spring Boot)
cd services/auth
$env:APP_MASTER_KEY = "dev-master-key-32-chars-minimum-please"
.\mvnw spring-boot:run

# Service Catalog (Laravel)
cd services/catalog
composer install
php artisan serve --port=8012

# Service Inscription (Laravel)
cd services/inscription
composer install
php artisan serve --port=8013

# Frontend React
cd frontend
npm install
npm run dev
```

Documentation complete :

- [GUIDE_DEMARRAGE_LOCAL.md](GUIDE_DEMARRAGE_LOCAL.md) - guide pas-a-pas pour lancer la stack
- [RAPPORT_ONBOARDING.md](RAPPORT_ONBOARDING.md) - onboarding developpeur junior
- [RAPPORT_QUALITE.md](RAPPORT_QUALITE.md) - rapport SonarCloud avant/apres
- [contributing.md](contributing.md) - guide de contribution

---

## Sommaire

1. Presentation generale
2. Architecture technique
3. Fonctionnalites detaillees
4. Structure du depot
5. Installation et demarrage
6. Configuration et secrets
7. Cycle de vie CI/CD
8. Securite et bonnes pratiques
9. Depannage et FAQ
10. Contribution
11. References et documentation
12. Pages et routes principales (Frontend)
13. Utilisation d'intelligence artificielle
---

## 1. Presentation generale

SkillHub est une plateforme web qui met en relation formateurs et apprenants. Le projet est realise dans le cadre du Bachelor Concepteur Developpeur Web Full Stack (Bloc 04 : Cloud, DevOps et Architecture).

Ce depot regroupe :

- Un frontend React (Vite)
- Un microservice Auth en Spring Boot 3 / Java 17
- Deux microservices Laravel (catalog, inscription)
- Un microservice audio (PHP / AES-256)
- Une execution en local natif (PHP CLI, Maven, npm)
- Un pipeline CI/CD complet


## 2. Architecture technique

### Frontend

- React 19 (Vite)
- Authentification JWT/HMAC, gestion de session, routing protege, UI moderne

### Backend (microservices)

#### Auth - Spring Boot 3 / Java 17 (port 8011)

- Authentification HMAC-SHA256 : le client envoie `{email, nonce, timestamp, hmac}` ou `hmac = HMAC_SHA256(cle=motDePasse, donnees="email:nonce:timestamp")`
- Generation de tokens JWT HS256 (15 min) signes via cle derivee SHA-256
- Endpoint `/api/validate-token` appele par les middlewares Laravel pour verifier chaque requete
- Anti-rejeu : nonce unique + fenetre timestamp 5 min
- Secrets injectes via `APP_MASTER_KEY` et `JWT_SECRET`

#### Catalog - Laravel 13 / PHP 8.4 (port 8012)

- CRUD formations, modules, recherche filtree, attribution formateurs
- Notation des formations (1 a 5) par les apprenants inscrits, avec moyenne et nombre d'avis
- Liste des apprenants inscrits (vue formateur proprietaire)
- Chaque requete protegee verifie le JWT aupres du service Auth

#### Inscription - Laravel 13 / PHP 8.4 (port 8013)

- Gestion des inscriptions apprenants, suivi de progression
- Limite metier : 5 inscriptions actives maximum (HTTP 400 a la 6e)
- Communique avec Auth (validation JWT) et Catalog (verification formation)

#### Audio - PHP / AES-256 (port 8014)

- Stockage chiffre AES-256-GCM des fichiers audio des formations

### Base de donnees

- MySQL : 4 bases metier (auth, catalog, inscription, audio)
- MongoDB : journal d'activite uniquement

### Orchestration et DevOps

- Execution native : `php artisan serve`, `mvnw spring-boot:run`, `npm run dev`
- GitHub Actions : lint, tests, build, analyse SonarCloud, Quality Gate
- SonarCloud : analyse de code, couverture, duplications, bugs

---

## 3. Fonctionnalites detaillees

### Authentification et securite

- Inscription, connexion, deconnexion, changement de mot de passe
- JWT pour l'authentification, HMAC pour la securite des requetes sensibles
- Middleware anti-rejeu (nonce, timestamp, signature)
- Gestion des roles (formateur, apprenant)

### Catalogue de formations

- CRUD formations, modules, recherche filtree
- Attribution des formations aux formateurs
- Statuts, categories, niveaux, duree, prix
- Notation par les apprenants inscrits, moyenne et nombre d'avis sur chaque formation

### Inscriptions

- Inscription a une formation, suivi des apprenants
- Liste des inscrits, validation, annulation
- Limite metier : 5 inscriptions actives maximum

### Vue formateur

- Liste des apprenants inscrits a une formation (id, nom, email, progression, date d'inscription)
- Reservee au formateur proprietaire de la formation

### Frontend

- Dashboard dynamique selon le role
- Routing public/prive, redirections intelligentes
- UI reactive, filtres, recherche, tableaux, sidebar, topbar, dark mode

### Communication inter-services

- Appels HTTP entre microservices via `localhost:<port>` (Auth 8011, Catalog 8012, Inscription 8013)
- Aucun code partage, chaque service est independant

### Qualite et tests

#### Auth - Spring Boot (JUnit 5 + JaCoCo)

`SkillhubControllerTest.java` couvre tous les endpoints (register, login, profil, change-password, validate-token).

`MasterKeyAbsentTest.java` verifie le refus de demarrage sans `APP_MASTER_KEY`.

#### Catalog - Laravel (PHPUnit + PCov)

`FormationControllerTest.php` couvre la liste publique, l'increment des vues, le CRUD formateur, les controles de role et de propriete.

`ModuleControllerTest.php` couvre le CRUD des modules.

`RatingControllerTest.php` couvre la notation : 201 OK, 400 doublon, 400 hors intervalle, 403 non inscrit, 401 sans token, plus la moyenne et le nombre d'avis.

`FormationApprenantsTest.php` couvre la liste apprenants : 200 propriete, 403 non proprietaire, 200 vide, 401 sans token, 404 formation inconnue.

`MongoActivityLoggerTest.php` couvre les logs MongoDB.

#### Inscription - Laravel (PHPUnit + PCov)

`EnrollmentControllerTest.php` couvre l'inscription et la desinscription, le controle de role, la limite a 5 inscriptions actives.

#### Commandes locales

```powershell
# Auth (Spring Boot)
cd services/auth
$env:APP_MASTER_KEY = "dev-master-key-32-chars-minimum-please"
.\mvnw verify

# Catalog (SQLite pour les tests)
cd services/catalog
$env:DB_CONNECTION = "sqlite"
$env:DB_DATABASE = ":memory:"
php artisan test

# Inscription (SQLite pour les tests)
cd services/inscription
$env:DB_CONNECTION = "sqlite"
$env:DB_DATABASE = ":memory:"
php artisan test
```

- Linting JS/PHP, ESLint cote frontend
- Analyse SonarCloud + Quality Gate bloquante a chaque push

---

## 4. Structure du depot

```
/frontend                # React.js (Vite)
/services/auth           # Microservice Authentification (Spring Boot / Java 17)
/services/catalog        # Microservice Catalogue (Laravel)
/services/inscription    # Microservice Inscriptions (Laravel)
/services/audio          # Microservice Audio (PHP / AES-256)
/contributing.md         # Guide de contribution
/sonar-project.properties# Configuration SonarCloud
```

Chaque microservice PHP contient :

- `app/`, `routes/`, `database/`, `config/`, `tests/`, `.env`, `composer.json`.

---

## 5. Installation et demarrage

### Prerequis

- PHP 8.4 + Composer 2.6+
- Java JDK 17 (Temurin)
- Node.js 18+
- MySQL 8 (MongoDB 7 pour les logs d'activite)

### Lancement

```powershell
# Auth (Spring Boot)
cd services/auth
$env:APP_MASTER_KEY = "dev-master-key-32-chars-minimum-please"
.\mvnw spring-boot:run

# Catalog (Laravel)
cd services/catalog
composer install
php artisan serve --port=8012

# Inscription (Laravel)
cd services/inscription
composer install
php artisan serve --port=8013
```

Frontend sur 5183, Auth sur 8011, Catalog sur 8012, Inscription sur 8013.

### Initialisation des bases

```powershell
cd services/catalog
php artisan migrate:fresh --seed

cd services/inscription
php artisan migrate:fresh

# Auth : la base MySQL skillhub_auth est geree par JPA (ddl-auto=update)
```

### Frontend en mode dev

```powershell
cd frontend
npm install
npm run dev
```

Voir [GUIDE_DEMARRAGE_LOCAL.md](GUIDE_DEMARRAGE_LOCAL.md) pour le detail et le depannage.

---

## 6. Configuration et secrets

Chaque microservice a son propre `.env` (voir `.env.example`).

Variables importantes :

- `APP_KEY`, `APP_MASTER_KEY`, `DB_*`, `JWT_SECRET`, `AUTH_SERVICE_URL`, `INSCRIPTION_SERVICE_URL`, `CATALOG_SERVICE_URL`.
- Le frontend utilise `VITE_API_URL`.

Ne jamais versionner de secrets en clair.

---

## 7. Cycle de vie CI/CD

### Pipeline GitHub Actions

- Lint, tests, build, analyse SonarCloud a chaque push/PR
- Declenche aussi sur les branches `feature/**` et `fix/**`
- Quality Gate bloquante

### SonarCloud

- Analyse code, duplications, bugs, couverture, vulnerabilites, hotspots
- Organisation et projectKey definis dans `sonar-project.properties`

---

## 8. Securite et bonnes pratiques

- Authentification JWT, signature HMAC, anti-rejeu
- Separation stricte des responsabilites, aucun code partage
- Variables d'environnement pour tous les secrets
- Tests unitaires obligatoires
- Conventional Commits

---

## 9. Depannage et FAQ

### Problemes courants

- Erreur 401 : verifier le token, la session, la coherence des cles JWT/HMAC entre services
- Connexion refusee entre services : verifier les ports (8011, 8012, 8013)
- Pipeline CI/CD echouee : verifier la config SonarCloud et la presence des tests
- Donnees non affichees : verifier le role, la session, la reponse API

### Commandes utiles

```powershell
# Relancer un service Laravel
cd services/catalog
php artisan serve --port=8012

# Relancer Auth Spring Boot
cd services/auth
.\mvnw spring-boot:run

# Vider le cache Laravel
php artisan cache:clear
php artisan config:clear
```

---

## 10. Contribution

- Fork, branche thematique, PR vers `dev`, review
- Respecter `contributing.md`
- Conventional Commits (`feat:`, `fix:`, `chore:`...)
- Tests et lint obligatoires avant merge

---

## 11. References et documentation

- [RAPPORT_ONBOARDING.md](RAPPORT_ONBOARDING.md) - onboarding junior
- [RAPPORT_QUALITE.md](RAPPORT_QUALITE.md) - rapport qualite SonarCloud
- [GUIDE_DEMARRAGE_LOCAL.md](GUIDE_DEMARRAGE_LOCAL.md) - guide demarrage
- [contributing.md](contributing.md) - guide de contribution
- [openapi.yaml](openapi.yaml) - contrat des APIs
- JavaDoc HTML : `services/auth/target/site/apidocs/index.html` (genere par `mvn javadoc:javadoc`)

---

## 12. Pages et routes principales (Frontend)

### Pages React

- `/` (Accueil) : page publique
- `/formations` : liste filtrable des formations
- `/formation/:id` : detail d'une formation
- `/connexion` : page de connexion
- `/inscription` : page de creation de compte
- `/dashboard/formateur` : tableau de bord formateur
- `/dashboard/apprenant` : tableau de bord apprenant
- `/creer-atelier` : creation d'une formation
- `/modifier-formation/:idFormation` : modification d'une formation
- `/apprendre/:id` : suivi d'une formation
- `/mes-ateliers` : liste des ateliers de l'utilisateur connecte

### Routing (React Router)

| Route                     | Acces       | Composant          | Description                                          |
| ------------------------- | ----------- | ------------------ | ---------------------------------------------------- |
| `/`                       | Public      | Accueil            | Page d'accueil                                       |
| `/formations`             | Public      | Formations         | Catalogue filtrable                                  |
| `/formation/:id`          | Public      | DetailFormation    | Detail formation, bouton inscription                 |
| `/connexion`              | Invite      | Connexion          | Authentification                                     |
| `/inscription`            | Invite      | Inscription        | Creation de compte                                   |
| `/dashboard/formateur`    | Formateur   | Formateur          | Dashboard formateur                                  |
| `/dashboard/apprenant`    | Apprenant   | Apprenant          | Dashboard apprenant                                  |
| `/creer-atelier`          | Formateur   | CreerAtelier       | Creation d'une formation                             |
| `/modifier-formation/:id` | Formateur   | ModifierFormation  | Modification d'une formation                         |
| `/apprendre/:id`          | Apprenant   | SuiviFormation     | Suivi d'une formation                                |
| `/mes-ateliers`           | Authentifie | Ateliers           | Liste des ateliers                                   |
| `/dashboard`              | Authentifie | RedirectionAccueil | Redirige selon le role                               |
| `*`                       | Public      | Redirect           | Redirection vers l'accueil pour route inconnue       |

Acces controles par RouteProtegee et RouteInvite selon role et session.

### 13. Utilisation d'intelligence artificielle
- ChatGPT: 
