<?php

namespace Database\Seeders;

use App\Models\Formation;
use App\Models\Module;
use Illuminate\Database\Seeder;

class FormationSeeder extends Seeder
{
    public function run(): void
    {
        $formations = [
            [
                'titre'         => 'Développement Web avec React',
                'description'   => 'Maîtrisez React de A à Z : composants, hooks, state management et déploiement. Construisez des interfaces modernes et performantes.',
                'category'      => 'Développement web',
                'date'          => '2026-05-01',
                'statut'        => 'Disponible',
                'price'         => 149.99,
                'duration'      => 40,
                'level'         => 'intermediaire',
                'vues'          => 312,
                'user_id'       => 1,
                'formateur_nom' => 'Jean Dupont',
                'apprenants_count' => 28,
                'modules'       => [
                    ['titre' => 'Introduction à React', 'contenu' => 'Présentation de la bibliothèque, JSX et premier composant.'],
                    ['titre' => 'Props et State',        'contenu' => 'Passage de données entre composants et gestion de l\'état local.'],
                    ['titre' => 'Hooks fondamentaux',   'contenu' => 'useState, useEffect, useRef et règles des hooks.'],
                    ['titre' => 'React Router',          'contenu' => 'Navigation, routes dynamiques et protection des pages.'],
                    ['titre' => 'Appels API REST',       'contenu' => 'Fetch, Axios, gestion des erreurs et état de chargement.'],
                    ['titre' => 'Projet final',          'contenu' => 'Application complète : todo-list avec backend simulé.'],
                ],
            ],
            [
                'titre'         => 'Python pour la Data Science',
                'description'   => 'Explorez l\'analyse de données avec Python, Pandas, NumPy et Matplotlib. Idéal pour débuter dans le domaine de la data.',
                'category'      => 'Data',
                'date'          => '2026-05-10',
                'statut'        => 'Disponible',
                'price'         => 129.00,
                'duration'      => 35,
                'level'         => 'beginner',
                'vues'          => 478,
                'user_id'       => 1,
                'formateur_nom' => 'Marie Curie',
                'apprenants_count' => 54,
                'modules'       => [
                    ['titre' => 'Les bases de Python',  'contenu' => 'Syntaxe, types de données, boucles et fonctions.'],
                    ['titre' => 'NumPy',                 'contenu' => 'Tableaux multidimensionnels et opérations vectorielles.'],
                    ['titre' => 'Pandas',                'contenu' => 'DataFrames, nettoyage et manipulation de données.'],
                    ['titre' => 'Visualisation',         'contenu' => 'Graphiques avec Matplotlib et Seaborn.'],
                    ['titre' => 'Projet data réel',      'contenu' => 'Analyse complète d\'un jeu de données public.'],
                ],
            ],
            [
                'titre'         => 'UX/UI Design : Les fondamentaux',
                'description'   => 'Apprenez les principes du design centré utilisateur, la création de wireframes et de prototypes avec Figma.',
                'category'      => 'Design',
                'date'          => '2026-05-15',
                'statut'        => 'Disponible',
                'price'         => 99.00,
                'duration'      => 25,
                'level'         => 'beginner',
                'vues'          => 203,
                'user_id'       => 1,
                'formateur_nom' => 'Sophie Martin',
                'apprenants_count' => 19,
                'modules'       => [
                    ['titre' => 'Principes UX',         'contenu' => 'Empathie, recherche utilisateur et personas.'],
                    ['titre' => 'Design visuel',         'contenu' => 'Couleurs, typographie, grilles et espace blanc.'],
                    ['titre' => 'Wireframes Figma',      'contenu' => 'Prototypage basse fidélité et tests utilisateurs.'],
                ],
            ],
            [
                'titre'         => 'DevOps & Docker en pratique',
                'description'   => 'Conteneurisez vos applications avec Docker et Docker Compose. Maîtrisez les pipelines CI/CD et le déploiement automatisé.',
                'category'      => 'DevOps',
                'date'          => '2026-06-01',
                'statut'        => 'À venir',
                'price'         => 179.00,
                'duration'      => 50,
                'level'         => 'advanced',
                'vues'          => 156,
                'user_id'       => 1,
                'formateur_nom' => 'Luc Bernard',
                'apprenants_count' => 12,
                'modules'       => [
                    ['titre' => 'Introduction Docker',   'contenu' => 'Images, conteneurs, Dockerfile et volumes.'],
                    ['titre' => 'Docker Compose',        'contenu' => 'Orchestration multi-services et réseaux.'],
                    ['titre' => 'CI/CD avec GitHub Actions', 'contenu' => 'Pipeline de build, test et déploiement automatisé.'],
                    ['titre' => 'Monitoring',            'contenu' => 'Logs, métriques et alertes avec Prometheus/Grafana.'],
                ],
            ],
            [
                'titre'         => 'Marketing Digital & Réseaux Sociaux',
                'description'   => 'Stratégie de contenu, SEO, publicité en ligne et analyse des performances. Développez votre présence digitale.',
                'category'      => 'Marketing',
                'date'          => '2026-05-20',
                'statut'        => 'Disponible',
                'price'         => 89.00,
                'duration'      => 20,
                'level'         => 'beginner',
                'vues'          => 389,
                'user_id'       => 1,
                'formateur_nom' => 'Emma Lefebvre',
                'apprenants_count' => 67,
                'modules'       => [
                    ['titre' => 'Stratégie de contenu', 'contenu' => 'Définir sa ligne éditoriale et son audience cible.'],
                    ['titre' => 'SEO & référencement',   'contenu' => 'Optimisation on-page, mots-clés et backlinks.'],
                    ['titre' => 'Publicité en ligne',    'contenu' => 'Google Ads, Meta Ads et mesure du ROI.'],
                ],
            ],
            [
                'titre'         => 'Java Spring Boot — API REST sécurisée',
                'description'   => 'Construisez une API REST complète avec Spring Boot, Spring Security, JWT et une base de données MySQL.',
                'category'      => 'Développement web',
                'date'          => '2026-05-05',
                'statut'        => 'Disponible',
                'price'         => 159.00,
                'duration'      => 45,
                'level'         => 'advanced',
                'vues'          => 241,
                'user_id'       => 1,
                'formateur_nom' => 'Pierre Leroy',
                'apprenants_count' => 23,
                'modules'       => [
                    ['titre' => 'Spring Boot Setup',    'contenu' => 'Initialisation du projet, dependencies Maven et structure.'],
                    ['titre' => 'JPA & MySQL',           'contenu' => 'Entités, repositories et migrations avec Liquibase.'],
                    ['titre' => 'API REST',              'contenu' => 'Controllers, DTOs, validation et codes HTTP.'],
                    ['titre' => 'Spring Security',       'contenu' => 'Authentification, autorisation et gestion des rôles.'],
                    ['titre' => 'Tests & Déploiement',   'contenu' => 'Tests unitaires avec JUnit et déploiement Docker.'],
                ],
            ],
        ];

        foreach ($formations as $data) {
            $modules = $data['modules'];
            unset($data['modules']);

            $formation = Formation::create($data);

            foreach (array_values($modules) as $index => $module) {
                Module::create([
                    'titre'        => $module['titre'],
                    'contenu'      => $module['contenu'],
                    'ordre'        => $index + 1,
                    'formation_id' => $formation->id,
                ]);
            }
        }
    }
}
