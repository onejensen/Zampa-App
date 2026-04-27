#!/usr/bin/env node
/**
 * Añade `coverPhotoUrl` y `profilePhotoUrl` a todos los bots existentes.
 * Idempotente: solo escribe los campos que falten o estén vacíos.
 *
 * Uso:
 *   node add-bot-photos.js              # dry-run
 *   node add-bot-photos.js --apply
 */

const { execSync } = require("child_process");
const config = require("./_seed-config");

const APPLY = process.argv.includes("--apply");

// Unsplash (free, attribution opcional). Asignación determinista por uid.
const COVER_PHOTOS = [
  "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=1200&q=80", // restaurant interior warm
  "https://images.unsplash.com/photo-1559339352-11d035aa65de?w=1200&q=80",   // bar terrace
  "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=1200&q=80", // food on table
  "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=1200&q=80",   // chef plating
  "https://images.unsplash.com/photo-1555992336-fb0d29498b13?w=1200&q=80",   // tapas spread
  "https://images.unsplash.com/photo-1466978913421-dad2ebd01d17?w=1200&q=80", // restaurant interior modern
  "https://images.unsplash.com/photo-1481833761820-0509d3217039?w=1200&q=80", // pizza wood oven
  "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=1200&q=80", // tapas
  "https://images.unsplash.com/photo-1424847651672-bf20a4b0982b?w=1200&q=80", // cafe interior
  "https://images.unsplash.com/photo-1592861956120-e524fc739696?w=1200&q=80", // ramen bowl
  "https://images.unsplash.com/photo-1485921325833-c519f76c4927?w=1200&q=80", // cafe latte art
  "https://images.unsplash.com/photo-1559339352-11d035aa65de?w=1200&q=80",   // outdoor seating
  "https://images.unsplash.com/photo-1525351484163-7529414344d8?w=1200&q=80", // spanish food
  "https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?w=1200&q=80", // pancakes
  "https://images.unsplash.com/photo-1565299507177-b0ac66763828?w=1200&q=80", // pizza closeup
  "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=1200&q=80", // tapas variety
  "https://images.unsplash.com/photo-1428515613728-6b4607e44363?w=1200&q=80", // grill steak
  "https://images.unsplash.com/photo-1482049016688-2d3e1b311543?w=1200&q=80", // pasta plate
  "https://images.unsplash.com/photo-1543007630-9710e4a00a20?w=1200&q=80",   // sushi
  "https://images.unsplash.com/photo-1551218808-94e220e084d2?w=1200&q=80",   // mediterranean spread
];

const PROFILE_PHOTOS = [
  "https://images.unsplash.com/photo-1577219491135-ce391730fb2c?w=400&q=80", // chef hat
  "https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?w=400&q=80", // restaurant front
  "https://images.unsplash.com/photo-1466978913421-dad2ebd01d17?w=400&q=80", // restaurant sign
  "https://images.unsplash.com/photo-1559339352-11d035aa65de?w=400&q=80",   // outdoor cafe
  "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=400&q=80",   // chef close
  "https://images.unsplash.com/photo-1571997478779-2adcbbe9ab2f?w=400&q=80", // bar interior
  "https://images.unsplash.com/photo-1592861956120-e524fc739696?w=400&q=80", // ramen
  "https://images.unsplash.com/photo-1424847651672-bf20a4b0982b?w=400&q=80", // cafe
  "https://images.unsplash.com/photo-1481833761820-0509d3217039?w=400&q=80", // wood oven
  "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=400&q=80", // dim restaurant
  "https://images.unsplash.com/photo-1485921325833-c519f76c4927?w=400&q=80", // latte art
  "https://images.unsplash.com/photo-1543007630-9710e4a00a20?w=400&q=80",   // sushi close
];

// Hash determinista para asignación estable de fotos
function deterministicIndex(str, modulo) {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0;
  return Math.abs(h) % modulo;
}

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

function fromFirestoreValue(v) {
  if (!v) return null;
  if ("stringValue" in v) return v.stringValue;
  if ("booleanValue" in v) return v.booleanValue;
  if ("integerValue" in v) return Number(v.integerValue);
  if ("doubleValue" in v) return v.doubleValue;
  return null;
}

async function listBots() {
  // Trae TODOS los businesses; filtramos localmente por los que falta foto.
  // Esto cubre tanto los seed-bots viejos (sin isBot) como los Mallorca (con isBot=true).
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
  const maskParams = Object.keys(update).map(k => `updateMask.fieldPaths=${encodeURIComponent(k)}`).join("&");
  const url = `${FIRESTORE_BASE}/businesses/${id}?${maskParams}`;
  const fields = Object.fromEntries(
    Object.entries(update).map(([k, v]) => [k, { stringValue: v }])
  );
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

  const bots = await listBots();
  console.log(`Bots encontrados: ${bots.length}\n`);

  const writes = [];
  let alreadyOk = 0;

  for (const bot of bots) {
    const update = {};
    if (!bot.data.coverPhotoUrl) {
      update.coverPhotoUrl = COVER_PHOTOS[deterministicIndex(bot.id + "::cover", COVER_PHOTOS.length)];
    }
    if (!bot.data.profilePhotoUrl) {
      update.profilePhotoUrl = PROFILE_PHOTOS[deterministicIndex(bot.id + "::profile", PROFILE_PHOTOS.length)];
    }
    if (Object.keys(update).length === 0) {
      alreadyOk++;
      continue;
    }
    writes.push({ id: bot.id, name: bot.data.name, update });
  }

  console.log(`A actualizar: ${writes.length}`);
  console.log(`Ya con fotos: ${alreadyOk}`);

  if (!APPLY) {
    console.log("\nDry-run. Pasa --apply para escribir.");
    if (writes.length > 0) {
      console.log("\nEjemplo de las primeras 3 actualizaciones:");
      writes.slice(0, 3).forEach(w => console.log(`  [${w.id}] ${w.name}`, w.update));
    }
    return;
  }

  for (let i = 0; i < writes.length; i++) {
    try {
      await patchBusiness(writes[i].id, writes[i].update);
      process.stdout.write(".");
      if ((i + 1) % 50 === 0) process.stdout.write(` ${i + 1}\n`);
    } catch (e) {
      console.log(`\n  ✗ ${writes[i].id}: ${e.message}`);
    }
  }
  console.log("\n\n✓ Completado.");
})().catch(err => { console.error("Fallo:", err.message); process.exit(1); });
