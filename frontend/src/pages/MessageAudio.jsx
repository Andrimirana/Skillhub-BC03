import { useEffect, useRef, useState } from "react";
import {
  compteurNonLus,
  envoyerMessage,
  listerConversations,
  listerMessages,
  listerUtilisateurs,
  supprimerMessage,
} from "../services/audioApi";
import { recupererUtilisateur } from "../services/auth";

const STATUTS = { IDLE: "idle", ENREGISTREMENT: "enregistrement", ENVOI: "envoi" };

export default function MessageAudio() {
  const utilisateur = recupererUtilisateur();

  const [utilisateurs, setUtilisateurs] = useState([]);
  const [conversations, setConversations] = useState([]);
  const [destinataireId, setDestinataireId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [nonLus, setNonLus] = useState(0);
  const [statut, setStatut] = useState(STATUTS.IDLE);
  const [erreur, setErreur] = useState("");
  const [dureeEnreg, setDureeEnreg] = useState(0);

  const mediaRecorderRef = useRef(null);
  const chunksRef = useRef([]);
  const intervalRef = useRef(null);
  const messagesEndRef = useRef(null);

  // Chargement initial
  useEffect(() => {
    chargerDonnees();
  }, []);

  // Charger les messages quand on change de destinataire
  useEffect(() => {
    if (destinataireId) {
      chargerMessages(destinataireId);
    }
  }, [destinataireId]);

  // Scroll automatique vers le dernier message
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  async function chargerDonnees() {
    try {
      const [users, convs, count] = await Promise.all([
        listerUtilisateurs(),
        listerConversations(),
        compteurNonLus(),
      ]);
      setUtilisateurs(users.filter((u) => u.id !== utilisateur?.id));
      setConversations(convs);
      setNonLus(count);
    } catch {
      setErreur("Impossible de charger les données.");
    }
  }

  async function chargerMessages(autreId) {
    try {
      const msgs = await listerMessages(autreId);
      setMessages(msgs);
    } catch {
      setErreur("Impossible de charger les messages.");
    }
  }

  async function demarrerEnregistrement() {
    setErreur("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (e) => chunksRef.current.push(e.data);
      recorder.start();
      mediaRecorderRef.current = recorder;
      setStatut(STATUTS.ENREGISTREMENT);
      setDureeEnreg(0);
      intervalRef.current = setInterval(() => setDureeEnreg((d) => d + 1), 1000);
    } catch {
      setErreur("Impossible d'accéder au microphone.");
    }
  }

  async function arreterEtEnvoyer() {
    if (!destinataireId) {
      setErreur("Sélectionnez un destinataire avant d'envoyer.");
      return;
    }
    clearInterval(intervalRef.current);
    setStatut(STATUTS.ENVOI);

    const recorder = mediaRecorderRef.current;
    recorder.onstop = async () => {
      const blob = new Blob(chunksRef.current, { type: "audio/webm" });
      recorder.stream.getTracks().forEach((t) => t.stop());

      const reader = new FileReader();
      reader.onloadend = async () => {
        try {
          await envoyerMessage(destinataireId, reader.result, {
            nomFichier: `audio_${Date.now()}.webm`,
            duree: dureeEnreg,
            taille: blob.size,
          });
          await chargerMessages(destinataireId);
          await chargerDonnees();
        } catch {
          setErreur("Échec de l'envoi du message audio.");
        } finally {
          setStatut(STATUTS.IDLE);
          setDureeEnreg(0);
        }
      };
      reader.readAsDataURL(blob);
    };
    recorder.stop();
  }

  async function annulerEnregistrement() {
    clearInterval(intervalRef.current);
    mediaRecorderRef.current?.stream?.getTracks().forEach((t) => t.stop());
    setStatut(STATUTS.IDLE);
    setDureeEnreg(0);
  }

  async function handleSupprimerMessage(id) {
    try {
      await supprimerMessage(id);
      setMessages((prev) => prev.filter((m) => m.id !== id));
    } catch {
      setErreur("Impossible de supprimer le message.");
    }
  }

  function nomUtilisateur(id) {
    const u = utilisateurs.find((u) => u.id === id);
    return u ? u.nom : `Utilisateur #${id}`;
  }

  function formaterDuree(sec) {
    const m = Math.floor(sec / 60).toString().padStart(2, "0");
    const s = (sec % 60).toString().padStart(2, "0");
    return `${m}:${s}`;
  }

  return (
    <div style={{ display: "flex", height: "calc(100vh - 60px)", fontFamily: "sans-serif" }}>
      {/* Panneau gauche — utilisateurs / conversations */}
      <aside style={{ width: 260, borderRight: "1px solid #e0e0e0", overflowY: "auto", background: "#f8f9fa" }}>
        <div style={{ padding: "16px 12px 8px", fontWeight: 700, fontSize: 15, borderBottom: "1px solid #e0e0e0" }}>
          Messages audio
          {nonLus > 0 && (
            <span style={{ marginLeft: 8, background: "#e53e3e", color: "#fff", borderRadius: 10, padding: "2px 8px", fontSize: 12 }}>
              {nonLus}
            </span>
          )}
        </div>

        <div style={{ padding: "8px 12px 4px", fontSize: 12, color: "#888", fontWeight: 600, textTransform: "uppercase" }}>
          Utilisateurs
        </div>
        {utilisateurs.length === 0 && (
          <div style={{ padding: "8px 12px", color: "#aaa", fontSize: 13 }}>Aucun autre utilisateur</div>
        )}
        {utilisateurs.map((u) => {
          const conv = conversations.find((c) => c.autre_id === u.id);
          return (
            <button
              key={u.id}
              onClick={() => setDestinataireId(u.id)}
              style={{
                display: "block", width: "100%", textAlign: "left",
                padding: "10px 12px", border: "none", cursor: "pointer",
                background: destinataireId === u.id ? "#e8f0fe" : "transparent",
                borderLeft: destinataireId === u.id ? "3px solid #4a90e2" : "3px solid transparent",
              }}
            >
              <div style={{ fontWeight: 600, fontSize: 14 }}>{u.nom}</div>
              <div style={{ fontSize: 12, color: "#888" }}>{u.email}</div>
              {conv?.messages_non_lus > 0 && (
                <span style={{ background: "#e53e3e", color: "#fff", borderRadius: 8, padding: "1px 6px", fontSize: 11 }}>
                  {conv.messages_non_lus} non lu{conv.messages_non_lus > 1 ? "s" : ""}
                </span>
              )}
            </button>
          );
        })}
      </aside>

      {/* Zone principale */}
      <main style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        {!destinataireId ? (
          <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", color: "#aaa" }}>
            Sélectionnez un utilisateur pour commencer une conversation audio
          </div>
        ) : (
          <>
            {/* En-tête */}
            <div style={{ padding: "12px 16px", borderBottom: "1px solid #e0e0e0", fontWeight: 700, fontSize: 16 }}>
              {nomUtilisateur(destinataireId)}
            </div>

            {/* Messages */}
            <div style={{ flex: 1, overflowY: "auto", padding: 16 }}>
              {erreur && (
                <div style={{ background: "#fff5f5", border: "1px solid #feb2b2", borderRadius: 6, padding: "8px 12px", marginBottom: 12, color: "#c53030", fontSize: 13 }}>
                  {erreur}
                </div>
              )}
              {messages.length === 0 && (
                <div style={{ color: "#aaa", textAlign: "center", marginTop: 40 }}>
                  Aucun message. Enregistrez et envoyez un message audio !
                </div>
              )}
              {messages.map((msg) => {
                const estMien = msg.id_utilisateur === utilisateur?.id;
                return (
                  <div
                    key={msg.id}
                    style={{
                      display: "flex", justifyContent: estMien ? "flex-end" : "flex-start",
                      marginBottom: 12,
                    }}
                  >
                    <div style={{
                      maxWidth: "60%", background: estMien ? "#4a90e2" : "#f0f0f0",
                      color: estMien ? "#fff" : "#333", borderRadius: 12, padding: "10px 14px",
                      fontSize: 13,
                    }}>
                      <div style={{ fontWeight: 600, marginBottom: 4, fontSize: 12 }}>
                        {estMien ? "Vous" : nomUtilisateur(msg.id_utilisateur)}
                      </div>
                      <div>
                        {msg.nom_fichier ?? "Message audio"}
                        {msg.duree_secondes > 0 && (
                          <span style={{ marginLeft: 8, opacity: 0.75 }}>({formaterDuree(msg.duree_secondes)})</span>
                        )}
                      </div>
                      {msg.donnees_audio && (
                        <audio controls src={msg.donnees_audio} style={{ marginTop: 6, width: "100%" }} />
                      )}
                      <div style={{ fontSize: 10, marginTop: 4, opacity: 0.7, textAlign: "right" }}>
                        {new Date(msg.date_creation).toLocaleString("fr-FR", { hour: "2-digit", minute: "2-digit" })}
                        {!estMien && msg.statut_lecture ? " (lu)" : ""}
                      </div>
                      {estMien && (
                        <button
                          onClick={() => handleSupprimerMessage(msg.id)}
                          style={{ background: "none", border: "none", color: "rgba(255,255,255,0.7)", cursor: "pointer", fontSize: 11, marginTop: 2 }}
                        >
                          Supprimer
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
              <div ref={messagesEndRef} />
            </div>

            {/* Barre d'enregistrement */}
            <div style={{ padding: "12px 16px", borderTop: "1px solid #e0e0e0", background: "#fafafa", display: "flex", alignItems: "center", gap: 10 }}>
              {statut === STATUTS.IDLE && (
                <button
                  onClick={demarrerEnregistrement}
                  style={{ background: "#4a90e2", color: "#fff", border: "none", borderRadius: 24, padding: "10px 20px", cursor: "pointer", fontWeight: 600 }}
                >
                  Enregistrer
                </button>
              )}
              {statut === STATUTS.ENREGISTREMENT && (
                <>
                  <span style={{ color: "#e53e3e", fontWeight: 700, fontSize: 14 }}>
                    ● {formaterDuree(dureeEnreg)}
                  </span>
                  <button
                    onClick={arreterEtEnvoyer}
                    style={{ background: "#38a169", color: "#fff", border: "none", borderRadius: 24, padding: "10px 20px", cursor: "pointer", fontWeight: 600 }}
                  >
                    Envoyer
                  </button>
                  <button
                    onClick={annulerEnregistrement}
                    style={{ background: "#e53e3e", color: "#fff", border: "none", borderRadius: 24, padding: "10px 16px", cursor: "pointer" }}
                  >
                    Annuler
                  </button>
                </>
              )}
              {statut === STATUTS.ENVOI && (
                <span style={{ color: "#888" }}>Envoi en cours…</span>
              )}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
