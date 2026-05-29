<?php

namespace Tests\Feature;

use App\Models\Formation;
use App\Models\Rating;
use App\Services\MongoActivityLogger;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

// tests de l'endpoint POST /api/formations/{id}/noter (notation des formations)
class RatingControllerTest extends TestCase
{
    use RefreshDatabase;

    private array $profilApprenant = ['id' => 42, 'nom' => 'Bob', 'email' => 'bob@test.com', 'role' => 'apprenant'];
    private array $profilFormateur = ['id' => 7,  'nom' => 'Alice', 'email' => 'alice@test.com', 'role' => 'formateur'];

    protected function setUp(): void
    {
        parent::setUp();

        $this->mock(MongoActivityLogger::class, function ($simulateur): void {
            $simulateur->shouldReceive('log')->andReturn(null);
        });
    }

    /**
     * Simule la connexion (validate-token) et la vérification d'inscription
     * auprès du service Inscription.
     */
    private function simulerHttp(array $profil, bool $estInscrit): void
    {
        Http::fake([
            '*/api/validate-token'             => Http::response(['valid' => true, 'user' => $profil], 200),
            '*/api/inscriptions/verifier/*'    => Http::response(['inscrit' => $estInscrit], 200),
        ]);
    }

    public function test_apprenant_inscrit_peut_noter(): void
    {
        $this->simulerHttp($this->profilApprenant, true);
        $formation = Formation::factory()->create();

        $reponse = $this->withToken('jeton-test')
            ->postJson("/api/formations/{$formation->id}/noter", [
                'note'        => 4,
                'commentaire' => 'Très bonne formation',
            ]);

        $reponse->assertCreated()
            ->assertJsonPath('note', 4)
            ->assertJsonPath('commentaire', 'Très bonne formation');

        $this->assertDatabaseHas('ratings', [
            'user_id'      => $this->profilApprenant['id'],
            'formation_id' => $formation->id,
            'note'         => 4,
        ]);
    }

    public function test_apprenant_ne_peut_noter_deux_fois(): void
    {
        $this->simulerHttp($this->profilApprenant, true);
        $formation = Formation::factory()->create();

        Rating::factory()->create([
            'user_id'      => $this->profilApprenant['id'],
            'formation_id' => $formation->id,
            'note'         => 5,
        ]);

        $reponse = $this->withToken('jeton-test')
            ->postJson("/api/formations/{$formation->id}/noter", ['note' => 3]);

        $reponse->assertStatus(400);
        $this->assertEquals(1, Rating::query()->where('formation_id', $formation->id)->count());
    }

    public function test_note_hors_intervalle(): void
    {
        $this->simulerHttp($this->profilApprenant, true);
        $formation = Formation::factory()->create();

        $reponse = $this->withToken('jeton-test')
            ->postJson("/api/formations/{$formation->id}/noter", ['note' => 6]);

        $reponse->assertStatus(400);
        $this->assertDatabaseCount('ratings', 0);
    }

    public function test_apprenant_non_inscrit(): void
    {
        $this->simulerHttp($this->profilApprenant, false);
        $formation = Formation::factory()->create();

        $reponse = $this->withToken('jeton-test')
            ->postJson("/api/formations/{$formation->id}/noter", ['note' => 4]);

        $reponse->assertForbidden();
        $this->assertDatabaseCount('ratings', 0);
    }

    public function test_sans_jwt_retourne_401(): void
    {
        $formation = Formation::factory()->create();

        $reponse = $this->postJson("/api/formations/{$formation->id}/noter", ['note' => 4]);

        $reponse->assertUnauthorized();
    }

    public function test_formateur_ne_peut_noter(): void
    {
        $this->simulerHttp($this->profilFormateur, true);
        $formation = Formation::factory()->create();

        $reponse = $this->withToken('jeton-test')
            ->postJson("/api/formations/{$formation->id}/noter", ['note' => 4]);

        $reponse->assertForbidden();
    }

    public function test_show_formation_retourne_note_moyenne_et_nbre_avis(): void
    {
        $formation = Formation::factory()->create();

        // Notes : 5 et 4  moyenne = 4.5, nbre_avis = 2
        Rating::factory()->create(['formation_id' => $formation->id, 'note' => 5]);
        Rating::factory()->create(['formation_id' => $formation->id, 'note' => 4]);

        $reponse = $this->getJson("/api/formations/{$formation->id}");

        $reponse->assertOk()
            ->assertJsonPath('nbre_avis', 2)
            ->assertJsonPath('note_moyenne', 4.5);
    }

    public function test_show_formation_sans_avis_retourne_null(): void
    {
        $formation = Formation::factory()->create();

        $reponse = $this->getJson("/api/formations/{$formation->id}");

        $reponse->assertOk()
            ->assertJsonPath('nbre_avis', 0)
            ->assertJsonPath('note_moyenne', null);
    }
}
