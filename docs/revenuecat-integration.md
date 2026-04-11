# RevenueCat — Plan de integración (Zampa Pro)

> **Estado actual:** el upgrade a Pro está deshabilitado en ambas apps con el mensaje "Próximamente". Las Firestore rules rechazan cualquier escritura cliente a `businesses.planTier` y `businesses.isHighlighted`. La fase de pago real (este documento) aún no está integrada.
>
> **Por qué RevenueCat:** Apple y Google obligan a usar IAP nativo (StoreKit / Play Billing) para suscripciones digitales en sus stores. RevenueCat envuelve ambos SDKs, valida los recibos en su servidor, y manda webhooks cuando un usuario se suscribe / renueva / cancela.

## 1. Pasos manuales (sin código)

Este bloque hay que hacerlo antes de integrar el SDK. **Yo no puedo hacerlo por ti** — requieren credenciales de cuentas externas.

### 1.1 App Store Connect (iOS)
1. Crear la app (Bundle ID `com.Sozolab.eatout`) si aún no existe.
2. **Acuerdos → Pagos e impuestos**: aceptar el contrato de "Apps de pago". Sin esto no se pueden vender suscripciones.
3. **Funciones → Compras integradas → Suscripciones**: crear un grupo (`Zampa Pro`) con una o varias suscripciones, p. ej.:
   - `eatout_pro_monthly` — 9.99 €/mes
   - `eatout_pro_yearly` — 79.99 €/año
4. Anotar el **Shared Secret** (para validación de recibos).
5. Crear al menos un **sandbox tester** en Users & Access → Sandbox Testers para probar.

### 1.2 Google Play Console (Android)
1. Crear la app (`com.sozolab.eatout`) si aún no existe.
2. Subir al menos un APK/AAB a una pista (Internal testing vale) — Google Play Billing exige que la app esté publicada para que aparezcan los productos.
3. **Monetizar → Productos → Suscripciones**: crear los mismos IDs (`eatout_pro_monthly`, `eatout_pro_yearly`) con base plans equivalentes.
4. **Configuración → Acceso a API**: vincular el proyecto Google Cloud y crear una **Service Account** con rol "Finance" para que RevenueCat pueda leer/validar compras.
5. Descargar el JSON de la service account.

### 1.3 RevenueCat dashboard
1. Crear cuenta en https://app.revenuecat.com (plan gratis: hasta 2.5 k$/mes).
2. Crear el proyecto **Zampa**.
3. Añadir la **app iOS** con Bundle ID `com.Sozolab.eatout` y subir el shared secret de App Store Connect.
4. Añadir la **app Android** con package `com.sozolab.eatout` y subir el JSON de la service account.
5. Crear la **Entitlement** `pro`. Asociar los productos de iOS y Android a esta entitlement.
6. **Project settings → API keys**: anotar las dos public SDK keys (una iOS, una Android).
7. **Integrations → Webhooks**: configurar el webhook con la URL del Cloud Function (paso 2.3) y un secret aleatorio.

## 2. Cambios en código (cuando estén los pasos 1.x)

### 2.1 iOS — añadir SDK Purchases
- Xcode → File → Add Package Dependencies → `https://github.com/RevenueCat/purchases-ios-spm`
- En `EatOutApp.swift` (`AppDelegate.application(_:didFinishLaunchingWithOptions:)`):
  ```swift
  Purchases.logLevel = .info
  Purchases.configure(
    with: Configuration.Builder(withAPIKey: "appl_XXXXXX")
      .with(appUserID: nil) // se enlaza con login (paso siguiente)
      .build()
  )
  ```
- En `FirebaseService.login` (después de `setAuthenticated`): `Purchases.shared.logIn(uid)`.
- En `logout`: `Purchases.shared.logOut()`.
- Sustituir el bloque "Próximamente" en `SubscriptionView.swift` por una vista que llame a `Purchases.shared.getOfferings()` y muestre los packages, con un botón que llame a `Purchases.shared.purchase(package:)`.
- Suscribirse a `Purchases.shared.customerInfoStream` para mantener `appState.isPremium = customerInfo.entitlements["pro"]?.isActive == true`. **Importante:** este flag pasa a ser solo informativo (UI). El backend sigue siendo la única fuente de verdad para `businesses.planTier`.

