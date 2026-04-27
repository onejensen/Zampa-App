const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

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
 *   6. Borra Storage users/{uid}/
 *   7. Borra users/{uid}
 *   8. Borra el Auth user
 *   9. Escribe purgedAccounts/{uid} con el resultado (auditoría)
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
