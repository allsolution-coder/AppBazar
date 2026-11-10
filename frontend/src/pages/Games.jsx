import { useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../api";
import { Loading, ProductCard } from "../ui";

const TABS = [["All", "All games"], ["Game source code", "Source code"], ["Games", "Playable games"]];

export default function Games() {
  const { data, error } = useApi("/products?category=AllGames");
  const [tab, setTab] = useState("All");
  const [q, setQ] = useState("");
  const list = (data || []).filter(
    (p) => (tab === "All" || p.category === tab) &&
      (p.name + " " + (p.techStack || "") + " " + p.description).toLowerCase().includes(q.toLowerCase())
  );
  return (
    <section>
      <h2>Games</h2>
      <p className="tag" style={{ maxWidth: "60ch" }}>Buy game source code to learn from or build on, or playable games. Sell your own and set your price.</p>
      <p><Link className="btn" to="/games/sell">Sell your game</Link></p>
      <div className="bar">
        <input type="search" placeholder="Search by name or engine, e.g. Unity" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search games" />
        {TABS.map(([v, label]) => (
          <button key={v} className={tab === v ? "" : "ghost"} aria-pressed={tab === v} onClick={() => setTab(v)}>{label}</button>
        ))}
      </div>
      {!data ? <Loading error={error} /> : (
        <div className="grid">
          {list.length ? list.map((p) => <ProductCard key={p.id} p={p} />) : <p>No games yet. Sellers can add theirs with the Sell your game button.</p>}
        </div>
      )}
    </section>
  );
}
