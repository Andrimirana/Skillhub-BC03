const CLE_JETON = "jeton_auth";
const CLE_UTILISATEUR = "utilisateur_auth";

// Nettoyage de l'ancienne session stockée dans localStorage
localStorage.removeItem(CLE_JETON);
localStorage.removeItem(CLE_UTILISATEUR);

export function sauvegarderSession(token, utilisateur) {
  // Accepte les tokens UUID (Auth_TP1 Spring Boot) et JWT
  if (typeof token !== 'string' || token.trim().length < 10) return;
  try {
    sessionStorage.setItem(CLE_JETON, token);
    sessionStorage.setItem(CLE_UTILISATEUR, JSON.stringify(utilisateur));
  } catch {
    console.error("Impossible de sauvegarder la session (stockage indisponible).");
  }
}

export function recupererJeton() {
  return sessionStorage.getItem(CLE_JETON);
}

export function recupererUtilisateur() {
  const utilisateur = sessionStorage.getItem(CLE_UTILISATEUR);

  if (!utilisateur) {
    return null;
  }

  try {
    return JSON.parse(utilisateur);
  } catch {
    return null;
  }
}

export function supprimerSession() {
  sessionStorage.removeItem(CLE_JETON);
  sessionStorage.removeItem(CLE_UTILISATEUR);
}

export function estConnecte() {
  return Boolean(recupererJeton());
}
