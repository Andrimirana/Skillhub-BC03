<?php

/**
 * Fichier : AuthController.php
 * Rôle    : Authentification HMAC-SHA256 (protocole Auth_TP1) intégrée dans Skillhub.
 *
 * Protocole de connexion (Auth_TP1) :
 *   1. Le client génère un nonce UUID et un timestamp Unix.
 *   2. Le client calcule : HMAC-SHA256(clé=mot_de_passe_clair, data="email:nonce:timestamp") → Base64.
 *   3. Le serveur vérifie le timestamp (±60 s), contrôle le nonce en base, recalcule le HMAC
 *      avec le mot de passe déchiffré et compare en temps constant.
 *   4. En cas d'échec répété (≥ 5), le compte est verrouillé 2 minutes.
 *
 * Modifié : 2026-04-23
 */

namespace App\Http\Controllers;

use App\Models\AuthNonce;
use App\Models\User;
use App\Services\ServiceJwt;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Cache;
use Throwable;

class AuthController extends Controller
{
    // ── Constantes de sécurité (alignées sur Auth_TP1) ───────────────────────
    private const MAX_TENTATIVES       = 5;
    private const MINUTES_VERROUILLAGE = 2;
    private const FENETRE_TIMESTAMP_S  = 60;
    private const TTL_NONCE_S          = 120;

    public function __construct(private ServiceJwt $serviceJwt)
    {
    }

    // ════════════════════════════════════════════════════════════════════
    //  INSCRIPTION
    // ════════════════════════════════════════════════════════════════════

    public function inscription(Request $requete): JsonResponse
    {
        $donneesValidees = $requete->validate([
            'nom'          => ['required', 'string', 'max:255'],
            'email'        => ['required', 'email', 'max:255', 'unique:users,email'],
            // Politique 12 chars min (Auth_TP1) : majuscule, minuscule, chiffre, caractère spécial
            'mot_de_passe' => ['required', 'string', 'min:12', 'regex:/^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/'],
            'role'         => ['required', 'in:formateur,apprenant'],
        ]);

        $utilisateur = User::query()->create([
            'name'     => $donneesValidees['nom'],
            'email'    => $donneesValidees['email'],
            'password' => $this->chiffrerAesGcm($donneesValidees['mot_de_passe']),
            'role'     => $donneesValidees['role'],
        ]);

        $expiration = CarbonImmutable::now()->addHours(8)->timestamp;

        $jeton = $this->serviceJwt->generer([
            'sub'   => $utilisateur->id,
            'email' => $utilisateur->email,
            'role'  => $utilisateur->role,
            'iat'   => CarbonImmutable::now()->timestamp,
            'exp'   => $expiration,
        ]);

        return response()->json($this->construireReponseJwt($utilisateur, $jeton, $expiration), 201);
    }

    // ════════════════════════════════════════════════════════════════════
    //  CONNEXION — protocole HMAC-SHA256 (Auth_TP1)
    // ════════════════════════════════════════════════════════════════════

    public function connexion(Request $requete): JsonResponse
    {
        $donneesValidees = $requete->validate([
            'email'     => ['required', 'email'],
            'nonce'     => ['required', 'string'],
            'timestamp' => ['required', 'integer'],
            'hmac'      => ['required', 'string'],
        ]);

        $utilisateur = User::query()->where('email', $donneesValidees['email'])->first();

        // Email inconnu — même message générique pour éviter l'énumération d'utilisateurs
        if (! $utilisateur) {
            return response()->json(['message' => 'Identifiants invalides.'], 401);
        }

        // Vérification du verrouillage anti brute-force (5 tentatives → 2 min)
        if ($utilisateur->lock_until && now()->lessThan($utilisateur->lock_until)) {
            return response()->json([
                'message' => 'Compte bloqué — trop de tentatives. Réessayez dans '
                    . self::MINUTES_VERROUILLAGE . ' minutes.',
            ], 429);
        }

        // Fenêtre timestamp ±60 secondes (anti-rejeu temporel)
        if (abs(time() - (int) $donneesValidees['timestamp']) > self::FENETRE_TIMESTAMP_S) {
            $this->incrementerTentativesEchouees($utilisateur);
            return response()->json(['message' => 'Identifiants invalides.'], 401);
        }

        // Nonce de connexion déjà utilisé → rejeu détecté
        if (AuthNonce::where('user_id', $utilisateur->id)
                     ->where('nonce', $donneesValidees['nonce'])
                     ->exists()) {
            $this->incrementerTentativesEchouees($utilisateur);
            return response()->json(['message' => 'Identifiants invalides.'], 401);
        }

        // Enregistrement du nonce avant la vérification HMAC (TTL 120 s)
        AuthNonce::create([
            'user_id'    => $utilisateur->id,
            'nonce'      => $donneesValidees['nonce'],
            'expires_at' => now()->addSeconds(self::TTL_NONCE_S),
            'consumed'   => false,
            'created_at' => now(),
        ]);

        // Déchiffrement du mot de passe stocké pour recalculer le HMAC côté serveur
        $motDePasseClair = $this->dechiffrerAesGcm($utilisateur->password);
        if ($motDePasseClair === null) {
            $this->incrementerTentativesEchouees($utilisateur);
            return response()->json(['message' => 'Identifiants invalides.'], 401);
        }

        // HMAC-SHA256(clé=mot_de_passe, data="email:nonce:timestamp") encodé en Base64
        $message     = $donneesValidees['email'] . ':' . $donneesValidees['nonce'] . ':' . $donneesValidees['timestamp'];
        $hmacAttendu = base64_encode(hash_hmac('sha256', $message, $motDePasseClair, true));

        // Comparaison en temps constant (prévient les attaques temporelles)
        if (! hash_equals($hmacAttendu, $donneesValidees['hmac'])) {
            $this->incrementerTentativesEchouees($utilisateur);
            return response()->json(['message' => 'Identifiants invalides.'], 401);
        }

        // Succès : réinitialisation compteur + marquage nonce consommé + émission JWT
        $utilisateur->failed_attempts = 0;
        $utilisateur->lock_until      = null;
        $utilisateur->save();

        AuthNonce::where('user_id', $utilisateur->id)
                 ->where('nonce', $donneesValidees['nonce'])
                 ->update(['consumed' => true]);

        $expiration = CarbonImmutable::now()->addHours(8)->timestamp;

        $jeton = $this->serviceJwt->generer([
            'sub'   => $utilisateur->id,
            'email' => $utilisateur->email,
            'role'  => $utilisateur->role,
            'iat'   => CarbonImmutable::now()->timestamp,
            'exp'   => $expiration,
        ]);

        return response()->json($this->construireReponseJwt($utilisateur, $jeton, $expiration));
    }

