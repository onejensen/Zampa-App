const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Expira menús automáticamente cada hora.
 * Busca menús que:
 * 1. Estén activos (isActive: true)
 * 2. Hayan pasado su fecha de expiración (expiresAt < ahora)
 */
exports.expireMenus = onSchedule("every 1 hours", async (event) => {
    const nowISO = new Date().toISOString();
    const db = admin.firestore();

    const expiredMenusQuery = await db.collection("menus")
        .where("isActive", "==", true)
        .where("expiresAt", "<", nowISO)
        .get();

    if (expiredMenusQuery.empty) {
        logger.info("No hay menús para expirar en este ciclo.");
        return;
    }

    const batch = db.batch();
    expiredMenusQuery.docs.forEach((doc) => {
        batch.update(doc.ref, { isActive: false });
    });

    await batch.commit();
    logger.info(`Se han expirado ${expiredMenusQuery.size} menús.`);
});

/**
 * Envía notificaciones push a los seguidores cuando se publica un menú.
 */
exports.onMenuPublished = onDocumentCreated("menus/{menuId}", async (event) => {
    const menuData = event.data.data();
    if (!menuData) return;

    const merchantId = menuData.merchantId;
    const db = admin.firestore();

    // 1. Buscar seguidores del merchant
    const followersSnapshot = await db.collection("favorites")
        .where("merchantId", "==", merchantId)
        .where("notificationsEnabled", "==", true)
        .get();

    if (followersSnapshot.empty) {
        logger.info(`Sin seguidores con notificaciones para merchant: ${merchantId}`);
        return;
    }

    const userIds = followersSnapshot.docs.map(doc => doc.data().userId);

    // 2. Obtener tokens de FCM para estos usuarios
    // Nota: Por simplicidad, buscamos en deviceTokens. En una app grande se haría en batches.
    const tokensSnapshot = await db.collection("deviceTokens")
        .where("userId", "in", userIds)
        .get();

    const tokens = tokensSnapshot.docs.map(doc => doc.data().token);
    if (tokens.length === 0) return;

    // 3. Enviar notificaciones
    const payload = {
        notification: {
            title: "¡Nuevo menú publicado!",
            body: `${menuData.title} ya está disponible.`,
        },
        data: {
            menuId: event.params.menuId,
            type: "new_menu"
        }
    };

    try {
        const response = await admin.messaging().sendEachForMulticast({
            tokens: tokens,
            notification: payload.notification,
            data: payload.data
        });
        logger.info(`Notificaciones enviadas: ${response.successCount}, Fallidas: ${response.failureCount}`);
    } catch (error) {
        logger.error("Error enviando notificaciones:", error);
    }
});
