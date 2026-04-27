/**
 * Seed script: 100 bots de restaurantes distribuidos por Mallorca con ofertas.
 *
 * Genera los restaurantes procedurally (20 zonas × ~5 variantes) para no tener
 * que escribir 100 entradas a mano. Todos se marcan `isBot: true` y con
 * suscripción activa hasta 2099 (bots exentos del trial).
 *
 * Idempotente: ID de oferta determinista por (businessId, title, día). Reejecutar
 * el mismo día sobreescribe en lugar de duplicar.
 *
 * Setup: copiar functions/.env.example → functions/.env y rellenar.
 * Uso:   node seed-bots-mallorca.js
 */

const { execSync } = require("child_process");
const config = require("./_seed-config");

const PROJECT_ID = config.PROJECT_ID;
const API_KEY = config.API_KEY;

// ── 20 zonas reales de Mallorca con coordenadas aproximadas ───────────
const zones = [
  { name: "Palma",                 lat: 39.5696, lng: 2.6502 },
  { name: "Port de Pollença",      lat: 39.9056, lng: 3.0869 },
  { name: "Alcúdia",               lat: 39.8529, lng: 3.1207 },
  { name: "Sóller",                lat: 39.7692, lng: 2.7149 },
  { name: "Port de Sóller",        lat: 39.7959, lng: 2.6937 },
  { name: "Valldemossa",           lat: 39.7117, lng: 2.6225 },
  { name: "Deià",                  lat: 39.7477, lng: 2.6480 },
  { name: "Manacor",               lat: 39.5696, lng: 3.2097 },
  { name: "Inca",                  lat: 39.7208, lng: 2.9121 },
  { name: "Cala Ratjada",          lat: 39.7060, lng: 3.4633 },
  { name: "Port d'Andratx",        lat: 39.5453, lng: 2.3893 },
  { name: "Santanyí",              lat: 39.3589, lng: 3.1259 },
  { name: "Colònia de Sant Jordi", lat: 39.3107, lng: 2.9928 },
  { name: "Artà",                  lat: 39.6931, lng: 3.1115 },
  { name: "Cala Millor",           lat: 39.5977, lng: 3.3858 },
  { name: "Portocolom",            lat: 39.4157, lng: 3.2739 },
  { name: "Cala d'Or",             lat: 39.3675, lng: 3.2350 },
  { name: "Pollença",              lat: 39.8775, lng: 3.0157 },
  { name: "Binissalem",            lat: 39.6867, lng: 2.8381 },
  { name: "Sa Pobla",              lat: 39.7694, lng: 3.0236 },
];

// ── Plantillas de nombres típicos mallorquines ─────────────────────────
const namePrefixes = ["Ca'n", "Ca na", "Es", "Sa", "Bar", "Cafè", "Restaurant", "Trattoria", "Celler", "Forn"];
const nameSuffixes = [
  "Pere", "Joan", "Toni", "Miquel", "Tomeu", "Jaume", "Biel", "Xesc", "Rafel",
  "Marina", "Pescador", "Molí", "Racó", "Cova", "Figuera", "Oliva", "Bon Gust",
  "Mariner", "Port", "Mallorca", "Tramuntana", "Serra", "Vistes", "Platja",
  "Olivera", "Ametller", "Palmera", "Font", "Xaloc", "Mestral",
];

// Tipos de cocina típicos en Mallorca
const cuisineSets = [
  ["Mallorquina", "Tradicional"],
  ["Mediterránea"],
  ["Marisquería"],
  ["Tapas", "Mediterránea"],
  ["Pizzería", "Italiana"],
  ["Vegana", "Saludable"],
  ["Parrilla", "Carnes"],
  ["Japonesa", "Asiática"],
  ["Casera", "Mallorquina"],
  ["Brunch", "Cafetería"],
];

