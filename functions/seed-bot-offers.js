#!/usr/bin/env node
/**
 * Genera ofertas realistas en `dailyOffers` para todos los bots
 * (`bot*@eatout-test.com`), adaptando el contenido al cuisineType
 * del comercio.
 *
 * - 1 oferta por bot (idempotente gracias a deterministicOfferId)
 * - Mezcla equilibrada de: Menú del día / Plato del día / Oferta permanente
 * - Precios acordes al tipo de comercio
 * - Fotos Unsplash con query relevante
 * - isMerchantPro según planTier del business
 *
 * Uso:
 *   node seed-bot-offers.js            # dry-run
 *   node seed-bot-offers.js --commit   # ejecuta
 */

const { execSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const crypto = require("crypto");
const seedConfig = require("./_seed-config");

const PROJECT_ID = seedConfig.PROJECT_ID;
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

// ───────────────────────── OAuth + Firestore helpers ─────────────────────

let _token = null;
function accessToken() {
  if (_token) return _token;
  const configRaw = fs.readFileSync(
    path.join(os.homedir(), ".config/configstore/firebase-tools.json"),
    "utf8"
  );
  const config = JSON.parse(configRaw);
  const refreshToken = config.tokens?.refresh_token;
  if (!refreshToken) throw new Error("firebase login requerido");
  const resp = execSync(
    `curl -s -X POST "https://oauth2.googleapis.com/token" ` +
    `-H "Content-Type: application/x-www-form-urlencoded" ` +
    `-d "grant_type=refresh_token&refresh_token=${refreshToken}&client_id=${seedConfig.OAUTH_CLIENT_ID}&client_secret=${seedConfig.OAUTH_CLIENT_SECRET}"`,
    { encoding: "utf8" }
  );
  _token = JSON.parse(resp).access_token;
  return _token;
}

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
  if (Array.isArray(val)) return { arrayValue: { values: val.map(toFirestoreValue) } };
  if (typeof val === "object") {
    const fields = {};
    for (const [k, v] of Object.entries(val)) fields[k] = toFirestoreValue(v);
    return { mapValue: { fields } };
  }
  return { stringValue: String(val) };
}

async function firestoreUpsert(collection, docId, data) {
  const fields = {};
  for (const [key, value] of Object.entries(data)) fields[key] = toFirestoreValue(value);
  const url = `${FIRESTORE_BASE}/${collection}/${docId}`;
  await fetchJSON(url, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${accessToken()}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields }),
  });
}

async function firestoreGetDoc(collection, docId) {
  const url = `${FIRESTORE_BASE}/${collection}/${docId}`;
  const resp = await fetch(url, { headers: { Authorization: `Bearer ${accessToken()}` } });
  if (resp.status === 404) return null;
  const body = await resp.json();
  if (!resp.ok) throw new Error(`${resp.status}: ${JSON.stringify(body.error || body)}`);
  return body;
}

function extractField(doc, name) {
  if (!doc || !doc.fields || !doc.fields[name]) return null;
  const v = doc.fields[name];
  if (v.stringValue !== undefined) return v.stringValue;
  if (v.booleanValue !== undefined) return v.booleanValue;
  if (v.integerValue !== undefined) return Number(v.integerValue);
  if (v.doubleValue !== undefined) return v.doubleValue;
  if (v.arrayValue) return (v.arrayValue.values || []).map(x => x.stringValue ?? null).filter(x => x !== null);
  return null;
}

// ───────────────────────── Auth export (bots) ─────────────────────

function listBots() {
  const tmp = path.join(os.tmpdir(), `zampa-users-${Date.now()}.json`);
  console.log("→ Exportando Auth (firebase CLI)…");
  execSync(
    `firebase auth:export "${tmp}" --format=JSON --project ${PROJECT_ID}`,
    { stdio: ["ignore", "ignore", "inherit"] }
  );
  const raw = fs.readFileSync(tmp, "utf8");
  fs.unlinkSync(tmp);
  const data = JSON.parse(raw);
  const bots = (data.users || []).filter(u => u.email && u.email.endsWith("@eatout-test.com"));
  console.log(`  ${bots.length} bots\n`);
  return bots.map(u => ({ uid: u.localId, email: u.email }));
}

