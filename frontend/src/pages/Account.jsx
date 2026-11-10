import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { Loading, useToast } from "../ui";

const ROLE = { BUYER: "Buyer", SELLER: "Seller", ADMIN: "Admin" };
const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function Account() {
  const { auth, login } = useAuth();
  const toast = useToast();
  const { data, error, reload } = useApi("/account/me");
  const [name, setName] = useState("");
  const [nameErr, setNameErr] = useState("");
  const [pwErr, setPwErr] = useState("");

  useEffect(() => { if (data) setName(data.name); }, [data]);
  if (!data) return <Loading error={error} />;

  async function saveName(e) {
    e.preventDefault();
    setNameErr("");
    try {
      const r = await api("/account/me", { method: "PUT", body: { name } });
      login({ ...auth, name: r.name });
      reload(); toast("Name saved");
    } catch (x) { setNameErr(x.message); }
  }

  async function savePassword(e) {
    e.preventDefault();
    const f = e.target;
    const v = Object.fromEntries(new FormData(f));
    setPwErr("");
    try {
      await api("/account/password", { method: "POST", body: v });
      f.reset(); toast("Password changed");
    } catch (x) { setPwErr(x.message); }
  }

  const row = (label, value) => (
    <tr><th style={{ width: 190 }}>{label}</th><td>{value}</td></tr>
  );

  return (
    <section>
      <h2>My account</h2>
      <div className="cols">
        <div className="box">
          <h3>Details</h3>
          <div className="tbl">
            <table>
              <tbody>
                {row("Name", data.name)}
                {row("Email", data.email)}
                {row("Account type", ROLE[data.role] || data.role)}
                {row("Registration fee", data.role === "ADMIN" ? "Not needed" : data.feePaid ? (data.feePaidAt ? `Paid on ${new Date(data.feePaidAt).toLocaleDateString()}` : "Paid") : "Not paid yet")}
                {data.role !== "ADMIN" && row("Orders", data.orders)}
                {data.role === "SELLER" && row("Your listings", data.listings)}
              </tbody>
            </table>
          </div>
          <p><Link className="btn ghost" to={HOME[data.role]}>{data.role === "SELLER" ? "Go to seller dashboard" : data.role === "ADMIN" ? "Go to admin panel" : "Go to my orders"}</Link></p>
        </div>

        <div className="box">
          <h3>Display name</h3>
          <form onSubmit={saveName}>
            <label>Name<input value={name} onChange={(e) => setName(e.target.value)} maxLength={60} required /></label>
            {nameErr && <span className="err">{nameErr}</span>}
            <button>Save name</button>
          </form>
        </div>

        <div className="box">
          <h3>Change password</h3>
          <form onSubmit={savePassword}>
            <label>Current password<input name="currentPassword" type="password" autoComplete="current-password" required /></label>
            <label>New password (8 or more characters)<input name="newPassword" type="password" minLength={8} maxLength={72} autoComplete="new-password" required /></label>
            {pwErr && <span className="err">{pwErr}</span>}
            <button>Change password</button>
            <span className="tag">Forgot your current password? Log out and use Forgot password on the Log in page.</span>
          </form>
        </div>
      </div>
    </section>
  );
}
