FROM php:8.3-cli-alpine

RUN apk add --no-cache \
    git curl libpng-dev oniguruma-dev libxml2-dev zip unzip

RUN docker-php-ext-install pdo_mysql mbstring exif pcntl bcmath

COPY --from=composer:latest /usr/bin/composer /usr/bin/composer

WORKDIR /var/www

# Couche dépendances — invalidée uniquement si composer.json/lock change
COPY composer.json composer.lock ./
COPY .env.example .env

RUN composer install --prefer-dist --no-dev --optimize-autoloader --no-scripts

# Code source (vendor/ préservé car exclu du contexte par .dockerignore)
COPY . .

# Créer les répertoires requis avant les commandes artisan
RUN mkdir -p storage/logs \
             storage/framework/cache \
             storage/framework/sessions \
             storage/framework/views \
             bootstrap/cache && \
    chmod -R 775 storage bootstrap/cache

RUN php artisan package:discover --ansi && \
    php artisan key:generate --force

EXPOSE 8000