// ───────────────────────── Offer templates ─────────────────────

function fullDietary(partial = {}) {
  return {
    isVegetarian: false, isVegan: false, hasMeat: false, hasFish: false,
    hasGluten: false, hasLactose: false, hasNuts: false, hasEgg: false,
    ...partial,
  };
}

// Pool de plantillas por categoría de cocina.
// Cada plantilla define: title, description, priceTotal, dietary flags, photo query
const TEMPLATES = {
  tradicional: [
    { title: "Cocido madrileño casero", desc: "Garbanzos, ternera, chorizo, morcilla, repollo y fideos en su caldo", price: 14.5, diet: { hasMeat: true, hasGluten: true }, photo: "cocido,stew" },
    { title: "Rabo de toro al vino tinto", desc: "Guisado 6 horas a fuego lento con zanahoria y patatas panadera", price: 16.0, diet: { hasMeat: true }, photo: "beef,stew" },
    { title: "Callos a la madrileña", desc: "Callos con chorizo, morcilla y garbanzos en salsa tradicional", price: 12.5, diet: { hasMeat: true, hasGluten: true }, photo: "callos,tripe" },
    { title: "Tortilla de patata + ensalada", desc: "Tortilla jugosa con cebolla pochada, ensalada de temporada y pan", price: 10.0, diet: { hasEgg: true, hasGluten: true, hasLactose: true }, photo: "spanish,omelette" },
    { title: "Cochinillo segoviano", desc: "Cuarto de cochinillo asado en horno de leña con su jugo", price: 22.0, diet: { hasMeat: true }, photo: "cochinillo,roast" },
    { title: "Fabada asturiana", desc: "Fabes con chorizo, morcilla, lacón y panceta. Postre y pan", price: 13.5, diet: { hasMeat: true, hasGluten: true }, photo: "fabada,bean,stew" },
  ],
  tapas: [
    { title: "Tabla ibérica + vermú", desc: "Jamón, lomo, chorizo, queso manchego y pan con tomate", price: 14.0, diet: { hasMeat: true, hasGluten: true, hasLactose: true }, photo: "iberico,ham,tapas" },
    { title: "Gilda + pintxos variados", desc: "Selección de 4 pintxos calientes y fríos con copa de txakoli", price: 9.5, diet: { hasFish: true, hasGluten: true }, photo: "pintxos,basque" },
    { title: "Patatas bravas + croquetas", desc: "Ración de bravas y 6 croquetas caseras (jamón y bacalao)", price: 10.5, diet: { hasGluten: true, hasLactose: true, hasFish: true }, photo: "tapas,croquetas" },
    { title: "Vermú con tapas de la casa", desc: "Vermú rojo + 3 tapas rotativas + aceitunas y conservas", price: 8.0, diet: { hasFish: true, hasGluten: true }, photo: "vermut,tapas" },
  ],
  marisqueria: [
    { title: "Mariscada para 1", desc: "Langostinos, gambas, navajas, mejillones y berberechos", price: 28.0, diet: { hasFish: true }, photo: "seafood,mariscada" },
    { title: "Pulpo a la gallega", desc: "Pulpo cocido a punto, patata cachelos, aceite y pimentón", price: 18.0, diet: { hasFish: true }, photo: "pulpo,galician" },
    { title: "Paella de marisco", desc: "Arroz con gambas, mejillones, calamar y alioli. Incluye bebida", price: 16.5, diet: { hasFish: true }, photo: "paella,seafood" },
    { title: "Sardinas a la brasa", desc: "Ración de sardinas a la parrilla con pimientos y patata", price: 12.0, diet: { hasFish: true }, photo: "sardines,grilled" },
  ],
  italiana: [
    { title: "Pizza Margherita + bebida", desc: "Masa artesana 48h, mozzarella fiordilatte, albahaca fresca", price: 11.5, diet: { isVegetarian: true, hasGluten: true, hasLactose: true }, photo: "pizza,margherita" },
    { title: "Pasta carbonara auténtica", desc: "Spaghetti con guanciale, pecorino romano, huevo y pimienta", price: 12.5, diet: { hasMeat: true, hasGluten: true, hasLactose: true, hasEgg: true }, photo: "carbonara,pasta" },
    { title: "Lasaña boloñesa casera", desc: "Carne de ternera, bechamel, parmesano y salsa de tomate DOP", price: 13.0, diet: { hasMeat: true, hasGluten: true, hasLactose: true, hasEgg: true }, photo: "lasagna" },
    { title: "Risotto de hongos", desc: "Arborio, boletus, parmesano y trufa negra rallada", price: 14.5, diet: { isVegetarian: true, hasLactose: true }, photo: "risotto,mushroom" },
  ],
  japonesa: [
    { title: "Menú sushi 16 piezas", desc: "8 nigiris variados + 8 makis salmón/atún + sopa miso", price: 16.0, diet: { hasFish: true, hasGluten: true }, photo: "sushi,platter" },
    { title: "Ramen tonkotsu", desc: "Caldo de cerdo 12h, chashu, huevo ajitsuke, nori y brotes", price: 13.5, diet: { hasMeat: true, hasGluten: true, hasEgg: true }, photo: "ramen,tonkotsu" },
    { title: "Bao buns (3 uds) + gyozas", desc: "3 baos (cerdo/pollo/seta) + 6 gyozas + sopa miso", price: 14.0, diet: { hasMeat: true, hasGluten: true }, photo: "bao,buns" },
    { title: "Sushi fusión box", desc: "16 piezas de roll creativo + edamame + té verde", price: 18.5, diet: { hasFish: true, hasGluten: true }, photo: "sushi,box,creative" },
  ],
  asiatica: [
    { title: "Pad thai de pollo", desc: "Fideos de arroz salteados con cacahuete, lima y huevo", price: 11.5, diet: { hasMeat: true, hasNuts: true, hasEgg: true }, photo: "pad,thai" },
    { title: "Curry verde thai", desc: "Curry verde con pollo, leche de coco, berenjena y arroz jazmín", price: 12.0, diet: { hasMeat: true }, photo: "thai,curry" },
    { title: "Menú indio del día", desc: "Chicken tikka masala + arroz basmati + naan + lassi de mango", price: 13.5, diet: { hasMeat: true, hasGluten: true, hasLactose: true }, photo: "indian,curry" },
  ],
  mexicana: [
    { title: "Tacos al pastor (3 uds)", desc: "3 tacos de cerdo marinado con piña, cilantro y salsa verde", price: 9.5, diet: { hasMeat: true, hasGluten: true }, photo: "tacos,pastor" },
    { title: "Burrito XL + guacamole", desc: "Tortilla grande, carne, frijoles, arroz, queso, guacamole y bebida", price: 11.0, diet: { hasMeat: true, hasGluten: true, hasLactose: true }, photo: "burrito,mexican" },
    { title: "Quesadilla + nachos", desc: "Quesadilla de pollo y queso + nachos con pico de gallo", price: 10.5, diet: { hasMeat: true, hasGluten: true, hasLactose: true }, photo: "quesadilla,nachos" },
  ],
  americana: [
    { title: "Smash burger + patatas", desc: "Doble smash de ternera, cheddar, pepinillo, patatas y salsa", price: 12.5, diet: { hasMeat: true, hasGluten: true, hasLactose: true, hasEgg: true }, photo: "smash,burger" },
    { title: "BBQ ribs + coleslaw", desc: "1/2 costillar de cerdo a baja temperatura con salsa BBQ casera", price: 15.0, diet: { hasMeat: true, hasGluten: true, hasEgg: true }, photo: "bbq,ribs" },
    { title: "Boneless wings + cerveza", desc: "12 nuggets de pollo con salsa buffalo + ranch + cerveza", price: 11.5, diet: { hasMeat: true, hasGluten: true, hasLactose: true, hasEgg: true }, photo: "chicken,wings" },
  ],
  vegana: [
    { title: "Buddha bowl de quinoa", desc: "Quinoa, aguacate, edamame, zanahoria, tahini y semillas", price: 11.0, diet: { isVegan: true, isVegetarian: true, hasNuts: true }, photo: "buddha,bowl" },
    { title: "Curry vegano de garbanzo", desc: "Garbanzo, espinaca, tomate, leche de coco y arroz basmati", price: 10.0, diet: { isVegan: true, isVegetarian: true }, photo: "vegan,curry" },
    { title: "Hamburguesa vegetal", desc: "Hamburguesa de lenteja y remolacha con pan integral y patatas", price: 11.5, diet: { isVegan: true, isVegetarian: true, hasGluten: true }, photo: "vegan,burger" },
  ],
  saludable: [
    { title: "Poke bowl de salmón", desc: "Arroz, salmón marinado, aguacate, edamame, mango y sésamo", price: 12.5, diet: { hasFish: true }, photo: "poke,bowl" },
    { title: "Ensalada de quinoa y feta", desc: "Quinoa, feta, cherry, pepino, olivas Kalamata y vinagreta", price: 10.5, diet: { isVegetarian: true, hasLactose: true }, photo: "quinoa,salad" },
    { title: "Wrap de pollo y aguacate", desc: "Pollo a la plancha, aguacate, queso fresco, tomate y pan integral", price: 9.5, diet: { hasMeat: true, hasGluten: true, hasLactose: true }, photo: "chicken,wrap" },
  ],
  francesa: [
    { title: "Foie a la plancha + higos", desc: "Foie fresco, higos caramelizados, reducción de Pedro Ximénez", price: 22.0, diet: { hasMeat: true, hasLactose: true }, photo: "foie,gras" },
    { title: "Steak tartare tradicional", desc: "Solomillo picado a cuchillo con alcaparras, cebolleta y yema", price: 18.0, diet: { hasMeat: true, hasEgg: true }, photo: "steak,tartare" },
    { title: "Confit de pato + patata trufada", desc: "Muslo de pato confitado a 85ºC con puré de patata y trufa", price: 21.0, diet: { hasMeat: true, hasLactose: true }, photo: "duck,confit" },
  ],
  argentina: [
    { title: "Bife de chorizo + chimichurri", desc: "Bife de 300g a la parrilla con chimichurri y papas al horno", price: 19.5, diet: { hasMeat: true }, photo: "argentine,steak" },
    { title: "Empanadas mixtas (6 uds)", desc: "2 carne, 2 jamón y queso, 2 pollo. Incluye bebida", price: 9.5, diet: { hasMeat: true, hasGluten: true, hasLactose: true }, photo: "empanadas" },
    { title: "Asado completo con guarnición", desc: "Entraña, morcilla, chorizo criollo, papas y ensalada", price: 22.5, diet: { hasMeat: true }, photo: "asado,parrilla" },
  ],
  brunch: [
    { title: "Brunch completo", desc: "Huevos benedict, tostada de aguacate, café y zumo natural", price: 14.5, diet: { isVegetarian: true, hasGluten: true, hasLactose: true, hasEgg: true }, photo: "brunch" },
    { title: "Tostada de aguacate y salmón", desc: "Pan de masa madre, aguacate, salmón ahumado, huevo y eneldo", price: 11.5, diet: { hasFish: true, hasGluten: true, hasEgg: true }, photo: "avocado,toast" },
    { title: "Pancakes con fruta y sirope", desc: "3 pancakes americanos con fruta fresca, nata y sirope de arce", price: 9.5, diet: { isVegetarian: true, hasGluten: true, hasLactose: true, hasEgg: true }, photo: "pancakes" },
  ],
  mallorquina: [
    { title: "Ensaimada + chocolate", desc: "Ensaimada artesanal tradicional con chocolate caliente a la taza", price: 5.5, diet: { isVegetarian: true, hasGluten: true, hasLactose: true, hasEgg: true }, photo: "ensaimada" },
    { title: "Tumbet mallorquín", desc: "Berenjena, patata, calabacín y pimiento con salsa de tomate", price: 11.0, diet: { isVegan: true, isVegetarian: true }, photo: "tumbet,mallorca" },
    { title: "Frit mallorquí", desc: "Frito tradicional con hígado, patata, pimiento y especias", price: 12.5, diet: { hasMeat: true }, photo: "mallorcan,food" },
  ],
};

