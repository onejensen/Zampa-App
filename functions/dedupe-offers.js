/**
 * Limpieza de ofertas duplicadas en `dailyOffers`.
 *
 * Agrupa por (businessId, title) y conserva solo la oferta más reciente
 * (por createdAt); borra el resto. Antes de borrar imprime un resumen
 * para que puedas cancelar con Ctrl+C si ves algo raro.
 *
 * Uso:   node dedupe-offers.js
 * Setup: copy functions/.env.example to functions/.env si no existe.
 */

const { execSync } = require("child_process");
const seedConfig = require("./_seed-config");

const PROJECT_ID = seedConfig.PROJECT_ID;
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

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

async function fetchJSON(url, options = {}) {
  const resp = await fetch(url, options);
  const body = await resp.json();
  if (!resp.ok) throw new Error(`${resp.status}: ${JSON.stringify(body.error || body)}`);
  return body;
}

// Convierte un doc Firestore REST a un objeto JS plano (solo campos que nos importan).
function parseDoc(doc) {
  const fields = doc.fields || {};
  const flat = {};
  for (const [k, v] of Object.entries(fields)) {
    if (v.stringValue !== undefined) flat[k] = v.stringValue;
    else if (v.booleanValue !== undefined) flat[k] = v.booleanValue;
    else if (v.integerValue !== undefined) flat[k] = Number(v.integerValue);
    else if (v.doubleValue !== undefined) flat[k] = v.doubleValue;
    // ignoramos arrays/maps: no los necesitamos aquí
  }
  flat._name = doc.name;               // ruta completa: projects/.../documents/dailyOffers/{id}
  flat._id = doc.name.split("/").pop();
  return flat;
}

async function listAllOffers() {
  const token = accessToken();
  let offers = [];
  let pageToken = null;
  do {
    const url = new URL(`${FIRESTORE_BASE}/dailyOffers`);
    url.searchParams.set("pageSize", "300");
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const data = await fetchJSON(url.toString(), {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (data.documents) {
      offers.push(...data.documents.map(parseDoc));
    }
    pageToken = data.nextPageToken || null;
  } while (pageToken);
  return offers;
}

async function deleteDoc(docName) {
  const token = accessToken();
  const url = `https://firestore.googleapis.com/v1/${docName}`;
  await fetchJSON(url, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
}

async function main() {
  console.log("Cargando todas las dailyOffers...");
  const offers = await listAllOffers();
  console.log(`Total offers en Firestore: ${offers.length}\n`);

  // Agrupar por (businessId + title)
  const groups = new Map();
  for (const offer of offers) {
    const key = `${offer.businessId || "?"}::${offer.title || "?"}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(offer);
  }

  // Filtrar solo grupos con duplicados
  const duplicates = [...groups.entries()].filter(([_, arr]) => arr.length > 1);

  if (duplicates.length === 0) {
    console.log("No hay ofertas duplicadas. Nada que hacer.");
    return;
  }

  console.log(`Grupos con duplicados: ${duplicates.length}\n`);
  let toDelete = [];
  for (const [key, arr] of duplicates) {
    // Ordenar descendente por createdAt (string ISO, el orden lexicográfico coincide)
    arr.sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
    const keeper = arr[0];
    const losers = arr.slice(1);
    const [businessId, title] = key.split("::");
    console.log(`  ${title} (business: ${businessId.slice(0, 8)}...)`);
    console.log(`    KEEP  ${keeper._id}  ${keeper.createdAt || ""}`);
    losers.forEach(l => {
      console.log(`    DROP  ${l._id}  ${l.createdAt || ""}`);
      toDelete.push(l);
    });
  }

  console.log(`\nVa a borrar ${toDelete.length} documentos.`);
  console.log("Pulsa Ctrl+C en 5s para cancelar.\n");
  await new Promise(r => setTimeout(r, 5000));

  let done = 0;
  for (const doc of toDelete) {
    try {
      await deleteDoc(doc._name);
      done++;
      if (done % 10 === 0) console.log(`  ${done}/${toDelete.length}...`);
    } catch (e) {
      console.error(`  fallo borrando ${doc._id}: ${e.message}`);
    }
  }
  console.log(`\nHecho: ${done}/${toDelete.length} borradas.`);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Dedupe falló:", err);
    process.exit(1);
  });
