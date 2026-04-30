package com.sozolab.zampa.data.model

/** Modelo de usuario */
data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: UserRole = UserRole.CLIENTE,
    val phone: String? = null,
    val photoUrl: String? = null,
    /** Fecha en la que el usuario solicitó la eliminación. Nulo = activa. */
    val deletedAt: com.google.firebase.Timestamp? = null,
    /** Fecha programada para la purga definitiva (deletedAt + 30 días). */
    val scheduledPurgeAt: com.google.firebase.Timestamp? = null,
    /** Código ISO 4217 de la moneda preferida. Default EUR cuando ausente. */
    val currencyPreference: String = "EUR",
    val languagePreference: String = "auto",
) {
    enum class UserRole {
        CLIENTE, COMERCIO;
        companion object {
            fun fromString(s: String) = when(s.uppercase()) {
                "COMERCIO" -> COMERCIO
                else -> CLIENTE
            }
        }
        fun toFirestore() = name
    }
}

/** Dirección del comercio */
data class MerchantAddress(
    val formatted: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val placeId: String? = null,
)

/** Entrada de horario */
data class ScheduleEntry(
    val day: String = "",
    val open: String = "",
    val close: String = "",
)

/** Perfil de comercio (colección `businesses` en Firestore) */
/**
 * Estado de suscripción de un merchant.
 *  - "trial": dentro del periodo gratuito de 90 días tras registro
 *  - "active": suscripción de pago vigente (Cloud Functions appStoreNotifications/playRTDN)
 *  - "expired": trial o suscripción caducada — no puede publicar
 */
object SubscriptionStatus {
    const val TRIAL = "trial"
    const val ACTIVE = "active"
    const val EXPIRED = "expired"
}

data class Merchant(
    val id: String = "",
    val userId: String? = null,
    val name: String = "",
    val phone: String? = null,
    val address: MerchantAddress? = null,
    val addressText: String? = null,
    val schedule: List<ScheduleEntry>? = null,
    val cuisineTypes: List<String>? = null,
    val acceptsReservations: Boolean = false,
    val shortDescription: String? = null,
    val coverPhotoUrl: String? = null,
    val profilePhotoUrl: String? = null,
    val planTier: String? = "free",
    val isHighlighted: Boolean? = false,
    /** NIF/CIF (España) del negocio. Normalizado: sin espacios, en mayúsculas. */
    val taxId: String? = null,
    /** Estado efectivo. Legacy null = trial. */
    val subscriptionStatus: String? = null,
    /** Fin del periodo de prueba (ms epoch). */
    val trialEndsAt: Long? = null,
    /** Fin de la suscripción de pago vigente (ms epoch). */
    val subscriptionActiveUntil: Long? = null,
    /**
     * Verificación manual del comercio. Falso al registrarse; admin lo cambia desde Firebase Console
     * tras revisar `pendingVerifications/{id}`. Las ofertas de comercios no verificados se filtran
     * del feed. Ausente o `true` = verificado (compat con docs legacy).
     */
    val isVerified: Boolean? = null,
    /**
     * UUID generado por la app antes de la primera compra IAP. Apple lo recibe como
     * `appAccountToken` (StoreKit 2) y Google como `obfuscatedAccountId` (Play Billing).
     * El webhook usa este campo para mapear la transacción al merchant correcto.
     */
    val appAccountToken: String? = null,
) {
    /** Trial no expirado o suscripción vigente. */
    fun canPublish(nowMs: Long = System.currentTimeMillis()): Boolean {
        val status = subscriptionStatus ?: SubscriptionStatus.TRIAL
        return when (status) {
            SubscriptionStatus.TRIAL -> (trialEndsAt ?: 0L) > nowMs
            SubscriptionStatus.ACTIVE -> (subscriptionActiveUntil ?: 0L) > nowMs
            else -> false
        }
    }

    /** Días restantes de trial (0 si expiró, null si no está en trial). */
    fun trialDaysRemaining(nowMs: Long = System.currentTimeMillis()): Int? {
        val status = subscriptionStatus ?: SubscriptionStatus.TRIAL
        if (status != SubscriptionStatus.TRIAL) return null
        val end = trialEndsAt ?: return 0
        return maxOf(0, ((end - nowMs) / (24 * 60 * 60 * 1000L)).toInt())
    }
}

/** Información dietética y alérgenos de una oferta */
data class DietaryInfo(
    val isVegetarian: Boolean = false,
    val isVegan: Boolean = false,
    val hasMeat: Boolean = false,
    val hasFish: Boolean = false,
    val hasGluten: Boolean = false,
    val hasLactose: Boolean = false,
    val hasNuts: Boolean = false,
    val hasEgg: Boolean = false,
) {
    val hasAnyAllergen get() = hasGluten || hasLactose || hasNuts || hasEgg
    val hasAnyInfo get() = isVegetarian || isVegan || hasMeat || hasFish || hasAnyAllergen

    fun toMap(): Map<String, Any> = mapOf(
        "isVegetarian" to isVegetarian, "isVegan" to isVegan,
        "hasMeat" to hasMeat, "hasFish" to hasFish,
        "hasGluten" to hasGluten, "hasLactose" to hasLactose,
        "hasNuts" to hasNuts, "hasEgg" to hasEgg,
    )

    companion object {
        fun from(map: Map<String, Any>): DietaryInfo = DietaryInfo(
            isVegetarian = map["isVegetarian"] as? Boolean ?: false,
            isVegan       = map["isVegan"]       as? Boolean ?: false,
            hasMeat       = map["hasMeat"]       as? Boolean ?: false,
            hasFish       = map["hasFish"]       as? Boolean ?: false,
            hasGluten     = map["hasGluten"]     as? Boolean ?: false,
            hasLactose    = map["hasLactose"]    as? Boolean ?: false,
            hasNuts       = map["hasNuts"]       as? Boolean ?: false,
            hasEgg        = map["hasEgg"]        as? Boolean ?: false,
        )
    }
}

