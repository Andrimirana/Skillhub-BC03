import axios from "axios";
import CryptoJS from "crypto-js";
import { recupererJeton, supprimerSession } from "./auth";

const apiAuth = axios.create({
  baseURL: import.meta.env.VITE_AUTH_URL || "http://127.0.0.1:8011/api",
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

export async function inscrire(nom, email, motDePasse, motDePasseConfirmation, role) {
  const reponse = await apiAuth.post("/register", {
    nom,
    email,
    password: motDePasse,
    passwordConfirm: motDePasseConfirmation,
    role,
  });
  return reponse.data;
}

export async function connecter(email, motDePasse) {
  const timestamp = Math.floor(Date.now() / 1000);
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  const nonce = "front_" + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

  // Protocole HMAC-SHA256 : clé = mot de passe, données = "email:nonce:timestamp"
  const hmac = CryptoJS.HmacSHA256(`${email}:${nonce}:${timestamp}`, motDePasse).toString(CryptoJS.enc.Base64);

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
