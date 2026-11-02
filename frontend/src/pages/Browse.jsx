import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useApi } from "../api";
import { CATS } from "../constants";
import { Loading, ProductCard } from "../ui";

export default function Browse() {
  const [params, setParams] = useSearchParams();
  const cat = params.get("cat") || "All";
  const [q, setQ] = useState("");
  const [dq, setDq] = useState("");
  useEffect(() => { const t = setTimeout(() => setDq(q), 250); return () => clearTimeout(t); }, [q]);
  const { data, error } = useApi(`/products?q=${encodeURIComponent(dq)}&category=${encodeURIComponent(cat)}`);
  return (
    <section>
      <h2>Browse products</h2>
      <div className="bar">
        <input type="search" placeholder="Search products…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search" />
        <select value={cat} onChange={(e) => setParams(e.target.value === "All" ? {} : { cat: e.target.value })} aria-label="Category">
          {["All", ...CATS].map((c) => <option key={c}>{c}</option>)}
        </select>
      </div>
      {!data ? <Loading error={error} /> : (
        <div className="grid">
          {data.length ? data.map((p) => <ProductCard key={p.id} p={p} />) : <p>No products match. Try a different search or category.</p>}
        </div>
      )}
    </section>
  );
}
