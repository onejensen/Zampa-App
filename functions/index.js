const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onRequest } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { defineSecret } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

// ── Suscripciones in-app (StoreKit + Play Billing) ────────────────────────────
//
// Apple → la app se inicializa pasando `appAccountToken: UUID(uuidString: firebase.auth.uid)`
// en `Product.PurchaseOption.appAccountToken(...)`. Apple incluye ese UUID en cada
// notificación (`signedTransactionInfo.appAccountToken`), permitiéndonos mapear el
// evento al `businesses/{uid}` correcto.
//
// Google → la app pasa `obfuscatedAccountId = firebase.auth.uid` en `BillingFlowParams`.
// Google lo devuelve como `obfuscatedExternalAccountId` al consultar el purchaseToken.
//
// Ambas plataformas requieren validación server-side antes de fiarnos del cliente.

// Apple: secret con la KEY (.p8) del App Store Server API.
// Generar en App Store Connect → Users and Access → Integrations → App Store Server API.
// Se sube con: `firebase functions:secrets:set APPLE_ASSAPI_PRIVATE_KEY`
// (pegar el contenido completo del .p8 incluyendo BEGIN/END).
const APPLE_ASSAPI_PRIVATE_KEY = defineSecret("APPLE_ASSAPI_PRIVATE_KEY");
// IDs públicos (no son secretos pero los dejamos como secrets para no hardcodear).
const APPLE_ASSAPI_KEY_ID = defineSecret("APPLE_ASSAPI_KEY_ID");
const APPLE_ASSAPI_ISSUER_ID = defineSecret("APPLE_ASSAPI_ISSUER_ID");
// Bundle ID del app iOS publicado (com.Sozolab.zampa).
const APPLE_BUNDLE_ID = "com.Sozolab.zampa";
// Package name del app Android (com.sozolab.zampa).
const ANDROID_PACKAGE_NAME = "com.sozolab.zampa";
// SKU del producto (debe coincidir en App Store Connect, Play Console y nuestro código).
const SUBSCRIPTION_PRODUCT_ID = "zampa_pro_monthly";

// Apple Root CA - G3 embebido en base64 (cert público, ~580 bytes binarios).
// Fuente original: https://www.apple.com/certificateauthority/AppleRootCA-G3.cer
// Si Apple lo rota (raro: válido hasta 2039), reemplazar este string.
const APPLE_ROOT_CA_G3_B64 = "MIICQzCCAcmgAwIBAgIILcX8iNLFS5UwCgYIKoZIzj0EAwMwZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMTQwNDMwMTgxOTA2WhcNMzkwNDMwMTgxOTA2WjBnMRswGQYDVQQDDBJBcHBsZSBSb290IENBIC0gRzMxJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABJjpLz1AcqTtkyJygRMc3RCV8cWjTnHcFBbZDuWmBSp3ZHtfTjjTuxxEtX/1H7YyYl3J6YRbTzBPEVoA/VhYDKX1DyxNB0cTddqXl5dvMVztK517IDvYuVTZXpmkOlEKMaNCMEAwHQYDVR0OBBYEFLuw3qFYM4iapIqZ3r6966/ayySrMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMDA2gAMGUCMQCD6cHEFl4aXTQY2e3v9GwOAEZLuN+yRhHFD/3meoyhpmvOwgPUnPWTxnS4at+qIxUCMG1mihDK1A3UT82NQz60imOlM27jbdoXt2QfyFMm+YhidDkLF1vLUagM6BgD56KyKA==";

/**
 * Expira menús automáticamente cada hora.
 *
 * Regla: una oferta se marca `isActive = false` cuando su `createdAt` es de un
 * día anterior al día actual en hora local Madrid, salvo que sea permanente
 * (`isPermanent == true`). Así "Menú del día" significa realmente "hoy" y el
 * rollover ocurre a las 00:00 Madrid sin importar el timezone del servidor.
 *
 * Nota: las ofertas del seed antiguas (sin `isPermanent`) también caerán aquí;
 * `isPermanent` ausente se trata como `false`.
 */
