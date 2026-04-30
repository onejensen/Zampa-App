# Republish past offers — design

**Status:** Approved (2026-05-01)
**Branch context:** `feat/permanent-offers-recurring-days`
**Platforms:** iOS (`Zampa-iOS/`) and Android (`Zampa-Android/`) — both must ship in lockstep.

## Goal

Let merchants ("comercios") re-publish a previous offer (daily or permanent) from their history without re-creating it from scratch. When the new publication conflicts with an active offer in the same time slot, ask the merchant to confirm overwriting.

## Non-goals

- Bulk republish (multiple at once).
- Scheduling a republish for a future date.
- Editing the original document in place — every republish creates a new `dailyOffers` document.
- Telemetry on republish frequency (out of scope; trivial to add later).

## Definitions

- **Past offer** — a `dailyOffers` document with `isActive: false`, regardless of type. Daily offers become inactive automatically via the `expireMenus` Cloud Function; permanent offers become inactive only when the merchant disables them.
- **Republish** — creating a new `dailyOffers` document whose fields are copied from a chosen source document (the user can edit them before publishing). The new document carries `republishedFrom = <sourceId>` and `republishedAt = <now>`.
- **Conflict** — two offers (any type) collide if they share **at least one calendar day** of activity AND **at least one `serviceTime`**, where `"both"` overlaps with `"lunch"`, `"dinner"`, and `"both"`.

## Data model changes

### `dailyOffers` (additive — no breaking changes)

Two optional fields on the existing collection:

| Field | Type | Notes |
| --- | --- | --- |
| `republishedFrom` | `string?` | Document id of the source menu. Immutable after creation. |
| `republishedAt` | `Timestamp?` | Server timestamp set when the republish is created. |

Existing parse code (`parseMenu` iOS / `parseMenu` Android) must read both, defaulting to `null`. Documents that lack the fields keep working unchanged.

### `config/republishLimits` (new doc)

Lives in the existing `config/` collection (same place as `config/promo`). Existing rules already handle this collection: read for authenticated users, write only via Cloud Functions / admin.

```jsonc
{
  "perDay": 3   // max republications per merchant per calendar day (UTC)
}
```

- Cached client-side ~1h.
- Default value (if doc missing): `3`.

## Conflict detection (pure helper)

Implemented as a pure function on `Menu` — no Firebase dependency, fully unit-testable.

### Inputs

`candidate: Menu` (the offer about to publish), `existing: [Menu]` (active offers of the same `businessId`).

### Day set

```
daysOf(menu):
  daily                              → { todayLocal }
  permanent + recurringDays == null  → { next 7 calendar days }   // "every day"
  permanent + recurringDays != null  → recurringDays mapped to next 7 calendar days
```

`todayLocal` uses the merchant's device timezone, consistent with how `expireMenus` and `getActiveMenus` already filter.

### Service-time set

```
serviceTimesOf(menu):
  "lunch"  → { lunch }
  "dinner" → { dinner }
  "both"   → { lunch, dinner }
```

### Predicate

```
conflicts(c, o) := daysOf(c) ∩ daysOf(o) ≠ ∅
                AND serviceTimesOf(c) ∩ serviceTimesOf(o) ≠ ∅
                AND c.id != o.id      // republish is a brand-new doc, but defensive
                AND o.isActive == true
```

`findConflicts(candidate, existing) -> [Menu]` returns the list to surface in the confirmation modal.

## UI flow

### Dashboard — new "Ofertas anteriores" section

- Lives in the merchant Dashboard (iOS `DashboardScreen` / Android `DashboardScreen.kt`), below the active offers section.
- Collapsible, default **collapsed**.
- Lists inactive offers from `getMenusByMerchant`, sorted by `createdAt` desc, initial cap 30 with a "Ver más" pagination button.
- Each row: thumbnail + title + price + creation date + type badge (📅 daily / ♾️ permanent) + **"Republicar"** button.
- Empty state: localized `republish.history.empty`.

### Republish action

