import { useEffect } from "react";
import { Link, NavLink, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "./auth";
import { useCart } from "./cart";
import { RequireRole } from "./ui";
import Home from "./pages/Home";
import Browse from "./pages/Browse";
import Product from "./pages/Product";
import Cart from "./pages/Cart";
import Projects from "./pages/Projects";
import Games from "./pages/Games";
import RegisterProject from "./pages/RegisterProject";
import Login, { StaffLogin } from "./pages/Login";
import Fee from "./pages/Fee";
import Account from "./pages/Account";
import PaymentReturn, { PaymentCancel } from "./pages/PaymentReturn";
import { Buyer, Seller, Admin } from "./pages/Dashboards";
import { About, Contact, Privacy, Terms, NotFound } from "./pages/Static";

const HOME = { BUYER: "/buyer", SELLER: "/seller", ADMIN: "/admin" };

export default function App() {
  const { auth, logout } = useAuth();
  const { items } = useCart();
  const nav = useNavigate();
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);

  const theme = () => {
    const r = document.documentElement;
    const dark = r.dataset.theme ? r.dataset.theme === "dark" : matchMedia("(prefers-color-scheme:dark)").matches;
    r.dataset.theme = dark ? "light" : "dark";
    try { localStorage.setItem("ab-theme", r.dataset.theme); } catch { /* storage blocked */ }
  };
  useEffect(() => {
    try { const t = localStorage.getItem("ab-theme"); if (t) document.documentElement.dataset.theme = t; } catch { /* storage blocked */ }
  }, []);

  return (
    <>
      <header>
        <div className="w">
          <nav aria-label="Main">
            <Link className="logo" to="/">AppBazaar</Link>
            <div className="links">
              <NavLink to="/browse">Browse</NavLink>
              <NavLink to="/projects">Projects</NavLink>
              <NavLink to="/games">Games</NavLink>
              <NavLink to="/about">About</NavLink>
              <NavLink to="/contact">Contact</NavLink>
              {auth && (auth.feePaid === false
                ? <NavLink to="/fee">Pay $5 fee</NavLink>
                : <NavLink to={HOME[auth.role]}>{auth.role === "ADMIN" ? "Admin" : auth.role === "SELLER" ? "Seller" : "My orders"}</NavLink>)}
              {auth && auth.feePaid !== false && <NavLink to="/account">Account</NavLink>}
            </div>
            <div className="acts">
              <Link to="/cart" id="cartlink">Cart ({items.length})</Link>
              {auth ? <button className="ghost" onClick={() => { logout(); nav("/"); }}>Log out</button> : <Link to="/login">Log in</Link>}
              <button className="ghost" onClick={theme} aria-label="Switch light and dark theme">Theme</button>
            </div>
          </nav>
        </div>
      </header>
      <main className="w">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/browse" element={<Browse />} />
          <Route path="/product/:id" element={<Product />} />
          <Route path="/payment/success" element={<PaymentReturn />} />
          <Route path="/payment/cancel" element={<PaymentCancel />} />
          <Route path="/fee" element={<Fee />} />
          <Route path="/account" element={<RequireRole roles={["BUYER", "SELLER", "ADMIN"]}><Account /></RequireRole>} />
          <Route path="/projects" element={<Projects />} />
          <Route path="/projects/register" element={<RegisterProject />} />
          <Route path="/games" element={<Games />} />
          <Route path="/games/sell" element={<RegisterProject kind="game" />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/login" element={<Login />} />
          <Route path="/staff" element={<StaffLogin />} />
          <Route path="/buyer" element={<RequireRole roles={["BUYER", "SELLER", "ADMIN"]}><Buyer /></RequireRole>} />
          <Route path="/seller" element={<RequireRole roles={["SELLER"]}><Seller /></RequireRole>} />
          <Route path="/admin" element={<RequireRole roles={["ADMIN"]}><Admin /></RequireRole>} />
          <Route path="/about" element={<About />} />
          <Route path="/contact" element={<Contact />} />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/terms" element={<Terms />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
      <footer>
        <div className="w">
          <Link to="/privacy" style={{ marginRight: 14 }}>Privacy policy</Link>
          <Link to="/terms" style={{ marginRight: 14 }}>Terms and conditions</Link>
          <span>© 2026 AppBazaar.</span>
        </div>
      </footer>
    </>
  );
}
