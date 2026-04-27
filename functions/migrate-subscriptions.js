#!/usr/bin/env node
/**
 * Backfill de campos de suscripción en `businesses/{id}`.
 *
 * Cambios en el modelo (2026-04-20): todos los merchants ahora tienen suscripción
 * de pago (14,99 €/mes) con 90 días de prueba desde `createdAt`.
 *
 * Idempotente: solo escribe los campos que falten.
 *
 *   node migrate-subscriptions.js              # dry-run (no escribe)
 *   node migrate-subscriptions.js --apply      # aplica cambios
 *
 * Usa OAuth token de firebase-tools (igual que seed-bots-mallorca.js). No
 * necesita credenciales Admin SDK.
 */

const { execSync } = require("child_process");
const config = require("./_seed-config");

const APPLY = process.argv.includes("--apply");
const TRIAL_DAYS = 90;
const DEMO_UNTIL_MS = new Date("2099-12-31T00:00:00Z").getTime();

// ── OAuth token via firebase-tools (copiado de seed-bots-mallorca.js) ──
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

function toFirestoreValue(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === "string") return { stringValue: val };
  if (typeof val === "boolean") return { booleanValue: val };
  if (typeof val === "number") {
    if (Number.isInteger(val)) return { integerValue: String(val) };
    return { doubleValue: val };
  }
  return { stringValue: String(val) };
}

function fromFirestoreValue(v) {
  if (!v) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  if ("nullValue" in v) return null;
  return null;
}

async function listBusinesses() {
  const url = `${FIRESTORE_BASE}:runQuery`;
  const query = { structuredQuery: { from: [{ collectionId: "businesses" }] } };
  const resp = await fetchJSON(url, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken()}`, "Content-Type": "application/json" },
    body: JSON.stringify(query),
  });
  return resp.filter(r => r.document).map(r => ({
    id: r.document.name.split("/").pop(),
    data: Object.fromEntries(
      Object.entries(r.document.fields || {}).map(([k, v]) => [k, fromFirestoreValue(v)])
    ),
  }));
}

async function patchBusiness(id, update) {
  // PATCH con updateMask para escribir solo los campos nuevos sin tocar el resto.
  const maskParams = Object.keys(update).map(k => `updateMask.fieldPaths=${encodeURIComponent(k)}`).join("&");
  const url = `${FIRESTORE_BASE}/businesses/${id}?${maskParams}`;
  const fields = Object.fromEntries(Object.entries(update).map(([k, v]) => [k, toFirestoreValue(v)]));
  await fetchJSON(url, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${accessToken()}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  });
}

(async () => {
  console.log("Getting OAuth token...");
  accessToken();
  console.log("Token OK.\n");

  const businesses = await listBusinesses();
  console.log(`Businesses encontrados: ${businesses.length}`);

  let toUpdate = 0;
  let botCount = 0;
  let skippedAlreadyDone = 0;
  const writes = [];

  for (const biz of businesses) {
    const data = biz.data;
    const update = {};
    const looksLikeBot = data.isBot === true;

    if (looksLikeBot) {
      botCount++;
      if (data.subscriptionStatus !== "active" || data.subscriptionActiveUntil !== DEMO_UNTIL_MS) {
        update.subscriptionStatus = "active";
        update.subscriptionActiveUntil = DEMO_UNTIL_MS;
      }
      if (!data.taxId) {
        update.taxId = config.syntheticBotTaxId(biz.id);
      }
    } else {
      if (typeof data.subscriptionStatus !== "string") update.subscriptionStatus = "trial";
      if (typeof data.trialEndsAt !== "number") {
        update.trialEndsAt = Date.now() + TRIAL_DAYS * 24 * 60 * 60 * 1000;
      }
    }

    if (Object.keys(update).length === 0) {
      skippedAlreadyDone++;
      continue;
    }

    toUpdate++;
    console.log(`  [${biz.id}] ${data.name || "(sin nombre)"} →`, update);
    writes.push({ id: biz.id, update });
  }

  console.log("");
  console.log(`Resumen:`);
  console.log(`  Bots detectados:  ${botCount}`);
  console.log(`  A actualizar:     ${toUpdate}`);
  console.log(`  Ya migrados:      ${skippedAlreadyDone}`);

  if (!APPLY) {
    console.log("\nDry run. Pasa --apply para escribir los cambios.");
    return;
  }

  for (let i = 0; i < writes.length; i++) {
    const w = writes[i];
    try {
      await patchBusiness(w.id, w.update);
      process.stdout.write(".");
    } catch (e) {
      console.log(`\n  ✗ ${w.id}: ${e.message}`);
    }
  }
  console.log("\nMigración completada.");
})().catch(err => { console.error("Fallo en migración:", err); process.exit(1); });
