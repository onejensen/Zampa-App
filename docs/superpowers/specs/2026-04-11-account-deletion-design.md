# Account Deletion — Design Spec

**Date:** 2026-04-11
**Scope:** Add a "Delete account" flow for customer (cliente) accounts on iOS, Android, and Cloud Functions. Merchant (comercio) deletion is out of scope for this iteration.
**Deletion model:** Soft delete with a 30-day grace period. A scheduled Cloud Function purges accounts whose grace period has expired.

---

## 1. Goals and non-goals

### Goals
- Let customers request deletion of their own account from the Profile screen.
- Show a clear, irreversible-once-purged warning before confirming.
- Keep the deletion reversible for 30 days by design (grace period with explicit recovery screen).
- Remove the user's PII and content (favorites, history, notifications, avatar) at the end of the grace period.
- Delete the Firebase Auth user at the end of the grace period so the email/phone is freed up.

### Non-goals
- Deleting merchant (comercio) accounts — requires a separate flow because of subscriptions, offers, metrics, and follower fan-out.
- GDPR data export ("right to access").
- Analytics or telemetry around deletion events.
- Automated test suites for rules, Cloud Functions, or UI — manual verification for v1.

---

## 2. Data model

Two optional fields on the existing `users/{uid}` document:

| Field | Type | Semantics |
|---|---|---|
| `deletedAt` | `Timestamp` \| absent | Set when the user requests deletion. Absent/null means the account is active. |
| `scheduledPurgeAt` | `Timestamp` \| absent | Equals `deletedAt + 30 days`. Denormalized so the Cloud Function query is cheap and the client can display the exact purge date without computing it. |

No other collection schemas change. Nothing else is modified during the grace period.

### Account states

| State | `deletedAt` | `scheduledPurgeAt` | App behavior |
|---|---|---|---|
| Active | absent / null | absent / null | Normal. |
| Pending deletion | set (past) | set (future) | Router redirects to recovery screen; writes to Firestore blocked; no push notifications. |
| Purge eligible | set (past) | set (past) | Cloud Function will purge in the next scheduled run. |
| Purged | — | — | User doc no longer exists. Audit entry in `purgedAccounts/{uid}`. |

---

## 3. Client flow

### 3.1 Entry point — "Eliminar cuenta" button

Only visible for `role == cliente`. Placed at the bottom of the Profile screen, below the existing "Cerrar Sesión" button, with a `Spacer` separating them so it can't be mistapped.

- **iOS:** added as a new `Section` at the end of the `List` in `ProfileView.swift`, styled as a destructive secondary text button (muted red).
- **Android:** added as a new `item {}` at the bottom of the `LazyColumn` in `ProfileScreen.kt`, as a `TextButton` with `contentColor = MaterialTheme.colorScheme.error`.

### 3.2 Typed-confirmation dialog

Tapping "Eliminar cuenta" opens a modal dialog with:

- A warning body explaining that the account is scheduled for permanent deletion in 30 days, that the user can recover it by logging in during that window, and listing what will be purged (profile and photo, favorites, history).
- A `TextField` labeled "Para confirmar, escribe ELIMINAR".
- Two buttons: `Cancelar` (dismiss) and `Eliminar` (destructive, disabled until the text field equals exactly `ELIMINAR` — case-sensitive, trimmed).

On confirm:
1. Client executes a Firestore batch. Both timestamps are computed client-side so they stay consistent:
   - `let now = Date()` (or `Timestamp.now()` on Android)
   - Update `users/{uid}` with `deletedAt = Timestamp(now)` and `scheduledPurgeAt = Timestamp(now + 30 * 24 * 3600)`.
   - Delete `deviceTokens/{uid}` so push notifications stop immediately.
   - Client-side timestamps can drift a few seconds from the server, but the purge function's criterion is `scheduledPurgeAt <= serverNow`, so any drift is absorbed safely.
2. Firebase Auth `signOut()`.
3. Navigate back to the auth entry point (`AuthView` / `AuthScreen`).

### 3.3 Recovery screen

After `onAuthStateChanged` fires and the user document is loaded, the router checks `user.deletedAt`. If it is non-null and `scheduledPurgeAt > now()`, the router redirects to the recovery screen instead of `MainTabView` / `MainScreen`.

The recovery screen is a full-screen modal:

- Warning icon + title: "Cuenta pendiente de eliminación".
- Body: "Tu cuenta se eliminará el [formatted scheduledPurgeAt]."
- Secondary line: "Si quieres conservarla, pulsa Recuperar cuenta."
- Primary button: `Recuperar cuenta` — executes `users/{uid}` update clearing `deletedAt` and `scheduledPurgeAt`, then routes to `MainTabView` / `MainScreen` and shows a toast/snackbar "Cuenta recuperada".
- Secondary button: `Cerrar sesión` — calls `signOut()` and returns to `AuthView`.

The recovery screen fires on every app launch with cached session while `deletedAt != null`, not only on explicit login. This means if the user already had the app open and their session alive at deletion time, the next foreground will land them on the recovery screen (but in practice they'll be signed out immediately after confirming deletion, so this is the edge case of a second device).

### 3.4 Router implementation

- **iOS:** `ContentView.swift` already routes between `AuthView`, `MerchantProfileSetupView`, and `MainTabView` based on `AppState`. Add a new case: when `appState.currentUser?.deletedAt != nil`, show `AccountDeletionRecoveryView`.
- **Android:** `Navigation.kt` (`EatOutNavHost`) owns the route switch. Add a new route and check in the same place where Auth → Main transitions happen. `AuthViewModel` inspects the `User` returned from `FirebaseService.getCurrentUser()` and emits a state indicating "pending deletion".

---

## 4. Firestore rules

Changes to `firebase/firestore.rules`:

```
match /users/{userId} {
  allow update: if request.auth.uid == userId &&
    (
      // Requesting deletion: sets both timestamps
      (request.resource.data.deletedAt is timestamp &&
       request.resource.data.scheduledPurgeAt is timestamp) ||
      // Recovering: clears both
      (!('deletedAt' in request.resource.data) &&
       !('scheduledPurgeAt' in request.resource.data)) ||
      // Normal write: account must not be pending deletion
      (!('deletedAt' in resource.data) || resource.data.deletedAt == null)
    );
}
```

Other collections (`favorites`, `userHistory`, `deviceTokens`, `notifications`) are NOT updated to block writes from pending-deletion users. The client flow guarantees the user is signed out or on the recovery screen during the grace period, so there is no legitimate write path. Any stray writes (e.g., from a bypassed client) would be purged anyway at day 30. The `get()` lookup in rules is expensive and would apply to every write in the app — the cost isn't justified for an edge case the purge cleans up regardless.

---

## 5. Cloud Function — `purgeDeletedAccounts`

### 5.1 Definition

Added to `functions/index.js` alongside `onMenuPublished` and `expireMenus`. Node.js 22, 2nd-gen Cloud Functions.

- **Type:** scheduled function.
- **Schedule:** `every day 03:00` in `Europe/Madrid` timezone.
- **Credentials:** default admin credentials (bypasses Firestore rules).
- **Timeout:** 540 seconds (9 minutes, the max for scheduled functions).

### 5.2 Algorithm

```
1. Query users where scheduledPurgeAt <= now(), limit 50.
2. For each user doc, serially:
   a. uid = doc.id
   b. Delete, best-effort in this order:
      - favorites where userId == uid (paginated batched deletes, 500/chunk)
      - userHistory where userId == uid (paginated batched deletes)
      - notifications where userId == uid (paginated batched deletes)
      - deviceTokens/{uid}
      - customers/{uid}
      - users/{uid}
      - Storage: list and delete everything under avatars/{uid}/
      - Firebase Auth: admin.auth().deleteUser(uid)
   c. Write purgedAccounts/{uid} with {
        purgedAt: serverTimestamp(),
        deletedAt: <original>,
        stepsCompleted: [...],
        errors: [...]
      }
3. If any step throws, capture the error in the audit log and continue
   to the next step (do NOT abort the user's purge on partial failure).
```

### 5.3 Idempotency and partial failures

- The query in step 1 filters on `scheduledPurgeAt <= now()` and the presence of the field. After a successful purge, `users/{uid}` no longer exists, so the query won't re-select it. Subcollection writes that fail leave orphans, but those are invisible to the app and can be cleaned up manually if needed.
- If `users/{uid}` deletion itself fails, the user remains in the query for the next day's run, which will retry all steps. Earlier-completed steps (e.g., favorites already deleted) become no-ops.
- `admin.auth().deleteUser(uid)` throws `auth/user-not-found` if the Auth user was already removed (manual deletion in the console, or a previous partial run). The error is caught and logged as `stepsCompleted: ['auth_already_missing']`, not as an error.

### 5.4 Batch size

50 users per run. Given the expected volume (pre-launch app, low daily requests), this is more than enough. If the backlog ever grows, the next day picks up the remainder. If we ever need higher throughput we can increase the limit or parallelize — out of scope for v1.

### 5.5 Audit log

`purgedAccounts/{uid}` is a new top-level collection. It stores only the uid (not PII) and metadata about the purge. It is kept indefinitely. It exists so that if a user ever asks "did you really delete my account and when", we have an answer, and so that partial-failure debugging is possible.

---

## 6. Client service methods

### 6.1 `FirebaseService.requestAccountDeletion()` (iOS and Android)

Executes the batch described in section 3.2 step 1. Returns on success; throws on batch failure (caller handles by showing an error alert, no state change).

### 6.2 `FirebaseService.cancelAccountDeletion()` (iOS and Android)

Updates `users/{uid}` clearing `deletedAt` and `scheduledPurgeAt` using `FieldValue.delete()`. Called from the recovery screen's "Recuperar cuenta" button.

### 6.3 Model update

- **iOS:** `User.swift` adds `let deletedAt: Date?` and `let scheduledPurgeAt: Date?` to the Firestore codable.
- **Android:** `Models.kt` adds `val deletedAt: com.google.firebase.Timestamp? = null` and `val scheduledPurgeAt: com.google.firebase.Timestamp? = null` to `data class User`.

---

## 7. Implementation order

1. `functions/index.js` — add `purgeDeletedAccounts`. Deploy.
2. `firebase/firestore.rules` — add rule for `users` update. Deploy.
3. `User` model — add fields on iOS and Android.
4. `FirebaseService` — add `requestAccountDeletion()` and `cancelAccountDeletion()` on iOS and Android.
5. Router check — `ContentView.swift` and `Navigation.kt` / `AuthViewModel.kt` detect `deletedAt != null` and route accordingly.
6. Recovery screen — `AccountDeletionRecoveryView.swift` and `AccountDeletionRecoveryScreen.kt`.
7. Typed-confirmation dialog — inline in `ProfileView.swift` (as a sheet) and `ProfileScreen.kt` (as an `AlertDialog`).
8. "Eliminar cuenta" button — in `ProfileView.swift` and `ProfileScreen.kt`, visible only when `user.role == cliente`.
9. Optional: `functions/admin-delete-user.js` — admin CLI helper to force-purge a given uid. Useful for testing and support.
10. Manual end-to-end test: request deletion on iOS, verify Firestore state, log back in, see recovery screen, recover, log in again normally; repeat with forced `scheduledPurgeAt` in the past to trigger the purge function.

## 8. Files affected

### iOS
**New:**
- `Zampa-iOS/Zampa/Features/Auth/AccountDeletionRecoveryView.swift`

**Modified:**
- `Zampa-iOS/Zampa/Core/Models/User.swift` — new optional fields
- `Zampa-iOS/Zampa/Services/FirebaseService.swift` — two new methods
- `Zampa-iOS/Zampa/App/ContentView.swift` — router check
- `Zampa-iOS/Zampa/Features/Profile/ProfileView.swift` — button + typed-confirmation sheet

### Android
**New:**
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AccountDeletionRecoveryScreen.kt`

**Modified:**
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt` — new optional fields
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt` — two new methods
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt` — router check
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt` — detect pending-deletion state post-login
- `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt` — button + typed-confirmation `AlertDialog`

### Backend
**Modified:**
- `functions/index.js` — new `purgeDeletedAccounts` scheduled function
- `firebase/firestore.rules` — updated `users` rule

**Optional new:**
- `functions/admin-delete-user.js` — CLI helper for manual/support-triggered purges

---

## 9. Testing strategy (v1)

- **Cloud Function:** manual test via a helper script that seeds a throwaway user with `scheduledPurgeAt` in the past, then triggers the function with `firebase functions:shell` or by deploying and waiting for the scheduler.
- **Firestore rules:** manual verification in the Firebase Console Rules Playground. Verify positive cases (user sets own `deletedAt`, user clears own `deletedAt`, user writes normally) and negative cases (user sets someone else's `deletedAt`, user writes while `deletedAt` is set).
- **iOS / Android:** manual end-to-end on a test device. No unit or UI tests added for v1 — the project has no existing test suites for these layers.
- **Regression check:** verify the existing `onMenuPublished` and `expireMenus` functions still deploy successfully after adding the new one.
