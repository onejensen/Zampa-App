# Permanent Offers — Recurring Days Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add weekday-slot selection to permanent offers so merchants can schedule recurring specials (e.g. "tapas every Thursday"), with the customer feed showing each permanent offer only on its selected days.

**Architecture:** Add a `recurringDays: [Int]?` field to the `Menu` model (stored in Firestore). The feed filters permanent offers client-side by comparing today's weekday against the array. The publish form shows a 7-button day picker when "Oferta permanente" is selected; occupied slots (claimed by another of the merchant's permanents) are greyed out. The convention for weekday indices is 0=Monday…6=Sunday (European Mon-first order, consistent across iOS and Android).

**Tech Stack:** SwiftUI + Firebase iOS SDK (iOS), Jetpack Compose + Firebase Android SDK (Android). Both platforms implement identical logic in parallel. Unit tests: XCTest (iOS), JUnit (Android).

---

## File Map

| Platform | File | Action | What changes |
|---|---|---|---|
| iOS | `Zampa-iOS/Zampa/Core/Models/Menu.swift` | Modify | Add `recurringDays` field + `isVisibleOnDay(_:)` + static `occupiedDays(from:)` |
| iOS | `Zampa-iOS/EatOutTests/RecurringDaysTests.swift` | Create | Unit tests for filter logic |
| iOS | `Zampa-iOS/Zampa/Services/FirebaseService.swift` | Modify | Read/write `recurringDays` in `parseMenu`, `getMenuById`, `getMenusByMerchant`, `createMenu`; weekday filter in `getActiveMenus` |
| iOS | `Zampa-iOS/Zampa/Services/MenuService.swift` | Modify | Add `recurringDays` param to `createMenu` |
| iOS | `Zampa-iOS/Zampa/Features/Merchant/RecurringDaysPicker.swift` | Create | Reusable day-picker SwiftUI view |
| iOS | `Zampa-iOS/Zampa/Features/Merchant/CreateMenuView.swift` | Modify | Add `recurringDays`/`occupiedDays` state to VM; show picker; update `isValid` |
| iOS | `Zampa-iOS/Zampa/Features/Merchant/EditMenuView.swift` | Modify | Add `recurringDays` to VM `setup`/`updateMenu`; show picker for permanent menus |
| iOS | `Zampa-iOS/Zampa/Localization/*.json` (8 files) | Modify | Add 3 new keys |
| Android | `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt` | Modify | Add `recurringDays` field + filter helpers |
| Android | `Zampa-Android/app/src/test/java/com/sozolab/zampa/ModelsTest.kt` | Modify | Unit tests for filter logic |
| Android | `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt` | Modify | Read/write `recurringDays`; weekday filter in `getActiveMenus` |
| Android | `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/RecurringDaysPicker.kt` | Create | Reusable composable day picker |
| Android | `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt` | Modify | Add `recurringDays` param to `createMenu`; add `updateMenuWithDays` |
| Android | `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt` | Modify | Integrate picker in `CreateMenuSheet` + `EditMenuSheet` |
| Android | `Zampa-Android/app/src/main/res/values*/strings.xml` (9 files) | Modify | Add 3 new keys |

---

## Task 1: Localization strings — all 8 iOS locales + Android

**Files:**
- Modify: `Zampa-iOS/Zampa/Localization/es.json` (and ca, de, en, eu, fr, gl, it)
- Modify: `Zampa-Android/app/src/main/res/values/strings.xml` (and values-ca, de, en, eu, fr, gl, it, es)

Three new keys per language:

| Key | es | en | ca | de | fr | it | gl | eu |
|---|---|---|---|---|---|---|---|---|
| `create_menu_recurring_days_title` | ¿Qué días está disponible? | Which days is it available? | Quins dies està disponible? | An welchen Tagen ist es verfügbar? | Quels jours est-il disponible ? | Quali giorni è disponibile? | Que días está dispoñible? | Zein egunetan dago erabilgarri? |
| `create_menu_recurring_days_slots_free` | %d de 7 días libres | %d of 7 days free | %d de 7 dies lliures | %d von 7 Tagen frei | %d jours libres sur 7 | %d di 7 giorni liberi | %d de 7 días libres | 7tik %d egun libre |
| `create_menu_recurring_days_all_occupied` | Todos los días están ocupados. Edita o elimina una oferta permanente para liberar días. | All days are occupied. Edit or delete a permanent offer to free up days. | Tots els dies estan ocupats. Edita o elimina una oferta permanent per alliberar dies. | Alle Tage sind belegt. Bearbeite oder lösche ein Dauerangebot, um Tage freizugeben. | Tous les jours sont occupés. Modifiez ou supprimez une offre permanente pour libérer des jours. | Tutti i giorni sono occupati. Modifica o elimina un'offerta permanente per liberare giorni. | Todos os días están ocupados. Edita ou elimina unha oferta permanente para liberar días. | Egun guztiak beteta daude. Editatu edo ezabatu eskaintza iraunkor bat egunak askatzeko. |

- [ ] **Step 1: Add keys to iOS `es.json`**

Open `Zampa-iOS/Zampa/Localization/es.json`. After the `"create_menu_permanent_hint"` line (line 250), add:

```json
  "create_menu_recurring_days_title": "¿Qué días está disponible?",
  "create_menu_recurring_days_slots_free": "%d de 7 días libres",
  "create_menu_recurring_days_all_occupied": "Todos los días están ocupados. Edita o elimina una oferta permanente para liberar días.",
```

- [ ] **Step 2: Add keys to the remaining 7 iOS locale files**

For `en.json` (after line 250):
```json
  "create_menu_recurring_days_title": "Which days is it available?",
  "create_menu_recurring_days_slots_free": "%d of 7 days free",
  "create_menu_recurring_days_all_occupied": "All days are occupied. Edit or delete a permanent offer to free up days.",
```

For `ca.json`:
```json
  "create_menu_recurring_days_title": "Quins dies està disponible?",
  "create_menu_recurring_days_slots_free": "%d de 7 dies lliures",
  "create_menu_recurring_days_all_occupied": "Tots els dies estan ocupats. Edita o elimina una oferta permanent per alliberar dies.",
```

For `de.json`:
```json
  "create_menu_recurring_days_title": "An welchen Tagen ist es verfügbar?",
  "create_menu_recurring_days_slots_free": "%d von 7 Tagen frei",
  "create_menu_recurring_days_all_occupied": "Alle Tage sind belegt. Bearbeite oder lösche ein Dauerangebot, um Tage freizugeben.",
```

For `eu.json`:
```json
  "create_menu_recurring_days_title": "Zein egunetan dago erabilgarri?",
  "create_menu_recurring_days_slots_free": "7tik %d egun libre",
  "create_menu_recurring_days_all_occupied": "Egun guztiak beteta daude. Editatu edo ezabatu eskaintza iraunkor bat egunak askatzeko.",
```

