<?php

use Illuminate\Support\Facades\Route;

Route::get('/', function () {
    return response()->json([
        'message' => 'Catalog API Service',
        'version' => '1.0.0',
        'status' => 'UP'
    ]);
});