exports.expireMenus = onSchedule("every 1 hours", async (event) => {
    const db = admin.firestore();

    try {
        // YMD de hoy en zona horaria Europe/Madrid, formato "YYYY-MM-DD".
        // Usamos el locale "sv-SE" porque produce ISO date directamente.
        const todayMadrid = new Intl.DateTimeFormat("sv-SE", {
            timeZone: "Europe/Madrid",
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
        }).format(new Date());

        const activeSnap = await db.collection("dailyOffers")
            .where("isActive", "==", true)
            .get();

        if (activeSnap.empty) {
            logger.info("No hay ofertas activas.");
            return;
        }

        const toExpire = [];
        for (const doc of activeSnap.docs) {
            const data = doc.data();
            if (data.isPermanent === true) continue;
            const createdAt = data.createdAt;
            if (!createdAt) continue;
            const createdDate = new Date(createdAt);
            if (isNaN(createdDate.getTime())) continue;

            const createdYMDMadrid = new Intl.DateTimeFormat("sv-SE", {
                timeZone: "Europe/Madrid",
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
            }).format(createdDate);

            if (createdYMDMadrid < todayMadrid) {
                toExpire.push(doc);
            }
        }

        if (toExpire.length === 0) {
            logger.info(`Todas las ${activeSnap.size} ofertas activas son de hoy. Nada que expirar.`);
            return;
        }

        // Firestore batch limit = 500 operaciones; agrupamos de 400 en 400
        for (const chunk of chunkArray(toExpire, 400)) {
            const batch = db.batch();
            chunk.forEach((doc) => {
                batch.update(doc.ref, { isActive: false });
            });
            await batch.commit();
        }

        logger.info(`Expiradas ${toExpire.length} ofertas (de ${activeSnap.size} activas, día actual Madrid: ${todayMadrid}).`);
    } catch (error) {
        logger.error("Error expirando menús:", error);
    }
});

/**
 * Envía notificaciones push a los seguidores cuando un merchant Pro publica una oferta.
 * Solo se envían a usuarios que:
 * 1. Tienen el restaurante en favoritos con notificationsEnabled = true
 * 2. Tienen habilitada la preferencia newMenuFromFavorites (o no tienen preferencias configuradas = default true)
 */
