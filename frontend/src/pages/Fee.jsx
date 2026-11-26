import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { useToast } from "../ui";

function loadCashfree() {
  if (window.Cashfree) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://sdk.cashfree.com/js/v3/cashfree.js";
    script.onload = resolve;
    script.onerror = () => { script.remove(); reject(new Error("Could not load Cashfree Checkout")); };
    document.head.appendChild(script);
  });
}

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };
const ACCOUNT_LABEL = {
  APP_SOFTWARE_SELLER: "App / Software Seller",
  GAME_DEVELOPER: "Game Developer",
  SAAS_DEVELOPER: "SaaS Developer",
};

export default function Fee() {
  const { auth, login } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const cfg = useApi("/payments/cashfree-registration/config");
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const [currentOrderId, setCurrentOrderId] = useState("");

  async function verify(orderId) {
    const res = await api(`/payments/cashfree-registration/orders/${encodeURIComponent(orderId)}/verify`, { method: "POST" });
    login(res);
    toast("Registration complete");
    nav(HOME[res.role] || "/seller", { replace: true });
  }

  useEffect(() => {
    const orderId = new URLSearchParams(location.search).get("cashfree_order_id");
    if (!orderId || !auth) return;
    setBusy(true);
    verify(orderId).catch((e) => setErr(e.message)).finally(() => setBusy(false));
    // Only verify the order id returned by Cashfree; the server checks its user, amount and paid status.
  }, []);

  if (!auth) return <Navigate to="/login" replace />;
  if (auth.feePaid !== false) return <Navigate to={HOME[auth.role]} replace />;

  async function pay() {
    setBusy(true); setErr("");
    try {
      await loadCashfree();
      const order = await api("/payments/cashfree-registration/orders", {
        method: "POST", body: { phone: phone.replace(/\D/g, "") },
      });
      setCurrentOrderId(order.orderId);
      const result = await window.Cashfree({ mode: cfg.data?.mode || "sandbox" }).checkout({
        paymentSessionId: order.paymentSessionId,
        redirectTarget: "_modal",
      });
      if (result?.error) throw new Error(result.error.message || "Cashfree checkout was not completed");
      await verify(order.orderId);
    } catch (e) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  }

  async function checkStatus() {
    setErr("");
    try {
      if (currentOrderId) return await verify(currentOrderId);
      const res = await api("/auth/refresh", { method: "POST" });
      if (res.feePaid) { login(res); toast("Registration complete"); nav(HOME[res.role]); }
      else toast("Payment is not confirmed yet. Check again shortly.");
    } catch (e) { setErr(e.message); }
  }

  const fee = cfg.data?.amount;
  const type = cfg.data?.accountType || auth.accountType;

  return (
    <section>
      <h2>Complete your registration</h2>
      <div className="box" style={{ maxWidth: 460 }}>
        <h3>One-time registration fee</h3>
        <p>{ACCOUNT_LABEL[type] || "Developer account"}</p>
        {fee != null && <p className="price">₹{fee}</p>}
        {cfg.data?.enabled ? (
          <>
            <label>Mobile number
              <input value={phone} onChange={(e) => setPhone(e.target.value.replace(/[^0-9+ -]/g, ""))}
                inputMode="tel" autoComplete="tel" placeholder="10-digit mobile number" required />
            </label>
            <button disabled={busy || phone.replace(/\D/g, "").length < 10} onClick={pay}>
              {busy ? "Opening secure checkout…" : `Pay ₹${fee} with Cashfree`}
            </button>
          </>
        ) : <p>{cfg.error || (cfg.data ? "Cashfree is not configured for this environment." : "Checking Cashfree availability…")}</p>}
        {err && <p className="err">{err}</p>}
        <p><button className="ghost" disabled={busy} onClick={checkStatus}>Check payment status</button></p>
      </div>
    </section>
  );
}
