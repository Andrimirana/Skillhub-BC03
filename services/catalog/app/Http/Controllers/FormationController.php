<?php

/**
 * Fichier : FormationController.php
 * Rôle    : Gère les opérations CRUD sur les formations (création, lecture, modification, suppression).
 * Modifié : 2026-04-21
 */

namespace App\Http\Controllers;

use App\Models\Formation;
use App\Models\Module;
use App\Services\MongoActivityLogger;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;

class FormationController extends Controller
{
    public function __construct(private MongoActivityLogger $mongoLogger)
    {
    }

    public function index(Request $requete): JsonResponse
    {
        $utilisateurAuth = $requete->input('auth_user');

        $requeteDB = Formation::query()->with('modules');

        $recherche = trim((string) $requete->query('recherche', $requete->query('search', '')));
        $categorie = trim((string) $requete->query('category', ''));
        $niveau    = trim((string) $requete->query('level', ''));

        if ($recherche !== '') {
            $requeteDB->where(function ($q) use ($recherche): void {
                $q->where('titre', 'like', "%{$recherche}%") // NOSONAR php:S2077 — Eloquent paramètre la valeur, pas d'injection SQL
                  ->orWhere('description', 'like', "%{$recherche}%"); // NOSONAR php:S2077
            });
        }

        if ($categorie !== '') {
            $requeteDB->where('category', $categorie);
        }

        if ($niveau !== '') {
            $requeteDB->where('level', $niveau);
        }

        // Un formateur connecté ne voit que ses propres formations dans la liste publique
        if ($utilisateurAuth && ($utilisateurAuth['role'] ?? '') === 'formateur') {
            $requeteDB->where('user_id', $utilisateurAuth['id']);
        }

        $inclureUserId = $utilisateurAuth && ($utilisateurAuth['role'] ?? '') === 'formateur';

        $formations = $requeteDB->orderByDesc('date')->get()
            ->map(fn (Formation $f) => $this->presenterFormation($f, $inclureUserId));

        return response()->json($formations);
    }

    public function myFormations(Request $requete): JsonResponse
    {
        $utilisateurAuth = $requete->input('auth_user');

        if (($utilisateurAuth['role'] ?? '') !== 'formateur') {
            return response()->json(['message' => 'Seuls les formateurs peuvent accéder à leurs formations.'], 403);
        }

        $formations = Formation::query()
            ->where('user_id', $utilisateurAuth['id'])
            ->with('modules')
            ->orderByDesc('date')
            ->get()
            ->map(fn (Formation $f) => $this->presenterFormation($f, true));

        return response()->json($formations);
    }

    public function show(Formation $formation): JsonResponse
    {
        $formation->increment('vues');
        $formation->refresh();
        $formation->load(['modules' => fn ($q) => $q->orderBy('ordre')]);

        $this->mongoLogger->log('course_viewed', ['course_id' => $formation->id]);

        return response()->json([
            ...$this->presenterFormation($formation, false),
            'modules' => $formation->modules->map(fn ($m) => [
                'id'      => $m->id,
                'titre'   => $m->titre,
                'contenu' => $m->contenu,
                'ordre'   => $m->ordre,
            ])->values(),
        ]);
    }

    public function store(Request $requete): JsonResponse
    {
        $utilisateurAuth = $requete->input('auth_user');

        if (($utilisateurAuth['role'] ?? '') !== 'formateur') {
            return response()->json(['message' => 'Seuls les formateurs peuvent créer une formation.'], 403);
        }

        $donneesValidees = $requete->validate($this->reglesFormation(true));

        $formation = Formation::query()->create([
            'titre'            => $donneesValidees['titre'],
            'description'      => $donneesValidees['description'],
            'category'         => $donneesValidees['category'],
            'date'             => $donneesValidees['date'],
            'statut'           => $donneesValidees['statut'] ?? 'À venir',
            'price'            => $donneesValidees['price'],
            'duration'         => $donneesValidees['duration'],
            'level'            => $donneesValidees['level'],
            'vues'             => 0,
            'user_id'          => $utilisateurAuth['id'],
            'formateur_nom'    => $utilisateurAuth['nom'],
            'apprenants_count' => 0,
        ]);

        $this->mongoLogger->log('course_created', [
            'course_id'  => $formation->id,
            'created_by' => $utilisateurAuth['id'],
        ]);

        $this->remplacerModules($formation, $donneesValidees['modules'] ?? $this->modulesParDefaut());

        return response()->json($this->presenterFormation($formation, true), 201);
    }

