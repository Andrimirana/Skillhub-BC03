<?php

use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return response()->json([
        'message' => 'Inscription API Service',
        'version' => '1.0.0',
        'status' => 'UP'
    ]);
});
