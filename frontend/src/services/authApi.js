import axios from "axios";
import CryptoJS from "crypto-js";
import { recupererJeton, supprimerSession } from "./auth";

const apiAuth = axios.create({
  baseURL: import.meta.env.VITE_AUTH_URL || "http://127.0.0.1:8011/api",
  timeout: 10000,
  headers: { "Content-Type": "application/json" },
});

apiAuth.interceptors.request.use((config) => {
  const jeton = recupererJeton();
  if (jeton) config.headers.Authorization = `Bearer ${jeton}`;
  return config;
});

apiAuth.interceptors.response.use(
  (reponse) => reponse,
  (erreur) => {
    if (erreur.response?.status === 401) supprimerSession();
    return Promise.reject(erreur);
  },
);

/**
 * Inscription — envoie nom, email, mot de passe (+ confirmation) et rôle
 * au service Java Auth_TP1.
 */
export async function inscrire(nom, email, motDePasse, role) {
  const reponse = await apiAuth.post("/register", {
    nom,
    email,
    password: motDePasse,
    passwordConfirm: motDePasse,
    role,
  });
  return reponse.data;
}

/**
 * Connexion HMAC-SHA256 (protocole Auth_TP1) :
 *   1. Le client génère un nonce UUID et un timestamp Unix.
 *   2. Il calcule HMAC_SHA256(clé=mot_de_passe, data="email:nonce:timestamp") en Base64.
 *   3. Le serveur reçoit {email, nonce, timestamp, hmac} et vérifie le HMAC
 *      en recalculant avec le mot de passe déchiffré — le mot de passe ne circule jamais.
 */
export async function connecter(email, motDePasse) {
  const nonce     = crypto.randomUUID();
  const timestamp = Math.floor(Date.now() / 1000);
  const message   = `${email}:${nonce}:${timestamp}`;

  // HMAC-SHA256 encodé en Base64 standard (identique à Java Base64.getEncoder())
  const hmac = CryptoJS.HmacSHA256(message, motDePasse).toString(CryptoJS.enc.Base64);

  const reponse = await apiAuth.post("/login", { email, nonce, timestamp, hmac });
  return reponse.data;
}

export async function profilConnecte() {
  const reponse = await apiAuth.get("/profil");
  return reponse.data;
}

export async function deconnecter() {
  await apiAuth.post("/logout");
}
