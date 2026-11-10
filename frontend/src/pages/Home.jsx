import { Link } from "react-router-dom";
import { useApi } from "../api";
import { COL } from "../constants";
import { Loading, ProductCard } from "../ui";

export default function Home() {
  const { data, error } = useApi("/products");
  if (!data) return <Loading error={error} />;
  const ks = Object.keys(COL);
  const games = data.filter((p) => p.category === "Game source code" || p.category === "Games");
  const top = [...data].sort((a, b) => b.rating - a.rating)[0];
  const ticker = ks.concat(ks).map((k, i) => <span key={i}>{k}<i>✦</i></span>);
  return (
    <>
      <section className="hero">
        <div>
          <h1>Apps, templates, college projects and AI tools. All in one bazaar.</h1>
          <p>Developers, studios and students list what they built. Buyers pick it up and ship this week.</p>
          <p><Link className="btn" to="/browse">Browse products</Link> <Link className="btn ghost" to="/seller">Start selling</Link></p>
        </div>
        <div className="bento">
          <Link to="/browse"><span>Live listings</span><b>{data.length}</b></Link>
          {top && <Link to={`/product/${top.id}`}><span>Top rated</span><strong>{top.name}</strong></Link>}
          <Link to="/seller"><span>Seller fee</span><b>10%</b></Link>
        </div>
      </section>
      <div className="mq" aria-hidden="true"><div>{ticker}{ticker}</div></div>
      <section>
        <h2>Pick a stall</h2>
        <div className="cols">
          {ks.map((c) => (
            <Link key={c} className="cat" to={`/browse?cat=${encodeURIComponent(c)}`} style={{ background: COL[c] }}>
              <h3>{c}</h3><span className="tag">{data.filter((p) => p.category === c).length} listings</span>
            </Link>
          ))}
        </div>
      </section>
      <section>
        <h2>Student projects</h2>
        <div className="grid">
          {[...data.filter((p) => p.category === "College projects").slice(0, 4), ...data.filter((p) => p.category === "Mini projects").slice(0, 4)].map((p) => <ProductCard key={p.id} p={p} />)}
        </div>
        <p><Link className="btn" to="/projects">See all projects</Link> <Link className="btn ghost" to="/projects/register">Register your project</Link></p>
      </section>
      <section>
        <h2>Games</h2>
        {games.length ? (
          <div className="grid">{games.slice(0, 4).map((p) => <ProductCard key={p.id} p={p} />)}</div>
        ) : (
          <div className="box"><p>No games yet. Be the first to sell a game or its source code.</p></div>
        )}
        <p><Link className="btn" to="/games">See all games</Link> <Link className="btn ghost" to="/games/sell">Sell your game</Link></p>
      </section>
      <section>
        <h2>Popular right now</h2>
        <div className="grid">{data.filter((p) => p.rating >= 4.6).map((p) => <ProductCard key={p.id} p={p} />)}</div>
      </section>
      <section>
        <h2>Why people trust it</h2>
        <div className="cols">
          <div className="box"><h3>Every listing approved</h3><p>Admins review each product before it goes live.</p></div>
          <div className="box"><h3>Secure checkout</h3><p>Pay securely with PayPal. Your payment details never touch our servers.</p></div>
          <div className="box"><h3>Talk to the seller</h3><p>Ask questions before you buy, and leave a review after.</p></div>
        </div>
      </section>
    </>
  );
}
