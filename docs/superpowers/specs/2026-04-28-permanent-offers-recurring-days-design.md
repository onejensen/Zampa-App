# Permanent Offers — Recurring Days Selection

**Date:** 2026-04-28
**Status:** Approved

## Overview

Allow merchants to attach a day-of-week schedule to permanent offers so a "every Thursday tapas" special only appears in the customer feed on Thursdays. Enforce a limit of one permanent offer per weekday slot per merchant to prevent feed saturation.

## Context

The app already has `isPermanent: true` on offers that never expire. Currently a permanent offer is always visible in the customer feed, every day. There is no way to express "this offer is only available on certain days of the week."

This feature is exclusively for subscribed merchants (no free plan going forward, only a trial period at onboarding).

## Decisions

| Question | Decision |
|---|---|
| Feed visibility on non-selected days | Hidden — offer only appears on its selected days |
| Multi-day selection | Yes — one offer doc can cover multiple days (e.g. Mon + Wed + Fri) |
| Existing `isPermanent` offers without day selection | Treated as all 7 days (backward-compatible, occupies all slots) |
| Free vs Pro | Pro / trial only — no change from today |
| Filtering approach | Client-side (Approach A) — no new Firestore indexes or Cloud Functions |

## Data Model

One new field added to `dailyOffers` documents:

```
recurringDays: [Int]   // 0=Sunday, 1=Monday, …, 6=Saturday
```

Rules:
- Present only when `isPermanent == true`
- Absent or `null` on all non-permanent offers
- Existing permanent offers without `recurringDays` are treated by the client as `[0,1,2,3,4,5,6]` (always visible, occupies all 7 slots)
- No Firestore rules change required — the field is write-free for the authenticated owner, same as all other fields

## UI — Publish Form

When the merchant selects "Oferta permanente" as offer type, a day-picker section appears below the type selector:

- Seven circular buttons (L M X J V S D) arranged in a row
- **Available days:** white fill, dark border — tappable, toggles selection
- **Selected days:** brand orange fill — the days this offer will be active
- **Occupied days:** grey, non-tappable — already claimed by another permanent offer from this merchant
- Badge in the picker header: "N de 7 libres"
- Legend: "Seleccionado / Ocupado por otra permanente"
- Publish button disabled if zero days selected

The picker does not appear for non-permanent offer types.

**Occupied-day calculation (on form open):**
1. Fetch merchant's active permanent offers (`isPermanent == true && isActive == true`)
2. Compute `occupiedDays = union of all recurringDays` across those offers (offers without `recurringDays` → treat as all 7)
3. Disable those days in the picker

## Feed Filtering

Added to the client-side offer list processing in `FeedViewModel` (iOS and Android), after the Firestore query returns:

```
For each offer where isPermanent == true:
  if recurringDays is nil or empty → show (legacy behavior)
  else if todayWeekday ∈ recurringDays → show
  else → exclude from feed list
```

`todayWeekday` uses the device's local calendar (same timezone logic as the existing expiry check, which uses Madrid time — but for display purposes device locale is fine since the merchant and their local customers share the same day).

No change to Firestore queries. Non-permanent offers are unaffected.

## Editing

- `recurringDays` is editable from `EditMenuView` (iOS) and `EditMenuSheet` (Android)
- Occupied-day calculation excludes the offer currently being edited
- `isPermanent` remains immutable post-creation (no converting between daily and permanent)

## Validation & Limits

Client-side only — enforced through the day picker UX (occupied slots disabled). No Firestore rule change. Bypassing the client results in at most two permanent offers visible on the same day for that merchant — acceptable risk given the audience.

If all 7 slots are occupied, the day picker shows all buttons disabled and a message below: "Tienes una oferta permanente para cada día de la semana. Edita o elimina una existente para liberar días." The publish button remains disabled. The merchant can still change the offer type to a non-permanent type and publish normally.

## Migration

No data migration script needed. Existing `isPermanent: true` documents without `recurringDays` continue to work as-is; the client treats them as covering all 7 days. Merchants will notice all their slots appear occupied when they try to add a new permanent offer, which prompts them to edit or delete the existing one.

## Scope — What This Does NOT Change

- `expireMenus` Cloud Function — permanent offers are still never expired regardless of `recurringDays`
- `onMenuPublished` Cloud Function — push notifications on publish are unchanged
- Firestore security rules — no change
- Non-permanent offer types (Menú del día, Plato del día, Oferta del día) — unaffected
- Any subscription / billing logic

## Files to Touch

**iOS:**
- `Core/Models/Menu.swift` — add `recurringDays: [Int]?` field
- `Services/FirebaseService.swift` — write `recurringDays` on create; read it on fetch
- `Features/Merchant/CreateMenuView.swift` — add day picker section
- `Features/Merchant/EditMenuView.swift` — add editable day picker
- `Features/Feed/FeedViewModel.swift` (or equivalent) — filter logic
- `Localization/*.json` — new strings for picker labels

**Android:**
- `data/model/Models.kt` — add `recurringDays: List<Int>?` field
- `data/FirebaseService.kt` — write/read `recurringDays`
- `ui/merchant/DashboardScreen.kt` — day picker in CreateMenuSheet + EditMenuSheet
- `ui/feed/FeedScreen.kt` or feed ViewModel — filter logic
- `res/values*/strings.xml` — new strings for all 8 locales