    public function update(Request $requete, Formation $formation): JsonResponse
    {
        $utilisateurAuth = $requete->input('auth_user');

        if (($utilisateurAuth['role'] ?? '') !== 'formateur') {
            return response()->json(['message' => 'Seuls les formateurs peuvent modifier une formation.'], 403);
        }

        if ($formation->user_id !== $utilisateurAuth['id']) {
            return response()->json(['message' => 'Cette formation ne vous appartient pas.'], 403);
        }

        $donneesValidees = $requete->validate($this->reglesFormation(false));

        $formation->update([
            'titre'       => $donneesValidees['titre'],
            'description' => $donneesValidees['description'],
            'category'    => $donneesValidees['category'],
            'date'        => $donneesValidees['date'],
            'statut'      => $donneesValidees['statut'] ?? $formation->statut,
            'price'       => $donneesValidees['price'] ?? $formation->price,
            'duration'    => $donneesValidees['duration'] ?? $formation->duration,
            'level'       => $donneesValidees['level'] ?? $formation->level,
        ]);

        $this->mongoLogger->log('course_update', [
            'course_id'  => $formation->id,
            'updated_by' => $utilisateurAuth['id'],
        ]);

        if (\array_key_exists('modules', $donneesValidees)) {
            $this->remplacerModules($formation, $donneesValidees['modules']);
        }

        return response()->json($this->presenterFormation($formation, true));
    }

    public function apprenants(Request $requete, int $idFormation): JsonResponse
    {
        $utilisateurAuth = $requete->input('auth_user');

        // Seul un formateur peut consulter la liste des apprenants
        if (($utilisateurAuth['role'] ?? '') !== 'formateur') {
            return response()->json(['message' => 'Seuls les formateurs peuvent consulter la liste des apprenants.'], 403);
        }

        // La formation doit exister
        $formation = Formation::query()->find($idFormation);
        if ($formation === null) {
            return response()->json(['message' => 'Formation introuvable.'], 404);
        }

        // Le formateur doit être propriétaire de la formation
        if ((int) $formation->user_id !== (int) $utilisateurAuth['id']) {
            return response()->json(['message' => 'Cette formation ne vous appartient pas.'], 403);
        }

        $jeton = (string) $requete->bearerToken();

        // Inscriptions de la formation récupérées auprès du service Inscription
        $urlInscription = rtrim((string) config('services.inscription.url'), '/');
        $reponseInscriptions = Http::withToken($jeton)
            ->get(sprintf('%s/api/formations/%d/inscriptions', $urlInscription, $idFormation)); // NOSONAR — appel inter-services interne, URL provient de la configuration

        if (! $reponseInscriptions->ok()) {
            return response()->json([], 200);
        }

        $inscriptions = collect($reponseInscriptions->json() ?? []);

        if ($inscriptions->isEmpty()) {
            return response()->json([]);
        }

        // Détails utilisateurs (nom, email) récupérés auprès du service Auth
        $urlAuth = rtrim((string) config('services.auth.url'), '/');
        $reponseUtilisateurs = Http::withToken($jeton)
            ->get(sprintf('%s/api/users', $urlAuth)); // NOSONAR — appel inter-services interne, URL provient de la configuration

        $utilisateurs = collect();
        if ($reponseUtilisateurs->ok()) {
            $utilisateurs = collect($reponseUtilisateurs->json('utilisateurs') ?? [])
                ->keyBy('id');
        }

        $apprenants = $inscriptions->map(function (array $inscription) use ($utilisateurs): array {
            $utilisateur = $utilisateurs->get($inscription['utilisateur_id'], []);

            return [
                'id'               => $inscription['utilisateur_id'],
                'nom'              => $utilisateur['nom']   ?? 'Utilisateur inconnu',
                'email'            => $utilisateur['email'] ?? null,
                'progression'      => $inscription['progression'] ?? 0,
                'date_inscription' => $inscription['date_inscription'] ?? null,
            ];
        });

        return response()->json($apprenants->values());
    }