// Mapeo cuisine → categoría de templates
function categoryFor(cuisines) {
  const joined = (cuisines || []).join(" ").toLowerCase();
  if (/tapas|vermuter|vino/.test(joined)) return "tapas";
  if (/japones|ramen/.test(joined)) return "japonesa";
  if (/thai|india|asiátic|asiatic/.test(joined)) return "asiatica";
  if (/italian|pizza|pasta/.test(joined)) return "italiana";
  if (/marisquer|gallega|chiringuit/.test(joined)) return "marisqueria";
  if (/vegan/.test(joined)) return "vegana";
  if (/hawaian|saludable/.test(joined)) return "saludable";
  if (/americ|hamburgues/.test(joined)) return "americana";
  if (/mexican|street/.test(joined)) return "mexicana";
  if (/frances|gourmet|creativ|mediterr/.test(joined)) return "francesa";
  if (/argentin|parrill/.test(joined)) return "argentina";
  if (/brunch|cafeter/.test(joined)) return "brunch";
  if (/mallorq/.test(joined)) return "mallorquina";
  return "tradicional";
}

// Tipo de oferta rotativo, distribución controlada ~60/25/15
function offerTypeFor(idx) {
  const mod = idx % 20;
  if (mod < 12) return "Menu del dia";        // 60%
  if (mod < 17) return "Plato del dia";       // 25%
  return "Oferta permanente";                 // 15%
}

