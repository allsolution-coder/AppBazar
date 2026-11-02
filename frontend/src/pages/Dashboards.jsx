import { useState } from "react";
import { api, downloadFile, useApi } from "../api";
import { CATS } from "../constants";
import { Loading, fmtSize, usd, useToast } from "../ui";

const MAX_MB = 50;
const ACCEPT = ".zip,.rar,.7z,.tar,.gz,.pdf,.apk,.jar,.txt,.md";

export function Buyer() {
  const { data, error } = useApi("/orders/mine");
  const toast = useToast();
  const get = (i) => downloadFile(i.id, i.fileName).catch((e) => toast(e.message));
  return (
    <section>
      <h2>My orders</h2>
      {!data ? <Loading error={error} /> : data.length ? (
        <div className="tbl">
          <table>
            <thead><tr><th>Order</th><th>Items</th><th>Total</th><th>Status</th></tr></thead>
            <tbody>
              {data.map((o) => (
                <tr key={o.id}>
                  <td>#{o.id}</td>
                  <td>
                    {o.items.map((i) => (
                      <div key={i.id} style={{ margin: "4px 0" }}>
                        {i.name}{" "}
                        {i.hasFile ? <button onClick={() => get(i)}>Download</button> : <span className="tag">(demo listing, no file)</span>}
                      </div>
                    ))}
                  </td>
                  <td>{usd(o.total)}</td>
                  <td><span className="pill ok">Paid</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : <p>No orders yet. Browse products to place your first one.</p>}
    </section>
  );
}

export function Seller() {
  const { data, error, reload } = useApi("/products/mine");
  const toast = useToast();
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    const f = e.target;
    const fd = new FormData(f);
    const file = fd.get("file");
    setErr("");
    if (!file || !file.size) return setErr("Please attach the product file");
    if (file.size > MAX_MB * 1024 * 1024) return setErr(`File is too large. Maximum is ${MAX_MB} MB`);
    setBusy(true);
    try {
      await api("/products", { method: "POST", body: fd });
      f.reset(); reload(); toast("Submitted for approval");
    } catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  return (
    <section>
      <h2>Seller dashboard</h2>
      <div className="cols">
        <div className="box">
          <h3>Upload a product</h3>
          <form onSubmit={submit}>
            <label>Name<input name="name" required /></label>
            <label>Category<select name="category">{CATS.map((c) => <option key={c}>{c}</option>)}</select></label>
            <label>Price ($)<input name="price" type="number" min="1" required /></label>
            <label>Description<textarea name="description" rows="3" required /></label>
            <label>Product file<input name="file" type="file" accept={ACCEPT} required /></label>
            <span className="tag">Allowed: zip, rar, 7z, tar, gz, pdf, apk, jar, txt, md. Max {MAX_MB} MB. Zip your project first.</span>
            {err && <span className="err">{err}</span>}
            <button disabled={busy}>{busy ? "Uploading…" : "Submit for approval"}</button>
          </form>
        </div>
        <div className="box">
          <h3>Your listings</h3>
          {!data ? <Loading error={error} /> : data.length ? data.map((p) => (
            <p key={p.id}>
              {p.name} <span className={"pill" + (p.status === "LIVE" ? " ok" : "")}>{p.status === "LIVE" ? "Live" : "Awaiting approval"}</span>
              <br /><span className="tag">{p.hasFile ? `${p.fileName} (${fmtSize(p.fileSize)})` : "No file attached"}</span>
            </p>
          )) : <p>Nothing listed yet. New products go live after an admin approves them.</p>}
          <p className="tag">Commission: 10% per sale.</p>
        </div>
      </div>
    </section>
  );
}

export function Admin() {
  const stats = useApi("/admin/stats");
  const pending = useApi("/admin/pending");
  const toast = useToast();
  const refresh = () => { stats.reload(); pending.reload(); };

  async function act(id, approve) {
    try {
      await api(approve ? `/admin/products/${id}/approve` : `/admin/products/${id}`, { method: approve ? "POST" : "DELETE" });
      toast(approve ? "Approved" : "Rejected"); refresh();
    } catch (e) { toast(e.message); }
  }

  const s = stats.data;
  return (
    <section>
      <h2>Admin panel</h2>
      {!s ? <Loading error={stats.error} /> : (
        <div className="cols">
          <div className="box"><h3>{s.liveProducts}</h3>Live products</div>
          <div className="box"><h3>{s.pending}</h3>Awaiting approval</div>
          <div className="box"><h3>{s.orders}</h3>Orders</div>
          <div className="box"><h3>{usd(s.commission)}</h3>Commission earned</div>
          <div className="box"><h3>{usd(s.feeIncome)}</h3>Registration fees</div>
        </div>
      )}
      <h2 style={{ marginTop: 28 }}>Approval queue</h2>
      {!pending.data ? <Loading error={pending.error} /> : pending.data.length ? pending.data.map((p) => (
        <div className="box" style={{ marginBottom: 10 }} key={p.id}>
          <b>{p.name}</b> · {p.category} · {usd(p.price)} · by {p.seller}<br />
          <span className="tag">{p.hasFile ? `File: ${p.fileName} (${fmtSize(p.fileSize)})` : "No file attached"}</span><br />
          {p.hasFile && <><button className="ghost" onClick={() => downloadFile(p.id, p.fileName).catch((e) => toast(e.message))}>Download to review</button>{" "}</>}
          <button onClick={() => act(p.id, true)}>Approve</button>{" "}
          <button className="ghost" onClick={() => act(p.id, false)}>Reject</button>
        </div>
      )) : <p>Queue is clear. New seller uploads will appear here.</p>}
    </section>
  );
}
