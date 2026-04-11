# Currency Preference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users pick a preferred display currency from the profile. Menu prices (stored in EUR) are shown with an approximate conversion to that currency in menu detail and price filter, backed by a daily-refreshed exchange rate table.

**Architecture:** A new scheduled Cloud Function `refreshExchangeRates` pulls daily rates from `frankfurter.app` (free, no key) into `config/exchangeRates`. A client-side `CurrencyService` (iOS `ObservableObject`, Android Hilt singleton) loads those rates once per session with an embedded fallback snapshot for cold starts. Each `users/{uid}` doc gains a `currencyPreference: String` field (default `EUR`). UI picker lives in profile preferences; menu detail and filter view show an optional converted secondary line when the preference is non-EUR.

**Tech Stack:** Swift / SwiftUI (iOS), Kotlin / Jetpack Compose + Hilt (Android), Node.js 22 + Firebase Functions 2nd gen, Firestore, Firebase Remote Config not used (we use Firestore as our rate store).

**Related spec:** `docs/superpowers/specs/2026-04-11-currency-preference-design.md`

**Note on testing:** The project has no automated test suite. Each task ends in a compile check and a manual verification checklist, consistent with prior features in this repo.

---

## Task 1: Cloud Function `refreshExchangeRates`

**Files:**
- Modify: `functions/index.js`

- [ ] **Step 1: Append the scheduled function to `functions/index.js`**

Append the following block at the very END of `functions/index.js`, after the existing `deleteQueryInBatches` helper (the current last function in the file). All imports it needs (`admin`, `logger`, `onSchedule`) are already present at the top; do not duplicate.

```javascript
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
            const resp = await fetch(url);
            if (!resp.ok) {
                logger.error(`refreshExchangeRates: HTTP ${resp.status} ${resp.statusText}`);
                return;
            }
            payload = await resp.json();
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
```

- [ ] **Step 2: Syntax check**

Run from repo root:
```bash
node --check functions/index.js && echo "syntax ok"
```

Expected: `syntax ok`.

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add functions/index.js && git commit -m "$(cat <<'EOF'
Add refreshExchangeRates scheduled Cloud Function

Fetches daily EUR-based rates for 9 supported currencies from
frankfurter.app (ECB data, free, no API key) and writes them to
config/exchangeRates. On any failure the previous document is
preserved so clients always see usable rates.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Seed script `functions/seed-exchange-rates.js`

**Files:**
- Create: `functions/seed-exchange-rates.js`

- [ ] **Step 1: Create the seed script**

Create the file with:

