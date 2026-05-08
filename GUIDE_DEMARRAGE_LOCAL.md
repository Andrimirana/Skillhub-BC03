# Guide de démarrage local — SkillHub microservices

Lance les 3 services en natif (sans Docker) pour faire les tests manuels.

> Les `.env` actuels pointent vers des hôtes Docker (`db`, `auth-service`).
> Ce guide configure tout pour de l'exécution **native sur localhost**.

---

## 0. Pré-requis (vérification)

Ouvre un PowerShell et vérifie que tout est installé :

```powershell
java -version           # → 17.x
mvn -v                  # facultatif, on utilise mvnw
php -v                  # → 8.4.x (XAMPP)
composer --version      # → 2.x
```

MySQL doit tourner. Le plus simple : XAMPP Control Panel → démarrer MySQL.

---

## 1. Configurer les .env (UNE SEULE FOIS)

Le projet est branché Docker par défaut. Il faut surcharger 4 variables côté Catalog et 4 côté Inscription pour pointer sur localhost.

### `services/catalog/.env` — remplace les lignes existantes par :

```env
# DB locale XAMPP
DB_CONNECTION=sqlite
DB_DATABASE=database/database.sqlite

# URLs des autres microservices (natif)
AUTH_SERVICE_URL=http://localhost:8011
INSCRIPTION_SERVICE_URL=http://localhost:8013
```

### `services/inscription/.env` — remplace les lignes existantes par :

```env
# DB locale XAMPP
DB_CONNECTION=sqlite
DB_DATABASE=database/database.sqlite

# URLs des autres microservices (natif)
AUTH_SERVICE_URL=http://localhost:8011
CATALOG_SERVICE_URL=http://localhost:8012
```

### Auth — pas de .env, on passe par variables d'environnement au lancement

L'auth Java a `server.port=8080` par défaut. On va le forcer sur **8011** au lancement (commande plus bas).

---

## 2. Préparer les bases (UNE SEULE FOIS)

```powershell
# Catalog : créer le fichier SQLite + tables + données de démo
cd services/catalog
ni database\database.sqlite -ItemType File -Force | Out-Null
php artisan migrate:fresh --seed
cd ../..

# Inscription : pareil sans seed (pas de données utiles)
cd services/inscription
ni database\database.sqlite -ItemType File -Force | Out-Null
php artisan migrate:fresh
cd ../..
```

> Pour Auth, MySQL doit avoir une base `skillhub_auth`. Spring Boot la peuple seul au premier démarrage (JPA `ddl-auto=update`).
> Crée-la une fois via phpMyAdmin (http://localhost/phpmyadmin) ou en ligne :
> ```powershell
> & "C:\xampp\mysql\bin\mysql.exe" -uroot -e "CREATE DATABASE IF NOT EXISTS skillhub_auth;"
> ```

---

## 3. Démarrage quotidien — 3 terminaux PowerShell

### Terminal 1 — AUTH (port 8011)

```powershell
cd services/auth
$env:APP_MASTER_KEY = "dev-master-key-32-chars-minimum-please"
$env:SERVER_PORT = "8011"
.\mvnw spring-boot:run
```

Attends le message `Started AuthApplication in X seconds`. Vérifie dans un 4e terminal :
```powershell
Invoke-RestMethod http://localhost:8011/api/health   # → {status: UP}
```

### Terminal 2 — CATALOG (port 8012)

```powershell
cd services/catalog
php artisan serve --port=8012
```

Vérifie :
```powershell
Invoke-RestMethod http://localhost:8012/api/health   # → {status: UP}
Invoke-RestMethod http://localhost:8012/api/formations | Select-Object -First 1
# → la formation 1 (React) seedée
```

### Terminal 3 — INSCRIPTION (port 8013)

```powershell
cd services/inscription
php artisan serve --port=8013
```

Vérifie :
```powershell
Invoke-RestMethod http://localhost:8013/api/health   # → {status: UP}
```

---

## 4. Tester les fonctionnalités

Ouvre un **4ᵉ terminal PowerShell** pour les requêtes de test :

- Branche `feature/notation-formations` → suis [services/catalog/TESTS_MANUELS_NOTATION.md](services/catalog/TESTS_MANUELS_NOTATION.md)
- Branche `feature/liste-apprenants` → suis [services/catalog/TESTS_MANUELS_LISTE_APPRENANTS.md](services/catalog/TESTS_MANUELS_LISTE_APPRENANTS.md)

Pour changer de branche, **arrête catalog (Ctrl+C dans le terminal 2)** puis :
```powershell
git checkout feature/notation-formations   # ou feature/liste-apprenants
cd services/catalog
php artisan migrate:fresh --seed
php artisan serve --port=8012
```

---

## Dépannage des erreurs courantes

### Auth : `APP_MASTER_KEY est obligatoire`
Tu as oublié `$env:APP_MASTER_KEY = "..."` avant `.\mvnw spring-boot:run`. Réessaie.

### Auth : `Communications link failure` (MySQL)
MySQL n'est pas démarré ou la base `skillhub_auth` n'existe pas. Démarre MySQL via XAMPP, puis crée la base (voir §2).

### Auth : `Web server failed to start. Port 8011 was already in use`
Un autre service écoute déjà sur 8011. Trouve et tue le process :
```powershell
Get-NetTCPConnection -LocalPort 8011 | Select OwningProcess
Stop-Process -Id <PID>
```

### Catalog : `SQLSTATE[HY000] [2002] No such file or directory`
Le `.env` pointe encore sur `DB_HOST=db`. Refais l'étape 1.

### Catalog/Inscription : `could not find driver`
Active l'extension `pdo_sqlite` dans `c:\xampp\php\php.ini` (cherche `;extension=pdo_sqlite` et enlève le `;`).

### Tests : `401 Non autorisé` partout
Le JWT a expiré (15 min de durée de vie). Refais l'inscription/login pour obtenir un nouveau token.

### Tests : `403` au lieu de `201` sur la notation
L'apprenant n'est pas inscrit à la formation. Vérifie que tu as bien fait l'étape `POST /api/formations/1/inscription` AVANT de noter.

---

## Mode rapide : tests automatisés (sans démarrer les services)

Si tu veux juste valider le code sans lancer les services :

```powershell
# Tests notation
git checkout feature/notation-formations
cd services/catalog
$env:DB_CONNECTION="sqlite"; $env:DB_DATABASE=":memory:"
php artisan test --filter=RatingControllerTest
# Attendu : 8/8 verts

# Tests liste apprenants
git checkout feature/liste-apprenants
php artisan test --filter=FormationApprenantsTest
# Attendu : 6/6 verts
```
