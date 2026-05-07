<?php

namespace Tests\Unit;

use App\Models\Formation;
use App\Models\Module;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

// Tests unitaires sur le modèle Formation et ses relations
class FormationModelTest extends TestCase
{
    use RefreshDatabase;

    // Le modèle Formation doit accepter tous les champs déclarés en fillable
    public function test_formation_can_be_created_with_all_fields(): void
    {
        $formation = Formation::factory()->create([
            'titre'         => 'Test Formation',
            'description'   => 'Description de test',
            'category'      => 'web',
            'price'         => 99.99,
            'duration'      => 30,
            'level'         => 'intermediaire',
            'user_id'       => 1,
            'formateur_nom' => 'Alice',
        ]);

        $this->assertDatabaseHas('formations', [
            'id'    => $formation->id,
            'titre' => 'Test Formation',
        ]);
    }

    // Le champ price doit être casté en décimal à 2 chiffres
    public function test_price_is_cast_to_decimal(): void
    {
        $formation = Formation::factory()->create(['price' => 99.99]);

        // En base SQLite, le decimal:2 est stocké comme string "99.99"
        $this->assertSame('99.99', (string) $formation->price);
    }

    // Le champ duration doit être un entier
    public function test_duration_is_cast_to_integer(): void
    {
        $formation = Formation::factory()->create(['duration' => 45]);

        $this->assertIsInt($formation->duration);
        $this->assertSame(45, $formation->duration);
    }

    // Une formation peut avoir plusieurs modules (relation hasMany)
    public function test_formation_has_many_modules(): void
    {
        $formation = Formation::factory()->create();
        Module::factory()->count(3)->create(['formation_id' => $formation->id]);

        $this->assertCount(3, $formation->modules);
    }

    // Les modules d'une formation sont retournés triés par ordre croissant
    public function test_modules_are_ordered_by_ordre(): void
    {
        $formation = Formation::factory()->create();
        Module::factory()->create(['formation_id' => $formation->id, 'ordre' => 3, 'titre' => 'Trois']);
        Module::factory()->create(['formation_id' => $formation->id, 'ordre' => 1, 'titre' => 'Un']);
        Module::factory()->create(['formation_id' => $formation->id, 'ordre' => 2, 'titre' => 'Deux']);

        $titres = $formation->modules->pluck('titre')->toArray();
        $this->assertSame(['Un', 'Deux', 'Trois'], $titres);
    }

    // Le compteur de vues commence à 0 par défaut
    public function test_default_views_count_is_zero(): void
    {
        $formation = Formation::factory()->create(['vues' => 0]);

        $this->assertSame(0, $formation->vues);
    }

    // L'incrémentation des vues fonctionne via increment()
    public function test_views_can_be_incremented(): void
    {
        $formation = Formation::factory()->create(['vues' => 5]);
        $formation->increment('vues');
        $formation->refresh();

        $this->assertSame(6, $formation->vues);
    }
}
