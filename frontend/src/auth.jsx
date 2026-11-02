import { createContext, useContext, useState } from "react";
import { getAuth, setAuth } from "./api";

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);

export function AuthProvider({ children }) {
  const [auth, set] = useState(getAuth());
  const login = (a) => { setAuth(a); set(a); };
  const logout = () => { setAuth(null); set(null); };
  return <Ctx.Provider value={{ auth, login, logout }}>{children}</Ctx.Provider>;
}