    // ════════════════════════════════════════════════════════════════════
    //  FORCE DU MOT DE PASSE (Auth_TP1 — évaluation sans stockage)
    // ════════════════════════════════════════════════════════════════════

    public function evaluatePasswordStrength(Request $requete): JsonResponse
    {
        $requete->validate(['mot_de_passe' => ['required', 'string']]);

        return response()->json([
            'force' => $this->calculerForceMotDePasse($requete->input('mot_de_passe')),
        ]);
    }

    // ════════════════════════════════════════════════════════════════════
    //  PROFIL
    // ════════════════════════════════════════════════════════════════════

    public function profil(Request $requete): JsonResponse
    {
        return response()->json($this->presenterUtilisateur($requete->user()));
    }

    // ════════════════════════════════════════════════════════════════════
    //  DÉCONNEXION (blacklist JWT)
    // ════════════════════════════════════════════════════════════════════

    public function deconnexion(Request $requete): JsonResponse
    {
        $jeton = $requete->bearerToken();

        if ($jeton) {
            try {
                $donneesJwt       = $this->serviceJwt->decoder($jeton);
                $expiration        = (int) ($donneesJwt['exp'] ?? CarbonImmutable::now()->addHours(8)->timestamp);
                $secondesRestantes = max(1, $expiration - CarbonImmutable::now()->timestamp);

                Cache::put($this->cleBlacklist($jeton), true, now()->addSeconds($secondesRestantes));
            } catch (Throwable $e) {
                error_log('[Auth] Erreur blacklist jeton : ' . $e->getMessage());
            }
        }

        return response()->json(['message' => 'Déconnexion effectuée.']);
    }

    // ════════════════════════════════════════════════════════════════════
    //  CHANGEMENT DE MOT DE PASSE
    // ════════════════════════════════════════════════════════════════════

    public function modifierMotDePasse(Request $requete): JsonResponse
    {
        $utilisateur = $requete->user();

        $donneesValidees = $requete->validate([
            'ancien_mot_de_passe'  => ['required', 'string'],
            'nouveau_mot_de_passe' => [
                'required', 'string', 'min:12',
                'regex:/^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/',
                'different:ancien_mot_de_passe',
            ],
        ]);

        $ancienMotDePasseClair = $this->dechiffrerAesGcm($utilisateur->password);

        if ($ancienMotDePasseClair !== $donneesValidees['ancien_mot_de_passe']) {
            return response()->json(['message' => "L'ancien mot de passe est incorrect."], 403);
        }

        $utilisateur->password = $this->chiffrerAesGcm($donneesValidees['nouveau_mot_de_passe']);
        $utilisateur->save();

        return response()->json(['message' => 'Mot de passe modifié avec succès.']);
    }

    // ════════════════════════════════════════════════════════════════════
    //  VALIDATION INTERNE (appelée par les autres microservices)
    // ════════════════════════════════════════════════════════════════════

