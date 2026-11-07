import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useToast } from "../ui";

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function Login({ initialMode = "email" }) {
  const { auth, login } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const [mode, setMode] = useState(initialMode); // email, code or password
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("BUYER");
  const [code, setCode] = useState("");
  const [devMode, setDevMode] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  if (auth) return <Navigate to={auth.feePaid === false ? "/fee" : HOME[auth.role]} replace />;

  const done = (res) => {
    login(res); toast("Welcome, " + res.name);
    nav(res.feePaid === false ? "/fee" : HOME[res.role]);
  };
  const run = async (fn) => { setBusy(true); setError(""); try { await fn(); } catch (e) { setError(e.message); } finally { setBusy(false); } };

  const sendCode = (e) => {
    if (e) e.preventDefault();
    return run(async () => {
      const r = await api("/auth/request-code", { method: "POST", body: { email } });
      setDevMode(r.devMode); setCode(""); setMode("code"); setCooldown(60);
    });
  };
  const verify = (e) => {
    e.preventDefault();
    return run(async () => done(await api("/auth/verify-code", { method: "POST", body: { email, code, role } })));
  };
  const passwordLogin = (e) => {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(e.target));
    return run(async () => done(await api("/auth/login", { method: "POST", body: v })));
  };

  if (mode === "password") return (
    <section>
      <h2>Password login</h2>
      <p className="tag">For the admin and the demo seller accounts. Everyone else uses an email code.</p>
      <form onSubmit={passwordLogin}>
        <label>Email<input name="email" type="email" required /></label>
        <label>Password<input name="password" type="password" required /></label>
        {error && <span className="err">{error}</span>}
        <button disabled={busy}>Log in</button>
        <button type="button" className="ghost" onClick={() => { setMode("email"); setError(""); }}>Back to email login</button>
      </form>
    </section>
  );

  if (mode === "code") return (
    <section>
      <h2>Enter your code</h2>
      <p className="tag">We sent a 6-digit code to <b>{email}</b>. It works for 10 minutes.</p>
      {devMode && <p className="tag">Mail server is not set up on this server (development mode). The code is printed in the backend window.</p>}
      <form onSubmit={verify}>
        <label>6-digit code
          <input value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" required autoFocus />
        </label>
        {error && <span className="err">{error}</span>}
        <button disabled={busy || code.length !== 6}>Verify and continue</button>
        <button type="button" className="ghost" disabled={busy || cooldown > 0} onClick={() => sendCode()}>
          {cooldown > 0 ? `Send a new code in ${cooldown}s` : "Send a new code"}
        </button>
        <button type="button" className="ghost" onClick={() => { setMode("email"); setError(""); }}>Use a different email</button>
      </form>
    </section>
  );

  return (
    <section>
      <h2>Log in or sign up</h2>
      <p className="tag" style={{ maxWidth: "52ch" }}>Just enter your email. We send you a code, no password needed. New accounts pay a one-time $5 registration fee after the first login.</p>
      <form onSubmit={sendCode}>
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus /></label>
        <label>I want to
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="BUYER">Buy products</option>
            <option value="SELLER">Sell products</option>
          </select>
        </label>
        <span className="tag">This choice is used only the first time, for a new account.</span>
        {error && <span className="err">{error}</span>}
        <button disabled={busy}>{busy ? "Sending…" : "Send me a code"}</button>
      </form>
    </section>
  );
}

// Hidden page for the admin and demo seller accounts (password login). Not linked from the site.
export function StaffLogin() {
  return <Login initialMode="password" />;
}
