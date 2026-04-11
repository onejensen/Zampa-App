#!/usr/bin/env node
/**
 * Seed único de config/exchangeRates.
 *
 * Uso:
 *   node seed-exchange-rates.js
 *
 * Se ejecuta una sola vez tras el primer deploy para que el doc exista
 * antes de que la scheduled function corra a las 05:00 del día siguiente.
 *
 * Requiere:
 *   - Variables de entorno de functions/.env cargadas, O
 *   - `gcloud auth application-default login` ejecutado previamente.
 */

const admin = require("firebase-admin");

const SUPPORTED = ["USD", "GBP", "JPY", "CHF", "SEK", "NOK", "DKK", "CAD", "AUD"];

async function main() {
    admin.initializeApp();
    const db = admin.firestore();

    const url = `https://api.frankfurter.app/latest?from=EUR&to=${SUPPORTED.join(",")}`;
    console.log(`Fetching ${url}`);
    const resp = await fetch(url);
    if (!resp.ok) {
        console.error(`HTTP ${resp.status} ${resp.statusText}`);
        process.exit(1);
    }
    const payload = await resp.json();
    const rates = payload?.rates;
    if (!rates || typeof rates !== "object") {
        console.error("Respuesta sin 'rates':", payload);
        process.exit(2);
    }

    const clean = {};
    for (const code of SUPPORTED) {
        const value = rates[code];
        if (typeof value !== "number" || !isFinite(value) || value <= 0) {
            console.error(`Tasa inválida para ${code}:`, value);
            process.exit(3);
        }
        clean[code] = value;
    }

    await db.collection("config").doc("exchangeRates").set({
        base: "EUR",
        rates: clean,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log("OK — config/exchangeRates escrito:");
    console.log(clean);
}

main().catch(e => {
    console.error("Error:", e);
    process.exit(99);
});
