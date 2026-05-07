import ChangePassword from "../components/ChangePassword";
import Topbar from "../components/Topbar";
import { recupererUtilisateur } from "../services/auth";
import "../styles/layout.css";

function Profil() {
  const utilisateur = recupererUtilisateur();
  const initiales = (utilisateur?.nom || "?").split(" ").map(n => n[0]).join("").toUpperCase().slice(0, 2);

  return (
    <div style={{ minHeight: "100vh", background: "linear-gradient(180deg, #f7f5ff 0%, #f7f8fc 100%)", fontFamily: "'Inter', 'Segoe UI', sans-serif" }}>
      <Topbar />
      <main style={{ maxWidth: 560, margin: "0 auto", padding: "32px 16px", display: "flex", flexDirection: "column", gap: 24 }}>

        {/* Card profil */}
        <section style={{ background: "#fff", borderRadius: 16, border: "1px solid #e7e9f4", boxShadow: "0 4px 20px rgba(98,60,222,0.08)", padding: 28 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 20, marginBottom: 24 }}>
            {/* Avatar initiales */}
            <div style={{
              width: 72, height: 72, borderRadius: "50%",
              background: "linear-gradient(135deg, #623cde, #4a90e2)",
              display: "flex", alignItems: "center", justifyContent: "center",
              color: "#fff", fontSize: 26, fontWeight: 700, flexShrink: 0
            }}>
              {initiales}
            </div>
            <div>
              <h1 style={{ margin: 0, fontSize: "1.4rem", fontWeight: 700, color: "#1f2233" }}>{utilisateur?.nom}</h1>
              <p style={{ margin: "4px 0 0", color: "#6d7388", fontSize: "0.9rem" }}>{utilisateur?.email}</p>
              <span style={{
                display: "inline-block", marginTop: 8,
                background: utilisateur?.role === "formateur" ? "rgba(98,60,222,0.1)" : "rgba(74,144,226,0.1)",
                color: utilisateur?.role === "formateur" ? "#623cde" : "#2d7dd2",
                padding: "3px 12px", borderRadius: 20, fontSize: "0.8rem", fontWeight: 600, textTransform: "capitalize"
              }}>
                {utilisateur?.role}
              </span>
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            {[
              { label: "Nom complet", value: utilisateur?.nom },
              { label: "Adresse e-mail", value: utilisateur?.email },
              { label: "Rôle", value: utilisateur?.role },
              { label: "Statut", value: "Actif" },
            ].map(({ label, value }) => (
              <div key={label} style={{ background: "#f8f7ff", borderRadius: 10, padding: "12px 14px" }}>
                <p style={{ margin: 0, fontSize: "0.75rem", color: "#9099b5", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em" }}>{label}</p>
                <p style={{ margin: "4px 0 0", fontSize: "0.9rem", color: "#1f2233", fontWeight: 500 }}>{value}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Card changer mot de passe */}
        <section style={{ background: "#fff", borderRadius: 16, border: "1px solid #e7e9f4", boxShadow: "0 4px 20px rgba(98,60,222,0.08)", padding: 28 }}>
          <h2 style={{ margin: "0 0 20px", fontSize: "1.1rem", fontWeight: 700, color: "#1f2233", display: "flex", alignItems: "center", gap: 10 }}>
            Changer le mot de passe
          </h2>
          <ChangePassword />
        </section>

      </main>
    </div>
  );
}

export default Profil;
