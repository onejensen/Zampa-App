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
        if (recentOffersSnap.size > 5) {
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

        const followerUserIds = followersSnapshot.docs
            .map(doc => doc.data().customerId)
            .filter(Boolean); // eliminar nulls

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
        const tokens = [];
        const tokenUserMap = {};
        for (const batch of chunkArray(eligibleUserIds, 30)) {
            const tokensSnap = await db.collection("deviceTokens")
                .where("userId", "in", batch)
                .get();
            tokensSnap.docs.forEach(doc => {
                const data = doc.data();
                if (data.token) {
                    tokens.push(data.token);
                    tokenUserMap[data.token] = data.userId;
                }
            });
        }

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