/** Oferta diaria (colección `dailyOffers` en Firestore) */
data class Menu(
    val id: String = "",
    val businessId: String = "",
    val date: String = "",
    val title: String = "",
    val description: String? = null,
    val priceTotal: Double = 0.0,
    val currency: String = "EUR",
    val photoUrls: List<String> = emptyList(),
    val tags: List<String>? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val isActive: Boolean = true,
    val isMerchantPro: Boolean? = false,
    /**
     * Denormalizado de `businesses/{businessId}.isVerified`. Ausente o `true` = visible.
     * Las ofertas con `false` se filtran del feed hasta que admin verifique el comercio.
     */
    val isMerchantVerified: Boolean? = null,
    val dietaryInfo: DietaryInfo = DietaryInfo(),
    val offerType: String? = null,
    val includesDrink: Boolean = false,
    val includesDessert: Boolean = false,
    val includesCoffee: Boolean = false,
    /** Horario de la oferta: "lunch", "dinner" o "both" */
    val serviceTime: String = "both",
    /** Oferta permanente (no expira a las 24h) */
    val isPermanent: Boolean = false,
    /** Días de la semana en que esta oferta permanente es visible (0=lun…6=dom). Null = todos los días (legado). */
    val recurringDays: List<Int>? = null,
) {
    val isToday: Boolean
        get() {
            if (isPermanent || offerType == "Oferta permanente") return true
            try {
                val utc = java.util.TimeZone.getTimeZone("UTC")
                val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = utc }
                val isoMs = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = utc }
                val dateObj = iso.parse(createdAt) ?: isoMs.parse(createdAt) ?: iso.parse(date) ?: isoMs.parse(date) ?: return false
                val diffMs = java.util.Date().time - dateObj.time
                return diffMs < 24 * 60 * 60 * 1000 // Menos de 24 horas
            } catch (e: Exception) {
                return false
            }
        }

    /** Texto localizado del horario de la oferta */
    val serviceTimeLabel: String
        get() = when (serviceTime) {
            "lunch" -> "Mediodía"
            "dinner" -> "Noche"
            else -> "Mediodía y noche"
        }

    /** Returns true if this offer should appear in the feed on the given weekday index (0=Mon…6=Sun). */
    fun isVisibleOnDay(weekday: Int): Boolean {
        if (!isPermanent) return true
        val days = recurringDays ?: return true
        if (days.isEmpty()) return true
        return weekday in days
    }

    /**
     * True if this candidate offer collides with [other] when the candidate would publish on
     * a day whose weekday index is [todayWeekday] (0=Mon…6=Sun).
     * Conflict ⇔ they share at least one calendar day AND at least one serviceTime,
     * where "both" overlaps with "lunch", "dinner" and "both".
     * Inactive offers never conflict.
     */
    fun conflictsWith(other: Menu, todayWeekday: Int): Boolean {
        if (!other.isActive || this.id == other.id) return false
        if (serviceTimesOf(this).intersect(serviceTimesOf(other)).isEmpty()) return false
        return activeDaysOf(this, todayWeekday).intersect(activeDaysOf(other, todayWeekday)).isNotEmpty()
    }

    companion object {
        /** Returns the set of weekday indices (0=Mon…6=Sun) already occupied by the given permanent offers. */
        fun occupiedDays(permanents: List<Menu>): Set<Int> {
            val occupied = mutableSetOf<Int>()
            for (menu in permanents) {
                if (!menu.isPermanent) continue
                val days = menu.recurringDays
                if (days == null || days.isEmpty()) {
                    occupied.addAll(0..6)
                } else {
                    occupied.addAll(days)
                }
            }
            return occupied
        }

        /** Returns the active offers in [existing] that conflict with [candidate]. */
        fun findConflicts(candidate: Menu, existing: List<Menu>, todayWeekday: Int): List<Menu> =
            existing.filter { candidate.conflictsWith(it, todayWeekday) }

        private fun serviceTimesOf(m: Menu): Set<String> = when (m.serviceTime) {
            "lunch" -> setOf("lunch")
            "dinner" -> setOf("dinner")
            else -> setOf("lunch", "dinner")
        }

        private fun activeDaysOf(m: Menu, todayWeekday: Int): Set<Int> {
            if (!m.isPermanent) return setOf(todayWeekday)
            val d = m.recurringDays
            return if (d.isNullOrEmpty()) (0..6).toSet() else d.toSet()
        }
    }
}

/** Favorito (colección `favorites` en Firestore) */
data class Favorite(
    val id: String = "",
    val customerId: String = "",
    val businessId: String = "",
    val createdAt: String = "",
    val notificationsEnabled: Boolean = true,
)

/** Catálogo de tipos de cocina (colección `cuisineTypes` en Firestore) */
data class CuisineType(
    val id: String = "",
    val name: String = "",
)

/** Modelo de suscripción de comercio (colección `subscriptions` en Firestore) */
data class Subscription(
    val id: String = "",
    val businessId: String = "",
    val type: String = "MONTHLY",
    val status: String = "ACTIVE",
    val startDate: String = "",
    val endDate: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** Notificación del sistema (colección `notifications` en Firestore) */
data class NotificationPreferences(
    val newMenuFromFavorites: Boolean = true,
    val promotions: Boolean = true,
    val general: Boolean = true,
)

data class AppNotification(
    val id: String = "",
    val userId: String = "",
    val businessId: String? = null,
    val offerId: String? = null,
    val type: String = "NEW_OFFER_FAVORITE",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    val createdAt: String = "",
)
