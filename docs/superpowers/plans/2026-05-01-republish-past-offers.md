# Republish past offers — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let merchants republish a past `dailyOffers` document (daily or permanent) from a Dashboard history section, with conflict detection that prompts before overwriting an active offer in the same day/time slot, and a 3/day spam guard.

**Architecture:** A pure conflict helper (added to the existing `Menu` model on both platforms) decides whether a candidate offer collides with the merchant's active set. The merchant Dashboard gains a collapsible "Ofertas anteriores" section listing inactive offers with a per-row Republicar button that opens the existing edit sheet pre-filled. Publishing routes through a new `FirebaseService.republishMenu(...)` that runs an atomic `WriteBatch` (deactivate conflicts + create new doc with `republishedFrom`/`republishedAt`). A Cloud Function update enforces the daily limit hard-side and skips push fanout when the limit is exceeded.

**Tech Stack:** Swift / SwiftUI (iOS), Kotlin / Jetpack Compose (Android), Firebase Firestore + Cloud Functions (Node 22), Firebase Storage. Spec at `docs/superpowers/specs/2026-05-01-republish-past-offers-design.md`.

---

## File Structure

| Path | Action | Responsibility |
| --- | --- | --- |
| `Zampa-iOS/Zampa/Core/Models/Menu.swift` | modify | Add `republishedFrom`, `republishedAt`; add `conflicts(with:)` and `findConflicts(for:in:on:)` helpers. |
| `Zampa-iOS/EatOutTests/MenuConflictTests.swift` | create | Unit tests for the conflict helper (all `serviceTime` × type combinations). |
| `Zampa-iOS/Zampa/Services/FirebaseService.swift` | modify | Add `getRepublishLimit()`, `countTodayRepublishes(...)`, `republishMenu(...)`. Update `parseMenu` to read the two new fields. |
| `Zampa-iOS/Zampa/Services/MenuService.swift` | modify | Thin pass-through `republishMenu(...)` matching `createMenu(...)` style. |
| `Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift` | modify | Add collapsible "Ofertas anteriores" section + Republicar entry + soft-limit handling. |
| `Zampa-iOS/Zampa/Features/Merchant/EditMenuView.swift` | modify | Accept `mode: .edit \| .republish` so the same view handles both. |
| `Zampa-iOS/Zampa/Localization/{ca,de,en,es,eu,fr,gl,it}.json` | modify | Add 14 new keys (see Task 9). |
| `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt` | modify | Add fields + `conflicts(with:)` + `findConflicts(...)` (mirror of iOS). |
| `Zampa-Android/app/src/test/java/com/sozolab/zampa/MenuConflictTest.kt` | create | Unit tests mirroring iOS. |
| `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt` | modify | Add `getRepublishLimit()`, `countTodayRepublishes(...)`, `republishMenu(...)`. Update `getMenusByMerchant` and `getActiveMenus` to read the two new fields. |
| `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt` | modify | History section + Republicar entry + soft-limit toast + republish-mode wiring on `EditMenuSheet`. |
| `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt` | modify | Add `republishMenu(...)` and `republishLimit` state. |
| `Zampa-Android/app/src/main/res/values{,-ca,-de,-en,-eu,-fr,-gl,-it}/strings.xml` | modify | Add the 14 new keys. |
| `firebase/firestore.rules` | modify | Validate `republishedFrom` (immutable, source must exist + same merchant). |
| `functions/index.js` | modify | Hard-limit branch in `onMenuPublished`. |
| `functions/test/republishLimit.test.js` | create | Emulator test for the hard limit. |
| `docs/superpowers/launch-checklist.md` | modify (or create snippet) | Document the manual `config/republishLimits` seed step. |

---

### Task 1: iOS conflict detection helper + tests

**Files:**
- Modify: `Zampa-iOS/Zampa/Core/Models/Menu.swift:194` (add helpers below the existing `occupiedDays(from:)`)
- Create: `Zampa-iOS/EatOutTests/MenuConflictTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `Zampa-iOS/EatOutTests/MenuConflictTests.swift`:

```swift
import XCTest
@testable import Zampa

final class MenuConflictTests: XCTestCase {

    private func daily(_ id: String, serviceTime: String = "both", isActive: Bool = true) -> Menu {
        Menu(id: id, businessId: "b", date: "", title: id, priceTotal: 5,
             isActive: isActive, serviceTime: serviceTime, isPermanent: false)
    }

    private func permanent(_ id: String, days: [Int]? = nil, serviceTime: String = "both",
                           isActive: Bool = true) -> Menu {
        Menu(id: id, businessId: "b", date: "", title: id, priceTotal: 5,
             isActive: isActive, serviceTime: serviceTime, isPermanent: true,
             recurringDays: days)
    }

    /// Today fixed at Wednesday (weekday=2 in 0=Mon..6=Sun).
    private let weekday = 2

