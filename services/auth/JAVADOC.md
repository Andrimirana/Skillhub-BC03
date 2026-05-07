# Documentation JavaDoc - Service d'Authentification

## Vue d'ensemble

JavaDoc complète générée pour le service d'authentification du projet SkillHub BC03.

**Date de génération** : 7 mai 2026  
**Version** : 0.0.1-SNAPSHOT  
**Java** : 17  
**Framework** : Spring Boot 3.2.5

---

## Statistiques du Code Documenté

| Package        | Nombre de Classes | Description                                |
| -------------- | ----------------- | ------------------------------------------ |
| **controller** | 3 classes         | Contrôleurs REST API                       |
| **service**    | 5 classes         | Logique métier et services                 |
| **repository** | 3 classes         | Accès aux données (JPA)                    |
| **entity**     | 3 classes         | Entités JPA (User, AccessToken, AuthNonce) |
| **dto**        | 7 classes         | Objets de transfert de données             |
| **config**     | 1 classe          | Configuration Spring Security              |
| **exception**  | 4 classes         | Gestion des exceptions personnalisées      |

**Total** : **27 fichiers Java** documentés

---

## Accès à la Documentation

### **Chemin local**

```
services/auth/target/site/apidocs/index.html
```

### **Ouvrir dans le navigateur**

Double-cliquez sur le fichier `index.html` ou utilisez la commande :

```powershell
cd services/auth
Start-Process target\site\apidocs\index.html
```

---

## Régénération de la JavaDoc

### **Commande Maven**

```bash
cd services/auth
mvn javadoc:javadoc -DskipTests
```

### **Commande avec Maven Wrapper**

```bash
cd services/auth
./mvnw clean javadoc:javadoc
```

### **Générer avec les tests**

```bash
mvn clean install javadoc:javadoc
```

---

## Structure de la Documentation

```
target/site/apidocs/
├── index.html                          # Page d'accueil de la JavaDoc
├── allclasses-index.html               # Index de toutes les classes
├── allpackages-index.html              # Index de tous les packages
├── constant-values.html                # Valeurs constantes
├── index-all.html                      # Index alphabétique complet
├── com/example/auth/
│   ├── controller/                     # Documentation des contrôleurs
│   │   ├── AuthController.html
│   │   ├── SkillhubController.html
│   │   └── UserController.html
│   ├── service/                        # Documentation des services
│   │   ├── AuthService.html
│   │   ├── TokenService.html
│   │   ├── HmacService.html
│   │   ├── MasterKeyService.html
│   │   └── PasswordPolicyValidator.html
│   ├── repository/                     # Documentation des repositories
│   │   ├── UserRepository.html
│   │   ├── AccessTokenRepository.html
│   │   └── AuthNonceRepository.html
│   ├── entity/                         # Documentation des entités
│   │   ├── User.html
│   │   ├── AccessToken.html
│   │   └── AuthNonce.html
│   ├── dto/                            # Documentation des DTOs
│   │   ├── LoginRequest.html
│   │   ├── LoginResponse.html
│   │   ├── RegisterRequest.html
│   │   ├── SkillhubAuthResponse.html
│   │   ├── SkillhubRegisterRequest.html
│   │   ├── ChangePasswordRequest.html
│   │   └── UtilisateurInfo.html
│   ├── config/                         # Documentation de la configuration
│   │   └── SecurityConfig.html
│   └── exception/                      # Documentation des exceptions
│       ├── GlobalExceptionHandler.html
│       ├── AuthenticationFailedException.html
│       ├── ResourceConflictException.html
│       └── ...
└── resources/                          # CSS et ressources statiques
```

---

## Principales Classes Documentées

### **1. AuthController**

**Package** : `com.example.auth.controller`  
**Description** : Contrôleur REST pour l'authentification (login)  
**Endpoints** :

- `POST /api/login` - Connexion utilisateur avec HMAC

### **2. SkillhubController**

