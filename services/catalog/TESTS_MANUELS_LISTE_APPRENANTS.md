# Tests manuels — Liste des apprenants inscrits (vue formateur)

Branche : `feature/liste-apprenants`
Endpoint testé : `GET /api/formations/{id}/apprenants`

## Pré-requis

- Auth (port 8011), Catalog (port 8012), Inscription (port 8013) lancés
- Migrations + seeders catalog appliqués (`php artisan migrate:fresh --seed`)
- Migrations inscription appliquées (`php artisan migrate:fresh`)

> Note : la migration `ratings` n'existe pas sur cette branche, c'est attendu (elle est portée par `feature/notation-formations`).

## Préparation des comptes

```powershell
# Alice : formateur id=1, propriétaire de la formation 1 (par seed)
$alice = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Alice";email="alice@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="formateur"} | ConvertTo-Json)
$jwtAlice = $alice.token

# Eve : autre formateur (NON propriétaire)
$eve = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Eve";email="eve@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="formateur"} | ConvertTo-Json)
$jwtEve = $eve.token

# Bob et Charlie : apprenants inscrits à la formation 1
$bob = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Bob";email="bob@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="apprenant"} | ConvertTo-Json)
$charlie = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Charlie";email="charlie@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="apprenant"} | ConvertTo-Json)

Invoke-RestMethod -Uri http://localhost:8013/api/formations/1/inscription -Method POST -Headers @{Authorization="Bearer $($bob.token)"}
Invoke-RestMethod -Uri http://localhost:8013/api/formations/1/inscription -Method POST -Headers @{Authorization="Bearer $($charlie.token)"}
```

## Cas 1 — Formateur propriétaire → 200 + structure JSON

```powershell
Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/apprenants `
  -Headers @{Authorization="Bearer $jwtAlice"}
```

Attendu : tableau de 2 éléments avec la structure `{id, nom, email, progression, date_inscription}`.

## Cas 2 — Formateur non propriétaire → 403

```powershell
try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/apprenants `
    -Headers @{Authorization="Bearer $jwtEve"}
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 403 (« Cette formation ne vous appartient pas. »).

## Cas 3 — Formation sans apprenants → 200 + tableau vide

```powershell
# Alice crée une nouvelle formation (sans inscription)
$nouvelle = Invoke-RestMethod -Uri http://localhost:8012/api/formations -Method POST `
  -Headers @{Authorization="Bearer $jwtAlice"} `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{titre="Test vide";description="Aucun inscrit";category="dev";date="2026-12-01";price=50;duration=10;level="beginner"} | ConvertTo-Json)

Invoke-RestMethod -Uri "http://localhost:8012/api/formations/$($nouvelle.id)/apprenants" `
  -Headers @{Authorization="Bearer $jwtAlice"}
```

Attendu : `[]` (tableau vide).

## Cas 4 — Sans token JWT → 401

```powershell
try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/apprenants
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 401 (« Jeton manquant. »).

## Bonus — Formation introuvable → 404

```powershell
try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/9999/apprenants `
    -Headers @{Authorization="Bearer $jwtAlice"}
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 404 (« Formation introuvable. »).

## Bonus — Apprenant tente d'accéder → 403

```powershell
try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/apprenants `
    -Headers @{Authorization="Bearer $($bob.token)"}
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 403 (« Seuls les formateurs peuvent consulter la liste des apprenants. »).

## Tests automatisés équivalents

```powershell
cd services/catalog
$env:DB_CONNECTION="sqlite"; $env:DB_DATABASE=":memory:"
php artisan test --filter=FormationApprenantsTest
```

Résultat attendu : **6/6 tests verts**.
