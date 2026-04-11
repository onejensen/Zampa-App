#!/usr/bin/env node
/**
 * Forzar la purga inmediata de una cuenta.
 *
 * Uso:
 *   node admin-delete-user.js <uid>
 *
 * Setea scheduledPurgeAt al momento actual, de modo que la próxima
 * ejecución de la Cloud Function purgeDeletedAccounts (o una llamada
 * manual con `firebase functions:shell`) la procese. NO borra nada
 * directamente — deja que la función haga el trabajo para que el
 * proceso sea idéntico al flujo normal y quede registrado en
 * purgedAccounts/{uid}.
 *
 * Requiere:
 *   - functions/.env con GOOGLE_APPLICATION_CREDENTIALS apuntando a
 *     una service account con permisos Firestore + Auth admin
 *   - O haber ejecutado `gcloud auth application-default login`
 */

const admin = require("firebase-admin");

async function main() {
    const uid = process.argv[2];
    if (!uid) {
        console.error("Usage: node admin-delete-user.js <uid>");
        process.exit(1);
    }

    admin.initializeApp();
    const db = admin.firestore();

    const userRef = db.collection("users").doc(uid);
    const snap = await userRef.get();
    if (!snap.exists) {
        console.error(`users/${uid} no existe.`);
        process.exit(2);
    }

    const now = admin.firestore.Timestamp.now();
    await userRef.update({
        deletedAt: now,
        scheduledPurgeAt: now,
    });

    console.log(`OK — users/${uid} marcado con scheduledPurgeAt=now.`);
    console.log("La próxima ejecución de purgeDeletedAccounts lo procesará.");
    console.log("Para disparar manualmente: firebase functions:shell → purgeDeletedAccounts()");
}

main().catch(e => {
    console.error("Error:", e);
    process.exit(3);
});
