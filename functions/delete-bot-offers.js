#!/usr/bin/env node
/**
 * Borra TODAS las ofertas (dailyOffers) cuyo businessId pertenezca a un bot
 * (cuenta con email `bot*@eatout-test.com`).
 *
 * NO borra ofertas de usuarios reales.
 * NO borra las cuentas bot, ni sus businesses, ni sus metrics.
 *
 * Uso:
 *   node delete-bot-offers.js            # dry-run (lista pero no borra)
 *   node delete-bot-offers.js --commit   # ejecuta el borrado
 *
 * Usa el mismo patrón que el resto de seeds: OAuth refresh token del
 * firebase CLI + REST API de Firestore. No requiere gcloud ni service
 * account separadas.
 */

const { execSync } = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const seedConfig = require("./_seed-config");

const PROJECT_ID = seedConfig.PROJECT_ID;
const FIRESTORE_BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

let _accessToken = null;
function accessToken() {
  if (_accessToken) return _accessToken;
  const configRaw = fs.readFileSync(
    path.join(os.homedir(), ".config/configstore/firebase-tools.json"),
    "utf8"
  );
  const config = JSON.parse(configRaw);
  const refreshToken = config.tokens?.refresh_token;
  if (!refreshToken) throw new Error("No refresh token. Run: firebase login");
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

function exportUsersToTempFile() {
  const tmp = path.join(os.tmpdir(), `zampa-users-${Date.now()}.json`);
  console.log(`→ Exportando cuentas Auth vía firebase CLI → ${tmp}`);
  execSync(
    `firebase auth:export "${tmp}" --format=JSON --project ${PROJECT_ID}`,
    { stdio: ["ignore", "ignore", "inherit"] }
  );
  const raw = fs.readFileSync(tmp, "utf8");
  fs.unlinkSync(tmp);
  return JSON.parse(raw);
}

function listBotUids() {
  const data = exportUsersToTempFile();
  const users = data.users || [];
  const botUids = new Set();
  for (const u of users) {
    if (u.email && u.email.endsWith("@eatout-test.com")) {
      botUids.add(u.localId);
    }
  }
  console.log(`  total cuentas: ${users.length}, bots: ${botUids.size}\n`);
  return botUids;
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
        const fields = doc.fields || {};
        offers.push({
          _name: doc.name,
          _id: doc.name.split("/").pop(),
          businessId: fields.businessId?.stringValue || null,
          title: fields.title?.stringValue || "(sin título)",
        });
      }
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
  const commit = process.argv.includes("--commit");

  const botUids = listBotUids();
  if (botUids.size === 0) {
    console.log("No hay bots. Nada que borrar.");
    return;
  }

  console.log("→ Escaneando dailyOffers…");
  const offers = await listAllOffers();
  console.log(`  ${offers.length} ofertas totales.\n`);

  const toDelete = offers.filter(o => o.businessId && botUids.has(o.businessId));
  const keep = offers.length - toDelete.length;
  console.log(`  ${toDelete.length} ofertas de bots → borrar`);
  console.log(`  ${keep} ofertas de usuarios reales → conservar\n`);

  if (toDelete.length === 0) {
    console.log("No hay ofertas de bots. Nada que hacer.");
    return;
  }

  if (!commit) {
    console.log("DRY-RUN. Primeras 10:");
    toDelete.slice(0, 10).forEach(o => {
      console.log(`  - [${o._id.slice(0, 12)}…] ${o.title}  (biz ${o.businessId.slice(0, 8)}…)`);
    });
    console.log(`\nEjecuta con --commit para borrar las ${toDelete.length} ofertas.`);
    return;
  }

  console.log(`Borrando ${toDelete.length} ofertas en 5 segundos… (Ctrl+C para cancelar)`);
  await new Promise(r => setTimeout(r, 5000));

  let done = 0;
  for (const o of toDelete) {
    try {
      await deleteDoc(o._name);
      done++;
      if (done % 10 === 0) console.log(`  ${done}/${toDelete.length}…`);
    } catch (e) {
      console.error(`  fallo borrando ${o._id}: ${e.message}`);
    }
  }
  console.log(`\nHecho: ${done}/${toDelete.length} ofertas de bots borradas.`);
}

main()
  .then(() => process.exit(0))
  .catch(err => {
    console.error("Fallo:", err);
    process.exit(1);
  });
