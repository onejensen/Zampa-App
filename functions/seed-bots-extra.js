/**
 * Seed script: creates 50 extra test restaurant bots across Spanish cities.
 * Uses firebase-tools credentials (no service account needed).
 *
 * Setup: copy functions/.env.example to functions/.env and fill in values.
 * Usage: node seed-bots-extra.js
 */

const { execSync } = require("child_process");
const seedConfig = require("./_seed-config");

const PROJECT_ID = seedConfig.PROJECT_ID;
const API_KEY = seedConfig.API_KEY;

// ── Helpers (same as seed-bots.js) ───────────────────────────────────

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

const weekdays = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"];
function makeSchedule(open, close, closedDays = []) {
  return weekdays.filter(d => !closedDays.includes(d)).map(day => ({ day, open, close }));
}

// ── Auth & Firestore ─────────────────────────────────────────────────

async function createAuthUser(email, password, displayName) {
  const url = `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}`;
  try {
    const data = await fetchJSON(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, displayName, returnSecureToken: true }),
    });
    return data.localId;
  } catch (err) {
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

let _accessToken = null;
function accessToken() {
  if (_accessToken) return _accessToken;
  const configRaw = require("fs").readFileSync(
    require("path").join(require("os").homedir(), ".config/configstore/firebase-tools.json"),
    "utf8"
  );
  const config = JSON.parse(configRaw);
  const refreshToken = config.tokens?.refresh_token;
  if (!refreshToken) throw new Error("No refresh token. Run: npx firebase-tools login");
  const resp = execSync(
    `curl -s -X POST "https://oauth2.googleapis.com/token" ` +
    `-H "Content-Type: application/x-www-form-urlencoded" ` +
    `-d "grant_type=refresh_token&refresh_token=${refreshToken}&client_id=${seedConfig.OAUTH_CLIENT_ID}&client_secret=${seedConfig.OAUTH_CLIENT_SECRET}"`,
    { encoding: "utf8" }
  );
  _accessToken = JSON.parse(resp).access_token;
  return _accessToken;
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
    for (const [k, v] of Object.entries(val)) fields[k] = toFirestoreValue(v);
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

async function firestoreSet(collection, docId, data) {
  const url = `${FIRESTORE_BASE}/${collection}/${docId}`;
  const fields = {};
  for (const [key, value] of Object.entries(data)) fields[key] = toFirestoreValue(value);

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

// ── Food image pool ──────────────────────────────────────────────────

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

// ── 50 Restaurants across Spain ──────────────────────────────────────

const restaurants = [
  // ── BARCELONA (8) ──────────────────────────────────────────────────
  {
    name: "Can Culleretes",
    shortDescription: "Cocina catalana desde 1786",
    phone: "+34612345011",
    cuisineTypes: ["Catalana", "Tradicional"],
    address: { formatted: "Carrer d'en Quintana 5, Barcelona", lat: 41.3812, lng: 2.1734, placeId: null },
    schedule: makeSchedule("13:00", "23:00", ["monday"]),
    menus: [
      { title: "Menú catalán", description: "Escudella, botifarra con mongetes, crema catalana", price: 14.50, offerType: "Menú del día", tags: ["Catalana"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
      { title: "Fideuà de marisco", description: "Fideuà con gambas, mejillones y alioli casero", price: 13.00, offerType: "Plato del día", tags: ["Catalana", "Mariscos"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasGluten: true, hasEgg: true } },
    ],
  },
  {
    name: "La Boquería Tapas",
    shortDescription: "Tapas creativas junto al mercado",
    phone: "+34612345012",
    cuisineTypes: ["Tapas", "Mediterránea"],
    address: { formatted: "La Rambla 91, Barcelona", lat: 41.3818, lng: 2.1720, placeId: null },
    schedule: makeSchedule("10:00", "00:00"),
    isPro: true,
    menus: [
      { title: "Degustación de tapas", description: "6 tapas variadas del mercado: croquetas, bravas, boquerones, jamón, pimientos, tortilla", price: 18.90, offerType: "Menú del día", tags: ["Tapas"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasFish: true, hasGluten: true, hasEgg: true } },
      { title: "Paella mixta", description: "Paella valenciana con pollo, conejo y verduras", price: 12.50, offerType: "Plato del día", tags: ["Mediterránea"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Ramen-Ya Barcelona",
    shortDescription: "Ramen artesanal japonés en el Born",
    phone: "+34612345013",
    cuisineTypes: ["Japonesa", "Ramen"],
    address: { formatted: "Carrer de l'Argenteria 65, Barcelona", lat: 41.3840, lng: 2.1815, placeId: null },
    schedule: makeSchedule("12:30", "23:30", ["tuesday"]),
    menus: [
      { title: "Ramen Shoyu", description: "Caldo de soja, chashu de cerdo, huevo ajitsuke, menma, negi", price: 12.90, offerType: "Menú del día", tags: ["Japonesa", "Ramen"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
      { title: "Gyoza + Edamame combo", description: "6 gyozas de cerdo a la plancha + edamame con sal marina", price: 8.50, offerType: "Plato del día", tags: ["Japonesa"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasMeat: true, hasGluten: true } },
    ],
  },
  {
    name: "Flax & Kale",
    shortDescription: "Flexitariano y healthy en el Raval",
    phone: "+34612345014",
    cuisineTypes: ["Saludable", "Vegetariana"],
    address: { formatted: "Carrer dels Tallers 74B, Barcelona", lat: 41.3835, lng: 2.1685, placeId: null },
    schedule: makeSchedule("09:00", "23:00"),
    isPro: true,
    menus: [
      { title: "Green Power Bowl", description: "Kale, quinoa, aguacate, hummus, semillas de calabaza, vinagreta de limón", price: 13.50, offerType: "Menú del día", tags: ["Saludable", "Vegetariana"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { isVegetarian: true, isVegan: true } },
      { title: "Pizza de coliflor", description: "Base de coliflor, pesto, burrata, tomate cherry, rúcula", price: 14.00, offerType: "Plato del día", tags: ["Saludable"], includesDrink: false, serviceTime: "both", dietaryInfo: { isVegetarian: true, hasLactose: true } },
    ],
  },
  {
    name: "El Xampanyet",
    shortDescription: "Bar de tapas y cava desde 1929",
    phone: "+34612345015",
    cuisineTypes: ["Tapas", "Catalana"],
    address: { formatted: "Carrer de Montcada 22, Barcelona", lat: 41.3845, lng: 2.1810, placeId: null },
    schedule: makeSchedule("12:00", "15:30", ["monday"]),
    menus: [
      { title: "Tapa + cava", description: "Anchoas del Cantábrico, pan con tomate y copa de cava", price: 9.90, offerType: "Menú del día", tags: ["Tapas"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasGluten: true } },
      { title: "Tabla de embutidos", description: "Fuet, longaniza, jamón ibérico, queso manchego, pan con tomate", price: 15.00, offerType: "Plato del día", tags: ["Catalana"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true } },
    ],
  },
  {
    name: "Bacoa Burger",
    shortDescription: "Hamburguesas gourmet con carne ecológica",
    phone: "+34612345016",
    cuisineTypes: ["Americana", "Hamburguesas"],
    address: { formatted: "Carrer del Jutjat 6, Barcelona", lat: 41.3830, lng: 2.1800, placeId: null },
    schedule: makeSchedule("12:00", "23:00"),
    menus: [
      { title: "Bacoa Classic + patatas", description: "Burger de ternera ecológica, cheddar, lechuga, tomate, salsa especial, patatas artesanas", price: 12.90, offerType: "Menú del día", tags: ["Americana", "Hamburguesas"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true } },
      { title: "Veggie Burger", description: "Hamburguesa de remolacha y quinoa, aguacate, brotes, salsa yogur", price: 11.50, offerType: "Plato del día", tags: ["Hamburguesas", "Vegetariana"], includesDrink: false, serviceTime: "both", dietaryInfo: { isVegetarian: true, hasGluten: true, hasLactose: true } },
    ],
  },
  {
    name: "Cervecería Catalana",
    shortDescription: "Tapas y montaditos en el Eixample",
    phone: "+34612345017",
    cuisineTypes: ["Tapas", "Española"],
    address: { formatted: "Carrer de Mallorca 236, Barcelona", lat: 41.3935, lng: 2.1625, placeId: null },
    schedule: makeSchedule("08:00", "01:30"),
    menus: [
      { title: "Surtido de montaditos", description: "8 montaditos variados: salmón, anchoa, jamón, tortilla, sobrasada, queso, pimiento, atún", price: 14.00, offerType: "Menú del día", tags: ["Tapas"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasFish: true, hasGluten: true, hasEgg: true } },
      { title: "Patatas bravas + croquetas", description: "Bravas con alioli y salsa brava, 4 croquetas de jamón", price: 9.50, offerType: "Plato del día", tags: ["Tapas"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
    ],
  },
  {
    name: "Poke House BCN",
    shortDescription: "Poke bowls hawaianos personalizables",
    phone: "+34612345018",
    cuisineTypes: ["Hawaiana", "Saludable"],
    address: { formatted: "Passeig de Gràcia 55, Barcelona", lat: 41.3940, lng: 2.1645, placeId: null },
    schedule: makeSchedule("11:30", "22:30"),
    menus: [
      { title: "Poke Salmón Premium", description: "Arroz de sushi, salmón fresco, aguacate, edamame, mango, salsa ponzu", price: 13.90, offerType: "Menú del día", tags: ["Hawaiana", "Saludable"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasFish: true } },
      { title: "Poke Vegano", description: "Base de quinoa, tofu marinado, mango, pepino, wakame, sésamo", price: 11.90, offerType: "Plato del día", tags: ["Hawaiana", "Vegana"], includesDrink: false, serviceTime: "both", dietaryInfo: { isVegan: true, isVegetarian: true } },
    ],
  },

  // ── VALENCIA (7) ───────────────────────────────────────────────────
  {
    name: "Casa Roberto",
    shortDescription: "Paellas y arroces valencianos desde 1950",
    phone: "+34612345019",
    cuisineTypes: ["Valenciana", "Arroces"],
    address: { formatted: "Carrer del Mestre Gozalbo 19, Valencia", lat: 39.4715, lng: -0.3780, placeId: null },
    schedule: makeSchedule("13:00", "16:00", ["monday"]),
    menus: [
      { title: "Paella valenciana", description: "Paella tradicional con pollo, conejo, garrofón, judía verde, azafrán", price: 14.00, offerType: "Menú del día", tags: ["Valenciana", "Arroces"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
      { title: "Arroz a banda", description: "Arroz con caldo de pescado, alioli y gambas", price: 13.50, offerType: "Plato del día", tags: ["Valenciana", "Arroces"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasEgg: true } },
    ],
  },
  {
    name: "La Pepica",
    shortDescription: "Mariscos frente a la playa de la Malvarrosa",
    phone: "+34612345020",
    cuisineTypes: ["Marisquería", "Mediterránea"],
    address: { formatted: "Paseo de Neptuno 6, Valencia", lat: 39.4665, lng: -0.3270, placeId: null },
    schedule: makeSchedule("13:00", "23:30", ["tuesday"]),
    isPro: true,
    menus: [
      { title: "Menú marinero", description: "Clóchinas al vapor, arroz negro con sepia, postre del día", price: 22.00, offerType: "Menú del día", tags: ["Marisquería"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
      { title: "All i pebre de anguila", description: "Plato típico valenciano de anguila con patatas en salsa de ajo y pimentón", price: 16.00, offerType: "Plato del día", tags: ["Valenciana"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Horchatería Santa Catalina",
    shortDescription: "Horchata artesanal y fartons desde 1836",
    phone: "+34612345021",
    cuisineTypes: ["Cafetería", "Valenciana"],
    address: { formatted: "Plaça de Santa Caterina 6, Valencia", lat: 39.4755, lng: -0.3790, placeId: null },
    schedule: makeSchedule("08:00", "21:00"),
    menus: [
      { title: "Almuerzo valenciano", description: "Bocadillo de longaniza, tomate y aceite + horchata con fartons", price: 7.50, offerType: "Oferta permanente", tags: ["Valenciana"], includesDrink: true, serviceTime: "lunch", isPermanent: true, dietaryInfo: { hasMeat: true, hasGluten: true, hasNuts: true } },
      { title: "Merienda dulce", description: "Horchata grande + 3 fartons + buñuelo de calabaza", price: 6.00, offerType: "Oferta permanente", tags: ["Cafetería"], includesDrink: true, serviceTime: "lunch", isPermanent: true, dietaryInfo: { hasGluten: true, hasNuts: true, hasEgg: true } },
    ],
  },
  {
    name: "Karak Shawarma",
    shortDescription: "Shawarma y falafel auténtico libanés",
    phone: "+34612345022",
    cuisineTypes: ["Libanesa", "Street Food"],
    address: { formatted: "Carrer de Russafa 20, Valencia", lat: 39.4635, lng: -0.3740, placeId: null },
    schedule: makeSchedule("11:00", "00:00"),
    menus: [
      { title: "Combo Shawarma", description: "Shawarma de pollo, hummus, tabulé, pan pita casero", price: 9.90, offerType: "Menú del día", tags: ["Libanesa"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Plato falafel", description: "6 falafel caseros, hummus, ensalada, salsa tahini, pan pita", price: 8.90, offerType: "Plato del día", tags: ["Libanesa", "Vegana"], includesDrink: false, serviceTime: "both", dietaryInfo: { isVegan: true, isVegetarian: true, hasGluten: true } },
    ],
  },
  {
    name: "Taquería Los Cuñados",
    shortDescription: "Tacos y burritos mexicanos en Ruzafa",
    phone: "+34612345023",
    cuisineTypes: ["Mexicana", "Street Food"],
    address: { formatted: "Carrer de Cadis 42, Valencia", lat: 39.4620, lng: -0.3715, placeId: null },
    schedule: makeSchedule("12:00", "00:30", ["monday"]),
    menus: [
      { title: "Combo Taco Tuesday", description: "3 tacos al pastor + nachos con guacamole + agua de jamaica", price: 11.50, offerType: "Menú del día", tags: ["Mexicana"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Burrito XXL", description: "Burrito gigante de carnitas con arroz, frijoles, pico de gallo, crema", price: 10.50, offerType: "Plato del día", tags: ["Mexicana"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true } },
    ],
  },
  {
    name: "Vegan Nàstic",
    shortDescription: "Comida vegana creativa en el centro",
    phone: "+34612345024",
    cuisineTypes: ["Vegana", "Creativa"],
    address: { formatted: "Carrer d'En Llop 4, Valencia", lat: 39.4730, lng: -0.3770, placeId: null },
    schedule: makeSchedule("12:00", "16:00", ["sunday"]),
    menus: [
      { title: "Menú vegano completo", description: "Crema de verduras de temporada, curry de garbanzos con arroz, brownie vegano", price: 12.90, offerType: "Menú del día", tags: ["Vegana"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { isVegan: true, isVegetarian: true, hasGluten: true } },
      { title: "Pad Thai vegano", description: "Noodles de arroz, tofu, verduras, cacahuetes, lima", price: 10.50, offerType: "Plato del día", tags: ["Vegana", "Asiática"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { isVegan: true, isVegetarian: true, hasNuts: true } },
    ],
  },
  {
    name: "Trattoria La Nonna",
    shortDescription: "Pasta fresca artesanal italiana",
    phone: "+34612345025",
    cuisineTypes: ["Italiana", "Pasta"],
    address: { formatted: "Carrer del Mar 27, Valencia", lat: 39.4700, lng: -0.3720, placeId: null },
    schedule: makeSchedule("13:00", "23:30", ["monday"]),
    menus: [
      { title: "Menú pasta del giorno", description: "Antipasto de bruschetta, pasta fresca del día (carbonara/pesto/amatriciana), tiramisú", price: 13.90, offerType: "Menú del día", tags: ["Italiana"], includesDrink: true, includesDessert: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true, hasEgg: true } },
      { title: "Pizza quattro formaggi", description: "Mozzarella, gorgonzola, parmesano, fontina, base fina", price: 10.90, offerType: "Plato del día", tags: ["Italiana", "Pizza"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasGluten: true, hasLactose: true } },
    ],
  },

  // ── SEVILLA (7) ────────────────────────────────────────────────────
  {
    name: "Bodega Santa Cruz",
    shortDescription: "Tapas sevillanas en el barrio de Santa Cruz",
    phone: "+34612345026",
    cuisineTypes: ["Andaluza", "Tapas"],
    address: { formatted: "Calle Rodrigo Caro 1, Sevilla", lat: 37.3850, lng: -5.9910, placeId: null },
    schedule: makeSchedule("12:00", "00:00"),
    menus: [
      { title: "Surtido de tapas andaluzas", description: "Salmorejo, flamenquín, pescaíto frito, ensaladilla rusa", price: 13.50, offerType: "Menú del día", tags: ["Andaluza", "Tapas"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasFish: true, hasGluten: true, hasEgg: true } },
      { title: "Cola de toro", description: "Rabo de toro estofado con puré de patatas", price: 14.00, offerType: "Plato del día", tags: ["Andaluza"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "El Rinconcillo",
    shortDescription: "El bar más antiguo de Sevilla, desde 1670",
    phone: "+34612345027",
    cuisineTypes: ["Andaluza", "Tradicional"],
    address: { formatted: "Calle Gerona 40, Sevilla", lat: 37.3950, lng: -5.9930, placeId: null },
    schedule: makeSchedule("13:00", "01:00", ["wednesday"]),
    isPro: true,
    menus: [
      { title: "Menú histórico", description: "Espinacas con garbanzos, carrillada ibérica, tocino de cielo", price: 16.50, offerType: "Menú del día", tags: ["Andaluza", "Tradicional"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasEgg: true } },
      { title: "Pavía de bacalao", description: "Bacalao rebozado con pimientos del piquillo", price: 10.00, offerType: "Plato del día", tags: ["Andaluza"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasFish: true, hasGluten: true, hasEgg: true } },
    ],
  },
  {
    name: "Cañabota",
    shortDescription: "Mariscos y pescados del golfo de Cádiz",
    phone: "+34612345028",
    cuisineTypes: ["Marisquería", "Andaluza"],
    address: { formatted: "Plaza de la Alfalfa 4, Sevilla", lat: 37.3890, lng: -5.9880, placeId: null },
    schedule: makeSchedule("13:30", "16:30", ["monday"]),
    menus: [
      { title: "Menú del pescador", description: "Gambas blancas de Huelva, atún rojo de almadraba, sorbet de limón", price: 24.00, offerType: "Menú del día", tags: ["Marisquería"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
      { title: "Fritura gaditana", description: "Surtido de pescaíto frito: boquerones, calamares, chopitos", price: 14.00, offerType: "Plato del día", tags: ["Andaluza", "Marisquería"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasGluten: true } },
    ],
  },
  {
    name: "Wok Garden Sevilla",
    shortDescription: "Wok asiático con ingredientes frescos",
    phone: "+34612345029",
    cuisineTypes: ["Asiática", "Thai"],
    address: { formatted: "Avenida de la Constitución 16, Sevilla", lat: 37.3860, lng: -5.9955, placeId: null },
    schedule: makeSchedule("12:00", "23:30"),
    menus: [
      { title: "Pad Thai de gambas", description: "Noodles de arroz salteados con gambas, huevo, cacahuete, brotes de soja, lima", price: 11.90, offerType: "Menú del día", tags: ["Thai", "Asiática"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasFish: true, hasNuts: true, hasEgg: true } },
      { title: "Curry verde thai", description: "Pollo en curry verde con leche de coco, bambú, berenjena thai, arroz jazmín", price: 10.90, offerType: "Plato del día", tags: ["Thai"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Pizzería San Marco",
    shortDescription: "Pizza romana al taglio en Triana",
    phone: "+34612345030",
    cuisineTypes: ["Italiana", "Pizza"],
    address: { formatted: "Calle San Jacinto 33, Sevilla", lat: 37.3830, lng: -6.0020, placeId: null },
    schedule: makeSchedule("12:00", "00:00"),
    menus: [
      { title: "Pizza + bebida", description: "Pizza al taglio (2 cortes) a elegir + bebida", price: 8.50, offerType: "Menú del día", tags: ["Italiana", "Pizza"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasGluten: true, hasLactose: true } },
      { title: "Calzone relleno", description: "Calzone de jamón, mozzarella, champiñones y ricotta", price: 9.90, offerType: "Plato del día", tags: ["Italiana"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true } },
    ],
  },
  {
    name: "Green Vita",
    shortDescription: "Bowls saludables y smoothies en Nervión",
    phone: "+34612345031",
    cuisineTypes: ["Saludable", "Vegana"],
    address: { formatted: "Calle Luis de Morales 2, Sevilla", lat: 37.3810, lng: -5.9750, placeId: null },
    schedule: makeSchedule("09:00", "20:00", ["sunday"]),
    menus: [
      { title: "Açaí bowl + smoothie", description: "Bowl de açaí con granola, plátano, coco + smoothie verde", price: 10.90, offerType: "Oferta permanente", tags: ["Saludable", "Vegana"], includesDrink: true, serviceTime: "lunch", isPermanent: true, dietaryInfo: { isVegan: true, isVegetarian: true, hasNuts: true } },
      { title: "Wrap de falafel", description: "Wrap integral con falafel, hummus, verduras frescas, tahini", price: 8.50, offerType: "Plato del día", tags: ["Vegana", "Saludable"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { isVegan: true, isVegetarian: true, hasGluten: true } },
    ],
  },
  {
    name: "Casa Morales",
    shortDescription: "Vermutería y tapas clásicas desde 1850",
    phone: "+34612345032",
    cuisineTypes: ["Tapas", "Tradicional"],
    address: { formatted: "Calle García de Vinuesa 11, Sevilla", lat: 37.3870, lng: -5.9960, placeId: null },
    schedule: makeSchedule("12:00", "16:30", ["sunday"]),
    menus: [
      { title: "Vermú + 3 tapas", description: "Vermú de grifo + croquetas, ensaladilla y montadito de pringá", price: 10.00, offerType: "Menú del día", tags: ["Tapas", "Tradicional"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
      { title: "Chicharrones de Cádiz", description: "Chicharrones crujientes con salsa de miel y mostaza", price: 7.50, offerType: "Plato del día", tags: ["Andaluza"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true } },
    ],
  },

  // ── BILBAO (6) ─────────────────────────────────────────────────────
  {
    name: "Gure Toki",
    shortDescription: "Pintxos gourmet en el Casco Viejo",
    phone: "+34612345033",
    cuisineTypes: ["Vasca", "Pintxos"],
    address: { formatted: "Plaza Nueva 12, Bilbao", lat: 43.2590, lng: -2.9235, placeId: null },
    schedule: makeSchedule("11:00", "23:00", ["monday"]),
    isPro: true,
    menus: [
      { title: "Ruta de pintxos", description: "5 pintxos creativos del día + txakoli", price: 16.00, offerType: "Menú del día", tags: ["Vasca", "Pintxos"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasFish: true, hasGluten: true } },
      { title: "Txuleta a la brasa", description: "Chuletón de vaca vieja (500g) con pimientos de Gernika", price: 25.00, offerType: "Plato del día", tags: ["Vasca"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "La Viña del Ensanche",
    shortDescription: "Cocina vasca de mercado y vinos",
    phone: "+34612345034",
    cuisineTypes: ["Vasca", "Tradicional"],
    address: { formatted: "Calle de la Diputación 10, Bilbao", lat: 43.2630, lng: -2.9340, placeId: null },
    schedule: makeSchedule("12:30", "16:00", ["sunday"]),
    menus: [
      { title: "Menú vasco", description: "Pimientos rellenos de bacalao, merluza en salsa verde, arroz con leche", price: 18.00, offerType: "Menú del día", tags: ["Vasca"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasGluten: true, hasLactose: true } },
      { title: "Kokotxas de merluza", description: "Kokotxas al pil-pil con aceite de oliva y ajo", price: 19.50, offerType: "Plato del día", tags: ["Vasca"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Kafe Antzokia",
    shortDescription: "Cocina de mercado y cultura en Bilbao La Vieja",
    phone: "+34612345035",
    cuisineTypes: ["Creativa", "Fusión"],
    address: { formatted: "Calle San Vicente 2, Bilbao", lat: 43.2575, lng: -2.9270, placeId: null },
    schedule: makeSchedule("10:00", "01:00"),
    menus: [
      { title: "Menú del día creativo", description: "Entrante + principal + postre (carta cambiante según mercado)", price: 14.50, offerType: "Menú del día", tags: ["Creativa"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Burger de bonito", description: "Hamburguesa de bonito del norte con pimientos asados y alioli", price: 12.00, offerType: "Plato del día", tags: ["Fusión", "Vasca"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasFish: true, hasGluten: true, hasEgg: true } },
    ],
  },
  {
    name: "Sushi Artist Bilbao",
    shortDescription: "Sushi fusión en Abando",
    phone: "+34612345036",
    cuisineTypes: ["Japonesa", "Fusión"],
    address: { formatted: "Gran Vía de Don Diego López de Haro 40, Bilbao", lat: 43.2640, lng: -2.9370, placeId: null },
    schedule: makeSchedule("12:30", "23:00"),
    menus: [
      { title: "Sushi fusión box", description: "10 piezas de maki y nigiri fusión + sopa miso + gyozas", price: 15.90, offerType: "Menú del día", tags: ["Japonesa", "Fusión"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasFish: true, hasGluten: true } },
      { title: "Chirashi bowl", description: "Arroz de sushi con salmón, atún, langostino, aguacate, sésamo", price: 14.50, offerType: "Plato del día", tags: ["Japonesa"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Yandiola",
    shortDescription: "Alta cocina vasca contemporánea",
    phone: "+34612345037",
    cuisineTypes: ["Vasca", "Gourmet"],
    address: { formatted: "Plaza Arriquíbar 4, Bilbao", lat: 43.2610, lng: -2.9360, placeId: null },
    schedule: makeSchedule("13:00", "15:30", ["sunday", "monday"]),
    isPro: true,
    menus: [
      { title: "Menú degustación", description: "4 platos de temporada + postre + maridaje", price: 35.00, offerType: "Menú del día", tags: ["Vasca", "Gourmet"], includesDrink: true, includesDessert: true, includesCoffee: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasFish: true, hasLactose: true } },
      { title: "Bacalao al pil-pil", description: "Bacalao confitado en aceite de oliva con emulsión de pil-pil y guindilla", price: 22.00, offerType: "Plato del día", tags: ["Vasca"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Street Food Bilbao",
    shortDescription: "Comida callejera internacional en Indautxu",
    phone: "+34612345038",
    cuisineTypes: ["Street Food", "Internacional"],
    address: { formatted: "Calle de Ercilla 18, Bilbao", lat: 43.2620, lng: -2.9390, placeId: null },
    schedule: makeSchedule("11:30", "23:30"),
    menus: [
      { title: "Korean BBQ bowl", description: "Arroz, ternera bulgogi, kimchi, huevo frito, sésamo, gochujang", price: 11.90, offerType: "Menú del día", tags: ["Coreana", "Street Food"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasEgg: true } },
      { title: "Bao buns (3 uds)", description: "3 bao al vapor: cerdo char siu, pollo karaage, tofu teriyaki", price: 10.50, offerType: "Plato del día", tags: ["Asiática", "Street Food"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true } },
    ],
  },

  // ── MÁLAGA (5) ─────────────────────────────────────────────────────
  {
    name: "El Pimpi",
    shortDescription: "Bodega malagueña con vistas a la Alcazaba",
    phone: "+34612345039",
    cuisineTypes: ["Andaluza", "Tapas"],
    address: { formatted: "Calle Granada 62, Málaga", lat: 36.7220, lng: -4.4180, placeId: null },
    schedule: makeSchedule("10:00", "02:00"),
    isPro: true,
    menus: [
      { title: "Menú malagueño", description: "Ensalada malagueña, espetos de sardinas, bienmesabe", price: 15.00, offerType: "Menú del día", tags: ["Andaluza"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasEgg: true, hasNuts: true } },
      { title: "Plato de los montes", description: "Huevos fritos, lomo, chorizo, pimientos, patatas, morcilla", price: 12.00, offerType: "Plato del día", tags: ["Andaluza", "Tradicional"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasEgg: true } },
    ],
  },
  {
    name: "Óleo Restaurante",
    shortDescription: "Cocina de autor en el CAC Málaga",
    phone: "+34612345040",
    cuisineTypes: ["Creativa", "Mediterránea"],
    address: { formatted: "Calle Alemania, Málaga", lat: 36.7170, lng: -4.4250, placeId: null },
    schedule: makeSchedule("13:00", "16:00", ["monday"]),
    menus: [
      { title: "Menú ejecutivo", description: "Entrante + principal + postre de temporada (carta semanal)", price: 16.90, offerType: "Menú del día", tags: ["Creativa", "Mediterránea"], includesDrink: true, includesDessert: true, includesCoffee: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Tataki de atún rojo", description: "Atún rojo de almadraba con sésamo, wakame y soja", price: 18.00, offerType: "Plato del día", tags: ["Creativa"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Chiringuito Tropicana",
    shortDescription: "Espetos y pescaíto en la playa de Pedregalejo",
    phone: "+34612345041",
    cuisineTypes: ["Chiringuito", "Marisquería"],
    address: { formatted: "Paseo Marítimo El Pedregal 20, Málaga", lat: 36.7195, lng: -4.3920, placeId: null },
    schedule: makeSchedule("11:00", "23:00"),
    menus: [
      { title: "Espetos + cerveza", description: "6 espetos de sardinas a la brasa + cerveza bien fría", price: 8.00, offerType: "Oferta permanente", tags: ["Chiringuito"], includesDrink: true, serviceTime: "both", isPermanent: true, dietaryInfo: { hasFish: true } },
      { title: "Fritura malagueña", description: "Boquerones, calamares, chopitos y langostinos fritos", price: 14.00, offerType: "Plato del día", tags: ["Marisquería", "Andaluza"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasGluten: true } },
    ],
  },
  {
    name: "Vino Mío",
    shortDescription: "Cocina creativa con espectáculo flamenco",
    phone: "+34612345042",
    cuisineTypes: ["Creativa", "Española"],
    address: { formatted: "Plaza Jerónimo Cuervo 2, Málaga", lat: 36.7210, lng: -4.4220, placeId: null },
    schedule: makeSchedule("19:00", "01:00", ["monday", "tuesday"]),
    menus: [
      { title: "Cena con flamenco", description: "Menú de 3 platos + copa de vino + show flamenco en vivo", price: 35.00, offerType: "Menú del día", tags: ["Creativa", "Española"], includesDrink: true, includesDessert: true, serviceTime: "dinner", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Risotto de setas", description: "Risotto cremoso con boletus, parmesano y trufa negra", price: 14.50, offerType: "Plato del día", tags: ["Italiana", "Creativa"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { isVegetarian: true, hasLactose: true } },
    ],
  },
  {
    name: "Mimo Sushi Málaga",
    shortDescription: "Sushi de calidad en Soho",
    phone: "+34612345043",
    cuisineTypes: ["Japonesa", "Sushi"],
    address: { formatted: "Calle Casas de Campos 27, Málaga", lat: 36.7175, lng: -4.4275, placeId: null },
    schedule: makeSchedule("13:00", "23:00", ["tuesday"]),
    menus: [
      { title: "Menú sakura", description: "Ensalada wakame, 8 piezas maki/nigiri, postre mochi", price: 16.90, offerType: "Menú del día", tags: ["Japonesa", "Sushi"], includesDrink: false, includesDessert: true, serviceTime: "both", dietaryInfo: { hasFish: true } },
      { title: "Udon de gambas", description: "Noodles udon con gambas, shiitake, pak choi, salsa teriyaki", price: 12.50, offerType: "Plato del día", tags: ["Japonesa"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasFish: true, hasGluten: true } },
    ],
  },

  // ── SAN SEBASTIÁN (4) ──────────────────────────────────────────────
  {
    name: "La Cuchara de San Telmo",
    shortDescription: "Pintxos de autor en el Parte Vieja",
    phone: "+34612345044",
    cuisineTypes: ["Vasca", "Pintxos"],
    address: { formatted: "Calle 31 de Agosto 28, San Sebastián", lat: 43.3250, lng: -1.9830, placeId: null },
    schedule: makeSchedule("12:30", "15:30", ["monday"]),
    isPro: true,
    menus: [
      { title: "Degustación pintxos", description: "5 pintxos de autor calientes + copa de Txakoli", price: 19.00, offerType: "Menú del día", tags: ["Vasca", "Pintxos"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasFish: true, hasEgg: true } },
      { title: "Foie a la plancha", description: "Foie fresco a la plancha con compota de manzana y reducción de Pedro Ximénez", price: 14.00, offerType: "Plato del día", tags: ["Gourmet"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Bodega Donostiarra",
    shortDescription: "Pintxos clásicos y sidra natural",
    phone: "+34612345045",
    cuisineTypes: ["Vasca", "Sidrería"],
    address: { formatted: "Calle Peña y Goñi 13, San Sebastián", lat: 43.3200, lng: -1.9780, placeId: null },
    schedule: makeSchedule("11:00", "23:00"),
    menus: [
      { title: "Menú sidrería", description: "Tortilla de bacalao, chuletón a la brasa, queso idiazábal con nueces, sidra natural", price: 30.00, offerType: "Menú del día", tags: ["Vasca", "Sidrería"], includesDrink: true, includesDessert: true, serviceTime: "dinner", dietaryInfo: { hasMeat: true, hasFish: true, hasEgg: true, hasNuts: true, hasLactose: true } },
      { title: "Gilda + txakoli", description: "3 gildas (oliva, anchoa, guindilla) + vaso de txakoli", price: 7.50, offerType: "Plato del día", tags: ["Pintxos"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Narru Gastro",
    shortDescription: "Cocina vasca moderna en Gros",
    phone: "+34612345046",
    cuisineTypes: ["Vasca", "Gourmet"],
    address: { formatted: "Calle de Zabaleta 52, San Sebastián", lat: 43.3230, lng: -1.9700, placeId: null },
    schedule: makeSchedule("13:00", "15:30", ["sunday", "monday"]),
    menus: [
      { title: "Menú degustación Narru", description: "6 pases de cocina vasca contemporánea + maridaje de vinos", price: 45.00, offerType: "Menú del día", tags: ["Vasca", "Gourmet"], includesDrink: true, includesDessert: true, includesCoffee: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasFish: true, hasLactose: true } },
      { title: "Merluza a la koskera", description: "Merluza de anzuelo con almejas, espárragos, huevo y perejil", price: 20.00, offerType: "Plato del día", tags: ["Vasca"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasEgg: true } },
    ],
  },
  {
    name: "La Mejillonera",
    shortDescription: "Mejillones y tigres legendarios",
    phone: "+34612345047",
    cuisineTypes: ["Marisquería", "Vasca"],
    address: { formatted: "Calle del Puerto 15, San Sebastián", lat: 43.3240, lng: -1.9815, placeId: null },
    schedule: makeSchedule("11:00", "22:30", ["wednesday"]),
    menus: [
      { title: "Combo mejillonero", description: "Ración de mejillones al vapor + 3 tigres + caña", price: 9.50, offerType: "Oferta permanente", tags: ["Marisquería"], includesDrink: true, serviceTime: "both", isPermanent: true, dietaryInfo: { hasFish: true, hasGluten: true } },
      { title: "Pulpo a la brasa", description: "Pulpo braseado con patata rota y pimentón de la Vera", price: 14.00, offerType: "Plato del día", tags: ["Marisquería", "Vasca"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasFish: true } },
    ],
  },

  // ── GRANADA (4) ────────────────────────────────────────────────────
  {
    name: "Bodegas Castañeda",
    shortDescription: "Tapas gratis con cada bebida en el centro",
    phone: "+34612345048",
    cuisineTypes: ["Andaluza", "Tapas"],
    address: { formatted: "Calle Almireceros 1, Granada", lat: 37.1760, lng: -3.5995, placeId: null },
    schedule: makeSchedule("12:00", "01:00"),
    menus: [
      { title: "Vermú + 3 tapas", description: "Vermú rojo + 3 tapas granadinas: habas con jamón, croquetas, ensaladilla", price: 8.50, offerType: "Menú del día", tags: ["Andaluza", "Tapas"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
      { title: "Rabo de toro al PX", description: "Rabo de toro estofado en Pedro Ximénez con puré de boniato", price: 13.00, offerType: "Plato del día", tags: ["Andaluza"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Arrayanes",
    shortDescription: "Cocina marroquí y tetería en el Albaicín",
    phone: "+34612345049",
    cuisineTypes: ["Marroquí", "Árabe"],
    address: { formatted: "Cuesta Marañas 4, Granada", lat: 37.1790, lng: -3.5930, placeId: null },
    schedule: makeSchedule("13:00", "23:00", ["monday"]),
    menus: [
      { title: "Menú marroquí", description: "Sopa harira, cuscús de cordero con verduras, pastelitos de almendra, té moruno", price: 14.00, offerType: "Menú del día", tags: ["Marroquí"], includesDrink: true, includesDessert: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true, hasNuts: true } },
      { title: "Tajín de pollo", description: "Tajín de pollo con aceitunas, limón confitado y cuscús", price: 12.00, offerType: "Plato del día", tags: ["Marroquí", "Árabe"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Om Kalsum",
    shortDescription: "Cocina libanesa en la calle Elvira",
    phone: "+34612345050",
    cuisineTypes: ["Libanesa", "Vegetariana"],
    address: { formatted: "Calle Jardines 3, Granada", lat: 37.1775, lng: -3.5980, placeId: null },
    schedule: makeSchedule("12:00", "00:00"),
    menus: [
      { title: "Plato libanés mixto", description: "Hummus, baba ganoush, falafel, tabulé, kibbe, pan pita", price: 12.50, offerType: "Menú del día", tags: ["Libanesa"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Plato vegano libanés", description: "Mujaddara, hummus, falafel, fattoush, pan pita", price: 10.50, offerType: "Plato del día", tags: ["Libanesa", "Vegana"], includesDrink: false, serviceTime: "both", dietaryInfo: { isVegan: true, isVegetarian: true, hasGluten: true } },
    ],
  },
  {
    name: "La Tana",
    shortDescription: "Vinos naturales y tapas de mercado",
    phone: "+34612345051",
    cuisineTypes: ["Española", "Vinos"],
    address: { formatted: "Placeta del Agua 3, Granada", lat: 37.1740, lng: -3.5960, placeId: null },
    schedule: makeSchedule("13:00", "16:00", ["sunday", "monday"]),
    menus: [
      { title: "Menú maridaje", description: "3 tapas del día maridadas con 3 vinos naturales seleccionados", price: 20.00, offerType: "Menú del día", tags: ["Española", "Vinos"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasLactose: true } },
      { title: "Tabla de quesos", description: "5 quesos artesanales españoles con membrillo y frutos secos", price: 14.00, offerType: "Plato del día", tags: ["Española"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasLactose: true, hasNuts: true } },
    ],
  },

  // ── ZARAGOZA (4) ───────────────────────────────────────────────────
  {
    name: "Los Victorinos",
    shortDescription: "Tapas aragonesas junto a El Pilar",
    phone: "+34612345052",
    cuisineTypes: ["Aragonesa", "Tapas"],
    address: { formatted: "Calle José de la Hera 6, Zaragoza", lat: 41.6570, lng: -0.8780, placeId: null },
    schedule: makeSchedule("11:00", "00:00", ["monday"]),
    menus: [
      { title: "Menú baturro", description: "Huevos rotos con jamón, ternasco a la pastora, fruta de temporada", price: 13.50, offerType: "Menú del día", tags: ["Aragonesa"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasEgg: true } },
      { title: "Migas aragonesas", description: "Migas de pastor con uvas, longaniza y huevo frito", price: 10.00, offerType: "Plato del día", tags: ["Aragonesa", "Tradicional"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
    ],
  },
  {
    name: "Birosta",
    shortDescription: "Cocina de autor aragonesa en el Tubo",
    phone: "+34612345053",
    cuisineTypes: ["Creativa", "Aragonesa"],
    address: { formatted: "Calle Méndez Núñez 3, Zaragoza", lat: 41.6530, lng: -0.8790, placeId: null },
    schedule: makeSchedule("13:00", "16:00", ["sunday", "monday"]),
    isPro: true,
    menus: [
      { title: "Menú degustación Birosta", description: "5 pases creativos con ingredientes de la huerta aragonesa + postre", price: 28.00, offerType: "Menú del día", tags: ["Creativa", "Aragonesa"], includesDrink: true, includesDessert: true, includesCoffee: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasFish: true, hasLactose: true } },
      { title: "Ternasco confitado", description: "Paletilla de ternasco confitada 12h con verduras de la huerta", price: 18.00, offerType: "Plato del día", tags: ["Aragonesa"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Doña Casta",
    shortDescription: "Vermutería y cocina castiza",
    phone: "+34612345054",
    cuisineTypes: ["Española", "Vermutería"],
    address: { formatted: "Calle Libertad 12, Zaragoza", lat: 41.6555, lng: -0.8760, placeId: null },
    schedule: makeSchedule("11:00", "23:00", ["tuesday"]),
    menus: [
      { title: "Vermú con tapas", description: "Vermú casero + 3 tapas del día: croquetas, bravas, tortilla", price: 9.50, offerType: "Menú del día", tags: ["Española", "Vermutería"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true } },
      { title: "Bocadillo de calamares", description: "Bocadillo de calamares a la romana con alioli", price: 6.50, offerType: "Plato del día", tags: ["Española"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasFish: true, hasGluten: true, hasEgg: true } },
    ],
  },
  {
    name: "Thai Garden Zaragoza",
    shortDescription: "Cocina tailandesa auténtica",
    phone: "+34612345055",
    cuisineTypes: ["Thai", "Asiática"],
    address: { formatted: "Paseo de Sagasta 40, Zaragoza", lat: 41.6460, lng: -0.8870, placeId: null },
    schedule: makeSchedule("12:30", "23:30", ["monday"]),
    menus: [
      { title: "Menú thai", description: "Rollitos de primavera, pollo al curry rojo con arroz jazmín, helado de coco", price: 13.90, offerType: "Menú del día", tags: ["Thai"], includesDrink: true, includesDessert: true, serviceTime: "both", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Tom Yum Kung", description: "Sopa picante y ácida con langostinos, setas, lemongrass, galanga", price: 9.90, offerType: "Plato del día", tags: ["Thai", "Asiática"], includesDrink: false, serviceTime: "dinner", dietaryInfo: { hasFish: true } },
    ],
  },

  // ── PALMA DE MALLORCA (3) ──────────────────────────────────────────
  {
    name: "Ca'n Joan de s'Aigo",
    shortDescription: "Cafetería histórica desde 1700",
    phone: "+34612345056",
    cuisineTypes: ["Mallorquina", "Cafetería"],
    address: { formatted: "Carrer de Can Sanç 10, Palma", lat: 39.5700, lng: 2.6510, placeId: null },
    schedule: makeSchedule("08:00", "21:00", ["monday"]),
    menus: [
      { title: "Almuerzo mallorquín", description: "Pa amb oli, sobrasada, coca de patata, chocolate caliente con ensaimada", price: 9.50, offerType: "Oferta permanente", tags: ["Mallorquina", "Cafetería"], includesDrink: true, serviceTime: "lunch", isPermanent: true, dietaryInfo: { hasMeat: true, hasGluten: true, hasLactose: true } },
      { title: "Ensaimada + café", description: "Ensaimada de crema artesanal + café con leche", price: 5.50, offerType: "Oferta permanente", tags: ["Cafetería"], includesDrink: true, serviceTime: "lunch", isPermanent: true, dietaryInfo: { hasGluten: true, hasLactose: true, hasEgg: true } },
    ],
  },
  {
    name: "Marc Fosh",
    shortDescription: "Alta cocina mediterránea con estrella Michelin",
    phone: "+34612345057",
    cuisineTypes: ["Mediterránea", "Gourmet"],
    address: { formatted: "Carrer de la Missió 7A, Palma", lat: 39.5710, lng: 2.6480, placeId: null },
    schedule: makeSchedule("13:00", "15:00", ["sunday", "monday"]),
    isPro: true,
    menus: [
      { title: "Menú Fosh", description: "Menú degustación de 5 tiempos con productos de Mallorca + maridaje", price: 65.00, offerType: "Menú del día", tags: ["Gourmet", "Mediterránea"], includesDrink: true, includesDessert: true, includesCoffee: true, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasLactose: true } },
      { title: "Cordero mallorquín", description: "Carré de cordero de raza mallorquina con hierbas mediterráneas y verduras asadas", price: 28.00, offerType: "Plato del día", tags: ["Gourmet"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },
  {
    name: "Sa Llotja",
    shortDescription: "Pescados y arroces frente al puerto",
    phone: "+34612345058",
    cuisineTypes: ["Marisquería", "Mallorquina"],
    address: { formatted: "Passeig Sagrera 5, Palma", lat: 39.5680, lng: 2.6440, placeId: null },
    schedule: makeSchedule("13:00", "23:30", ["tuesday"]),
    menus: [
      { title: "Arroz marinero", description: "Arroz caldoso con gamba roja de Sóller, cigalas y almejas", price: 19.00, offerType: "Menú del día", tags: ["Marisquería", "Mallorquina"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
      { title: "Tumbet mallorquín", description: "Capas de patata, berenjena, pimiento rojo con salsa de tomate casera", price: 10.00, offerType: "Plato del día", tags: ["Mallorquina", "Vegetariana"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { isVegetarian: true } },
    ],
  },

  // ── A CORUÑA (3) ───────────────────────────────────────────────────
  {
    name: "A Penela",
    shortDescription: "Mariscos gallegos del día en la Pescadería",
    phone: "+34612345059",
    cuisineTypes: ["Gallega", "Marisquería"],
    address: { formatted: "Plaza de María Pita 12, A Coruña", lat: 43.3710, lng: -8.3960, placeId: null },
    schedule: makeSchedule("12:30", "16:00", ["monday"]),
    menus: [
      { title: "Menú gallego", description: "Caldo gallego, pulpo á feira, tarta de Santiago", price: 16.00, offerType: "Menú del día", tags: ["Gallega"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasNuts: true, hasEgg: true } },
      { title: "Empanada de zamburiñas", description: "Empanada gallega de zamburiñas con cebolla caramelizada", price: 12.00, offerType: "Plato del día", tags: ["Gallega"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasGluten: true } },
    ],
  },
  {
    name: "O Lagar da Estrela",
    shortDescription: "Cocina gallega de mercado en Monte Alto",
    phone: "+34612345060",
    cuisineTypes: ["Gallega", "Tradicional"],
    address: { formatted: "Calle de la Estrella 12, A Coruña", lat: 43.3730, lng: -8.4010, placeId: null },
    schedule: makeSchedule("13:00", "16:30", ["sunday"]),
    menus: [
      { title: "Menú de mercado", description: "Pimientos de Padrón, lacón con grelos, filloas de crema", price: 14.50, offerType: "Menú del día", tags: ["Gallega", "Tradicional"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true, hasLactose: true } },
      { title: "Navajas a la plancha", description: "Navajas frescas a la plancha con ajo y perejil", price: 15.00, offerType: "Plato del día", tags: ["Gallega", "Marisquería"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
  {
    name: "Taberna do Puntal",
    shortDescription: "Vinos y raciones en la calle Real",
    phone: "+34612345061",
    cuisineTypes: ["Gallega", "Tapas"],
    address: { formatted: "Calle Real 26, A Coruña", lat: 43.3700, lng: -8.3940, placeId: null },
    schedule: makeSchedule("12:00", "01:00"),
    menus: [
      { title: "Ración + Ribeiro", description: "Ración de chipirones encebollados + jarra de Ribeiro", price: 11.00, offerType: "Menú del día", tags: ["Gallega", "Tapas"], includesDrink: true, serviceTime: "both", dietaryInfo: { hasFish: true } },
      { title: "Zorza con cachelos", description: "Carne adobada gallega con patatas cocidas", price: 9.50, offerType: "Plato del día", tags: ["Gallega"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true } },
    ],
  },

  // ── SALAMANCA (2) ──────────────────────────────────────────────────
  {
    name: "Mesón de Gonzalo",
    shortDescription: "Cocina castellana junto a la Plaza Mayor",
    phone: "+34612345062",
    cuisineTypes: ["Castellana", "Tradicional"],
    address: { formatted: "Plaza del Poeta Iglesias 10, Salamanca", lat: 40.9650, lng: -5.6640, placeId: null },
    schedule: makeSchedule("13:00", "16:00", ["monday"]),
    menus: [
      { title: "Menú castellano", description: "Sopa castellana, cochinillo asado, natillas caseras", price: 15.00, offerType: "Menú del día", tags: ["Castellana", "Tradicional"], includesDrink: true, includesDessert: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasGluten: true, hasEgg: true, hasLactose: true } },
      { title: "Farinato con huevos", description: "Farinato salmantino a la plancha con huevos fritos y pimientos", price: 9.50, offerType: "Plato del día", tags: ["Castellana"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasEgg: true } },
    ],
  },
  {
    name: "Tapas 3.0",
    shortDescription: "Tapas modernas y cócteles en el centro",
    phone: "+34612345063",
    cuisineTypes: ["Tapas", "Creativa"],
    address: { formatted: "Calle Zamora 6, Salamanca", lat: 40.9660, lng: -5.6650, placeId: null },
    schedule: makeSchedule("12:00", "01:00"),
    menus: [
      { title: "Afterwork tapas + cóctel", description: "3 tapas de autor + cóctel del día", price: 14.00, offerType: "Menú del día", tags: ["Tapas", "Creativa"], includesDrink: true, serviceTime: "dinner", dietaryInfo: { hasMeat: true, hasGluten: true } },
      { title: "Tartar de atún", description: "Tartar de atún rojo con aguacate, soja, sésamo y chips de wonton", price: 13.50, offerType: "Plato del día", tags: ["Creativa"], includesDrink: false, serviceTime: "both", dietaryInfo: { hasFish: true, hasGluten: true } },
    ],
  },

  // ── ALICANTE (2) ───────────────────────────────────────────────────
  {
    name: "El Portal Taberna",
    shortDescription: "Arroces y tapas alicantinas",
    phone: "+34612345064",
    cuisineTypes: ["Alicantina", "Arroces"],
    address: { formatted: "Calle Bilbao 2, Alicante", lat: 38.3460, lng: -0.4900, placeId: null },
    schedule: makeSchedule("13:00", "16:30", ["monday"]),
    menus: [
      { title: "Arroz alicantino", description: "Arroz con costra alicantina: longaniza, morcilla, huevo gratinado", price: 13.00, offerType: "Menú del día", tags: ["Alicantina", "Arroces"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasMeat: true, hasEgg: true } },
      { title: "Coca de San Juan", description: "Coca de recapte con verduras asadas y atún", price: 8.00, offerType: "Plato del día", tags: ["Alicantina"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasGluten: true } },
    ],
  },
  {
    name: "Nou Manolín",
    shortDescription: "Mariscos y arroces de tradición marinera",
    phone: "+34612345065",
    cuisineTypes: ["Marisquería", "Mediterránea"],
    address: { formatted: "Calle Villegas 3, Alicante", lat: 38.3450, lng: -0.4870, placeId: null },
    schedule: makeSchedule("13:00", "23:30", ["sunday"]),
    isPro: true,
    menus: [
      { title: "Arroz a banda premium", description: "Arroz a banda con caldo de galera y alioli de azafrán", price: 16.00, offerType: "Menú del día", tags: ["Mediterránea", "Arroces"], includesDrink: true, serviceTime: "lunch", dietaryInfo: { hasFish: true, hasEgg: true } },
      { title: "Gamba roja de Dénia", description: "Gamba roja a la plancha con sal Maldon (200g)", price: 22.00, offerType: "Plato del día", tags: ["Marisquería"], includesDrink: false, serviceTime: "lunch", dietaryInfo: { hasFish: true } },
    ],
  },
];

// ── Main ─────────────────────────────────────────────────────────────

async function seed() {
  console.log("Getting access token...");
  accessToken();
  console.log("Token OK\n");

  const now = new Date();
  const isoNow = now.toISOString();
  const todayYMD = isoNow.slice(0, 10);
  let imgIdx = 0;
  const total = restaurants.length;
  const botOffset = 11; // bot11 .. bot60

  for (let i = 0; i < total; i++) {
    const r = restaurants[i];
    const botNum = botOffset + i;
    const email = `bot${botNum}@eatout-test.com`;
    const password = seedConfig.BOT_PASSWORD;

    console.log(`[${i + 1}/${total}] ${r.name} (${email})...`);

    const uid = await createAuthUser(email, password, r.name);

    await firestoreSet("users", uid, {
      id: uid,
      email,
      name: r.name,
      role: "COMERCIO",
      createdAt: isoNow,
    });

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

    // ID determinista por (businessId + title + día): reejecutar el seed
    // sobreescribe los docs en vez de crear duplicados.
    for (const menu of r.menus) {
      const offerId = seedConfig.deterministicOfferId(uid, menu.title, todayYMD);
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

  console.log(`\n=== Done! ${total} restaurant bots + ${total * 2} daily offers ===`);
  console.log(`Login: bot${botOffset}@eatout-test.com ... bot${botOffset + total - 1}@eatout-test.com`);
  console.log("Password: (see EATOUT_BOT_PASSWORD in functions/.env)");
}

seed()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Seed failed:", err);
    process.exit(1);
  });