```javascript
#!/usr/bin/env node
/**
 * Seed único de config/exchangeRates.
 *
 * Uso:
 *   node seed-exchange-rates.js
 *
 * Se ejecuta una sola vez tras el primer deploy para que el doc exista
 * antes de que la scheduled function corra a las 05:00 del día siguiente.
 *
 * Requiere:
 *   - Variables de entorno de functions/.env cargadas, O
 *   - `gcloud auth application-default login` ejecutado previamente.
 */

const admin = require("firebase-admin");

const SUPPORTED = ["USD", "GBP", "JPY", "CHF", "SEK", "NOK", "DKK", "CAD", "AUD"];

async function main() {
    admin.initializeApp();
    const db = admin.firestore();

    const url = `https://api.frankfurter.app/latest?from=EUR&to=${SUPPORTED.join(",")}`;
    console.log(`Fetching ${url}`);
    const resp = await fetch(url);
    if (!resp.ok) {
        console.error(`HTTP ${resp.status} ${resp.statusText}`);
        process.exit(1);
    }
    const payload = await resp.json();
    const rates = payload?.rates;
    if (!rates || typeof rates !== "object") {
        console.error("Respuesta sin 'rates':", payload);
        process.exit(2);
    }

    const clean = {};
    for (const code of SUPPORTED) {
        const value = rates[code];
        if (typeof value !== "number" || !isFinite(value) || value <= 0) {
            console.error(`Tasa inválida para ${code}:`, value);
            process.exit(3);
        }
        clean[code] = value;
    }

    await db.collection("config").doc("exchangeRates").set({
        base: "EUR",
        rates: clean,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    console.log("OK — config/exchangeRates escrito:");
    console.log(clean);
}

main().catch(e => {
    console.error("Error:", e);
    process.exit(99);
});
```

- [ ] **Step 2: Syntax check**

```bash
node --check functions/seed-exchange-rates.js && echo "syntax ok"
```

Expected: `syntax ok`.

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add functions/seed-exchange-rates.js && git commit -m "$(cat <<'EOF'
Add seed-exchange-rates.js for first-time config/exchangeRates bootstrap

Writes the exchangeRates doc once so clients have data before the
daily scheduled refresh runs. Run after the first deploy.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Firestore rules — `/config/` + `currencyPreference` validator

**Files:**
- Modify: `firebase/firestore.rules`

- [ ] **Step 1: Add the `/config/{docId}` block**

Inside the `match /databases/{database}/documents { ... }` block, AFTER the existing `match /cuisineTypes/{typeId}` block and BEFORE `match /reports/{reportId}`, insert a new block:

```
    // ── Config singleton docs (exchangeRates, future config entries) ──
    // Lectura pública para cualquier usuario autenticado. Sólo Cloud
    // Functions escriben (admin bypass).
    match /config/{docId} {
      allow read: if isAuth();
      allow write: if false;
    }
```

- [ ] **Step 2: Extend the `/users/{userId}` update rule with currencyPreference validation**

In `firebase/firestore.rules`, find the `allow update` block inside `match /users/{userId} { ... }` (added in the account deletion feature). It currently looks like:

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
        );
```

Replace with (adding a new `&&` clause that validates `currencyPreference`):

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

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add firebase/firestore.rules && git commit -m "$(cat <<'EOF'
Firestore rules: /config read + currencyPreference validator on /users

- /config/{docId}: authenticated read, no client writes (Cloud Functions
  bypass the rule via admin credentials).
- /users/{userId}.update: if the write includes currencyPreference, it
  must be one of the 10 supported ISO codes. Blocks attackers from
  writing arbitrary strings into their own user doc.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: iOS — `User` model + `FirebaseService` currency parsing and update

**Files:**
- Modify: `Zampa-iOS/Zampa/Core/Models/User.swift`
- Modify: `Zampa-iOS/Zampa/Services/FirebaseService.swift`

- [ ] **Step 1: Add `currencyPreference` to `User` struct**

Replace the entire contents of `Zampa-iOS/Zampa/Core/Models/User.swift` with:

```swift
import Foundation

/// Alias para evitar colisión con FirebaseAuth.User sin depender del module name
typealias AppUser = User

/// Modelo de usuario de la aplicación
struct User: Codable, Identifiable {
    let id: String
    let email: String
    let name: String
    let role: UserRole
    let phone: String?
    let photoUrl: String?
    /// Fecha en la que el usuario solicitó la eliminación de su cuenta.
    /// Si es no-nil, la cuenta está en periodo de gracia hasta `scheduledPurgeAt`.
    let deletedAt: Date?
    /// Fecha programada para el purgado definitivo (deletedAt + 30 días).
    let scheduledPurgeAt: Date?
    /// Código ISO 4217 de la moneda preferida para mostrar precios.
    /// Default `"EUR"` cuando el campo no está presente en Firestore.
    let currencyPreference: String

    init(
        id: String,
        email: String,
        name: String,
        role: UserRole,
        phone: String? = nil,
        photoUrl: String? = nil,
        deletedAt: Date? = nil,
        scheduledPurgeAt: Date? = nil,
        currencyPreference: String = "EUR"
    ) {
        self.id = id
        self.email = email
        self.name = name
        self.role = role
        self.phone = phone
        self.photoUrl = photoUrl
        self.deletedAt = deletedAt
        self.scheduledPurgeAt = scheduledPurgeAt
        self.currencyPreference = currencyPreference
    }

    enum UserRole: String, Codable {
        case cliente = "CLIENTE"
        case comercio = "COMERCIO"
    }
}
```

- [ ] **Step 2: Parse `currencyPreference` in `getCurrentUser()`**

In `Zampa-iOS/Zampa/Services/FirebaseService.swift`, replace the `getCurrentUser()` method (currently around lines 24-43) with:

```swift
    /// Lee el perfil completo del usuario desde Firestore
    func getCurrentUser() async throws -> AppUser? {
        guard let fbUser = currentFirebaseUser else { return nil }

        let doc = try await db.collection("users").document(fbUser.uid).getDocument()
        guard let data = doc.data() else { return nil }

        let roleString = data["role"] as? String ?? "CLIENTE"
        let role = AppUser.UserRole(rawValue: roleString) ?? .cliente

        return AppUser(
            id: fbUser.uid,
            email: data["email"] as? String ?? fbUser.email ?? "",
            name: data["name"] as? String ?? fbUser.displayName ?? "Usuario",
            role: role,
            phone: data["phone"] as? String,
            photoUrl: data["photoUrl"] as? String,
            deletedAt: (data["deletedAt"] as? Timestamp)?.dateValue(),
            scheduledPurgeAt: (data["scheduledPurgeAt"] as? Timestamp)?.dateValue(),
            currencyPreference: data["currencyPreference"] as? String ?? "EUR"
        )
    }
```

- [ ] **Step 3: Add `updateCurrencyPreference(_:)`**

In `Zampa-iOS/Zampa/Services/FirebaseService.swift`, find the existing `cancelAccountDeletion()` method (it was added near `updateUserName` in the account deletion feature). Immediately AFTER the closing brace of `cancelAccountDeletion()`, add:

```swift
    // MARK: - Currency preference

    /// Actualiza la moneda preferida del usuario en Firestore.
    /// El caller debe refrescar `appState.currentUser` tras el éxito para
    /// que la UI observe el cambio.
    func updateCurrencyPreference(_ code: String) async throws {
        guard let uid = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        let supported = ["EUR", "USD", "GBP", "JPY", "CHF", "SEK", "NOK", "DKK", "CAD", "AUD"]
        guard supported.contains(code) else {
            throw NSError(
                domain: "FirebaseService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Código de moneda no soportado: \(code)"]
            )
        }
        try await db.collection("users").document(uid).updateData([
            "currencyPreference": code
        ])
    }
```

- [ ] **Step 4: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-iOS/Zampa/Core/Models/User.swift Zampa-iOS/Zampa/Services/FirebaseService.swift && git commit -m "$(cat <<'EOF'
iOS: add currencyPreference to User model and FirebaseService

New field on User (default "EUR" when absent), parsed in getCurrentUser,
and a new updateCurrencyPreference(_:) method that validates the code
against the 10 supported currencies before writing to Firestore.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: iOS — `CurrencyService.swift`

**Files:**
- Create: `Zampa-iOS/Zampa/Core/CurrencyService.swift`
- Modify: `Zampa-iOS/Zampa.xcodeproj/project.pbxproj`

- [ ] **Step 1: Create the service**

Create `/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa/Core/CurrencyService.swift`:

```swift
import Foundation
import FirebaseFirestore

/// Tasas de cambio en memoria. Base es siempre EUR. `rates[code]` es
/// cuántas unidades de `code` equivalen a 1 EUR.
struct ExchangeRates {
    let base: String
    let rates: [String: Double]
    let updatedAt: Date
}

/// Servicio shared que carga las tasas de `config/exchangeRates` una vez
/// por sesión y expone helpers de conversión y formato.
@MainActor
final class CurrencyService: ObservableObject {
    static let shared = CurrencyService()

    @Published private(set) var rates: ExchangeRates?

    /// ISO codes soportados en v1 (orden para el picker).
    static let supported: [String] = [
        "EUR", "USD", "GBP", "JPY", "CHF",
        "SEK", "NOK", "DKK", "CAD", "AUD"
    ]

    /// Snapshot embebido como fallback cuando la primera lectura de
    /// Firestore aún no ha terminado (o no tiene conectividad).
    /// Actualizado a mano en cada release grande.
    static let fallbackRates: [String: Double] = [
        "USD": 1.09, "GBP": 0.85, "JPY": 158.3, "CHF": 0.96,
        "SEK": 11.42, "NOK": 11.78, "DKK": 7.46, "CAD": 1.48, "AUD": 1.63
    ]

    private var hasLoaded = false
    private let db = Firestore.firestore()

    private init() {}

    /// Dispara una sola vez por sesión. Si falla silenciosamente, el
    /// fallback embebido cubre todas las conversiones.
    func loadIfNeeded() async {
        guard !hasLoaded else { return }
        hasLoaded = true
        do {
            let doc = try await db.collection("config").document("exchangeRates").getDocument()
            guard let data = doc.data() else { return }
            guard let base = data["base"] as? String,
                  let ratesAny = data["rates"] as? [String: Any],
                  let ts = data["updatedAt"] as? Timestamp else {
                return
            }
            var parsed: [String: Double] = [:]
            for (key, value) in ratesAny {
                if let d = value as? Double {
                    parsed[key] = d
                } else if let n = value as? NSNumber {
                    parsed[key] = n.doubleValue
                }
            }
            self.rates = ExchangeRates(base: base, rates: parsed, updatedAt: ts.dateValue())
        } catch {
            print("CurrencyService.loadIfNeeded error:", error.localizedDescription)
        }
    }

    /// Convierte un importe en EUR al código destino. Retorna nil si el
    /// código no es conocido ni en memoria ni en fallback.
    func convert(eurAmount: Double, to code: String) -> Double? {
        if code == "EUR" { return eurAmount }
        let rate = rates?.rates[code] ?? Self.fallbackRates[code]
        guard let rate else { return nil }
        return eurAmount * rate
    }

    /// Formatea "12,50 €" / "$13.60 USD" / "¥1980 JPY" etc.
    /// JPY usa 0 decimales; el resto 2. EUR usa coma decimal (es_ES);
    /// el resto usa punto (en_US) por convención internacional.
    static func format(amount: Double, code: String) -> String {
        let isJPY = code == "JPY"
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = isJPY ? 0 : 2
        formatter.maximumFractionDigits = isJPY ? 0 : 2
        formatter.locale = code == "EUR" ? Locale(identifier: "es_ES") : Locale(identifier: "en_US")
        let numStr = formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"

        switch code {
        case "EUR": return "\(numStr) €"
        case "USD": return "$\(numStr) USD"
        case "GBP": return "£\(numStr) GBP"
        case "JPY": return "¥\(numStr) JPY"
        case "CHF": return "\(numStr) CHF"
        case "SEK": return "\(numStr) kr SEK"
        case "NOK": return "\(numStr) kr NOK"
        case "DKK": return "\(numStr) kr DKK"
        case "CAD": return "C$\(numStr) CAD"
        case "AUD": return "A$\(numStr) AUD"
        default:    return "\(numStr) \(code)"
        }
    }

    /// Helper ergonómico: convierte un precio en EUR a `code` y devuelve el
    /// string listo para mostrar. Si la conversión no es posible, devuelve nil.
    static func formatConverted(eurAmount: Double, to code: String) -> String? {
        guard let converted = CurrencyService.shared.convert(eurAmount: eurAmount, to: code) else {
            return nil
        }
        return format(amount: converted, code: code)
    }
}
```

- [ ] **Step 2: Register the new file in `project.pbxproj`**

Xcode tracks source files in `Zampa-iOS/Zampa.xcodeproj/project.pbxproj`. A file isn't compiled until it's listed in four places (PBXBuildFile, PBXFileReference, group children, Sources build phase).

Mirror the pattern used by an existing sibling file in `Zampa-iOS/Zampa/Core/` — `DesignSystem.swift` is a good reference. Grep for its 4 occurrences:

```bash
grep -n "DesignSystem.swift" "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa.xcodeproj/project.pbxproj"
```

Add four matching entries for `CurrencyService.swift`:

1. A new `PBXBuildFile` entry (pick a unique 24-char hex ID like `CS0011042500000000000001`).
2. A new `PBXFileReference` entry (pick another unique 24-char hex ID like `CS0011042500000000000002`).
3. The file reference added to the `Core` group's `children = (...)` array (next to DesignSystem.swift).
4. The build file ID added to the `Zampa` target's `Sources` phase `files = (...)` array.

Before picking IDs, grep to confirm they don't exist:
```bash
grep "CS001104250000000000000" "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa.xcodeproj/project.pbxproj" || echo "free to use"
```

After edits, validate the project file is still parseable:
```bash
plutil -lint "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa.xcodeproj/project.pbxproj"
```

Expected: `OK`.

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-iOS/Zampa/Core/CurrencyService.swift Zampa-iOS/Zampa.xcodeproj/project.pbxproj && git commit -m "$(cat <<'EOF'
iOS: add CurrencyService with Firestore rates loader and fallback

ObservableObject singleton that reads config/exchangeRates once per
session and caches the result in memory. Exposes convert(eurAmount:to:)
and format(amount:code:) plus a formatConverted helper. Includes an
embedded fallback snapshot so conversions still render during cold
starts or offline.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: iOS — `CurrencyPreferenceView.swift`

**Files:**
- Create: `Zampa-iOS/Zampa/Features/Profile/CurrencyPreferenceView.swift`
- Modify: `Zampa-iOS/Zampa.xcodeproj/project.pbxproj`

- [ ] **Step 1: Create the picker view**

Create `/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa/Features/Profile/CurrencyPreferenceView.swift`:

```swift
import SwiftUI

/// Catálogo estático de monedas soportadas para el picker.
private struct CurrencyOption: Identifiable {
    let code: String
    let flag: String
    let name: String
    let symbol: String
    var id: String { code }
}

private let currencyOptions: [CurrencyOption] = [
    .init(code: "EUR", flag: "🇪🇺", name: "Euro",                    symbol: "€"),
    .init(code: "USD", flag: "🇺🇸", name: "Dólar estadounidense",    symbol: "$"),
    .init(code: "GBP", flag: "🇬🇧", name: "Libra esterlina",          symbol: "£"),
    .init(code: "JPY", flag: "🇯🇵", name: "Yen japonés",              symbol: "¥"),
    .init(code: "CHF", flag: "🇨🇭", name: "Franco suizo",             symbol: "CHF"),
    .init(code: "SEK", flag: "🇸🇪", name: "Corona sueca",             symbol: "kr"),
    .init(code: "NOK", flag: "🇳🇴", name: "Corona noruega",           symbol: "kr"),
    .init(code: "DKK", flag: "🇩🇰", name: "Corona danesa",            symbol: "kr"),
    .init(code: "CAD", flag: "🇨🇦", name: "Dólar canadiense",         symbol: "C$"),
    .init(code: "AUD", flag: "🇦🇺", name: "Dólar australiano",        symbol: "A$"),
]

struct CurrencyPreferenceView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var pendingCode: String?
    @State private var errorMessage: String?

    private var selectedCode: String {
        appState.currentUser?.currencyPreference ?? "EUR"
    }

    var body: some View {
        List {
            if let err = errorMessage {
                Section {
                    Text(err)
                        .font(.appBody)
                        .foregroundColor(.red)
                }
            }
            Section {
                ForEach(currencyOptions) { option in
                    Button(action: { select(option.code) }) {
                        HStack(spacing: 12) {
                            Text(option.flag)
                                .font(.system(size: 24))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(option.code)
                                    .font(.appBody)
                                    .fontWeight(.semibold)
                                    .foregroundColor(.appTextPrimary)
                                Text(option.name)
                                    .font(.appCaption)
                                    .foregroundColor(.appTextSecondary)
                            }
                            Spacer()
                            Text(option.symbol)
                                .font(.appBody)
                                .foregroundColor(.appTextSecondary)
                            if pendingCode == option.code {
                                ProgressView()
                                    .controlSize(.small)
                            } else if option.code == selectedCode {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.appPrimary)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .disabled(pendingCode != nil)
                }
            } footer: {
                Text("Los precios siempre se cobran en euros. Esta opción sólo cambia cómo se muestran en la app.")
                    .font(.appCaption)
                    .foregroundColor(.appTextSecondary)
            }
        }
        .listStyle(InsetGroupedListStyle())
        .navigationTitle("Moneda")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func select(_ code: String) {
        guard code != selectedCode, pendingCode == nil else { return }
        pendingCode = code
        errorMessage = nil
        Task {
            do {
                try await FirebaseService.shared.updateCurrencyPreference(code)
                if let updated = try? await FirebaseService.shared.getCurrentUser() {
                    await MainActor.run {
                        appState.currentUser = updated
                        pendingCode = nil
                        dismiss()
                    }
                } else {
                    await MainActor.run {
                        pendingCode = nil
                        dismiss()
                    }
                }
            } catch {
                await MainActor.run {
                    pendingCode = nil
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

#Preview {
    NavigationView {
        CurrencyPreferenceView()
            .environmentObject(AppState())
    }
}
```

- [ ] **Step 2: Register the file in `project.pbxproj`**

Same 4-location registration as Task 5. Use the `Features/Profile/` group (where `ProfileView.swift`, `DietaryPreferencesView.swift` etc. live — they are your siblings to mirror). Pick fresh unique IDs like `CP0011042500000000000001` and `CP0011042500000000000002`. After editing, run:

```bash
plutil -lint "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa.xcodeproj/project.pbxproj"
```

Expected: `OK`.

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-iOS/Zampa/Features/Profile/CurrencyPreferenceView.swift Zampa-iOS/Zampa.xcodeproj/project.pbxproj && git commit -m "$(cat <<'EOF'
iOS: add CurrencyPreferenceView picker for the 10 supported currencies

Grouped list with flag + ISO code + Spanish name + symbol per row.
Tapping a row writes through FirebaseService.updateCurrencyPreference,
refreshes appState.currentUser, and pops back to profile. Inline error
row on write failure (no disruptive alert).

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: iOS — `AppState` hooks `CurrencyService.loadIfNeeded()`

**Files:**
- Modify: `Zampa-iOS/Zampa/Core/AppState.swift`

- [ ] **Step 1: Load rates after authentication resume**

In `Zampa-iOS/Zampa/Core/AppState.swift`, locate the Task inside `checkAuthenticationStatus()` (around lines 103-131). Inside the `if let user = try await FirebaseService.shared.getCurrentUser()` block, AFTER the existing `await MainActor.run { ... }` assignment and BEFORE the merchant branch, add a fire-and-forget call to load exchange rates.

Replace this:
```swift
        if FirebaseService.shared.isAuthenticated {
            Task {
                do {
                    if let user = try await FirebaseService.shared.getCurrentUser() {
                        await MainActor.run {
                            self.currentUser = user
                            self.isAuthenticated = true
                            self.isLoading = false
                            self.loadDietaryPreferences(for: user.id)
                            PushManager.shared.refreshTokenIfNeeded()
                        }

                        // Si es merchant, cargar perfil
                        if user.role == .comercio {
                            await loadMerchantProfile(merchantId: user.id)
                        }
                    } else {
```

With:
```swift
        if FirebaseService.shared.isAuthenticated {
            Task {
                do {
                    if let user = try await FirebaseService.shared.getCurrentUser() {
                        await MainActor.run {
                            self.currentUser = user
                            self.isAuthenticated = true
                            self.isLoading = false
                            self.loadDietaryPreferences(for: user.id)
                            PushManager.shared.refreshTokenIfNeeded()
                        }
                        // Carga tasas de cambio en background (fallback embebido
                        // cubre hasta que esta llamada termine).
                        Task { await CurrencyService.shared.loadIfNeeded() }

                        // Si es merchant, cargar perfil
                        if user.role == .comercio {
                            await loadMerchantProfile(merchantId: user.id)
                        }
                    } else {
```

- [ ] **Step 2: Also fire on fresh login**

In the same file, locate `setAuthenticated(user:)` (around lines 157-169). Currently:

```swift
    func setAuthenticated(user: User) {
        self.currentUser = user
        self.isAuthenticated = true
        self.isLoading = false
        loadDietaryPreferences(for: user.id)
        PushManager.shared.refreshTokenIfNeeded()

        if user.role == .comercio {
            Task {
                await loadMerchantProfile(merchantId: user.id)
            }
        }
    }
```

Replace with:

```swift
    func setAuthenticated(user: User) {
        self.currentUser = user
        self.isAuthenticated = true
        self.isLoading = false
        loadDietaryPreferences(for: user.id)
        PushManager.shared.refreshTokenIfNeeded()
        Task { await CurrencyService.shared.loadIfNeeded() }

        if user.role == .comercio {
            Task {
                await loadMerchantProfile(merchantId: user.id)
            }
        }
    }
```

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-iOS/Zampa/Core/AppState.swift && git commit -m "$(cat <<'EOF'
iOS: trigger CurrencyService.loadIfNeeded on session start and login

Once the user is authenticated (either via cached session on app
launch or a fresh login), kick off a background task to fetch the
latest exchange rates. The fallback snapshot covers any time before
the fetch completes.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: iOS — Profile row + navigation to the picker

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Profile/ProfileView.swift`

- [ ] **Step 1: Add the "Moneda" row to the Preferencias section**

In `Zampa-iOS/Zampa/Features/Profile/ProfileView.swift`, locate `preferencesSection` (around lines 104-129). It currently contains three rows: DietaryPreferences (NavigationLink), NotificationPreferences (NavigationLink), and the Tema HStack.

Replace the entire `preferencesSection` body with:

```swift
    // MARK: - Preferences section
    @ViewBuilder
    private var preferencesSection: some View {
        Section(header: Text("Preferencias").font(.caption).foregroundColor(.appTextSecondary)) {
            NavigationLink(destination: DietaryPreferencesView()) {
                ProfileMenuRowContent(icon: "leaf.fill", title: "Preferencias Alimentarias", color: .green)
            }
            NavigationLink(destination: NotificationPreferencesView()) {
                ProfileMenuRowContent(icon: "bell.fill", title: "Notificaciones", color: .orange)
            }
            NavigationLink(destination: CurrencyPreferenceView()) {
                HStack(spacing: 16) {
                    Image(systemName: "dollarsign.circle.fill")
                        .foregroundColor(.appPrimary)
                        .frame(width: 24)
                    Text("Moneda")
                        .font(.appBody)
                        .foregroundColor(.appTextPrimary)
                    Spacer()
                    Text(currentCurrencyLabel)
                        .font(.appCaption)
                        .foregroundColor(.appTextSecondary)
                }
            }
            HStack(spacing: 16) {
                Image(systemName: "circle.lefthalf.filled")
                    .foregroundColor(.purple)
                    .frame(width: 24)
                Text("Tema")
                    .font(.appBody)
                    .foregroundColor(.appTextPrimary)
                Spacer()
                Picker("", selection: $appState.appColorScheme) {
                    ForEach(ColorSchemePreference.allCases, id: \.self) { pref in
                        Text(pref.label).tag(pref)
                    }
                }
                .pickerStyle(.segmented)
                .frame(maxWidth: 180)
            }
        }
    }

    /// Etiqueta corta mostrada en la trailing de la fila Moneda: "EUR (€)".
    private var currentCurrencyLabel: String {
        let code = appState.currentUser?.currencyPreference ?? "EUR"
        let symbol: String
        switch code {
        case "EUR": symbol = "€"
        case "USD": symbol = "$"
        case "GBP": symbol = "£"
        case "JPY": symbol = "¥"
        case "CHF": symbol = "CHF"
        case "SEK", "NOK", "DKK": symbol = "kr"
        case "CAD": symbol = "C$"
        case "AUD": symbol = "A$"
        default:    symbol = code
        }
        return "\(code) (\(symbol))"
    }
```

- [ ] **Step 2: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-iOS/Zampa/Features/Profile/ProfileView.swift && git commit -m "$(cat <<'EOF'
iOS: add Moneda row to the profile Preferencias section

NavigationLink to CurrencyPreferenceView. Trailing label shows the
current selection as "EUR (€)" / "USD ($)" etc., derived from
appState.currentUser.currencyPreference.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: iOS — `MenuDetailView` numeric price + currency conversion

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Feed/MenuDetailView.swift`

**Context:** Currently `MenuDetailView.swift` does NOT display the actual numeric price anywhere — only a tier tag `priceRange(menu.priceTotal)` that returns `$` / `$$` / `$$$`. This task adds a prominent numeric price block inside the "MENU ITEM" section (below the section header) and, when the user's currency is not EUR, a smaller converted hint below.

- [ ] **Step 1: Insert the numeric price block in the Menu Item section**

In `Zampa-iOS/Zampa/Features/Feed/MenuDetailView.swift`, locate the menu item section header HStack (around lines 210-229, opens with `HStack(spacing: 8) { Image(systemName: "fork.knife") ...` and closes with the `.padding(.bottom, 14)` line).

Find the closing of that HStack and the transition to the "Includes row" (around line 230-237, starts with `// Includes row`).

INSERT a new price VStack between the section header HStack and the includes row. The insertion point is just before the `// Includes row` comment.

After the section header's `.padding(.bottom, 14)` closing, add:

```swift
                            // ── PRICE ────────────────────────────────────
                            VStack(alignment: .leading, spacing: 2) {
                                Text(CurrencyService.format(amount: menu.priceTotal, code: "EUR"))
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundColor(.appPrimary)
                                if let prefCode = appState.currentUser?.currencyPreference,
                                   prefCode != "EUR",
                                   let converted = CurrencyService.formatConverted(eurAmount: menu.priceTotal, to: prefCode) {
                                    Text("~\(converted)")
                                        .font(.system(size: 13))
                                        .foregroundColor(.appTextSecondary)
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 14)

```

- [ ] **Step 2: Ensure `MenuDetailView` has access to `appState`**

Check if `MenuDetailView` already has `@EnvironmentObject var appState: AppState`. Run:

```bash
grep -n "appState" "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa/Features/Feed/MenuDetailView.swift" | head -5
```

If `appState` is NOT present in the struct (no grep hits for declarations), add the property near the top of the `MenuDetailView` struct, just after the opening `struct MenuDetailView: View {` line:

```swift
    @EnvironmentObject var appState: AppState
```

If it's already there (for example used elsewhere for `locationManager`), skip this sub-step.

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-iOS/Zampa/Features/Feed/MenuDetailView.swift && git commit -m "$(cat <<'EOF'
iOS: show numeric price in MenuDetailView with optional conversion

MenuDetailView previously only showed a $/$$/$$$ tier tag in the hero
overlay and never displayed the actual price. Add a bold EUR price
block inside the Menu Item section (below the section header) and,
when currencyPreference is non-EUR, a smaller secondary line with
the approximate converted value.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: iOS — `FilterView` slider hint

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Feed/FilterView.swift`

- [ ] **Step 1: Add the conversion hint below the "Hasta N €" label**

In `Zampa-iOS/Zampa/Features/Feed/FilterView.swift`, locate the price range VStack (around lines 93-115). It currently looks like:

```swift
                    // Price Range
                    VStack(alignment: .leading, spacing: 16) {
                        HStack {
                            Text("Precio máximo")
                                .font(.appSubheadline)
                                .fontWeight(.bold)
                            Spacer()
                            Text("Hasta \(Int(maxPrice)) €")
                                .font(.appBody)
                                .foregroundColor(.appPrimary)
                                .fontWeight(.bold)
                        }

                        Slider(value: $maxPrice, in: 5...100, step: 5)
                            .accentColor(.appPrimary)

                        HStack {
                            Text("5€")
                            Spacer()
                            Text("100€")
                        }
                        .font(.caption)
                        .foregroundColor(.appTextSecondary)
                    }
```

Replace with:

```swift
                    // Price Range
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(alignment: .top) {
                            Text("Precio máximo")
                                .font(.appSubheadline)
                                .fontWeight(.bold)
                            Spacer()
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("Hasta \(Int(maxPrice)) €")
                                    .font(.appBody)
                                    .foregroundColor(.appPrimary)
                                    .fontWeight(.bold)
                                if let prefCode = appState.currentUser?.currencyPreference,
                                   prefCode != "EUR",
                                   let converted = CurrencyService.formatConverted(eurAmount: Double(Int(maxPrice)), to: prefCode) {
                                    Text("~\(converted)")
                                        .font(.appCaption)
                                        .foregroundColor(.appTextSecondary)
                                }
                            }
                        }

                        Slider(value: $maxPrice, in: 5...100, step: 5)
                            .accentColor(.appPrimary)

                        HStack {
                            Text("5€")
                            Spacer()
                            Text("100€")
                        }
                        .font(.caption)
                        .foregroundColor(.appTextSecondary)
                    }
