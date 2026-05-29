<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('ratings', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('user_id');       // id de l'apprenant (service Auth)
            $table->unsignedBigInteger('formation_id');  // id de la formation notée
            $table->unsignedTinyInteger('note');          // note de 1 à 5
            $table->text('commentaire')->nullable();
            $table->timestamps();

            // Un apprenant ne peut noter qu'une seule fois la même formation
            $table->unique(['user_id', 'formation_id'], 'uk_user_formation');
            $table->foreign('formation_id')->references('id')->on('formations')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('ratings');
    }
};
