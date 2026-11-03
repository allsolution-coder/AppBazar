import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { useCart } from "../cart";
import { usd, useToast } from "../ui";

export default function Cart() {
  const { items, remove, clear } = useCart();
  const { auth } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const cfg = useApi("/payments/config");
  const total = items.reduce((a, p) => a + p.price, 0);

  async function pay() {
    if (!auth) return nav("/login");
    if (auth.feePaid === false) return nav("/fee");
    setBusy(true); setError("");
    try {
      if (cfg.data && cfg.data.online) {
        const { url } = await api("/payments/order-session", { method: "POST", body: { productIds: items.map((p) => p.id) } });
        location.assign(url);
        return;
      }
      await api("/orders", { method: "POST", body: { productIds: items.map((p) => p.id) } });
      clear(); toast("Payment successful"); nav("/buyer");
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

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
          <p><button disabled={busy} onClick={pay}>{auth ? (cfg.data && cfg.data.online ? "Pay with PayPal" : "Pay now (demo)") : "Log in to pay"}</button></p>
        </>
      ) : <p>Your cart is empty. <Link to="/browse">Find something to buy</Link>.</p>}
    </section>
  );
}
