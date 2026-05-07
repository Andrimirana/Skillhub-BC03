import axios from "axios";
import { recupererJeton, supprimerSession } from "./auth";

const audioApi = axios.create({
  baseURL: import.meta.env.VITE_AUDIO_URL || "http://127.0.0.1:8014/api",
  headers: { "Content-Type": "application/json" },
});

audioApi.interceptors.request.use((config) => {
  const jeton = recupererJeton();
  if (jeton) config.headers.Authorization = `Bearer ${jeton}`;
  return config;
});

audioApi.interceptors.response.use(
  (reponse) => reponse,
  (erreur) => {
    if (erreur.response?.status === 401) supprimerSession();
    return Promise.reject(erreur);
  },
);

export async function listerUtilisateurs() {
  const { data } = await audioApi.get("/users");
  return data.utilisateurs ?? [];
}

export async function listerConversations() {
  const { data } = await audioApi.get("/conversations");
  return data.conversations ?? [];
}

export async function listerMessages(autreUserId) {
  const { data } = await audioApi.get(`/messages/${autreUserId}`);
  return data.messages ?? [];
}

export async function envoyerMessage(idDestinataire, donneesAudio, options = {}) {
  const { data } = await audioApi.post("/messages", {
    id_destinataire: idDestinataire,
    donnees_audio: donneesAudio,
    nom_fichier: options.nomFichier ?? `audio_${Date.now()}.webm`,
    duree_secondes: options.duree ?? 0,
    taille_octets: options.taille ?? donneesAudio.length,
  });
  return data;
}

export async function marquerLu(messageId) {
  const { data } = await audioApi.put(`/messages/${messageId}/lu`);
  return data;
}

export async function supprimerMessage(messageId) {
  const { data } = await audioApi.delete(`/messages/${messageId}`);
  return data;
}

export async function compteurNonLus() {
  const { data } = await audioApi.get("/messages/non-lus/count");
  return data.total_non_lus ?? 0;
}

export default audioApi;
