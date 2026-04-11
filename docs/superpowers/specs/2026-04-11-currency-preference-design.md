# Currency Preference — Design Spec

**Date:** 2026-04-11
**Scope:** Let a user pick a preferred display currency from the profile. Menu prices (stored in EUR) are shown with an approximate conversion to that currency in the menu detail and the price filter. The merchant still operates in EUR; the conversion is informational.
**Target audience context:** Zampa is a Spain/Europe-focused marketplace. Tourists from the US, UK, Japan, Switzerland, Scandinavia, Canada, and Australia are the secondary audience. Spanish/European users must see zero UX change unless they opt in.

---

## 1. Goals and non-goals

### Goals
- Add a "Moneda" row in the profile Preferencias section, listing a fixed set of 10 supported currencies.
- Show an approximate converted price in `MenuDetailView` / `MenuDetailScreen` as a secondary line below the main EUR price, when the selected currency is not EUR.
- Show a converted hint under the price slider in `FilterView` / `FilterScreen`, when the selected currency is not EUR.
- Keep EUR as the default for everyone. A user who never touches the setting never sees any currency-related UI change.
- Keep rates fresh via a daily Cloud Function backed by `frankfurter.app` (free, no key, ECB data).
- Work offline / cold-start via an embedded rate snapshot so conversions always render something.
- Sync the selection across devices via Firestore, reusing the existing `users/{uid}` document.

### Non-goals
- Letting merchants set prices in currencies other than EUR.
- Real-time mid-market rates (daily refresh is enough for a rounding hint).
- Multi-currency bill / receipt generation.
- A currency converter calculator UI.
- Automatic locale-based detection of the default currency. EUR is always the default; the user opts in manually.
- Automated tests (project has no test suite — manual verification only, consistent with prior features).
- Mirror / duplicate persistence in `UserDefaults` / `SharedPreferences`. Firestore is the single source of truth for the selection.

---

## 2. Data model

### 2.1 New singleton Firestore document: `config/exchangeRates`

| Field | Type | Semantics |
|---|---|---|
| `base` | `String` | Always `"EUR"`. |
| `rates` | `Map<String, Number>` | One entry per supported non-EUR currency: `{ USD: 1.09, GBP: 0.85, JPY: 158.3, CHF: 0.96, SEK: 11.42, NOK: 11.78, DKK: 7.46, CAD: 1.48, AUD: 1.63 }`. EUR is implicit (factor 1) and NOT present in the map. |
| `updatedAt` | `Timestamp` | Server timestamp written on every refresh. |

Read by any authenticated user. Written only by Cloud Functions (admin credentials bypass rules).

### 2.2 New optional field on `users/{uid}`

| Field | Type | Semantics |
|---|---|---|
| `currencyPreference` | `String` \| absent | ISO 4217 code (`EUR`, `USD`, `GBP`, `JPY`, `CHF`, `SEK`, `NOK`, `DKK`, `CAD`, `AUD`). Absent ⇒ EUR. |

The client never writes anything other than one of the 10 supported codes. Firestore rules validate this (see Section 4).

### 2.3 No changes to `dailyOffers`

Prices remain in EUR (`priceTotal: Double`, `currency: "EUR"`). All conversion is client-side.

### 2.4 Supported currencies (ordered for the picker)

1. `EUR` — Euro (€)
2. `USD` — Dólar estadounidense ($)
3. `GBP` — Libra esterlina (£)
4. `JPY` — Yen japonés (¥)
5. `CHF` — Franco suizo (CHF)
6. `SEK` — Corona sueca (kr)
7. `NOK` — Corona noruega (kr)
8. `DKK` — Corona danesa (kr)
9. `CAD` — Dólar canadiense (C$)
10. `AUD` — Dólar australiano (A$)

---

## 3. Backend — `refreshExchangeRates` Cloud Function

### 3.1 Definition

Added to `functions/index.js` alongside `expireMenus`, `onMenuPublished`, and `purgeDeletedAccounts`. Node.js 22, 2nd-gen.

- **Type:** scheduled function.
- **Schedule:** `every day 05:00` Europe/Madrid.
- **Timeout:** 60 seconds (fetch + write is fast).
- **Source:** `https://api.frankfurter.app/latest?from=EUR&to=USD,GBP,JPY,CHF,SEK,NOK,DKK,CAD,AUD`.
- **No API key.** Frankfurter is free, backed by the ECB daily reference rates.

