import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { useCart } from "../cart";
import { usd } from "../ui";

export default function Cart() {
  const { items, remove } = useCart();
  const { auth } = useAuth();
  const nav = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const cfg = useApi("/payments/config");
  const paypalOn = !!(cfg.data && cfg.data.online);
  const total = items.reduce((a, p) => a + p.price, 0);

  async function pay() {
    if (!auth) return nav("/login");
    if (auth.feePaid === false) return nav("/fee");
    setBusy(true); setError("");
    try {
      const { url } = await api("/payments/order-session", { method: "POST", body: { productIds: items.map((p) => p.id) } });
      location.assign(url);
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
          {auth && auth.feePaid !== false && !paypalOn && <p>{cfg.error || (cfg.data ? "Checkout is currently unavailable." : "Checking PayPal availability…")}</p>}
          {(paypalOn || !auth || auth.feePaid === false) && (
            <p><button disabled={busy} onClick={pay}>{!auth ? "Log in to pay" : auth.feePaid === false ? "Complete registration" : "Pay with PayPal"}</button></p>
          )}
        </>
      ) : <p>Your cart is empty. <Link to="/browse">Find something to buy</Link>.</p>}
    </section>
  );
}