```

- [ ] **Step 2: Ensure `FilterView` has `appState`**

```bash
grep -n "appState\|@EnvironmentObject" "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa/Features/Feed/FilterView.swift" | head -5
```

If `FilterView` doesn't already have `@EnvironmentObject var appState: AppState`, add it as the first property of the struct (right after `struct FilterView: View {`):

```swift
    @EnvironmentObject var appState: AppState
```

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-iOS/Zampa/Features/Feed/FilterView.swift && git commit -m "$(cat <<'EOF'
iOS: show converted slider hint in FilterView when currency is non-EUR

The "Hasta N €" label gains a small "~$X.XX USD" subtitle below it
when the user's currencyPreference is not EUR, matching the
MenuDetailView pattern. The slider itself keeps stepping in EUR.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Android — `User` model + `FirebaseService` currency parsing and update

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`

- [ ] **Step 1: Add `currencyPreference` to `User` data class**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt`, replace the `User` data class:

```kotlin
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
```

- [ ] **Step 2: Parse `currencyPreference` in `getUserProfile`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`, replace `getUserProfile` (around lines 127-140):

```kotlin
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
```

- [ ] **Step 3: Add `updateCurrencyPreference(code)`**

In the same file, locate `cancelAccountDeletion()` (added in the account deletion feature, around line 456). Immediately AFTER its closing brace, add:

```kotlin
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
```

- [ ] **Step 4: Compile**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt && git commit -m "$(cat <<'EOF'
Android: add currencyPreference to User model and FirebaseService

New field on User (default "EUR" when absent) parsed in getUserProfile.
New updateCurrencyPreference(code) method validates the code against
the 10 supported ISO currencies before writing to Firestore.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Android — `CurrencyService.kt` + Hilt provide

**Files:**
- Create: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/CurrencyService.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/di/AppModule.kt`

- [ ] **Step 1: Create the service**

Create `/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android/app/src/main/java/com/sozolab/zampa/data/CurrencyService.kt`:

```kotlin
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
                "USD" -> "$$numStr USD"
                "GBP" -> "£$numStr GBP"
                "JPY" -> "¥$numStr JPY"
                "CHF" -> "$numStr CHF"
                "SEK" -> "$numStr kr SEK"
                "NOK" -> "$numStr kr NOK"
                "DKK" -> "$numStr kr DKK"
                "CAD" -> "C$$numStr CAD"
                "AUD" -> "A$$numStr AUD"
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
```

- [ ] **Step 2: Add `@Provides` to `AppModule.kt`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/di/AppModule.kt`, replace the entire file with:

```kotlin
package com.sozolab.zampa.di

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.sozolab.zampa.data.CurrencyService
import com.sozolab.zampa.data.FirebaseService
import com.sozolab.zampa.data.LocationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideFirebaseService(): FirebaseService = FirebaseService()

    @Provides
    @Singleton
    fun provideLocationService(@ApplicationContext context: Context): LocationService = LocationService(context)

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideCurrencyService(db: FirebaseFirestore): CurrencyService = CurrencyService(db)
}
```

- [ ] **Step 3: Compile**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`. If it complains about `FirebaseFirestore` not being resolvable, ensure the package import in `AppModule.kt` is exactly `com.google.firebase.firestore.FirebaseFirestore`.

- [ ] **Step 4: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/CurrencyService.kt Zampa-Android/app/src/main/java/com/sozolab/zampa/di/AppModule.kt && git commit -m "$(cat <<'EOF'
Android: add CurrencyService with Firestore rates loader and fallback

Hilt @Singleton that reads config/exchangeRates once per session and
exposes convert/format helpers plus a formatConverted shortcut.
Includes an embedded fallback rates snapshot so conversions render
even before the first Firestore fetch completes. AppModule gains a
FirebaseFirestore provider to inject into the service.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Android — `AuthViewModel` hooks `loadIfNeeded()` + VM methods

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt`

- [ ] **Step 1: Inject `CurrencyService` into the ViewModel**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt`, find the class constructor:

```kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseService: FirebaseService
) : ViewModel() {
```

Replace with:

```kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseService: FirebaseService,
    private val currencyService: com.sozolab.zampa.data.CurrencyService,
) : ViewModel() {
```

- [ ] **Step 2: Call `loadIfNeeded()` from `routePostLogin` active branch**

Find `routePostLogin(user: User)` (added in Task 12 of the account deletion feature). Replace the function:

```kotlin
    private fun routePostLogin(user: User) {
        if (user.deletedAt != null) {
            _pendingDeletionUser.value = user
            _currentUser.value = user
            _isAuthenticated.value = false
        } else {
            _pendingDeletionUser.value = null
            _currentUser.value = user
            _isAuthenticated.value = true
            refreshDeviceToken()
        }
    }
```

With:

```kotlin
    private fun routePostLogin(user: User) {
        if (user.deletedAt != null) {
            _pendingDeletionUser.value = user
            _currentUser.value = user
            _isAuthenticated.value = false
        } else {
            _pendingDeletionUser.value = null
            _currentUser.value = user
            _isAuthenticated.value = true
            refreshDeviceToken()
            viewModelScope.launch { currencyService.loadIfNeeded() }
        }
    }
```

- [ ] **Step 3: Add a VM method to update currency preference**

At the bottom of the class (after `cancelAccountDeletion`), add:

```kotlin
    /**
     * Solicita un cambio de moneda preferida. Tras el éxito, refresca el
     * User cargado para propagar el cambio a todas las pantallas que
     * observan currentUser.
     */
    fun updateCurrencyPreference(code: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firebaseService.updateCurrencyPreference(code)
                val uid = firebaseService.currentUid ?: return@launch
                val refreshed = firebaseService.getUserProfile(uid) ?: return@launch
                _currentUser.value = refreshed
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al cambiar la moneda")
            }
        }
    }
