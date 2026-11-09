import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useToast } from "../ui";

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function Login() {
  const { auth, login } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const [mode, setMode] = useState("login"); // login, signup, code (verify sign-up), forgot, reset
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
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

  const go = (m) => { setMode(m); setError(""); };
  const done = (res) => {
    login(res); toast("Welcome, " + res.name);
    nav(res.feePaid === false ? "/fee" : HOME[res.role]);
  };
  const run = async (fn) => { setBusy(true); setError(""); try { await fn(); } catch (e) { setError(e.message); } finally { setBusy(false); } };

  const doLogin = (e) => { e.preventDefault(); return run(async () => done(await api("/auth/login", { method: "POST", body: { email, password } }))); };
  const sendSignup = (e) => {
    if (e) e.preventDefault();
    return run(async () => {
      const r = await api("/auth/signup", { method: "POST", body: { email, password, role } });
      setDevMode(r.devMode); setCode(""); setMode("code"); setCooldown(60);
    });
  };
  const verifySignup = (e) => { e.preventDefault(); return run(async () => done(await api("/auth/signup/verify", { method: "POST", body: { email, code } }))); };
  const sendForgot = (e) => {
    if (e) e.preventDefault();
    return run(async () => {
      const r = await api("/auth/forgot", { method: "POST", body: { email } });
      setDevMode(r.devMode); setCode(""); setPassword(""); setMode("reset"); setCooldown(60);
    });
  };
  const doReset = (e) => { e.preventDefault(); return run(async () => done(await api("/auth/reset", { method: "POST", body: { email, code, password } }))); };

  const codeBox = (
    <label>6-digit code
      <input value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" required autoFocus />
    </label>
  );
  const errBox = error && <span className="err">{error}</span>;

  if (mode === "signup") return (
    <section>
      <h2>Create your account</h2>
      <p className="tag" style={{ maxWidth: "52ch" }}>We email you a code once to confirm your address. After that you log in with your password. New accounts pay a one-time $5 registration fee.</p>
      <form onSubmit={sendSignup}>
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required autoFocus /></label>
        <label>Password (8 or more characters)<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72} autoComplete="new-password" required /></label>
        <label>I want to
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="BUYER">Buy products</option>
            <option value="SELLER">Sell products</option>
          </select>
        </label>
        {errBox}
        <button disabled={busy}>{busy ? "Sending…" : "Send verification code"}</button>
        <button type="button" className="ghost" onClick={() => go("login")}>I already have an account</button>
      </form>
    </section>
  );

  if (mode === "code") return (
    <section>
      <h2>Verify your email</h2>
      <p className="tag">We sent a 6-digit code to <b>{email}</b>. It works for 10 minutes. Check your spam folder too.</p>
      {devMode && <p className="tag">Mail is not set up on this server (development mode). The code is printed in the backend window.</p>}
      <form onSubmit={verifySignup}>
        {codeBox}
        {errBox}
        <button disabled={busy || code.length !== 6}>Verify and create account</button>
        <button type="button" className="ghost" disabled={busy || cooldown > 0} onClick={() => sendSignup()}>
          {cooldown > 0 ? `Send a new code in ${cooldown}s` : "Send a new code"}
        </button>
        <button type="button" className="ghost" onClick={() => go("signup")}>Use a different email</button>
      </form>
    </section>
  );

  if (mode === "forgot") return (
    <section>
      <h2>Forgot your password?</h2>
      <p className="tag">Enter your email and we send a code to set a new password.</p>
      <form onSubmit={sendForgot}>
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required autoFocus /></label>
        {errBox}
        <button disabled={busy}>{busy ? "Sending…" : "Send code"}</button>
        <button type="button" className="ghost" onClick={() => go("login")}>Back to log in</button>
      </form>
    </section>
  );

  if (mode === "reset") return (
    <section>
      <h2>Set a new password</h2>
      <p className="tag">If <b>{email}</b> has an account, a 6-digit code is on its way. It works for 10 minutes.</p>
      {devMode && <p className="tag">Mail is not set up on this server (development mode). The code is printed in the backend window.</p>}
      <form onSubmit={doReset}>
        {codeBox}
        <label>New password (8 or more characters)<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} maxLength={72} autoComplete="new-password" required /></label>
        {errBox}
        <button disabled={busy || code.length !== 6}>Save password and log in</button>
        <button type="button" className="ghost" disabled={busy || cooldown > 0} onClick={() => sendForgot()}>
          {cooldown > 0 ? `Send a new code in ${cooldown}s` : "Send a new code"}
        </button>
        <button type="button" className="ghost" onClick={() => go("login")}>Back to log in</button>
      </form>
    </section>
  );

  return (
    <section>
      <h2>Log in</h2>
      <form onSubmit={doLogin}>
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required autoFocus /></label>
        <label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required /></label>
        {errBox}
        <button disabled={busy}>{busy ? "Logging in…" : "Log in"}</button>
        <button type="button" className="ghost" onClick={() => go("signup")}>New here? Create an account</button>
        <button type="button" className="ghost" onClick={() => go("forgot")}>Forgot password?</button>
      </form>
    </section>
  );
}

// kept so the old /staff address still works; the normal login page now takes a password for everyone
export function StaffLogin() {
  return <Login />;
}
