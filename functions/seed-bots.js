/**
 * Seed script: creates 10 test restaurant bots with daily offers.
 * Uses firebase-tools credentials (no service account needed).
 *
 * Setup: copy functions/.env.example to functions/.env and fill in values.
 * Usage: node seed-bots.js
 */

const { execSync } = require("child_process");
const config = require("./_seed-config");

const PROJECT_ID = config.PROJECT_ID;
const API_KEY = config.API_KEY;

// ── Restaurant definitions ──────────────────────────────────────────

// Schedule templates
const weekdays = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];
function makeSchedule(open, close, closedDays = []) {
  return weekdays.filter(d => !closedDays.includes(d)).map(day => ({ day, open, close }));
}

const restaurants = [
  {
    name: "La Taberna de Miguel",
    shortDescription: "Cocina casera tradicional con productos de mercado",
    phone: "+34612345001",
    cuisineTypes: ["Casera", "Tradicional"],
    address: { formatted: "Calle Gran Vía 15, Madrid", lat: 40.4200, lng: -3.7025, placeId: null },
    schedule: makeSchedule("12:00", "23:00", ["sunday"]),
    menus: [
      { title: "Menú del día casero", description: "Sopa de fideos, pollo asado con patatas, flan casero", price: 12.50, offerType: "Menú del día", tags: ["Casera"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Cocido madrileño", description: "Cocido completo: sopa, garbanzos, verduras y carne", price: 14.00, offerType: "Plato del día", tags: ["Tradicional"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true } },
    ],
  },
  {
    name: "Sushi Sakura",
    shortDescription: "Sushi fresco y ramen artesanal",
    phone: "+34612345002",
    cuisineTypes: ["Japonesa", "Asiática"],
    address: { formatted: "Calle Fuencarral 42, Madrid", lat: 40.4255, lng: -3.7017, placeId: null },
    schedule: makeSchedule("13:00", "23:30"),
    menus: [
      { title: "Combo Sushi Lovers", description: "12 piezas variadas de nigiri y maki, sopa miso, edamame", price: 16.90, offerType: "Menú del día", tags: ["Japonesa"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasFish: true } },
      { title: "Ramen Tonkotsu", description: "Caldo de cerdo 12h, chashu, huevo marinado, noodles frescos", price: 11.50, offerType: "Plato del día", tags: ["Japonesa"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
    ],
  },
  {
    name: "Pizzeria Napoli",
    shortDescription: "Pizza napolitana al horno de leña",
    phone: "+34612345003",
    cuisineTypes: ["Italiana", "Pizza"],
    address: { formatted: "Calle Hortaleza 28, Madrid", lat: 40.4232, lng: -3.6985, placeId: null },
    schedule: makeSchedule("12:30", "00:00", ["monday"]),
    menus: [
      { title: "Pizza Margherita + Bebida", description: "Auténtica margherita con mozzarella di bufala DOP", price: 9.90, offerType: "Menú del día", tags: ["Italiana", "Pizza"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasGluten: true, hasLactose: true } },
      { title: "Pasta fresca del día", description: "Tagliatelle al ragú bolognese con parmigiano reggiano", price: 10.50, offerType: "Plato del día", tags: ["Italiana"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true } },
    ],
  },
  {
    name: "El Rincón Vegano",
    shortDescription: "100% plant-based, orgánico y de temporada",
    phone: "+34612345004",
    cuisineTypes: ["Vegana", "Saludable"],
    address: { formatted: "Calle Malasaña 8, Madrid", lat: 40.4270, lng: -3.7050, placeId: null },
    schedule: makeSchedule("10:00", "21:00", ["sunday"]),
    menus: [
      { title: "Bowl Buddha", description: "Quinoa, aguacate, garbanzos, kale, hummus, semillas", price: 11.00, offerType: "Menú del día", tags: ["Vegana", "Saludable"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { isVegan: true, isVegetarian: true } },
      { title: "Burger vegana artesanal", description: "Hamburguesa de remolacha y judías negras, patatas boniato", price: 12.50, offerType: "Plato del día", tags: ["Vegana"], includesDrink: true, serviceTime: "both", dietaryInfo: { isVegan: true, isVegetarian: true, hasGluten: true } },
    ],
  },
  {
    name: "Marisquería El Puerto",
    shortDescription: "Mariscos frescos del Atlántico cada día",
    phone: "+34612345005",
    cuisineTypes: ["Marisquería", "Gallega"],
    address: { formatted: "Paseo de la Castellana 60, Madrid", lat: 40.4380, lng: -3.6910, placeId: null },
    schedule: makeSchedule("13:00", "16:30", ["monday"]),
    menus: [
      { title: "Menú marinero", description: "Pulpo a la gallega, merluza a la plancha, arroz con leche", price: 18.90, offerType: "Menú del día", tags: ["Marisquería", "Gallega"], includesDrink: true, includesDessert: true, includesCoffee: true, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
      { title: "Paella de marisco", description: "Paella con gambas, mejillones, calamares y almejas (2 personas)", price: 22.00, offerType: "Plato del día", tags: ["Marisquería"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Tacos El Charro",
    shortDescription: "Auténtica comida mexicana callejera",
    phone: "+34612345006",
    cuisineTypes: ["Mexicana", "Street Food"],
    address: { formatted: "Calle Pez 12, Madrid", lat: 40.4248, lng: -3.7065, placeId: null },
    schedule: makeSchedule("11:00", "23:00"),
    menus: [
      { title: "Combo 3 tacos + guacamole", description: "Pastor, carnitas y pollo tinga con guacamole fresco y chips", price: 10.90, offerType: "Menú del día", tags: ["Mexicana"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Burrito bowl", description: "Arroz, frijoles, pico de gallo, crema agria, elección de proteína", price: 9.50, offerType: "Plato del día", tags: ["Mexicana"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Le Petit Bistrot",
    shortDescription: "Bistronomía francesa en el centro de Madrid",
    phone: "+34612345007",
    cuisineTypes: ["Francesa", "Gourmet"],
    address: { formatted: "Calle Barquillo 5, Madrid", lat: 40.4215, lng: -3.6960, placeId: null },
    schedule: makeSchedule("12:00", "15:30", ["sunday", "monday"]),
    isPro: true,
    menus: [
      { title: "Menú déjeuner", description: "Crema de puerros, confit de pato con gratin dauphinois, crème brûlée", price: 19.90, offerType: "Menú del día", tags: ["Francesa", "Gourmet"], includesDrink: true, includesDessert: true, includesCoffee: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasLactose: true, hasEgg: true } },
      { title: "Croque Monsieur gourmet", description: "Con jamón de París, gruyère gratinado y ensalada", price: 8.50, offerType: "Plato del día", tags: ["Francesa"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true } },
    ],
  },
  {
    name: "Curry House",
    shortDescription: "Curries auténticos del norte de la India",
    phone: "+34612345008",
    cuisineTypes: ["India", "Asiática"],
    address: { formatted: "Calle Lavapiés 30, Madrid", lat: 40.4090, lng: -3.7005, placeId: null },
    schedule: makeSchedule("12:00", "00:00"),
    menus: [
      { title: "Thali vegetariano", description: "Dal tadka, palak paneer, arroz basmati, naan, raita", price: 11.90, offerType: "Menú del día", tags: ["India"], includesDrink: true, serviceTime: "both", dietaryInfo: { isVegetarian: true, hasLactose: true, hasGluten: true } },
      { title: "Chicken Tikka Masala", description: "Pollo marinado en tandoor con salsa masala cremosa, arroz y naan", price: 12.90, offerType: "Plato del día", tags: ["India"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasMeat: true, hasLactose: true, hasGluten: true } },
    ],
  },
  {
    name: "Brunch & Co.",
    shortDescription: "Brunch artesanal todo el día, café de especialidad",
    phone: "+34612345009",
    cuisineTypes: ["Brunch", "Cafetería"],
    address: { formatted: "Calle Ponzano 18, Madrid", lat: 40.4365, lng: -3.6975, placeId: null },
    schedule: makeSchedule("09:00", "17:00"),
    isPro: true,
    menus: [
      { title: "Brunch completo", description: "Tostada de aguacate, huevos benedict, zumo natural, café", price: 14.50, offerType: "Oferta permanente", tags: ["Brunch"], includesDrink: true, includesCoffee: true, serviceTime: "lunch", isPermanent: true, dietaryInfo: { hasEgg: true, hasGluten: true } },
      { title: "Acai bowl tropical", description: "Acai, plátano, granola casera, coco rallado, frutos rojos", price: 8.90, offerType: "Oferta permanente", tags: ["Saludable", "Brunch"], includesDrink: false, serviceTime: "lunch", isPermanent: true, dietaryInfo: { isVegan: true, isVegetarian: true, hasNuts: true } },
    ],
  },
  {
    name: "Asador Don Julio",
    shortDescription: "Carnes a la brasa y parrilla argentina",
    phone: "+34612345010",
    cuisineTypes: ["Argentina", "Parrilla"],
    address: { formatted: "Calle Serrano 45, Madrid", lat: 40.4290, lng: -3.6865, placeId: null },
    schedule: makeSchedule("13:00", "01:00", ["tuesday"]),
    menus: [
      { title: "Parrillada para uno", description: "Entraña, chorizo criollo, morcilla, ensalada criolla, chimichurri", price: 16.50, offerType: "Menú del día", tags: ["Argentina", "Parrilla"], includesDrink: true, serviceTime: "dinner", dietaryInfo: { hasMeat: true } },
      { title: "Milanesa napolitana", description: "Milanesa de ternera con jamón, queso y salsa de tomate, papas fritas", price: 13.00, offerType: "Plato del día", tags: ["Argentina"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true, hasEgg: true } },
    ],
  },
];

// Placeholder food image URLs
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

// ── Helpers ──────────────────────────────────────────────────────────

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

// ── Firebase Auth via REST API ───────────────────────────────────────

async function createAuthUser(email, password, displayName) {
  // Use the Identity Toolkit REST API (works with just API key)
  const url = `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}`;
  try {
    const data = await fetchJSON(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, displayName, returnSecureToken: true }),
    });
    return data.localId; // this is the UID
  } catch (err) {
    // If user already exists, sign in to get UID
    if (err.message.includes("EMAIL_EXISTS")) {
      const signInUrl = `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${API_KEY}`;
      const data = await fetchJSON(signInUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password, returnSecureToken: true }),
      });
      return data.localId;
    }
    throw err;
  }
}

// ── Firestore via REST API (using firebase-tools OAuth token) ────────

let _accessToken = null;
function accessToken() {
  if (_accessToken) return _accessToken;
  try {
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

async function firestoreSet(collection, docId, data) {
  const url = `${FIRESTORE_BASE}/${collection}/${docId}`;

  // Convert JS object to Firestore REST format
  const fields = {};
  for (const [key, value] of Object.entries(data)) {
    fields[key] = toFirestoreValue(value);
  }

  await fetchJSON(url + "?currentDocument.exists=true", {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${accessToken()}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields }),
  }).catch(async () => {
    // Doc doesn't exist, create it
    const createUrl = `${FIRESTORE_BASE}/${collection}?documentId=${docId}`;
    await fetchJSON(createUrl, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken()}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ fields }),
    });
  });
}

// ── Main ─────────────────────────────────────────────────────────────

async function seed() {
  console.log("Getting access token...");
  accessToken(); // warm up
  console.log("Token OK\n");

  const now = new Date();
  const isoNow = now.toISOString();
  let imgIdx = 0;

  for (let i = 0; i < restaurants.length; i++) {
    const r = restaurants[i];
    const email = `bot${i + 1}@eatout-test.com`;
    const password = config.BOT_PASSWORD;

    console.log(`[${i + 1}/10] ${r.name} (${email})...`);

    // 1. Create Firebase Auth user
    const uid = await createAuthUser(email, password, r.name);

    // 2. Firestore: users/{uid}
    await firestoreSet("users", uid, {
      id: uid,
      email,
      name: r.name,
      role: "COMERCIO",
      createdAt: isoNow,
    });

    // 3. Firestore: businesses/{uid}
    await firestoreSet("businesses", uid, {
      id: uid,
      userId: uid,
      name: r.name,
      phone: r.phone,
      shortDescription: r.shortDescription,
      cuisineTypes: r.cuisineTypes,
      address: r.address,
      addressText: r.address.formatted,
      schedule: r.schedule || [],
      acceptsReservations: false,
      planTier: r.isPro ? "pro" : "free",
      isHighlighted: r.isPro || false,
      createdAt: isoNow,
    });

    // 4. Firestore: dailyOffers (2 per restaurant)
    // ID determinista: si el seed se reejecuta el mismo día, sobreescribe en
    // vez de crear duplicados. El día se toma del isoNow (UTC) para ser estable.
    const todayYMD = isoNow.slice(0, 10);
    for (const menu of r.menus) {
      const offerId = config.deterministicOfferId(uid, menu.title, todayYMD);
      const photoUrl = foodImageUrls[imgIdx % foodImageUrls.length];
      imgIdx++;

      await firestoreSet("dailyOffers", offerId, {
        id: offerId,
        businessId: uid,
        date: isoNow,
        title: menu.title,
        description: menu.description,
        priceTotal: menu.price,
        currency: "EUR",
        photoUrls: [photoUrl],
        tags: menu.tags || [],
        createdAt: isoNow,
        updatedAt: isoNow,
        isActive: true,
        isMerchantPro: r.isPro || false,
        dietaryInfo: fullDietaryInfo(menu.dietaryInfo),
        offerType: menu.offerType || null,
        includesDrink: menu.includesDrink || false,
        includesDessert: menu.includesDessert || false,
        includesCoffee: menu.includesCoffee || false,
        serviceTime: menu.serviceTime || "both",
        isPermanent: menu.isPermanent || false,
      });
    }

    console.log(`   uid=${uid} | ${r.menus.length} offers created`);
  }

  console.log("\n=== Done! 10 restaurant bots + 20 daily offers ===");
  console.log("Login: bot1@eatout-test.com ... bot10@eatout-test.com");
  console.log("Password: (see EATOUT_BOT_PASSWORD in functions/.env)");
}

seed()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Seed failed:", err);
    process.exit(1);
  });