```

- [ ] **Step 4: Compile**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt && git commit -m "$(cat <<'EOF'
Android: AuthViewModel injects CurrencyService and gains updateCurrencyPreference

routePostLogin now fires currencyService.loadIfNeeded() on the active
account branch so rates are fetched once per session. A new
updateCurrencyPreference(code, onError) VM method writes through
FirebaseService and refreshes the local user so observers see the new
value immediately.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Android — `CurrencyPreferenceScreen.kt` + Navigation + MainScreen

**Files:**
- Create: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/CurrencyPreferenceScreen.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt`

- [ ] **Step 1: Create the picker screen**

Create `/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/CurrencyPreferenceScreen.kt`:

```kotlin
package com.sozolab.zampa.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class CurrencyOption(
    val code: String,
    val flag: String,
    val name: String,
    val symbol: String,
)

private val options = listOf(
    CurrencyOption("EUR", "🇪🇺", "Euro",                    "€"),
    CurrencyOption("USD", "🇺🇸", "Dólar estadounidense",    "$"),
    CurrencyOption("GBP", "🇬🇧", "Libra esterlina",          "£"),
    CurrencyOption("JPY", "🇯🇵", "Yen japonés",              "¥"),
    CurrencyOption("CHF", "🇨🇭", "Franco suizo",             "CHF"),
    CurrencyOption("SEK", "🇸🇪", "Corona sueca",             "kr"),
    CurrencyOption("NOK", "🇳🇴", "Corona noruega",           "kr"),
    CurrencyOption("DKK", "🇩🇰", "Corona danesa",            "kr"),
    CurrencyOption("CAD", "🇨🇦", "Dólar canadiense",         "C$"),
    CurrencyOption("AUD", "🇦🇺", "Dólar australiano",        "A$"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPreferenceScreen(
    currentCode: String,
    onSelect: (code: String, onError: (String) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var pendingCode by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moneda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            errorMessage?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
            items(options) { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = pendingCode == null) {
                            if (option.code == currentCode) return@clickable
                            pendingCode = option.code
                            errorMessage = null
                            onSelect(option.code) { err ->
                                pendingCode = null
                                errorMessage = err
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option.flag, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            option.code,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            option.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        option.symbol,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    when {
                        pendingCode == option.code -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        option.code == currentCode -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    text = "Los precios siempre se cobran en euros. Esta opción sólo cambia cómo se muestran en la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }

    // Cuando el current code cambia tras un select exitoso, volvemos atrás.
    LaunchedEffect(currentCode) {
        if (pendingCode != null && pendingCode == currentCode) {
            pendingCode = null
            onBack()
        }
    }
}
```

