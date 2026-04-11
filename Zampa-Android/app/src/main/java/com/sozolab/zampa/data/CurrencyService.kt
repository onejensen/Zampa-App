package com.sozolab.zampa.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Tasas de cambio cargadas en memoria. Base siempre EUR. */
data class ExchangeRates(
    val base: String,
    val rates: Map<String, Double>,
    val updatedAt: com.google.firebase.Timestamp,
)

/**
 * Servicio @Singleton que lee `config/exchangeRates` una vez por sesión
 * y expone helpers de conversión y formato. Con fallback embebido para
 * que las conversiones funcionen incluso antes del primer fetch.
 */
@Singleton
class CurrencyService @Inject constructor(
    private val db: FirebaseFirestore,
) {
    companion object {
        val supported = listOf(
            "EUR", "USD", "GBP", "JPY", "CHF",
            "SEK", "NOK", "DKK", "CAD", "AUD"
        )

        /**
         * Snapshot embebido como fallback cuando la primera lectura de
         * Firestore aún no ha terminado o hay un fallo.
         * Actualizado a mano en cada release grande.
         */
        val fallbackRates = mapOf(
            "USD" to 1.09, "GBP" to 0.85, "JPY" to 158.3, "CHF" to 0.96,
            "SEK" to 11.42, "NOK" to 11.78, "DKK" to 7.46,
            "CAD" to 1.48, "AUD" to 1.63
        )

        /**
         * Formatea "12,50 €" / "$13.60 USD" / "¥1980 JPY" etc.
         * JPY = 0 decimales; resto = 2. EUR usa coma (es_ES);
         * resto usa punto (en_US).
         */
        fun format(amount: Double, code: String): String {
            val isJpy = code == "JPY"
            val pattern = if (isJpy) "#,##0" else "#,##0.00"
            val locale = if (code == "EUR") Locale("es", "ES") else Locale.US
            val symbols = DecimalFormatSymbols(locale)
            val df = DecimalFormat(pattern, symbols)
            val numStr = df.format(amount)
            return when (code) {
                "EUR" -> "$numStr €"
                "USD" -> "\$$numStr USD"
                "GBP" -> "£$numStr GBP"
                "JPY" -> "¥$numStr JPY"
                "CHF" -> "$numStr CHF"
                "SEK" -> "$numStr kr SEK"
                "NOK" -> "$numStr kr NOK"
                "DKK" -> "$numStr kr DKK"
                "CAD" -> "C\$$numStr CAD"
                "AUD" -> "A\$$numStr AUD"
                else  -> "$numStr $code"
            }
        }
    }

    private val _rates = MutableStateFlow<ExchangeRates?>(null)
    val rates: StateFlow<ExchangeRates?> = _rates

    private var hasLoaded = false

    /** Una sola vez por sesión. Silencioso en fallo. */
    suspend fun loadIfNeeded() {
        if (hasLoaded) return
        hasLoaded = true
        try {
            val doc = db.collection("config").document("exchangeRates").get().await()
            val data = doc.data ?: return
            val base = data["base"] as? String ?: return
            val ratesAny = data["rates"] as? Map<*, *> ?: return
            val ts = data["updatedAt"] as? com.google.firebase.Timestamp ?: return
            val parsed = ratesAny.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toDouble() ?: return@mapNotNull null
                key to value
            }.toMap()
            _rates.value = ExchangeRates(base, parsed, ts)
        } catch (e: Exception) {
            // Silencio — el fallback embebido cubre las conversiones.
            android.util.Log.w("CurrencyService", "loadIfNeeded falló: ${e.message}")
        }
    }

    /**
     * Convierte un importe en EUR al código destino. Retorna null si no
     * hay tasa ni en memoria ni en fallback.
     */
    fun convert(eurAmount: Double, to: String): Double? {
        if (to == "EUR") return eurAmount
        val rate = _rates.value?.rates?.get(to) ?: fallbackRates[to] ?: return null
        return eurAmount * rate
    }

    /**
     * Conveniencia: convierte + formatea. Devuelve null si la conversión
     * no es posible.
     */
    fun formatConverted(eurAmount: Double, to: String): String? {
        val converted = convert(eurAmount, to) ?: return null
        return format(converted, to)
    }
}
