# SkillHub - Bloc 03 - Cloud, DevOps et Architecture

## Demarrage rapide

Les microservices tournent en natif sur la machine de developpement. Il faut PHP 8.4, Java 17 et Node 18+.

```powershell
# Service Auth (Spring Boot)
cd services/auth
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

**Documentation complete :**

- [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) - vue d'ensemble du projet
- [RAPPORT_ONBOARDING.md](RAPPORT_ONBOARDING.md) - onboarding developpeur junior
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

---

## 1. Presentation generale

SkillHub est une plateforme web collaborative de mise en relation entre formateurs et apprenants, developpee dans le cadre du Bachelor Concepteur Developpeur Web Full Stack (Bloc 03 : Cloud, DevOps et Architecture, Promotion 2025/2026).

Ce depot regroupe :

- Un frontend React (Vite)
- Un microservice Auth en Spring Boot 3 / Java 17
- Deux microservices Laravel (catalog, inscription)
- Un microservice audio (PHP / AES-256)
- Une execution en local natif (PHP CLI, Maven, npm)
- Un pipeline CI/CD complet

Objectifs Bloc 03 : industrialisation, automatisation, qualite logicielle.

---

## 2. Architecture technique

### Frontend

- React 19 (Vite)
- Authentification JWT/HMAC, gestion de session, routing protege, UI moderne

### Backend (microservices)

#### Auth - Spring Boot 3 / Java 17 (port 8011)

- Authentification par HMAC-SHA256 : le client envoie `{email, nonce, timestamp, hmac}` ou `hmac = HMAC_SHA256(cle=motDePasse, donnees="email:nonce:timestamp")`
- Generation de tokens JWT HS256 (expiration 15 min) signes avec une cle derivee par SHA-256
- Endpoint `/api/validate-token` appele par les middlewares Laravel pour verifier chaque requete
- Protection anti-rejeu : nonce unique + fenetre timestamp de 5 minutes
- Injecte via `APP_MASTER_KEY` et `JWT_SECRET` (jamais en dur)

#### Catalog - Laravel 13 / PHP 8.4 (port 8012)

- CRUD formations, modules, recherche filtree, attribution formateurs
- Chaque requete protegee verifie le JWT aupres du service Auth avant de repondre

#### Inscription - Laravel 13 / PHP 8.4 (port 8013)

- Gestion des inscriptions apprenants, suivi de progression
- Limite metier : 5 inscriptions actives maximum par apprenant (HTTP 400 si depasse)
- Communique avec Auth (validation JWT) et Catalog (verification formation existante)

#### Audio - PHP / AES-256 (port 8014)

- Stockage chiffre AES-256-GCM des fichiers audio des formations

### Base de donnees

- MySQL : 4 bases metier (auth, catalog, inscription, audio)
- MongoDB : journal d'activite uniquement (audit trail)

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
- Gestion des statuts, categories, niveaux, duree, prix

### Inscriptions

- Inscription a une formation, suivi des apprenants
- Gestion des listes d'inscrits, validation, annulation
- Limite metier : 5 inscriptions actives maximum

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

`MasterKeyAbsentTest.java` verifie que l'application refuse de demarrer sans `APP_MASTER_KEY`.

#### Catalog - Laravel (PHPUnit + PCov)

`FormationControllerTest.php` couvre la liste publique, l'increment des vues, la creation/modification/suppression par formateur, les controles de role (apprenant interdit) et de propriete (formation d'autrui interdite).

`ModuleControllerTest.php` couvre le CRUD des modules avec les memes controles.

`MongoActivityLoggerTest.php` couvre les logs d'activite MongoDB.

#### Inscription - Laravel (PHPUnit + PCov)

`EnrollmentControllerTest.php` couvre l'inscription/desinscription apprenant, le controle des roles, la formation introuvable.

Test dedie `tests-limite` dans le pipeline CI : verifie qu'un apprenant deja inscrit a 5 formations recoit HTTP 400 a la 6e tentative.

#### Commandes locales

```powershell
# Auth (Spring Boot)
cd services/auth
.\mvnw verify

# Catalog (SQLite pour les tests)
cd services/catalog
composer install
Copy-Item .env.example .env
php artisan key:generate
New-Item database/database.sqlite -ItemType File -Force | Out-Null
$env:DB_CONNECTION="sqlite"
$env:DB_DATABASE="database/database.sqlite"
php artisan test --coverage-clover coverage.xml

