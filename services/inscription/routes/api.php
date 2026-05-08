<?php

use App\Http\Controllers\EnrollmentController;
use Illuminate\Support\Facades\Route;

// Santé du service
Route::get('/health', fn () => response()->json(['status' => 'UP']));

// Toutes les routes sont privées — inscription requiert un token valide
Route::middleware('auth.service')->group(function (): void {
    Route::post('/formations/{formationId}/inscription',    [EnrollmentController::class, 'store']);
    Route::delete('/formations/{formationId}/inscription',  [EnrollmentController::class, 'destroy']);
    Route::get('/apprenant/formations',                     [EnrollmentController::class, 'myCourses']);

    // Vérification d'inscription appelée par le service Catalog (notation des formations)
    Route::get('/inscriptions/verifier/{formationId}',      [EnrollmentController::class, 'verifier']);
});