- [ ] **Step 2: Add `Route.CurrencyPreference` and composable in `Navigation.kt`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt`, find the `Route` sealed class and add a new route. Insert `data object CurrencyPreference : Route("currency_preference")` right after `NotificationPreferences`:

```kotlin
sealed class Route(val route: String) {
    data object Auth : Route("auth")
    data object Main : Route("main")
    data object AccountDeletionRecovery : Route("account_deletion_recovery")
    data object MerchantSetup : Route("merchant_setup")
    data object LocationOnboarding : Route("location_onboarding")
    data object Stats : Route("stats")
    data object DietaryPreferences : Route("dietary_preferences")
    data object NotificationPreferences : Route("notification_preferences")
    data object CurrencyPreference : Route("currency_preference")
    data object History : Route("history")
    data object PrivacyPolicy : Route("privacy_policy")
    data object Terms : Route("terms")
    data object MenuDetail : Route("menu_detail/{menuId}") {
        fun createRoute(menuId: String) = "menu_detail/$menuId"
    }
}
```

Then inside the `NavHost`, add a new composable entry alongside the existing ones. Place it next to `Route.NotificationPreferences.route` (around line 137-141):

```kotlin
        composable(Route.CurrencyPreference.route) {
            val user by authViewModel.currentUser.collectAsState()
            CurrencyPreferenceScreen(
                currentCode = user?.currencyPreference ?: "EUR",
                onSelect = { code, onError -> authViewModel.updateCurrencyPreference(code, onError) },
                onBack = { navController.popBackStack() }
            )
        }