# Inscription (SQLite pour les tests)
cd services/inscription
composer install
Copy-Item .env.example .env
php artisan key:generate
New-Item database/database.sqlite -ItemType File -Force | Out-Null
$env:DB_CONNECTION="sqlite"
$env:DB_DATABASE="database/database.sqlite"
php artisan test --coverage-clover coverage.xml
```

- Linting JS/PHP, ESLint cote frontend
- Analyse SonarCloud + Quality Gate bloquante a chaque push

---

## 4. Structure du depot

```
/frontend                # Application React.js (Vite)
/services/auth           # Microservice Authentification (Spring Boot / Java 17)
/services/catalog        # Microservice Catalogue (Laravel)
/services/inscription    # Microservice Inscriptions (Laravel)
/services/audio          # Microservice Audio (PHP / AES-256)
/contributing.md         # Guide de contribution
/sonar-project.properties# Configuration SonarCloud
```

Chaque microservice PHP contient :

- `app/`, `routes/`, `database/`, `config/`, `tests/`, `.env`, `composer.json`, etc.

---

## 5. Installation et demarrage

### Prerequis

- PHP 8.4 + Composer 2.6+
- Java JDK 17 (Temurin)
- Node.js 18+
- MySQL 8 (ou SQLite pour les tests)
- MongoDB 7 ( pour les logs d'activite)

### Clonage et lancement

```powershell
git clone https://github.com/Andrimirana/Skillhub-BC03.git
cd Skillhub-BC03

# Auth (Spring Boot)
cd services/auth
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

Le frontend sera accessible sur le port 5183, les microservices sur 8011 (Auth Spring Boot), 8012 (Catalog), 8013 (Inscription).

### Initialisation des bases de donnees

```powershell
cd services/catalog
php artisan migrate:fresh --seed

cd services/inscription
php artisan migrate:fresh --seed

# Auth (Spring Boot) : la base est geree par JPA/Hibernate (ddl-auto=update)
```

### Lancer le frontend en mode dev

```powershell
cd frontend
npm install
npm run dev
```

---

## 6. Configuration et secrets

Chaque microservice possede son propre fichier `.env` (voir `.env.example` dans chaque dossier).

Variables importantes :

- `APP_KEY`, `APP_MASTER_KEY` (HMAC), `DB_*`, `JWT_SECRET`, etc.
- Le frontend utilise `VITE_API_URL` pour cibler l'API.

Ne jamais versionner les secrets en clair.

---

## 7. Cycle de vie CI/CD

### Pipeline GitHub Actions

- Lint, tests unitaires, build, analyse SonarCloud a chaque push/PR
- Quality Gate bloquante

### SonarCloud

- Analyse de code, duplications, bugs, couverture
- Organisation et projectKey definis dans `sonar-project.properties`

---

## 8. Securite et bonnes pratiques

- Authentification JWT, signature HMAC, anti-rejeu
- Separation stricte des responsabilites (aucun code monolithe)
- Variables d'environnement pour tous les secrets
- Tests unitaires obligatoires
- Convention de nommage Git (Conventional Commits)

---

## 9. Depannage et FAQ

### Problemes courants

- Erreur 401 : verifier le token, la session, la synchro des cles JWT/HMAC entre services
- Connexion refusee entre services : verifier que chaque microservice tourne bien sur son port (Auth 8011, Catalog 8012, Inscription 8013)
- Pipeline CI/CD echouee : verifier la config SonarCloud, la presence des tests
- Donnees non affichees : verifier le role, la session, les reponses API

### Commandes utiles

```powershell
# Relancer un service Laravel apres modifications
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
- Respecter le guide `contributing.md`
- Convention de commit : Conventional Commits (`feat:`, `fix:`, `chore:`, etc.)
- Tests et lint obligatoires avant merge

---

## 11. References et documentation

- [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) - reference projet
- [RAPPORT_ONBOARDING.md](RAPPORT_ONBOARDING.md) - onboarding junior
- [contributing.md](contributing.md) - guide de contribution
- [openapi.yaml](openapi.yaml) - contrat des APIs
- SonarCloud, GitHub Actions

---

## 12. Pages et routes principales (Frontend)

### Pages React

- `/` (Accueil) : page publique, presentation de la plateforme
- `/formations` : liste filtrable de toutes les formations disponibles
- `/formation/:id` : detail d'une formation
- `/connexion` : page de connexion
- `/inscription` : page de creation de compte
- `/dashboard/formateur` : tableau de bord formateur
- `/dashboard/apprenant` : tableau de bord apprenant
- `/creer-atelier` : creation d'une nouvelle formation
- `/modifier-formation/:idFormation` : modification d'une formation
- `/apprendre/:id` : suivi d'une formation par l'apprenant
- `/mes-ateliers` : liste des ateliers de l'utilisateur connecte

### Routing (React Router)

| Route                     | Acces       | Composant          | Description                                          |
| ------------------------- | ----------- | ------------------ | ---------------------------------------------------- |
| `/`                       | Public      | Accueil            | Page d'accueil, presentation                         |
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

Les acces sont controles par des guards (RouteProtegee, RouteInvite) selon le role et la session.

13. Utilisation d'intelligence artificielle
- ChatGPT: 
