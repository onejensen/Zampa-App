#!/usr/bin/env node
/**
 * Arregla las fotos rotas de las ofertas creadas con source.unsplash.com
 * (API deprecada desde 2024, devuelve 503).
 *
 * Sustituye el campo photoUrls de cada oferta por una URL directa del CDN
 * de Unsplash. Asigna fotos de una lista curada según la categoría de cocina.
 *
 * Uso:
 *   node fix-offer-photos.js            # dry-run
 *   node fix-offer-photos.js --commit   # ejecuta
 */

const { execSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const crypto = require("crypto");
const seedConfig = require("./_seed-config");

const PROJECT_ID = seedConfig.PROJECT_ID;
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

let _token = null;
function accessToken() {
  if (_token) return _token;
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
  _token = JSON.parse(resp).access_token;
  return _token;
}

async function fetchJSON(url, options = {}) {
  const resp = await fetch(url, options);
  const body = await resp.json();
  if (!resp.ok) throw new Error(`${resp.status}: ${JSON.stringify(body.error || body)}`);
  return body;
}

// Pool curado de fotos directas de Unsplash CDN (verificadas 200 OK).
// Sabemos que algunas no coinciden exactamente con la categoría de cocina,
// pero todas son fotos de comida profesionales y funcionan.
const PHOTOS_BY_CATEGORY = {
  tradicional:   [
    "photo-1504674900247-0877df9cc836",
    "photo-1546833999-b9f581a1996d",
    "photo-1540189549336-e6e99c3679fe",
  ],
  tapas:         [
    "photo-1565299624946-b28f40a0ae38",
    "photo-1504754524776-8f4f37790ca0",
    "photo-1551218808-94e220e084d2",
  ],
  marisqueria:   [
    "photo-1414235077428-338989a2e8c0",
    "photo-1551504734-5ee1c4a1479b",
    "photo-1598515214211-89d3c73ae83b",
  ],
  italiana:      [
    "photo-1513104890138-7c749659a591",
    "photo-1565299624946-b28f40a0ae38",
    "photo-1546833999-b9f581a1996d",
  ],
  japonesa:      [
    "photo-1414235077428-338989a2e8c0",
    "photo-1551504734-5ee1c4a1479b",
    "photo-1551218808-94e220e084d2",
  ],
  asiatica:      [
    "photo-1565557623262-b51c2513a641",
    "photo-1543339308-43e59d6b73a6",
    "photo-1551218808-94e220e084d2",
  ],
  mexicana:      [
    "photo-1565958011703-44f9829ba187",
    "photo-1568901346375-23c9450c58cd",
    "photo-1543339308-43e59d6b73a6",
  ],
  americana:     [
    "photo-1568901346375-23c9450c58cd",
    "photo-1551024506-0bccd828d307",
    "photo-1565958011703-44f9829ba187",
  ],
  vegana:        [
    "photo-1540189549336-e6e99c3679fe",
    "photo-1598515214211-89d3c73ae83b",
    "photo-1586190848861-99aa4a171e90",
  ],
  saludable:     [
    "photo-1540189549336-e6e99c3679fe",
    "photo-1565557623262-b51c2513a641",
    "photo-1586190848861-99aa4a171e90",
  ],
  francesa:      [
    "photo-1504674900247-0877df9cc836",
    "photo-1546833999-b9f581a1996d",
    "photo-1543339308-43e59d6b73a6",
  ],
  argentina:     [
    "photo-1504674900247-0877df9cc836",
    "photo-1546833999-b9f581a1996d",
    "photo-1551024506-0bccd828d307",
  ],
  brunch:        [
    "photo-1481070555726-e2fe8357725c",
    "photo-1504754524776-8f4f37790ca0",
    "photo-1529042410759-befb1204b468",
  ],
  mallorquina:   [
    "photo-1504754524776-8f4f37790ca0",
    "photo-1540189549336-e6e99c3679fe",
    "photo-1481070555726-e2fe8357725c",
  ],
};

function categoryFor(tags) {
  const t = (tags || []).map(x => x.toLowerCase()).join(" ");
  for (const cat of Object.keys(PHOTOS_BY_CATEGORY)) {
    if (t.includes(cat)) return cat;
  }
  return "tradicional";
}

function pickPhoto(docId, category) {
  const pool = PHOTOS_BY_CATEGORY[category] || PHOTOS_BY_CATEGORY.tradicional;
  const h = crypto.createHash("md5").update(docId).digest();
  const photoId = pool[h[0] % pool.length];
  return `https://images.unsplash.com/${photoId}?w=800&q=80`;
}

async function listAllOffers() {
  const token = accessToken();
  const offers = [];
  let pageToken = null;
  do {
    const url = new URL(`${FIRESTORE_BASE}/dailyOffers`);
    url.searchParams.set("pageSize", "300");
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const data = await fetchJSON(url.toString(), {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (data.documents) {
      for (const doc of data.documents) {
        const f = doc.fields || {};
        const photoUrls = f.photoUrls?.arrayValue?.values?.map(v => v.stringValue).filter(Boolean) || [];
        const tags = f.tags?.arrayValue?.values?.map(v => v.stringValue).filter(Boolean) || [];
        offers.push({
          _name: doc.name,
          _id: doc.name.split("/").pop(),
          photoUrls,
          tags,
          title: f.title?.stringValue || "",
        });
      }
    }
    pageToken = data.nextPageToken || null;
  } while (pageToken);
  return offers;
}

async function patchPhotoUrls(docName, newUrl) {
  const token = accessToken();
  const fields = {
    photoUrls: { arrayValue: { values: [{ stringValue: newUrl }] } },
  };
  const url = `https://firestore.googleapis.com/v1/${docName}?updateMask.fieldPaths=photoUrls`;
  await fetchJSON(url, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields }),
  });
}

