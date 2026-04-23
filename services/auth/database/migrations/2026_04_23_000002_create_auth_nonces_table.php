<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('auth_nonces', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained()->onDelete('cascade');
            $table->string('nonce');
            $table->timestamp('expires_at');
            $table->boolean('consumed')->default(false);
            $table->timestamp('created_at')->useCurrent();
            $table->unique(['user_id', 'nonce']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('auth_nonces');
    }
};
