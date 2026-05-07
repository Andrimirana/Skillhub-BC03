<?php
/**
 * SkillHub – Audio Service
 * Point d'entrée PHP. Valide les tokens via le service Auth (validate-token)
 * Chiffre les données audio avec AES-256-CBC.
 */

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *'); // NOSONAR — service interne, accessible uniquement depuis le réseau Docker
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$requestUri    = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$requestMethod = $_SERVER['REQUEST_METHOD'];


// Health check (no auth required)
// ──────────────────────────────────────────────
if ($requestUri === '/api/health' && $requestMethod === 'GET') {
    echo json_encode(['status' => 'UP', 'service' => 'audio-service']);
    exit;
}

// ──────────────────────────────────────────────
// Token validation via Auth service
// ──────────────────────────────────────────────
function validateToken(string $token): ?array
{
    $authUrl = getenv('AUTH_SERVICE_URL') ?: 'http://auth-service:8080'; // NOSONAR — réseau Docker interne, HTTPS non requis
    $ch = curl_init("{$authUrl}/api/validate-token");
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_POST           => true,
        CURLOPT_HTTPHEADER     => ["Authorization: Bearer {$token}", 'Content-Type: application/json'],
        CURLOPT_TIMEOUT        => 5,
    ]);
    $body   = curl_exec($ch);
    $status = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($status !== 200 || !$body) {
        return null;
    }
    $data = json_decode($body, true);
    return ($data['valid'] ?? false) ? ($data['user'] ?? null) : null;
}

// Extract bearer token from Authorization header
$headers = getallheaders();
$rawToken = null;
foreach ($headers as $name => $value) {
    if (strtolower($name) === 'authorization') {
        if (preg_match('/Bearer\s+(.+)$/i', $value, $m)) {
            $rawToken = trim($m[1]);
        }
        break;
    }
}

if (!$rawToken) {
    http_response_code(401);
    echo json_encode(['message' => 'Token manquant']);
    exit;
}

$authUser = validateToken($rawToken);
if (!$authUser) {
    http_response_code(401);
    echo json_encode(['message' => 'Token invalide ou expiré']);
    exit;
}

$userId = (int) $authUser['id'];

// ──────────────────────────────────────────────
// Database connection
// ──────────────────────────────────────────────
try {
    $db = new PDO(
        sprintf(
            'mysql:host=%s;port=%s;dbname=%s;charset=utf8mb4',
            getenv('DB_HOST')     ?: 'db',
            getenv('DB_PORT')     ?: '3306',
            getenv('DB_DATABASE') ?: 'skillhub_audio'
        ),
        getenv('DB_USERNAME') ?: 'skillhub_user',
        getenv('DB_PASSWORD') ?: 'skillhub_pass'
    );
    $db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
} catch (PDOException $e) {
    http_response_code(503);
    echo json_encode(['message' => 'Connexion base de données impossible']);
    exit;
}

// ──────────────────────────────────────────────
// AES-256-CBC helpers
// ──────────────────────────────────────────────
function encryptAES256(string $data): string
{
    $key    = str_pad(getenv('ENCRYPTION_KEY') ?: 'skillhub-audio-default-key-32!!', 32);
    $iv     = openssl_random_pseudo_bytes(16);
    $cipher = openssl_encrypt($data, 'aes-256-cbc', $key, 0, $iv);
    return base64_encode($iv . '::' . $cipher);
}

function decryptAES256(string $data): string
{
    $key  = str_pad(getenv('ENCRYPTION_KEY') ?: 'skillhub-audio-default-key-32!!', 32);
    $raw  = base64_decode($data);
    [$iv, $cipher] = explode('::', $raw, 2);
    return openssl_decrypt($cipher, 'aes-256-cbc', $key, 0, $iv);
}

// ──────────────────────────────────────────────
// GET /api/users — liste des utilisateurs (via Auth service)
// ──────────────────────────────────────────────
if ($requestUri === '/api/users' && $requestMethod === 'GET') {
    $authUrl = getenv('AUTH_SERVICE_URL') ?: 'http://auth-service:8080'; // NOSONAR — réseau Docker interne
    $ch = curl_init("{$authUrl}/api/users");
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => ["Authorization: Bearer {$rawToken}"],
        CURLOPT_TIMEOUT        => 5,
    ]);
    $body   = curl_exec($ch);
    $status = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($status === 200 && $body) {
        echo $body;
    } else {
        echo json_encode(['utilisateurs' => []]);
    }
    exit;
}

