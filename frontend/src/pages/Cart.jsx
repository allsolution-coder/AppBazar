import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { useCart } from "../cart";
import UpiPay from "../UpiPay";
import { usd, useToast } from "../ui";

export default function Cart() {
  const { items, remove, clear } = useCart();
  const { auth } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [upiSent, setUpiSent] = useState(false);
  const cfg = useApi("/payments/config");
  const upiCfg = useApi("/upi/config");
  const paypalOn = !!(cfg.data && cfg.data.online);
  const upiOn = !!(upiCfg.data && upiCfg.data.enabled);
  const total = items.reduce((a, p) => a + p.price, 0);

  async function pay() {
    if (!auth) return nav("/login");
    if (auth.feePaid === false) return nav("/fee");
    setBusy(true); setError("");
    try {
      if (paypalOn) {
        const { url } = await api("/payments/order-session", { method: "POST", body: { productIds: items.map((p) => p.id) } });
        location.assign(url);
        return;
      }
      await api("/orders", { method: "POST", body: { productIds: items.map((p) => p.id) } });
      clear(); toast("Payment successful"); nav("/buyer");
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  if (upiSent) return (
    <section>
      <h2>Payment submitted</h2>
      <p style={{ maxWidth: "56ch" }}>Thank you. We check your UPI payment, and then your order appears in <Link to="/buyer">My orders</Link>, where you can download your files. This can take a little while.</p>
    </section>
  );

  return (
    <section>
      <h2>Your cart</h2>
      {items.length ? (
        <>
          <div className="tbl">
            <table>
              <tbody>
                {items.map((p) => (
                  <tr key={p.id}><td>{p.name}</td><td>{usd(p.price)}</td><td><button className="ghost" onClick={() => remove(p.id)}>Remove</button></td></tr>
                ))}
                <tr><th>Total</th><th>{usd(total)}</th><th></th></tr>
              </tbody>
            </table>
          </div>
          {error && <p className="err">{error}</p>}
          {(paypalOn || !upiOn || !auth) && (
            <p><button disabled={busy} onClick={pay}>{!auth ? "Log in to pay" : paypalOn ? "Pay with PayPal" : "Pay now (demo)"}</button></p>
          )}
          {auth && auth.feePaid !== false && upiOn && (
            <div className="box" style={{ marginTop: 16, maxWidth: 520 }}>
              <h3>Paying from India?</h3>
              <UpiPay kind="order" productIds={items.map((p) => p.id)} onSubmitted={() => { setUpiSent(true); clear(); }} />
            </div>
          )}
        </>
      ) : <p>Your cart is empty. <Link to="/browse">Find something to buy</Link>.</p>}
    </section>
  );
}