    func testDailyVsDailySameServiceTimeConflicts() {
        let c = daily("c", serviceTime: "lunch")
        let o = daily("o", serviceTime: "lunch")
        XCTAssertTrue(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testDailyVsDailyDifferentServiceTimeNoConflict() {
        let c = daily("c", serviceTime: "lunch")
        let o = daily("o", serviceTime: "dinner")
        XCTAssertFalse(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testBothOverlapsLunch() {
        let c = daily("c", serviceTime: "both")
        let o = daily("o", serviceTime: "lunch")
        XCTAssertTrue(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testBothOverlapsDinner() {
        let c = daily("c", serviceTime: "both")
        let o = daily("o", serviceTime: "dinner")
        XCTAssertTrue(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testDailyVsPermanentTodayInDaysConflicts() {
        let c = daily("c", serviceTime: "lunch")
        let o = permanent("o", days: [weekday, 5], serviceTime: "lunch")
        XCTAssertTrue(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testDailyVsPermanentTodayNotInDaysNoConflict() {
        let c = daily("c", serviceTime: "lunch")
        let o = permanent("o", days: [0, 5], serviceTime: "lunch")
        XCTAssertFalse(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testDailyVsPermanentNullDaysAlwaysConflicts() {
        let c = daily("c", serviceTime: "lunch")
        let o = permanent("o", days: nil, serviceTime: "lunch")
        XCTAssertTrue(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testPermanentVsPermanentOverlappingDaysConflicts() {
        let c = permanent("c", days: [0, 1, 2], serviceTime: "lunch")
        let o = permanent("o", days: [2, 3], serviceTime: "lunch")
        XCTAssertTrue(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testPermanentVsPermanentNonOverlappingDaysNoConflict() {
        let c = permanent("c", days: [0, 1], serviceTime: "lunch")
        let o = permanent("o", days: [2, 3], serviceTime: "lunch")
        XCTAssertFalse(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testPermanentVsPermanentNullVsAnyConflicts() {
        let c = permanent("c", days: nil, serviceTime: "lunch")
        let o = permanent("o", days: [4], serviceTime: "lunch")
        XCTAssertTrue(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testInactiveOfferIgnored() {
        let c = daily("c", serviceTime: "lunch")
        let o = daily("o", serviceTime: "lunch", isActive: false)
        XCTAssertFalse(c.conflicts(with: o, todayWeekday: weekday))
    }

    func testFindConflictsReturnsList() {
        let c = daily("c", serviceTime: "both")
        let active: [Menu] = [
            daily("o1", serviceTime: "lunch"),
            daily("o2", serviceTime: "dinner"),
            daily("o3", serviceTime: "lunch", isActive: false),
        ]
        let result = Menu.findConflicts(for: c, in: active, todayWeekday: weekday)
        XCTAssertEqual(Set(result.map { $0.id }), Set(["o1", "o2"]))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Open `Zampa-iOS/Zampa.xcodeproj` in Xcode, ⌘U on the EatOutTests target.
Expected: 12 failures, all citing `Value of type 'Menu' has no member 'conflicts'` (or similar).

- [ ] **Step 3: Add helpers to `Menu.swift`**

In `Zampa-iOS/Zampa/Core/Models/Menu.swift`, append below the existing `occupiedDays(from:)` (around line 194), still inside the `Menu` struct:

```swift
    /// Returns true if this candidate offer collides with `other` when the candidate would
    /// publish on a day whose weekday index is `todayWeekday` (0=Mon…6=Sun).
    /// Conflict ⇔ they share at least one calendar day AND at least one serviceTime,
    /// where "both" overlaps with "lunch", "dinner" and "both".
    /// Inactive offers never conflict.
    func conflicts(with other: Menu, todayWeekday: Int) -> Bool {
        guard other.isActive, self.id != other.id else { return false }
        if Menu.serviceTimes(for: self).isDisjoint(with: Menu.serviceTimes(for: other)) {
            return false
        }
        return !Menu.activeDays(for: self, todayWeekday: todayWeekday)
            .isDisjoint(with: Menu.activeDays(for: other, todayWeekday: todayWeekday))
    }

    /// Returns the active offers (out of `existing`) that conflict with `candidate`.
    static func findConflicts(for candidate: Menu, in existing: [Menu], todayWeekday: Int) -> [Menu] {
        existing.filter { candidate.conflicts(with: $0, todayWeekday: todayWeekday) }
    }

    private static func serviceTimes(for menu: Menu) -> Set<String> {
        switch menu.serviceTime {
        case "lunch": return ["lunch"]
        case "dinner": return ["dinner"]
        default: return ["lunch", "dinner"]
        }
    }

    /// Set of weekday indices (0=Mon…6=Sun) on which the menu would be active in the upcoming week.
    /// - Daily offers occupy only `todayWeekday`.
    /// - Permanent offers occupy their `recurringDays`, or all 7 days if null/empty.
    private static func activeDays(for menu: Menu, todayWeekday: Int) -> Set<Int> {
        if menu.isPermanent {
            if let d = menu.recurringDays, !d.isEmpty { return Set(d) }
            return Set(0...6)
        }
        return [todayWeekday]
    }
```

- [ ] **Step 4: Run tests to verify they pass**

⌘U on the EatOutTests target.
Expected: all 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Zampa-iOS/Zampa/Core/Models/Menu.swift Zampa-iOS/EatOutTests/MenuConflictTests.swift
git commit -m "feat(ios): add Menu.conflicts(with:todayWeekday:) helper + tests"
```

---

### Task 2: Android conflict detection helper + tests

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt:212` (extend the `companion object`)
- Create: `Zampa-Android/app/src/test/java/com/sozolab/zampa/MenuConflictTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `Zampa-Android/app/src/test/java/com/sozolab/zampa/MenuConflictTest.kt`:

```kotlin
package com.sozolab.zampa

import com.sozolab.zampa.data.model.Menu
import org.junit.Assert.*
import org.junit.Test

class MenuConflictTest {

    private val weekday = 2 // Wednesday

    private fun daily(id: String, serviceTime: String = "both", isActive: Boolean = true) =
        Menu(id = id, businessId = "b", title = id, priceTotal = 5.0,
            isActive = isActive, serviceTime = serviceTime, isPermanent = false)

    private fun permanent(id: String, days: List<Int>? = null, serviceTime: String = "both",
                          isActive: Boolean = true) =
        Menu(id = id, businessId = "b", title = id, priceTotal = 5.0,
            isActive = isActive, serviceTime = serviceTime, isPermanent = true,
            recurringDays = days)

    @Test fun dailyVsDailySameServiceTimeConflicts() {
        assertTrue(daily("c", "lunch").conflictsWith(daily("o", "lunch"), weekday))
    }

    @Test fun dailyVsDailyDifferentServiceTimeNoConflict() {
        assertFalse(daily("c", "lunch").conflictsWith(daily("o", "dinner"), weekday))
    }

    @Test fun bothOverlapsLunch() {
        assertTrue(daily("c", "both").conflictsWith(daily("o", "lunch"), weekday))
    }

    @Test fun bothOverlapsDinner() {
        assertTrue(daily("c", "both").conflictsWith(daily("o", "dinner"), weekday))
    }

    @Test fun dailyVsPermanentTodayInDaysConflicts() {
        assertTrue(daily("c", "lunch")
            .conflictsWith(permanent("o", listOf(weekday, 5), "lunch"), weekday))
    }

    @Test fun dailyVsPermanentTodayNotInDaysNoConflict() {
        assertFalse(daily("c", "lunch")
            .conflictsWith(permanent("o", listOf(0, 5), "lunch"), weekday))
    }

    @Test fun dailyVsPermanentNullDaysAlwaysConflicts() {
        assertTrue(daily("c", "lunch")
            .conflictsWith(permanent("o", null, "lunch"), weekday))
    }

    @Test fun permanentVsPermanentOverlappingDaysConflicts() {
        assertTrue(permanent("c", listOf(0, 1, 2), "lunch")
            .conflictsWith(permanent("o", listOf(2, 3), "lunch"), weekday))
    }

    @Test fun permanentVsPermanentNonOverlappingDaysNoConflict() {
        assertFalse(permanent("c", listOf(0, 1), "lunch")
            .conflictsWith(permanent("o", listOf(2, 3), "lunch"), weekday))
    }

    @Test fun permanentVsPermanentNullVsAnyConflicts() {
        assertTrue(permanent("c", null, "lunch")
            .conflictsWith(permanent("o", listOf(4), "lunch"), weekday))
    }

    @Test fun inactiveOfferIgnored() {
        assertFalse(daily("c", "lunch")
            .conflictsWith(daily("o", "lunch", isActive = false), weekday))
    }

    @Test fun findConflictsReturnsList() {
        val c = daily("c", "both")
        val existing = listOf(
            daily("o1", "lunch"),
            daily("o2", "dinner"),
            daily("o3", "lunch", isActive = false),
        )
        val result = Menu.findConflicts(c, existing, weekday).map { it.id }.toSet()
        assertEquals(setOf("o1", "o2"), result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd Zampa-Android && ./gradlew :app:testDebugUnitTest --tests "com.sozolab.zampa.MenuConflictTest"
```

Expected: build error / compile failure citing `unresolved reference: conflictsWith`.

- [ ] **Step 3: Add helpers to `Models.kt`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt`, extend the `Menu` data class (instance method) and `companion object`. Add the instance method right above `companion object`:

```kotlin
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
```

Inside the existing `companion object`, append:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd Zampa-Android && ./gradlew :app:testDebugUnitTest --tests "com.sozolab.zampa.MenuConflictTest"
```

Expected: BUILD SUCCESSFUL, 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt \
        Zampa-Android/app/src/test/java/com/sozolab/zampa/MenuConflictTest.kt
git commit -m "feat(android): add Menu.conflictsWith(...) helper + tests"
```

---

### Task 3: Add `republishedFrom` / `republishedAt` schema (iOS)

**Files:**
- Modify: `Zampa-iOS/Zampa/Core/Models/Menu.swift` (struct + initializer)
- Modify: `Zampa-iOS/Zampa/Services/FirebaseService.swift` — `parseMenu` (and any second parse site)

- [ ] **Step 1: Add the two fields to the `Menu` struct**

In `Menu.swift`, after the `recurringDays` property (around line 94), append:

```swift
    /// Source menu id when this offer was created via "Republicar"; nil for fresh creations.
    let republishedFrom: String?
    /// Server timestamp (ISO 8601 string) of when the republish happened; nil otherwise.
    let republishedAt: String?
```

In the same file, extend the initializer signature (after `recurringDays:`) and assignments:

```swift
        recurringDays: [Int]? = nil,
        republishedFrom: String? = nil,
        republishedAt: String? = nil
```

```swift
        self.recurringDays = recurringDays
        self.republishedFrom = republishedFrom
        self.republishedAt = republishedAt
```

- [ ] **Step 2: Read the new fields in `parseMenu`**

Open `Zampa-iOS/Zampa/Services/FirebaseService.swift` and locate `parseMenu(...)`. Add the two reads alongside the other optional fields (search for `recurringDays` and add right after it):

```swift
        let republishedFrom = data["republishedFrom"] as? String
        let republishedAt = (data["republishedAt"] as? Timestamp).map {
            ISO8601DateFormatter().string(from: $0.dateValue())
        } ?? data["republishedAt"] as? String
```

Pass them to the `Menu(...)` initializer at the bottom of `parseMenu` (append the two trailing args to the existing call).

- [ ] **Step 3: Build to confirm compilation**

In Xcode, ⌘B.
Expected: Build Succeeded with no warnings about uninitialized fields.

- [ ] **Step 4: Run the existing test suite**

⌘U.
Expected: all tests still pass (additive change).

- [ ] **Step 5: Commit**

```bash
git add Zampa-iOS/Zampa/Core/Models/Menu.swift Zampa-iOS/Zampa/Services/FirebaseService.swift
git commit -m "feat(ios): add republishedFrom/republishedAt to Menu + parser"
```

---

### Task 4: Add `republishedFrom` / `republishedAt` schema (Android)

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt` — `getMenusByMerchant` (line 237) and `getActiveMenus` (line 165)

- [ ] **Step 1: Add the two fields to `Menu`**

In `Models.kt`, immediately after `recurringDays` (around line 179), add:

```kotlin
    /** Source menu id when this offer was created via "Republicar"; null for fresh creations. */
    val republishedFrom: String? = null,
    /** ISO 8601 timestamp of when the republish happened; null otherwise. */
    val republishedAt: String? = null,
```

- [ ] **Step 2: Read the new fields in `getMenusByMerchant`**

In `FirebaseService.kt` inside `getMenusByMerchant` (line 237 region), add to the `Menu(...)` builder at the very end (just before the closing `)`):

```kotlin
                republishedFrom = d["republishedFrom"] as? String,
                republishedAt = (d["republishedAt"] as? com.google.firebase.Timestamp)?.let {
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(it.toDate())
                } ?: d["republishedAt"] as? String,
```

- [ ] **Step 3: Mirror the read in `getActiveMenus`**

In `FirebaseService.kt` inside `getActiveMenus` (line 165 region), do the same — append the two args to the `Menu(...)` constructor call there.

- [ ] **Step 4: Build and run the unit tests**

```bash
cd Zampa-Android && ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass (additive change; defaults are null).

- [ ] **Step 5: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt \
        Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt
git commit -m "feat(android): add republishedFrom/republishedAt to Menu + parsers"
```

---

### Task 5: Cached `config/republishLimits` reader (iOS)

**Files:**
- Modify: `Zampa-iOS/Zampa/Services/FirebaseService.swift`

- [ ] **Step 1: Add cache state + reader**

Inside `FirebaseService` (private state near other singletons; if no cache region exists, place near `getPromoFreeUntilMs()`), add:

```swift
    private var cachedRepublishLimit: (value: Int, fetchedAt: Date)?
    private static let republishLimitTTL: TimeInterval = 3600 // 1h
    private static let republishLimitDefault = 3

    /// Reads `config/republishLimits.perDay` with a 1h in-memory cache. Returns the default
    /// (3) when the doc is missing or unreachable. Never throws — limit-reading must not
    /// block the user flow.
    func getRepublishLimit() async -> Int {
        if let c = cachedRepublishLimit,
           Date().timeIntervalSince(c.fetchedAt) < Self.republishLimitTTL {
            return c.value
        }
        do {
            let snap = try await db.collection("config").document("republishLimits").getDocument()
            let value = (snap.data()?["perDay"] as? Int) ?? Self.republishLimitDefault
            cachedRepublishLimit = (value, Date())
            return value
        } catch {
            return Self.republishLimitDefault
        }
    }

    /// Counts the merchant's republishes since 00:00 UTC today. Used for the soft client-side
    /// limit before opening the editor.
    func countTodayRepublishes(merchantId: String) async throws -> Int {
        let cal = Calendar(identifier: .gregorian)
        var c = cal.dateComponents([.year, .month, .day], from: Date())
        c.timeZone = TimeZone(identifier: "UTC")
        let startOfToday = Calendar(identifier: .gregorian).date(from: c) ?? Date()
        let snap = try await db.collection("dailyOffers")
            .whereField("businessId", isEqualTo: merchantId)
            .whereField("republishedFrom", isNotEqualTo: NSNull())
            .whereField("createdAt", isGreaterThanOrEqualTo: ISO8601DateFormatter().string(from: startOfToday))
            .getDocuments()
        return snap.documents.count
    }
```

- [ ] **Step 2: Build**

⌘B in Xcode. Expected: Build Succeeded.

- [ ] **Step 3: Commit**

```bash
git add Zampa-iOS/Zampa/Services/FirebaseService.swift
git commit -m "feat(ios): add cached config/republishLimits reader + countTodayRepublishes"
```

---

### Task 6: Cached `config/republishLimits` reader (Android)

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`

- [ ] **Step 1: Add cache state + reader**

Near the existing `getPromoFreeUntilMs()` (line 537 region), add:

```kotlin
    private data class CachedLimit(val value: Int, val fetchedAtMs: Long)
    private var cachedRepublishLimit: CachedLimit? = null
    private val republishLimitTtlMs: Long = 60 * 60 * 1000L
    private val republishLimitDefault: Int = 3

    /**
     * Reads `config/republishLimits.perDay` with a 1h in-memory cache. Returns 3 when the doc
     * is missing or unreachable. Never throws — limit reads must not block the user flow.
     */
    suspend fun getRepublishLimit(): Int {
        val cached = cachedRepublishLimit
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs < republishLimitTtlMs) {
            return cached.value
        }
        return try {
            val snap = db.collection("config").document("republishLimits").get().await()
            val value = (snap.getLong("perDay")?.toInt()) ?: republishLimitDefault
            cachedRepublishLimit = CachedLimit(value, System.currentTimeMillis())
            value
        } catch (_: Exception) {
            republishLimitDefault
        }
    }

    /** Counts this merchant's republishes since 00:00 UTC today. */
    suspend fun countTodayRepublishes(merchantId: String): Int {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val startOfToday = iso.format(cal.time)
        val snap = db.collection("dailyOffers")
            .whereEqualTo("businessId", merchantId)
            .whereGreaterThanOrEqualTo("createdAt", startOfToday)
            .get().await()
        // Filter by republishedFrom != null in-memory (Firestore can't combine != with the
        // existing range filter on createdAt).
        return snap.documents.count { (it.get("republishedFrom") as? String) != null }
    }
```

- [ ] **Step 2: Build**

```bash
cd Zampa-Android && ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt
git commit -m "feat(android): add cached config/republishLimits reader + countTodayRepublishes"
```

---

### Task 7: `republishMenu` service method (iOS)

**Files:**
- Modify: `Zampa-iOS/Zampa/Services/FirebaseService.swift`
- Modify: `Zampa-iOS/Zampa/Services/MenuService.swift`

- [ ] **Step 1: Add the error type**

At the top of `FirebaseService.swift` (alongside other custom errors), add:

```swift
enum RepublishError: LocalizedError {
    case limitExceeded(perDay: Int)
    case sourceMissing
    var errorDescription: String? {
        switch self {
        case .limitExceeded(let n): return "republish_limit_exceeded:\(n)"
        case .sourceMissing: return "republish_source_missing"
        }
    }
}
```

- [ ] **Step 2: Add `republishMenu(...)` to `FirebaseService`**

Place near `createMenu(...)` (line 499 region):

```swift
    /// Republishes a past offer as a new `dailyOffers` document.
    /// - When `existingPhotoUrl` is non-nil, reuses it without a new upload.
    /// - When non-empty, all `conflictsToDeactivate` ids are flipped to `isActive: false`
    ///   atomically with the new doc creation.
    /// - Throws `RepublishError.limitExceeded` if the merchant already hit today's quota.
    func republishMenu(
        sourceMenuId: String,
        title: String,
        description: String,
        price: Double,
        currency: String,
        photoData: Data?,
        existingPhotoUrl: String?,
        tags: [String]? = nil,
        dietaryInfo: DietaryInfo = DietaryInfo(),
        offerType: String? = nil,
        includesDrink: Bool = false,
        includesDessert: Bool = false,
        includesCoffee: Bool = false,
        serviceTime: String = "both",
        isPermanent: Bool = false,
        recurringDays: [Int]? = nil,
        conflictsToDeactivate: [String]
    ) async throws -> Menu {
        guard let uid = auth.currentUser?.uid else { throw NSError(domain: "auth", code: 401) }

        // 1. Hard-cap soft check before doing any work.
        let limit = await getRepublishLimit()
        let count = try await countTodayRepublishes(merchantId: uid)
        if count >= limit { throw RepublishError.limitExceeded(perDay: limit) }

        // 2. Server-derive Pro / Verified / subscription (mirrors createMenu).
        let businessDoc = try await db.collection("businesses").document(uid).getDocument()
        let planTier = businessDoc.data()? ["planTier"] as? String ?? "free"
        let isPro = planTier == "pro"
        let isVerified = businessDoc.data()? ["isVerified"] as? Bool ?? true
        let status = businessDoc.data()? ["subscriptionStatus"] as? String ?? "trial"
        let trialEnd = businessDoc.data()? ["trialEndsAt"] as? Int64 ?? 0
        let activeUntil = businessDoc.data()? ["subscriptionActiveUntil"] as? Int64 ?? 0
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let promoMs = (try? await getPromoFreeUntilMs()) ?? 0
        let canPublish: Bool = {
            if promoMs > nowMs { return true }
            if status == "trial" { return trialEnd > nowMs }
            if status == "active" { return activeUntil > nowMs }
            return false
        }()
        guard canPublish else { throw NSError(domain: "subscription_expired", code: 402) }

        // 3. Photo: reuse URL or upload bytes.
        let photoUrl: String
        if let existing = existingPhotoUrl {
            photoUrl = existing
        } else if let data = photoData {
            photoUrl = try await uploadImage(data: data, path: "dailyOffers/\(UUID().uuidString).jpg")
        } else {
            throw NSError(domain: "missing_photo", code: 400)
        }

        // 4. Build the new doc payload (mirrors createMenu's shape).
        let id = UUID().uuidString
        let isoFormatter = ISO8601DateFormatter()
        let createdAtStr = isoFormatter.string(from: Date())
        var data: [String: Any] = [
            "id": id, "businessId": uid, "date": createdAtStr,
            "title": title, "description": description,
            "priceTotal": price, "currency": currency,
            "photoUrls": [photoUrl], "tags": tags ?? [],
            "createdAt": createdAtStr, "updatedAt": createdAtStr,
            "isActive": true,
            "isMerchantPro": isPro, "isMerchantVerified": isVerified,
            "dietaryInfo": dietaryInfo.toDictionary(),
            "includesDrink": includesDrink, "includesDessert": includesDessert,
            "includesCoffee": includesCoffee,
            "serviceTime": serviceTime, "isPermanent": isPermanent,
            "republishedFrom": sourceMenuId,
            "republishedAt": FieldValue.serverTimestamp(),
        ]
        if let t = offerType { data["offerType"] = t }
        if isPermanent, let days = recurringDays, !days.isEmpty {
            data["recurringDays"] = days
        }

        // 5. Atomic batch: deactivate conflicts + create new doc.
        let batch = db.batch()
        for victimId in conflictsToDeactivate {
            let ref = db.collection("dailyOffers").document(victimId)
            batch.updateData([
                "isActive": false,
                "deactivatedReason": "republish_overwrite",
                "updatedAt": FieldValue.serverTimestamp(),
            ], forDocument: ref)
        }
        let newRef = db.collection("dailyOffers").document(id)
        batch.setData(data, forDocument: newRef)
        try await batch.commit()

        return Menu(
            id: id, businessId: uid, date: createdAtStr,
            title: title, description: description, priceTotal: price, currency: currency,
            photoUrls: [photoUrl], tags: tags,
            createdAt: createdAtStr, updatedAt: createdAtStr,
            isActive: true, isMerchantPro: isPro, isMerchantVerified: isVerified,
            dietaryInfo: dietaryInfo, offerType: offerType,
            includesDrink: includesDrink, includesDessert: includesDessert,
            includesCoffee: includesCoffee,
            serviceTime: serviceTime, isPermanent: isPermanent,
            recurringDays: isPermanent ? recurringDays : nil,
            republishedFrom: sourceMenuId, republishedAt: createdAtStr
        )
    }
```

If `uploadImage(data:path:)` doesn't exist by that exact name in `FirebaseService.swift`, replace with the existing equivalent (search the file for `Storage` upload helpers — `createMenu` already uses one).

- [ ] **Step 3: Add `MenuService.republishMenu(...)` pass-through**

In `MenuService.swift`, mirror `createMenu(...)` style. Place under it:

```swift
    func republishMenu(
        sourceMenuId: String,
        title: String,
        description: String,
        price: Double,
        currency: String = "EUR",
        photoData: Data?,
        existingPhotoUrl: String?,
        tags: [String]? = nil,
        dietaryInfo: DietaryInfo = DietaryInfo(),
        offerType: String? = nil,
        includesDrink: Bool = false,
        includesDessert: Bool = false,
        includesCoffee: Bool = false,
        serviceTime: String = "both",
        isPermanent: Bool = false,
        recurringDays: [Int]? = nil,
        conflictsToDeactivate: [String]
    ) async throws -> Menu {
        try await firebase.republishMenu(
            sourceMenuId: sourceMenuId,
            title: title, description: description, price: price, currency: currency,
            photoData: photoData, existingPhotoUrl: existingPhotoUrl,
            tags: tags, dietaryInfo: dietaryInfo, offerType: offerType,
            includesDrink: includesDrink, includesDessert: includesDessert,
            includesCoffee: includesCoffee, serviceTime: serviceTime,
            isPermanent: isPermanent, recurringDays: recurringDays,
            conflictsToDeactivate: conflictsToDeactivate
        )
    }
```

- [ ] **Step 4: Build**

⌘B in Xcode. Expected: Build Succeeded.

- [ ] **Step 5: Commit**

```bash
git add Zampa-iOS/Zampa/Services/FirebaseService.swift Zampa-iOS/Zampa/Services/MenuService.swift
git commit -m "feat(ios): add FirebaseService.republishMenu(...) + MenuService passthrough"
```

---

### Task 8: `republishMenu` service method (Android)

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`

- [ ] **Step 1: Add the typed error**

Near other exceptions in `FirebaseService.kt` (or top-level), declare:

```kotlin
class RepublishLimitExceededException(val perDay: Int) : Exception("republish_limit_exceeded:$perDay")
```

- [ ] **Step 2: Add `republishMenu(...)`**

Place just below the existing `createMenu(...)` (line 275 region):

```kotlin
    suspend fun republishMenu(
        sourceMenuId: String,
        title: String,
        description: String,
        price: Double,
        currency: String = "EUR",
        photoData: ByteArray?,
        existingPhotoUrl: String?,
        tags: List<String>? = null,
        dietaryInfo: DietaryInfo = DietaryInfo(),
        offerType: String? = null,
        includesDrink: Boolean = false,
        includesDessert: Boolean = false,
        includesCoffee: Boolean = false,
        serviceTime: String = "both",
        isPermanent: Boolean = false,
        recurringDays: List<Int>? = null,
        conflictsToDeactivate: List<String>,
    ): Menu {
        val uid = currentUid ?: throw Exception("No autenticado")

        // 1. Soft cap.
        val limit = getRepublishLimit()
        val count = countTodayRepublishes(uid)
        if (count >= limit) throw RepublishLimitExceededException(limit)

        // 2. Server-derive isPro / isVerified / subscription (mirrors createMenu).
        val businessDoc = db.collection("businesses").document(uid).get().await()
        val planTier = businessDoc.getString("planTier") ?: "free"
        val isPro = planTier == "pro"
        val isVerified = businessDoc.getBoolean("isVerified") ?: true
        val status = businessDoc.getString("subscriptionStatus")
            ?: com.sozolab.zampa.data.model.SubscriptionStatus.TRIAL
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

        // 3. Photo: reuse URL or upload bytes.
        val photoUrl: String = existingPhotoUrl
            ?: photoData?.let { uploadImage(it, "dailyOffers/${UUID.randomUUID()}.jpg") }
            ?: throw Exception("missing_photo")

        // 4. Build payload.
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val now = Date()
        val id = UUID.randomUUID().toString()
        val createdAtStr = iso.format(now)
        val data = mutableMapOf<String, Any>(
            "id" to id, "businessId" to uid, "date" to createdAtStr,
            "title" to title, "description" to description,
            "priceTotal" to price, "currency" to currency,
            "photoUrls" to listOf(photoUrl), "tags" to (tags ?: emptyList<String>()),
            "createdAt" to createdAtStr, "updatedAt" to createdAtStr,
            "isActive" to true,
            "isMerchantPro" to isPro, "isMerchantVerified" to isVerified,
            "dietaryInfo" to dietaryInfo.toMap(),
            "includesDrink" to includesDrink, "includesDessert" to includesDessert,
            "includesCoffee" to includesCoffee,
            "serviceTime" to serviceTime, "isPermanent" to isPermanent,
            "republishedFrom" to sourceMenuId,
            "republishedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
        )
        offerType?.let { data["offerType"] = it }
        if (isPermanent && !recurringDays.isNullOrEmpty()) data["recurringDays"] = recurringDays

        // 5. Atomic batch.
        val batch = db.batch()
        for (victimId in conflictsToDeactivate) {
            batch.update(
                db.collection("dailyOffers").document(victimId),
                mapOf(
                    "isActive" to false,
                    "deactivatedReason" to "republish_overwrite",
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                )
            )
        }
        batch.set(db.collection("dailyOffers").document(id), data)
        batch.commit().await()

        return Menu(
            id = id, businessId = uid, title = title, description = description,
            priceTotal = price, currency = currency, photoUrls = listOf(photoUrl), tags = tags,
            createdAt = createdAtStr, isActive = true,
            isMerchantPro = isPro, isMerchantVerified = isVerified,
            dietaryInfo = dietaryInfo, offerType = offerType,
            includesDrink = includesDrink, includesDessert = includesDessert,
            includesCoffee = includesCoffee, serviceTime = serviceTime,
            isPermanent = isPermanent,
            recurringDays = if (isPermanent) recurringDays else null,
            republishedFrom = sourceMenuId, republishedAt = createdAtStr,
        )
    }
```

If `uploadImage(...)` here doesn't match the existing helper, update the call to use the same one `createMenu` uses (search for `uploadImage` in the file).

- [ ] **Step 3: Build**

```bash
cd Zampa-Android && ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt
git commit -m "feat(android): add FirebaseService.republishMenu(...)"
```

---

### Task 9: Localization strings (both platforms, all locales)

**Files:**
- Modify: `Zampa-iOS/Zampa/Localization/{ca,de,en,es,eu,fr,gl,it}.json` (8 files)
- Modify: `Zampa-Android/app/src/main/res/values{,-ca,-de,-en,-eu,-fr,-gl,-it}/strings.xml` (9 files including default)

The 14 keys (with Spanish source values; translate to each locale following existing tone):

| Key | Spanish |
| --- | --- |
| `republish.section.title` | Ofertas anteriores |
| `republish.section.empty` | Aún no tienes ofertas anteriores |
| `republish.section.show_more` | Ver más |
| `republish.button` | Republicar |
| `republish.editor.header` | Republicar — revisa y publica |
| `republish.editor.cta` | Publicar |
| `republish.conflict.title` | Ya tienes una oferta activa en esta franja |
| `republish.conflict.body` | Esta republicación choca con %d oferta(s): |
| `republish.conflict.confirm` | Sobrescribir y publicar |
| `republish.conflict.cancel` | Cancelar |
| `republish.limit.reached` | Has alcanzado el límite diario de %d republicaciones |
| `republish.error.source_missing` | La oferta original ya no existe |
| `republish.error.generic` | No se pudo republicar. Inténtalo de nuevo. |
| `republish.notification.limit_blocked.title` | Republicación bloqueada |
| `republish.notification.limit_blocked.body` | Has superado el límite diario de %d republicaciones. |

(15 entries — kept the typo-free count at 15.)

- [ ] **Step 1: Add the keys to all 8 iOS JSON files**

Open each `Zampa-iOS/Zampa/Localization/<lang>.json` and add the keys, preserving the file's existing JSON shape (top-level flat keys). For Spanish (`es.json`) use the source values verbatim; for the others, translate. Reference the Catalan/Basque/Galician/English/German/French/Italian style already in the file for tone.

For the strings with `%d`, use the same placeholder syntax already in the file (e.g., if other Spanish strings use `%lld` or `%d`, match them — use `%d` for now; iOS substitution accepts both for `Int`).

- [ ] **Step 2: Add the keys to all 9 Android XML files**

In each `Zampa-Android/app/src/main/res/values{,-ca,-de,-en,-eu,-fr,-gl,-it}/strings.xml`, add (translated for each locale):

```xml
    <string name="republish_section_title">Ofertas anteriores</string>
    <string name="republish_section_empty">Aún no tienes ofertas anteriores</string>
    <string name="republish_section_show_more">Ver más</string>
    <string name="republish_button">Republicar</string>
    <string name="republish_editor_header">Republicar — revisa y publica</string>
    <string name="republish_editor_cta">Publicar</string>
    <string name="republish_conflict_title">Ya tienes una oferta activa en esta franja</string>
    <string name="republish_conflict_body">Esta republicación choca con %1$d oferta(s):</string>
    <string name="republish_conflict_confirm">Sobrescribir y publicar</string>
    <string name="republish_conflict_cancel">Cancelar</string>
    <string name="republish_limit_reached">Has alcanzado el límite diario de %1$d republicaciones</string>
    <string name="republish_error_source_missing">La oferta original ya no existe</string>
    <string name="republish_error_generic">No se pudo republicar. Inténtalo de nuevo.</string>
    <string name="republish_notification_limit_blocked_title">Republicación bloqueada</string>
    <string name="republish_notification_limit_blocked_body">Has superado el límite diario de %1$d republicaciones.</string>
```

Android uses underscores not dots in resource names — that's why the iOS keys (dot syntax) become underscores here.

- [ ] **Step 3: Build both platforms**

```bash
cd Zampa-Android && ./gradlew :app:assembleDebug
```

In Xcode, ⌘B.

Expected: both build green; no missing-string warnings.

- [ ] **Step 4: Commit**

```bash
git add Zampa-iOS/Zampa/Localization/*.json Zampa-Android/app/src/main/res/values*/strings.xml
git commit -m "i18n: add 15 republish strings across iOS + Android (8 locales)"
```

---

### Task 10: iOS — collapsible "Ofertas anteriores" section in Dashboard

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift`

- [ ] **Step 1: Add state + section UI**

Inside `MerchantDashboardView` near the existing `@State` properties, add:

```swift
    @State private var showHistory = false
    @State private var visibleHistoryCount = 30
    @State private var historyToRepublish: Menu? = nil
    @State private var republishLimit = 3
    @State private var republishCountToday = 0
    @State private var republishLimitToast: String? = nil
```

Below the active offers list, add the section. The exact insertion point depends on the current view body — look for the `ForEach` / `List` that renders active offers and add after it:

```swift
            DisclosureGroup(isExpanded: $showHistory) {
                let inactive = menus.filter { !$0.isActive }
                if inactive.isEmpty {
                    Text(localization.t("republish.section.empty"))
                        .foregroundColor(.secondary)
                        .padding(.vertical, 8)
                } else {
                    ForEach(inactive.prefix(visibleHistoryCount)) { m in
                        HistoryRow(menu: m, onRepublish: { handleRepublishTap(m) })
                    }
                    if inactive.count > visibleHistoryCount {
                        Button(localization.t("republish.section.show_more")) {
                            visibleHistoryCount += 30
                        }
                    }
                }
            } label: {
                Text(localization.t("republish.section.title"))
                    .font(.headline)
            }
            .padding(.horizontal)
```

And the row at the bottom of the file:

```swift
private struct HistoryRow: View {
    @ObservedObject var localization = LocalizationManager.shared
    let menu: Menu
    let onRepublish: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            if let url = menu.photoUrls.first {
                AsyncImage(url: URL(string: url)) { phase in
                    switch phase {
                    case .success(let img): img.resizable().aspectRatio(contentMode: .fill)
                    default: Color.gray.opacity(0.2)
                    }
                }
                .frame(width: 48, height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(menu.title).font(.subheadline).bold()
                Text(menu.formattedPrice).font(.caption).foregroundColor(.secondary)
                Text(menu.isPermanent ? "♾️" : "📅").font(.caption2)
            }
            Spacer()
            Button(localization.t("republish.button"), action: onRepublish)
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
        }
        .padding(.vertical, 4)
    }
}
```

- [ ] **Step 2: Add the soft-limit handler stub**

Inside the view, add:

```swift
    private func handleRepublishTap(_ menu: Menu) {
        Task {
            republishLimit = await FirebaseService.shared.getRepublishLimit()
            republishCountToday = (try? await FirebaseService.shared.countTodayRepublishes(
                merchantId: appState.currentUser?.id ?? ""
            )) ?? 0
            if republishCountToday >= republishLimit {
                republishLimitToast = String(format: localization.t("republish.limit.reached"),
                                             republishLimit)
                return
            }
            historyToRepublish = menu
        }
    }
```

And a toast `.alert(...)` somewhere in the view body:

```swift
            .alert(item: Binding(
                get: { republishLimitToast.map { ToastWrapper(message: $0) } },
                set: { _ in republishLimitToast = nil }
            )) { wrap in
                Alert(title: Text(wrap.message))
            }
```

With a small wrapper at the bottom of the file:

```swift
private struct ToastWrapper: Identifiable {
    let id = UUID()
    let message: String
}
```

- [ ] **Step 3: Build**

⌘B. Expected: Build Succeeded.

- [ ] **Step 4: Commit**

```bash
git add Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift
git commit -m "feat(ios): add collapsible 'Ofertas anteriores' section to merchant dashboard"
```

---

### Task 11: iOS — republish editor mode + conflict modal

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Merchant/EditMenuView.swift`
- Modify: `Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift` (sheet wiring)

- [ ] **Step 1: Add a `mode` enum to `EditMenuView`**

At the top of `EditMenuView.swift`:

```swift
enum EditMenuMode {
    case edit
    case republish(sourceMenuId: String)

    var isRepublish: Bool {
        if case .republish = self { return true }
        return false
    }

    var sourceMenuId: String? {
        if case .republish(let id) = self { return id }
        return nil
    }
}
```

Extend the `EditMenuView` initializer to take `mode: EditMenuMode = .edit`. Replace the header text and CTA label with the localized republish strings when `mode.isRepublish` is true:

```swift
    Text(mode.isRepublish
         ? localization.t("republish.editor.header")
         : localization.t("merchant.menu.edit.title"))
```

```swift
    Button(mode.isRepublish
           ? localization.t("republish.editor.cta")
           : localization.t("common.save")) {
        publishOrSave()
    }
```

- [ ] **Step 2: Branch the publish action**

Replace the existing save action with:

```swift
    private func publishOrSave() {
        switch mode {
        case .edit:
            // existing save flow — unchanged
            saveExisting()
        case .republish(let sourceId):
            Task { await runRepublish(sourceMenuId: sourceId) }
        }
    }

    private func runRepublish(sourceMenuId: String) async {
        // 1. Compute conflicts against active offers loaded into AppState (or pass via init).
        let candidate = makeCandidateMenu()
        let active = (await loadActiveMenus()).filter { $0.businessId == candidate.businessId && $0.isActive }
        let weekday = currentMondayZeroWeekday()
        let conflicts = Menu.findConflicts(for: candidate, in: active, todayWeekday: weekday)

        if conflicts.isEmpty {
            await performRepublish(sourceId: sourceMenuId, conflicts: [])
        } else {
            pendingConflicts = conflicts
            pendingSourceId = sourceMenuId
            showConflictModal = true
        }
    }

    private func performRepublish(sourceId: String, conflicts: [Menu]) async {
        do {
            _ = try await MenuService.shared.republishMenu(
                sourceMenuId: sourceId,
                title: title, description: description, price: price, currency: currency,
                photoData: pickedImageData,
                existingPhotoUrl: pickedImageData == nil ? menu.photoUrls.first : nil,
                tags: tags, dietaryInfo: dietaryInfo, offerType: offerType,
                includesDrink: includesDrink, includesDessert: includesDessert,
                includesCoffee: includesCoffee, serviceTime: serviceTime,
                isPermanent: isPermanent, recurringDays: recurringDays,
                conflictsToDeactivate: conflicts.map { $0.id }
            )
            dismiss()
        } catch let RepublishError.limitExceeded(perDay) {
            errorMessage = String(format: localization.t("republish.limit.reached"), perDay)
        } catch {
            errorMessage = localization.t("republish.error.generic")
        }
    }
```

Add the supporting state:

```swift
    @State private var showConflictModal = false
    @State private var pendingConflicts: [Menu] = []
    @State private var pendingSourceId: String? = nil
```

And helpers:

```swift
    private func currentMondayZeroWeekday() -> Int {
        let cal = Calendar(identifier: .iso8601)
        // ISO 8601: Monday=1..Sunday=7; map to 0..6 with Monday=0.
        return (cal.component(.weekday, from: Date()) + 5) % 7
    }

    private func makeCandidateMenu() -> Menu {
        Menu(id: "candidate", businessId: AppState.shared.currentUser?.id ?? "", date: "",
             title: title, priceTotal: price, isActive: true,
             serviceTime: serviceTime, isPermanent: isPermanent, recurringDays: recurringDays)
    }

    private func loadActiveMenus() async -> [Menu] {
        guard let uid = AppState.shared.currentUser?.id else { return [] }
        return (try? await FirebaseService.shared.getMenusByMerchant(merchantId: uid))?
            .filter { $0.isActive } ?? []
    }
```

If `AppState.shared` is not the actual accessor in this project, swap to whatever pattern `EditMenuView` already uses (look near the top of the file for `@EnvironmentObject` references).

- [ ] **Step 3: Add the conflict confirmation alert**

Inside the body, attach:

```swift
    .alert(localization.t("republish.conflict.title"),
           isPresented: $showConflictModal,
           presenting: pendingConflicts) { conflicts in
        Button(localization.t("republish.conflict.confirm"), role: .destructive) {
            if let id = pendingSourceId {
                Task { await performRepublish(sourceId: id, conflicts: conflicts) }
            }
        }
        Button(localization.t("republish.conflict.cancel"), role: .cancel) {}
    } message: { conflicts in
        Text(String(format: localization.t("republish.conflict.body"), conflicts.count) + "\n"
             + conflicts.map { "• \($0.title) — \($0.serviceTimeLabel)" }.joined(separator: "\n"))
    }
```

- [ ] **Step 4: Wire the dashboard sheet to use `.republish` mode**

In `MerchantDashboardView.swift`, add the sheet presenter:

```swift
    .sheet(item: $historyToRepublish) { menu in
        EditMenuView(menu: menu, mode: .republish(sourceMenuId: menu.id))
    }
```

- [ ] **Step 5: Build and smoke-test in Simulator**

⌘B then ⌘R. Open the merchant Dashboard, expand "Ofertas anteriores", tap Republicar on an inactive offer, verify the editor opens with all fields pre-filled and the header reads "Republicar — revisa y publica". Tap Publicar with no conflicts to ensure happy path works; then create a deliberate conflict and verify the alert.

Expected: Both flows succeed; the new doc appears in the active list and the conflict (if any) goes inactive.

- [ ] **Step 6: Commit**

```bash
git add Zampa-iOS/Zampa/Features/Merchant/EditMenuView.swift \
        Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift
git commit -m "feat(ios): wire republish mode in EditMenuView with conflict modal"
```

---

### Task 12: Android — collapsible "Ofertas anteriores" section in Dashboard

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt`

- [ ] **Step 1: Add ViewModel state**

In `DashboardViewModel.kt`, add fields and a load/check method:

```kotlin
    private val _republishLimit = MutableStateFlow(3)
    val republishLimit: StateFlow<Int> = _republishLimit

    private val _republishCountToday = MutableStateFlow(0)
    val republishCountToday: StateFlow<Int> = _republishCountToday

    suspend fun refreshRepublishQuota() {
        val uid = firebaseService.currentUid ?: return
        _republishLimit.value = firebaseService.getRepublishLimit()
        _republishCountToday.value = try {
            firebaseService.countTodayRepublishes(uid)
        } catch (_: Exception) { 0 }
    }
```

- [ ] **Step 2: Add the collapsible section to `DashboardScreen.kt`**

Find the `LazyColumn` / `Column` that renders the active offers list (it iterates over `viewModel.menus.collectAsState()`). Below it, add:

```kotlin
                val republishLimit by viewModel.republishLimit.collectAsState()
                val republishCount by viewModel.republishCountToday.collectAsState()
                val inactive = menus.filter { !it.isActive }
                var historyExpanded by remember { mutableStateOf(false) }
                var visibleCount by remember { mutableIntStateOf(30) }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.republish_section_title)) },
                    trailingContent = {
                        IconButton(onClick = { historyExpanded = !historyExpanded }) {
                            Icon(
                                imageVector = if (historyExpanded) Icons.Default.ExpandLess
                                              else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.clickable { historyExpanded = !historyExpanded }
                )

                if (historyExpanded) {
                    if (inactive.isEmpty()) {
                        Text(
                            stringResource(R.string.republish_section_empty),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        inactive.take(visibleCount).forEach { menu ->
                            HistoryRow(menu = menu, onRepublish = {
                                scope.launch {
                                    viewModel.refreshRepublishQuota()
                                    if (republishCount >= republishLimit) {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.republish_limit_reached, republishLimit)
                                        )
                                    } else {
                                        republishingMenu = menu
                                    }
                                }
                            })
                        }
                        if (inactive.size > visibleCount) {
                            TextButton(onClick = { visibleCount += 30 }) {
                                Text(stringResource(R.string.republish_section_show_more))
                            }
                        }
                    }
                }
```

Add at the top of the `@Composable` (Dashboard screen function), if not already present:

```kotlin
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }
                var republishingMenu by remember { mutableStateOf<Menu?>(null) }
```

Add the `HistoryRow` composable at file scope (near `EditMenuSheet`):

```kotlin
@Composable
private fun HistoryRow(menu: Menu, onRepublish: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = menu.photoUrls.firstOrNull(),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(menu.title, style = MaterialTheme.typography.titleSmall)
            Text("%.2f %s".format(menu.priceTotal, menu.currency),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (menu.isPermanent) "♾️" else "📅",
                 style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onRepublish) {
            Text(stringResource(R.string.republish_button))
        }
    }
}
```

- [ ] **Step 3: Build and run Dashboard manually**

```bash
cd Zampa-Android && ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Install + run on an emulator and confirm the new section renders, lists inactive offers, and the Republicar button is wired (clicking refreshes quota and either shows snackbar or sets `republishingMenu`).

- [ ] **Step 4: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt \
        Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt
git commit -m "feat(android): add collapsible 'Ofertas anteriores' section to merchant dashboard"
```

---

### Task 13: Android — republish editor sheet + conflict modal

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt`

- [ ] **Step 1: Add a `republishMode: Menu?` parameter to `EditMenuSheet`**

Change the signature at line 581 from:

```kotlin
fun EditMenuSheet(menu: Menu, viewModel: DashboardViewModel, onDismiss: () -> Unit)
```

to:

```kotlin
fun EditMenuSheet(
    menu: Menu,
    viewModel: DashboardViewModel,
    republishSource: Menu? = null,   // when non-null, the sheet acts as Republicar of this source
    onDismiss: () -> Unit,
)
```

Inside the sheet, define `val isRepublish = republishSource != null`. Replace the title `Text(...)` and primary CTA label using `stringResource(R.string.republish_editor_header)` and `stringResource(R.string.republish_editor_cta)` when `isRepublish` is true.

- [ ] **Step 2: Branch the publish action**

Find the `viewModel.createMenu(...)` call (around line 1011) and wrap it:

```kotlin
                if (isRepublish) {
                    val source = republishSource!!
                    scope.launch {
                        val candidate = source.copy(
                            id = "candidate",
                            title = title, description = description, priceTotal = price,
                            serviceTime = serviceTime, isPermanent = isPermanent,
                            recurringDays = recurringDays, isActive = true,
                        )
                        val active = viewModel.menus.value.filter { it.isActive }
                        val weekday = (java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                            .get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                        val conflicts = Menu.findConflicts(candidate, active, weekday)
                        if (conflicts.isEmpty()) {
                            viewModel.republishMenu(source, candidate, /*photoData*/ pickedPhotoBytes,
                                                    existingPhotoUrl = pickedPhotoBytes?.let { null }
                                                                       ?: source.photoUrls.firstOrNull(),
                                                    conflictsToDeactivate = emptyList())
                            onDismiss()
                        } else {
                            pendingConflicts = conflicts
                            pendingCandidate = candidate
                            showConflictDialog = true
                        }
                    }
                } else {
                    viewModel.createMenu(/* existing args */)
                }
```

Add the dialog state at the top of the composable:

```kotlin
            var showConflictDialog by remember { mutableStateOf(false) }
            var pendingConflicts by remember { mutableStateOf<List<Menu>>(emptyList()) }
            var pendingCandidate by remember { mutableStateOf<Menu?>(null) }
```

Add the dialog body somewhere in the sheet:

```kotlin
            if (showConflictDialog && republishSource != null && pendingCandidate != null) {
                AlertDialog(
                    onDismissRequest = { showConflictDialog = false },
                    title = { Text(stringResource(R.string.republish_conflict_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.republish_conflict_body, pendingConflicts.size))
                            Spacer(Modifier.height(8.dp))
                            pendingConflicts.forEach { c ->
                                Text("• ${c.title} — ${c.serviceTimeLabel}",
                                     style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch {
                                viewModel.republishMenu(
                                    source = republishSource,
                                    candidate = pendingCandidate!!,
                                    photoData = pickedPhotoBytes,
                                    existingPhotoUrl = pickedPhotoBytes?.let { null }
                                                       ?: republishSource.photoUrls.firstOrNull(),
                                    conflictsToDeactivate = pendingConflicts.map { it.id },
                                )
                                showConflictDialog = false
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.republish_conflict_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConflictDialog = false }) {
                            Text(stringResource(R.string.republish_conflict_cancel))
                        }
                    }
                )
            }
```

- [ ] **Step 3: Add `viewModel.republishMenu(...)` to `DashboardViewModel`**

```kotlin
    suspend fun republishMenu(
        source: Menu,
        candidate: Menu,
        photoData: ByteArray?,
        existingPhotoUrl: String?,
        conflictsToDeactivate: List<String>,
    ) {
        try {
            firebaseService.republishMenu(
                sourceMenuId = source.id,
                title = candidate.title,
                description = candidate.description.orEmpty(),
                price = candidate.priceTotal,
                currency = candidate.currency,
                photoData = photoData,
                existingPhotoUrl = existingPhotoUrl,
                tags = candidate.tags,
                dietaryInfo = candidate.dietaryInfo,
                offerType = candidate.offerType,
                includesDrink = candidate.includesDrink,
                includesDessert = candidate.includesDessert,
                includesCoffee = candidate.includesCoffee,
                serviceTime = candidate.serviceTime,
                isPermanent = candidate.isPermanent,
                recurringDays = candidate.recurringDays,
                conflictsToDeactivate = conflictsToDeactivate,
            )
            // Refresh menus list
            val uid = firebaseService.currentUid ?: return
            _menus.value = firebaseService.getMenusByMerchant(uid)
        } catch (e: RepublishLimitExceededException) {
            _toast.value = e.message
        } catch (e: Exception) {
            _toast.value = "republish_error_generic"
        }
    }
```

(If `_toast` doesn't exist, fall back to logging the exception and surfacing the error via the existing error channel of the ViewModel.)

- [ ] **Step 4: Wire the sheet from `republishingMenu` state**

In the Dashboard composable, add (next to the existing `editingMenu` sheet):

```kotlin
    republishingMenu?.let { src ->
        EditMenuSheet(
            menu = src,
            viewModel = viewModel,
            republishSource = src,
            onDismiss = { republishingMenu = null }
        )
    }
```

- [ ] **Step 5: Build and smoke-test on emulator**

```bash
cd Zampa-Android && ./gradlew :app:assembleDebug && ./gradlew :app:installDebug
```

Open Dashboard → expand history → tap Republicar → editor opens with prefilled values → tap Publicar with no conflicts → succeeds; create a conflict and verify the AlertDialog appears with the conflict list.

- [ ] **Step 6: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt \
        Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardViewModel.kt
git commit -m "feat(android): wire republish mode in EditMenuSheet with conflict modal"
```

---

### Task 14: Firestore rules — `republishedFrom` validation

**Files:**
- Modify: `firebase/firestore.rules`

- [ ] **Step 1: Update the `dailyOffers` create rule**

Replace the existing `allow create` block (line 169-176) with:

```
      allow create: if isMerchant()
        && hasActiveSubscription(request.auth.uid)
        && request.resource.data.businessId == request.auth.uid
        && request.resource.data.isMerchantPro == isMerchantProUser(request.auth.uid)
        && (!('isMerchantVerified' in request.resource.data)
            || request.resource.data.isMerchantVerified == isMerchantVerifiedUser(request.auth.uid))
        && (!('republishedFrom' in request.resource.data)
            || (
                request.resource.data.republishedFrom is string
                && exists(/databases/$(database)/documents/dailyOffers/$(request.resource.data.republishedFrom))
                && get(/databases/$(database)/documents/dailyOffers/$(request.resource.data.republishedFrom)).data.businessId == request.auth.uid
              )
           );
```

- [ ] **Step 2: Lock `republishedFrom` and `republishedAt` immutability on update**

Append to the existing `allow update` block (line 177-182), after the existing checks:

```
        && unchanged('republishedFrom')
        && unchanged('republishedAt')
```

- [ ] **Step 3: Validate locally with the Firestore emulator**

```bash
cd functions && npm run --silent test 2>/dev/null || true
firebase emulators:exec --only firestore "echo 'rules loaded'"
```

Expected: emulator boots without rules-syntax errors.

- [ ] **Step 4: Commit**

```bash
git add firebase/firestore.rules
git commit -m "feat(rules): validate republishedFrom on dailyOffers create + immutable on update"
```

---

### Task 15: Cloud Function — hard limit + emulator test

**Files:**
- Modify: `functions/index.js` — `onMenuPublished`
- Create: `functions/test/republishLimit.test.js`

- [ ] **Step 1: Add the hard-limit branch in `onMenuPublished`**

At the very top of the handler body (right after parsing the `data` snapshot), insert:

```js
  // Hard daily-republish limit: enforce server-side. Soft check on client may have raced.
  if (data.republishedFrom) {
    const limitDoc = await admin.firestore().doc('config/republishLimits').get();
    const perDay = limitDoc.exists && Number.isFinite(limitDoc.data().perDay)
      ? limitDoc.data().perDay
      : 3;
    const startOfTodayIso = new Date(
      Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth(), new Date().getUTCDate())
    ).toISOString();
    const todayQuery = await admin.firestore().collection('dailyOffers')
      .where('businessId', '==', data.businessId)
      .where('createdAt', '>=', startOfTodayIso)
      .get();
    const republishedToday = todayQuery.docs.filter(d => d.data().republishedFrom).length;
    if (republishedToday > perDay) {
      await snap.ref.update({
        isActive: false,
        deactivatedReason: 'republish_limit_exceeded',
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      // In-app notification to the merchant.
      await admin.firestore().collection('notifications').add({
        userId: data.businessId,
        title: 'Republicación bloqueada',
        body: `Has superado el límite diario de ${perDay} republicaciones.`,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        read: false,
        type: 'republish_limit_blocked',
      });
      return; // Do not fan out push notifications.
    }
  }
```

(Keep the rest of the handler — follower fan-out, push, etc. — untouched.)

- [ ] **Step 2: Write the emulator test**

Create `functions/test/republishLimit.test.js`:

```js
const test = require('firebase-functions-test')({ projectId: 'demo-republish' });
const admin = require('firebase-admin');

admin.initializeApp({ projectId: 'demo-republish' });

const myFunctions = require('../index');

describe('onMenuPublished republish limit', () => {
  beforeEach(async () => {
    const fs = admin.firestore();
    await fs.recursiveDelete(fs.collection('dailyOffers'));
    await fs.recursiveDelete(fs.collection('notifications'));
    await fs.doc('config/republishLimits').set({ perDay: 3 });
  });

  afterAll(() => test.cleanup());

  it('deactivates the 4th republish of the day and skips push fanout', async () => {
    const fs = admin.firestore();
    const merchantId = 'merch-1';
    const today = new Date().toISOString();
    // Seed 3 prior republishes already in dailyOffers
    for (let i = 0; i < 3; i++) {
      await fs.collection('dailyOffers').add({
        businessId: merchantId, republishedFrom: 'src', createdAt: today,
        isActive: true, title: `R${i}`, priceTotal: 5,
      });
    }
    // Trigger handler with a 4th
    const wrapped = test.wrap(myFunctions.onMenuPublished);
    const ref = await fs.collection('dailyOffers').add({
      businessId: merchantId, republishedFrom: 'src', createdAt: today,
      isActive: true, title: 'R4', priceTotal: 5,
    });
    const snap = await ref.get();
    await wrapped({ data: snap, params: { menuId: ref.id } });

    const after = (await ref.get()).data();
    expect(after.isActive).toBe(false);
    expect(after.deactivatedReason).toBe('republish_limit_exceeded');
    const notifs = await fs.collection('notifications')
      .where('userId', '==', merchantId).get();
    expect(notifs.size).toBe(1);
  });
});
```

- [ ] **Step 3: Run the emulator test**

```bash
cd functions && firebase emulators:exec --only firestore "npx jest test/republishLimit.test.js"
```

Expected: 1 passing test.

If `firebase-functions-test` isn't installed yet:

```bash
cd functions && npm install --save-dev firebase-functions-test jest
```

Add `"test": "jest"` to `functions/package.json` if missing.

- [ ] **Step 4: Commit**

```bash
git add functions/index.js functions/test/republishLimit.test.js functions/package.json
git commit -m "feat(functions): hard-limit republish daily quota in onMenuPublished + test"
```

---

### Task 16: Document the manual `config/republishLimits` seed

**Files:**
- Modify: `docs/superpowers/launch-checklist.md` (create if missing — append section)

- [ ] **Step 1: Append a checklist item**

Add or append:

```markdown
## Republish daily limit

The `config/republishLimits` Firestore doc controls the max republications per merchant per day.

**Action (Firebase Console, one-time):**
1. Open Firestore → `config` collection.
2. Create document `republishLimits` if missing.
3. Field: `perDay` (number) → `3`.

If the document is missing, both the iOS/Android apps and the `onMenuPublished` Cloud
Function fall back to `3`. Lower it to `2` or raise it as needed without redeploying.
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/launch-checklist.md
git commit -m "docs: document config/republishLimits manual seed step"
```

---

## Self-Review

**Spec coverage:**
- Data model fields ✅ Tasks 3–4
- `config/republishLimits` ✅ Tasks 5–6 (read), 16 (seed)
- Conflict detection helper ✅ Tasks 1–2
- UI flow (Dashboard section, editor mode, conflict modal, soft limit) ✅ Tasks 10–13
- Service layer `republishMenu` ✅ Tasks 7–8
- Firestore rules ✅ Task 14
- Cloud Function hard limit ✅ Task 15
- Localization ✅ Task 9
- Tests (pure helpers + Cloud Function) ✅ Tasks 1, 2, 15

**Type consistency:** iOS uses `conflicts(with:todayWeekday:)` and `findConflicts(for:in:todayWeekday:)`. Android uses `conflictsWith(other, todayWeekday)` and `findConflicts(candidate, existing, todayWeekday)`. Names differ idiomatically per platform but signatures match across tasks.

**Placeholder scan:** Steps that touch large existing files (Dashboard views) reference exact line ranges to insert near; the actual code block to insert is fully shown. UI snippets reference one or two existing helpers (`saveExisting()`, `pickedImageData`, `pickedPhotoBytes`) that the engineer must locate in the existing file — flagged inline.