async function main() {
  const commit = process.argv.includes("--commit");

  console.log("→ Listando dailyOffers…");
  const offers = await listAllOffers();
  console.log(`  ${offers.length} ofertas totales\n`);

  const broken = offers.filter(o =>
    o.photoUrls.some(u => u.includes("source.unsplash.com")) || o.photoUrls.length === 0
  );
  console.log(`  ${broken.length} ofertas con fotos rotas o vacías\n`);

  if (broken.length === 0) {
    console.log("No hay nada que arreglar.");
    return;
  }

  const plan = broken.map(o => {
    const cat = categoryFor(o.tags);
    const newUrl = pickPhoto(o._id, cat);
    return { ...o, newUrl, cat };
  });

  if (!commit) {
    console.log("DRY-RUN. Primeras 5:");
    plan.slice(0, 5).forEach(p => {
      console.log(`  [${p._id.slice(0, 10)}…] ${p.title}`);
      console.log(`    cat: ${p.cat}`);
      console.log(`    old: ${p.photoUrls[0] || "(vacío)"}`);
      console.log(`    new: ${p.newUrl}\n`);
    });
    console.log(`Ejecuta con --commit para actualizar ${plan.length} ofertas.`);
    return;
  }

  console.log(`Actualizando ${plan.length} ofertas en 3s… (Ctrl+C para cancelar)\n`);
  await new Promise(r => setTimeout(r, 3000));

  let done = 0, failed = 0;
  for (const p of plan) {
    try {
      await patchPhotoUrls(p._name, p.newUrl);
      done++;
      if (done % 10 === 0) console.log(`  ${done}/${plan.length}…`);
    } catch (e) {
      failed++;
      console.error(`  fallo ${p._id}: ${e.message}`);
    }
  }
  console.log(`\nHecho: ${done} actualizadas, ${failed} fallidas.`);
}

main().catch(e => { console.error("Fallo:", e); process.exit(1); });
