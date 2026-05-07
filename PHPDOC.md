# Documentation Laravel — Services Catalog et Inscription

**Projet** : SkillHub – Bloc 03
**Frameworks** : Laravel 13 / PHP 8.4
**Date de génération** : 8 mai 2026
**Version** : 1.0

---

## Vue d'ensemble

Cette documentation couvre les deux microservices Laravel du projet SkillHub :

- **Catalog** (port 8012) — gestion des formations, modules et logs d'activité.
- **Inscription** (port 8013) — gestion des inscriptions des apprenants, limitation à 5 inscriptions actives par utilisateur.

Les deux services partagent une architecture identique :
- Routes définies dans `routes/api.php`
- Contrôleurs dans `app/Http/Controllers/`
- Middleware `ValidateServiceToken` qui valide chaque JWT auprès du service Auth
- Modèles Eloquent dans `app/Models/`
- Service utilitaire `MongoActivityLogger` pour journaliser les actions métier

---

## Statistiques du code documenté

### Service Catalog

| Package                    | Nombre  | Description                                       |
| -------------------------- | ------- | ------------------------------------------------- |
| `Http/Controllers`         | 4       | FormationController, ModuleController, ActivityLogController, Controller (base) |
| `Http/Middleware`          | 1       | ValidateServiceToken (vérification JWT auprès du service Auth) |
| `Models`                   | 4       | Formation, Module, ActivityLog (MongoDB), User    |
| `Services`                 | 1       | MongoActivityLogger                               |
| `Providers`                | 1       | AppServiceProvider                                |
| **Tests Feature**          | 4       | FormationControllerTest, ModuleControllerTest, FormationFilterTest, ExampleTest |
| **Tests Unit**             | 3       | FormationModelTest, MongoActivityLoggerTest, ExampleTest |

### Service Inscription

| Package                    | Nombre  | Description                                       |
| -------------------------- | ------- | ------------------------------------------------- |
| `Http/Controllers`         | 2       | EnrollmentController, Controller (base)           |
| `Http/Middleware`          | 1       | ValidateServiceToken                              |
| `Models`                   | 2       | Enrollment, User                                  |
| `Services`                 | 1       | MongoActivityLogger                               |
| `Providers`                | 1       | AppServiceProvider                                |
| **Tests Feature**          | 2       | EnrollmentControllerTest, ExampleTest             |
| **Tests Unit**             | 3       | EnrollmentModelTest, MongoActivityLoggerTest, ExampleTest |

---

## Génération de la documentation HTML

phpDocumentor est configuré pour générer une documentation HTML à partir des annotations PHPDoc.

### Installation (une seule fois)

```powershell
cd services/catalog
composer require --dev phpdocumentor/shim

cd services/inscription
composer require --dev phpdocumentor/shim
```

### Régénération de la documentation

```powershell
# Catalog
cd services/catalog
.\vendor\bin\phpdoc -d app -t docs

# Inscription
cd services/inscription
.\vendor\bin\phpdoc -d app -t docs
```

La documentation HTML est ensuite disponible dans `services/<service>/docs/index.html`.

### Ouvrir la doc générée

```powershell
Start-Process services/catalog/docs/index.html
Start-Process services/inscription/docs/index.html
```

---

## Service Catalog — Endpoints REST

| Méthode | Route                                       | Auth      | Contrôleur                          | Description                                          |
| ------- | ------------------------------------------- | --------- | ----------------------------------- | ---------------------------------------------------- |
| GET     | `/api/health`                               | -         | -                                   | Endpoint de santé du service                         |
| GET     | `/api/formations`                           | Public    | `FormationController@index`         | Liste filtrable (recherche, category, level)         |
| GET     | `/api/formations/{formation}`               | Public    | `FormationController@show`          | Détail d'une formation, incrémente les vues          |
| GET     | `/api/formations/{formation}/modules`       | Public    | `ModuleController@index`            | Modules d'une formation                              |
| GET     | `/api/formations/{formationId}/logs`        | Public    | `ActivityLogController@getByFormation` | 50 derniers logs MongoDB                          |
| GET     | `/api/my-formations`                        | Formateur | `FormationController@myFormations`  | Formations du formateur connecté                     |
| POST    | `/api/formations`                           | Formateur | `FormationController@store`         | Créer une formation                                  |
| PUT     | `/api/formations/{formation}`               | Formateur | `FormationController@update`        | Modifier sa propre formation                         |
| DELETE  | `/api/formations/{formation}`               | Formateur | `FormationController@destroy`       | Supprimer sa propre formation                        |
| POST    | `/api/formations/{formation}/modules`       | Formateur | `ModuleController@store`            | Ajouter un module à sa formation                     |
| PUT     | `/api/modules/{module}`                     | Formateur | `ModuleController@update`           | Modifier son module                                  |
| DELETE  | `/api/modules/{module}`                     | Formateur | `ModuleController@destroy`          | Supprimer son module                                 |

### Filtres disponibles sur GET /api/formations

