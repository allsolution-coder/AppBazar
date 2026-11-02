import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useToast } from "../ui";

const MAX_MB = 50;
const ACCEPT = ".zip,.rar,.7z,.tar,.gz,.pdf,.apk,.jar,.txt,.md";

export default function RegisterProject() {
  const { auth } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  if (!auth) return (
    <section><h2>Register your project</h2>
      <p><Link to="/login">Log in</Link> with a seller account to register a project. New here? Choose "Sell" when you create your account.</p></section>
  );
  if (auth.feePaid === false) return (
    <section><h2>Register your project</h2>
      <p>Pay the one-time $5 registration fee first. <Link to="/fee">Pay the fee</Link>.</p></section>
  );
  if (auth.role !== "SELLER") return (
    <section><h2>Register your project</h2>
      <p>Only seller accounts can register projects. Log out and create an account with the "Sell" option.</p></section>
  );

  async function submit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const file = fd.get("file");
    setErr("");
    if (!file || !file.size) return setErr("Please attach your project file");
    if (file.size > MAX_MB * 1024 * 1024) return setErr(`File is too large. Maximum is ${MAX_MB} MB`);
    setBusy(true);
    try {
      await api("/products", { method: "POST", body: fd });
      toast("Project registered. It goes live after admin approval.");
      nav("/seller");
    } catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  return (
    <section>
      <h2>Register your project</h2>
      <p className="tag" style={{ maxWidth: "60ch" }}>Add your project details and price. An admin reviews it first, then it appears under Projects.</p>
      <form onSubmit={submit}>
        <label>Project name<input name="name" required /></label>
        <label>Type
          <select name="category" defaultValue="College projects">
            <option value="College projects">College project</option>
            <option value="Mini projects">Mini project</option>
          </select>
        </label>
        <label>Tech stack<input name="techStack" placeholder="e.g. Java, Spring Boot, React, MySQL" required maxLength={200} /></label>
        <label>College or university (optional)<input name="college" maxLength={200} /></label>
        <label>Price ($)<input name="price" type="number" min="1" required /></label>
        <label>Description<textarea name="description" rows="4" placeholder="What does it do? What is included?" required /></label>
        <label>Project file<input name="file" type="file" accept={ACCEPT} required /></label>
        <span className="tag">Zip your source code, database script and report. Max {MAX_MB} MB.</span>
        {err && <span className="err">{err}</span>}
        <button disabled={busy}>{busy ? "Uploading…" : "Register project"}</button>
      </form>
    </section>
  );
}
