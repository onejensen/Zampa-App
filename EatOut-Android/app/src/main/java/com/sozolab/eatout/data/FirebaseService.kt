package com.sozolab.eatout.data

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.sozolab.eatout.data.model.*
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
        db.collection("users").document(uid).set(userData).await()

        if (role == User.UserRole.COMERCIO) {
            db.collection("businesses").document(uid).set(mapOf(
                "id" to uid, "userId" to uid, "name" to name,
                "acceptsReservations" to false, "planTier" to "free",
                "isHighlighted" to false, "createdAt" to FieldValue.serverTimestamp()
            )).await()
        }

        if (role == User.UserRole.CLIENTE) {
            db.collection("customers").document(uid).set(mapOf(
                "id" to uid, "userId" to uid, "displayName" to name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )).await()
        }

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

    /** Finalizes social registration: updates role and creates the corresponding profile document. */
    suspend fun finalizeSocialRegistration(uid: String, role: User.UserRole, name: String, email: String): User {
        db.collection("users").document(uid).update(mapOf("role" to role.toFirestore(), "name" to name)).await()
        if (role == User.UserRole.COMERCIO) {
            val biz = db.collection("businesses").document(uid).get().await()
            if (!biz.exists()) {
                db.collection("businesses").document(uid).set(mapOf(
                    "id" to uid, "userId" to uid, "name" to name,
                    "acceptsReservations" to false, "planTier" to "free",
                    "isHighlighted" to false, "createdAt" to FieldValue.serverTimestamp()
                )).await()
            }
        } else {
            val cust = db.collection("customers").document(uid).get().await()
            if (!cust.exists()) {
                db.collection("customers").document(uid).set(mapOf(
                    "id" to uid, "userId" to uid, "displayName" to name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )).await()
            }
        }
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
            photoUrl = data["photoUrl"] as? String
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
                createdAt = d["createdAt"] as? String ?: "",
                updatedAt = d["updatedAt"] as? String ?: "",
                isActive = d["isActive"] as? Boolean ?: true,
                isMerchantPro = d["isMerchantPro"] as? Boolean ?: false,
                dietaryInfo = (d["dietaryInfo"] as? Map<*, *>)
                    ?.let { DietaryInfo.from(it.mapKeys { k -> k.key.toString() }.mapValues { v -> v.value as Any }) }
                    ?: DietaryInfo(),
                offerType = d["offerType"] as? String,
                includesDrink = d["includesDrink"] as? Boolean ?: false,
                includesDessert = d["includesDessert"] as? Boolean ?: false,
                includesCoffee = d["includesCoffee"] as? Boolean ?: false,
            )
        }
        return MenuPage(menus, snapshot.documents.lastOrNull())
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
                createdAt = d["createdAt"] as? String ?: "",
                isActive = d["isActive"] as? Boolean ?: true,
                isMerchantPro = d["isMerchantPro"] as? Boolean ?: false,
                dietaryInfo = (d["dietaryInfo"] as? Map<*, *>)
                    ?.let { DietaryInfo.from(it.mapKeys { k -> k.key.toString() }.mapValues { v -> v.value as Any }) }
                    ?: DietaryInfo(),
                offerType = d["offerType"] as? String,
                includesDrink = d["includesDrink"] as? Boolean ?: false,
                includesDessert = d["includesDessert"] as? Boolean ?: false,
                includesCoffee = d["includesCoffee"] as? Boolean ?: false,
            )
        }
    }

    suspend fun createMenu(title: String, description: String, price: Double, currency: String = "EUR", photoData: ByteArray, tags: List<String>? = null, isPro: Boolean = false, dietaryInfo: DietaryInfo = DietaryInfo(), offerType: String? = null, includesDrink: Boolean = false, includesDessert: Boolean = false, includesCoffee: Boolean = false): Menu {
        val uid = currentUid ?: throw Exception("No autenticado")
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
            "isMerchantPro" to isPro, "dietaryInfo" to dietaryInfo.toMap(),
            "includesDrink" to includesDrink, "includesDessert" to includesDessert, "includesCoffee" to includesCoffee
        )
        offerType?.let { menuData["offerType"] = it }
        db.collection("dailyOffers").document(id).set(menuData).await()

        return Menu(id = id, businessId = uid, title = title, description = description,
            priceTotal = price, currency = currency, photoUrls = listOf(photoUrl), tags = tags,
            createdAt = createdAtStr, isActive = true, isMerchantPro = isPro, dietaryInfo = dietaryInfo,
            offerType = offerType, includesDrink = includesDrink, includesDessert = includesDessert, includesCoffee = includesCoffee)
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
        val id = UUID.randomUUID().toString()
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
        return Merchant(id = merchantId,
            userId = d["userId"] as? String,
            name = d["name"] as? String ?: "",
            phone = d["phone"] as? String,
            shortDescription = d["shortDescription"] as? String,
            acceptsReservations = d["acceptsReservations"] as? Boolean ?: false,
            planTier = d["planTier"] as? String,
            isHighlighted = d["isHighlighted"] as? Boolean)
    }

    suspend fun isMerchantProfileComplete(merchantId: String): Boolean {
        val doc = db.collection("businesses").document(merchantId).get().await()
        val d = doc.data ?: return false
        return d["address"] != null && d["phone"] != null
    }

    suspend fun updateUserName(name: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("name", name).await()
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
        merchant.planTier?.let { data["planTier"] = it }
        merchant.addressText?.let { data["addressText"] = it }
        merchant.isHighlighted?.let { data["isHighlighted"] = it }
        merchant.address?.let { data["address"] = mapOf("formatted" to it.formatted, "lat" to it.lat, "lng" to it.lng) }
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
}