exports.onMenuPublished = onDocumentCreated("dailyOffers/{menuId}", async (event) => {
    try {
        const menuData = event.data?.data();
        if (!menuData) {
            logger.warn("onMenuPublished: menuData vacío, saliendo.");
            return;
        }

        const merchantId = menuData.businessId;
        if (!merchantId) {
            logger.warn("onMenuPublished: businessId ausente en la oferta, saliendo.");
            return;
        }

        const db = admin.firestore();

        // Solo merchants Pro envían notificaciones push
        if (!menuData.isMerchantPro) {
            logger.info(`Merchant ${merchantId} no es Pro, no se envían notificaciones push.`);
            return;
        }

        // Rate limit: máx 5 ofertas con notificación en las últimas 6 horas por merchant
        const sixHoursAgo = new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString();
        const recentOffersSnap = await db.collection("dailyOffers")
            .where("businessId", "==", merchantId)
            .where("isMerchantPro", "==", true)
            .where("createdAt", ">", sixHoursAgo)
            .get();
        if (recentOffersSnap.size > 20) {
            logger.warn(`Rate limit: merchant ${merchantId} ha publicado ${recentOffersSnap.size} ofertas en las últimas 6h. Se omiten notificaciones push.`);
            return;
        }

        // Obtener nombre del merchant
        const merchantDoc = await db.collection("businesses").doc(merchantId).get();
        const merchantName = merchantDoc.exists && merchantDoc.data().name
            ? merchantDoc.data().name
            : "Un restaurante";

        // 1. Buscar seguidores del merchant con notificaciones habilitadas en el favorito
        const followersSnapshot = await db.collection("favorites")
            .where("businessId", "==", merchantId)
            .where("notificationsEnabled", "==", true)
            .get();

        if (followersSnapshot.empty) {
            logger.info(`Sin seguidores con notificaciones para merchant: ${merchantId}`);
            return;
        }

        const followerUserIds = [...new Set(
            followersSnapshot.docs
                .map(doc => doc.data().customerId)
                .filter(Boolean)
        )];

        if (followerUserIds.length === 0) {
            logger.info("No hay IDs de seguidores válidos.");
            return;
        }

        // 2. Filtrar usuarios que tengan deshabilitada la preferencia global newMenuFromFavorites
        //    (por defecto true si no existe el campo)
        const eligibleUserIds = [];
        for (const batch of chunkArray(followerUserIds, 10)) {
            const usersSnap = await db.collection("users")
                .where(admin.firestore.FieldPath.documentId(), "in", batch)
                .get();
            usersSnap.docs.forEach(doc => {
                const prefs = doc.data().notificationPreferences;
                if (!prefs || prefs.newMenuFromFavorites !== false) {
                    eligibleUserIds.push(doc.id);
                }
            });
        }

        if (eligibleUserIds.length === 0) {
            logger.info("Todos los seguidores tienen notificaciones deshabilitadas.");
            return;
        }

        const title = `${merchantName}`;
        const body = `Acaba de publicar: ${menuData.title || "nueva oferta"}`;
        const menuId = event.params.menuId;

        // Idempotencia: evitar envío duplicado si el trigger se dispara más de una vez.
        // Usa create() que falla atómicamente si el doc ya existe (evita race condition).
        const lockRef = db.collection("_pushLocks").doc(menuId);
        try {
            await lockRef.create({ createdAt: admin.firestore.FieldValue.serverTimestamp() });
        } catch (lockError) {
            if (lockError.code === 6) { // ALREADY_EXISTS
                logger.info(`Push ya enviada para oferta ${menuId}, saliendo (idempotencia).`);
                return;
            }
            throw lockError;
        }

        // 3. Crear notificaciones in-app (batch de máx 400 para no rozar el límite de 500)
        for (const chunk of chunkArray(eligibleUserIds, 400)) {
            const notifBatch = db.batch();
            for (const userId of chunk) {
                const notifRef = db.collection("notifications").doc();
                notifBatch.set(notifRef, {
                    id: notifRef.id,
                    userId: userId,
                    businessId: merchantId,
                    offerId: menuId,
                    type: "NEW_OFFER_FAVORITE",
                    title: title,
                    body: body,
                    read: false,
                    createdAt: admin.firestore.FieldValue.serverTimestamp(),
                });
            }
            await notifBatch.commit();
        }
        logger.info(`Creadas ${eligibleUserIds.length} notificaciones in-app.`);

        // 4. Obtener tokens FCM (batches de 30 por limitación de 'in')
        // Se deduplica por token para evitar enviar 2 push al mismo dispositivo
        // cuando el usuario cambia de cuenta en el mismo teléfono.
        const tokenSet = new Set();
        const tokenUserMap = {};
        for (const batch of chunkArray(eligibleUserIds, 30)) {
            const tokensSnap = await db.collection("deviceTokens")
                .where("userId", "in", batch)
                .get();
            tokensSnap.docs.forEach(doc => {
                const data = doc.data();
                if (data.token && !tokenSet.has(data.token)) {
                    tokenSet.add(data.token);
                    tokenUserMap[data.token] = data.userId;
                }
            });
        }
        const tokens = [...tokenSet];

        if (tokens.length === 0) {
            logger.info("No hay tokens FCM disponibles.");
            return;
        }

        // 5. Enviar push notifications (FCM admite máx 500 tokens por llamada)
        for (const tokenBatch of chunkArray(tokens, 500)) {
            try {
                const response = await admin.messaging().sendEachForMulticast({
                    tokens: tokenBatch,
                    notification: { title, body },
                    data: {
                        menuId: menuId,
                        businessId: merchantId,
                        type: "NEW_OFFER_FAVORITE",
                    },
                    apns: {
                        payload: {
                            aps: {
                                sound: "zampa_bell.caf",
                            },
                        },
                    },
                    android: {
                        notification: {
                            channelId: "menu_updates_v2",
                            sound: "zampa_bell",
                        },
                    },
                });
                logger.info(`Push enviadas: ${response.successCount} OK, ${response.failureCount} fallidas.`);

                // Limpiar tokens inválidos
                const invalidTokens = response.responses
                    .map((resp, idx) => (!resp.success && isInvalidTokenError(resp.error?.code)) ? tokenBatch[idx] : null)
                    .filter(Boolean);

                if (invalidTokens.length > 0) {
                    await removeInvalidTokens(db, invalidTokens);
                }
            } catch (pushError) {
                logger.error("Error enviando push batch:", pushError);
            }
        }
    } catch (error) {
        logger.error("Error en onMenuPublished:", error);
    }
});

/**
 * Cuando un nuevo comercio se registra (`businesses/{id}` se crea con `isVerified: false`),
 * añadimos una entrada en `pendingVerifications/{id}` para que el admin pueda revisarla
 * desde Firebase Console y decidir si flippear `isVerified: true` manualmente.
 *
 * Si el doc se crea ya verificado (legacy / seed), no hacemos nada.
 *
 * Idempotente: si el doc de `pendingVerifications` ya existe, no lo sobreescribe.
 */
exports.onBusinessCreated = onDocumentCreated("businesses/{merchantId}", async (event) => {
    try {
        const data = event.data?.data();
        if (!data) {
            logger.warn("onBusinessCreated: data vacío, saliendo.");
            return;
        }

        // Si ya viene verificado o sin el flag, no es un caso de moderación.
        if (data.isVerified !== false) {
            logger.info(`onBusinessCreated: ${event.params.merchantId} no requiere verificación (isVerified=${data.isVerified}).`);
            return;
        }

        const db = admin.firestore();
        const ref = db.collection("pendingVerifications").doc(event.params.merchantId);

        // Snapshot mínimo de datos para que el admin pueda decidir desde Firebase Console
        // sin tener que abrir el doc del businesses por separado.
        await ref.set({
            merchantId: event.params.merchantId,
            name: data.name || null,
            email: data.email || null,        // si lo guardáramos en el futuro
            phone: data.phone || null,
            taxId: data.taxId || null,
            addressText: data.addressText || null,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            status: "pending",
        }, { merge: true });

        logger.info(`onBusinessCreated: pendingVerifications/${event.params.merchantId} creado.`);
    } catch (error) {
        logger.error("Error en onBusinessCreated:", error);
    }
});