### 3.2 Algorithm

```
1. HTTP GET the frankfurter URL. 10s timeout.
2. Parse the JSON response — it looks like:
   { amount: 1, base: "EUR", date: "2026-04-11",
     rates: { USD: 1.09, GBP: 0.85, ... } }
3. Sanity-check: all 9 expected keys are present and are positive finite numbers.
4. Write config/exchangeRates with:
   { base: "EUR", rates: <response.rates>, updatedAt: serverTimestamp() }
5. Log success with the list of rates written.
```

### 3.3 Error handling

- Network error / timeout / HTTP non-2xx → `logger.error(...)`, **do not touch the existing doc**. Yesterday's rates are still good enough for a hint.
- Response schema invalid (missing key, NaN) → same: log and abort.
- Writing to Firestore fails → log, rethrow, Cloud Functions retry handles it on the next scheduled run.

### 3.4 Seed script

`functions/seed-exchange-rates.js` — same fetch + write logic as the Cloud Function, but runs once from the developer's machine with `node functions/seed-exchange-rates.js`. Needed after the first deployment so the doc exists before 05:00 the next day.

Uses Firebase Admin SDK (same pattern as existing `seed-bots.js`).

### 3.5 No callable variant in v1

A `refreshExchangeRatesNow` HTTPS callable for manual refresh is out of scope. `firebase functions:shell → refreshExchangeRates()` is enough for support/debugging.

---

## 4. Firestore rules

Two new rule blocks added to `firebase/firestore.rules`:

### 4.1 `/config/{docId}` — public read, no client writes

```
match /config/{docId} {
  allow read: if isAuth();
  allow write: if false;  // Only Cloud Functions / admin
}
```

### 4.2 `/users/{userId}` — validate `currencyPreference`

The existing `allow update` rule already prevents role changes and enforces the deletion-flow shapes. Extend it to also validate that the new `currencyPreference` field, if present, is one of the supported codes:

```
allow update: if isOwner(userId)
  && unchanged('role')
  && (
    (request.resource.data.deletedAt is timestamp
      && request.resource.data.scheduledPurgeAt is timestamp)
    ||
    (!('deletedAt' in request.resource.data)
      && !('scheduledPurgeAt' in request.resource.data))
    ||
    (!('deletedAt' in resource.data) || resource.data.deletedAt == null)
  )
  && (!('currencyPreference' in request.resource.data)
      || request.resource.data.currencyPreference in
         ['EUR','USD','GBP','JPY','CHF','SEK','NOK','DKK','CAD','AUD']);
```

This blocks an attacker from writing `users/{self}.currencyPreference = "<anything>"`.

---

## 5. Client — `CurrencyService`

A single shared service on each platform, responsible for loading rates, caching them for the session, and exposing `convert()` and `format()`. Does not own the user's preference (that lives on `AppState` / `AuthViewModel` like other user fields).

### 5.1 iOS — `Zampa-iOS/Zampa/Core/CurrencyService.swift`

```swift
import Foundation
import FirebaseFirestore

struct ExchangeRates {
    let base: String
    let rates: [String: Double]
    let updatedAt: Date
}

@MainActor
final class CurrencyService: ObservableObject {
    static let shared = CurrencyService()

    @Published private(set) var rates: ExchangeRates?

    /// ISO codes supported in v1, ordered for the picker.
    static let supported: [String] = [
        "EUR", "USD", "GBP", "JPY", "CHF",
        "SEK", "NOK", "DKK", "CAD", "AUD"
    ]

    /// Embedded snapshot for cold start / offline.
    /// Updated manually on each release (copy-paste from a frankfurter response).
    static let fallbackRates: [String: Double] = [
        "USD": 1.09, "GBP": 0.85, "JPY": 158.3, "CHF": 0.96,
        "SEK": 11.42, "NOK": 11.78, "DKK": 7.46, "CAD": 1.48, "AUD": 1.63
    ]

    private var hasLoaded = false

    /// Reads config/exchangeRates once per session. Silently fails if offline.
    func loadIfNeeded() async { ... }

    /// Converts an EUR amount to `code`. Returns nil for unknown codes.
    /// EUR → EUR returns the input unchanged.
    func convert(eurAmount: Double, to code: String) -> Double? {
        if code == "EUR" { return eurAmount }
        let rate = rates?.rates[code] ?? Self.fallbackRates[code]
        guard let rate else { return nil }
        return eurAmount * rate
    }

    /// "12,50 €" / "$13.60 USD" / "¥1980 JPY" etc.
    /// JPY uses 0 decimals; every other supported currency uses 2.
    static func format(amount: Double, code: String) -> String { ... }
}
```

