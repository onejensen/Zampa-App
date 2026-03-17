#!/usr/bin/env node

/**
 * Clears all Firestore collections and optionally Firebase Auth users.
 * 
 * Usage:
 *   node scripts/clear-firestore.js              # Clear Firestore only
 *   node scripts/clear-firestore.js --auth       # Clear Firestore + Auth users
 */

const { initializeApp, cert, applicationDefault } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');

const serviceAccountKeyPath = require('path').resolve(__dirname, '../EatOut-Backend/serviceAccountKey.json');
const serviceAccount = require(serviceAccountKeyPath);

// Initialize with Application Default Credentials or service account
initializeApp({
    credential: cert(serviceAccount)
});

const db = getFirestore();
const clearAuth = process.argv.includes('--auth');

const COLLECTIONS = [
    // Recientes (Fases 1 y 2)
    'users',
    'businesses',
    'dailyOffers',
    'favorites',
    'metrics',
    'deviceTokens',
    'reports',
    'customers',
    'subscriptions',
    'notifications',
    // Antiguas (Legacy)
    'merchants',
    'menus',
    'stats'
    // 'cuisineTypes'  <-- No se borra para preservar el catálogo
];

async function deleteCollection(collectionPath) {
    const collectionRef = db.collection(collectionPath);
    const snapshot = await collectionRef.get();

    if (snapshot.empty) {
        console.log(`  ⏭️  ${collectionPath}: vacía`);
        return 0;
    }

    const batch = db.batch();
    let count = 0;

    for (const doc of snapshot.docs) {
        // Check for subcollections (e.g., stats/{id}/daily)
        const subcollections = await doc.ref.listCollections();
        for (const subcol of subcollections) {
            const subDocs = await subcol.get();
            for (const subDoc of subDocs.docs) {
                batch.delete(subDoc.ref);
                count++;
            }
        }
        batch.delete(doc.ref);
        count++;
    }

    await batch.commit();
    console.log(`  🗑️  ${collectionPath}: ${count} documentos eliminados`);
    return count;
}

async function deleteAllAuthUsers() {
    const auth = getAuth();
    const listResult = await auth.listUsers(1000);

    if (listResult.users.length === 0) {
        console.log('  ⏭️  Auth: sin usuarios');
        return;
    }

    const uids = listResult.users.map(u => u.uid);
    await auth.deleteUsers(uids);
    console.log(`  🗑️  Auth: ${uids.length} usuarios eliminados`);
}

async function main() {
    console.log('\n🔥 Limpiando base de datos EatOut (eatout-70b8b)\n');

    let total = 0;
    for (const collection of COLLECTIONS) {
        total += await deleteCollection(collection);
    }

    if (clearAuth) {
        console.log('');
        await deleteAllAuthUsers();
    }

    console.log(`\n✅ Listo! ${total} documentos eliminados.`);
    if (!clearAuth) {
        console.log('   (Para borrar también usuarios Auth, usa: node scripts/clear-firestore.js --auth)\n');
    }

    process.exit(0);
}

main().catch(err => {
    console.error('❌ Error:', err.message);
    process.exit(1);
});