// ── Helpers ──

function chunkArray(arr, size) {
    const chunks = [];
    for (let i = 0; i < arr.length; i += size) {
        chunks.push(arr.slice(i, i + size));
    }
    return chunks;
}

function isInvalidTokenError(code) {
    return code === "messaging/invalid-registration-token" ||
           code === "messaging/registration-token-not-registered";
}

async function removeInvalidTokens(db, invalidTokens) {
    try {
        for (const chunk of chunkArray(invalidTokens, 30)) {
            const invalidSnap = await db.collection("deviceTokens")
                .where("token", "in", chunk)
                .get();
            if (invalidSnap.empty) continue;
            const cleanBatch = db.batch();
            invalidSnap.docs.forEach(doc => cleanBatch.delete(doc.ref));
            await cleanBatch.commit();
            logger.info(`Eliminados ${invalidSnap.size} tokens inválidos.`);
        }
    } catch (error) {
        logger.error("Error limpiando tokens inválidos:", error);
    }
}

/**
 * Purga cuentas de usuario que han pasado el periodo de gracia de 30 días.
 * Scheduled: diario a las 03:00 Europe/Madrid. Timeout máximo (9 min) para
 * tolerar backlogs pequeños. Lote máx 50 usuarios por ejecución.
 *
 * Para cada usuario con scheduledPurgeAt <= now():
 *   1. Borra favorites (customerId == uid)
 *   2. Borra userHistory (userId == uid)
 *   3. Borra notifications (userId == uid)
 *   4. Borra deviceTokens (userId == uid)
 *   5. Borra customers/{uid}
 *   6. Si es merchant: borra dailyOffers (businessId == uid),
 *      metrics/{uid} (incluyendo subcollection daily/), pendingVerifications/{uid},
 *      subscriptions (businessId == uid), y businesses/{uid}.
 *   7. Borra Storage users/{uid}/ y dailyOffers/{uid}-* (fotos de oferta).
 *   8. Borra users/{uid}
 *   9. Borra el Auth user
 *  10. Escribe purgedAccounts/{uid} con el resultado (auditoría)
 *
 * Pasos best-effort: un fallo en un paso NO aborta el resto, se registra en
 * `errors` y el usuario queda marcado como parcialmente procesado. Si el paso
 * 7 falla, la query del siguiente día lo volverá a seleccionar y reintentará.
 */
