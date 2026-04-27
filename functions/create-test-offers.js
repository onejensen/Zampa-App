/**
 * Creates a new daily offer from Marc Fosh (bot57) and Ca'n Joan de s'Aigo (bot56)
 * to test push notifications.
 *
 * Usage: node create-test-offers.js
 */

const { execSync } = require("child_process");
const seedConfig = require("./_seed-config");

const PROJECT_ID = seedConfig.PROJECT_ID;
const API_KEY = seedConfig.API_KEY;


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

// Sign in and get UID + idToken
async function signIn(email, password) {
  const url = `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}`;
  const data = await fetchJSON(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  return { uid: data.localId, idToken: data.idToken };
}

// Get OAuth access token from firebase-tools
function accessToken() {
  const configRaw = require("fs").readFileSync(
    require("path").join(require("os").homedir(), ".config/configstore/firebase-tools.json"),
    "utf8"
  );
  const config = JSON.parse(configRaw);
  const refreshToken = config.tokens?.refresh_token;
  if (!refreshToken) throw new Error("No refresh token");
  const resp = execSync(
    `curl -s -X POST "https://oauth2.googleapis.com/token" ` +
    `-H "Content-Type: application/x-www-form-urlencoded" ` +
    `-d "grant_type=refresh_token&refresh_token=${refreshToken}&client_id=${seedConfig.OAUTH_CLIENT_ID}&client_secret=${seedConfig.OAUTH_CLIENT_SECRET}"`,
    { encoding: "utf8" }
  );
  return JSON.parse(resp).access_token;
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
  if (Array.isArray(val)) {
    return { arrayValue: { values: val.map(toFirestoreValue) } };
  }
  if (typeof val === "object") {
    const fields = {};
    for (const [k, v] of Object.entries(val)) {
      fields[k] = toFirestoreValue(v);
    }
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

async function firestoreCreate(collection, docId, data, token) {
  const fields = {};
  for (const [key, value] of Object.entries(data)) {
    fields[key] = toFirestoreValue(value);
  }
  const url = `${FIRESTORE_BASE}/${collection}?documentId=${docId}`;
  await fetchJSON(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields }),
  });
}

const foodImages = [
  "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=600&q=80",
  "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=600&q=80",
];

async function main() {
  console.log("Getting access token...");
  const token = accessToken();
  console.log("Token OK\n");

  const now = new Date();
  const isoNow = now.toISOString();
  const todayYMD = isoNow.slice(0, 10);

  // --- Marc Fosh (bot57, isPro: true) ---
  console.log("Signing in as Marc Fosh (bot57)...");
  const fosh = await signIn("bot57@eatout-test.com", seedConfig.BOT_PASSWORD);
  const foshTitle = "Menu Primavera Fosh";
  const foshOfferId = seedConfig.deterministicOfferId(fosh.uid, foshTitle, todayYMD);

  await firestoreCreate("dailyOffers", foshOfferId, {
    id: foshOfferId,
    businessId: fosh.uid,
    date: isoNow,
    title: foshTitle,
    description: "Tartar de gamba roja de Soller, lubina salvaje con espuma de azafran mallorquin, mousse de almendra con higos",
    priceTotal: 55.00,
    currency: "EUR",
    photoUrls: [foodImages[0]],
    tags: ["Gourmet", "Mediterranea"],
    createdAt: isoNow,
    updatedAt: isoNow,
    isActive: true,
    isMerchantPro: true,
    dietaryInfo: fullDietaryInfo({ hasFish: true, hasLactose: true, hasNuts: true }),
    offerType: "Menú del día",
    includesDrink: true,
    includesDessert: true,
    includesCoffee: true,
    serviceTime: "lunch",
    isPermanent: false,
  }, token);

  console.log(`  Marc Fosh offer created: ${foshOfferId}`);

  // --- Ca'n Joan de s'Aigo (bot56, free) ---
  console.log("Signing in as Ca'n Joan de s'Aigo (bot56)...");
  const joan = await signIn("bot56@eatout-test.com", seedConfig.BOT_PASSWORD);
  const joanTitle = "Merienda Mallorquina Especial";
  const joanOfferId = seedConfig.deterministicOfferId(joan.uid, joanTitle, todayYMD);

  await firestoreCreate("dailyOffers", joanOfferId, {
    id: joanOfferId,
    businessId: joan.uid,
    date: isoNow,
    title: joanTitle,
    description: "Ensaimada rellena de cabello de angel artesanal + coca de patata con sobrasada + chocolate caliente a la taza",
    priceTotal: 8.90,
    currency: "EUR",
    photoUrls: [foodImages[1]],
    tags: ["Mallorquina", "Cafeteria"],
    createdAt: isoNow,
    updatedAt: isoNow,
    isActive: true,
    isMerchantPro: false,
    dietaryInfo: fullDietaryInfo({ hasMeat: true, hasGluten: true, hasLactose: true, hasEgg: true }),
    offerType: "Oferta permanente",
    includesDrink: true,
    includesDessert: false,
    includesCoffee: false,
    serviceTime: "both",
    isPermanent: false,
  }, token);

  console.log(`  Ca'n Joan de s'Aigo offer created: ${joanOfferId}`);

  console.log("\nDone! Two new offers created.");
  console.log("Marc Fosh (Pro) should trigger push notifications to followers.");
  console.log("Ca'n Joan de s'Aigo (free) should NOT trigger push notifications.");
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Failed:", err);
    process.exit(1);
  });