// Selecciona plantilla determinista según UID
function pickTemplate(uid, category) {
  const pool = TEMPLATES[category] || TEMPLATES.tradicional;
  const h = crypto.createHash("md5").update(uid).digest();
  return pool[h[0] % pool.length];
}

// ───────────────────────── Main ─────────────────────

async function main() {
  const commit = process.argv.includes("--commit");
  const bots = listBots();

  console.log("→ Leyendo businesses/{uid} para mapear cocina y plan…");
  const enriched = [];
  for (const b of bots) {
    const doc = await firestoreGetDoc("businesses", b.uid);
    if (!doc) { console.log(`  skip ${b.email}: sin business doc`); continue; }
    const cuisines = extractField(doc, "cuisineTypes") || [];
    const planTier = extractField(doc, "planTier") || "free";
    const name = extractField(doc, "name") || "(sin nombre)";
    enriched.push({ ...b, name, cuisines, planTier });
  }
  console.log(`  ${enriched.length} bots con business válido\n`);

  const now = new Date();
  const isoNow = now.toISOString();
  const todayYMD = isoNow.slice(0, 10);

  const plan = enriched.map((b, i) => {
    const cat = categoryFor(b.cuisines);
    const tpl = pickTemplate(b.uid, cat);
    const offerType = offerTypeFor(i);
    const docId = seedConfig.deterministicOfferId(b.uid, tpl.title, todayYMD);
    return { bot: b, cat, tpl, offerType, docId };
  });

  console.log("Distribución por tipo de oferta:");
  const countByType = plan.reduce((acc, p) => { acc[p.offerType] = (acc[p.offerType] || 0) + 1; return acc; }, {});
  for (const [k, v] of Object.entries(countByType)) console.log(`  ${k}: ${v}`);
  console.log();

  console.log("Distribución por categoría de cocina:");
  const countByCat = plan.reduce((acc, p) => { acc[p.cat] = (acc[p.cat] || 0) + 1; return acc; }, {});
  for (const [k, v] of Object.entries(countByCat).sort((a, b) => b[1] - a[1])) console.log(`  ${k}: ${v}`);
  console.log();

  if (!commit) {
    console.log("DRY-RUN. Primeras 5 ofertas que se crearán:\n");
    for (const p of plan.slice(0, 5)) {
      console.log(`  ${p.bot.name}  [${p.bot.planTier}]  ${p.cat}`);
      console.log(`    → ${p.tpl.title}  (${p.offerType})  ${p.tpl.price}€`);
      console.log(`    ${p.tpl.desc}\n`);
    }
    console.log(`Ejecuta con --commit para crear las ${plan.length} ofertas.`);
    return;
  }

  console.log(`Creando ${plan.length} ofertas en 3s… (Ctrl+C para cancelar)\n`);
  await new Promise(r => setTimeout(r, 3000));

  let done = 0, failed = 0;
  for (const p of plan) {
    const { bot, tpl, offerType, docId } = p;
    // source.unsplash.com está deprecado; usamos direct CDN de Unsplash.
    // El photo ID concreto se elige con fix-offer-photos.js si quedara vacío.
    const photoPool = [
      "photo-1504674900247-0877df9cc836", "photo-1546833999-b9f581a1996d",
      "photo-1540189549336-e6e99c3679fe", "photo-1565299624946-b28f40a0ae38",
      "photo-1504754524776-8f4f37790ca0", "photo-1551218808-94e220e084d2",
      "photo-1414235077428-338989a2e8c0", "photo-1551504734-5ee1c4a1479b",
      "photo-1598515214211-89d3c73ae83b", "photo-1513104890138-7c749659a591",
      "photo-1565557623262-b51c2513a641", "photo-1543339308-43e59d6b73a6",
      "photo-1565958011703-44f9829ba187", "photo-1568901346375-23c9450c58cd",
      "photo-1551024506-0bccd828d307", "photo-1481070555726-e2fe8357725c",
      "photo-1586190848861-99aa4a171e90", "photo-1529042410759-befb1204b468",
    ];
    const idx = crypto.createHash("md5").update(bot.uid).digest()[1] % photoPool.length;
    const photoUrl = `https://images.unsplash.com/${photoPool[idx]}?w=800&q=80`;
    try {
      await firestoreUpsert("dailyOffers", docId, {
        id: docId,
        businessId: bot.uid,
        date: isoNow,
        title: tpl.title,
        description: tpl.desc,
        priceTotal: tpl.price,
        currency: "EUR",
        photoUrls: [photoUrl],
        tags: [p.cat, ...bot.cuisines.slice(0, 2)],
        createdAt: isoNow,
        updatedAt: isoNow,
        isActive: true,
        isMerchantPro: bot.planTier === "pro",
        isMerchantVerified: true,
        dietaryInfo: fullDietary(tpl.diet),
        offerType,
        includesDrink: offerType === "Menu del dia",
        includesDessert: offerType === "Menu del dia",
        includesCoffee: offerType === "Menu del dia",
        serviceTime: "lunch",
        isPermanent: offerType === "Oferta permanente",
      });
      done++;
      if (done % 10 === 0) console.log(`  ${done}/${plan.length}…`);
    } catch (e) {
      failed++;
      console.error(`  fallo ${bot.email}: ${e.message}`);
    }
  }
  console.log(`\nHecho: ${done} creadas, ${failed} fallidas.`);
}

main()
  .then(() => process.exit(0))
  .catch(err => { console.error("Fallo:", err); process.exit(1); });
