import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { usd, useToast } from "../ui";

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function Fee() {
  const { auth, login } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const info = useApi("/auth/fee-info");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  if (!auth) return <Navigate to="/login" replace />;
  if (auth.feePaid !== false) return <Navigate to={HOME[auth.role]} replace />;

  const amount = info.data ? info.data.amount : 5;

  async function pay() {
    setBusy(true); setErr("");
    try {
      const res = await api("/auth/pay-fee", { method: "POST" });
      login(res); toast("Registration complete"); nav(HOME[res.role]);
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  return (
    <section>
      <h2>Complete your registration</h2>
      <div className="box" style={{ maxWidth: 460 }}>
        <h3>One-time registration fee</h3>
        <p className="price">{usd(amount)}</p>
        <p>Hi {auth.name}. Buyers and sellers pay this fee once to activate their account. You will not be charged again.</p>
        <p className="tag">Demo mode: no real money is charged yet.</p>
        {err && <p className="err">{err}</p>}
        <button disabled={busy} onClick={pay}>{busy ? "Processing…" : `Pay ${usd(amount)} (demo)`}</button>
      </div>
    </section>
  );
}