// ──────────────────────────────────────────────
// GET /api/conversations
// ──────────────────────────────────────────────
if ($requestUri === '/api/conversations' && $requestMethod === 'GET') {
    $stmt = $db->prepare('
        SELECT DISTINCT
            IF(e.id_utilisateur = :uid, e.id_destinataire, e.id_utilisateur) AS autre_id,
            (SELECT COUNT(*) FROM enregistrements_audio
             WHERE id_destinataire = :uid2 AND id_utilisateur = IF(e.id_utilisateur = :uid3, e.id_destinataire, e.id_utilisateur)
             AND statut_lecture = FALSE) AS messages_non_lus,
            MAX(e.date_creation) AS dernier_message_date
        FROM enregistrements_audio e
        WHERE e.id_utilisateur = :uid4 OR e.id_destinataire = :uid5
        GROUP BY autre_id
        ORDER BY dernier_message_date DESC
    ');
    $stmt->execute([
        ':uid'  => $userId, ':uid2' => $userId, ':uid3' => $userId,
        ':uid4' => $userId, ':uid5' => $userId,
    ]);
    echo json_encode(['conversations' => $stmt->fetchAll()]);
    exit;
}

// ──────────────────────────────────────────────
// GET /api/messages/non-lus/count
// ──────────────────────────────────────────────
if ($requestUri === '/api/messages/non-lus/count' && $requestMethod === 'GET') {
    $stmt = $db->prepare('SELECT COUNT(*) AS total FROM enregistrements_audio WHERE id_destinataire = ? AND statut_lecture = FALSE');
    $stmt->execute([$userId]);
    echo json_encode(['total_non_lus' => (int) $stmt->fetchColumn()]);
    exit;
}

// ──────────────────────────────────────────────
// GET /api/messages/{userId}
// ──────────────────────────────────────────────
if (preg_match('#^/api/messages/(\d+)$#', $requestUri, $m) && $requestMethod === 'GET') {
    $autreId = (int) $m[1];
    $stmt = $db->prepare('
        SELECT id, id_utilisateur, id_destinataire, nom_fichier, duree_secondes,
               taille_octets, statut_lecture, date_lecture, date_creation,
               hash_sha256,
               IF(id_destinataire = :uid, 1, 0) AS est_recu
        FROM enregistrements_audio
        WHERE (id_utilisateur = :uid2 AND id_destinataire = :autre)
           OR (id_utilisateur = :autre2 AND id_destinataire = :uid3)
        ORDER BY date_creation ASC
    ');
    $stmt->execute([':uid' => $userId, ':uid2' => $userId, ':autre' => $autreId, ':autre2' => $autreId, ':uid3' => $userId]);
    $messages = $stmt->fetchAll();

    // Déchiffrer les données pour les messages reçus
    foreach ($messages as &$msg) {
        if ($msg['est_recu']) {
            $row = $db->prepare('SELECT donnees_chiffrees FROM enregistrements_audio WHERE id = ?');
            $row->execute([$msg['id']]);
            $enc = $row->fetchColumn();
            if ($enc) {
                $msg['donnees_audio'] = decryptAES256($enc);
            }
        }
        unset($msg['est_recu']);
    }
    unset($msg);

    // Marquer comme lus
    $upd = $db->prepare('UPDATE enregistrements_audio SET statut_lecture = TRUE, date_lecture = NOW() WHERE id_destinataire = ? AND id_utilisateur = ? AND statut_lecture = FALSE');
    $upd->execute([$userId, $autreId]);

    echo json_encode(['messages' => $messages]);
    exit;
}

// ──────────────────────────────────────────────
// POST /api/messages — envoyer un message audio
// ──────────────────────────────────────────────
if ($requestUri === '/api/messages' && $requestMethod === 'POST') {
    $data = json_decode(file_get_contents('php://input'), true) ?? [];

    if (empty($data['id_destinataire']) || empty($data['donnees_audio'])) {
        http_response_code(400);
        echo json_encode(['message' => 'Destinataire et données audio requis']);
        exit;
    }

    $idDest   = (int) $data['id_destinataire'];
    $audio    = $data['donnees_audio'];
    $nomF     = $data['nom_fichier']       ?? ('audio_' . time() . '.webm');
    $duree    = (int) ($data['duree_secondes'] ?? 0);
    $taille   = (int) ($data['taille_octets']  ?? strlen($audio));

    $chiffre = encryptAES256($audio);
    $hash    = hash('sha256', $audio);

    $ins = $db->prepare('
        INSERT INTO enregistrements_audio
            (id_utilisateur, id_destinataire, donnees_chiffrees, hash_sha256, nom_fichier, duree_secondes, taille_octets)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    ');
    $ins->execute([$userId, $idDest, $chiffre, $hash, $nomF, $duree, $taille]);
    $newId = $db->lastInsertId();

    $sel = $db->prepare('SELECT id, id_utilisateur, id_destinataire, nom_fichier, duree_secondes, taille_octets, statut_lecture, date_creation FROM enregistrements_audio WHERE id = ?');
    $sel->execute([$newId]);

    http_response_code(201);
    echo json_encode(['message' => 'Message envoyé avec succès', 'donnees' => $sel->fetch()]);
    exit;
}

// ──────────────────────────────────────────────
// PUT /api/messages/{id}/lu
// ──────────────────────────────────────────────
if (preg_match('#^/api/messages/(\d+)/lu$#', $requestUri, $m) && $requestMethod === 'PUT') {
    $msgId = (int) $m[1];
    $stmt  = $db->prepare('UPDATE enregistrements_audio SET statut_lecture = TRUE, date_lecture = NOW() WHERE id = ? AND id_destinataire = ?');
    $stmt->execute([$msgId, $userId]);
    echo json_encode(['message' => 'Message marqué comme lu']);
    exit;
}

// ──────────────────────────────────────────────
// DELETE /api/messages/{id}
// ──────────────────────────────────────────────
if (preg_match('#^/api/messages/(\d+)$#', $requestUri, $m) && $requestMethod === 'DELETE') {
    $msgId = (int) $m[1];
    $stmt  = $db->prepare('DELETE FROM enregistrements_audio WHERE id = ? AND id_utilisateur = ?');
    $stmt->execute([$msgId, $userId]);
    if ($stmt->rowCount() > 0) {
        echo json_encode(['message' => 'Message supprimé avec succès']);
    } else {
        http_response_code(404);
        echo json_encode(['message' => 'Message introuvable ou non autorisé']);
    }
    exit;
}

// ──────────────────────────────────────────────
// 404
// ──────────────────────────────────────────────
http_response_code(404);
echo json_encode(['message' => 'Route non trouvée']);