// Plantillas de ofertas (title + description + price range + offerType + tags + dietaryInfo)
const offerTemplates = [
  { title: "Menú del día mallorquín", description: "Sopes mallorquines, tumbet, postre casero", priceBase: 13, offerType: "Menú del día", tags: ["Mallorquina"], dietary: { hasGluten: true }, includesDrink: true, includesDessert: true, serviceTime: "lunch" },
  { title: "Paella de marisco", description: "Arroz de gambas, mejillones, calamar y sepia", priceBase: 16, offerType: "Plato del día", tags: ["Paella", "Marisquería"], dietary: { hasFish: true }, includesDrink: true, serviceTime: "lunch" },
  { title: "Arroz brut", description: "Arroz caldoso tradicional con carne y verduras de temporada", priceBase: 14, offerType: "Menú del día", tags: ["Mallorquina", "Tradicional"], dietary: { hasMeat: true, hasGluten: true }, includesDrink: true, serviceTime: "lunch" },
  { title: "Ensaimada salada del chef", description: "Ensaimada rellena de sobrasada y queso manchego", priceBase: 9, offerType: "Oferta del día", tags: ["Mallorquina"], dietary: { hasMeat: true, hasLactose: true, hasGluten: true }, serviceTime: "both" },
  { title: "Frit mallorquí", description: "Plato típico: hígado, patata, cebolla, pimiento, guisantes", priceBase: 12, offerType: "Plato del día", tags: ["Mallorquina"], dietary: { hasMeat: true }, includesDrink: true, serviceTime: "lunch" },
  { title: "Pulpo a la gallega", description: "Pulpo cocido, patata, pimentón dulce y aceite de oliva", priceBase: 15, offerType: "Plato del día", tags: ["Marisquería"], dietary: { hasFish: true }, serviceTime: "both" },
  { title: "Tapa del día + caña", description: "Elección de tapa del día con caña de cerveza local", priceBase: 6, offerType: "Oferta del día", tags: ["Tapas"], dietary: {}, includesDrink: true, serviceTime: "both" },
  { title: "Pizza margherita artesanal", description: "Masa madre, mozzarella di bufala DOP, albahaca fresca", priceBase: 10, offerType: "Oferta permanente", tags: ["Pizzería", "Italiana"], dietary: { hasGluten: true, hasLactose: true }, isPermanent: true, serviceTime: "dinner" },
  { title: "Bowl vegano tropical", description: "Quinoa, aguacate, mango, edamame, tahini y semillas", priceBase: 11, offerType: "Oferta permanente", tags: ["Vegana", "Saludable"], dietary: { isVegan: true, isVegetarian: true, hasNuts: true }, isPermanent: true, serviceTime: "lunch" },
  { title: "Parrillada mixta", description: "Entraña, chorizo, morcilla, chimichurri y ensalada", priceBase: 17, offerType: "Menú del día", tags: ["Parrilla", "Carnes"], dietary: { hasMeat: true }, includesDrink: true, serviceTime: "dinner" },
  { title: "Combo sushi 12 piezas", description: "Nigiri y maki variados, sopa miso, edamame", priceBase: 15, offerType: "Menú del día", tags: ["Japonesa"], dietary: { hasFish: true }, includesDrink: true, serviceTime: "both" },
  { title: "Brunch completo", description: "Tostada de aguacate, huevos benedict, zumo, café", priceBase: 13, offerType: "Oferta permanente", tags: ["Brunch"], dietary: { hasEgg: true, hasGluten: true }, isPermanent: true, includesDrink: true, includesCoffee: true, serviceTime: "lunch" },
];

// Horarios típicos
const scheduleTemplates = [
  { open: "12:00", close: "23:00", closedDays: ["sunday"] },
  { open: "13:00", close: "00:00", closedDays: [] },
  { open: "09:00", close: "17:00", closedDays: ["monday"] },
  { open: "19:00", close: "01:00", closedDays: ["tuesday"] },
  { open: "12:30", close: "23:30", closedDays: ["wednesday"] },
];
const weekdays = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];
function makeSchedule({ open, close, closedDays }) {
  return weekdays.filter(d => !closedDays.includes(d)).map(day => ({ day, open, close }));
}

// PRNG determinista (LCG) para que el seed sea reproducible.
function lcg(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 0xFFFFFFFF;
  };
}
const rand = lcg(42); // misma seed → mismos bots en cada ejecución

function pick(arr) { return arr[Math.floor(rand() * arr.length)]; }
function jitter(base, delta) { return base + (rand() - 0.5) * delta * 2; }
function roundPrice(p) { return Math.round(p * 2) / 2; } // .00 o .50