exports.purgeDeletedAccounts = onSchedule(
    {
        schedule: "every day 03:00",
        timeZone: "Europe/Madrid",
        timeoutSeconds: 540,
    },
    async (event) => {
        const db = admin.firestore();
        const now = admin.firestore.Timestamp.now();

        const pendingSnap = await db.collection("users")
            .where("scheduledPurgeAt", "<=", now)
            .limit(50)
            .get();

        if (pendingSnap.empty) {
            logger.info("purgeDeletedAccounts: no hay cuentas pendientes de purga.");
            return;
        }

        logger.info(`purgeDeletedAccounts: procesando ${pendingSnap.size} cuentas.`);

        for (const userDoc of pendingSnap.docs) {
            const uid = userDoc.id;
            const originalDeletedAt = userDoc.data().deletedAt || null;
            const stepsCompleted = [];
            const errors = [];

            async function runStep(name, fn) {
                try {
                    await fn();
                    stepsCompleted.push(name);
                } catch (e) {
                    errors.push({ step: name, message: e.message || String(e) });
                    logger.error(`purgeDeletedAccounts[${uid}] paso '${name}' falló:`, e);
                }
            }

            await runStep("favorites", () => deleteQueryInBatches(
                db.collection("favorites").where("customerId", "==", uid)
            ));
            await runStep("userHistory", () => deleteQueryInBatches(
                db.collection("userHistory").where("userId", "==", uid)
            ));
            await runStep("notifications", () => deleteQueryInBatches(
                db.collection("notifications").where("userId", "==", uid)
            ));
            await runStep("deviceTokens", () => deleteQueryInBatches(
                db.collection("deviceTokens").where("userId", "==", uid)
            ));
            await runStep("customers", () =>
                db.collection("customers").doc(uid).delete()
            );

            // Datos de comercio: solo procesamos si existe el doc en businesses/.
            const bizDoc = await db.collection("businesses").doc(uid).get();
            if (bizDoc.exists) {
                await runStep("dailyOffers", () => deleteQueryInBatches(
                    db.collection("dailyOffers").where("businessId", "==", uid)
                ));
                await runStep("metricsDaily", async () => {
                    const dailySnap = await db.collection("metrics").doc(uid)
                        .collection("daily").get();
                    if (dailySnap.empty) return;
                    for (const chunk of chunkArray(dailySnap.docs, 400)) {
                        const batch = db.batch();
                        chunk.forEach(d => batch.delete(d.ref));
                        await batch.commit();
                    }
                });
                await runStep("metricsRoot", () =>
                    db.collection("metrics").doc(uid).delete()
                );
                await runStep("pendingVerifications", () =>
                    db.collection("pendingVerifications").doc(uid).delete()
                );
                await runStep("subscriptions", () => deleteQueryInBatches(
                    db.collection("subscriptions").where("businessId", "==", uid)
                ));
                await runStep("businesses", () =>
                    db.collection("businesses").doc(uid).delete()
                );
            }
            await runStep("storage", async () => {
                const bucket = admin.storage().bucket();
                const [files] = await bucket.getFiles({ prefix: `users/${uid}/` });
                for (const file of files) {
                    await file.delete();
                }
            });
            await runStep("users", () =>
                db.collection("users").doc(uid).delete()
            );

            // Auth deletion has a special-cased "already missing" state
            try {
                await admin.auth().deleteUser(uid);
                stepsCompleted.push("auth");
            } catch (e) {
                if (e.code === "auth/user-not-found") {
                    stepsCompleted.push("auth_already_missing");
                } else {
                    errors.push({ step: "auth", message: e.message || String(e) });
                    logger.error(`purgeDeletedAccounts[${uid}] paso 'auth' falló:`, e);
                }
            }

            // Audit log (fuera del try/catch principal para que se registre siempre)
            try {
                await db.collection("purgedAccounts").doc(uid).set({
                    purgedAt: admin.firestore.FieldValue.serverTimestamp(),
                    deletedAt: originalDeletedAt,
                    stepsCompleted,
                    errors,
                });
            } catch (e) {
                logger.error(`purgeDeletedAccounts[${uid}] no se pudo escribir auditoría:`, e);
            }

            if (errors.length > 0) {
                logger.warn(`purgeDeletedAccounts[${uid}] completado con errores.`);
            } else {
                logger.info(`purgeDeletedAccounts[${uid}] purgado completamente.`);
            }
        }
    }
);

/** Borra todos los documentos que coincidan con una query en lotes de 400. */
async function deleteQueryInBatches(query) {
    const db = admin.firestore();
    while (true) {
        const snap = await query.limit(400).get();
        if (snap.empty) return;
        const batch = db.batch();
        snap.docs.forEach(doc => batch.delete(doc.ref));
        await batch.commit();
        if (snap.size < 400) return;
    }
}

/**
 * Obtiene tasas de cambio diarias (EUR → 9 monedas soportadas) desde
 * frankfurter.app (API pública del BCE, sin key) y las escribe en
 * config/exchangeRates. Si la request falla, NO tocamos el doc previo —
 * las tasas de ayer siguen siendo perfectamente usables para una
 * conversión orientativa.
 *
 * Scheduled: diario a las 05:00 Europe/Madrid (tras el cierre europeo).
 */
