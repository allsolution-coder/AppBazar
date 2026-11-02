import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, useApi } from "../api";
import { useAuth } from "../auth";
import { useCart } from "../cart";
import { COL } from "../constants";
import { Loading, fmtSize, stars, usd, useToast } from "../ui";

export default function Product() {
  const { id } = useParams();
  const product = useApi(`/products/${id}`);
  const reviews = useApi(`/products/${id}/reviews`);
  const { auth } = useAuth();
  const { add } = useCart();
  const toast = useToast();
  const [bought, setBought] = useState(false);
  const [err, setErr] = useState("");
  const p = product.data;

  useEffect(() => {
    setBought(false);
    if (!auth) return;
    api("/orders/mine")
      .then((os) => setBought(os.some((o) => o.items.some((i) => String(i.id) === id))))
      .catch(() => {});
  }, [auth, id]);

  if (!p) return <Loading error={product.error} />;

  async function submit(e) {
    e.preventDefault();
    const f = e.target;
    const v = Object.fromEntries(new FormData(f));
    setErr("");
    try {
      await api(`/products/${id}/reviews`, { method: "POST", body: { rating: Number(v.rating), comment: v.comment } });
      f.reset(); reviews.reload(); product.reload(); toast("Thanks for your review");
    } catch (x) { setErr(x.message); }
  }

  const list = reviews.data || [];
  return (
    <section>
      <Link to="/browse">← Back to browse</Link>
      <div className="hero" style={{ paddingTop: 20 }}>
        <div>
          <span className="tag">{p.category} by {p.seller}</span>
          <h1 style={{ fontSize: "2.4rem" }}>{p.name}</h1>
          <p>{p.description}</p>
          {p.techStack && <p className="tag">Tech stack: {p.techStack}{p.college ? ` · ${p.college}` : ""}</p>}
          <p className="tag">{stars(p.rating)} · {p.reviewCount > 0 ? `${p.reviewCount} review${p.reviewCount > 1 ? "s" : ""}` : "no reviews yet"}</p>
          <p className="tag">{p.hasFile ? `Includes: ${p.fileName} (${fmtSize(p.fileSize)}), available to download after purchase` : "Demo listing: no file attached"}</p>
          <p className="price">{usd(p.price)}</p>
          <button onClick={() => { add(p); toast("Added to cart"); }}>Add to cart</button>{" "}
          <button className="ghost" onClick={() => toast("Message sent to " + p.seller + " (demo)")}>Ask the seller</button>
        </div>
        <div className="card"><div className="thumb" style={{ height: 200, background: COL[p.category] }}>{p.name[0]}</div></div>
      </div>

      <h2>Reviews</h2>
      {!reviews.data ? <Loading error={reviews.error} /> : list.length ? (
        <div className="cols">
          {list.map((r) => (
            <div className="box" key={r.id}>
              <b>{stars(r.rating)}</b>
              <p>{r.comment}</p>
              <span className="tag">{r.user} · {new Date(r.createdAt).toLocaleDateString()}</span>
            </div>
          ))}
        </div>
      ) : <p>No reviews yet. Buyers can be the first to review this product.</p>}

      <div className="box" style={{ marginTop: 18, maxWidth: 520 }}>
        <h3>Write a review</h3>
        {!auth ? <p><Link to="/login">Log in</Link> to review products you bought.</p>
          : !bought ? <p className="tag">Only buyers of this product can review it. Buy it first, then come back here.</p>
          : (
            <form onSubmit={submit}>
              <label>Rating
                <select name="rating" defaultValue="5">
                  {[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} star{n > 1 ? "s" : ""}</option>)}
                </select>
              </label>
              <label>Your review<textarea name="comment" rows="3" maxLength="1000" required /></label>
              {err && <span className="err">{err}</span>}
              <button>Post review</button>
              <span className="tag">Posting again replaces your earlier review.</span>
            </form>
          )}
      </div>
    </section>
  );
}
