FROM php:8.3-cli

RUN apt-get update && apt-get install -y \
    git curl libpng-dev libonig-dev libxml2-dev zip unzip && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN docker-php-ext-install pdo_mysql mbstring exif pcntl bcmath gd

COPY --from=composer:latest /usr/bin/composer /usr/bin/composer

WORKDIR /var/www

# Couche dépendances — invalidée uniquement si composer.json/lock change
COPY composer.json composer.lock ./
COPY .env.example .env

RUN composer install --prefer-dist --no-dev --optimize-autoloader --no-scripts

# Code source (vendor/ préservé car exclu du contexte par .dockerignore)
COPY . .

# Découverte des packages, génération de la clé et permissions
RUN php artisan package:discover --ansi && \
    php artisan key:generate --force && \
    mkdir -p storage/logs \
             storage/framework/cache \
             storage/framework/sessions \
             storage/framework/views \
             bootstrap/cache && \
    chmod -R 775 storage bootstrap/cache

EXPOSE 8000
