#!/usr/bin/env node
/**
 * CLI para gestionar la promoción global (gratuidad para todos los merchants).
 *
 * Escribe `config/promo.freeUntil` (ms epoch) en Firestore, que es leído por
 * las Firestore rules + la app iOS/Android para saltarse la verificación de
 * suscripción individual mientras la promo esté activa.
 *
 * Uso:
 *   node set-promo.js                    # Muestra el estado actual
 *   node set-promo.js 60                 # Gratis los próximos 60 días
 *   node set-promo.js 2026-09-30         # Gratis hasta el 30/09/2026 (23:59 UTC)
 *   node set-promo.js off                # Desactiva la promo
 *
 * Requiere `firebase-tools login` (usa OAuth token de ~/.config/configstore).
 */

const { execSync } = require("child_process");
const config = require("./_seed-config");

function getAccessToken() {
  const cfgRaw = require("fs").readFileSync(
    require("path").join(require("os").homedir(), ".config/configstore/firebase-tools.json"),
    "utf8"
  );
  const cfg = JSON.parse(cfgRaw);
  const refreshToken = cfg.tokens?.refresh_token;
  if (!refreshToken) throw new Error("No refresh token. Run `npx firebase-tools login`.");
  const resp = execSync(
    `curl -s -X POST "https://oauth2.googleapis.com/token" ` +
    `-H "Content-Type: application/x-www-form-urlencoded" ` +
    `-d "grant_type=refresh_token&refresh_token=${refreshToken}&client_id=${config.OAUTH_CLIENT_ID}&client_secret=${config.OAUTH_CLIENT_SECRET}"`,
    { encoding: "utf8" }
  );
  return JSON.parse(resp).access_token;
}

const DOC_URL = `https://firestore.googleapis.com/v1/projects/${config.PROJECT_ID}/databases/(default)/documents/config/promo`;

async function readCurrent(token) {
  const resp = await fetch(DOC_URL, { headers: { Authorization: `Bearer ${token}` } });
  if (resp.status === 404) return null;
  const body = await resp.json();
  if (body.error) throw new Error(JSON.stringify(body.error));
  const raw = body.fields?.freeUntil;
  if (!raw) return null;
  if (raw.integerValue) return Number(raw.integerValue);
  if (raw.doubleValue) return Math.round(raw.doubleValue);
  return null;
}

async function write(token, freeUntilMs) {
  const body = freeUntilMs === null
    ? { fields: {} }
    : { fields: { freeUntil: { integerValue: String(freeUntilMs) } } };
  const resp = await fetch(DOC_URL, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const res = await resp.json();
  if (res.error) throw new Error(JSON.stringify(res.error));
}

function formatState(ms) {
  if (ms === null) return "Promo NO configurada (todos los merchants usan trial/suscripción individual).";
  const now = Date.now();
  const d = new Date(ms);
  const locale = "es-ES";
  const str = d.toLocaleString(locale, { dateStyle: "full", timeStyle: "short" });
  if (ms > now) {
    const daysLeft = Math.ceil((ms - now) / (24 * 60 * 60 * 1000));
    return `Promo ACTIVA: gratis hasta ${str} (${daysLeft} día${daysLeft === 1 ? "" : "s"} restantes).`;
  }
  return `Promo EXPIRADA: terminó el ${str}.`;
}

function parseArg(arg) {
  if (arg === "off" || arg === "0") return { action: "off" };
  // Número puro → días desde ahora
  if (/^\d+$/.test(arg)) {
    const days = parseInt(arg, 10);
    if (days <= 0) return { action: "off" };
    return { action: "set", ms: Date.now() + days * 24 * 60 * 60 * 1000 };
  }
  // ISO date (YYYY-MM-DD) o timestamp ms
  const parsed = new Date(arg);
  if (!isNaN(parsed.getTime())) {
    // Si la fecha no incluye hora, ponemos las 23:59 UTC
    const ms = /T|\s/.test(arg) ? parsed.getTime() : parsed.getTime() + 23 * 3600 * 1000 + 59 * 60 * 1000;
    return { action: "set", ms };
  }
  return null;
}

(async () => {
  const arg = process.argv[2];
  const token = getAccessToken();

  if (!arg) {
    const current = await readCurrent(token);
    console.log(formatState(current));
    console.log("\nUso:");
    console.log("  node set-promo.js 60              # Gratis los próximos 60 días");
    console.log("  node set-promo.js 2026-09-30      # Gratis hasta el 30/09/2026");
    console.log("  node set-promo.js off             # Desactiva la promo");
    return;
  }

  const parsed = parseArg(arg);
  if (!parsed) {
    console.error(`Argumento no reconocido: ${arg}`);
    console.error("Esperaba un número de días, una fecha ISO (YYYY-MM-DD) o 'off'.");
    process.exit(1);
  }

  if (parsed.action === "off") {
    await write(token, null);
    console.log("✓ Promo desactivada. Los merchants ahora se rigen por su trial/suscripción individual.");
    return;
  }

  const current = await readCurrent(token);
  if (current !== null) console.log("Estado previo:", formatState(current));

  await write(token, parsed.ms);
  console.log("✓ Promo actualizada.");
  console.log(formatState(parsed.ms));
})().catch(err => { console.error("Fallo:", err.message); process.exit(1); });
