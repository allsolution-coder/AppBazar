import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import UpiPay from "../UpiPay";
import { usd, useToast } from "../ui";

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function Fee() {
  const { auth, login } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const info = useApi("/auth/fee-info");
  const cfg = useApi("/payments/config");
  const upiCfg = useApi("/upi/config");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [upiSent, setUpiSent] = useState(false);

  if (!auth) return <Navigate to="/login" replace />;
  if (auth.feePaid !== false) return <Navigate to={HOME[auth.role]} replace />;

  const amount = info.data ? info.data.amount : 5;
  const paypalOn = !!(cfg.data && cfg.data.online);
  const demoOn = cfg.data?.demo === true;
  const upiOn = !!(upiCfg.data && upiCfg.data.enabled);

  async function pay() {
    setBusy(true); setErr("");
    try {
      if (paypalOn) {
        const { url } = await api("/payments/fee-session", { method: "POST" });
        location.assign(url);
        return;
      }
      const res = await api("/auth/pay-fee", { method: "POST" });
      login(res); toast("Registration complete"); nav(HOME[res.role]);
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  async function checkStatus() {
    setErr("");
    try {
      const res = await api("/auth/refresh", { method: "POST" });
      if (res.feePaid) { login(res); toast("Registration complete"); nav(HOME[res.role]); }
      else toast("Not approved yet. Please check again in a while.");
    } catch (e) { setErr(e.message); }
  }

  return (
    <section>
      <h2>Complete your registration</h2>
      <div className="cols">
        {!paypalOn && !demoOn && !upiOn && <p>Registration payments are currently unavailable.</p>}
        {(paypalOn || demoOn) && (
          <div className="box" style={{ maxWidth: 460 }}>
            <h3>One-time registration fee</h3>
            <p className="price">{usd(amount)}</p>
            <p>Hi {auth.name}. Buyers and sellers pay this fee once to activate their account. You will not be charged again.</p>
            {!paypalOn && <p className="tag">Demo mode: no real money is charged yet.</p>}
            {err && <p className="err">{err}</p>}
            <button disabled={busy} onClick={pay}>{busy ? "Processing…" : `Pay ${usd(amount)}${paypalOn ? " with PayPal" : " (demo)"}`}</button>
          </div>
        )}
        {upiOn && (
          <div className="box" style={{ maxWidth: 460 }}>
            <h3>{paypalOn ? "Paying from India? Use UPI" : "One-time registration fee"}</h3>
            {!paypalOn && <p>Hi {auth.name}. Pay once to activate your account. You will not be charged again.</p>}
            {upiSent ? (
              <>
                <p>Thank you. We are checking your UPI payment. Press the button below to see if your account is active.</p>
                {err && <p className="err">{err}</p>}
                <button onClick={checkStatus}>Check status</button>
              </>
            ) : (
              <UpiPay kind="fee" onSubmitted={() => setUpiSent(true)} />
            )}
          </div>
        )}
      </div>
    </section>
  );
}