| Paramètre   | Description                                                    |
| ----------- | -------------------------------------------------------------- |
| `recherche` | Texte cherché dans le titre ou la description                  |
| `category`  | Catégorie exacte (ex: "Développement web", "Data", "Design")   |
| `level`     | Niveau (`beginner`, `intermediaire`, `advanced`)               |

---

## Service Inscription — Endpoints REST

| Méthode | Route                                       | Auth      | Contrôleur                          | Description                                          |
| ------- | ------------------------------------------- | --------- | ----------------------------------- | ---------------------------------------------------- |
| GET     | `/api/health`                               | -         | -                                   | Endpoint de santé du service                         |
| POST    | `/api/formations/{idFormation}/inscription` | Apprenant | `EnrollmentController@store`        | S'inscrire à une formation (limite 5 actives)        |
| DELETE  | `/api/formations/{idFormation}/inscription` | Apprenant | `EnrollmentController@destroy`      | Se désinscrire d'une formation                       |
| GET     | `/api/apprenant/formations`                 | Apprenant | `EnrollmentController@myCourses`    | Liste des formations suivies par l'apprenant         |

### Règle métier — Limite 5 inscriptions

L'apprenant peut être inscrit à **5 formations actives maximum**. À la 6ᵉ tentative, le service répond **HTTP 400** avec le message :

```json
{ "message": "Limite atteinte : un apprenant ne peut pas suivre plus de 5 formations actives." }
```

Cette règle est testée dans le pipeline CI via le job `tests-limite`.

---

## Principales classes

### Catalog

#### `App\Http\Controllers\FormationController`

Gère les opérations CRUD sur les formations (création, lecture, modification, suppression).

- `index(Request)` — liste publique avec filtres (recherche, category, level). Un formateur connecté ne voit que ses propres formations.
- `myFormations(Request)` — liste les formations du formateur connecté.
- `show(Formation)` — détail d'une formation, incrémente le compteur de vues, journalise l'événement `course_viewed`.
- `store(Request)` — crée une formation (formateur uniquement). Crée aussi les modules associés.
- `update(Request, Formation)` — modifie une formation (le formateur doit être propriétaire).
- `destroy(Request, Formation)` — supprime une formation (le formateur doit être propriétaire).

#### `App\Http\Controllers\ModuleController`

Gère les modules d'une formation (ajout, modification, suppression) réservés au formateur propriétaire.

- `index(Formation)` — liste les modules d'une formation, triés par ordre.
- `store(Request, Formation)` — ajoute un module (formateur propriétaire uniquement).
- `update(Request, Module)` — modifie un module (formateur propriétaire uniquement).
- `destroy(Request, Module)` — supprime un module (formateur propriétaire uniquement).

#### `App\Http\Controllers\ActivityLogController`

Expose les logs d'activité MongoDB d'une formation.

- `getByFormation(int $formationId)` — retourne les 50 derniers logs d'une formation, triés du plus récent au plus ancien.

#### `App\Models\Formation`

Entité Eloquent représentant une formation. Champs principaux : `titre`, `description`, `category`, `date`, `statut`, `price`, `duration`, `level`, `vues`, `user_id`, `formateur_nom`, `apprenants_count`.

- Relation : `modules()` (HasMany, triés par `ordre`)
- Casts : `date` → date, `price` → decimal:2, `duration` → integer

#### `App\Models\Module`

Entité Eloquent représentant un module d'une formation. Champs : `titre`, `contenu`, `ordre`, `formation_id`.

- Relation : `formation()` (BelongsTo)

#### `App\Models\ActivityLog`

Entité MongoDB (via `Jenssegers\Mongodb\Eloquent\Model`) stockée dans la collection `activity_logs`. Champs : `event`, `user_id`, `course_id`, `updated_by`, `old_values`, `new_values`, `timestamp`.

#### `App\Services\MongoActivityLogger`

Service utilitaire pour journaliser les événements métier dans MongoDB.

- `log(string $event, array $context)` — insère un document dans la collection `activity_logs`. Si l'URI MongoDB est vide ou que la connexion échoue, l'opération est silencieusement ignorée (pas de blocage du flux principal).

#### `App\Http\Middleware\ValidateServiceToken`

Middleware Laravel qui valide chaque requête authentifiée en appelant `POST /api/validate-token` sur le service Auth (Spring Boot).

- Si le token est valide, ajoute `auth_user` (id, nom, email, role) à la requête.
- Si le token est manquant ou invalide, renvoie HTTP 401.

### Inscription

#### `App\Http\Controllers\EnrollmentController`

Gère les inscriptions des apprenants aux formations.

- `store(Request, int $idFormation)` — inscrit un apprenant à une formation. Vérifie d'abord que la formation existe via le service Catalog. Limite : 5 inscriptions actives maximum (HTTP 400 si dépassée).
- `destroy(Request, int $idFormation)` — désinscrit l'apprenant d'une formation.
- `myCourses(Request)` — liste les formations suivies par l'apprenant connecté, enrichies avec les détails récupérés depuis le service Catalog.