    public function destroy(Request $requete, Formation $formation): JsonResponse
    {
        $utilisateurAuth = $requete->input('auth_user');

        if (($utilisateurAuth['role'] ?? '') !== 'formateur') {
            return response()->json(['message' => 'Seuls les formateurs peuvent supprimer une formation.'], 403);
        }

        if ($formation->user_id !== $utilisateurAuth['id']) {
            return response()->json(['message' => 'Cette formation ne vous appartient pas.'], 403);
        }

        $idFormation = $formation->id;
        $formation->delete();

        $this->mongoLogger->log('course_deleted', [
            'course_id'  => $idFormation,
            'deleted_by' => $utilisateurAuth['id'],
        ]);

        return response()->json(['message' => 'Formation supprimée avec succès.']);
    }

    /**
     * Retourne les règles de validation communes à la création et à la modification.
     * Le paramètre $creation rend obligatoires price, duration et level uniquement à la création.
     */
    private function reglesFormation(bool $creation): array
    {
        $requis = $creation ? 'required' : 'nullable';

        return [
            'titre'             => ['required', 'string', 'max:255'],
            'description'       => ['required', 'string'],
            'category'          => ['required', 'string', 'max:100'],
            'date'              => ['required', 'date'],
            'statut'            => ['nullable', 'string', 'max:60'],
            'price'             => [$requis, 'numeric', 'min:0'],
            'duration'          => [$requis, 'integer', 'min:1'],
            'level'             => [$requis, 'in:beginner,intermediaire,advanced'],
            'modules'           => ['nullable', 'array', 'min:3'],
            'modules.*.titre'   => ['required_with:modules', 'string', 'max:255'],
            'modules.*.contenu' => ['required_with:modules', 'string'],
        ];
    }

    private function presenterFormation(Formation $formation, bool $inclureUserId): array
    {
        $donnees = [
            'id'          => $formation->id,
            'titre'       => $formation->titre,
            'description' => $formation->description,
            'category'    => $formation->category,
            'date'        => optional($formation->date)->format('Y-m-d'),
            'statut'      => $formation->statut,
            'price'       => (float) $formation->price,
            'duration'    => $formation->duration,
            'level'       => $formation->level,
            'vues'        => $formation->vues,
            'apprenants'  => $formation->apprenants_count ?? 0,
            'formateur'   => $formation->formateur_nom,
        ];

        if ($inclureUserId) {
            $donnees['user_id'] = $formation->user_id;
        }

        return $donnees;
    }

    /**
     * Supprime tous les modules existants et les recrée dans l'ordre fourni.
     * Cette approche simple remplace un diff complexe ligne par ligne.
     */
    private function remplacerModules(Formation $formation, array $modules): void
    {
        $formation->modules()->delete();

        foreach (array_values($modules) as $index => $module) {
            Module::query()->create([
                'titre'        => $module['titre'],
                'contenu'      => $module['contenu'],
                'ordre'        => $index + 1,
                'formation_id' => $formation->id,
            ]);
        }
    }

    private function modulesParDefaut(): array
    {
        return [
            ['titre' => 'Introduction',          'contenu' => 'Présentation générale de la formation.'],
            ['titre' => 'Concepts fondamentaux', 'contenu' => 'Notions essentielles à maîtriser.'],
            ['titre' => 'Projet pratique',       'contenu' => 'Application concrète des acquis.'],
        ];
    }
}
