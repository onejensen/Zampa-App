package com.sozolab.zampa.data

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.sozolab.zampa.data.model.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseService @Inject constructor() {

    private val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    val isAuthenticated: Boolean get() = auth.currentUser != null
    val currentUid: String? get() = auth.currentUser?.uid

    // ── Auth ──

    suspend fun register(email: String, password: String, name: String, role: User.UserRole, phone: String? = null): User {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user!!.uid

        result.user!!.updateProfile(userProfileChangeRequest { displayName = name }).await()

        val userData = mutableMapOf<String, Any>(
            "id" to uid, "email" to email, "name" to name,
            "role" to role.toFirestore(), "createdAt" to FieldValue.serverTimestamp()
        )
        phone?.takeIf { it.isNotBlank() }?.let { userData["phone"] = it }

        val batch = db.batch()
        batch.set(db.collection("users").document(uid), userData)

        if (role == User.UserRole.COMERCIO) {
            val trialEndMs = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000
            batch.set(db.collection("businesses").document(uid), mapOf(
                "id" to uid, "userId" to uid, "name" to name,
                "acceptsReservations" to false, "planTier" to "free",
                "isHighlighted" to false,
                "isVerified" to false,
                "subscriptionStatus" to com.sozolab.zampa.data.model.SubscriptionStatus.TRIAL,
                "trialEndsAt" to trialEndMs,
                "createdAt" to FieldValue.serverTimestamp()
            ))
        }

        if (role == User.UserRole.CLIENTE) {
            batch.set(db.collection("customers").document(uid), mapOf(
                "id" to uid, "userId" to uid, "displayName" to name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }

        batch.commit().await()
        return User(id = uid, email = email, name = name, role = role, phone = phone)
    }

    suspend fun login(email: String, password: String): User {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user!!.uid
        return getUserProfile(uid) ?: User(id = uid, email = email, name = result.user?.displayName ?: "")
    }

    fun logout() { auth.signOut() }

    /** Signs in with a social credential (Google). Returns the user and whether it's a new account. */
    suspend fun signInWithCredential(credential: AuthCredential): Pair<User, Boolean> {
        val result = auth.signInWithCredential(credential).await()
        val fbUser = result.user ?: throw Exception("No se pudo obtener el usuario")
        val uid = fbUser.uid
        val isNewUser = result.additionalUserInfo?.isNewUser == true

        val doc = db.collection("users").document(uid).get().await()
        return if (doc.exists()) {
            val user = getUserProfile(uid) ?: User(id = uid, email = fbUser.email ?: "", name = fbUser.displayName ?: "")
            Pair(user, false)
        } else {
            // New user — create base document with provisional role
            val userName = fbUser.displayName ?: "Usuario"
            val userEmail = fbUser.email ?: ""
            db.collection("users").document(uid).set(mapOf(
                "id" to uid, "email" to userEmail, "name" to userName,
                "role" to User.UserRole.CLIENTE.toFirestore(),
                "createdAt" to FieldValue.serverTimestamp()
            )).await()
            Pair(User(id = uid, email = userEmail, name = userName, role = User.UserRole.CLIENTE), true)
        }
    }

    /** Finalizes social registration: updates role and creates the corresponding profile document.
     *  Uses a batch write so both documents are created atomically — no partial state possible. */
    suspend fun finalizeSocialRegistration(uid: String, role: User.UserRole, name: String, email: String): User {
        val batch = db.batch()
        batch.update(db.collection("users").document(uid), mapOf("role" to role.toFirestore(), "name" to name))

        if (role == User.UserRole.COMERCIO) {
            val biz = db.collection("businesses").document(uid).get().await()
            if (!biz.exists()) {
                val trialEndMs = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000
                batch.set(db.collection("businesses").document(uid), mapOf(
                    "id" to uid, "userId" to uid, "name" to name,
                    "acceptsReservations" to false, "planTier" to "free",
                    "isHighlighted" to false,
                    "isVerified" to false,
                    "subscriptionStatus" to com.sozolab.zampa.data.model.SubscriptionStatus.TRIAL,
                    "trialEndsAt" to trialEndMs,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            }
        } else {
            val cust = db.collection("customers").document(uid).get().await()
            if (!cust.exists()) {
                batch.set(db.collection("customers").document(uid), mapOf(
                    "id" to uid, "userId" to uid, "displayName" to name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
        }

        batch.commit().await()
        return getUserProfile(uid) ?: User(id = uid, email = email, name = name, role = role)
    }

    suspend fun getUserProfile(uid: String): User? {
        val doc = db.collection("users").document(uid).get().await()
        val data = doc.data ?: return null
        return User(
            id = uid,
            email = data["email"] as? String ?: "",
            name = data["name"] as? String ?: "",
            role = User.UserRole.fromString(data["role"] as? String ?: "CLIENTE"),
            phone = data["phone"] as? String,
            photoUrl = data["photoUrl"] as? String,
            deletedAt = data["deletedAt"] as? com.google.firebase.Timestamp,
            scheduledPurgeAt = data["scheduledPurgeAt"] as? com.google.firebase.Timestamp,
            currencyPreference = data["currencyPreference"] as? String ?: "EUR",
        )
    }

    suspend fun uploadProfilePhoto(data: ByteArray): String {
        val uid = currentUid ?: throw Exception("No autenticado")
        val timestamp = System.currentTimeMillis()
        val url = uploadImage(data, "users/$uid/profile_$timestamp.jpg")
        db.collection("users").document(uid).update("photoUrl", url).await()
        return url
    }

    // ── Daily Offers ──

    data class MenuPage(val menus: List<Menu>, val lastDoc: com.google.firebase.firestore.DocumentSnapshot?)

    suspend fun getActiveMenus(
        limit: Int = 20,
        lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null,
        cuisineFilter: String? = null,
        maxPrice: Double? = null
    ): MenuPage {
        var query: com.google.firebase.firestore.Query = db.collection("dailyOffers")
            .whereEqualTo("isActive", true)
        
        cuisineFilter?.let {
            query = query.whereArrayContains("tags", it)
        }
        
        maxPrice?.let {
            query = query.whereLessThanOrEqualTo("priceTotal", it)
        }
        
        query = query.orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit.toLong())
        
        lastDocument?.let {
            query = query.startAfter(it)
        }

        val snapshot = query.get().await()
        val menus = snapshot.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            Menu(
                id = doc.id,
                businessId = d["businessId"] as? String ?: "",
                date = d["date"] as? String ?: "",
                title = d["title"] as? String ?: return@mapNotNull null,
                description = d["description"] as? String,
                priceTotal = (d["priceTotal"] as? Number)?.toDouble() ?: return@mapNotNull null,
                currency = d["currency"] as? String ?: "EUR",
                photoUrls = (d["photoUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                tags = (d["tags"] as? List<*>)?.filterIsInstance<String>(),
                createdAt = (d["createdAt"] as? com.google.firebase.Timestamp)?.let {
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(it.toDate())
                } ?: (d["createdAt"] as? String ?: ""),
                updatedAt = (d["updatedAt"] as? com.google.firebase.Timestamp)?.let {
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(it.toDate())
                } ?: (d["updatedAt"] as? String ?: ""),
                isActive = d["isActive"] as? Boolean ?: true,
                isMerchantPro = d["isMerchantPro"] as? Boolean ?: false,
                isMerchantVerified = d["isMerchantVerified"] as? Boolean,
                dietaryInfo = (d["dietaryInfo"] as? Map<*, *>)
                    ?.let { DietaryInfo.from(it.mapKeys { k -> k.key.toString() }.mapValues { v -> v.value as Any }) }
                    ?: DietaryInfo(),
                offerType = d["offerType"] as? String,
                includesDrink = d["includesDrink"] as? Boolean ?: false,
                includesDessert = d["includesDessert"] as? Boolean ?: false,
                includesCoffee = d["includesCoffee"] as? Boolean ?: false,
                serviceTime = d["serviceTime"] as? String ?: "both",
                isPermanent = d["isPermanent"] as? Boolean ?: false,
                recurringDays = (d["recurringDays"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() },
            )
        }
        // Filtrar menús expirados (>24h) excepto permanentes y comercios no verificados
        // (`isMerchantVerified == false`). Ausente o `true` = visible (compat con docs legacy).
        val calWeekday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val todayWeekday = if (calWeekday == java.util.Calendar.SUNDAY) 6 else calWeekday - 2
        val activeMenus = menus.filter {
            it.isToday && it.isVisibleOnDay(todayWeekday) && (it.isMerchantVerified ?: true)
        }
        return MenuPage(activeMenus, snapshot.documents.lastOrNull())
    }

    suspend fun getMenusByMerchant(merchantId: String): List<Menu> {
        val snapshot = db.collection("dailyOffers")
            .whereEqualTo("businessId", merchantId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()

        return snapshot.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            Menu(
                id = doc.id, businessId = merchantId,
                title = d["title"] as? String ?: return@mapNotNull null,
                description = d["description"] as? String,
                priceTotal = (d["priceTotal"] as? Number)?.toDouble() ?: 0.0,
                currency = d["currency"] as? String ?: "EUR",
                photoUrls = (d["photoUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                tags = (d["tags"] as? List<*>)?.filterIsInstance<String>(),
                createdAt = (d["createdAt"] as? com.google.firebase.Timestamp)?.let {
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(it.toDate())
                } ?: (d["createdAt"] as? String ?: ""),
                isActive = d["isActive"] as? Boolean ?: true,
                isMerchantPro = d["isMerchantPro"] as? Boolean ?: false,
                isMerchantVerified = d["isMerchantVerified"] as? Boolean,
                dietaryInfo = (d["dietaryInfo"] as? Map<*, *>)
                    ?.let { DietaryInfo.from(it.mapKeys { k -> k.key.toString() }.mapValues { v -> v.value as Any }) }
                    ?: DietaryInfo(),
                offerType = d["offerType"] as? String,
                includesDrink = d["includesDrink"] as? Boolean ?: false,
                includesDessert = d["includesDessert"] as? Boolean ?: false,
                includesCoffee = d["includesCoffee"] as? Boolean ?: false,
                serviceTime = d["serviceTime"] as? String ?: "both",
                isPermanent = d["isPermanent"] as? Boolean ?: false,
                recurringDays = (d["recurringDays"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() },
            )
        }
    }

    suspend fun createMenu(title: String, description: String, price: Double, currency: String = "EUR",
                           photoData: ByteArray, tags: List<String>? = null, dietaryInfo: DietaryInfo = DietaryInfo(),
                           offerType: String? = null, includesDrink: Boolean = false,
                           includesDessert: Boolean = false, includesCoffee: Boolean = false,
                           serviceTime: String = "both", isPermanent: Boolean = false,
                           recurringDays: List<Int>? = null): Menu {
        val uid = currentUid ?: throw Exception("No autenticado")

        // SECURITY: derivar isMerchantPro del documento del comercio en el servidor.
        // Nunca confiar en un flag pasado desde la UI / ViewModel (podría manipularse).
        // Las Firestore rules además validan que el valor enviado coincida con el server-side.
        val businessDoc = db.collection("businesses").document(uid).get().await()
        val planTier = businessDoc.getString("planTier") ?: "free"
        val isPro = planTier == "pro"
        // Comercio verificado: ausente o true. Sólo `false` explícito oculta del feed.
        val isVerified = businessDoc.getBoolean("isVerified") ?: true

        // Guard client-side: suscripción vigente (trial o pago).
        // Las rules bloquean server-side de todas formas, pero esto evita subir la foto
        // y da un mensaje más claro.
        val status = businessDoc.getString("subscriptionStatus") ?: com.sozolab.zampa.data.model.SubscriptionStatus.TRIAL
        val trialEnd = businessDoc.getLong("trialEndsAt") ?: 0L
        val activeUntil = businessDoc.getLong("subscriptionActiveUntil") ?: 0L
        val nowMs = System.currentTimeMillis()
        val promoActive = (getPromoFreeUntilMs() ?: 0L) > nowMs
        val canPublish = when {
            promoActive -> true
            status == com.sozolab.zampa.data.model.SubscriptionStatus.TRIAL -> trialEnd > nowMs
            status == com.sozolab.zampa.data.model.SubscriptionStatus.ACTIVE -> activeUntil > nowMs
            else -> false
        }
        if (!canPublish) throw Exception("subscription_expired")

        val imagePath = "dailyOffers/${UUID.randomUUID()}.jpg"
        val photoUrl = uploadImage(photoData, imagePath)

        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = Date()
        val id = UUID.randomUUID().toString()
        val createdAtStr = iso.format(now)

        val menuData = mutableMapOf<String, Any>(
            "id" to id, "businessId" to uid, "date" to iso.format(now),
            "title" to title, "description" to description, "priceTotal" to price, "currency" to currency,
            "photoUrls" to listOf(photoUrl), "tags" to (tags ?: emptyList<String>()),
            "createdAt" to createdAtStr, "updatedAt" to createdAtStr, "isActive" to true,
            "isMerchantPro" to isPro, "isMerchantVerified" to isVerified, "dietaryInfo" to dietaryInfo.toMap(),
            "includesDrink" to includesDrink, "includesDessert" to includesDessert, "includesCoffee" to includesCoffee,
            "serviceTime" to serviceTime, "isPermanent" to isPermanent
        )
        offerType?.let { menuData["offerType"] = it }
        if (isPermanent && recurringDays != null && recurringDays.isNotEmpty()) {
            menuData["recurringDays"] = recurringDays
        }
        db.collection("dailyOffers").document(id).set(menuData).await()

        return Menu(id = id, businessId = uid, title = title, description = description,
            priceTotal = price, currency = currency, photoUrls = listOf(photoUrl), tags = tags,
            createdAt = createdAtStr, isActive = true, isMerchantPro = isPro, isMerchantVerified = isVerified, dietaryInfo = dietaryInfo,
            offerType = offerType, includesDrink = includesDrink, includesDessert = includesDessert, includesCoffee = includesCoffee,
            serviceTime = serviceTime, isPermanent = isPermanent,
            recurringDays = if (isPermanent) recurringDays else null)
    }

    suspend fun updateMenu(menuId: String, data: Map<String, Any>) {
        val updateData = data.toMutableMap()
        updateData["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        db.collection("dailyOffers").document(menuId).update(updateData).await()
    }

    suspend fun deleteMenu(menuId: String) { db.collection("dailyOffers").document(menuId).delete().await() }

    // ── Favorites ──

    suspend fun getFavorites(): List<Favorite> {
        val uid = currentUid ?: return emptyList()
        val snapshot = db.collection("favorites").whereEqualTo("customerId", uid).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            Favorite(id = doc.id, customerId = uid, businessId = d["businessId"] as? String ?: "",
                createdAt = d["createdAt"] as? String ?: "",
                notificationsEnabled = d["notificationsEnabled"] as? Boolean ?: true)
        }
    }

    suspend fun addFavorite(merchantId: String) {
        val uid = currentUid ?: throw Exception("No autenticado")
        val id = "${uid}_${merchantId}"
        db.collection("favorites").document(id).set(mapOf(
            "id" to id, "customerId" to uid, "businessId" to merchantId,
            "createdAt" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            "notificationsEnabled" to true
        )).await()
    }

    suspend fun removeFavorite(merchantId: String) {
        val uid = currentUid ?: return
        val docs = db.collection("favorites").whereEqualTo("customerId", uid).whereEqualTo("businessId", merchantId).get().await()
        docs.documents.forEach { it.reference.delete().await() }
    }

    suspend fun isFavorite(merchantId: String): Boolean {
        val uid = currentUid ?: return false
        val snapshot = db.collection("favorites")
            .whereEqualTo("customerId", uid)
            .whereEqualTo("businessId", merchantId)
            .limit(1)
            .get().await()
        return !snapshot.isEmpty
    }

    suspend fun toggleFavorite(merchantId: String): Boolean {
        return if (isFavorite(merchantId)) {
            removeFavorite(merchantId)
            false
        } else {
            addFavorite(merchantId)
            true
        }
    }

    suspend fun trackAction(menuId: String, merchantId: String, action: String) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val docRef = db.collection("metrics").document(merchantId)
            .collection("daily").document(dateStr)

        try {
            db.runTransaction { transaction ->
                val doc = transaction.get(docRef)
                if (doc.exists()) {
                    transaction.update(docRef, "clicks.$action", FieldValue.increment(1))
                } else {
                    val stats = mapOf(
                        "impressions" to 0,
                        "favorites" to 0,
                        "clicks" to mapOf(action to 1)
                    )
                    transaction.set(docRef, stats)
                }
            }.await()
        } catch (e: Exception) {
            // Silently fail tracking
        }
    }

    suspend fun trackImpression(merchantId: String) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        db.collection("metrics").document(merchantId)
            .collection("daily").document(dateStr)
            .set(mapOf("impressions" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    // ── Business Profile ──

    suspend fun getMerchantProfile(merchantId: String): Merchant? {
        val doc = db.collection("businesses").document(merchantId).get().await()
        val d = doc.data ?: return null
        return parseMerchantDoc(merchantId, d)
    }

    /**
     * Devuelve todos los comercios verificados con dirección geocodificada,
     * para mostrarlos como pines en el mapa del feed (incluso sin oferta del día).
     * Filtramos cliente-side por `isVerified != false` (ausente o true cuenta como
     * verificado, compat legacy) y por coordenadas no-cero.
     */
    suspend fun getAllVerifiedMerchants(): List<Merchant> {
        val snap = db.collection("businesses").get().await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            val m = parseMerchantDoc(doc.id, d)
            if (m.isVerified == false) return@mapNotNull null
            val addr = m.address ?: return@mapNotNull null
            if (addr.lat == 0.0 && addr.lng == 0.0) return@mapNotNull null
            m
        }
    }

    /** Helper compartido entre `getMerchantProfile` y `getAllVerifiedMerchants`. */
    private fun parseMerchantDoc(merchantId: String, d: Map<String, Any?>): Merchant {
        val address = (d["address"] as? Map<*, *>)?.let { addrMap ->
            val formatted = addrMap["formatted"] as? String ?: return@let null
            val lat = (addrMap["lat"] as? Number)?.toDouble() ?: return@let null
            val lng = (addrMap["lng"] as? Number)?.toDouble() ?: return@let null
            MerchantAddress(formatted, lat, lng, addrMap["placeId"] as? String)
        }

        val schedule = (d["schedule"] as? List<*>)?.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val day = map["day"] as? String ?: return@mapNotNull null
            val open = map["open"] as? String ?: return@mapNotNull null
            val close = map["close"] as? String ?: return@mapNotNull null
            ScheduleEntry(day, open, close)
        }

        @Suppress("UNCHECKED_CAST")
        val cuisineTypes = d["cuisineTypes"] as? List<String>

        return Merchant(id = merchantId,
            userId = d["userId"] as? String,
            name = d["name"] as? String ?: "",
            phone = d["phone"] as? String,
            address = address,
            addressText = d["addressText"] as? String,
            schedule = schedule,
            cuisineTypes = cuisineTypes,
            shortDescription = d["shortDescription"] as? String,
            acceptsReservations = d["acceptsReservations"] as? Boolean ?: false,
            planTier = d["planTier"] as? String,
            isHighlighted = d["isHighlighted"] as? Boolean,
            coverPhotoUrl = d["coverPhotoUrl"] as? String,
            profilePhotoUrl = d["profilePhotoUrl"] as? String,
            taxId = d["taxId"] as? String,
            subscriptionStatus = d["subscriptionStatus"] as? String,
            trialEndsAt = (d["trialEndsAt"] as? Number)?.toLong(),
            subscriptionActiveUntil = (d["subscriptionActiveUntil"] as? Number)?.toLong(),
            isVerified = d["isVerified"] as? Boolean,
            appAccountToken = d["appAccountToken"] as? String)
    }

    /**
     * Genera y guarda un UUID en `businesses/{uid}.appAccountToken` si aún no existe.
     * Idempotente: devuelve el existente si ya hay uno. Necesario antes de la primera
     * compra IAP — el webhook (playRTDN) lo usa para mapear la transacción al merchant.
     */
    suspend fun getOrCreateAppAccountToken(): String {
        val uid = currentUid ?: throw Exception("No autenticado")
        val ref = db.collection("businesses").document(uid)
        val doc = ref.get().await()
        val existing = doc.getString("appAccountToken")
        if (!existing.isNullOrBlank()) return existing
        val token = java.util.UUID.randomUUID().toString()
        ref.set(mapOf("appAccountToken" to token), com.google.firebase.firestore.SetOptions.merge()).await()
        return token
    }

    /**
     * Registra `purchaseToken → merchantId` en `playPurchases/{purchaseToken}`.
     * La llama el `BillingClient` tras un acknowledgePurchase exitoso. Sirve como
     * fallback para que `playRTDN` (Cloud Function) pueda mapear la compra al
     * merchant cuando la cuenta de Play Console no tiene API access habilitada
     * (situación común en cuentas nuevas).
     *
     * Idempotente: el doc se sobreescribe en cada llamada.
     */
    suspend fun recordPlayPurchase(purchaseToken: String) {
        val uid = currentUid ?: throw Exception("No autenticado")
        db.collection("playPurchases").document(purchaseToken).set(
            mapOf(
                "businessId" to uid,
                "recordedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    /**
     * Devuelve el timestamp (ms epoch) hasta el cual la app es gratis por promo
     * global (config/promo.freeUntil). Null si no hay promo configurada.
     * El admin controla esto desde Firebase Console.
     */
    suspend fun getPromoFreeUntilMs(): Long? {
        val doc = db.collection("config").document("promo").get().await()
        return doc.getLong("freeUntil")
    }

    suspend fun isMerchantProfileComplete(merchantId: String): Boolean {
        val doc = db.collection("businesses").document(merchantId).get().await()
        val d = doc.data ?: return false
        val taxId = d["taxId"] as? String
        return d["address"] != null
            && d["phone"] != null
            && !taxId.isNullOrBlank()
    }

    suspend fun updateUserName(name: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("name", name).await()
    }

    // ── Account deletion (soft delete + 30-day grace period) ──

    /**
     * Marca la cuenta actual como pendiente de eliminación:
     * setea deletedAt y scheduledPurgeAt en users/{uid} y borra los
     * deviceTokens del usuario para cortar notificaciones push.
     * NO borra el Auth user ni datos del usuario — eso lo hace la Cloud
     * Function purgeDeletedAccounts a los 30 días.
     */
    suspend fun requestAccountDeletion() {
        val uid = currentUid ?: throw Exception("No autenticado")

        // 1. Borrar device tokens del usuario (query + batch delete)
        val tokensSnap = db.collection("deviceTokens")
            .whereEqualTo("userId", uid)
            .get().await()
        if (!tokensSnap.isEmpty) {
            val batch = db.batch()
            tokensSnap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        // 2. Marcar users/{uid} con deletedAt y scheduledPurgeAt
        val nowMs = System.currentTimeMillis()
        val purgeMs = nowMs + 30L * 24 * 60 * 60 * 1000
        db.collection("users").document(uid).update(
            mapOf(
                "deletedAt" to com.google.firebase.Timestamp(java.util.Date(nowMs)),
                "scheduledPurgeAt" to com.google.firebase.Timestamp(java.util.Date(purgeMs)),
            )
        ).await()
    }

    /**
     * Cancela una eliminación pendiente limpiando deletedAt y scheduledPurgeAt.
     */
    suspend fun cancelAccountDeletion() {
        val uid = currentUid ?: throw Exception("No autenticado")
        db.collection("users").document(uid).update(
            mapOf(
                "deletedAt" to com.google.firebase.firestore.FieldValue.delete(),
                "scheduledPurgeAt" to com.google.firebase.firestore.FieldValue.delete(),
            )
        ).await()
    }

    // ── Currency preference ──

    private val supportedCurrencyCodes = setOf(
        "EUR", "USD", "GBP", "JPY", "CHF", "SEK", "NOK", "DKK", "CAD", "AUD"
    )

    /**
     * Actualiza la moneda preferida del usuario en Firestore. El caller
     * debe refrescar el User observado tras el éxito.
     */
    suspend fun updateCurrencyPreference(code: String) {
        val uid = currentUid ?: throw Exception("No autenticado")
        require(code in supportedCurrencyCodes) { "Código de moneda no soportado: $code" }
        db.collection("users").document(uid).update("currencyPreference", code).await()
    }

    suspend fun updateMerchantProfile(merchant: Merchant) {
        val data = mutableMapOf<String, Any>(
            "name" to merchant.name, "acceptsReservations" to merchant.acceptsReservations,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        merchant.phone?.let { data["phone"] = it }
        merchant.shortDescription?.let { data["shortDescription"] = it }
        merchant.cuisineTypes?.let { data["cuisineTypes"] = it }
        merchant.coverPhotoUrl?.let { data["coverPhotoUrl"] = it }
        merchant.profilePhotoUrl?.let { data["profilePhotoUrl"] = it }
        merchant.planTier?.let { data["planTier"] = it }
        merchant.addressText?.let { data["addressText"] = it }
        merchant.isHighlighted?.let { data["isHighlighted"] = it }
        merchant.taxId?.trim()?.takeIf { it.isNotEmpty() }?.let { data["taxId"] = it.uppercase() }
        merchant.address?.let { data["address"] = mapOf("formatted" to it.formatted, "lat" to it.lat, "lng" to it.lng, "placeId" to (it.placeId ?: "")) }
        merchant.schedule?.let { list -> data["schedule"] = list.map { mapOf("day" to it.day, "open" to it.open, "close" to it.close) } }
        db.collection("businesses").document(merchant.id).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    // ── Storage ──

    suspend fun uploadImage(data: ByteArray, path: String): String {
        val ref = storage.reference.child(path)
        ref.putBytes(data).await()
        return ref.downloadUrl.await().toString()
    }

    // ── Notifications ──

    suspend fun registerDeviceToken(token: String) {
        val uid = currentUid ?: return
        val tokenId = "${uid}_ANDROID"
        db.collection("deviceTokens").document(tokenId).set(mapOf(
            "userId" to uid,
            "token" to token,
            "platform" to "ANDROID",
            "createdAt" to FieldValue.serverTimestamp(),
            "lastUsedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun getMenuById(menuId: String): Menu? {
        val doc = db.collection("dailyOffers").document(menuId).get().await()
        val d = doc.data ?: return null
        return Menu(
            id = doc.id,
            businessId = d["businessId"] as? String ?: "",
            date = d["date"] as? String ?: "",
            title = d["title"] as? String ?: "",
            description = d["description"] as? String,
            priceTotal = (d["priceTotal"] as? Number)?.toDouble() ?: 0.0,
            currency = d["currency"] as? String ?: "EUR",
            photoUrls = (d["photoUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            tags = (d["tags"] as? List<*>)?.filterIsInstance<String>(),
            createdAt = d["createdAt"] as? String ?: "",
            updatedAt = d["updatedAt"] as? String ?: "",
            isActive = d["isActive"] as? Boolean ?: true,
            dietaryInfo = (d["dietaryInfo"] as? Map<*, *>)
                ?.let { DietaryInfo.from(it.mapKeys { k -> k.key.toString() }.mapValues { v -> v.value as Any }) }
                ?: DietaryInfo(),
        )
    }

    suspend fun getMerchantStats(merchantId: String, days: Int = 7): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()

        for (i in 0 until days) {
            val dateStr = sdf.format(calendar.time)
            val doc = db.collection("metrics").document(merchantId)
                .collection("daily").document(dateStr).get().await()
            
            doc.data?.let {
                val data = it.toMutableMap()
                data["date"] = dateStr
                results.add(data)
            }
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return results
    }

    suspend fun reportMenu(menuId: String, reason: String): Boolean {
        val uid = currentUid ?: return false
        val id = UUID.randomUUID().toString()
        val data = mapOf(
            "id" to id,
            "offerId" to menuId,
            "reporterId" to uid,
            "reason" to reason,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            db.collection("reports").document(id).set(data).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Cuisine Types ──
    suspend fun fetchCuisineTypes(): List<CuisineType> {
        return try {
            val snapshot = db.collection("cuisineTypes").orderBy("name").get().await()
            snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                CuisineType(id = doc.id, name = name)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Subscriptions ──
    suspend fun getActiveSubscription(businessId: String): Subscription? {
        val snapshot = db.collection("subscriptions")
            .whereEqualTo("businessId", businessId)
            .whereEqualTo("status", "ACTIVE")
            .limit(1)
            .get().await()
        
        val doc = snapshot.documents.firstOrNull() ?: return null
        return Subscription(
            id = doc.id,
            businessId = doc.getString("businessId") ?: "",
            type = doc.getString("type") ?: "MONTHLY",
            status = doc.getString("status") ?: "ACTIVE",
            startDate = doc.getString("startDate") ?: "",
            endDate = doc.getString("endDate") ?: ""
        )
    }

    suspend fun createSubscription(businessId: String, type: String = "MONTHLY"): Subscription {
        val id = UUID.randomUUID().toString()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val now = sdf.format(Date())
        
        val calendar = Calendar.getInstance()
        if (type == "YEARLY") calendar.add(Calendar.YEAR, 1)
        else calendar.add(Calendar.MONTH, 1)
        
        val endDate = sdf.format(calendar.time)
        val data = mapOf(
            "id" to id,
            "businessId" to businessId,
            "type" to type,
            "status" to "ACTIVE",
            "startDate" to now,
            "endDate" to endDate,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        
        db.collection("subscriptions").document(id).set(data).await()
        return Subscription(
            id = id, businessId = businessId, type = type,
            status = "ACTIVE", startDate = now, endDate = endDate
        )
    }

    // ── Notifications ──
    suspend fun getNotifications(): List<AppNotification> {
        val uid = currentUid ?: return emptyList()
        val snapshot = db.collection("notifications")
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get().await()
            
        return snapshot.documents.mapNotNull { doc ->
            AppNotification(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                businessId = doc.getString("businessId"),
                offerId = doc.getString("offerId"),
                type = doc.getString("type") ?: "",
                title = doc.getString("title") ?: "",
                body = doc.getString("body") ?: "",
                read = doc.getBoolean("read") ?: false,
                createdAt = doc.getString("createdAt") ?: "" // Sometimes timestamp, but safe to map or ignore
            )
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        db.collection("notifications").document(notificationId)
            .update("read", true).await()
    }

    // ── Notification Preferences ──
    suspend fun getNotificationPreferences(): NotificationPreferences {
        val uid = currentUid ?: return NotificationPreferences()
        val doc = db.collection("users").document(uid).get().await()
        val prefs = doc.get("notificationPreferences") as? Map<*, *> ?: return NotificationPreferences()
        return NotificationPreferences(
            newMenuFromFavorites = prefs["newMenuFromFavorites"] as? Boolean ?: true,
            promotions = prefs["promotions"] as? Boolean ?: true,
            general = prefs["general"] as? Boolean ?: true,
        )
    }

    suspend fun updateNotificationPreferences(prefs: NotificationPreferences) {
        val uid = currentUid ?: return
        db.collection("users").document(uid).update(
            "notificationPreferences", mapOf(
                "newMenuFromFavorites" to prefs.newMenuFromFavorites,
                "promotions" to prefs.promotions,
                "general" to prefs.general,
            )
        ).await()
    }

    // ── User History ──

    suspend fun saveUserHistoryEntry(businessId: String, businessName: String, action: String) {
        val uid = currentUid ?: return
        val docId = "${uid}_${businessId}_${action}"
        db.collection("userHistory").document(docId).set(mapOf(
            "userId" to uid,
            "businessId" to businessId,
            "businessName" to businessName,
            "action" to action,
            "timestamp" to FieldValue.serverTimestamp()
        ), SetOptions.merge()).await()
    }

    suspend fun getUserHistory(): List<Map<String, Any>> {
        val uid = currentUid ?: return emptyList()
        val snapshot = db.collection("userHistory")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get().await()
        return snapshot.documents.mapNotNull { it.data }
    }
}
