#!/usr/bin/env node
/**
 * Backfill del campo `isVerified` en `businesses/{id}` y denormalización
 * `isMerchantVerified` en `dailyOffers/{id}`.
 *
 * Estrategia:
 *   - Comercios bot (isBot === true) → isVerified = true (los seedeamos como confiables).
 *   - Resto de comercios existentes → isVerified = true (no los queremos romper retroactivamente).
 *   - dailyOffers de cualquier comercio existente → isMerchantVerified = true.
 *
 * Los nuevos registros tras esta migración nacen con `isVerified: false` y deben
 * ser aprobados manualmente desde Firebase Console (cola en `pendingVerifications`).
 *
 * Idempotente: solo escribe los campos que falten.
 *
 *   node migrate-isVerified.js              # dry-run (no escribe)
 *   node migrate-isVerified.js --apply      # aplica cambios
 */

const { execSync } = require("child_process");
const config = require("./_seed-config");

const APPLY = process.argv.includes("--apply");

// ── OAuth token via firebase-tools (mismo patrón que migrate-subscriptions.js) ──
let _accessToken = null;
function accessToken() {
  if (_accessToken) return _accessToken;
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
  _accessToken = JSON.parse(resp).access_token;
  return _accessToken;
}

const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${config.PROJECT_ID}/databases/(default)/documents`;

async function fetchJSON(url, options = {}) {
  const resp = await fetch(url, options);
  const body = await resp.json();
  if (!resp.ok) throw new Error(`${resp.status}: ${JSON.stringify(body.error || body)}`);
  return body;
}

function toFs(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "string") return { stringValue: v };
  return { stringValue: String(v) };
}

function fromFs(v) {
  if (!v) return null;
  if ("booleanValue" in v) return v.booleanValue;
  if ("stringValue" in v) return v.stringValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("nullValue" in v) return null;
  return null;
}

async function listAll(collectionId) {
  const url = `${FIRESTORE_BASE}:runQuery`;
  const query = { structuredQuery: { from: [{ collectionId }] } };
  const resp = await fetchJSON(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken()}`, "Content-Type": "application/json" },
    body: JSON.stringify(query),
  });
  return resp.filter(r => r.document).map(r => ({
    id: r.document.name.split("/").pop(),
    data: Object.fromEntries(
      Object.entries(r.document.fields || {}).map(([k, v]) => [k, fromFs(v)])
    ),
  }));
}

async function patch(collection, id, update) {
  const maskParams = Object.keys(update).map(k => `updateMask.fieldPaths=${encodeURIComponent(k)}`).join("&");
  const url = `${FIRESTORE_BASE}/${collection}/${id}?${maskParams}`;
  const fields = Object.fromEntries(Object.entries(update).map(([k, v]) => [k, toFs(v)]));
  await fetchJSON(url, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${accessToken()}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  });
}

(async () => {
  console.log(`Mode: ${APPLY ? "APPLY" : "dry-run"}\n`);
  accessToken();

  // 1. Backfill businesses
  const businesses = await listAll("businesses");
  console.log(`Businesses encontrados: ${businesses.length}`);
  let bizUpdates = 0;
  for (const biz of businesses) {
    if (biz.data.isVerified === true) continue;
    bizUpdates++;
    console.log(`  [biz ${biz.id}] ${biz.data.name || "(sin nombre)"} → isVerified=true`);
    if (APPLY) await patch("businesses", biz.id, { isVerified: true });
  }
  console.log(`Businesses a actualizar: ${bizUpdates}\n`);

  // 2. Backfill dailyOffers
  const offers = await listAll("dailyOffers");
  console.log(`dailyOffers encontrados: ${offers.length}`);
  let offerUpdates = 0;
  for (const offer of offers) {
    if (offer.data.isMerchantVerified === true) continue;
    offerUpdates++;
    if (APPLY) await patch("dailyOffers", offer.id, { isMerchantVerified: true });
  }
  console.log(`dailyOffers a actualizar: ${offerUpdates}\n`);

  if (!APPLY) {
    console.log("Dry-run: no se ha escrito nada. Ejecuta con --apply para aplicar.");
  } else {
    console.log("Migración aplicada.");
  }
})().catch(err => {
  console.error("Error:", err);
  process.exit(1);
});
