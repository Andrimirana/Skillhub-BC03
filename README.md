# SkillHub – Bloc 03 – Cloud, DevOps et Architecture

## 🚀 Démarrage rapide

**Nouveau !** Le projet a été corrigé et est maintenant opérationnel.

```powershell
# 1. Vérifier que Docker est démarré
docker version

# 2. Lancer tous les services
docker-compose up -d

# 3. Vérifier que tout fonctionne
docker-compose ps
```

**📚 Documentation complète :**

- **[GUIDE_CORRECTION.md](GUIDE_CORRECTION.md)** - Guide de démarrage et dépannage
- **[REFERENCE_TECHNIQUE.md](REFERENCE_TECHNIQUE.md)** - Documentation technique complète
- **[contributing.md](contributing.md)** - Guide de contribution

**✅ Problèmes corrigés :**

- ✅ Frontend manquant (commenté dans docker-compose)
- ✅ Healthcheck du service Auth (utilise maintenant netcat)
- ✅ Dépendances MongoDB inutiles (retirées)
- ✅ Fichier .env créé avec configuration complète
- ✅ Pipeline CI/CD corrigé (références frontend retirées)

---

## Sommaire

1. Présentation générale
2. Architecture technique
3. Fonctionnalités détaillées
4. Structure du dépôt
5. Installation & démarrage
6. Configuration & secrets
7. Cycle de vie CI/CD
8. Sécurité & bonnes pratiques
9. Dépannage & FAQ
10. Contribution
11. Références & documentation
12. Pages et routes principales (Frontend)
13. Utilisation d'intelligence artificielle

---

## 1. Présentation générale

SkillHub est une plateforme web collaborative de mise en relation entre formateurs et apprenants, développée dans le cadre du Bachelor Concepteur Développeur Web Full Stack (Bloc 03 : Cloud, DevOps et Architecture, Promotion 2025/2026).

Ce dépôt regroupe :

- Un frontend React (Vite)
- Un microservice Auth en **Spring Boot 3 / Java 17**
- Deux microservices Laravel (catalog, inscription)
- Une orchestration Docker
- Un pipeline CI/CD complet

Objectifs Bloc 03 : industrialisation, conteneurisation, automatisation, qualité logicielle

---

## 2. Architecture technique

### Frontend

- **React 19** (Vite)
- Authentification JWT/HMAC, gestion de session, routing protégé, UI moderne

### Backend (microservices)

#### Auth — Spring Boot 3 / Java 17 (port 8011 → 8080)

- Authentification par **HMAC-SHA256** : le client envoie `{email, nonce, timestamp, hmac}` où `hmac = HMAC_SHA256(clé=motDePasse, données="email:nonce:timestamp")`
- Génération de **tokens JWT HS256** (expiration 15 min) signés avec une clé dérivée par SHA-256
- Endpoint `/api/validate-token` appelé par les middlewares Laravel pour vérifier chaque requête
- Protection anti-rejeu : nonce unique + fenêtre timestamp de 5 minutes
- Injecté via `APP_MASTER_KEY` et `JWT_SECRET` (jamais en dur)

#### Catalog — Laravel 13 / PHP 8.3 (port 8012 → 8000)

- CRUD formations, modules, recherche filtrée, attribution formateurs
- Chaque requête protégée vérifie le JWT auprès du service Auth avant de répondre

#### Inscription — Laravel 13 / PHP 8.3 (port 8013 → 8000)

- Gestion des inscriptions apprenants, suivi de progression
- **Limite métier : 5 inscriptions actives maximum par apprenant** (HTTP 400 si dépassé)
- Communique avec Auth (validation JWT) et Catalog (vérification formation existante)

### Base de données

- **MySQL**
- Migrations et seeders pour chaque microservice

### Orchestration & DevOps

- **Docker Compose** : orchestration multi-conteneurs
- **GitHub Actions** : lint, tests, build, analyse SonarCloud, Quality Gate
- **SonarCloud** : analyse de code, couverture, duplications, bugs

---

## 3. Fonctionnalités détaillées

### Authentification & sécurité

- Inscription, connexion, déconnexion, changement de mot de passe
- JWT pour l’authentification, HMAC pour la sécurité des requêtes sensibles
- Middleware anti-rejeu (nonce, timestamp, signature)
- Gestion des rôles (formateur, apprenant)

