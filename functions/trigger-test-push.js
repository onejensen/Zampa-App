#!/usr/bin/env node
/**
 * Crea una oferta fresca para bot57 (Marc Fosh, Pro) y fuerza el disparo
 * de onMenuPublished con el nuevo sonido de campanilla.
 *
 * No depende de la API key de Firebase (esquiva la restricción de package).
 * Usa el OAuth refresh token de firebase CLI.
 */

const { execSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const seedConfig = require("./_seed-config");

const PROJECT_ID = seedConfig.PROJECT_ID;
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

function accessToken() {
  const configRaw = fs.readFileSync(
    path.join(os.homedir(), ".config/configstore/firebase-tools.json"),
    "utf8"
  );
  const config = JSON.parse(configRaw);
  const refreshToken = config.tokens?.refresh_token;
  const resp = execSync(
    `curl -s -X POST "https://oauth2.googleapis.com/token" ` +
    `-H "Content-Type: application/x-www-form-urlencoded" ` +
    `-d "grant_type=refresh_token&refresh_token=${refreshToken}&client_id=${seedConfig.OAUTH_CLIENT_ID}&client_secret=${seedConfig.OAUTH_CLIENT_SECRET}"`,
    { encoding: "utf8" }
  );
  return JSON.parse(resp).access_token;
}

async function fetchJSON(url, options = {}) {
  const resp = await fetch(url, options);
  const body = await resp.json();
  if (!resp.ok) throw new Error(`${resp.status}: ${JSON.stringify(body.error || body)}`);
  return body;
}

function toFirestoreValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "string") return { stringValue: v };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toFirestoreValue) } };
  if (typeof v === "object") {
    const fields = {};
    for (const [k, val] of Object.entries(v)) fields[k] = toFirestoreValue(val);
    return { mapValue: { fields } };
  }
  return { stringValue: String(v) };
}

function findBotUid(email) {
  const tmp = path.join(os.tmpdir(), `zampa-bots-${Date.now()}.json`);
  execSync(`firebase auth:export "${tmp}" --format=JSON --project ${PROJECT_ID}`,
    { stdio: ["ignore", "ignore", "inherit"] });
  const data = JSON.parse(fs.readFileSync(tmp, "utf8"));
  fs.unlinkSync(tmp);
  const user = (data.users || []).find(u => u.email === email);
  if (!user) throw new Error(`No encontré usuario con email ${email}`);
  return user.localId;
}

async function main() {
  console.log("→ Buscando UID de bot57 (Marc Fosh)…");
  const uid = findBotUid("bot57@eatout-test.com");
  console.log(`  uid = ${uid}\n`);

  const token = accessToken();
  const now = new Date();
  const isoNow = now.toISOString();
  const title = `Campanilla test ${now.toISOString().slice(11, 19)}`;
  const offerId = require("crypto").randomUUID();

  const data = {
    id: offerId,
    businessId: uid,
    date: isoNow,
    title,
    description: "Oferta de prueba para verificar el sonido de campanilla en la notificación push.",
    priceTotal: 19.90,
    currency: "EUR",
    photoUrls: ["https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800&q=80"],
    tags: ["test", "push", "campanilla"],
    createdAt: isoNow,
    updatedAt: isoNow,
    isActive: true,
    isMerchantPro: true,
    dietaryInfo: {
      isVegetarian: false, isVegan: false, hasMeat: true, hasFish: false,
      hasGluten: false, hasLactose: true, hasNuts: false, hasEgg: false,
    },
    offerType: "Plato del día",
    includesDrink: false,
    includesDessert: false,
    includesCoffee: false,
    serviceTime: "lunch",
    isPermanent: false,
  };

  const fields = {};
  for (const [k, v] of Object.entries(data)) fields[k] = toFirestoreValue(v);

  console.log(`→ Creando oferta "${title}"…`);
  await fetchJSON(`${FIRESTORE_BASE}/dailyOffers?documentId=${offerId}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  });
  console.log(`  ✓ oferta creada: ${offerId}`);
  console.log(`\nLa Cloud Function onMenuPublished debería disparar push a los`);
  console.log(`seguidores de Marc Fosh en los próximos segundos con la nueva campanilla.`);
}

main().catch(e => { console.error("Fallo:", e); process.exit(1); });