// ── Generar 100 restaurantes ─────────────────────────────────────────
const restaurants = [];
for (let i = 0; i < 100; i++) {
  const zone = zones[i % zones.length];
  const prefix = pick(namePrefixes);
  const suffix = pick(nameSuffixes);
  const name = `${prefix} ${suffix}`;
  const cuisine = pick(cuisineSets);
  // Pequeño jitter alrededor del centro de cada zona (~1 km)
  const lat = jitter(zone.lat, 0.012);
  const lng = jitter(zone.lng, 0.015);
  const addressFormatted = `${name}, ${zone.name}, Mallorca`;
  const sched = pick(scheduleTemplates);
  // 1-3 ofertas por restaurante
  const numMenus = 1 + Math.floor(rand() * 3);
  const menus = [];
  const usedTemplates = new Set();
  for (let m = 0; m < numMenus; m++) {
    let tpl;
    let guard = 0;
    do {
      tpl = pick(offerTemplates);
      guard++;
    } while (usedTemplates.has(tpl.title) && guard < 6);
    usedTemplates.add(tpl.title);
    menus.push({
      title: tpl.title,
      description: tpl.description,
      price: roundPrice(tpl.priceBase + (rand() - 0.5) * 4), // ±2 €
      offerType: tpl.offerType,
      tags: tpl.tags,
      dietaryInfo: tpl.dietary,
      includesDrink: tpl.includesDrink || false,
      includesDessert: tpl.includesDessert || false,
      includesCoffee: tpl.includesCoffee || false,
      serviceTime: tpl.serviceTime || "both",
      isPermanent: tpl.isPermanent || false,
    });
  }
  // Teléfono sintético: 6 + i (asegura no colisionar con otros seeds; 100 → 612345700..99)
  const phone = `+346123457${String(i).padStart(2, "0")}`;
  restaurants.push({
    name,
    shortDescription: `Restaurante en ${zone.name} - ${cuisine.join(", ")}`,
    phone,
    cuisineTypes: cuisine,
    address: { formatted: addressFormatted, lat, lng, placeId: null },
    schedule: makeSchedule(sched),
    menus,
  });
}

// ── Placeholder food images ────────────────────────────────────────────
const foodImageUrls = [
  "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=600&q=80",
  "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=600&q=80",
  "https://images.unsplash.com/photo-1476224203421-9ac39bcb3327?w=600&q=80",
  "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=600&q=80",
  "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=600&q=80",
  "https://images.unsplash.com/photo-1540189549336-e6e99c3679fe?w=600&q=80",
  "https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?w=600&q=80",
  "https://images.unsplash.com/photo-1565958011703-44f9829ba187?w=600&q=80",
  "https://images.unsplash.com/photo-1482049016688-2d3e1b311543?w=600&q=80",
  "https://images.unsplash.com/photo-1484723091739-30a097e8f929?w=600&q=80",
  "https://images.unsplash.com/photo-1432139509613-5c4255a1d031?w=600&q=80",
  "https://images.unsplash.com/photo-1455619452474-d2be8b1e70cd?w=600&q=80",
  "https://images.unsplash.com/photo-1473093295043-cdd812d0e601?w=600&q=80",
  "https://images.unsplash.com/photo-1529042410759-befb1204b468?w=600&q=80",
  "https://images.unsplash.com/photo-1551183053-bf91a1d81141?w=600&q=80",
  "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=600&q=80",
  "https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=600&q=80",
  "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=600&q=80",
  "https://images.unsplash.com/photo-1504754524776-8f4f37790ca0?w=600&q=80",
  "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=600&q=80",
];

// ── Helpers ───────────────────────────────────────────────────────────
function fullDietaryInfo(partial = {}) {
  return {
    isVegetarian: false, isVegan: false, hasMeat: false, hasFish: false,
    hasGluten: false, hasLactose: false, hasNuts: false, hasEgg: false,
    ...partial,
  };
}

async function fetchJSON(url, options = {}) {
  const resp = await fetch(url, options);
  const body = await resp.json();
  if (!resp.ok) throw new Error(`${resp.status}: ${JSON.stringify(body.error || body)}`);
  return body;
}

// Admin REST endpoint (OAuth token, no API key) para evitar restricciones de la
// API key por app Android. Requiere que el usuario esté logueado con
// firebase-tools y sea owner/editor del proyecto.
async function createAuthUser(email, password, displayName) {
  const url = `https://identitytoolkit.googleapis.com/v1/projects/${PROJECT_ID}/accounts`;
  try {
    const data = await fetchJSON(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, password, displayName }),
    });
    return data.localId;
  } catch (err) {
    if (err.message.includes("EMAIL_EXISTS") || err.message.includes("DUPLICATE")) {
      // Buscar el usuario ya existente por email
      const lookup = await fetchJSON(
        `https://identitytoolkit.googleapis.com/v1/projects/${PROJECT_ID}/accounts:lookup`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken()}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ email: [email] }),
        }
      );
      const uid = lookup.users?.[0]?.localId;
      if (!uid) throw new Error(`EMAIL_EXISTS but lookup failed for ${email}`);
      return uid;
    }
    throw err;
  }
}

let _accessToken = null;
function accessToken() {
  if (_accessToken) return _accessToken;
  try {
    const configRaw = require("fs").readFileSync(
      require("path").join(require("os").homedir(), ".config/configstore/firebase-tools.json"),
      "utf8"
    );
    const cfg = JSON.parse(configRaw);
    const refreshToken = cfg.tokens?.refresh_token;
    if (!refreshToken) throw new Error("No refresh token");
    const resp = execSync(
      `curl -s -X POST "https://oauth2.googleapis.com/token" ` +
      `-H "Content-Type: application/x-www-form-urlencoded" ` +
      `-d "grant_type=refresh_token&refresh_token=${refreshToken}&client_id=${config.OAUTH_CLIENT_ID}&client_secret=${config.OAUTH_CLIENT_SECRET}"`,
      { encoding: "utf8" }
    );
    _accessToken = JSON.parse(resp).access_token;
    return _accessToken;
  } catch (e) {
    throw new Error("Cannot get access token. Run: npx firebase-tools login");
  }
}

