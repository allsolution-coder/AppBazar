import { createContext, useContext, useEffect, useState } from "react";

const Ctx = createContext(null);
export const useCart = () => useContext(Ctx);

export function CartProvider({ children }) {
  const [items, setItems] = useState(() => {
    try { return JSON.parse(localStorage.getItem("ab-cart")) || []; } catch { return []; }
  });
  useEffect(() => { try { localStorage.setItem("ab-cart", JSON.stringify(items)); } catch { /* storage blocked */ } }, [items]);
  const add = (p) => setItems((x) => (x.some((i) => i.id === p.id) ? x : [...x, p]));
  const remove = (id) => setItems((x) => x.filter((i) => i.id !== id));
  const clear = () => setItems([]);
  return <Ctx.Provider value={{ items, add, remove, clear }}>{children}</Ctx.Provider>;
}