### 5.2 Android — `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/CurrencyService.kt`

Hilt `@Singleton`, injected into ViewModels that need it (`AuthViewModel`, `FeedViewModel`, `MenuDetailViewModel`). Same shape:

```kotlin
data class ExchangeRates(
    val base: String,
    val rates: Map<String, Double>,
    val updatedAt: com.google.firebase.Timestamp,
)

@Singleton
class CurrencyService @Inject constructor(
    private val db: FirebaseFirestore,
) {
    companion object {
        val supported = listOf(
            "EUR", "USD", "GBP", "JPY", "CHF",
            "SEK", "NOK", "DKK", "CAD", "AUD"
        )

        val fallbackRates = mapOf(
            "USD" to 1.09, "GBP" to 0.85, "JPY" to 158.3, "CHF" to 0.96,
            "SEK" to 11.42, "NOK" to 11.78, "DKK" to 7.46,
            "CAD" to 1.48, "AUD" to 1.63
        )

        fun format(amount: Double, code: String): String = ...
    }

    private val _rates = MutableStateFlow<ExchangeRates?>(null)
    val rates: StateFlow<ExchangeRates?> = _rates

    private var hasLoaded = false

    suspend fun loadIfNeeded() { ... }

    fun convert(eurAmount: Double, to: String): Double? {
        if (to == "EUR") return eurAmount
        val rate = _rates.value?.rates?.get(to) ?: fallbackRates[to] ?: return null
        return eurAmount * rate
    }
}
```

Provided by `di/AppModule.kt`:
```kotlin
@Provides @Singleton
fun provideCurrencyService(db: FirebaseFirestore): CurrencyService = CurrencyService(db)
```

### 5.3 Session caching

- `loadIfNeeded()` short-circuits on `hasLoaded == true`. One Firestore read per cold start, nothing after.
- No disk cache. The fallback snapshot handles the pre-first-read case; after the first read the in-memory value is used for the rest of the session.
- On logout the service is NOT reset. Rates are not personal data; they're shared across users. Leaving them cached saves a read on the next user's login.

### 5.4 Formatting rules

| Code | Decimals | Symbol placement | Example |
|---|---|---|---|
| `EUR` | 2 | suffix space | `12,50 €` |
| `USD` | 2 | prefix | `$13.60 USD` |
| `GBP` | 2 | prefix | `£10.63 GBP` |
| `JPY` | 0 | prefix | `¥1980 JPY` |
| `CHF` | 2 | suffix space | `12.00 CHF` |
| `SEK` | 2 | suffix space | `142.75 kr SEK` |
| `NOK` | 2 | suffix space | `147.25 kr NOK` |
| `DKK` | 2 | suffix space | `93.25 kr DKK` |
| `CAD` | 2 | prefix | `C$18.50 CAD` |
| `AUD` | 2 | prefix | `A$20.38 AUD` |

The ISO code is always appended (even for USD/GBP) to disambiguate Nordic crowns and the various "dollar" currencies.

EUR decimal separator follows Spanish convention (`12,50 €` with a comma). Non-EUR uses the dot (`$13.60 USD`) since the audience is international.

---

## 6. User preference flow

### 6.1 Loading the preference

On successful login or session resume:

- **iOS `FirebaseService.getCurrentUser()`** already parses the user doc. Add:
  ```swift
  let currencyPreference = data["currencyPreference"] as? String ?? "EUR"
  ```
  Add `currencyPreference: String` to the `User` model (non-optional, defaults to `"EUR"` when absent).

- **Android `FirebaseService.getUserProfile(uid)`** equivalent:
  ```kotlin
  val currencyPreference = data["currencyPreference"] as? String ?: "EUR"
  ```
  Add `currencyPreference: String = "EUR"` to `data class User`.

### 6.2 Updating the preference

- **iOS `FirebaseService.updateCurrencyPreference(_ code: String)`**:
  ```swift
  func updateCurrencyPreference(_ code: String) async throws {
      guard let uid = currentFirebaseUser?.uid else {
          throw FirebaseServiceError.notAuthenticated
      }
      guard CurrencyService.supported.contains(code) else {
          throw FirebaseServiceError.invalidInput
      }
      try await db.collection("users").document(uid).updateData([
          "currencyPreference": code
      ])
  }
  ```

