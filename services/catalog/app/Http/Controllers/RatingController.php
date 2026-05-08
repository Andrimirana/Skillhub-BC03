<?php

/**
 * Fichier : RatingController.php
 * Rôle    : Permet à un apprenant inscrit à une formation de la noter (1 à 5) une seule fois.
 * Modifié : 2026-05-08
 */

namespace App\Http\Controllers;

use App\Models\Formation;
use App\Models\Rating;
use App\Services\MongoActivityLogger;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Illuminate\Validation\ValidationException;

class RatingController extends Controller
{
    public function __construct(private MongoActivityLogger $mongoLogger)
    {
    }

    public function store(Request $requete, int $idFormation): JsonResponse
    {
        $utilisateurAuth = $requete->input('auth_user');

        // Seul un apprenant peut noter une formation
        if (($utilisateurAuth['role'] ?? '') !== 'apprenant') {
            return response()->json(['message' => 'Seuls les apprenants peuvent noter une formation.'], 403);
        }

        // La formation doit exister localement
        $formation = Formation::query()->find($idFormation);
        if ($formation === null) {
            return response()->json(['message' => 'Formation introuvable.'], 404);
        }

        // Validation de la note (1 à 5) et du commentaire optionnel
        try {
            $donneesValidees = $requete->validate([
                'note'        => ['required', 'integer', 'between:1,5'],
                'commentaire' => ['nullable', 'string', 'max:1000'],
            ]);
        } catch (ValidationException $erreur) {
            return response()->json(['message' => 'Note invalide. Elle doit être un entier entre 1 et 5.'], 400);
        }

        // L'apprenant ne peut noter qu'une seule fois la même formation
        $existeDeja = Rating::query()
            ->where('user_id', $utilisateurAuth['id'])
            ->where('formation_id', $idFormation)
            ->exists();

        if ($existeDeja) {
            return response()->json(['message' => 'Vous avez déjà noté cette formation.'], 400);
        }

        // Vérification d'inscription auprès du service Inscription
        if (! $this->apprenantEstInscrit($requete, $idFormation)) {
            return response()->json(['message' => 'Vous devez être inscrit à la formation pour la noter.'], 403);
        }

        $note = Rating::query()->create([
            'user_id'      => $utilisateurAuth['id'],
            'formation_id' => $idFormation,
            'note'         => $donneesValidees['note'],
            'commentaire'  => $donneesValidees['commentaire'] ?? null,
        ]);

        $this->mongoLogger->log('formation_rated', [
            'user_id'   => $utilisateurAuth['id'],
            'course_id' => $idFormation,
            'note'      => $note->note,
        ]);

        return response()->json([
            'id'           => $note->id,
            'user_id'      => $note->user_id,
            'formation_id' => $note->formation_id,
            'note'         => $note->note,
            'commentaire'  => $note->commentaire,
            'created_at'   => optional($note->created_at)->toIso8601String(),
        ], 201);
    }

    /**
     * Vérifie auprès du service Inscription que l'apprenant est inscrit à la formation.
     * Le jeton Bearer de l'apprenant est transmis pour authentifier l'appel inter-services.
     */
    private function apprenantEstInscrit(Request $requete, int $idFormation): bool
    {
        $urlInscription = rtrim((string) config('services.inscription.url'), '/');
        $jeton          = $requete->bearerToken();

        $reponseApi = Http::withToken((string) $jeton)
            ->get(sprintf('%s/api/inscriptions/verifier/%d', $urlInscription, $idFormation)); // NOSONAR — appel inter-services interne, URL provient de la configuration

        return $reponseApi->ok() && (bool) $reponseApi->json('inscrit');
    }
}
