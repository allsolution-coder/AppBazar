import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { usd, useToast } from "../ui";

function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = resolve;
    script.onerror = () => {
      script.remove();
      reject(new Error("Could not load Razorpay Checkout"));
    };
    document.head.appendChild(script);
  });
}

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function Fee() {
  const { auth, login } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const razorpay = useApi("/payments/razorpay-registration/config");
  const cfg = useApi("/payments/config");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  if (!auth) return <Navigate to="/login" replace />;
  if (auth.feePaid !== false) return <Navigate to={HOME[auth.role]} replace />;

  const amount = cfg.data?.fee;
  const paypalOn = !!(cfg.data && cfg.data.online);

  async function pay() {
    setBusy(true); setErr("");
    try {
      const { url } = await api("/payments/fee-session", { method: "POST" });
      location.assign(url);
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  async function payRazorpay() {
    setBusy(true);
    setErr("");
    try {
      await loadRazorpay();
      const order = await api("/payments/razorpay-registration/order", { method: "POST" });
      const checkout = new window.Razorpay({
        key: order.keyId,
        order_id: order.orderId,
        amount: order.amount,
        currency: order.currency,
        name: "AppBazaar Test",
        description: "Registration fee",
        modal: { ondismiss: () => setBusy(false) },
        handler: async (payment) => {
          try {
            const res = await api("/payments/razorpay-registration/verify", {
              method: "POST",
              body: { orderId: order.orderId, paymentId: payment.razorpay_payment_id, signature: payment.razorpay_signature },
            });
            login(res);
            toast("Registration complete");
            nav(HOME[res.role]);
          } catch (e) {
            setErr(e.message);
          } finally {
            setBusy(false);
          }
        },
      });
      checkout.open();
    } catch (e) {
      setErr(e.message);
      setBusy(false);
    }
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
      <div className="box" style={{ maxWidth: 460 }}>
        <h3>One-time registration fee</h3>
        {razorpay.data?.enabled ? (
          <>
            <p className="price">₹{razorpay.data.amount / 100}</p>
            <button disabled={busy} onClick={payRazorpay}>{busy ? "Processing…" : "Pay with Razorpay (Test Mode)"}</button>
          </>
        ) : paypalOn ? (
          <>
            <p className="price">{usd(amount)}</p>
            <button disabled={busy} onClick={pay}>{busy ? "Processing…" : "Pay with PayPal"}</button>
          </>
        ) : <p>{cfg.error || (cfg.data ? "Registration payments are currently unavailable." : "Checking PayPal availability…")}</p>}
        {err && <p className="err">{err}</p>}
        <p><button className="ghost" onClick={checkStatus}>Check an earlier payment</button></p>
      </div>
    </section>
  );
}