- **Android `FirebaseService.updateCurrencyPreference(code: String)`**: mirror with `require(code in CurrencyService.supported)` and `.update("currencyPreference", code).await()`.

After each call, the caller refreshes the local `User` and updates the observable state (iOS `appState.currentUser`, Android `AuthViewModel._currentUser`) so the new currency propagates immediately to every screen observing the user.

### 6.3 Kicking off `loadIfNeeded()`

- **iOS**: in `AppState.checkAuthenticationStatus()`, after a successful `getCurrentUser()`, fire `Task { await CurrencyService.shared.loadIfNeeded() }`. Also fire it from `AppState.setAuthenticated(user:)` for the fresh-login path.
- **Android**: in `AuthViewModel.routePostLogin(user)`, on the active-account branch, call `viewModelScope.launch { currencyService.loadIfNeeded() }`.

These fire once per session. The user sees the embedded fallback until Firestore responds (typically sub-second).

---

## 7. UI changes

### 7.1 Profile — new "Moneda" row

- **Location**: inside the existing "Preferencias" section, between "Notificaciones" and "Tema".
- **Icon**: `dollarsign.circle.fill` (iOS) / `Icons.Default.AttachMoney` (Android), with the same accent color style used by sibling rows.
- **Title**: "Moneda".
- **Trailing**: shows the current selection — `EUR (€)`, `USD ($)`, etc. — followed by the chevron.
- **Tap**: navigates to `CurrencyPreferenceView` (iOS) / `CurrencyPreferenceScreen` (Android).

### 7.2 Currency picker screen

Full-screen pushed view with:

- Nav title: "Moneda".
- 10 list rows, one per supported currency, in the order from Section 2.4.
- Each row: flag emoji + bold code + Spanish name + symbol + trailing checkmark on the currently selected row.
- Example row: `🇺🇸  USD  Dólar estadounidense        $   ✓` (checkmark only on the active one).
- Tapping a row calls `updateCurrencyPreference(code)`, updates local state, and pops back to profile. A tiny spinner replaces the checkmark during the write (< 500 ms typical).
- Error handling: if the write fails, show an inline error row above the list and keep the selection on the previous value. No alert — less intrusive.

Flags are emoji (`🇪🇺 🇺🇸 🇬🇧 🇯🇵 🇨🇭 🇸🇪 🇳🇴 🇩🇰 🇨🇦 🇦🇺`) — zero asset overhead, renders on every device. `🇪🇺` for the EU flag on the EUR row.

### 7.3 `MenuDetailView` / `MenuDetailScreen` — secondary price line

In the price block of the menu hero:

- **When `currentUser.currencyPreference == "EUR"`** (the default for most users): render exactly as today, one line: `12,50 €`.
- **When `currentUser.currencyPreference` is anything else**: render a `VStack` / `Column` with:
  - Line 1 (unchanged, bold primary): `12,50 €`
  - Line 2 (smaller, secondary text color, with a leading `~` to emphasize approximation): `~$13.60 USD`

No padding is added on the EUR path — the layout collapses naturally to the single line.

### 7.4 `FilterView` / `FilterScreen` — slider hint

Inside the price filter block:

- Existing label `Hasta 15 €` stays bold and primary.
- Below it, when `currencyPreference != "EUR"`, a thin secondary line: `~$16.30 USD`.
- The slider remains in EUR (steps in `Double` over 5..100), because: (a) users ultimately pay in EUR, (b) slider granularity in non-EUR currencies would drift, (c) the menu doc field is in EUR and the filter is a client-side numeric comparison.

### 7.5 Cards in the feed — unchanged

`FeedView` / `FeedScreen` cards keep `12,50€` as the only price element. No secondary line in the grid. Conversion only appears when the user opens the detail page. This is deliberate — grid density matters more than discoverability, and the menu detail is one tap away.

### 7.6 Other screens — unchanged

- Onboarding, auth, merchant dashboard, create/edit menu, stats, push notifications: no currency references.
- Merchant-facing views always work in EUR; the merchant is the price authority.

---

## 8. Implementation order (for the plan)

