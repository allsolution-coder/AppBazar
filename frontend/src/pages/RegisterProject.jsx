import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useToast } from "../ui";

const MAX_MB = 50;
const ACCEPT = ".zip,.rar,.7z,.tar,.gz,.pdf,.apk,.jar,.txt,.md,.unitypackage";

const KINDS = {
  project: {
    title: "Register your project",
    intro: "Add your project details and price. An admin reviews it first, then it appears under Projects.",
    types: [["College projects", "College project"], ["Mini projects", "Mini project"]],
    stackLabel: "Tech stack",
    stackHint: "e.g. Java, Spring Boot, React, MySQL",
    college: true,
    fileHint: "Zip your source code, database script and report.",
    noun: "project",
    descHint: "What does it do? What is included?",
  },
  game: {
    title: "Sell your game",
    intro: "Add your game or its source code and set a price. An admin reviews it first, then it appears under Games.",
    types: [["Game source code", "Game source code (project files)"], ["Games", "Game (playable build)"]],
    stackLabel: "Game engine and tech",
    stackHint: "e.g. Unity, C#, Android",
    college: false,
    fileHint: "Zip the project or the build (.zip, .apk or .unitypackage).",
    noun: "game",
    descHint: "What is the game about? What is included (assets, scripts, levels)?",
  },
};

export default function RegisterProject({ kind = "project" }) {
  const k = KINDS[kind];
  const { auth } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  if (!auth) return (
    <section><h2>{k.title}</h2>
      <p><Link to="/login">Log in</Link> with a seller account to continue. New here? Choose "Sell products" when you create your account.</p></section>
  );
  if (auth.feePaid === false) return (
    <section><h2>{k.title}</h2>
      <p>Pay the one-time registration fee first. <Link to="/fee">Pay the fee</Link>.</p></section>
  );
  if (auth.role !== "SELLER") return (
    <section><h2>{k.title}</h2>
      <p>Only seller accounts can sell here. Log out and create an account with the "Sell products" option.</p></section>
  );

  async function submit(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const file = fd.get("file");
    setErr("");
    if (!file || !file.size) return setErr("Please attach your file");
    if (file.size > MAX_MB * 1024 * 1024) return setErr(`File is too large. Maximum is ${MAX_MB} MB`);
    setBusy(true);
    try {
      await api("/products", { method: "POST", body: fd });
      toast("Submitted. It goes live after admin approval.");
      nav("/seller");
    } catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  return (
    <section>
      <h2>{k.title}</h2>
      <p className="tag" style={{ maxWidth: "60ch" }}>{k.intro}</p>
      <form onSubmit={submit}>
        <label>Name<input name="name" required /></label>
        <label>Type
          <select name="category" defaultValue={k.types[0][0]}>
            {k.types.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
          </select>
        </label>
        <label>{k.stackLabel}<input name="techStack" placeholder={k.stackHint} required maxLength={200} /></label>
        {k.college && <label>College or university (optional)<input name="college" maxLength={200} /></label>}
        <label>Price ($)<input name="price" type="number" min="1" required /></label>
        <label>Description<textarea name="description" rows="4" placeholder={k.descHint} required /></label>
        <label>File<input name="file" type="file" accept={ACCEPT} required /></label>
        <span className="tag">{k.fileHint} Max {MAX_MB} MB.</span>
        <label style={{ display: "flex", gap: 8, alignItems: "flex-start", fontWeight: 500 }}>
          <input name="rights" type="checkbox" required style={{ width: "auto", marginTop: 5 }} />
          <span>I made this {k.noun} or have the right to sell it. It has no cracked, pirated or stolen content.</span>
        </label>
        {err && <span className="err">{err}</span>}
        <button disabled={busy}>{busy ? "Uploading…" : "Submit for approval"}</button>
      </form>
    </section>
  );
}