### Catalogue de formations

- CRUD formations, modules, recherche filtrée
- Attribution des formations aux formateurs
- Gestion des statuts, catégories, niveaux, durée, prix

### Inscriptions

- Inscription à une formation, suivi des apprenants
- Gestion des listes d’inscrits, validation, annulation

### Frontend

- Dashboard dynamique selon le rôle
- Routing public/privé, redirections intelligentes
- UI réactive, filtres, recherche, tableaux, sidebar, topbar, dark mode

### Communication inter-services

- Appels HTTP entre microservices via noms Docker (ex : http://auth_api:8000)
- Aucun code partagé, chaque service est indépendant

### Qualité & tests

#### Auth — Spring Boot (JUnit 5 + JaCoCo)

`SkillhubControllerTest.java` — 16 tests couvrant tous les endpoints :

| Test                              | Ce qui est vérifié                    |
| --------------------------------- | ------------------------------------- |
| `healthOk`                        | GET /api/health retourne UP           |
| `registerOk`                      | Inscription valide → JWT retourné     |
| `registerSansRoleDefautApprenant` | Rôle `apprenant` par défaut si absent |
| `registerEmailInvalide`           | Email malformé → 400                  |
| `registerEmailDejaExistant`       | Email dupliqué → 409                  |
| `loginOk`                         | Login HMAC-SHA256 valide → JWT        |
| `loginHmacInvalide`               | Mauvais HMAC → 401                    |
| `profilOk`                        | Accès profil avec token valide        |
| `profilTokenInvalide`             | Token invalide → 401                  |
| `logoutOk`                        | Déconnexion réussie                   |
| `changePasswordOk`                | Changement de mot de passe valide     |
| `changePasswordAncienIncorrect`   | Ancien mot de passe erroné → 400      |
| `validateTokenValide`             | JWT valide → `{valid: true}`          |
| `validateTokenInvalide`           | JWT falsifié → 401                    |
| `validateTokenSansHeader`         | Pas de header → 401                   |
| `validateTokenSansPrefixeBearer`  | Header sans `Bearer ` → 401           |

`MasterKeyAbsentTest.java` — 2 tests de démarrage :

- `demarrageSansMasterKeyEchoue` — l'application refuse de démarrer sans `APP_MASTER_KEY`
- `demarrageSansMasterKeyNullEchoue` — idem si la variable est null

#### Catalog — Laravel (PHPUnit + PCov)

`FormationControllerTest.php` — 12 tests :

| Test                                          | Ce qui est vérifié                       |
| --------------------------------------------- | ---------------------------------------- |
| `test_list_formations_public`                 | Liste publique sans token                |
| `test_show_formation_increments_views`        | Compteur de vues incrémenté              |
| `test_show_formation_returns_data`            | Données formation retournées             |
| `test_create_formation_as_trainer`            | Formateur peut créer                     |
| `test_create_formation_forbidden_for_learner` | Apprenant → 403                          |
| `test_update_own_formation`                   | Formateur modifie sa formation           |
| `test_update_other_formation_forbidden`       | Modification d'une autre formation → 403 |
| `test_delete_own_formation`                   | Formateur supprime sa formation          |
| `test_delete_other_formation_forbidden`       | Suppression d'une autre → 403            |
| `test_my_formations_returns_only_own`         | Filtre par formateur connecté            |
| `test_my_formations_forbidden_for_learner`    | Apprenant → 403                          |
| `test_no_token_returns_401`                   | Sans token → 401                         |

`ModuleControllerTest.php` — 8 tests (CRUD modules, contrôles de propriété et de rôle)

`MongoActivityLoggerTest.php` — 2 tests (logs MongoDB : URI vide, exception sur URI invalide)

#### Inscription — Laravel (PHPUnit + PCov)

`EnrollmentControllerTest.php` — 10 tests :

| Test                                       | Ce qui est vérifié                   |
| ------------------------------------------ | ------------------------------------ |
| `test_learner_can_enroll`                  | Apprenant s'inscrit à une formation  |
| `test_duplicate_enrollment_returns_same`   | Double inscription → même résultat   |
| `test_trainer_cannot_enroll`               | Formateur → 403                      |
| `test_enroll_not_found_returns_404`        | Formation inexistante → 404          |
| `test_learner_can_unenroll`                | Désinscription réussie               |
| `test_trainer_cannot_unenroll`             | Formateur ne peut pas se désinscrire |
| `test_learner_sees_enrollments`            | Apprenant voit ses inscriptions      |
| `test_learner_no_enrollment_returns_empty` | Pas d'inscription → liste vide       |
| `test_trainer_cannot_view_enrollments`     | Formateur → 403                      |
| `test_no_token_returns_401`                | Sans token → 401                     |

`MongoActivityLoggerTest.php` — 2 tests (identiques au Catalog)

#### Règle métier — Limite 5 inscriptions

Un test dédié (`tests-limite` dans le pipeline CI) vérifie qu'un apprenant déjà inscrit à 5 formations reçoit **HTTP 400** avec un message explicite à la 6ème tentative.

#### Commandes locales

```sh
# Auth (Spring Boot)
cd services/auth && ./mvnw verify

# Catalog (SQLite)
cd services/catalog
composer install && cp .env.example .env && php artisan key:generate
touch database/database.sqlite
DB_CONNECTION=sqlite DB_DATABASE=database/database.sqlite php artisan test --coverage-clover coverage.xml

# Inscription (SQLite)
cd services/inscription
composer install && cp .env.example .env && php artisan key:generate
touch database/database.sqlite
DB_CONNECTION=sqlite DB_DATABASE=database/database.sqlite php artisan test --coverage-clover coverage.xml
```

- Linting JS/PHP, ESLint côté frontend
- Analyse SonarCloud + Quality Gate bloquante à chaque push

---

## 4. Structure du dépôt

```
/frontend                # Application React.js (Vite)
/services/auth           # Microservice Authentification (Spring Boot / Java 17)
/services/catalog        # Microservice Catalogue (Laravel)
/services/inscription    # Microservice Inscriptions (Laravel)
/docker-compose.yml      # Orchestration multi-conteneurs
/contributing.md        # Guide de contribution
/sonar-project.properties# Configurtion SonarCloud
```

Chaque microservice contient :

- `app/`, `routes/`, `database/`, `config/`, `tests/`, `.env`, `composer.json`, `Dockerfile`, etc.

---

## 5. Installation & démarrage

### Prérequis

- Docker & Docker Compose
- Node.js 18+

### Clonage & lancement

```sh
- cloner le projet : it clone http....
- entrer dans le projet : cd Skillhub-copie
docker compose up -d
```

Le frontend sera accessible sur le port **5183**, les microservices sur **8011** (Auth Spring Boot), **8012** (Catalog), **8013** (Inscription).

### Initialisation des bases de données

Les migrations sont lancées automatiquement au démarrage. Pour reseeder :

```sh
docker compose exec catalog_api php artisan migrate:fresh --seed
docker compose exec inscription_api php artisan migrate:fresh --seed
# Auth (Spring Boot) : la base est gérée par JPA/Hibernate (ddl-auto=update)
```

### Lancer le frontend en mode dev

```sh
cd frontend
npm install
npm run dev
```

---

## 6. Configuration & secrets

Chaque microservice possède son propre fichier `.env` (voir `.env.example` dans chaque dossier).

Variables importantes :

- `APP_KEY`, `APP_MASTER_KEY` (HMAC), `DB_*`, `JWT_SECRET`, etc.
- Le frontend utilise `VITE_API_URL` pour cibler l’API.

**Ne jamais versionner les secrets en clair.**

---

## 7. Cycle de vie CI/CD

### Pipeline GitHub Actions

- Lint, tests unitaires, build, analyse SonarCloud à chaque push/PR
- Quality Gate bloquante

### SonarCloud

- Analyse de code, duplications, bugs, couverture
- Organisation : à renseigner dans `sonar-project.properties`

---

## 8. Sécurité & bonnes pratiques

- Authentification JWT, signature HMAC, anti-rejeu
- Séparation stricte des responsabilités (aucun code monolithe)
- Variables d’environnement pour tous les secrets
- Tests unitaires obligatoires
- Convention de nommage Git (Conventional Commits)

---

## 9. Dépannage & FAQ

### Problèmes courants

- **Erreur 401** : vérifier le token, la session, la synchro des clés JWT/HMAC
- **Connexion refusée entre services** : vérifier les URLs Docker (ex : `auth_api:8000`)
- **Pipeline CI/CD échouée** : vérifier la config SonarCloud, la présence des tests
- **Données non affichées** : vérifier le rôle, la session, les réponses API

### Commandes utiles

```sh
# Rebuild complet
docker compose down -v
docker compose up --build

# Logs d’un service
docker compose logs auth_api
```

---

## 10. Contribution

- Fork, branche thématique, PR, review
- Respecter le guide `contributing.md`
- Convention de commit : `type: sujet court`
- Tests et lint obligatoires avant merge

---

## 11. Références & documentation

- Documentation technique : `DOCUMENTATION_TECHNIQUE.md`
- Guide de contribution : `contributing.md`
- OpenAPI : `openapi.yaml`
- SonarCloud, GitHub Actions

---

## Pages et routes principales (Frontend)

### Pages React

- **/ (Accueil)** : Page d’accueil publique, présentation de la plateforme, témoignages, accès rapide aux formations.
- **/formations** : Liste filtrable de toutes les formations disponibles.
- **/formation/:id** : Détail d’une formation (description, modules, inscription).
- **/connexion** : Page de connexion utilisateur (formateur ou apprenant).
- **/inscription** : Page d’inscription avec validation locale et serveur.
- **/dashboard/formateur** : Tableau de bord du formateur (création, gestion, suppression de formations).
- **/dashboard/apprenant** : Tableau de bord de l’apprenant (formations suivies, inscription, progression).
- **/creer-atelier** : Création d’une nouvelle formation (formateur).
- **/modifier-formation/:idFormation** : Modification d’une formation existante (formateur).
- **/apprendre/:id** : Suivi détaillé d’une formation par l’apprenant (progression, modules).
- **/mes-ateliers** : Liste des ateliers/formations de l’utilisateur connecté (formateur ou apprenant).

### Routing (React Router)

| Route                     | Accès       | Composant/Page     | Description principale                               |
| ------------------------- | ----------- | ------------------ | ---------------------------------------------------- |
| `/`                       | Public      | Accueil            | Page d’accueil, présentation, accès rapide           |
| `/formations`             | Public      | Formations         | Catalogue filtrable de toutes les formations         |
| `/formation/:id`          | Public      | DetailFormation    | Détail d’une formation, bouton inscription           |
| `/connexion`              | Invité      | Connexion          | Authentification, redirection selon rôle             |
| `/inscription`            | Invité      | Inscription        | Création de compte, validation locale/serveur        |
| `/dashboard/formateur`    | Formateur   | Formateur          | Dashboard formateur, gestion formations/modules      |
| `/dashboard/apprenant`    | Apprenant   | Apprenant          | Dashboard apprenant, formations suivies              |
| `/creer-atelier`          | Formateur   | CreerAtelier       | Création d’une formation (formateur)                 |
| `/modifier-formation/:id` | Formateur   | ModifierFormation  | Modification d’une formation (formateur)             |
| `/apprendre/:id`          | Apprenant   | SuiviFormation     | Suivi détaillé d’une formation (apprenant)           |
| `/mes-ateliers`           | Authentifié | Ateliers           | Liste des ateliers/formations de l’utilisateur       |
| `/dashboard`              | Authentifié | RedirectionAccueil | Redirige selon le rôle connecté                      |
| `*`                       | Public      | Redirect           | Redirection vers l’accueil pour toute route inconnue |

**Remarque** : Les accès sont contrôlés par des guards (RouteProtegee, RouteInvite) selon le rôle et la session.

---

## 13. Utilisation d'intelligence artificelle

- ChatGPT: Comment adapter un middleware authentificatnio laravel afin d'appeler une ath avec spring boot?
- ChatGPT: Comment s'assurer retourne JWT valide?
- ChatGPT: Commentorchestrer 2 services laravel (plusieurs services) et spring boot
- Chatgpt: C quoi registry stockes
- CharGPT: Erreur issues dans quality gate
