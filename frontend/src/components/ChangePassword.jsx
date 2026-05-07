import { useState } from "react";
import { recupererJeton } from "../services/auth";
import axios from "axios";

export default function ChangePassword() {
  const [ancienPassword, setAncienPassword] = useState("");
  const [nouveauPassword, setNouveauPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [message, setMessage] = useState("");
  const [erreur, setErreur] = useState("");
  const [chargement, setChargement] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage("");
    setErreur("");

    if (nouveauPassword !== confirmPassword) {
      setErreur("Les nouveaux mots de passe ne correspondent pas.");
      return;
    }

    setChargement(true);
    try {
      await axios.put(
        `${import.meta.env.VITE_AUTH_URL || "http://127.0.0.1:8011/api"}/change-password`,
        { oldPassword: ancienPassword, newPassword: nouveauPassword, confirmPassword },
        { headers: { Authorization: `Bearer ${recupererJeton()}`, "Content-Type": "application/json" } }
      );
      setMessage("Mot de passe modifié avec succès !");
      setAncienPassword("");
      setNouveauPassword("");
      setConfirmPassword("");
    } catch (error) {
      setErreur(error.response?.data?.message || "Erreur lors de la modification.");
    } finally {
      setChargement(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      {[
        { label: "Ancien mot de passe", value: ancienPassword, setter: setAncienPassword, placeholder: "Votre mot de passe actuel" },
        { label: "Nouveau mot de passe", value: nouveauPassword, setter: setNouveauPassword, placeholder: "12 car. min, 1 maj, 1 chiffre, 1 spécial" },
        { label: "Confirmer le nouveau mot de passe", value: confirmPassword, setter: setConfirmPassword, placeholder: "Répéter le nouveau mot de passe" },
      ].map(({ label, value, setter, placeholder }) => (
        <label key={label} style={{ display: "flex", flexDirection: "column", gap: 6, fontSize: "0.88rem", fontWeight: 600, color: "#223c6f" }}>
          {label}
          <input
            type="password"
            value={value}
            placeholder={placeholder}
            onChange={(e) => setter(e.target.value)}
            required
            style={{
              border: "1px solid #d3ddf8", borderRadius: 10, padding: "10px 12px",
              fontSize: "0.9rem", fontFamily: "inherit", transition: "border-color 0.2s, box-shadow 0.2s",
              outline: "none"
            }}
            onFocus={e => { e.target.style.borderColor = "#623cde"; e.target.style.boxShadow = "0 0 0 3px rgba(98,60,222,0.12)"; }}
            onBlur={e => { e.target.style.borderColor = "#d3ddf8"; e.target.style.boxShadow = "none"; }}
          />
        </label>
      ))}

      {erreur && (
        <div style={{ background: "#fff3f4", border: "1px solid #ffd6da", borderRadius: 10, padding: "10px 14px", color: "#c5313e", fontSize: "0.87rem" }}>
          {erreur}
        </div>
      )}
      {message && (
        <div style={{ background: "#e7f7ee", border: "1px solid #bfe9cf", borderRadius: 10, padding: "10px 14px", color: "#157347", fontSize: "0.87rem" }}>
          {message}
        </div>
      )}

      <button
        type="submit"
        disabled={chargement}
        style={{
          border: 0, borderRadius: 10, padding: "11px 0",
          background: chargement ? "#9b85e8" : "linear-gradient(135deg, #623cde, #4a69c0)",
          color: "#fff", fontWeight: 600, fontSize: "0.95rem",
          cursor: chargement ? "not-allowed" : "pointer",
          transition: "opacity 0.2s, transform 0.2s"
        }}
      >
        {chargement ? "Modification en cours…" : "Enregistrer le nouveau mot de passe"}
      </button>
    </form>
  );
}