    public function validateToken(Request $requete): JsonResponse
    {
        $jeton = $requete->bearerToken();

        if (! $jeton) {
            return response()->json(['valid' => false, 'message' => 'Jeton manquant.'], 401);
        }

        if (Cache::has($this->cleBlacklist($jeton))) {
            return response()->json(['valid' => false, 'message' => 'Jeton blacklisté.'], 401);
        }

        try {
            $donneesJwt    = $this->serviceJwt->decoder($jeton);
            $idUtilisateur = (int) ($donneesJwt['sub'] ?? 0);
            $utilisateur   = User::query()->find($idUtilisateur);

            if (! $utilisateur) {
                return response()->json(['valid' => false, 'message' => 'Utilisateur introuvable.'], 401);
            }

            return response()->json(['valid' => true, 'user' => $this->presenterUtilisateur($utilisateur)]);
        } catch (Throwable) {
            return response()->json(['valid' => false, 'message' => 'Jeton invalide ou expiré.'], 401);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  MÉTHODES PRIVÉES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Incrémente le compteur d'échecs et verrouille le compte si le seuil est atteint.
     * Seuil : MAX_TENTATIVES (5) → verrouillage MINUTES_VERROUILLAGE (2) minutes.
     */
    private function incrementerTentativesEchouees(User $utilisateur): void
    {
        $utilisateur->failed_attempts += 1;

        if ($utilisateur->failed_attempts >= self::MAX_TENTATIVES) {
            $utilisateur->lock_until = Carbon::now()->addMinutes(self::MINUTES_VERROUILLAGE);
        }

        $utilisateur->save();
    }

    /**
     * Évalue la force d'un mot de passe (Auth_TP1 PasswordPolicyValidator).
     * WEAK   : < 12 caractères ou ≤ 2 critères.
     * MEDIUM : 3 critères ou 4 critères + < 16 caractères.
     * STRONG : 4 critères et ≥ 16 caractères.
     */
    private function calculerForceMotDePasse(string $motDePasse): string
    {
        if (strlen($motDePasse) < 12) {
            return 'WEAK';
        }

        $score = 0;
        if (preg_match('/[A-Z]/', $motDePasse)) {
            $score++;
        }
        if (preg_match('/[a-z]/', $motDePasse)) {
            $score++;
        }
        if (preg_match('/[0-9]/', $motDePasse)) {
            $score++;
        }
        if (preg_match('/[^a-zA-Z0-9]/', $motDePasse)) {
            $score++;
        }

        if ($score <= 2) {
            return 'WEAK';
        }

        if ($score === 3) {
            return 'MEDIUM';
        }

        return strlen($motDePasse) >= 16 ? 'STRONG' : 'MEDIUM';
    }

    private function presenterUtilisateur(User $utilisateur): array
    {
        return [
            'id'    => $utilisateur->id,
            'nom'   => $utilisateur->name,
            'email' => $utilisateur->email,
            'role'  => $utilisateur->role,
        ];
    }

    private function construireReponseJwt(User $utilisateur, string $jeton, int $expiration): array
    {
        return [
            'token'       => $jeton,
            'token_type'  => 'Bearer',
            'expires_at'  => $expiration,
            'utilisateur' => $this->presenterUtilisateur($utilisateur),
        ];
    }

    private function cleBlacklist(string $jeton): string
    {
        return 'jwt_blacklist:' . hash('sha256', $jeton);
    }

    /**
     * Chiffre un mot de passe en AES-256-GCM.
     * Format stocké : base64(iv):base64(ciphertext):base64(tag)
     */
    private function chiffrerAesGcm(string $motDePasseClair): string
    {
        $cle        = hash('sha256', env('APP_MASTER_KEY', 'cle_par_defaut'), true);
        $vecteurInit = openssl_random_pseudo_bytes(openssl_cipher_iv_length('aes-256-gcm'));
        $etiquette  = '';

        $chiffre = openssl_encrypt($motDePasseClair, 'aes-256-gcm', $cle, OPENSSL_RAW_DATA, $vecteurInit, $etiquette);

        return base64_encode($vecteurInit) . ':' . base64_encode($chiffre) . ':' . base64_encode($etiquette);
    }

    /**
     * Déchiffre une chaîne AES-256-GCM au format iv:ciphertext:tag.
     * Retourne null si le format est invalide ou si le déchiffrement échoue.
     */
    private function dechiffrerAesGcm(mixed $motDePasseChiffre): ?string
    {
        if (! is_string($motDePasseChiffre) || empty($motDePasseChiffre)) {
            return null;
        }

        $parties = explode(':', $motDePasseChiffre);

        if (count($parties) !== 3) {
            return null;
        }

        $cle         = hash('sha256', env('APP_MASTER_KEY', 'cle_par_defaut'), true);
        $vecteurInit = base64_decode((string) $parties[0], true);
        $chiffre     = base64_decode((string) $parties[1], true);
        $etiquette   = base64_decode((string) $parties[2], true);

        if ($vecteurInit === false || $chiffre === false || $etiquette === false) {
            return null;
        }

        try {
            return openssl_decrypt($chiffre, 'aes-256-gcm', $cle, OPENSSL_RAW_DATA, $vecteurInit, $etiquette);
        } catch (Throwable) {
            return null;
        }
    }
}