1. **Soft limit check** — query `dailyOffers where businessId == uid AND republishedFrom != null AND createdAt >= startOfTodayUTC`. If `count >= perDay`, show toast `republish.limit.reached` and abort.
2. **Open editor in republish mode** — reuses the existing edit sheet (`EditMenuView` iOS / `EditMenuSheet` Android) with all fields pre-filled from the source. Photo loaded by URL (no re-upload unless the user picks a new image). Header copy: `republish.editor.header`. Primary CTA copy: `republish.editor.cta`.
3. **On Publish tap** — run `findConflicts(candidate, currentActiveMenusOfMerchant)`.
   - **No conflicts** → publish directly.
   - **Conflicts** → modal `republish.conflict.title` listing each conflicting offer briefly. Buttons: `republish.conflict.confirm` / `republish.conflict.cancel`.
4. **Publish** — call `FirebaseService.republishMenu(...)` with the resolved `conflictsToDeactivate` ids.
5. On success → close sheet, refresh Dashboard.

## Service layer

New method on both `FirebaseService.swift` and `FirebaseService.kt`:

```
republishMenu(
  sourceMenuId: String,
  edited: MenuFields,            // all fields from the editor (title, description, price, etc.)
  existingPhotoUrl: String?,     // when non-null, reuse without re-upload
  newPhotoData: ByteArray?,      // when existingPhotoUrl is null, upload this
  conflictsToDeactivate: [String]
) -> Menu
```

### Steps

1. Read `config/republishLimits` (cached); fall back to `perDay = 3`.
2. Count today's republishes for the merchant; throw `republishLimitExceeded` if at limit.
3. Validate subscription / promo (same checks as `createMenu`).
4. Photo handling:
   - `existingPhotoUrl != null` → reuse the URL list as-is.
   - else → upload `newPhotoData` like `createMenu` does.
5. Server-derive `isMerchantPro` and `isMerchantVerified` from the merchant doc (must not trust client values).
6. Build a Firestore `WriteBatch`:
   - For each id in `conflictsToDeactivate`: `update` with `{ isActive: false, deactivatedReason: "republish_overwrite", updatedAt: serverTimestamp }`.
   - `set` the new `dailyOffers` doc with all edited fields plus `republishedFrom: sourceMenuId`, `republishedAt: serverTimestamp`.
7. Commit. Return the new `Menu`.

`createMenu` and `updateMenu` stay unchanged.

## Firestore rules (`firebase/firestore.rules`)

### `dailyOffers` create

Add to existing rules (do not relax current checks):

- If `request.resource.data.republishedFrom != null`:
  - Source doc must exist: `exists(/databases/$(database)/documents/dailyOffers/$(request.resource.data.republishedFrom))`.
  - Source must belong to the same merchant: `get(...).data.businessId == request.resource.data.businessId`.
- `republishedFrom` and `republishedAt` are immutable on update (no client-side rewrites).

### `dailyOffers` update

When `isActive` flips from `true` → `false`, allow if either:
- The merchant owns the doc and is the request author (existing rule), OR
- The update is part of an overwrite batch (no rule change needed — same author writing both docs).

### `config/{docId}` — no rule changes needed

The existing rule (`read: if isAuth(); write: if false;` from `firebase/firestore.rules:252-255`) already covers `config/republishLimits`. Admins edit it via Firebase Console.

## Cloud Function — hard limit

Modify `functions/index.js` → `onMenuPublished`:

1. If `data.republishedFrom != null`:
   - Query `dailyOffers where businessId == data.businessId AND republishedFrom != null AND createdAt >= startOfTodayUTC`.
   - Read `config/republishLimits.perDay` (fallback 3).
   - If `count > perDay`:
     - `update` the just-created doc: `{ isActive: false, deactivatedReason: "republish_limit_exceeded", updatedAt: serverTimestamp }`.
     - Insert an in-app `notifications` doc for the merchant explaining the rejection (localized server-side using the merchant's `languagePreference`).
     - **Return early** — do not fan out push notifications to followers.
2. Otherwise the existing notification flow runs unchanged (Pro merchants notify followers).

## Edge cases

- **No connectivity at publish time** — the `WriteBatch` fails atomically; nothing persists; show standard error toast.
- **Source menu deleted between Dashboard load and republish tap** — the rules `exists(...)` check rejects; surface a localized "ya no existe" error and refresh the list.
- **Plan tier or verification status changed since the source was created** — the new doc reflects the *current* server-derived values, not the source's snapshot.
- **Subscription expired** — same `subscription_expired` error path as `createMenu`.
- **Promo `freeUntil` active** — same handling as `createMenu`.
- **`recurringDays` semantics on overwrite** — overwrite means full deactivation of the conflicting docs (`isActive: false`); we do **not** trim `recurringDays`. The merchant always gets a clean "old offer is gone, new offer is live" outcome.
- **`getActiveMenus` and `expireMenus`** — both must keep ignoring `republishedFrom` (it does not affect activation logic). Verified: only `isActive`, `date`, and `recurringDays` matter for those queries today.

## Localization

New string keys, added to all 8 iOS JSON files (`Zampa-iOS/Zampa/Localization/{lang}.json`) and all 8 Android `values-{lang}/strings.xml` plus the default `values/strings.xml`:

| Key | Spanish source |
| --- | --- |
| `republish.section.title` | "Ofertas anteriores" |
| `republish.section.empty` | "Aún no tienes ofertas anteriores" |
| `republish.section.show_more` | "Ver más" |
| `republish.button` | "Republicar" |
| `republish.editor.header` | "Republicar — revisa y publica" |
| `republish.editor.cta` | "Publicar" |
| `republish.conflict.title` | "Ya tienes una oferta activa en esta franja" |
| `republish.conflict.body` | "Esta republicación choca con %d oferta(s):" |
| `republish.conflict.confirm` | "Sobrescribir y publicar" |
| `republish.conflict.cancel` | "Cancelar" |
| `republish.limit.reached` | "Has alcanzado el límite diario de %d republicaciones" |
| `republish.error.source_missing` | "La oferta original ya no existe" |
| `republish.error.generic` | "No se pudo republicar. Inténtalo de nuevo." |
| `republish.notification.limit_blocked.title` | "Republicación bloqueada" |
| `republish.notification.limit_blocked.body` | "Has superado el límite diario de %d republicaciones." |

Translations follow the conventions in the existing locale files (Catalan, Basque, Galician, English, German, French, Italian).

## Tests

### Pure helpers (unit)

- iOS: add to `Zampa-iOSTests/MenuConflictTests.swift` (or the closest existing test target).
- Android: add to `Zampa-Android/app/src/test/java/.../MenuConflictTest.kt`.
- Cover: daily↔daily same/different `serviceTime`; daily↔permanent (with and without `recurringDays`); permanent↔permanent overlapping/non-overlapping `recurringDays`; `"both"` interaction with `"lunch"` and `"dinner"`.

### Firestore rules

- Add to the existing rules test suite (or create one if absent under `firebase/`).
- Cases: republish to another merchant's doc (rejected), republish without source doc (rejected), republish with valid source (allowed), `republishedFrom` mutation on update (rejected).

### Cloud Function

- Add to `functions/test/` using the Firebase emulator.
- Case: 4 republishes in a row by the same merchant; the 4th lands with `isActive: false` and an in-app notification, and no FCM push is sent.

### Manual QA

- Republish a daily — verify no conflict, no overwrite, follower receives push.
- Republish a daily when one is already active today same `serviceTime` — verify modal, choose overwrite, verify old goes inactive and new is active.
- Republish a permanent with `recurringDays` overlapping an active permanent — verify modal lists the conflict.
- Hit the daily limit — verify button disabled with tooltip; force a 4th via two devices to validate the Cloud Function killswitch.
- Repeat all of the above on the other platform.

## Implementation order

This is an outline for the future writing-plans pass; it is not a binding plan.

1. Pure conflict helper + unit tests on both platforms.
2. Schema additions (`republishedFrom`, `republishedAt`) in `parseMenu` (read) and serialization helpers (write).
3. `FirebaseService.republishMenu(...)` on both platforms.
4. UI: collapsible "Ofertas anteriores" section + Republish entry + editor mode.
5. Conflict modal + overwrite path.
6. Soft-limit query and disabled-button state.
7. Localization strings (all locales, both platforms).
8. Firestore rules update + emulator tests.
9. Cloud Function hard-limit + emulator test.
10. `config/republishLimits` seeded in Firestore Console (manual GUI step, document in launch checklist).

## Open questions

None — design approved 2026-05-01.
