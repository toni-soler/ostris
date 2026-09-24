import es from "./locales/es.json";
import en from "./locales/en.json";

// IDAX Shell renders this extension's registered component with ITS OWN React reconciler
// (ModuleHost.jsx: `return <Component />`). A separate, esbuild-bundled copy of `react` here
// would have its own module-local dispatcher, which is never active outside a render pass by
// its own reconciler - Shell's render pass would call this component's hooks against a null
// dispatcher ("Invalid hook call" / "Cannot read properties of null (reading 'useState')"),
// leaving the whole app blank. Use Shell's own shared React instance instead, exactly like the
// Ledger extension does - never `import React from "react"` in a Shell module extension.
const sdk = window.__IDAX_MODULE_SDK__;
const { React, router, i18n, fetchWithAuth } = sdk;
const { useLocation, useNavigate } = router;
const api = async (path, options = {}) => { const response = await fetchWithAuth(`/api/ostris${path}`, { headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options }); const body = await response.json().catch(() => ({})); if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`); return body; };

i18n.addResourceBundle("es", "translation", es, true, true); i18n.addResourceBundle("en", "translation", en, true, true);

function Shell() {
  const navigate = useNavigate(); const location = useLocation(); const [error, setError] = React.useState(""); const [result, setResult] = React.useState(null); const [loading, setLoading] = React.useState(false);
  const [form, setForm] = React.useState({ communityId: "", unitId: "", transactionId: "", purpose: "EXCHANGE", entries: "[]" });
  const set = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const submit = async (event) => { event.preventDefault(); setError(""); setResult(null); setLoading(true); try { setResult(await api("/transactions/proposals", { method: "POST", body: JSON.stringify({ ...form, entries: JSON.parse(form.entries) }) })); } catch (e) { setError(e.message); } finally { setLoading(false); } };
  return <main className="ostris-module"><header className="module-heading"><div><small>IDAX / osTRIS</small><h2>Gobernanza y operaciones verificables</h2></div><button onClick={() => navigate("/")}>Volver al workspace</button></header><nav className="module-tabs"><button className={!location.pathname.includes("propose") ? "active" : ""} onClick={() => navigate("/ostris")}>Resumen</button><button className={location.pathname.includes("propose") ? "active" : ""} onClick={() => navigate("/ostris/propose")}>Proponer transacción</button></nav>{location.pathname.includes("propose") ? <section className="module-panel"><h3>Proponer una transacción</h3><p>El backend valida permisos, firmas y reglas de gobernanza antes de comprometerla.</p><form onSubmit={submit} className="module-form">{[["communityId","Community ID"],["unitId","Unit ID"],["transactionId","Transaction ID"],["purpose","Purpose"]].map(([key,label]) => <label key={key}>{label}<input value={form[key]} onChange={(e) => set(key, e.target.value)} required /></label>)}<label>Entries JSON<textarea value={form.entries} onChange={(e) => set("entries", e.target.value)} /></label>{error && <p className="error">{error}</p>}<button disabled={loading}>{loading ? "Enviando…" : "Crear propuesta"}</button>{result && <pre>{JSON.stringify(result, null, 2)}</pre>}</form></section> : <section className="module-cards"><article><strong>Identidad y continuidad</strong><p>Consulta decisiones de continuidad y evidencia privada mediante la API protegida.</p></article><article><strong>Transacciones gobernadas</strong><p>Propón, autoriza y compromete operaciones con trazabilidad verificable.</p></article><article><strong>Runtime open core</strong><p>El módulo comparte sesión, tenant, permisos y navegación con IDAX Shell.</p></article></section>}</main>;
}

window.__IDAX_MODULE_EXTENSIONS__ = window.__IDAX_MODULE_EXTENSIONS__ || {};
window.__IDAX_MODULE_EXTENSIONS__.ostris = { component: Shell };
window.dispatchEvent(new CustomEvent("idaxModuleExtensionRegistered", { detail: { module: "ostris" } }));
