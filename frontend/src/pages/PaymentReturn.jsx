import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useCart } from "../cart";
import { useToast } from "../ui";

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function PaymentReturn() {
  const [params] = useSearchParams();
  const sid = params.get("token");
  const { login } = useAuth();
  const { clear } = useCart();
  const nav = useNavigate();
  const toast = useToast();
  const [err, setErr] = useState("");
  const started = useRef(false);

  useEffect(() => {
    if (!sid || started.current) return;
    started.current = true;
    api("/payments/confirm", { method: "POST", body: { paymentId: sid } })
      .then((res) => {
        if (res.kind === "fee") {
          login(res.auth); toast("Registration complete"); nav(HOME[res.auth.role], { replace: true });
        } else {
          clear(); toast("Payment successful"); nav("/buyer", { replace: true });
        }
      })
      .catch((e) => setErr(e.message));
  }, [sid]);

  if (!sid) return <section><h2>Payment</h2><p>No payment found. <Link to="/">Go home</Link>.</p></section>;
  return (
    <section>
      <h2>Confirming your payment</h2>
      {err ? <p className="err">{err} <Link to="/login">Log in again</Link> and open this page from your browser history if you were charged.</p> : <p className="tag">Please wait…</p>}
    </section>
  );
}

export function PaymentCancel() {
  return (
    <section>
      <h2>Payment cancelled</h2>
      <p>You were not charged. <Link to="/cart">Back to cart</Link> or <Link to="/fee">registration fee page</Link>.</p>
    </section>
  );
}
