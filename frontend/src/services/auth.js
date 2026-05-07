const CLE_JETON = "jeton_auth";
const CLE_UTILISATEUR = "utilisateur_auth";

/**
 * Échappe les caractères spéciaux pour prévenir les attaques XSS
 * @param {string} str - La chaîne à échapper
 * @returns {string} La chaîne échappée
 */
function echapperHTML(str) {
  if (typeof str !== "string") return "";
  const map = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#x27;",
    "/": "&#x2F;",
  };
  return str.replace(/[&<>"'/]/g, (char) => map[char]);
}

export function sauvegarderSession(token, utilisateur) {
  if (typeof token !== "string" || !/^[\w-]+\.[\w-]+\.[\w-]+$/.test(token))
    return;
  localStorage.setItem(CLE_JETON, token);
  const utilisateurSanitise = {
    id: Number(utilisateur?.id) || 0,
    nom: echapperHTML(String(utilisateur?.nom || "")).slice(0, 255),
    email: echapperHTML(String(utilisateur?.email || "")).slice(0, 255),
    role: ["apprenant", "formateur", "admin"].includes(utilisateur?.role)
      ? utilisateur.role
      : "",
  };
  localStorage.setItem(CLE_UTILISATEUR, JSON.stringify(utilisateurSanitise));
}

export function recupererJeton() {
  return localStorage.getItem(CLE_JETON);
}

export function recupererUtilisateur() {
  const utilisateur = localStorage.getItem(CLE_UTILISATEUR);

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
  localStorage.removeItem(CLE_JETON);
  localStorage.removeItem(CLE_UTILISATEUR);
}

export function estConnecte() {
  return Boolean(recupererJeton());
}
