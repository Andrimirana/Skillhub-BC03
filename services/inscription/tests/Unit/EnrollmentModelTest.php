<?php

namespace Tests\Unit;

use App\Models\Enrollment;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

// Tests unitaires sur le modèle Enrollment
class EnrollmentModelTest extends TestCase
{
    use RefreshDatabase;

    // Une inscription doit pouvoir être créée avec les champs obligatoires
    public function test_enrollment_can_be_created(): void
    {
        $inscription = Enrollment::factory()->create([
            'utilisateur_id' => 1,
            'formation_id'   => 42,
            'progression'    => 0,
        ]);

        $this->assertDatabaseHas('enrollments', [
            'id'             => $inscription->id,
            'utilisateur_id' => 1,
            'formation_id'   => 42,
        ]);
    }

    // Le champ progression doit être un entier (cast configuré dans le modèle)
    public function test_progression_is_cast_to_integer(): void
    {
        $inscription = Enrollment::factory()->create(['progression' => 50]);

        $this->assertIsInt($inscription->progression);
        $this->assertSame(50, $inscription->progression);
    }

    // La date d'inscription doit être un Carbon (cast datetime)
    public function test_date_inscription_is_a_datetime(): void
    {
        $inscription = Enrollment::factory()->create();

        $this->assertInstanceOf(\Carbon\Carbon::class, $inscription->date_inscription);
    }

    // Plusieurs apprenants peuvent s'inscrire à la même formation
    public function test_multiple_users_can_enroll_to_same_formation(): void
    {
        Enrollment::factory()->create(['utilisateur_id' => 1, 'formation_id' => 10]);
        Enrollment::factory()->create(['utilisateur_id' => 2, 'formation_id' => 10]);
        Enrollment::factory()->create(['utilisateur_id' => 3, 'formation_id' => 10]);

        $this->assertSame(3, Enrollment::where('formation_id', 10)->count());
    }

    // Un même apprenant peut être inscrit à plusieurs formations
    public function test_user_can_enroll_to_multiple_formations(): void
    {
        Enrollment::factory()->create(['utilisateur_id' => 1, 'formation_id' => 10]);
        Enrollment::factory()->create(['utilisateur_id' => 1, 'formation_id' => 20]);
        Enrollment::factory()->create(['utilisateur_id' => 1, 'formation_id' => 30]);

        $this->assertSame(3, Enrollment::where('utilisateur_id', 1)->count());
    }

    // La progression par défaut est zéro
    public function test_default_progression_is_zero(): void
    {
        $inscription = Enrollment::factory()->create(['progression' => 0]);

        $this->assertSame(0, $inscription->progression);
    }
}