const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

function toFirestoreValue(val) {
  if (val === null || val === undefined) return { nullValue: null };
  if (typeof val === "string") return { stringValue: val };
  if (typeof val === "boolean") return { booleanValue: val };
  if (typeof val === "number") {
    if (Number.isInteger(val)) return { integerValue: String(val) };
    return { doubleValue: val };
  }
  if (Array.isArray(val)) return { arrayValue: { values: val.map(toFirestoreValue) } };
  if (typeof val === "object") {
    const fields = {};
    for (const [k, v] of Object.entries(val)) fields[k] = toFirestoreValue(v);
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

async function firestoreSet(collection, docId, data) {
  const url = `${FIRESTORE_BASE}/${collection}/${docId}`;
  const fields = {};
  for (const [k, v] of Object.entries(data)) fields[k] = toFirestoreValue(v);
  await fetchJSON(url + "?currentDocument.exists=true", {
    method: "PATCH",
    headers: { Authorization: `Bearer ${accessToken()}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  }).catch(async () => {
    const createUrl = `${FIRESTORE_BASE}/${collection}?documentId=${docId}`;
    await fetchJSON(createUrl, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken()}`, "Content-Type": "application/json" },
      body: JSON.stringify({ fields }),
    });
  });
}

// ── Main ─────────────────────────────────────────────────────────────
async function seed() {
  console.log("Getting access token...");
  accessToken();
  console.log("Token OK\n");

  const now = new Date();
  const isoNow = now.toISOString();
  const todayYMD = isoNow.slice(0, 10);
  const demoUntilMs = new Date("2099-12-31T00:00:00Z").getTime();
  let imgIdx = 0;

  // Offset para no chocar con los emails de seed-bots.js / seed-bots-extra.js.
  // seed-bots: bot1..bot10; seed-bots-extra: bot11..bot65; seed-bots-mallorca: bot100..bot199.
  const emailOffset = 100;

  for (let i = 0; i < restaurants.length; i++) {
    const r = restaurants[i];
    const email = `bot${emailOffset + i}@eatout-test.com`;
    const password = config.BOT_PASSWORD;

    process.stdout.write(`[${i + 1}/100] ${r.name.padEnd(30)} (${r.address.formatted.split(",")[1]?.trim() || "?"})...`);

    try {
      const uid = await createAuthUser(email, password, r.name);

      await firestoreSet("users", uid, {
        id: uid, email, name: r.name, role: "COMERCIO", createdAt: isoNow,
      });

      await firestoreSet("businesses", uid, {
        id: uid, userId: uid, name: r.name, phone: r.phone,
        shortDescription: r.shortDescription, cuisineTypes: r.cuisineTypes,
        address: r.address, addressText: r.address.formatted,
        schedule: r.schedule || [], acceptsReservations: false,
        planTier: "free", isHighlighted: false,
        subscriptionStatus: "active", subscriptionActiveUntil: demoUntilMs,
        isBot: true, taxId: config.syntheticBotTaxId(uid),
        createdAt: isoNow,
      });

      for (const menu of r.menus) {
        const offerId = config.deterministicOfferId(uid, menu.title, todayYMD);
        const photoUrl = foodImageUrls[imgIdx % foodImageUrls.length];
        imgIdx++;
        await firestoreSet("dailyOffers", offerId, {
          id: offerId, businessId: uid, date: isoNow,
          title: menu.title, description: menu.description,
          priceTotal: menu.price, currency: "EUR",
          photoUrls: [photoUrl], tags: menu.tags || [],
          createdAt: isoNow, updatedAt: isoNow,
          isActive: true, isMerchantPro: false,
          dietaryInfo: fullDietaryInfo(menu.dietaryInfo),
          offerType: menu.offerType || null,
          includesDrink: menu.includesDrink,
          includesDessert: menu.includesDessert,
          includesCoffee: menu.includesCoffee,
          serviceTime: menu.serviceTime,
          isPermanent: menu.isPermanent,
        });
      }
      console.log(" ✓");
    } catch (e) {
      console.log(` ✗ ${e.message}`);
    }
  }

  console.log("\nSeed completado: 100 bots Mallorca.");
}

seed().catch(e => { console.error(e); process.exit(1); });
