# Tests manuels — Notation des formations

Branche : `feature/notation-formations`
Endpoint testé : `POST /api/formations/{id}/noter`

## Pré-requis

- Auth (port 8011), Catalog (port 8012), Inscription (port 8013) lancés
- Migrations + seeders catalog appliqués (`php artisan migrate:fresh --seed`)
- Migrations inscription appliquées (`php artisan migrate:fresh`)

## Préparation des comptes

```powershell
# Formateur Alice (id=1, propriétaire de la formation 1 par seed)
$alice = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Alice";email="alice@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="formateur"} | ConvertTo-Json)

# Apprenant Bob
$bob = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Bob";email="bob@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="apprenant"} | ConvertTo-Json)

# Bob s'inscrit à la formation 1
Invoke-RestMethod -Uri http://localhost:8013/api/formations/1/inscription -Method POST `
  -Headers @{Authorization="Bearer $($bob.token)"}
```

## Cas 1 — Apprenant inscrit soumet une note valide → 201

```powershell
Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/noter -Method POST `
  -Headers @{Authorization="Bearer $($bob.token)"} `
  -ContentType "application/json; charset=utf-8" `
  -Body '{"note":4,"commentaire":"Très bonne formation"}'
```

Attendu : objet rating `{id, user_id, formation_id, note=4, commentaire, created_at}`.
Vérification SQL : `SELECT * FROM ratings;` doit contenir la ligne.

## Cas 2 — Même apprenant tente une 2ᵉ notation → 400

```powershell
try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/noter -Method POST `
    -Headers @{Authorization="Bearer $($bob.token)"} `
    -ContentType "application/json; charset=utf-8" `
    -Body '{"note":5}'
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 400 (« Vous avez déjà noté cette formation. »).

## Cas 3 — Note hors intervalle → 400

```powershell
$charlie = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Charlie";email="charlie@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="apprenant"} | ConvertTo-Json)
Invoke-RestMethod -Uri http://localhost:8013/api/formations/1/inscription -Method POST `
  -Headers @{Authorization="Bearer $($charlie.token)"}

try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/noter -Method POST `
    -Headers @{Authorization="Bearer $($charlie.token)"} `
    -ContentType "application/json; charset=utf-8" `
    -Body '{"note":6}'
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 400 (« Note invalide. Elle doit être un entier entre 1 et 5. »).

## Cas 4 — Apprenant non inscrit → 403

```powershell
$diana = Invoke-RestMethod -Uri http://localhost:8011/api/register -Method POST `
  -ContentType "application/json; charset=utf-8" `
  -Body (@{nom="Diana";email="diana@test.com";password="MotDePasse123!";passwordConfirm="MotDePasse123!";role="apprenant"} | ConvertTo-Json)

try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/noter -Method POST `
    -Headers @{Authorization="Bearer $($diana.token)"} `
    -ContentType "application/json; charset=utf-8" `
    -Body '{"note":4}'
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 403 (« Vous devez être inscrit à la formation pour la noter. »).

## Cas 5 — Sans token JWT → 401

```powershell
try {
  Invoke-RestMethod -Uri http://localhost:8012/api/formations/1/noter -Method POST `
    -ContentType "application/json; charset=utf-8" `
    -Body '{"note":4}'
} catch { $_.Exception.Response.StatusCode.value__ }
```

Attendu : 401 (« Jeton manquant. »).

## Bonus — Vérifier `note_moyenne` et `nbre_avis` sur GET formation

```powershell
Invoke-RestMethod -Uri http://localhost:8012/api/formations/1
```

Attendu : la réponse JSON contient `note_moyenne` (arrondi 0.1) et `nbre_avis` (entier).

## Tests automatisés équivalents

```powershell
cd services/catalog
$env:DB_CONNECTION="sqlite"; $env:DB_DATABASE=":memory:"
php artisan test --filter=RatingControllerTest
```

Résultat attendu : **8/8 tests verts**.