#### `App\Models\Enrollment`

Entité Eloquent représentant une inscription. Champs : `utilisateur_id`, `formation_id`, `progression`, `date_inscription`.

- Casts : `progression` → integer, `date_inscription` → datetime

---

## Tests PHPUnit

### Lancer les tests

```powershell
# Catalog (38 tests / 60 assertions)
cd services/catalog
$env:DB_CONNECTION="sqlite"
$env:DB_DATABASE="database/database.sqlite"
php artisan test --coverage-clover coverage.xml

# Inscription (19 tests + 1 skip / 26 assertions)
cd services/inscription
$env:DB_CONNECTION="sqlite"
$env:DB_DATABASE="database/database.sqlite"
php artisan test --coverage-clover coverage.xml
```

### Tests fonctionnels Catalog (Feature)

| Fichier                      | Tests | Couvre                                                                |
| ---------------------------- | ----- | --------------------------------------------------------------------- |
| `FormationControllerTest`    | 12    | Liste publique, vues incrémentées, CRUD formateur, contrôles de rôle  |
| `FormationFilterTest`        | 7     | Filtres recherche/category/level, combinés                            |
| `ModuleControllerTest`       | 8     | CRUD modules, contrôles de rôle et de propriété                       |

### Tests unitaires Catalog (Unit)

| Fichier                       | Tests | Couvre                                                                |
| ----------------------------- | ----- | --------------------------------------------------------------------- |
| `FormationModelTest`          | 7     | Création, casts, relations modules, incrémentation des vues           |
| `MongoActivityLoggerTest`     | 2     | Logger silencieux sans URI, capture d'exception sur URI invalide      |

### Tests fonctionnels Inscription (Feature)

| Fichier                      | Tests | Couvre                                                                |
| ---------------------------- | ----- | --------------------------------------------------------------------- |
| `EnrollmentControllerTest`   | 10    | Inscription/désinscription, contrôles de rôle, formation introuvable, doublon |

### Tests unitaires Inscription (Unit)

| Fichier                       | Tests | Couvre                                                                |
| ----------------------------- | ----- | --------------------------------------------------------------------- |
| `EnrollmentModelTest`         | 6     | Création, casts (progression integer, date datetime), relations multiples |
| `MongoActivityLoggerTest`     | 2     | (Skipped si extension MongoDB non installée localement)               |

**Total** : **57 tests** (catalog 38 + inscription 19), 1 skip légitime (extension MongoDB locale absente).

---

## Sécurité et authentification

Toutes les routes protégées (`Route::middleware('auth.service')`) passent par `ValidateServiceToken` qui :

1. Lit le header `Authorization: Bearer <jwt>`.
2. Appelle `POST /api/validate-token` sur le service Auth (Spring Boot).
3. Si la réponse est `200 { valid: true, user: {...} }`, ajoute `auth_user` à la requête.
4. Sinon, renvoie HTTP 401.

Les contrôleurs lisent `$requete->input('auth_user')` pour récupérer le profil utilisateur (id, nom, email, role) et appliquer les contrôles de rôle (formateur vs apprenant) et de propriété (le formateur ne peut modifier que ses propres formations).

---

## Configuration des bases de données

### MySQL (production / développement)

Chaque service possède sa propre base MySQL :
- `skillhub_catalog` (formations, modules)
- `skillhub_inscription` (enrollments)

Configuration via `.env` :

```env
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3306
DB_DATABASE=skillhub_catalog
DB_USERNAME=skillhub_user
DB_PASSWORD=skillhub_pass
```

### SQLite (tests)

Pour les tests PHPUnit, on utilise SQLite en fichier pour aller plus vite :

```powershell
$env:DB_CONNECTION="sqlite"
$env:DB_DATABASE="database/database.sqlite"
```

### MongoDB (logs d'activité)

MongoDB stocke uniquement les logs d'activité. 

---

## Maintenance

- Les fichiers de test passent en CI via les jobs `tests-catalog`, `tests-inscription` et `tests-limite` du workflow `.github/workflows/sonarcloud.yml`.
- La couverture est exportée au format Clover (`coverage.xml`) puis remontée à SonarCloud.
- Les exclusions SonarCloud sont définies dans `sonar-project.properties` (modèles Eloquent, seeders, factories, migrations exclus du calcul de duplication CPD).

---

## Ressources complémentaires

- [README.md](README.md) — vue d'ensemble du projet
- [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) — référence technique
- [RAPPORT_ONBOARDING.md](RAPPORT_ONBOARDING.md) — onboarding développeur junior
- [services/auth/JAVADOC.md](services/auth/JAVADOC.md) — documentation Java
- [openapi.yaml](openapi.yaml) — contrat OpenAPI 3
- [Documentation Laravel 13](https://laravel.com/docs/13.x)
- [Documentation phpDocumentor](https://docs.phpdoc.org/)
