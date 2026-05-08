<?php

namespace Tests\Feature;

use App\Models\Formation;
use App\Services\MongoActivityLogger;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

// tests de l'endpoint GET /api/formations/{id}/apprenants (vue formateur propriétaire)
class FormationApprenantsTest extends TestCase
{
    use RefreshDatabase;

    private array $profilFormateur      = ['id' => 1,  'nom' => 'Alice', 'email' => 'alice@test.com', 'role' => 'formateur'];
    private array $profilAutreFormateur = ['id' => 99, 'nom' => 'Eve',   'email' => 'eve@test.com',   'role' => 'formateur'];
    private array $profilApprenant      = ['id' => 42, 'nom' => 'Bob',   'email' => 'bob@test.com',   'role' => 'apprenant'];

    protected function setUp(): void
    {
        parent::setUp();

        $this->mock(MongoActivityLogger::class, function ($simulateur): void {
            $simulateur->shouldReceive('log')->andReturn(null);
        });
    }

    /**
     * Simule l'authentification et les appels inter-services (inscription + auth users).
     */
    private function simulerHttp(array $profil, array $inscriptions, array $utilisateurs): void
    {
        Http::fake([
            '*/api/validate-token'                      => Http::response(['valid' => true, 'user' => $profil], 200),
            '*/api/formations/*/inscriptions'           => Http::response($inscriptions, 200),
            '*/api/users'                               => Http::response(['utilisateurs' => $utilisateurs], 200),
        ]);
    }

    public function test_formateur_proprietaire_obtient_la_liste(): void
    {
        $formation = Formation::factory()->create(['user_id' => 1]);

        $this->simulerHttp(
            $this->profilFormateur,
            [
                ['utilisateur_id' => 42, 'progression' => 30, 'date_inscription' => '2026-04-01T10:00:00+00:00'],
                ['utilisateur_id' => 43, 'progression' => 80, 'date_inscription' => '2026-04-02T10:00:00+00:00'],
            ],
            [
                ['id' => 42, 'nom' => 'Bob',     'email' => 'bob@test.com'],
                ['id' => 43, 'nom' => 'Charlie', 'email' => 'charlie@test.com'],
            ]
        );

        $reponse = $this->withToken('jeton-test')->getJson("/api/formations/{$formation->id}/apprenants");

        $reponse->assertOk()
            ->assertJsonCount(2)
            ->assertJsonStructure([['id', 'nom', 'email', 'progression', 'date_inscription']])
            ->assertJsonPath('0.id', 42)
            ->assertJsonPath('0.nom', 'Bob')
            ->assertJsonPath('0.email', 'bob@test.com')
            ->assertJsonPath('0.progression', 30)
            ->assertJsonPath('1.id', 43);
    }

    public function test_formateur_non_proprietaire_obtient_403(): void
    {
        $formation = Formation::factory()->create(['user_id' => 1]);

        $this->simulerHttp($this->profilAutreFormateur, [], []);

        $reponse = $this->withToken('jeton-test')->getJson("/api/formations/{$formation->id}/apprenants");

        $reponse->assertForbidden();
    }

    public function test_formation_sans_apprenants_retourne_tableau_vide(): void
    {
        $formation = Formation::factory()->create(['user_id' => 1]);

        $this->simulerHttp($this->profilFormateur, [], []);

        $reponse = $this->withToken('jeton-test')->getJson("/api/formations/{$formation->id}/apprenants");

        $reponse->assertOk()->assertExactJson([]);
    }

    public function test_sans_jwt_retourne_401(): void
    {
        $formation = Formation::factory()->create(['user_id' => 1]);

        $reponse = $this->getJson("/api/formations/{$formation->id}/apprenants");

        $reponse->assertUnauthorized();
    }

    public function test_formation_introuvable_retourne_404(): void
    {
        $this->simulerHttp($this->profilFormateur, [], []);

        $reponse = $this->withToken('jeton-test')->getJson('/api/formations/9999/apprenants');

        $reponse->assertNotFound();
    }

    public function test_apprenant_obtient_403(): void
    {
        $formation = Formation::factory()->create(['user_id' => 1]);

        $this->simulerHttp($this->profilApprenant, [], []);

        $reponse = $this->withToken('jeton-test')->getJson("/api/formations/{$formation->id}/apprenants");

        $reponse->assertForbidden();
    }
}
