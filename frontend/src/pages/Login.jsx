import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useToast } from "../ui";

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function Login() {
  const { auth, login } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const [mode, setMode] = useState("login");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (auth) return <Navigate to={auth.feePaid === false ? "/fee" : HOME[auth.role]} replace />;

  async function submit(e) {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(e.target));
    setBusy(true); setError("");
    try {
      const res = await api(`/auth/${mode}`, { method: "POST", body: v });
      login(res); toast("Welcome, " + res.name); nav(res.feePaid === false ? "/fee" : HOME[res.role]);
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }

  return (
    <section>
      <h2>{mode === "login" ? "Log in" : "Create an account"}</h2>
      <form onSubmit={submit}>
        {mode === "register" && <label>Name<input name="name" required /></label>}
        <label>Email<input name="email" type="email" required /></label>
        <label>Password<input name="password" type="password" minLength={mode === "register" ? 8 : 1} required /></label>
        {mode === "register" && <label>I want to<select name="role"><option value="BUYER">Buy</option><option value="SELLER">Sell</option></select></label>}
        {mode === "register" && <span className="tag">A one-time $5 registration fee applies to buyers and sellers. You pay it right after creating your account.</span>}
        {error && <span className="err">{error}</span>}
        <button disabled={busy}>{mode === "login" ? "Log in" : "Register"}</button>
        <button type="button" className="ghost" onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }}>
          {mode === "login" ? "New here? Create an account" : "Have an account? Log in"}
        </button>
      </form>
    </section>
  );
}
