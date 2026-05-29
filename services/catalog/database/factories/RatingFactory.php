<?php

namespace Database\Factories;

use App\Models\Formation;
use App\Models\Rating;
use Illuminate\Database\Eloquent\Factories\Factory;

class RatingFactory extends Factory
{
    protected $model = Rating::class;

    public function definition(): array
    {
        return [
            'user_id'      => $this->faker->numberBetween(1, 1000),
            'formation_id' => Formation::factory(),
            'note'         => $this->faker->numberBetween(1, 5),
            'commentaire'  => $this->faker->optional()->sentence(),
        ];
    }
}