1. Cloud Function `refreshExchangeRates` + `seed-exchange-rates.js`. Deploy and seed so the doc exists.
2. Firestore rules for `/config/{docId}` and the `currencyPreference` validator on `/users/{userId}`. Deploy.
3. iOS `User` model + `getCurrentUser` parse + `updateCurrencyPreference` on `FirebaseService`.
4. iOS `CurrencyService.swift` + register in `project.pbxproj`.
5. iOS `AppState` hooks `loadIfNeeded()` on session start.
6. iOS `CurrencyPreferenceView` + register in `project.pbxproj`.
7. iOS Profile row + MenuDetailView secondary line + FilterView slider hint.
8. Android `User` model + `getUserProfile` parse + `updateCurrencyPreference` on `FirebaseService`.
9. Android `CurrencyService.kt` + Hilt provide in `AppModule`.
10. Android `AuthViewModel` hooks `loadIfNeeded()` on route-post-login.
11. Android `CurrencyPreferenceScreen` + Navigation route + MainScreen wire.
12. Android ProfileScreen row + MenuDetailScreen secondary line + FeedScreen FilterView slider hint.
13. Manual end-to-end verification on both platforms.

---

## 9. Files affected

### Backend
**New:**
- `functions/seed-exchange-rates.js`

**Modified:**
- `functions/index.js` — new `refreshExchangeRates` scheduled function.
- `firebase/firestore.rules` — `/config/{docId}` block + `currencyPreference` validator on `/users/{userId}`.

### iOS
**New:**
- `Zampa-iOS/Zampa/Core/CurrencyService.swift`
- `Zampa-iOS/Zampa/Features/Profile/CurrencyPreferenceView.swift`

**Modified:**
- `Zampa-iOS/Zampa.xcodeproj/project.pbxproj` — register both new files.
- `Zampa-iOS/Zampa/Core/Models/User.swift` — add `currencyPreference: String` (default `"EUR"`).
- `Zampa-iOS/Zampa/Services/FirebaseService.swift` — parse field in `getCurrentUser`, add `updateCurrencyPreference`.
- `Zampa-iOS/Zampa/Core/AppState.swift` — trigger `CurrencyService.shared.loadIfNeeded()`.
- `Zampa-iOS/Zampa/Features/Profile/ProfileView.swift` — new Preferencias row.
- `Zampa-iOS/Zampa/Features/Feed/MenuDetailView.swift` — secondary price line.
- `Zampa-iOS/Zampa/Features/Feed/FilterView.swift` — slider hint.

### Android
**New:**
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/CurrencyService.kt`
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/CurrencyPreferenceScreen.kt`

**Modified:**
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt` — add `currencyPreference` to `User`.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt` — parse + `updateCurrencyPreference`.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/di/AppModule.kt` — `@Provides` for `CurrencyService`.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt` — trigger `loadIfNeeded()`, expose `updateCurrencyPreference` VM method.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt` — `Route.CurrencyPreference` + composable.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt` — wire the navigation callback.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt` — new Preferencias row + new lambda parameter.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/MenuDetailScreen.kt` — secondary price line.
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/FeedScreen.kt` — slider hint inside the embedded `FilterView`.

---

## 10. Testing strategy

Consistent with prior features (no automated tests in the repo).

- **Cloud Function:** run `firebase functions:shell` → `refreshExchangeRates()` → verify `config/exchangeRates` is written with the expected keys and `updatedAt`. Also verify the fallback path by temporarily pointing the URL at something invalid and confirming the doc is untouched.
- **Seed script:** run `node functions/seed-exchange-rates.js` once against a clean project and confirm the doc appears.
- **Firestore rules:** verify in the Firebase Console Rules Playground that:
  - `users/{self}.currencyPreference = "USD"` is allowed.
  - `users/{self}.currencyPreference = "BTC"` is denied.
  - `users/{other}.currencyPreference = "USD"` is denied.
  - `config/exchangeRates` read is allowed for authenticated users.
  - `config/exchangeRates` write is denied from clients.
- **iOS / Android:** manual E2E on both:
  1. Open profile → tap Moneda → see the 10 rows with EUR marked.
  2. Tap USD → row shows a brief spinner, then checkmark jumps to USD, pops to profile, row shows `USD ($)`.
  3. Open any menu detail → see `12,50 €` primary and `~$13.60 USD` secondary.
  4. Back to feed → tap the filter → slider label still `Hasta 15 €` with `~$16.30 USD` hint below.
  5. Change back to EUR → menu detail secondary line disappears; filter hint disappears.
  6. Force offline → kill Firestore → relaunch app → verify conversions still render using the embedded fallback (modulo a small rate drift).