For `fr.json`:
```json
  "create_menu_recurring_days_title": "Quels jours est-il disponible ?",
  "create_menu_recurring_days_slots_free": "%d jours libres sur 7",
  "create_menu_recurring_days_all_occupied": "Tous les jours sont occupés. Modifiez ou supprimez une offre permanente pour libérer des jours.",
```

For `gl.json`:
```json
  "create_menu_recurring_days_title": "Que días está dispoñible?",
  "create_menu_recurring_days_slots_free": "%d de 7 días libres",
  "create_menu_recurring_days_all_occupied": "Todos os días están ocupados. Edita ou elimina unha oferta permanente para liberar días.",
```

For `it.json`:
```json
  "create_menu_recurring_days_title": "Quali giorni è disponibile?",
  "create_menu_recurring_days_slots_free": "%d di 7 giorni liberi",
  "create_menu_recurring_days_all_occupied": "Tutti i giorni sono occupati. Modifica o elimina un'offerta permanente per liberare giorni.",
```

- [ ] **Step 3: Add keys to Android `values/strings.xml`** (the default/Spanish file)

After `<string name="create_menu_permanent_hint">` add:
```xml
<string name="create_menu_recurring_days_title">¿Qué días está disponible?</string>
<string name="create_menu_recurring_days_slots_free">%d de 7 días libres</string>
<string name="create_menu_recurring_days_all_occupied">Todos los días están ocupados. Edita o elimina una oferta permanente para liberar días.</string>
```

- [ ] **Step 4: Add keys to the 8 remaining Android locale `strings.xml` files**

`values-en/strings.xml`:
```xml
<string name="create_menu_recurring_days_title">Which days is it available?</string>
<string name="create_menu_recurring_days_slots_free">%d of 7 days free</string>
<string name="create_menu_recurring_days_all_occupied">All days are occupied. Edit or delete a permanent offer to free up days.</string>
```

`values-ca/strings.xml`:
```xml
<string name="create_menu_recurring_days_title">Quins dies està disponible?</string>
<string name="create_menu_recurring_days_slots_free">%d de 7 dies lliures</string>
<string name="create_menu_recurring_days_all_occupied">Tots els dies estan ocupats. Edita o elimina una oferta permanent per alliberar dies.</string>
```

`values-de/strings.xml`:
```xml
<string name="create_menu_recurring_days_title">An welchen Tagen ist es verfügbar?</string>
<string name="create_menu_recurring_days_slots_free">%d von 7 Tagen frei</string>
<string name="create_menu_recurring_days_all_occupied">Alle Tage sind belegt. Bearbeite oder lösche ein Dauerangebot, um Tage freizugeben.</string>
```

`values-eu/strings.xml`:
```xml
<string name="create_menu_recurring_days_title">Zein egunetan dago erabilgarri?</string>
<string name="create_menu_recurring_days_slots_free">7tik %d egun libre</string>
<string name="create_menu_recurring_days_all_occupied">Egun guztiak beteta daude. Editatu edo ezabatu eskaintza iraunkor bat egunak askatzeko.</string>
```

`values-fr/strings.xml`:
```xml
<string name="create_menu_recurring_days_title">Quels jours est-il disponible ?</string>
<string name="create_menu_recurring_days_slots_free">%d jours libres sur 7</string>
<string name="create_menu_recurring_days_all_occupied">Tous les jours sont occupés. Modifiez ou supprimez une offre permanente pour libérer des jours.</string>
```

`values-gl/strings.xml`:
```xml
<string name="create_menu_recurring_days_title">Que días está dispoñible?</string>
<string name="create_menu_recurring_days_slots_free">%d de 7 días libres</string>
<string name="create_menu_recurring_days_all_occupied">Todos os días están ocupados. Edita ou elimina unha oferta permanente para liberar días.</string>
```

`values-it/strings.xml`:
```xml
<string name="create_menu_recurring_days_title">Quali giorni è disponibile?</string>
<string name="create_menu_recurring_days_slots_free">%d di 7 giorni liberi</string>
<string name="create_menu_recurring_days_all_occupied">Tutti i giorni sono occupati. Modifica o elimina un\'offerta permanente per liberare giorni.</string>
```

`values-es/strings.xml` (same as default):
```xml
<string name="create_menu_recurring_days_title">¿Qué días está disponible?</string>
<string name="create_menu_recurring_days_slots_free">%d de 7 días libres</string>
<string name="create_menu_recurring_days_all_occupied">Todos los días están ocupados. Edita o elimina una oferta permanente para liberar días.</string>
```

- [ ] **Step 5: Commit**

```bash
git add "Zampa-iOS/Zampa/Localization/" "Zampa-Android/app/src/main/res/values*/strings.xml"
git commit -m "feat(i18n): add recurring days localization strings (8 locales)"
```

---

## Task 2: iOS — Menu model + filter helpers + unit tests

**Files:**
- Modify: `Zampa-iOS/Zampa/Core/Models/Menu.swift`
- Create: `Zampa-iOS/EatOutTests/RecurringDaysTests.swift`

**Weekday convention:** `recurringDays` stores integers `0=Monday, 1=Tuesday, 2=Wednesday, 3=Thursday, 4=Friday, 5=Saturday, 6=Sunday`. This is the European Monday-first week order.

- [ ] **Step 1: Write failing tests first**

Create `Zampa-iOS/EatOutTests/RecurringDaysTests.swift`:

```swift
import XCTest
@testable import Zampa

final class RecurringDaysTests: XCTestCase {

    // MARK: - isVisibleOnDay

    func testNonPermanentAlwaysVisible() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: false)
        XCTAssertTrue(menu.isVisibleOnDay(0))
        XCTAssertTrue(menu.isVisibleOnDay(3))
        XCTAssertTrue(menu.isVisibleOnDay(6))
    }

    func testPermanentWithoutDaysAlwaysVisible() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: nil)
        XCTAssertTrue(menu.isVisibleOnDay(0))
        XCTAssertTrue(menu.isVisibleOnDay(6))
    }

    func testPermanentWithEmptyDaysAlwaysVisible() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [])
        XCTAssertTrue(menu.isVisibleOnDay(0))
    }

    func testPermanentOnlyVisibleOnSelectedDays() {
        // Thursday=3
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [3])
        XCTAssertFalse(menu.isVisibleOnDay(0))  // Monday
        XCTAssertFalse(menu.isVisibleOnDay(2))  // Wednesday
        XCTAssertTrue(menu.isVisibleOnDay(3))   // Thursday
        XCTAssertFalse(menu.isVisibleOnDay(4))  // Friday
    }

    func testPermanentMultipleDays() {
        // Mon(0) + Wed(2) + Fri(4)
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [0, 2, 4])
        XCTAssertTrue(menu.isVisibleOnDay(0))
        XCTAssertFalse(menu.isVisibleOnDay(1))
        XCTAssertTrue(menu.isVisibleOnDay(2))
        XCTAssertFalse(menu.isVisibleOnDay(3))
        XCTAssertTrue(menu.isVisibleOnDay(4))
        XCTAssertFalse(menu.isVisibleOnDay(5))
        XCTAssertFalse(menu.isVisibleOnDay(6))
    }

    // MARK: - occupiedDays(from:)

    func testOccupiedDaysEmpty() {
        XCTAssertEqual(Menu.occupiedDays(from: []), Set())
    }

    func testOccupiedDaysFromSingleOffer() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [1, 3, 5])
        XCTAssertEqual(Menu.occupiedDays(from: [menu]), Set([1, 3, 5]))
    }

    func testOccupiedDaysFromMultipleOffers() {
        let m1 = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                      isPermanent: true, recurringDays: [0, 1])
        let m2 = Menu(id: "2", businessId: "b", date: "", title: "T", priceTotal: 5,
                      isPermanent: true, recurringDays: [4, 5])
        XCTAssertEqual(Menu.occupiedDays(from: [m1, m2]), Set([0, 1, 4, 5]))
    }

    func testOccupiedDaysLegacyPermanentOccupiesAll() {
        // A permanent with no recurringDays is legacy — occupies all 7 days
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: nil)
        XCTAssertEqual(Menu.occupiedDays(from: [menu]), Set(0...6))
    }

    func testOccupiedDaysLegacyEmptyOccupiesAll() {
        let menu = Menu(id: "1", businessId: "b", date: "", title: "T", priceTotal: 5,
                        isPermanent: true, recurringDays: [])
        XCTAssertEqual(Menu.occupiedDays(from: [menu]), Set(0...6))
    }
}
```

