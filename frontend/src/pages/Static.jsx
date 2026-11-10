import { Link } from "react-router-dom";
import { useToast } from "../ui";

const Page = ({ title, children }) => (
  <section><h2>{title}</h2><p style={{ maxWidth: "62ch" }}>{children}</p></section>
);

export const About = () => (
  <Page title="About AppBazaar">AppBazaar connects developers, startups, freelancers and agencies in one marketplace for software, apps, templates, AI tools and services. We start small, review every listing, and grow from there.</Page>
);
export const Privacy = () => (
  <Page title="Privacy policy">We store only what we need to run your account and orders. Passwords are encrypted, and payments are handled by the gateway. We never sell your data.</Page>
);
export const Terms = () => (
  <Page title="Terms and conditions">Sellers must own what they list. Buyers receive a licence as described on the product page. AppBazaar keeps a commission on each sale and may remove listings that break these terms. Games and game code: sell only your own or properly licensed work. Selling game accounts, game keys, in-game items or currency, and cracked or pirated games is not allowed.</Page>
);
export const NotFound = () => (
  <Page title="Page not found">That page doesn’t exist. Head back to the <Link to="/">home page</Link>.</Page>
);

export function Contact() {
  const toast = useToast();
  return (
    <section>
      <h2>Contact us</h2>
      <form onSubmit={(e) => { e.preventDefault(); e.target.reset(); toast("Message sent. We reply within two days."); }}>
        <label>Email<input type="email" required /></label>
        <label>Message<textarea rows="4" required /></label>
        <button>Send message</button>
      </form>
    </section>
  );
}
