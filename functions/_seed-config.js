/**
 * Shared config loader for the seed scripts.
 *
 * Reads required values from environment variables (loaded from `functions/.env`
 * via `dotenv` if installed, otherwise from the real process env). Hard-fails
 * with an actionable error if any required value is missing.
 *
 * Why this exists: until 2026-04 these values were hardcoded inside the seed
 * scripts and committed to git, including a Google OAuth client secret. The
 * secret has been rotated and the scripts now load all credentials from .env.
 */

const fs = require("fs");
const path = require("path");

// Minimal .env loader so we don't introduce a runtime dependency on `dotenv`.
function loadDotEnv() {
  const envPath = path.join(__dirname, ".env");
  if (!fs.existsSync(envPath)) return;
  const raw = fs.readFileSync(envPath, "utf8");
  for (const line of raw.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq === -1) continue;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
}

loadDotEnv();

function require_(name) {
  const v = process.env[name];
  if (!v) {
    console.error(
      `\n[seed-config] Missing required env var: ${name}\n` +
      `Copy functions/.env.example to functions/.env and fill in the values.\n`
    );
    process.exit(1);
  }
  return v;
}

/**
 * ID determinista para una oferta a partir de (businessId, title, fecha).
 * Si se reejecuta un seed el mismo día, el doc se sobreescribe en vez de
 * crearse uno nuevo. Evita el problema histórico de acumular duplicados.
 */
function deterministicOfferId(businessId, title, dateYMD) {
  const crypto = require("crypto");
  const key = `${businessId}::${title}::${dateYMD}`;
  return crypto.createHash("sha1").update(key).digest("hex").slice(0, 22);
}

/**
 * Genera un NIF sintético VÁLIDO (algoritmo mod 23) a partir del uid del bot.
 * Formato: 8 dígitos + letra de control. Determinista: mismo uid → mismo NIF.
 * Estos NIF no corresponden a personas/empresas reales; solo sirven para que
 * las rules de Firestore acepten el campo `taxId` en los seed bots.
 */
function syntheticBotTaxId(uid) {
  const crypto = require("crypto");
  const hash = crypto.createHash("sha1").update(`bot-taxid::${uid}`).digest("hex");
  // Primeros 8 dígitos decimales estables a partir del hash (tratamos hex como int).
  const num = parseInt(hash.slice(0, 12), 16) % 100000000;
  const body = String(num).padStart(8, "0");
  const letters = "TRWAGMYFPDXBNJZSQVHLCKE";
  const letter = letters[num % 23];
  return body + letter;
}

module.exports = {
  PROJECT_ID:           require_("EATOUT_PROJECT_ID"),
  API_KEY:              require_("EATOUT_FIREBASE_API_KEY"),
  OAUTH_CLIENT_ID:      require_("EATOUT_FIREBASE_OAUTH_CLIENT_ID"),
  OAUTH_CLIENT_SECRET:  require_("EATOUT_FIREBASE_OAUTH_CLIENT_SECRET"),
  BOT_PASSWORD:         require_("EATOUT_BOT_PASSWORD"),
  deterministicOfferId,
  syntheticBotTaxId,
};