```

Add these imports at the top of `Navigation.kt`:

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sozolab.zampa.ui.profile.CurrencyPreferenceScreen
```

Note: `collectAsState` and `getValue` may already be imported via a wildcard `androidx.compose.runtime.*`. Verify before adding.

- [ ] **Step 3: Add `onNavigateToCurrency` parameter in `MainScreen`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt`, replace the `MainScreen` signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDietaryPreferences: () -> Unit = {},
    onNavigateToNotificationPreferences: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
```

With:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDietaryPreferences: () -> Unit = {},
    onNavigateToNotificationPreferences: () -> Unit = {},
    onNavigateToCurrencyPreference: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
```

Then, inside the `Tab.PROFILE -> { ProfileScreen(...) }` call, add a new argument `onNavigateToCurrencyPreference = onNavigateToCurrencyPreference` right after `onNavigateToNotificationPreferences`. The full ProfileScreen call becomes:

```kotlin
            Tab.PROFILE -> {
                com.sozolab.zampa.ui.profile.ProfileScreen(
                    user = currentUser,
                    pendingPhotoBitmap = pendingPhotoBitmap,
                    isMerchant = isMerchant,
                    onLogout = onLogout,
                    onUserNameUpdated = { name -> authViewModel.updateUserName(name) },
                    onProfilePhotoUpdated = { bitmap, photoData -> authViewModel.updateProfilePhoto(bitmap, photoData) },
                    onNavigateToStats = onNavigateToStats,
                    onNavigateToEditProfile = onNavigateToSetup,
                    onNavigateToSubscription = {},
                    onNavigateToDietaryPreferences = onNavigateToDietaryPreferences,
                    onNavigateToNotificationPreferences = onNavigateToNotificationPreferences,
                    onNavigateToCurrencyPreference = onNavigateToCurrencyPreference,
                    onNavigateToHistory = onNavigateToHistory,
                    onRequestAccountDeletion = { onError -> authViewModel.requestAccountDeletion(onError) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
```

- [ ] **Step 4: Wire `onNavigateToCurrencyPreference` in `Navigation.kt`'s `Main` composable**

In `Navigation.kt`, find the `composable(Route.Main.route) { MainScreen(...) }` call. Add `onNavigateToCurrencyPreference = { navController.navigate(Route.CurrencyPreference.route) }` in the callbacks, next to the other onNavigate callbacks. The final MainScreen call becomes:

```kotlin
        composable(Route.Main.route) {
            MainScreen(
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Route.Auth.route) {
                        popUpTo(Route.Main.route) { inclusive = true }
                    }
                },
                onNavigateToSetup = {
                    navController.navigate(Route.MerchantSetup.route)
                },
                onNavigateToLocation = {
                    navController.navigate(Route.LocationOnboarding.route)
                },
                onNavigateToDetail = { menuId ->
                    navController.navigate(Route.MenuDetail.createRoute(menuId))
                },
                onNavigateToStats = {
                    navController.navigate(Route.Stats.route)
                },
                onNavigateToDietaryPreferences = {
                    navController.navigate(Route.DietaryPreferences.route)
                },
                onNavigateToNotificationPreferences = {
                    navController.navigate(Route.NotificationPreferences.route)
                },
                onNavigateToCurrencyPreference = {
                    navController.navigate(Route.CurrencyPreference.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Route.History.route)
                }
            )
        }
```

- [ ] **Step 5: Compile**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If gradle complains about `onNavigateToCurrencyPreference` missing on `ProfileScreen`, that's because Task 15 hasn't added the parameter yet — it's fine, we'll address it in Task 15. For now, ensure the error points specifically to ProfileScreen.kt and not to other issues.

> **If the compile fails because of Task 15 dependency:** the simplest fix is to reorder — do Task 15 first, then re-run this step. OR temporarily remove the `onNavigateToCurrencyPreference` argument from the ProfileScreen call in step 3 so everything else compiles, and re-add it after Task 15.

- [ ] **Step 6: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/CurrencyPreferenceScreen.kt Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt && git commit -m "$(cat <<'EOF'
Android: add CurrencyPreferenceScreen and Navigation wiring

New composable with 10 rows (flag + code + Spanish name + symbol),
wired as Route.CurrencyPreference in NavHost and plumbed from
MainScreen's ProfileScreen call via a new onNavigateToCurrencyPreference
callback. Row tap writes through AuthViewModel.updateCurrencyPreference;
on success the currentUser StateFlow refreshes and a LaunchedEffect
pops back to profile.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Android — `ProfileScreen.kt` Moneda row

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt`

- [ ] **Step 1: Add the new parameter to `ProfileScreen`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt`, find the `ProfileScreen` signature. Add `onNavigateToCurrencyPreference: () -> Unit = {},` right after `onNavigateToNotificationPreferences`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User?,
    pendingPhotoBitmap: Bitmap? = null,
    isMerchant: Boolean,
    onLogout: () -> Unit,
    onUserNameUpdated: (String) -> Unit = {},
    onProfilePhotoUpdated: (Bitmap, ByteArray) -> Unit = { _, _ -> },
    onNavigateToDietaryPreferences: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToNotificationPreferences: () -> Unit = {},
    onNavigateToCurrencyPreference: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onRequestAccountDeletion: ((onError: (String) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
```

- [ ] **Step 2: Add the "Moneda" row to the Preferencias section**

In the LazyColumn body, find the item that renders the "Notificaciones" row (search for `onClick = onNavigateToNotificationPreferences`). Add a new item IMMEDIATELY AFTER it, showing the Moneda row with current selection as a trailing label:

```kotlin
        item {
            ProfileMenuItem(
                icon = Icons.Default.Notifications,
                title = "Notificaciones",
                iconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onNavigateToNotificationPreferences
            )
        }
        item {
            val code = user?.currencyPreference ?: "EUR"
            val symbol = when (code) {
                "EUR" -> "€"
                "USD" -> "$"
                "GBP" -> "£"
                "JPY" -> "¥"
                "CHF" -> "CHF"
                "SEK", "NOK", "DKK" -> "kr"
                "CAD" -> "C$"
                "AUD" -> "A$"
                else -> code
            }
            ListItem(
                headlineContent = { Text("Moneda") },
                supportingContent = { Text("$code ($symbol)") },
                leadingContent = {
                    Icon(
                        Icons.Default.AttachMoney,
                        contentDescription = "Moneda",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable(onClick = onNavigateToCurrencyPreference)
            )
        }
```

Add this import near the top of the file if it's not already present:

```kotlin
import androidx.compose.material.icons.filled.AttachMoney
```

Note: `androidx.compose.material.icons.filled.*` is already wildcard-imported in `ProfileScreen.kt` based on prior tasks, so `AttachMoney` should resolve automatically. Double-check by grepping:

```bash
grep "material.icons.filled" "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt"
```

If the wildcard import is present, no new import is needed.

- [ ] **Step 3: Compile**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. This should also fix any compile error from Task 14 if you deferred it.

- [ ] **Step 4: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt && git commit -m "$(cat <<'EOF'
Android: add Moneda row to ProfileScreen Preferencias section

New parameter onNavigateToCurrencyPreference and a ListItem showing
the current selection as the supporting text ("USD ($)"). Clicking
routes to CurrencyPreferenceScreen via the callback.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Android — `MenuDetailScreen` price fix + conversion

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/MenuDetailScreen.kt`

**Context:** The Android `MenuDetailScreen` currently displays the price as `$12.50` (wrong — should be `12,50 €`). This task corrects the primary display AND adds the optional converted line.

- [ ] **Step 1: Read the current user in `MenuDetailScreen`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/MenuDetailScreen.kt`, locate where the composable gets its dependencies. Find the top of the `MenuDetailScreen` composable function body and look for existing `hiltViewModel` or similar. It likely has an `authViewModel: AuthViewModel = hiltViewModel()` call. If not, add these at the top of the function body:

```kotlin
    val authViewModel: com.sozolab.zampa.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
```

If they're already present, skip this.

- [ ] **Step 2: Replace the price Text**

Find the price Text around line 516:

```kotlin
                        Text(
                            "$${"%.2f".format(currentMenu.priceTotal)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
```

Replace with:

```kotlin
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                com.sozolab.zampa.data.CurrencyService.format(currentMenu.priceTotal, "EUR"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            val prefCode = currentUser?.currencyPreference ?: "EUR"
                            if (prefCode != "EUR") {
                                val converted = remember(currentMenu.priceTotal, prefCode) {
                                    val service = com.sozolab.zampa.data.CurrencyService(
                                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    )
                                    service.formatConverted(currentMenu.priceTotal, prefCode)
                                }
                                converted?.let {
                                    Text(
                                        "~$it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
```

**Important:** the inline `CurrencyService(...)` constructor call above bypasses Hilt and creates a new service for the conversion math only. It does NOT trigger `loadIfNeeded` — it uses the embedded fallback rates. This is intentional because the MenuDetailScreen doesn't have a clean Hilt injection point for the service at this call site, and the fallback rates are good enough for a display hint. The actual session-wide rates are loaded by `AuthViewModel.routePostLogin` (Task 13) via the Hilt singleton, but that instance isn't accessible here without restructuring.

**Alternative (cleaner but bigger):** inject CurrencyService into a `MenuDetailViewModel` (if one exists) or into the AuthViewModel and read a StateFlow here. If MenuDetailScreen already has a ViewModel, prefer that path. If not, the inline approach is acceptable for v1.

Check for an existing viewmodel:

```bash
grep -n "MenuDetailViewModel\|menuDetailViewModel\|hiltViewModel" "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/MenuDetailScreen.kt" | head -5
```

If there's no dedicated view model, stick with the inline fallback approach above. The fallback rates will be current enough that the user won't notice a drift vs. the daily-refreshed ones for a "hint" purpose.

- [ ] **Step 3: Compile**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/MenuDetailScreen.kt && git commit -m "$(cat <<'EOF'
Android: fix MenuDetailScreen price currency and add conversion hint

The dish card previously displayed the priceTotal with a "$" prefix,
which is wrong — prices are EUR. Now uses CurrencyService.format for a
proper "12,50 €" and, when the user's currencyPreference is non-EUR,
renders a smaller "~$13.60 USD" hint below.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 17: Android — `FeedScreen` FilterView slider hint

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/FeedScreen.kt`

- [ ] **Step 1: Add the conversion hint below the "Hasta N €" label**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/FeedScreen.kt`, find the embedded FilterView price block (around lines 707-723):

```kotlin
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Precio máximo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Hasta ${maxPrice.toInt()} €", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = maxPrice,
                    onValueChange = { maxPrice = it },
                    valueRange = 5f..100f,
                    steps = 18 // 19 posiciones de 5 en 5: 5, 10, 15, ... 100
                )
                Row {
                    Text("5€", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("100€", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
```

Replace the top `Row(...)` line (the one with "Precio máximo" and "Hasta N €") with a Column-wrapped version that adds the hint:

```kotlin
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("Precio máximo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Hasta ${maxPrice.toInt()} €", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        val prefCode = currencyPreference ?: "EUR"
                        if (prefCode != "EUR") {
                            val converted = remember(maxPrice.toInt(), prefCode) {
                                val service = com.sozolab.zampa.data.CurrencyService(
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                )
                                service.formatConverted(maxPrice.toInt().toDouble(), prefCode)
                            }
                            converted?.let {
                                Text(
                                    "~$it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Slider(
                    value = maxPrice,
                    onValueChange = { maxPrice = it },
                    valueRange = 5f..100f,
                    steps = 18 // 19 posiciones de 5 en 5: 5, 10, 15, ... 100
                )
                Row {
                    Text("5€", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("100€", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
```

- [ ] **Step 2: Thread `currencyPreference` into the FilterView composable**

The FilterView block above is inside a private `@Composable fun FilterView(...)`. Find its parameter list (search upward from the price block). Add a new parameter `currencyPreference: String? = null` at the end of the parameter list:

```bash
grep -n "private fun FilterView\|@Composable.*FilterView" "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/FeedScreen.kt"
```

Then find where FilterView is called inside FeedScreen (search for `FilterView(`). Pass the current user's preference:

```kotlin
            val authViewModel: com.sozolab.zampa.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            val currentUser by authViewModel.currentUser.collectAsState()
            FilterView(
                // ... existing args ...
                currencyPreference = currentUser?.currencyPreference,
            )
```

If FeedScreen already has `authViewModel` and `currentUser` visible in the scope where `FilterView` is called, reuse them and just add the one new argument.

- [ ] **Step 3: Compile**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/FeedScreen.kt && git commit -m "$(cat <<'EOF'
Android: show converted slider hint in FeedScreen FilterView

Matches the iOS FilterView behavior: when currencyPreference is non-EUR,
the "Hasta N €" label gets a small "~$X.XX USD" subtitle below it.
The slider still steps in EUR.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 18: iOS build verification + manual E2E (deferred manual)

**Files:** (no changes — verification only)

- [ ] **Step 1: Open the project in Xcode and build**

```bash
open "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa.xcodeproj"
```

In Xcode: select a simulator and press **Cmd+B**. Expected: `Build Succeeded`. Common failure modes:
- `Cannot find 'CurrencyService' in scope`: the file is not registered in `project.pbxproj` — revisit Task 5 Step 2.
- `Cannot find 'CurrencyPreferenceView' in scope`: same, revisit Task 6 Step 2.
- `Cannot find 'appState' in scope` inside MenuDetailView or FilterView: the `@EnvironmentObject` declaration is missing — revisit Task 9 Step 2 / Task 10 Step 2.

- [ ] **Step 2: Manual E2E on a simulator**

Press **Cmd+R**. Once launched:

1. Log in as a test cliente.
2. Navigate to Perfil. Scroll to the Preferencias section. Verify the "Moneda" row is between "Notificaciones" and "Tema", showing `EUR (€)` in the trailing area.
3. Tap it. Verify the picker shows 10 rows with flag + code + Spanish name + symbol, with EUR marked.
4. Tap USD. Verify: spinner replaces the checkmark briefly, picker pops back to profile, row trailing now shows `USD ($)`.
5. Go to the Feed → tap any menu card → verify the menu detail shows the numeric EUR price block (bold, e.g. `12,50 €`) with `~$13.60 USD` underneath.
6. Go back to Feed → tap Filter → verify the "Hasta N €" label has `~$X.XX USD` underneath. Drag the slider — the hint updates live.
7. Change the moneda back to EUR in profile. Verify: both secondary lines disappear (MenuDetail shows only `12,50 €`, Filter shows only `Hasta N €`).
8. Kill the app and relaunch. Verify the selection persists (is read from Firestore on session resume).
9. Toggle airplane mode and relaunch. The app should still show the menu detail with a converted line if the pref is non-EUR (using the embedded fallback rates).

- [ ] **Step 3: No commit (verification only)**

---

## Task 19: Android build verification + manual E2E (deferred manual)

**Files:** (no changes — verification only)

- [ ] **Step 1: Build the debug APK**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew assembleDebug 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`. If the compile fails, read the error carefully and return to the relevant task.

- [ ] **Step 2: Install and launch**

```bash
./gradlew installDebug && adb shell am start -n com.sozolab.zampa/.MainActivity
```

- [ ] **Step 3: Manual E2E on device/emulator**

Same steps as Task 18 Step 2, adapted for Android:
1. Log in as a test cliente.
2. Profile → Preferencias → verify "Moneda" row with supporting text `EUR (€)`.
3. Tap the row → verify the picker screen with 10 rows.
4. Tap USD → spinner → pops back → supporting text now `USD ($)`.
5. Feed → menu card → detail → verify price shows `12,50 €` (previously wrong as `$12.50`) + `~$13.60 USD` below.
6. Filter → verify `Hasta N €` with `~$X.XX USD` subtitle.
7. Change back to EUR → verify secondary lines gone.
8. Relaunch → verify persistence.
9. Airplane mode → relaunch → verify embedded fallback still renders conversions.

- [ ] **Step 4: No commit (verification only)**

---

## Task 20: Deploy backend + seed (deferred manual)

**Files:** (no changes — deploy only)

- [ ] **Step 1: Deploy rules + functions**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && firebase deploy --only firestore:rules,functions:refreshExchangeRates --project eatout-70b8b
```

Expected: `Deploy complete!` with no errors.

- [ ] **Step 2: Seed the initial exchangeRates doc**

```bash
cd functions && node seed-exchange-rates.js
```

Expected: prints `OK — config/exchangeRates escrito:` followed by the rates map.

- [ ] **Step 3: Verify in Firebase Console**

Open the Firestore console and check that `config/exchangeRates` has `base: "EUR"`, 9 rate entries under `rates`, and a recent `updatedAt` timestamp. Also verify in Functions that `refreshExchangeRates` is listed with schedule `every day 05:00` Europe/Madrid.

- [ ] **Step 4: No commit (deploy only)**