**Package** : `com.example.auth.controller`  
**Description** : API SkillHub pour les microservices  
**Endpoints** :

- `POST /api/skillhub/register` - Inscription via autre service
- `POST /api/skillhub/auth` - Authentification inter-services

### **3. UserController**

**Package** : `com.example.auth.controller`  
**Description** : Gestion des utilisateurs  
**Endpoints** :

- `POST /api/register` - Inscription utilisateur
- `POST /api/users/change-password` - Changement de mot de passe

### **4. AuthService**

**Package** : `com.example.auth.service`  
**Description** : Service métier principal pour l'authentification  
**Méthodes clés** :

- `authenticate(LoginRequest)` - Authentification complète
- `generateAccessToken(User)` - Génération de JWT
- `validateToken(String)` - Validation de token

### **5. TokenService**

**Package** : `com.example.auth.service`  
**Description** : Génération et validation des tokens JWT  
**Caractéristiques** :

- Signature HMAC-SHA256
- Expiration 15 minutes
- Claims personnalisés (userId, email, prenom, nom)

### **6. HmacService**

**Package** : `com.example.auth.service`  
**Description** : Service de calcul HMAC-SHA256  
**Méthodes** :

- `calculateHmac(message, key)` - Calcul HMAC
- `verifyHmac(message, signature, key)` - Vérification

### **7. User (Entity)**

**Package** : `com.example.auth.entity`  
**Description** : Entité JPA représentant un utilisateur  
**Champs** :

- `id`, `email`, `password`, `nom`, `prenom`
- `createdAt`, `updatedAt`

---

## Sécurité et Authentification

### **Architecture HMAC-SHA256**

Le service utilise une authentification par signature HMAC :

1. **Clé maîtresse** : Générée au démarrage (256 bits)
2. **Nonce** : Nombre aléatoire pour éviter les attaques par rejeu
3. **Signature** : HMAC-SHA256(nonce + password, masterKey)
4. **Token JWT** : Généré après authentification réussie

### **Flux d'authentification**

```
Client → GET /api/nonce/{email}
       ← nonce

Client → Calcul HMAC = SHA256(nonce + password)
       → POST /api/login {email, hmacSignature}
       ← JWT token (15 min)

Client → Utilise Bearer token pour les requêtes suivantes
```

---

## Configuration de la JavaDoc

La configuration dans `pom.xml` :

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.3</version>
    <configuration>
        <show>public</show>
        <nohelp>true</nohelp>
        <encoding>UTF-8</encoding>
        <charset>UTF-8</charset>
        <docencoding>UTF-8</docencoding>
        <failOnError>false</failOnError>
        <additionalOptions>
            <additionalOption>-Xdoclint:none</additionalOption>
        </additionalOptions>
    </configuration>
</plugin>
```

---

## Ressources Complémentaires

- **README principal** : `../../README.md`
- **Documentation technique** : `../../REFERENCE_TECHNIQUE.md`

---

## Maintenance

### **Mise à jour de la documentation**

Après modification du code source :

```bash
mvn clean compile javadoc:javadoc
```

### **Génération avec rapport de couverture**

```bash
mvn clean test jacoco:report javadoc:javadoc
```

### **Déploiement de la JavaDoc**

```bash
mvn clean deploy site:site site:deploy
```

---

## Vérification

Pour vérifier que la JavaDoc a été correctement générée :

```powershell
# Vérifier l'existence du répertoire
Test-Path target\site\apidocs

# Lister les fichiers HTML générés
Get-ChildItem target\site\apidocs -Filter *.html | Select-Object Name

# Compter les classes documentées
(Get-ChildItem target\site\apidocs -Recurse -Filter *.html).Count
```

---

## Contact / Support

Pour toute question concernant la documentation :

- **Projet** : SkillHub BC03
- **Repository** : Andrimirana/Skillhub-BC03
- **Branch** : dev

---

**Dernière mise à jour** : 7 mai 2026  
**Généré par** : Maven JavaDoc Plugin 3.6.3
