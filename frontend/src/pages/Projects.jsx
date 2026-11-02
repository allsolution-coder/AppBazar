import { useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "../api";
import { Loading, ProductCard } from "../ui";

const TABS = [["All", "All projects"], ["College projects", "College"], ["Mini projects", "Mini"]];

export default function Projects() {
  const { data, error } = useApi("/products?category=Projects");
  const [tab, setTab] = useState("All");
  const [q, setQ] = useState("");
  const list = (data || []).filter(
    (p) => (tab === "All" || p.category === tab) &&
      (p.name + " " + (p.techStack || "") + " " + p.description).toLowerCase().includes(q.toLowerCase())
  );
  return (
    <section>
      <h2>Student projects</h2>
      <p className="tag" style={{ maxWidth: "60ch" }}>College and mini projects in one place. Buy a project to learn from, or register your own and set your price.</p>
      <p><Link className="btn" to="/projects/register">Register your project</Link></p>
      <div className="bar">
        <input type="search" placeholder="Search by name or tech, e.g. Spring Boot" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search projects" />
        {TABS.map(([v, label]) => (
          <button key={v} className={tab === v ? "" : "ghost"} aria-pressed={tab === v} onClick={() => setTab(v)}>{label}</button>
        ))}
      </div>
      {!data ? <Loading error={error} /> : (
        <div className="grid">
          {list.length ? list.map((p) => <ProductCard key={p.id} p={p} />) : <p>No projects match. Try a different search.</p>}
        </div>
      )}
    </section>
  );
}