exports.refreshExchangeRates = onSchedule(
    {
        schedule: "every day 05:00",
        timeZone: "Europe/Madrid",
        timeoutSeconds: 60,
    },
    async (event) => {
        const SUPPORTED = ["USD", "GBP", "JPY", "CHF", "SEK", "NOK", "DKK", "CAD", "AUD"];
        const url = `https://api.frankfurter.app/latest?from=EUR&to=${SUPPORTED.join(",")}`;

        let payload;
        try {
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000);
            try {
                const resp = await fetch(url, { signal: controller.signal });
                if (!resp.ok) {
                    logger.error(`refreshExchangeRates: HTTP ${resp.status} ${resp.statusText}`);
                    return;
                }
                payload = await resp.json();
            } finally {
                clearTimeout(timeoutId);
            }
        } catch (e) {
            logger.error("refreshExchangeRates: fetch falló, se mantiene el doc previo.", e);
            return;
        }

        const rates = payload?.rates;
        if (!rates || typeof rates !== "object") {
            logger.error("refreshExchangeRates: respuesta sin objeto 'rates'.", payload);
            return;
        }

        // Sanity-check: los 9 códigos deben venir como números finitos positivos.
        const clean = {};
        for (const code of SUPPORTED) {
            const value = rates[code];
            if (typeof value !== "number" || !isFinite(value) || value <= 0) {
                logger.error(`refreshExchangeRates: tasa inválida para ${code}: ${value}`);
                return;
            }
            clean[code] = value;
        }

        try {
            await admin.firestore().collection("config").doc("exchangeRates").set({
                base: "EUR",
                rates: clean,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            logger.info("refreshExchangeRates OK:", clean);
        } catch (e) {
            logger.error("refreshExchangeRates: escritura a Firestore falló.", e);
            throw e;
        }
    }
);

// ── Helper: resolver appAccountToken (UUID) → merchantId ──────────────────────
//
// Firebase UIDs no son UUIDs, pero Apple requiere UUID en `appAccountToken`.
// Solución: las apps generan un UUID y lo guardan en `businesses/{uid}.appAccountToken`
// antes de la primera compra. El webhook hace lookup por ese campo para mapear el
// evento al merchant correcto.
async function resolveMerchantId(appAccountToken) {
    if (!appAccountToken) return null;
    const db = admin.firestore();
    const q = await db.collection("businesses")
        .where("appAccountToken", "==", appAccountToken)
        .limit(1)
        .get();
    return q.empty ? null : q.docs[0].id;
}

// ── Helper: actualizar suscripción + auditar evento ───────────────────────────
//
// Idempotente: si `eventId` ya existe en `subscriptions`, salimos sin tocar nada.
// Devuelve `true` si se procesó, `false` si era duplicado.
async function applySubscriptionUpdate({
    eventId, businessId, platform, type, expirationMs, productId, rawEvent, newStatus,
}) {
    const db = admin.firestore();
    const eventRef = db.collection("subscriptions").doc(eventId);

    try {
        await eventRef.create({
            eventId,
            businessId: businessId || null,
            platform,        // 'apple' | 'google'
            type,
            receivedAt: admin.firestore.FieldValue.serverTimestamp(),
            expirationMs: expirationMs || null,
            productId: productId || null,
            rawEvent,
        });
    } catch (e) {
        if (e.code === 6) { // ALREADY_EXISTS
            logger.info(`Evento ${eventId} ya procesado (idempotencia).`);
            return false;
        }
        throw e;
    }

    if (!businessId || !newStatus) return true;

    await db.collection("businesses").doc(businessId).set({
        subscriptionStatus: newStatus,
        subscriptionActiveUntil: expirationMs || 0,
    }, { merge: true });
    logger.info(`businesses/${businessId} → ${newStatus} hasta ${expirationMs}`);
    return true;
}

// ── Apple App Store Server Notifications v2 ───────────────────────────────────
//
// Apple envía un único body `{ signedPayload: <JWS> }` al endpoint configurado en
// App Store Connect → App Information → App Store Server Notifications. La firma
// se verifica con la cadena de certificados de Apple (Apple Root CA - G3).
//
// Configuración en App Store Connect:
//   - Production Server URL: la URL devuelta por `firebase deploy --only functions:appStoreNotifications`
//   - Sandbox Server URL: la misma (el handler ya distingue por `data.environment`).
//   - Version 2 Notifications.
//
// La app debe pasar `Product.PurchaseOption.appAccountToken(UUID(uuidString: uid))`
// al iniciar la compra, donde `uid` = firebase.auth.uid del merchant.
exports.appStoreNotifications = onRequest(
    {
        secrets: [APPLE_ASSAPI_PRIVATE_KEY, APPLE_ASSAPI_KEY_ID, APPLE_ASSAPI_ISSUER_ID],
        region: "us-central1",
    },
    async (req, res) => {
        if (req.method !== "POST") {
            res.status(405).send("Method Not Allowed");
            return;
        }

        const signedPayload = req.body?.signedPayload;
        if (!signedPayload) {
            logger.warn("appStoreNotifications: body sin signedPayload.");
            res.status(400).send("Bad Request");
            return;
        }

        try {
            // Apple SDK oficial: verifica la cadena de certificados y decodifica el JWS.
            const {
                SignedDataVerifier, Environment,
            } = require("@apple/app-store-server-library");

            // Apple Root CA - G3 embebida en base64 (ver constante arriba).
            const rootCert = Buffer.from(APPLE_ROOT_CA_G3_B64, "base64");

            const verifier = new SignedDataVerifier(
                [rootCert],
                /* enableOnlineChecks */ true,
                Environment.PRODUCTION,  // el SDK detecta sandbox automáticamente
                APPLE_BUNDLE_ID
            );

            const notification = await verifier.verifyAndDecodeNotification(signedPayload);
            const data = notification.data || {};
            const env = data.environment;        // "Sandbox" | "Production"
            const notificationType = notification.notificationType;
            const subtype = notification.subtype;

            // notificationUUID es único por evento → usamos como event id.
            const eventId = `apple_${notification.notificationUUID}`;

            // Decodificar transactionInfo y renewalInfo.
            const txInfo = data.signedTransactionInfo
                ? await verifier.verifyAndDecodeTransaction(data.signedTransactionInfo)
                : null;

            // appAccountToken es un UUID generado por la app y guardado en
            // businesses/{uid}.appAccountToken. Lookup inverso por ese campo.
            const businessId = await resolveMerchantId(txInfo?.appAccountToken);
            const expirationMs = txInfo?.expiresDate || null;
            const productId = txInfo?.productId || null;

            // Mapeo de notificationType + subtype → estado.
            // https://developer.apple.com/documentation/appstoreservernotifications/notificationtype
            let newStatus = null;
            switch (notificationType) {
                case "SUBSCRIBED":
                case "DID_RENEW":
                case "DID_CHANGE_RENEWAL_STATUS":
                case "OFFER_REDEEMED":
                    newStatus = "active";
                    break;
                case "EXPIRED":
                case "REVOKE":
                case "GRACE_PERIOD_EXPIRED":
                    newStatus = "expired";
                    break;
                case "DID_FAIL_TO_RENEW":
                    // En grace period la suscripción sigue siendo válida hasta `expiresDate`.
                    // Si subtype === "GRACE_PERIOD" → mantenemos active.
                    // Si no hay grace period configurado → expira ahora.
                    newStatus = subtype === "GRACE_PERIOD" ? "active" : "expired";
                    break;
                case "REFUND":
                case "REFUND_DECLINED":
                case "CONSUMPTION_REQUEST":
                case "PRICE_INCREASE":
                case "RENEWAL_EXTENDED":
                case "TEST":
                    // No afectan al status del merchant (o son informativos).
                    break;
                default:
                    logger.info(`appStoreNotifications: type '${notificationType}' no manejado.`);
            }

            await applySubscriptionUpdate({
                eventId,
                businessId,
                platform: `apple_${env?.toLowerCase() || "unknown"}`,
                type: subtype ? `${notificationType}.${subtype}` : notificationType,
                expirationMs,
                productId,
                rawEvent: { notification, transactionInfo: txInfo },
                newStatus,
            });

            res.status(200).send({ ok: true });
        } catch (err) {
            logger.error("appStoreNotifications: fallo verificando/aplicando.", err);
            // 500 → Apple reintenta hasta 5 veces. Si el fallo es persistente y queremos
            // dejar de recibir reintentos para un evento concreto, lo procesamos en logs
            // manualmente. 200 con error interno también es válido.
            res.status(500).send("Internal Error");
        }
    }
);

// ── Google Play Real-time Developer Notifications ─────────────────────────────
//
// Google envía mensajes a un Pub/Sub topic. Cada mensaje contiene
// `subscriptionNotification: { purchaseToken, subscriptionId, notificationType }`
// pero NO incluye expiry ni `obfuscatedExternalAccountId` (= firebase uid).
// Hay que llamar a `androidpublisher.purchases.subscriptionsv2.get` para obtener
// el estado real.
//
// Configuración (una sola vez):
//   1. En GCP Console → Pub/Sub, crear topic `play-rtdn` en el proyecto eatout-70b8b.
//   2. En Play Console → Monetize setup → Real-time developer notifications,
//      pegar el topic name `projects/eatout-70b8b/topics/play-rtdn`.
//   3. Linkear la cuenta de servicio de Play (mostrada por Play Console) con permiso
//      `Pub/Sub Publisher` sobre el topic.
//   4. Habilitar Google Play Android Developer API en GCP Console.
//   5. En Play Console → Users and permissions → API access → vincular la cuenta de
//      servicio que usa Cloud Functions (por defecto la del proyecto, ej.
//      `eatout-70b8b@appspot.gserviceaccount.com`) con permiso "View financial data".
//
// La app debe pasar `BillingFlowParams.setObfuscatedAccountId(firebase.auth.uid)`
// al lanzar la compra para que `purchases.subscriptionsv2.get` devuelva ese campo.
exports.playRTDN = onMessagePublished(
    { topic: "play-rtdn", region: "us-central1" },
    async (event) => {
        try {
            const data = event.data?.message?.data;
            if (!data) {
                logger.warn("playRTDN: mensaje sin data.");
                return;
            }

            // Pub/Sub manda data en base64.
            const decoded = JSON.parse(Buffer.from(data, "base64").toString("utf8"));
            const { subscriptionNotification, testNotification, packageName } = decoded;

            // Test events del Play Console:
            if (testNotification) {
                logger.info("playRTDN: testNotification recibido OK.", testNotification);
                return;
            }

            // Sólo procesamos suscripciones (no compras one-time ni voided purchases).
            if (!subscriptionNotification) {
                logger.info("playRTDN: notificación no-subscription, ignorada.");
                return;
            }

            if (packageName !== ANDROID_PACKAGE_NAME) {
                logger.warn(`playRTDN: packageName desconocido '${packageName}', ignorando.`);
                return;
            }

            const {
                purchaseToken, subscriptionId, notificationType,
            } = subscriptionNotification;

            // notificationType (https://developer.android.com/google/play/billing/rtdn-reference#sub):
            //   1=RECOVERED  2=RENEWED  3=CANCELED  4=PURCHASED  5=ON_HOLD
            //   6=IN_GRACE_PERIOD  7=RESTARTED  8=PRICE_CHANGE_CONFIRMED  9=DEFERRED
            //   10=PAUSED  11=PAUSE_SCHEDULE_CHANGED  12=REVOKED  13=EXPIRED
            //   20=PENDING_PURCHASE_CANCELED
            const eventId = `google_${purchaseToken}_${notificationType}_${decoded.eventTimeMillis}`;

            // Estrategia híbrida:
            // (A) Intentamos validar contra androidpublisher para tener datos ricos
            //     (expiryTime, subscriptionState, obfuscatedAccountId). Requiere que
            //     la SA tenga permisos en Play Console → API access.
            // (B) Si (A) falla por permisos (o no está configurada API access aún),
            //     fallback: derivamos estado del notificationType + lookup del
            //     `playPurchases/{purchaseToken}` que la app registra tras la compra.
            let businessId = null;
            let expirationMs = null;
            let subState = "";
            let apiData = null;

            try {
                const { google } = require("googleapis");
                const auth = new google.auth.GoogleAuth({
                    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
                });
                const androidpublisher = google.androidpublisher({ version: "v3", auth });
                const subResp = await androidpublisher.purchases.subscriptionsv2.get({
                    packageName,
                    token: purchaseToken,
                });
                apiData = subResp.data || {};
                const expiryTimeIso = apiData.lineItems?.[0]?.expiryTime;
                expirationMs = expiryTimeIso ? Date.parse(expiryTimeIso) : null;
                businessId = await resolveMerchantId(
                    apiData.externalAccountIdentifiers?.obfuscatedExternalAccountId
                );
                subState = apiData.subscriptionState || "";
            } catch (apiErr) {
                logger.warn(
                    `playRTDN: androidpublisher API no disponible (${apiErr.code || apiErr.message}). ` +
                    `Usando fallback con purchaseToken lookup.`,
                );
                // Fallback (B): mapear merchant via doc registrado por la app.
                const db = admin.firestore();
                const lookupDoc = await db.collection("playPurchases").doc(purchaseToken).get();
                if (lookupDoc.exists) {
                    businessId = lookupDoc.data().businessId || null;
                }
            }

            // Si tenemos subState (de la API), úsalo. Si no, derivamos del notificationType.
            let newStatus = null;
            if (subState) {
                switch (subState) {
                    case "SUBSCRIPTION_STATE_ACTIVE":
                    case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD":
                    case "SUBSCRIPTION_STATE_CANCELED":
                        newStatus = "active";
                        break;
                    case "SUBSCRIPTION_STATE_ON_HOLD":
                    case "SUBSCRIPTION_STATE_PAUSED":
                    case "SUBSCRIPTION_STATE_EXPIRED":
                        newStatus = "expired";
                        break;
                    case "SUBSCRIPTION_STATE_PENDING":
                        break;  // sin cambio
                    default:
                        logger.info(`playRTDN: subscriptionState desconocido '${subState}'.`);
                }
            } else {
                // Fallback: derivar de notificationType (RTDN reference).
                switch (Number(notificationType)) {
                    case 1: case 2: case 4: case 7:  // RECOVERED, RENEWED, PURCHASED, RESTARTED
                    case 6:  // IN_GRACE_PERIOD
                    case 3:  // CANCELED → sigue activo hasta expiry
                        newStatus = "active";
                        break;
                    case 5:  // ON_HOLD
                    case 10: // PAUSED
                    case 12: // REVOKED
                    case 13: // EXPIRED
                        newStatus = "expired";
                        break;
                    case 8: case 9: case 11: case 20:  // PRICE_CHANGE / DEFERRED / PAUSE_SCHEDULE / PENDING_PURCHASE_CANCELED
                    default:
                        // Sin cambio de estado.
                        break;
                }
            }

            await applySubscriptionUpdate({
                eventId,
                businessId,
                platform: apiData ? "google" : "google_fallback",
                type: `RTDN_${notificationType}_${subState || "noApi"}`,
                expirationMs,
                productId: subscriptionId,
                rawEvent: { notification: decoded, subscription: apiData },
                newStatus,
            });
        } catch (err) {
            logger.error("playRTDN: fallo procesando notificación.", err);
            throw err;  // Pub/Sub reintenta automáticamente.
        }
    }
);

// Nota: NO hay callable function para registrar purchaseToken — el cliente
// Android escribe directamente a `playPurchases/{token}` vía Firestore (las rules
// permiten que cada usuario sólo escriba con su propio uid como businessId).
// Más simple que un callable y misma seguridad efectiva.
