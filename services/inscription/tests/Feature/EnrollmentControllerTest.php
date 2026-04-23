<?php
 
   //teste les fonctionnalités d'inscription et deconnexion à une formation
namespace Tests\Feature;

use App\Models\Enrollment;
use App\Services\MongoActivityLogger;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

// tests des endpoints inscription : seuls les apprenants peuvent s'inscrire à une formation
class EnrollmentControllerTest extends TestCase
{
    use RefreshDatabase;

    // les identifiants test
    private array $profilApprenant  = ['id' => 1, 'nom' => 'Bob', 'email' => 'bob@test.com', 'role' => 'apprenant'];
    private array $profilFormateur  = ['id' => 2, 'nom' => 'Alice', 'email' => 'alice@test.com', 'role' => 'formateur'];
    private int   $idFormation      = 42;

    protected function setUp(): void
    {
        parent::setUp();

        $this->mock(MongoActivityLogger::class, function ($simulateur): void {
            $simulateur->shouldReceive('log')->andReturn(null);
        });
    }

    private function simulerConnexion(array $profil): void
    {
        Http::fake([
            '*/api/validate-token' => Http::response(['valid' => true, 'user' => $profil], 200),
        ]);
    }

    private function simulerFormationDisponible(): void
    {
        Http::fake([
            '*/api/validate-token'              => Http::response(['valid' => true, 'user' => $this->profilApprenant], 200),
            "*/api/formations/{$this->idFormation}" => Http::response(['id' => $this->idFormation, 'titre' => 'PHP avancé'], 200),
        ]);
    }

    // test inscription d'un apprenant à une formation :  201 et créer un enregistrement dans la table enrollments
    public function test_learner_can_enroll(): void
    {
        $this->simulerFormationDisponible();

        $reponse = $this->withToken('jeton-test')->postJson("/api/formations/{$this->idFormation}/inscription");
        $reponse->assertCreated()->assertJsonPath('formation_id', $this->idFormation);
        $this->assertDatabaseHas('enrollments', ['utilisateur_id' => 1, 'formation_id' => $this->idFormation]);
    }

    // test inscription d'un apprenant à une formation twice :  201 et  un seul enregistrement dans la table enrollments
    public function test_duplicate_enrollment_returns_same(): void
    {
        $this->simulerFormationDisponible();

        $this->withToken('jeton-test')->postJson("/api/formations/{$this->idFormation}/inscription");
        $reponse = $this->withToken('jeton-test')->postJson("/api/formations/{$this->idFormation}/inscription");

        $reponse->assertCreated();
        $this->assertDatabaseCount('enrollments', 1);
    }

    // test inscription d'un formateur à une formation : doit retourner 403
    public function test_trainer_cannot_enroll(): void
    {
        $this->simulerConnexion($this->profilFormateur);

        $reponse = $this->withToken('jeton-test')->postJson("/api/formations/{$this->idFormation}/inscription");
        $reponse->assertForbidden();
    }

    public function test_enroll_not_found_returns_404(): void
    {
        Http::fake([
            '*/api/validate-token'              => Http::response(['valid' => true, 'user' => $this->profilApprenant], 200),
            "*/api/formations/{$this->idFormation}" => Http::response([], 404),
        ]);

        $reponse = $this->withToken('jeton-test')->postJson("/api/formations/{$this->idFormation}/inscription");
        $reponse->assertNotFound();
    }

    public function test_learner_can_unenroll(): void
    {
        $this->simulerConnexion($this->profilApprenant);

        Enrollment::factory()->create([
            'utilisateur_id' => 1,
            'formation_id'   => $this->idFormation,
        ]);

        $reponse = $this->withToken('jeton-test')->deleteJson("/api/formations/{$this->idFormation}/inscription");
        $reponse->assertOk();
        $this->assertDatabaseMissing('enrollments', ['utilisateur_id' => 1, 'formation_id' => $this->idFormation]);
    }

    
    public function test_trainer_cannot_unenroll(): void
    {
        $this->simulerConnexion($this->profilFormateur);

        $reponse = $this->withToken('jeton-test')->deleteJson("/api/formations/{$this->idFormation}/inscription");
        $reponse->assertForbidden();
    }
    // test deconnnexion d'une formation inexistante : 404
    public function test_learner_sees_enrollments(): void
    {
        Http::fake([
            '*/api/validate-token'   => Http::response(['valid' => true, 'user' => $this->profilApprenant], 200),
            '*/api/formations/*'     => Http::response(['id' => 10, 'titre' => 'Formation test', 'description' => '', 'category' => 'dev', 'date' => null, 'statut' => '', 'price' => 0, 'duration' => 0, 'level' => '', 'vues' => 0, 'apprenants' => 0, 'formateur' => null, 'modules' => []], 200),
        ]);

        Enrollment::factory()->create(['utilisateur_id' => 1, 'formation_id' => 10]);

        $reponse = $this->withToken('jeton-test')->getJson('/api/apprenant/formations');
        $reponse->assertOk()->assertJsonCount(1);
    }


    public function test_learner_no_enrollment_returns_empty(): void
    {
        $this->simulerConnexion($this->profilApprenant);

        $reponse = $this->withToken('jeton-test')->getJson('/api/apprenant/formations');
        $reponse->assertOk()->assertJson([]);
    }

    public function test_trainer_cannot_view_enrollments(): void
    {
        $this->simulerConnexion($this->profilFormateur);

        $reponse = $this->withToken('jeton-test')->getJson('/api/apprenant/formations');
        $reponse->assertForbidden();
    }

    // test inscription à une formation sans jeton : doit retourner 401
    public function test_no_token_returns_401(): void
    {
        $reponse = $this->postJson("/api/formations/{$this->idFormation}/inscription");
        $reponse->assertUnauthorized();
    }
}