> **Note:** Add this file to the `EatOutTests` target in Xcode: File > Add Files to "Zampa", select `RecurringDaysTests.swift`, check the `EatOutTests` target checkbox.

- [ ] **Step 2: Run tests to confirm they fail (model field doesn't exist yet)**

```bash
xcodebuild test \
  -project "Zampa-iOS/Zampa.xcodeproj" \
  -scheme Zampa \
  -destination "platform=iOS Simulator,name=iPhone 16,OS=latest" \
  -only-testing "EatOutTests/RecurringDaysTests" 2>&1 | grep -E "error:|FAILED|PASSED"
```

Expected: compile errors about `recurringDays` not found and `isVisibleOnDay` not found.

- [ ] **Step 3: Add `recurringDays` field to `Menu.swift`**

In `Zampa-iOS/Zampa/Core/Models/Menu.swift`:

After `let isPermanent: Bool` (line 89), add:
```swift
    /// Días de la semana en que esta oferta permanente es visible (0=lun…6=dom). Nil = todos los días (legado).
    let recurringDays: [Int]?
```

In the `init(...)` signature, after `isPermanent: Bool = false`, add:
```swift
        recurringDays: [Int]? = nil
```

In the init body, after `self.isPermanent = isPermanent`, add:
```swift
        self.recurringDays = recurringDays
```

- [ ] **Step 4: Add `isVisibleOnDay` and `occupiedDays` to `Menu.swift`**

At the bottom of the `Menu` struct, before the closing `}`, add:

```swift
    /// Returns true if this offer should appear in the feed on the given weekday index.
    /// weekday: 0=Monday…6=Sunday. Non-permanent offers always return true.
    func isVisibleOnDay(_ weekday: Int) -> Bool {
        guard isPermanent else { return true }
        guard let days = recurringDays, !days.isEmpty else { return true }
        return days.contains(weekday)
    }

    /// Returns the set of weekday indices (0=Mon…6=Sun) already occupied by the given permanent offers.
    /// Permanents without recurringDays (legacy) are treated as occupying all 7 days.
    static func occupiedDays(from permanents: [Menu]) -> Set<Int> {
        var occupied = Set<Int>()
        for menu in permanents {
            if let days = menu.recurringDays, !days.isEmpty {
                days.forEach { occupied.insert($0) }
            } else {
                (0...6).forEach { occupied.insert($0) }
            }
        }
        return occupied
    }
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
xcodebuild test \
  -project "Zampa-iOS/Zampa.xcodeproj" \
  -scheme Zampa \
  -destination "platform=iOS Simulator,name=iPhone 16,OS=latest" \
  -only-testing "EatOutTests/RecurringDaysTests" 2>&1 | grep -E "error:|FAILED|PASSED|Test Suite"
```

Expected: All 10 tests PASSED.

- [ ] **Step 6: Commit**

```bash
git add "Zampa-iOS/Zampa/Core/Models/Menu.swift" "Zampa-iOS/EatOutTests/RecurringDaysTests.swift"
git commit -m "feat(ios): add recurringDays to Menu model with filter helpers"
```

---

## Task 3: iOS — FirebaseService + MenuService: read, write, and filter `recurringDays`

**Files:**
- Modify: `Zampa-iOS/Zampa/Services/FirebaseService.swift`
- Modify: `Zampa-iOS/Zampa/Services/MenuService.swift`

- [ ] **Step 1: Add `recurringDays` to `parseMenu` helper (line ~963)**

In `parseMenu(doc:)`, the return statement creates a `Menu`. Replace it with:

```swift
    private func parseMenu(doc: QueryDocumentSnapshot) -> Menu? {
        let data = doc.data()
        guard let businessId = data["businessId"] as? String,
              let title = data["title"] as? String,
              let priceTotal = data["priceTotal"] as? Double else {
            return nil
        }

        let dietaryInfo = DietaryInfo.from(data["dietaryInfo"] as? [String: Any] ?? [:])
        let recurringDays: [Int]? = {
            guard let arr = data["recurringDays"] as? [Any] else { return nil }
            return arr.compactMap {
                if let n = $0 as? Int { return n }
                if let n = $0 as? Int64 { return Int(n) }
                return nil
            }
        }()
        return Menu(
            id: doc.documentID,
            businessId: businessId,
            date: data["date"] as? String ?? "",
            title: title,
            description: data["description"] as? String,
            priceTotal: priceTotal,
            currency: data["currency"] as? String ?? "EUR",
            photoUrls: data["photoUrls"] as? [String] ?? [],
            tags: data["tags"] as? [String],
            createdAt: data["createdAt"] as? String ?? "",
            updatedAt: data["updatedAt"] as? String ?? "",
            isActive: data["isActive"] as? Bool ?? true,
            isMerchantPro: data["isMerchantPro"] as? Bool ?? false,
            dietaryInfo: dietaryInfo,
            offerType: data["offerType"] as? String,
            includesDrink: data["includesDrink"] as? Bool ?? false,
            includesDessert: data["includesDessert"] as? Bool ?? false,
            includesCoffee: data["includesCoffee"] as? Bool ?? false,
            serviceTime: data["serviceTime"] as? String ?? "both",
            isPermanent: data["isPermanent"] as? Bool ?? false,
            recurringDays: recurringDays
        )
    }
```

- [ ] **Step 2: Add weekday filter to `getActiveMenus` (line ~415–418)**

Replace the `activeMenus` filter line:

```swift
        // Old:
        let activeMenus = menus.filter { $0.isToday }
```

With:

```swift
        // Today's weekday in 0=Mon…6=Sun convention
        let calWeekday = Calendar.current.component(.weekday, from: Date())
        let todayWeekday = calWeekday == 1 ? 6 : calWeekday - 2
        let activeMenus = menus.filter { $0.isToday && $0.isVisibleOnDay(todayWeekday) }
```

- [ ] **Step 3: Add `recurringDays` to `getMenuById` (line ~449)**

In `getMenuById`, the return statement creates a `Menu`. Add `recurringDays` to it:

```swift
        let recurringDays: [Int]? = {
            guard let arr = data["recurringDays"] as? [Any] else { return nil }
            return arr.compactMap {
                if let n = $0 as? Int { return n }
                if let n = $0 as? Int64 { return Int(n) }
                return nil
            }
        }()
        return Menu(id: id, businessId: businessId, date: date, title: title,
                    description: description, priceTotal: priceTotal, currency: currency,
                    photoUrls: photoUrls, tags: tags, createdAt: createdAt, updatedAt: updatedAt,
                    isActive: isActive, isMerchantPro: isMerchantPro, dietaryInfo: dietaryInfo,
                    offerType: data["offerType"] as? String,
                    includesDrink: data["includesDrink"] as? Bool ?? false,
                    includesDessert: data["includesDessert"] as? Bool ?? false,
                    includesCoffee: data["includesCoffee"] as? Bool ?? false,
                    serviceTime: data["serviceTime"] as? String ?? "both",
                    isPermanent: data["isPermanent"] as? Bool ?? false,
                    recurringDays: recurringDays)
```

- [ ] **Step 4: Add `recurringDays` to `getMenusByMerchant` mapping (line ~459)**

In `getMenusByMerchant`, the `compactMap` closure creates `Menu` objects. Add the `recurringDays` extraction and pass it through:

```swift
        return snapshot.documents.compactMap { doc -> Menu? in
            guard let data = doc.data() as? [String: Any] else { return nil }
            let recurringDays: [Int]? = {
                guard let arr = data["recurringDays"] as? [Any] else { return nil }
                return arr.compactMap {
                    if let n = $0 as? Int { return n }
                    if let n = $0 as? Int64 { return Int(n) }
                    return nil
                }
            }()
            return parseMenu(doc: doc).map { menu in
                // parseMenu already handles recurringDays — delegate to it
            }
        }
```

Wait — `getMenusByMerchant` uses `parseMenu(doc:)` indirectly? Let me re-read. Looking at lines 453–460:

```swift
        return snapshot.documents.compactMap { parseMenu(doc: $0) }
```

Actually `getMenusByMerchant` already calls `parseMenu`. Since we updated `parseMenu` in Step 1, `getMenusByMerchant` automatically gets `recurringDays` for free. No additional change needed here. ✓

- [ ] **Step 5: Add `recurringDays` param to `createMenu` in `FirebaseService.swift`**

Update the function signature (line 463). Add `recurringDays: [Int]? = nil` after `isPermanent: Bool = false`:

```swift
    func createMenu(title: String, description: String, price: Double, currency: String = "EUR",
                    photoData: Data, tags: [String]? = nil, dietaryInfo: DietaryInfo = DietaryInfo(),
                    offerType: String? = nil, includesDrink: Bool = false, includesDessert: Bool = false,
                    includesCoffee: Bool = false, serviceTime: String = "both",
                    isPermanent: Bool = false, recurringDays: [Int]? = nil) async throws -> Menu {
```

In the `menuData` dictionary (line 504), add `recurringDays` conditionally after `"isPermanent": isPermanent`:

```swift
            "isPermanent": isPermanent
        ]
        // Only store recurringDays for permanent offers that have day selection
        if isPermanent, let days = recurringDays, !days.isEmpty {
            menuData["recurringDays"] = days
        }
```

Note: `menuData` is declared as `let` but must be `var` to add the key. Change `let menuData:` to `var menuData:` on line 504.

In the return `Menu(...)` at line 529, add `recurringDays: isPermanent ? recurringDays : nil` after `isPermanent: isPermanent`.

- [ ] **Step 6: Update `MenuService.createMenu` signature**

In `Zampa-iOS/Zampa/Services/MenuService.swift`, update the `createMenu` function:

Add `recurringDays: [Int]? = nil` after `isPermanent: Bool = false` in both the function signature and the call to `firebase.createMenu(...)`:

```swift
    func createMenu(
        title: String,
        description: String,
        price: Double,
        currency: String = "EUR",
        photoData: Data,
        tags: [String]? = nil,
        dietaryInfo: DietaryInfo = DietaryInfo(),
        offerType: String? = nil,
        includesDrink: Bool = false,
        includesDessert: Bool = false,
        includesCoffee: Bool = false,
        serviceTime: String = "both",
        isPermanent: Bool = false,
        recurringDays: [Int]? = nil
    ) async throws -> Menu {
        return try await firebase.createMenu(
            title: title,
            description: description,
            price: price,
            currency: currency,
            photoData: photoData,
            tags: tags,
            dietaryInfo: dietaryInfo,
            offerType: offerType,
            includesDrink: includesDrink,
            includesDessert: includesDessert,
            includesCoffee: includesCoffee,
            serviceTime: serviceTime,
            isPermanent: isPermanent,
            recurringDays: recurringDays
        )
    }
```

- [ ] **Step 7: Build to confirm no compile errors**

```bash
xcodebuild build \
  -project "Zampa-iOS/Zampa.xcodeproj" \
  -scheme Zampa \
  -destination "platform=iOS Simulator,name=iPhone 16,OS=latest" 2>&1 | grep -E "error:|BUILD SUCCEEDED|BUILD FAILED"
```

Expected: `BUILD SUCCEEDED`

- [ ] **Step 8: Commit**

```bash
git add "Zampa-iOS/Zampa/Services/FirebaseService.swift" "Zampa-iOS/Zampa/Services/MenuService.swift"
git commit -m "feat(ios): read/write recurringDays in Firebase service + weekday feed filter"
```

---

## Task 4: iOS — `RecurringDaysPicker` view component

**Files:**
- Create: `Zampa-iOS/Zampa/Features/Merchant/RecurringDaysPicker.swift`

- [ ] **Step 1: Create the component**

```swift
import SwiftUI

/// Day-of-week picker for permanent offers.
/// weekday convention: 0=Monday … 6=Sunday (European Mon-first order).
struct RecurringDaysPicker: View {
    @ObservedObject var localization = LocalizationManager.shared

    /// Days already occupied by other permanent offers of this merchant.
    let occupiedDays: Set<Int>
    /// The merchant's current selection for this offer (excludes occupied).
    @Binding var selectedDays: Set<Int>

    /// Calendar symbols ordered Mon…Sun. Uses device locale automatically.
    private var orderedSymbols: [String] {
        let symbols = Calendar.current.veryShortWeekdaySymbols // index 0=Sun,1=Mon…6=Sat
        // Reorder to Mon-first: [1,2,3,4,5,6,0]
        let order = [1, 2, 3, 4, 5, 6, 0]
        return order.map { symbols[$0] }
    }

    private var freeSlotsCount: Int {
        7 - occupiedDays.count
    }

    private var allOccupied: Bool {
        occupiedDays.count >= 7
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack {
                Text(localization.t("create_menu_recurring_days_title"))
                    .font(.custom("Sora-SemiBold", size: 14))
                    .foregroundColor(.appTextPrimary)
                Spacer()
                Text(String(format: localization.t("create_menu_recurring_days_slots_free"), freeSlotsCount))
                    .font(.custom("Sora-SemiBold", size: 12))
                    .foregroundColor(.appPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Color.appPrimarySurface))
            }

            // Day buttons
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { dayIndex in
                    let label = orderedSymbols[dayIndex]
                    let isOccupied = occupiedDays.contains(dayIndex)
                    let isSelected = selectedDays.contains(dayIndex)

                    Button {
                        if !isOccupied {
                            if isSelected {
                                selectedDays.remove(dayIndex)
                            } else {
                                selectedDays.insert(dayIndex)
                            }
                        }
                    } label: {
                        Text(label)
                            .font(.custom("Sora-Bold", size: 13))
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                            .foregroundColor(isSelected ? .white : isOccupied ? Color.appTextSecondary.opacity(0.4) : .appTextPrimary)
                            .background(
                                Circle().fill(
                                    isSelected ? Color.appPrimary
                                    : isOccupied ? Color.appInputBackground
                                    : Color.appSurface
                                )
                            )
                            .overlay(
                                Circle().stroke(
                                    isSelected ? Color.appPrimary
                                    : isOccupied ? Color.clear
                                    : Color.appTextSecondary.opacity(0.3),
                                    lineWidth: 1.5
                                )
                            )
                    }
                    .disabled(isOccupied)
                    .buttonStyle(.borderless)
                }
            }

            // All-occupied warning
            if allOccupied {
                Text(localization.t("create_menu_recurring_days_all_occupied"))
                    .font(.custom("Sora-Regular", size: 12))
                    .foregroundColor(.appTextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.appPrimarySurface.opacity(0.5)))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.appPrimary.opacity(0.25), lineWidth: 1.5)
        )
    }
}

#Preview {
    VStack(spacing: 20) {
        RecurringDaysPicker(
            occupiedDays: [1, 3],
            selectedDays: .constant(Set([0, 2]))
        )
        RecurringDaysPicker(
            occupiedDays: Set(0...6),
            selectedDays: .constant(Set())
        )
    }
    .padding()
}
```

- [ ] **Step 2: Build to confirm the component compiles**

```bash
xcodebuild build \
  -project "Zampa-iOS/Zampa.xcodeproj" \
  -scheme Zampa \
  -destination "platform=iOS Simulator,name=iPhone 16,OS=latest" 2>&1 | grep -E "error:|BUILD SUCCEEDED|BUILD FAILED"
```

Expected: `BUILD SUCCEEDED`

- [ ] **Step 3: Commit**

```bash
git add "Zampa-iOS/Zampa/Features/Merchant/RecurringDaysPicker.swift"
git commit -m "feat(ios): add RecurringDaysPicker view component"
```

---

## Task 5: iOS — `CreateMenuView` + `CreateMenuViewModel`

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Merchant/CreateMenuView.swift`

- [ ] **Step 1: Add `recurringDays` and `occupiedDays` to `CreateMenuViewModel`**

In `CreateMenuViewModel` class body (after `@Published var serviceTime`), add:

```swift
    @Published var recurringDays: Set<Int> = []
    @Published var occupiedDays: Set<Int> = []
```

Replace `var isValid: Bool` with:

```swift
    var isValid: Bool {
        guard !title.isEmpty && price > 0 && selectedImage != nil else { return false }
        if offerType == OfferTypes.ofertaPermanente {
            return !recurringDays.isEmpty
        }
        return true
    }
```

Add a method to load occupied days (after `init()`):

```swift
    @MainActor
    func loadOccupiedDays() async {
        guard let uid = FirebaseService.shared.currentFirebaseUser?.uid else { return }
        let all = (try? await FirebaseService.shared.getMenusByMerchant(merchantId: uid)) ?? []
        let activePermanents = all.filter { $0.isPermanent && $0.isActive }
        self.occupiedDays = Menu.occupiedDays(from: activePermanents)
    }
```

In `createMenu()`, update the `MenuService.shared.createMenu(...)` call to pass `recurringDays`:

```swift
            _ = try await MenuService.shared.createMenu(
                title: title,
                description: description,
                price: price,
                currency: "EUR",
                photoData: imageData,
                tags: selectedTags,
                dietaryInfo: dietaryInfo,
                offerType: offerType,
                includesDrink: includesDrink,
                includesDessert: includesDessert,
                includesCoffee: includesCoffee,
                serviceTime: serviceTime,
                isPermanent: offerType == OfferTypes.ofertaPermanente,
                recurringDays: offerType == OfferTypes.ofertaPermanente ? Array(recurringDays) : nil
            )
```

- [ ] **Step 2: Show the day picker in `CreateMenuView.body`**

In `CreateMenuView.body`, inside the `VStack`, after `OfferDetailsSection(...)` and before the title `CustomTextField`, add:

```swift
                    // ── RECURRING DAYS (permanent offers only) ──────────
                    if viewModel.offerType == OfferTypes.ofertaPermanente {
                        RecurringDaysPicker(
                            occupiedDays: viewModel.occupiedDays,
                            selectedDays: $viewModel.recurringDays
                        )
                    }
```

- [ ] **Step 3: Load occupied days when the view appears**

Add `.task { await viewModel.loadOccupiedDays() }` to the `ScrollView` or `NavigationView`:

```swift
            .navigationTitle(localization.t("create_menu_title"))
            .navigationBarTitleDisplayMode(.inline)
            .task { await viewModel.loadOccupiedDays() }
```

- [ ] **Step 4: Build and verify**

```bash
xcodebuild build \
  -project "Zampa-iOS/Zampa.xcodeproj" \
  -scheme Zampa \
  -destination "platform=iOS Simulator,name=iPhone 16,OS=latest" 2>&1 | grep -E "error:|BUILD SUCCEEDED|BUILD FAILED"
```

Expected: `BUILD SUCCEEDED`

- [ ] **Step 5: Commit**

```bash
git add "Zampa-iOS/Zampa/Features/Merchant/CreateMenuView.swift"
git commit -m "feat(ios): integrate RecurringDaysPicker into CreateMenuView"
```

---

## Task 6: iOS — `EditMenuView` + `EditMenuViewModel`

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Merchant/EditMenuView.swift`

- [ ] **Step 1: Add `recurringDays`, `occupiedDays`, `isPermanentMenu` to `EditMenuViewModel`**

In `EditMenuViewModel` class body (after `@Published var serviceTime`), add:

```swift
    @Published var recurringDays: Set<Int> = []
    @Published var occupiedDays: Set<Int> = []
    private(set) var isPermanentMenu: Bool = false
    private var editingMenuId: String = ""
```

Replace `var isValid: Bool` with:

```swift
    var isValid: Bool {
        guard !title.isEmpty && price > 0 else { return false }
        if isPermanentMenu { return !recurringDays.isEmpty }
        return true
    }
```

In `setup(with menu: Menu)`, add after `self.serviceTime = menu.serviceTime`:

```swift
        self.isPermanentMenu = menu.isPermanent
        self.editingMenuId = menu.id
        if menu.isPermanent {
            self.recurringDays = Set(menu.recurringDays ?? [])
        }
        if menu.isPermanent {
            Task { await loadOccupiedDays() }
        }
```

Add the `loadOccupiedDays` method:

```swift
    @MainActor
    func loadOccupiedDays() async {
        guard let uid = FirebaseService.shared.currentFirebaseUser?.uid else { return }
        let all = (try? await FirebaseService.shared.getMenusByMerchant(merchantId: uid)) ?? []
        // Exclude the offer being edited so its days are not counted as occupied
        let activePermanents = all.filter { $0.isPermanent && $0.isActive && $0.id != editingMenuId }
        self.occupiedDays = Menu.occupiedDays(from: activePermanents)
    }
```

In `updateMenu(menuId:)`, add `recurringDays` to `updateData` when appropriate:

```swift
        var updateData: [String: Any] = [
            "title": title,
            "description": description,
            "priceTotal": price,
            "tags": selectedTags,
            "dietaryInfo": dietaryInfo.firestoreMap,
            "offerType": offerType as Any,
            "includesDrink": includesDrink,
            "includesDessert": includesDessert,
            "includesCoffee": includesCoffee,
            "serviceTime": serviceTime
        ]

        if isPermanentMenu {
            updateData["recurringDays"] = Array(recurringDays)
        }
```

- [ ] **Step 2: Show day picker in `EditMenuView`**

Add a new computed section after `offerTypeSection`:

```swift
    @ViewBuilder
    private var recurringDaysSection: some View {
        if viewModel.isPermanentMenu {
            Section(header: Text(LocalizationManager.shared.t("create_menu_recurring_days_title"))) {
                RecurringDaysPicker(
                    occupiedDays: viewModel.occupiedDays,
                    selectedDays: $viewModel.recurringDays
                )
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
        }
    }
```

In the `Form { ... }` body, add `recurringDaysSection` after `offerTypeSection`:

```swift
            Form {
                infoSection
                tagsSection
                offerTypeSection
                recurringDaysSection
                dietarySection
                photoSection
                saveSection
                deleteSection
            }
```

- [ ] **Step 3: Build and verify**

```bash
xcodebuild build \
  -project "Zampa-iOS/Zampa.xcodeproj" \
  -scheme Zampa \
  -destination "platform=iOS Simulator,name=iPhone 16,OS=latest" 2>&1 | grep -E "error:|BUILD SUCCEEDED|BUILD FAILED"
```

Expected: `BUILD SUCCEEDED`

- [ ] **Step 4: Commit**

```bash
git add "Zampa-iOS/Zampa/Features/Merchant/EditMenuView.swift"
git commit -m "feat(ios): integrate RecurringDaysPicker into EditMenuView"
```

---

## Task 7: Android — Menu model + filter helpers + unit tests

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt`
- Modify: `Zampa-Android/app/src/test/java/com/sozolab/zampa/ModelsTest.kt`

**Same weekday convention:** 0=Monday…6=Sunday.

- [ ] **Step 1: Write failing tests first**

Replace the contents of `ModelsTest.kt` with:

```kotlin
package com.sozolab.zampa

import com.sozolab.zampa.data.model.Menu
import org.junit.Assert.*
import org.junit.Test

class RecurringDaysTest {

    // MARK: isVisibleOnDay

    @Test
    fun nonPermanentAlwaysVisible() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = false)
        assertTrue(menu.isVisibleOnDay(0))
        assertTrue(menu.isVisibleOnDay(3))
        assertTrue(menu.isVisibleOnDay(6))
    }

    @Test
    fun permanentWithoutDaysAlwaysVisible() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = null)
        assertTrue(menu.isVisibleOnDay(0))
        assertTrue(menu.isVisibleOnDay(6))
    }

    @Test
    fun permanentWithEmptyDaysAlwaysVisible() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = emptyList())
        assertTrue(menu.isVisibleOnDay(0))
    }

    @Test
    fun permanentOnlyVisibleOnSelectedDays() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(3))
        assertFalse(menu.isVisibleOnDay(0))
        assertFalse(menu.isVisibleOnDay(2))
        assertTrue(menu.isVisibleOnDay(3))
        assertFalse(menu.isVisibleOnDay(4))
    }

    @Test
    fun permanentMultipleDays() {
        val menu = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(0, 2, 4))
        assertTrue(menu.isVisibleOnDay(0))
        assertFalse(menu.isVisibleOnDay(1))
        assertTrue(menu.isVisibleOnDay(2))
        assertFalse(menu.isVisibleOnDay(3))
        assertTrue(menu.isVisibleOnDay(4))
        assertFalse(menu.isVisibleOnDay(5))
        assertFalse(menu.isVisibleOnDay(6))
    }

    // MARK: occupiedDays

    @Test
    fun occupiedDaysEmpty() {
        assertEquals(emptySet<Int>(), Menu.occupiedDays(emptyList()))
    }

    @Test
    fun occupiedDaysFromSingleOffer() {
        val m = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(1, 3, 5))
        assertEquals(setOf(1, 3, 5), Menu.occupiedDays(listOf(m)))
    }

    @Test
    fun occupiedDaysFromMultipleOffers() {
        val m1 = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(0, 1))
        val m2 = Menu(id = "2", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = listOf(4, 5))
        assertEquals(setOf(0, 1, 4, 5), Menu.occupiedDays(listOf(m1, m2)))
    }

    @Test
    fun legacyPermanentOccupiesAll() {
        val m = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = null)
        assertEquals((0..6).toSet(), Menu.occupiedDays(listOf(m)))
    }

    @Test
    fun legacyEmptyOccupiesAll() {
        val m = Menu(id = "1", title = "T", priceTotal = 5.0, isPermanent = true, recurringDays = emptyList())
        assertEquals((0..6).toSet(), Menu.occupiedDays(listOf(m)))
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd "Zampa-Android" && ./gradlew test --tests "com.sozolab.zampa.RecurringDaysTest" 2>&1 | grep -E "PASSED|FAILED|error:|BUILD"
```

Expected: build errors because `recurringDays` and `isVisibleOnDay` don't exist yet.

- [ ] **Step 3: Add `recurringDays` field to `Menu` data class in `Models.kt`**

After `val isPermanent: Boolean = false,` add:

```kotlin
    /** Días de la semana en que esta oferta permanente es visible (0=lun…6=dom). Null = todos los días (legado). */
    val recurringDays: List<Int>? = null,
```

- [ ] **Step 4: Add `isVisibleOnDay` and `occupiedDays` to the `Menu` class**

At the bottom of the `Menu` class body (after `serviceTimeLabel`), add:

```kotlin
    /** Returns true if this offer should appear in the feed on the given weekday index (0=Mon…6=Sun). */
    fun isVisibleOnDay(weekday: Int): Boolean {
        if (!isPermanent) return true
        val days = recurringDays ?: return true
        if (days.isEmpty()) return true
        return weekday in days
    }

    companion object {
        /** Returns the set of weekday indices (0=Mon…6=Sun) already occupied by the given permanent offers. */
        fun occupiedDays(permanents: List<Menu>): Set<Int> {
            val occupied = mutableSetOf<Int>()
            for (menu in permanents) {
                val days = menu.recurringDays
                if (days == null || days.isEmpty()) {
                    occupied.addAll(0..6)
                } else {
                    occupied.addAll(days)
                }
            }
            return occupied
        }
    }
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
cd "Zampa-Android" && ./gradlew test --tests "com.sozolab.zampa.RecurringDaysTest" 2>&1 | grep -E "PASSED|FAILED|BUILD"
```

Expected: `BUILD SUCCESSFUL`, all 10 tests PASSED.

- [ ] **Step 6: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt" \
        "Zampa-Android/app/src/test/java/com/sozolab/zampa/ModelsTest.kt"
git commit -m "feat(android): add recurringDays to Menu model with filter helpers"
```

---

## Task 8: Android — `FirebaseService.kt`: read, write, and filter `recurringDays`

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`

- [ ] **Step 1: Add weekday filter to `getActiveMenus` (line ~216)**

Replace:
```kotlin
        val activeMenus = menus.filter { it.isToday }
```

With:
```kotlin
        // Today's weekday in 0=Mon…6=Sun convention
        val calWeekday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val todayWeekday = if (calWeekday == java.util.Calendar.SUNDAY) 6 else calWeekday - 2
        val activeMenus = menus.filter { it.isToday && it.isVisibleOnDay(todayWeekday) }
```

- [ ] **Step 2: Add `recurringDays` to the `Menu(...)` constructor in `getActiveMenus`**

In the `mapNotNull` lambda (lines 188–213), after `isPermanent = d["isPermanent"] as? Boolean ?: false,` add:

```kotlin
                recurringDays = (d["recurringDays"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() },
```

- [ ] **Step 3: Add `recurringDays` to `getMenusByMerchant` mapping**

In the `mapNotNull` lambda (lines 226–249), after `isPermanent = d["isPermanent"] as? Boolean ?: false,` add:

```kotlin
                recurringDays = (d["recurringDays"] as? List<*>)?.mapNotNull { (it as? Long)?.toInt() },
```

- [ ] **Step 4: Add `recurringDays` param to `createMenu` + write to Firestore**

Update the function signature (line 252). Add `recurringDays: List<Int>? = null` after `isPermanent: Boolean = false`:

```kotlin
    suspend fun createMenu(title: String, description: String, price: Double, currency: String = "EUR",
                           photoData: ByteArray, tags: List<String>? = null, dietaryInfo: DietaryInfo = DietaryInfo(),
                           offerType: String? = null, includesDrink: Boolean = false,
                           includesDessert: Boolean = false, includesCoffee: Boolean = false,
                           serviceTime: String = "both", isPermanent: Boolean = false,
                           recurringDays: List<Int>? = null): Menu {
```

After `offerType?.let { menuData["offerType"] = it }` (line 295), add:

```kotlin
        if (isPermanent && recurringDays != null && recurringDays.isNotEmpty()) {
            menuData["recurringDays"] = recurringDays
        }
```

In the `return Menu(...)` at line 298, add `recurringDays = if (isPermanent) recurringDays else null` to the constructor call.

- [ ] **Step 5: Build to confirm no compile errors**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD SUCCESSFUL|BUILD FAILED"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt"
git commit -m "feat(android): read/write recurringDays in Firebase service + weekday feed filter"
```

---

## Task 9: Android — `RecurringDaysPicker` composable

**Files:**
- Create: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/RecurringDaysPicker.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package com.sozolab.zampa.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.R
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * Day-of-week picker for permanent offers.
 * weekday convention: 0=Monday … 6=Sunday (European Mon-first order).
 *
 * @param occupiedDays Days already claimed by other permanents of this merchant.
 * @param selectedDays The current selection for this offer (mutable set).
 * @param onSelectionChange Called when the user toggles a day.
 */
@Composable
fun RecurringDaysPicker(
    occupiedDays: Set<Int>,
    selectedDays: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit
) {
    // Locale-aware short weekday names ordered Mon…Sun
    val rawSymbols = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
    // rawSymbols[0] is empty, [1]=Sun, [2]=Mon, … [7]=Sat
    val orderedSymbols = listOf(
        rawSymbols[2], // Mon
        rawSymbols[3], // Tue
        rawSymbols[4], // Wed
        rawSymbols[5], // Thu
        rawSymbols[6], // Fri
        rawSymbols[7], // Sat
        rawSymbols[1]  // Sun
    )

    val freeSlotsCount = 7 - occupiedDays.size
    val allOccupied = occupiedDays.size >= 7
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = primary.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.create_menu_recurring_days_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Text(
                        text = stringResource(R.string.create_menu_recurring_days_slots_free, freeSlotsCount),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Day buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (dayIndex in 0..6) {
                    val label = orderedSymbols[dayIndex].take(2)
                    val isOccupied = dayIndex in occupiedDays
                    val isSelected = dayIndex in selectedDays

                    val bgColor = when {
                        isSelected -> primary
                        isOccupied -> surface.copy(alpha = 0.5f)
                        else -> surface
                    }
                    val textColor = when {
                        isSelected -> Color.White
                        isOccupied -> onSurface.copy(alpha = 0.3f)
                        else -> onSurface
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(bgColor)
                            .then(
                                if (!isOccupied && !isSelected)
                                    Modifier.border(1.5.dp, onSurface.copy(alpha = 0.2f), CircleShape)
                                else Modifier
                            )
                            .clickable(enabled = !isOccupied) {
                                val newSet = selectedDays.toMutableSet()
                                if (isSelected) newSet.remove(dayIndex) else newSet.add(dayIndex)
                                onSelectionChange(newSet)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // All-occupied warning
            if (allOccupied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.create_menu_recurring_days_all_occupied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD SUCCESSFUL|BUILD FAILED"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/RecurringDaysPicker.kt"
git commit -m "feat(android): add RecurringDaysPicker composable"
```

---

## Task 10: Android — `DashboardViewModel` + `CreateMenuSheet`

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt`

- [ ] **Step 1: Update `DashboardViewModel.createMenu` and `updateMenu` signatures**

Replace the current `createMenu` function:

```kotlin
    fun createMenu(title: String, description: String, price: Double, photoData: ByteArray,
                   tags: List<String>, dietaryInfo: DietaryInfo = DietaryInfo(),
                   offerType: String? = null, includesDrink: Boolean = false,
                   includesDessert: Boolean = false, includesCoffee: Boolean = false,
                   serviceTime: String = "both", isPermanent: Boolean = false,
                   recurringDays: List<Int>? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                firebaseService.createMenu(title, description, price, "EUR", photoData, tags,
                    dietaryInfo, offerType, includesDrink, includesDessert, includesCoffee,
                    serviceTime, isPermanent, recurringDays)
                _createSuccess.value = true
                loadMenus()
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
        }
    }
```

Also update the `updateMenu` function to accept `recurringDays`:

```kotlin
    fun updateMenu(menuId: String, title: String, description: String, price: Double,
                   tags: List<String>, photoData: ByteArray?, dietaryInfo: DietaryInfo = DietaryInfo(),
                   offerType: String? = null, includesDrink: Boolean = false,
                   includesDessert: Boolean = false, includesCoffee: Boolean = false,
                   recurringDays: List<Int>? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updateData = mutableMapOf<String, Any>(
                    "title" to title, "description" to description, "priceTotal" to price,
                    "tags" to tags, "dietaryInfo" to dietaryInfo.toMap(),
                    "includesDrink" to includesDrink, "includesDessert" to includesDessert,
                    "includesCoffee" to includesCoffee
                )
                offerType?.let { updateData["offerType"] = it }
                recurringDays?.let { updateData["recurringDays"] = it }
                if (photoData != null) {
                    val imagePath = "dailyOffers/${java.util.UUID.randomUUID()}.jpg"
                    val photoUrl = firebaseService.uploadImage(photoData, imagePath)
                    updateData["photoUrls"] = listOf(photoUrl)
                }
                firebaseService.updateMenu(menuId, updateData)
                _updateSuccess.value = true
                loadMenus()
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            }
            _isLoading.value = false
        }
    }
```

- [ ] **Step 2: Add `loadOccupiedDays` to `DashboardViewModel`**

Add a `StateFlow` for occupied days and a load method:

```kotlin
    private val _occupiedDays = MutableStateFlow<Set<Int>>(emptySet())
    val occupiedDays: StateFlow<Set<Int>> = _occupiedDays.asStateFlow()

    fun loadOccupiedDays(excludingMenuId: String? = null) {
        viewModelScope.launch {
            val uid = firebaseService.currentUid ?: return@launch
            val all = try { firebaseService.getMenusByMerchant(uid) } catch (_: Exception) { emptyList() }
            val activePermanents = all.filter { it.isPermanent && it.isActive && it.id != excludingMenuId }
            _occupiedDays.value = Menu.occupiedDays(activePermanents)
        }
    }
```

- [ ] **Step 3: Integrate picker in `CreateMenuSheet`**

In `CreateMenuSheet`, add state variables for `recurringDays`:

```kotlin
    val selectedDays = remember { mutableStateOf(emptySet<Int>()) }
    val occupiedDays by viewModel.occupiedDays.collectAsState()
```

Add a `LaunchedEffect` to load occupied days when the sheet opens:

```kotlin
    LaunchedEffect(Unit) {
        viewModel.loadOccupiedDays()
        // existing cuisine types load
        availableTags = try {
            com.sozolab.zampa.data.FirebaseService().fetchCuisineTypes().map { it.name }
        } catch (_: Exception) { emptyList() }
    }
```

After `OfferDetailsSection(...)` in the `Column`, add:

```kotlin
            // Recurring days picker — only for permanent offers
            if (offerType == "Oferta permanente") {
                Spacer(Modifier.height(12.dp))
                RecurringDaysPicker(
                    occupiedDays = occupiedDays,
                    selectedDays = selectedDays.value,
                    onSelectionChange = { selectedDays.value = it }
                )
            }
```

Update the publish `Button.onClick` lambda to pass `recurringDays`:

```kotlin
            Button(
                onClick = {
                    imageData?.let {
                        viewModel.createMenu(
                            title, description, price, it, tags, dietary,
                            offerType, inclDrink, inclDessert, inclCoffee, serviceTime,
                            isPermanent = offerType == "Oferta permanente",
                            recurringDays = if (offerType == "Oferta permanente") selectedDays.value.sorted() else null
                        )
                    }
                },
                enabled = !isLoading && title.isNotBlank() && price > 0 && imageData != null
                    && (offerType != "Oferta permanente" || selectedDays.value.isNotEmpty()),
                ...
            )
```

- [ ] **Step 4: Build to confirm**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug 2>&1 | grep -E "error:|BUILD SUCCESSFUL|BUILD FAILED"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt" \
        "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt"
git commit -m "feat(android): integrate RecurringDaysPicker into CreateMenuSheet"
```

---

## Task 11: Android — `EditMenuSheet`

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt`

- [ ] **Step 1: Add `recurringDays` state and picker to `EditMenuSheet`**

In `EditMenuSheet(menu: Menu, ...)`, add state variables after existing ones:

```kotlin
    val isPermanentMenu = menu.isPermanent
    val selectedDays = remember { mutableStateOf(menu.recurringDays?.toSet() ?: emptySet()) }
    val occupiedDays by viewModel.occupiedDays.collectAsState()
```

Add a `LaunchedEffect` to load occupied days excluding the current menu:

```kotlin
    LaunchedEffect(Unit) {
        viewModel.loadOccupiedDays(excludingMenuId = menu.id)
    }
```

In the `EditMenuSheet` Column (after `OfferDetailsSection`), add:

```kotlin
            if (isPermanentMenu) {
                Spacer(Modifier.height(12.dp))
                RecurringDaysPicker(
                    occupiedDays = occupiedDays,
                    selectedDays = selectedDays.value,
                    onSelectionChange = { selectedDays.value = it }
                )
            }
```

- [ ] **Step 2: Pass `recurringDays` in the save/update call**

Locate the `viewModel.updateMenu(...)` call in the save button of `EditMenuSheet` and add the `recurringDays` parameter:

```kotlin
viewModel.updateMenu(
    menuId = menu.id,
    title = title,
    description = description,
    price = price,
    tags = tags,
    photoData = photoData,
    dietaryInfo = dietary,
    offerType = offerType,
    includesDrink = inclDrink,
    includesDessert = inclDessert,
    includesCoffee = inclCoffee,
    recurringDays = if (isPermanentMenu) selectedDays.value.sorted() else null
)
```

- [ ] **Step 3: Disable save if permanent has no days selected**

Find the save button's `enabled` condition and add:

```kotlin
enabled = !isLoading && title.isNotBlank() && price > 0
    && (!isPermanentMenu || selectedDays.value.isNotEmpty())
```

- [ ] **Step 4: Build and run unit tests**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug test 2>&1 | grep -E "error:|BUILD SUCCESSFUL|BUILD FAILED|PASSED|FAILED"
```

Expected: `BUILD SUCCESSFUL`, all tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt"
git commit -m "feat(android): integrate RecurringDaysPicker into EditMenuSheet"
```
