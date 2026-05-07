<?php

namespace Tests\Feature;

use App\Models\Formation;
use App\Services\MongoActivityLogger;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

// Tests des filtres de recherche sur la liste des formations
// Le contrôleur accepte trois paramètres : recherche, category, level
class FormationFilterTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        // On stub le logger MongoDB pour éviter une vraie connexion
        $this->mock(MongoActivityLogger::class, function ($simulateur): void {
            $simulateur->shouldReceive('log')->andReturn(null);
        });
    }

    // Le filtre recherche cherche dans le titre des formations
    public function test_filter_by_recherche_in_title(): void
    {
        Formation::factory()->create(['titre' => 'Cours de Java avancé']);
        Formation::factory()->create(['titre' => 'Cours de Python débutant']);
        Formation::factory()->create(['titre' => 'Initiation à Java']);

        $reponse = $this->getJson('/api/formations?recherche=Java');

        $reponse->assertOk()->assertJsonCount(2);
    }

    // Le filtre recherche cherche aussi dans la description
    public function test_filter_by_recherche_in_description(): void
    {
        Formation::factory()->create(['titre' => 'Formation A', 'description' => 'On apprend Docker en pratique']);
        Formation::factory()->create(['titre' => 'Formation B', 'description' => 'On apprend Kubernetes']);

        $reponse = $this->getJson('/api/formations?recherche=Docker');

        $reponse->assertOk()->assertJsonCount(1);
    }

    // Le filtre category retourne uniquement les formations de cette catégorie
    public function test_filter_by_category(): void
    {
        Formation::factory()->create(['category' => 'Développement web']);
        Formation::factory()->create(['category' => 'Développement web']);
        Formation::factory()->create(['category' => 'Data']);

        $reponse = $this->getJson('/api/formations?category=Data');

        $reponse->assertOk()->assertJsonCount(1);
    }

    // Le filtre level filtre par niveau (beginner, intermediaire, advanced)
    public function test_filter_by_level(): void
    {
        Formation::factory()->create(['level' => 'beginner']);
        Formation::factory()->create(['level' => 'beginner']);
        Formation::factory()->create(['level' => 'advanced']);

        $reponse = $this->getJson('/api/formations?level=advanced');

        $reponse->assertOk()->assertJsonCount(1);
    }

    // Sans filtre, toutes les formations sont retournées
    public function test_no_filter_returns_all(): void
    {
        Formation::factory()->count(5)->create();

        $reponse = $this->getJson('/api/formations');

        $reponse->assertOk()->assertJsonCount(5);
    }

    // Un filtre qui ne correspond à rien renvoie une liste vide
    public function test_filter_with_no_match_returns_empty(): void
    {
        Formation::factory()->count(3)->create(['category' => 'Design']);

        $reponse = $this->getJson('/api/formations?category=Inexistant');

        $reponse->assertOk()->assertJsonCount(0);
    }

    // Combinaison de plusieurs filtres : ils s'appliquent en AND
    public function test_combined_filters(): void
    {
        Formation::factory()->create(['titre' => 'PHP Test', 'category' => 'web', 'level' => 'beginner']);
        Formation::factory()->create(['titre' => 'PHP Pro',  'category' => 'web', 'level' => 'advanced']);
        Formation::factory()->create(['titre' => 'Java Test','category' => 'web', 'level' => 'beginner']);

        $reponse = $this->getJson('/api/formations?recherche=PHP&level=beginner');

        $reponse->assertOk()->assertJsonCount(1)->assertJsonPath('0.titre', 'PHP Test');
    }
}
