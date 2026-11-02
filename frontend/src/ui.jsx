import { createContext, useContext, useRef, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { COL } from "./constants";
import { useCart } from "./cart";
import { useAuth } from "./auth";

export const usd = (n) => "$" + n.toLocaleString("en-US");
export const fmtSize = (b) => (b == null ? "" : b < 1024 * 1024 ? Math.max(1, Math.round(b / 1024)) + " KB" : (b / 1048576).toFixed(1) + " MB");
export const stars = (r) => "★".repeat(Math.round(r)) + "☆".repeat(5 - Math.round(r)) + " " + r;

const ToastCtx = createContext(() => {});
export const useToast = () => useContext(ToastCtx);

export function ToastProvider({ children }) {
  const [msg, setMsg] = useState("");
  const timer = useRef();
  const show = (t) => { setMsg(t); clearTimeout(timer.current); timer.current = setTimeout(() => setMsg(""), 2200); };
  return (
    <ToastCtx.Provider value={show}>
      {children}
      {msg && <div id="toast" role="status" style={{ display: "block" }}>{msg}</div>}
    </ToastCtx.Provider>
  );
}

export function ProductCard({ p }) {
  const { add } = useCart();
  const toast = useToast();
  return (
    <article className="card">
      <div className="thumb" style={{ background: COL[p.category] }}>{p.name[0]}</div>
      <div className="body">
        <span className="tag">{p.category} · {p.seller}</span>
        {p.techStack && <span className="tag">Tech: {p.techStack}</span>}
        {p.college && <span className="tag">{p.college}</span>}
        <h3><Link to={`/product/${p.id}`}>{p.name}</Link></h3>
        <span className="tag" aria-label={`Rating ${p.rating} out of 5`}>{stars(p.rating)}{p.reviewCount > 0 ? ` (${p.reviewCount})` : ""}</span>
        <div className="row">
          <span className="price">{usd(p.price)}</span>
          <button onClick={() => { add(p); toast("Added to cart"); }}>Add</button>
        </div>
      </div>
    </article>
  );
}

export function RequireRole({ roles, children }) {
  const { auth } = useAuth();
  if (!auth) return <Navigate to="/login" replace />;
  if (auth.feePaid === false) return <Navigate to="/fee" replace />;
  if (!roles.includes(auth.role)) return <section><h2>Not allowed</h2><p>This page is for {roles.join(" or ").toLowerCase()} accounts.</p></section>;
  return children;
}

export const Loading = ({ error }) => (error ? <p className="err">{error}</p> : <p className="tag">Loading…</p>);
