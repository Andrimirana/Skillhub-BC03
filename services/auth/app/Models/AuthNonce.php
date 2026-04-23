<?php

/**
 * Fichier : AuthNonce.php
 * Rôle    : Modèle représentant un nonce de connexion HMAC — prévient le rejeu des credentials.
 * Modifié : 2026-04-23
 */

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class AuthNonce extends Model
{
    public $timestamps = false;

    protected $fillable = ['user_id', 'nonce', 'expires_at', 'consumed', 'created_at'];

    protected $casts = [
        'expires_at' => 'datetime',
        'consumed'   => 'boolean',
        'created_at' => 'datetime',
    ];

    public function utilisateur(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }
}