### 2.2 Android — añadir SDK Purchases
- `app/build.gradle.kts`:
  ```kotlin
  implementation("com.revenuecat.purchases:purchases:8.10.+")
  implementation("com.revenuecat.purchases:purchases-ui:8.10.+")
  ```
- En `EatOutApp.kt` (`Application.onCreate`):
  ```kotlin
  Purchases.logLevel = LogLevel.INFO
  Purchases.configure(PurchasesConfiguration.Builder(this, "goog_XXXXXX").build())
  ```
- En `FirebaseService.login` (post-success): `Purchases.sharedInstance.logIn(uid)`.
- En `logout`: `Purchases.sharedInstance.logOut()`.
- Sustituir el bloque "Próximamente" en `SubscriptionScreen.kt` por la `PaywallActivityLauncher` o un Composable propio que invoque `Purchases.sharedInstance.purchase(...)`.
- Observar `Purchases.sharedInstance.customerInfo` y mantener `_isPremium` solo para UI.

### 2.3 Cloud Function — webhook RevenueCat → Firestore
Nuevo callable HTTP en `functions/index.js`:

```js
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

const RC_WEBHOOK_SECRET = defineSecret("REVENUECAT_WEBHOOK_SECRET");

exports.revenuecatWebhook = onRequest(
  { secrets: [RC_WEBHOOK_SECRET], region: "europe-west1" },
  async (req, res) => {
    const auth = req.header("Authorization") || "";
    if (auth !== `Bearer ${RC_WEBHOOK_SECRET.value()}`) {
      res.status(401).send("unauthorized");
      return;
    }
    const event = req.body?.event;
    if (!event) { res.status(400).send("bad request"); return; }

    const uid = event.app_user_id;            // RevenueCat = Firebase UID
    const isActive = ["INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION"]
      .includes(event.type);
    const isCanceled = ["CANCELLATION", "EXPIRATION", "BILLING_ISSUE"]
      .includes(event.type);

    const db = admin.firestore();
    const planTier = isActive ? "pro" : (isCanceled ? "free" : null);
    if (planTier !== null) {
      await db.collection("businesses").doc(uid).set(
        { planTier, isHighlighted: isActive, updatedAt: new Date().toISOString() },
        { merge: true }
      );
      await db.collection("subscriptions").doc(uid).set({
        businessId: uid,
        type: event.product_id,
        status: planTier,
        eventType: event.type,
        purchasedAt: event.purchased_at_ms ? new Date(event.purchased_at_ms).toISOString() : null,
        expiresAt: event.expiration_at_ms ? new Date(event.expiration_at_ms).toISOString() : null,
      }, { merge: true });
    }
    res.status(200).send("ok");
  }
);
```

Setear el secret antes del primer deploy:
```bash
firebase functions:secrets:set REVENUECAT_WEBHOOK_SECRET
```

## 3. Migración de datos existentes
- Buscar comercios con `planTier == "pro"` que **no** tengan suscripción real (los que se autoupgradearon con el bug viejo): `db.collection("businesses").where("planTier", "==", "pro")`.
- Decisión de producto: ¿se les revoca? Recomendación: revocarlos a `free` antes del lanzamiento real, anunciarlo, y darles un descuento de bienvenida vía RevenueCat promo offer.

## 4. Testing
- iOS: usar sandbox testers + StoreKit Configuration File local en Xcode.
- Android: usar tester accounts en Internal testing, comprar con tarjetas de test.
- Probar end-to-end: compra → webhook → Firestore → cliente refresca y aparece como Pro.
- Probar cancelación, renovación, billing failure, restore purchases.
