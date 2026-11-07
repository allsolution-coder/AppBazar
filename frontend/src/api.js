import { useEffect, useState } from "react";

const KEY = "ab-auth";
// empty locally (Vite proxies /api); the backend address when the site is deployed
const API = (import.meta.env.VITE_API_URL || "").replace(/\/$/, "");

export const getAuth = () => {
  try { return JSON.parse(localStorage.getItem(KEY)); } catch { return null; }
};
export const setAuth = (a) => (a ? localStorage.setItem(KEY, JSON.stringify(a)) : localStorage.removeItem(KEY));

export async function api(path, { method = "GET", body } = {}) {
  const a = getAuth();
  const res = await fetch(API + "/api" + path, {
    method,
    headers: { ...(body instanceof FormData ? {} : { "Content-Type": "application/json" }), ...(a ? { Authorization: "Bearer " + a.token } : {}) },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    let msg = "Something went wrong";
    try { const j = await res.json(); msg = j.message || j.error || msg; } catch { /* no json body */ }
    if (res.status === 401 && a) { setAuth(null); location.assign("/login"); }
    throw new Error(msg);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export async function downloadFile(productId, name) {
  const a = getAuth();
  const res = await fetch(`${API}/api/files/${productId}`, { headers: a ? { Authorization: "Bearer " + a.token } : {} });
  if (!res.ok) {
    let msg = "Download failed";
    try { const j = await res.json(); msg = j.message || msg; } catch { /* no json body */ }
    throw new Error(msg);
  }
  const url = URL.createObjectURL(await res.blob());
  const link = document.createElement("a");
  link.href = url; link.download = name || "download";
  document.body.appendChild(link); link.click(); link.remove();
  URL.revokeObjectURL(url);
}

export function useApi(path) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let live = true;
    setError("");
    api(path).then((d) => live && setData(d)).catch((e) => live && setError(e.message));
    return () => { live = false; };
  }, [path, tick]);
  return { data, error, reload: () => setTick((t) => t + 1) };
}
