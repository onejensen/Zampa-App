# Account Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Delete account" flow for customer accounts across iOS, Android, and Cloud Functions, with a 30-day soft-delete grace period and a scheduled purge function.

**Architecture:** Client-side soft delete sets `deletedAt` and `scheduledPurgeAt` on `users/{uid}` and removes the user's FCM device tokens. On next login, a router check redirects to a recovery screen where the user can either recover or confirm sign-out. A daily scheduled Cloud Function (`purgeDeletedAccounts`) hard-deletes expired accounts: Firestore data, Storage avatars, and the Firebase Auth user, writing an audit trail to `purgedAccounts/{uid}`.

**Tech Stack:** Swift / SwiftUI (iOS), Kotlin / Jetpack Compose (Android), Node.js 22 + Firebase Functions 2nd gen, Firestore, Firebase Storage, Firebase Auth. **Note:** the project has no automated test suite for UI, rules, or Cloud Functions — verification is manual per the design spec. Each task ends in a build/compile check and a manual verification checklist, not unit tests.

**Related spec:** `docs/superpowers/specs/2026-04-11-account-deletion-design.md`

---

## Task 1: Cloud Function `purgeDeletedAccounts`

**Files:**
- Modify: `functions/index.js`

- [ ] **Step 1: Add the scheduled function and helper to `functions/index.js`**

Append this block at the end of `functions/index.js`, AFTER the existing `removeInvalidTokens` helper (so it sits alongside the existing functions and reuses the same `admin`, `logger`, `onSchedule`, and `chunkArray` imports that are already at the top of the file):

```javascript
/**
 * Purga cuentas de usuario que han pasado el periodo de gracia de 30 días.
 * Scheduled: diario a las 03:00 Europe/Madrid. Timeout máximo (9 min) para
 * tolerar backlogs pequeños. Lote máx 50 usuarios por ejecución.
 *
 * Para cada usuario con scheduledPurgeAt <= now():
 *   1. Borra favorites (customerId == uid)
 *   2. Borra userHistory (userId == uid)
 *   3. Borra notifications (userId == uid)
 *   4. Borra deviceTokens (userId == uid)
 *   5. Borra customers/{uid}
 *   6. Borra Storage users/{uid}/
 *   7. Borra users/{uid}
 *   8. Borra el Auth user
 *   9. Escribe purgedAccounts/{uid} con el resultado (auditoría)
 *
 * Pasos best-effort: un fallo en un paso NO aborta el resto, se registra en
 * `errors` y el usuario queda marcado como parcialmente procesado. Si el paso
 * 7 falla, la query del siguiente día lo volverá a seleccionar y reintentará.
 */
exports.purgeDeletedAccounts = onSchedule(
    {
        schedule: "every day 03:00",
        timeZone: "Europe/Madrid",
        timeoutSeconds: 540,
    },
    async (event) => {
        const db = admin.firestore();
        const now = admin.firestore.Timestamp.now();

        const pendingSnap = await db.collection("users")
            .where("scheduledPurgeAt", "<=", now)
            .limit(50)
            .get();

        if (pendingSnap.empty) {
            logger.info("purgeDeletedAccounts: no hay cuentas pendientes de purga.");
            return;
        }

        logger.info(`purgeDeletedAccounts: procesando ${pendingSnap.size} cuentas.`);

        for (const userDoc of pendingSnap.docs) {
            const uid = userDoc.id;
            const originalDeletedAt = userDoc.data().deletedAt || null;
            const stepsCompleted = [];
            const errors = [];

            async function runStep(name, fn) {
                try {
                    await fn();
                    stepsCompleted.push(name);
                } catch (e) {
                    errors.push({ step: name, message: e.message || String(e) });
                    logger.error(`purgeDeletedAccounts[${uid}] paso '${name}' falló:`, e);
                }
            }

            await runStep("favorites", () => deleteQueryInBatches(
                db.collection("favorites").where("customerId", "==", uid)
            ));
            await runStep("userHistory", () => deleteQueryInBatches(
                db.collection("userHistory").where("userId", "==", uid)
            ));
            await runStep("notifications", () => deleteQueryInBatches(
                db.collection("notifications").where("userId", "==", uid)
            ));
            await runStep("deviceTokens", () => deleteQueryInBatches(
                db.collection("deviceTokens").where("userId", "==", uid)
            ));
            await runStep("customers", () =>
                db.collection("customers").doc(uid).delete()
            );
            await runStep("storage", async () => {
                const bucket = admin.storage().bucket();
                const [files] = await bucket.getFiles({ prefix: `users/${uid}/` });
                for (const file of files) {
                    await file.delete();
                }
            });
            await runStep("users", () =>
                db.collection("users").doc(uid).delete()
            );

            // Auth deletion has a special-cased "already missing" state
            try {
                await admin.auth().deleteUser(uid);
                stepsCompleted.push("auth");
            } catch (e) {
                if (e.code === "auth/user-not-found") {
                    stepsCompleted.push("auth_already_missing");
                } else {
                    errors.push({ step: "auth", message: e.message || String(e) });
                    logger.error(`purgeDeletedAccounts[${uid}] paso 'auth' falló:`, e);
                }
            }

            // Audit log (fuera del try/catch principal para que se registre siempre)
            try {
                await db.collection("purgedAccounts").doc(uid).set({
                    purgedAt: admin.firestore.FieldValue.serverTimestamp(),
                    deletedAt: originalDeletedAt,
                    stepsCompleted,
                    errors,
                });
            } catch (e) {
                logger.error(`purgeDeletedAccounts[${uid}] no se pudo escribir auditoría:`, e);
            }

            if (errors.length > 0) {
                logger.warn(`purgeDeletedAccounts[${uid}] completado con errores.`);
            } else {
                logger.info(`purgeDeletedAccounts[${uid}] purgado completamente.`);
            }
        }
    }
);

/** Borra todos los documentos que coincidan con una query en lotes de 400. */
async function deleteQueryInBatches(query) {
    const db = admin.firestore();
    while (true) {
        const snap = await query.limit(400).get();
        if (snap.empty) return;
        const batch = db.batch();
        snap.docs.forEach(doc => batch.delete(doc.ref));
        await batch.commit();
        if (snap.size < 400) return;
    }
}
```

- [ ] **Step 2: Install dependencies and run a syntax check**

Run:
```bash
cd functions && node -e "require('./index.js'); console.log('ok')"
```

Expected: prints `ok` and exits 0. If you see a `SyntaxError`, fix the syntax in `functions/index.js` before proceeding. If you see a "cannot find module" error from Firebase Admin (because `admin.initializeApp()` runs on require), that's expected — in that case verify syntax only with:
```bash
node --check functions/index.js && echo "syntax ok"
```

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add functions/index.js && git commit -m "$(cat <<'EOF'
Add purgeDeletedAccounts scheduled Cloud Function

Daily purge of customer accounts whose 30-day grace period has expired.
Deletes Firestore data, Storage avatars, and the Firebase Auth user, then
writes an audit entry to purgedAccounts/{uid}.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Firestore rules — allow soft-delete flow

**Files:**
- Modify: `firebase/firestore.rules:31-38`

- [ ] **Step 1: Replace the `/users/{userId}` rule**

Find this block in `firebase/firestore.rules`:

```
    // ── Users ──
    // Privacy: solo el dueño puede leer su propio doc (email/teléfono no son públicos).
    // Para mostrar el nombre/avatar del comercio en el feed se usa /businesses (público).
    match /users/{userId} {
      allow read: if isOwner(userId);
      allow create: if isOwner(userId);
      allow update: if isOwner(userId)
        // Impedir auto-elevación de rol
        && unchanged('role');
      allow delete: if false;
    }
```

Replace it with:

```
    // ── Users ──
    // Privacy: solo el dueño puede leer su propio doc (email/teléfono no son públicos).
    // Para mostrar el nombre/avatar del comercio en el feed se usa /businesses (público).
    match /users/{userId} {
      allow read: if isOwner(userId);
      allow create: if isOwner(userId);
      // Update permitido sólo al dueño. No se puede auto-elevar el rol.
      // Además, el flow de eliminación de cuenta limita qué updates son válidos
      // dependiendo del estado actual del doc:
      //   - Solicitar eliminación: setea deletedAt y scheduledPurgeAt (ambos timestamp)
      //   - Recuperar cuenta: ambos campos deben quedar ausentes tras el write
      //   - Escritura normal: sólo si la cuenta está activa (deletedAt no presente / null)
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
      // Las cuentas NO se borran desde el cliente: sólo la Cloud Function
      // purgeDeletedAccounts lo hace con credenciales de admin (que bypasan rules).
      allow delete: if false;
    }
```

- [ ] **Step 2: Commit**

```bash
git add firebase/firestore.rules && git commit -m "$(cat <<'EOF'
Update users rules to support account deletion flow

Client writes to users/{uid} must now match one of three shapes: requesting
deletion (sets both timestamps), recovering (clears both timestamps), or a
normal write while the account is not pending deletion. Role can still not
be self-elevated.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Deploy backend (functions + rules)

**Files:** (no changes — deploy step only)

- [ ] **Step 1: Ensure Firebase CLI is logged in**

Run:
```bash
firebase projects:list
```

Expected: the list includes `eatout-70b8b`. If not, run `firebase login` first.

- [ ] **Step 2: Deploy Firestore rules**

Run:
```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && firebase deploy --only firestore:rules --project eatout-70b8b
```

Expected: `Deploy complete!` with no rule compilation errors.

- [ ] **Step 3: Deploy the new Cloud Function**

Run:
```bash
firebase deploy --only functions:purgeDeletedAccounts --project eatout-70b8b
```

Expected: `Function URL: ...` and `Deploy complete!`. Because it's a scheduled function, the URL will be internal. The first deployment may take 2-4 minutes.

- [ ] **Step 4: Verify the function exists in the console**

Open `https://console.firebase.google.com/project/eatout-70b8b/functions` in a browser and confirm `purgeDeletedAccounts` is listed with trigger "scheduled" and schedule "every day 03:00". No commit (this task produces no code changes).

---

## Task 4: iOS — User model + Firestore parsing

**Files:**
- Modify: `Zampa-iOS/Zampa/Core/Models/User.swift`
- Modify: `Zampa-iOS/Zampa/Services/FirebaseService.swift:24-41`

- [ ] **Step 1: Add fields to the `User` struct**

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

    init(
        id: String,
        email: String,
        name: String,
        role: UserRole,
        phone: String? = nil,
        photoUrl: String? = nil,
        deletedAt: Date? = nil,
        scheduledPurgeAt: Date? = nil
    ) {
        self.id = id
        self.email = email
        self.name = name
        self.role = role
        self.phone = phone
        self.photoUrl = photoUrl
        self.deletedAt = deletedAt
        self.scheduledPurgeAt = scheduledPurgeAt
    }

    enum UserRole: String, Codable {
        case cliente = "CLIENTE"
        case comercio = "COMERCIO"
    }
}
```

- [ ] **Step 2: Update `getCurrentUser()` to parse the new fields**

In `Zampa-iOS/Zampa/Services/FirebaseService.swift`, locate `getCurrentUser()` (around line 24). Replace the function body:

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
            scheduledPurgeAt: (data["scheduledPurgeAt"] as? Timestamp)?.dateValue()
        )
    }
```

`Timestamp` comes from `FirebaseFirestore`, which is already imported at the top of `FirebaseService.swift`.

- [ ] **Step 3: Commit**

```bash
git add Zampa-iOS/Zampa/Core/Models/User.swift Zampa-iOS/Zampa/Services/FirebaseService.swift && git commit -m "$(cat <<'EOF'
iOS: add deletedAt / scheduledPurgeAt to User model

Parse both fields from Firestore in getCurrentUser() so the router can
detect pending-deletion state.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: iOS — FirebaseService deletion methods

**Files:**
- Modify: `Zampa-iOS/Zampa/Services/FirebaseService.swift`

- [ ] **Step 1: Add `requestAccountDeletion()` and `cancelAccountDeletion()`**

In `Zampa-iOS/Zampa/Services/FirebaseService.swift`, find the existing `updateUserName(_:)` method (around line 247). Add the following two methods immediately AFTER it:

```swift
    // MARK: - Account deletion (soft delete + 30-day grace period)

    /// Marca la cuenta del usuario actual como pendiente de eliminación.
    /// Setea `deletedAt` y `scheduledPurgeAt` en `users/{uid}` y borra todos los
    /// documentos de `deviceTokens` del usuario para cortar notificaciones push
    /// inmediatamente. NO borra el Auth user ni datos de usuario: eso lo hace
    /// la Cloud Function `purgeDeletedAccounts` pasados los 30 días.
    func requestAccountDeletion() async throws {
        guard let uid = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }

        // 1. Borrar device tokens del usuario (query + delete por cada doc)
        let tokensSnap = try await db.collection("deviceTokens")
            .whereField("userId", isEqualTo: uid)
            .getDocuments()
        for doc in tokensSnap.documents {
            try await doc.reference.delete()
        }

        // 2. Marcar cuenta como pendiente de eliminación
        let now = Date()
        let purge = now.addingTimeInterval(30 * 24 * 60 * 60)
        try await db.collection("users").document(uid).updateData([
            "deletedAt": Timestamp(date: now),
            "scheduledPurgeAt": Timestamp(date: purge),
        ])
    }

    /// Cancela una eliminación pendiente limpiando `deletedAt` y `scheduledPurgeAt`.
    /// Sólo tiene sentido llamarla durante el periodo de gracia.
    func cancelAccountDeletion() async throws {
        guard let uid = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        try await db.collection("users").document(uid).updateData([
            "deletedAt": FieldValue.delete(),
            "scheduledPurgeAt": FieldValue.delete(),
        ])
    }
```

- [ ] **Step 2: Commit**

```bash
git add Zampa-iOS/Zampa/Services/FirebaseService.swift && git commit -m "$(cat <<'EOF'
iOS: add requestAccountDeletion and cancelAccountDeletion to FirebaseService

requestAccountDeletion sets deletedAt/scheduledPurgeAt and deletes the
user's FCM tokens. cancelAccountDeletion clears both fields to recover.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: iOS — AccountDeletionRecoveryView

**Files:**
- Create: `Zampa-iOS/Zampa/Features/Auth/AccountDeletionRecoveryView.swift`

- [ ] **Step 1: Create the recovery view**

Create a new file `Zampa-iOS/Zampa/Features/Auth/AccountDeletionRecoveryView.swift` with this content:

```swift
import SwiftUI

/// Pantalla que se muestra cuando un usuario inicia sesión (o abre la app con
/// sesión cacheada) y su cuenta está marcada como pendiente de eliminación.
/// Ofrece recuperar la cuenta o cerrar sesión.
struct AccountDeletionRecoveryView: View {
    @EnvironmentObject var appState: AppState
    @State private var isRecovering = false
    @State private var errorMessage: String?
    @State private var showRecoveredToast = false

    private var purgeDate: Date {
        appState.currentUser?.scheduledPurgeAt ?? Date()
    }

    private var formattedPurgeDate: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_ES")
        formatter.dateStyle = .full
        return formatter.string(from: purgeDate)
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.orange)

                Text("Cuenta pendiente\nde eliminación")
                    .font(.appHeadline)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.appTextPrimary)

                VStack(spacing: 8) {
                    Text("Tu cuenta se eliminará el")
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)
                    Text(formattedPurgeDate)
                        .font(.appBody)
                        .fontWeight(.bold)
                        .foregroundColor(.appTextPrimary)
                        .multilineTextAlignment(.center)
                }

                Text("Si quieres conservarla, pulsa Recuperar cuenta.")
                    .font(.appBody)
                    .foregroundColor(.appTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                Spacer()

                VStack(spacing: 12) {
                    Button(action: recoverAccount) {
                        HStack {
                            Spacer()
                            if isRecovering {
                                ProgressView().tint(.white)
                            } else {
                                Text("Recuperar cuenta")
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: isRecovering))
                    .disabled(isRecovering)

                    Button("Cerrar sesión") {
                        appState.logout()
                    }
                    .foregroundColor(.appTextSecondary)
                    .disabled(isRecovering)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 48)
            }

            if showRecoveredToast {
                VStack {
                    Text("Cuenta recuperada")
                        .font(.appBody)
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(Color.green))
                        .padding(.top, 60)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .alert("Error", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK") { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func recoverAccount() {
        isRecovering = true
        Task {
            do {
                try await FirebaseService.shared.cancelAccountDeletion()

                // Mostrar toast ANTES de refrescar el usuario: si refrescamos
                // primero, el router cambia a MainTabView y la vista se
                // desmonta antes de que el toast sea visible.
                await MainActor.run {
                    withAnimation { showRecoveredToast = true }
                }
                try? await Task.sleep(nanoseconds: 1_500_000_000)

                if let updated = try? await FirebaseService.shared.getCurrentUser() {
                    await MainActor.run {
                        appState.currentUser = updated
                        isRecovering = false
                    }
                }
            } catch {
                await MainActor.run {
                    isRecovering = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

#Preview {
    AccountDeletionRecoveryView()
        .environmentObject(AppState())
}
```

- [ ] **Step 2: Add the file to the Xcode project**

Xcode projects track files in `project.pbxproj`. To make the new file compile, you must add it to the `Zampa` target:

1. Open `Zampa-iOS/Zampa.xcodeproj` in Xcode.
2. In the Project Navigator (left sidebar), right-click on the `Features/Auth` group.
3. Choose **Add Files to "Zampa"...**.
4. Select `AccountDeletionRecoveryView.swift` from `Zampa-iOS/Zampa/Features/Auth/`.
5. Ensure **"Copy items if needed"** is UNCHECKED (file already in place), **"Create groups"** is selected, and the **Zampa** target checkbox IS CHECKED.
6. Click **Add**.

- [ ] **Step 3: Commit**

```bash
git add Zampa-iOS/Zampa/Features/Auth/AccountDeletionRecoveryView.swift Zampa-iOS/Zampa.xcodeproj/project.pbxproj && git commit -m "$(cat <<'EOF'
iOS: add AccountDeletionRecoveryView for the 30-day grace period

Shown when a logged-in user has deletedAt set on their profile.
Offers a "Recuperar cuenta" button that clears the pending deletion
flag, and a "Cerrar sesión" button that signs out without recovering.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: iOS — ContentView router check

**Files:**
- Modify: `Zampa-iOS/Zampa/App/ContentView.swift:27-38`

- [ ] **Step 1: Insert the deletedAt check into the router**

In `Zampa-iOS/Zampa/App/ContentView.swift`, locate this block:

```swift
            } else if appState.isAuthenticated {
                if appState.needsMerchantSetup {
                    // Merchant que no ha completado su perfil
                    MerchantProfileSetupView()
                } else {
                    // Usuario autenticado: pantalla principal
                    MainTabView()
                }
            } else {
                // No autenticado: login/registro
                AuthView()
            }
```

Replace it with:

```swift
            } else if appState.isAuthenticated {
                if appState.currentUser?.deletedAt != nil {
                    // Cuenta pendiente de eliminación → pantalla de recuperación
                    AccountDeletionRecoveryView()
                } else if appState.needsMerchantSetup {
                    // Merchant que no ha completado su perfil
                    MerchantProfileSetupView()
                } else {
                    // Usuario autenticado: pantalla principal
                    MainTabView()
                }
            } else {
                // No autenticado: login/registro
                AuthView()
            }
```

- [ ] **Step 2: Commit**

```bash
git add Zampa-iOS/Zampa/App/ContentView.swift && git commit -m "$(cat <<'EOF'
iOS: route to AccountDeletionRecoveryView when deletedAt is set

Checked before MerchantProfileSetupView so merchants in grace period
also land on the recovery screen (safety net — merchants can't trigger
deletion in v1 but the router should still honor the flag if it's set).

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: iOS — Profile delete button + typed-confirmation sheet

**Files:**
- Modify: `Zampa-iOS/Zampa/Features/Profile/ProfileView.swift`

- [ ] **Step 1: Add `.deleteAccount` to the `ProfileSheet` enum**

In `Zampa-iOS/Zampa/Features/Profile/ProfileView.swift`, locate the enum at line 6-11:

```swift
private enum ProfileSheet: Identifiable {
    case camera, gallery, editProfile
    case merchantStats, merchantSubscription, editBusiness

    var id: Int { hashValue }
}
```

Replace it with:

```swift
private enum ProfileSheet: Identifiable {
    case camera, gallery, editProfile
    case merchantStats, merchantSubscription, editBusiness
    case deleteAccount

    var id: Int { hashValue }
}
```

- [ ] **Step 2: Add the "Eliminar cuenta" section to the profile list**

In the same file, locate the logout section (around line 169-179):

```swift
                Section {
                    Button(action: { appState.logout() }) {
                        HStack {
                            Spacer()
                            Text("Cerrar Sesión")
                                .font(.appButton)
                                .foregroundColor(.red)
                            Spacer()
                        }
                    }
                }
```

Insert a new section AFTER it:

```swift
                Section {
                    Button(action: { appState.logout() }) {
                        HStack {
                            Spacer()
                            Text("Cerrar Sesión")
                                .font(.appButton)
                                .foregroundColor(.red)
                            Spacer()
                        }
                    }
                }

                // Sólo clientes pueden eliminar su cuenta desde la app en v1.
                // Comercios deben contactar soporte (no hay botón oculto).
                if appState.currentUser?.role == .cliente {
                    Section {
                        Button(action: { activeSheet = .deleteAccount }) {
                            HStack {
                                Spacer()
                                Text("Eliminar cuenta")
                                    .font(.appButton)
                                    .foregroundColor(.red.opacity(0.7))
                                Spacer()
                            }
                        }
                    }
                }
```

- [ ] **Step 3: Add the `deleteAccount` case to the sheet switch**

In the same file, locate the sheet presentation (around line 186-201):

```swift
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .camera:
                    CameraImagePicker(image: $profileImage)
                case .gallery:
                    GalleryImagePicker(image: $profileImage)
                case .editProfile:
                    editProfileSheet
                case .merchantStats:
                    StatsView()
                case .merchantSubscription:
                    SubscriptionView()
                case .editBusiness:
                    MerchantProfileSetupView(existingProfile: appState.merchantProfile)
                }
            }
```

Add the new case:

```swift
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .camera:
                    CameraImagePicker(image: $profileImage)
                case .gallery:
                    GalleryImagePicker(image: $profileImage)
                case .editProfile:
                    editProfileSheet
                case .merchantStats:
                    StatsView()
                case .merchantSubscription:
                    SubscriptionView()
                case .editBusiness:
                    MerchantProfileSetupView(existingProfile: appState.merchantProfile)
                case .deleteAccount:
                    DeleteAccountConfirmationSheet()
                        .environmentObject(appState)
                }
            }
```

- [ ] **Step 4: Add the `DeleteAccountConfirmationSheet` struct**

At the very end of `ProfileView.swift`, AFTER the `GalleryImagePicker` struct and BEFORE the `#Preview` block, add:

```swift
// MARK: - Delete account confirmation sheet

private struct DeleteAccountConfirmationSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appState: AppState
    @State private var typedConfirmation = ""
    @State private var isDeleting = false
    @State private var errorMessage: String?

    private var isValid: Bool { typedConfirmation == "ELIMINAR" }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("¿Eliminar tu cuenta?")
                        .font(.appHeadline)
                        .foregroundColor(.appTextPrimary)

                    Text("Esta acción programará la eliminación definitiva de tu cuenta en 30 días. Durante ese tiempo podrás recuperarla iniciando sesión. Pasado el plazo, se borrarán para siempre:")
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("• Tu perfil y foto")
                        Text("• Tus favoritos")
                        Text("• Tu historial")
                    }
                    .font(.appBody)
                    .foregroundColor(.appTextPrimary)

                    Divider()

                    Text("Para confirmar, escribe ELIMINAR:")
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)

                    TextField("ELIMINAR", text: $typedConfirmation)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .disabled(isDeleting)

                    Spacer(minLength: 32)

                    Button(action: performDelete) {
                        HStack {
                            Spacer()
                            if isDeleting {
                                ProgressView().tint(.white)
                            } else {
                                Text("Eliminar")
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(AppDesign.ButtonStyle(isPrimary: true, isDisabled: !isValid || isDeleting))
                    .disabled(!isValid || isDeleting)
                }
                .padding(24)
            }
            .navigationTitle("Eliminar cuenta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                        .disabled(isDeleting)
                }
            }
            .alert("Error", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK") { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func performDelete() {
        isDeleting = true
        Task {
            do {
                try await FirebaseService.shared.requestAccountDeletion()
                // Recargar el usuario para que ContentView detecte deletedAt
                // y enrute a AccountDeletionRecoveryView. También cerramos el sheet.
                if let updated = try? await FirebaseService.shared.getCurrentUser() {
                    await MainActor.run {
                        appState.currentUser = updated
                        isDeleting = false
                        dismiss()
                    }
                }
            } catch {
                await MainActor.run {
                    isDeleting = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add Zampa-iOS/Zampa/Features/Profile/ProfileView.swift && git commit -m "$(cat <<'EOF'
iOS: add Eliminar cuenta button and typed-confirmation sheet

Visible only for cliente role. Opens a sheet with a TextField that
requires the user to type ELIMINAR before the destructive button is
enabled. On confirm, calls requestAccountDeletion and refreshes the
user so the router transitions to AccountDeletionRecoveryView.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: iOS — Build and manual end-to-end verification

**Files:** (no changes — verification only)

- [ ] **Step 1: Open Xcode and build**

Run:
```bash
open "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-iOS/Zampa.xcodeproj"
```

In Xcode: select a simulator (iPhone 15 or similar) and press **Cmd+B** to build.

Expected: `Build Succeeded`. If the build fails with "Cannot find 'AccountDeletionRecoveryView' in scope", revisit Task 6 Step 2 (the file was not added to the target).

- [ ] **Step 2: Run the app on a simulator and verify the button**

Press **Cmd+R** in Xcode. Once the app launches:

1. Log in with an existing test cliente account.
2. Navigate to the Perfil tab.
3. Scroll to the bottom. Verify:
   - "Cerrar Sesión" button is there (red).
   - Below it, a new "Eliminar cuenta" button (lighter red).
4. Verify the button is NOT visible when logged in as a comercio (log out, register a merchant test account or switch to one, repeat).

- [ ] **Step 3: Verify the confirmation flow**

Back as a cliente:

1. Tap "Eliminar cuenta".
2. The confirmation sheet opens. Read the warning text.
3. Verify "Eliminar" button is disabled (greyed out).
4. Type `elimin` in the text field — button still disabled.
5. Clear and type `ELIMINAR` — button enables.
6. Tap "Eliminar".

- [ ] **Step 4: Verify Firestore state after confirming**

Open the Firebase Console at `https://console.firebase.google.com/project/eatout-70b8b/firestore/data` and:

1. Find the test user's doc in `users/{uid}`. Verify `deletedAt` and `scheduledPurgeAt` are set (both timestamps, 30 days apart).
2. Check `deviceTokens` collection filtered by `userId == {uid}`. Should be empty.
3. Confirm the iOS app is now on the `AccountDeletionRecoveryView` (orange warning icon + "Cuenta pendiente de eliminación").

- [ ] **Step 5: Verify recovery**

1. In the iOS app, tap "Recuperar cuenta".
2. Verify the green "Cuenta recuperada" toast appears at the top for ~1.5s.
3. Verify the app transitions to MainTabView.
4. Back in Firebase Console, verify `users/{uid}` no longer has `deletedAt` or `scheduledPurgeAt` fields.

- [ ] **Step 6: Verify "Cerrar sesión" from recovery screen**

1. Repeat steps 1-3 of Step 3 to get back into the recovery screen.
2. Tap "Cerrar sesión" (gray button).
3. Verify the app goes to `AuthView`.
4. Log back in with the same credentials.
5. Verify the app goes back to `AccountDeletionRecoveryView` (session resumed, but user is still pending deletion).
6. Recover via "Recuperar cuenta" to leave the test user in a clean state.

- [ ] **Step 7: No commit (verification only)**

This task is verification-only. If any step fails, return to the relevant earlier task and fix. Do not commit.

---

## Task 10: Android — User model + Firestore parsing

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt:4-22`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt:127-138`

- [ ] **Step 1: Add fields to `User` data class**

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

- [ ] **Step 2: Update `getUserProfile(uid)` to parse the new fields**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`, locate `getUserProfile` (around line 127). Replace the function:

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
        )
    }
```

- [ ] **Step 3: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/model/Models.kt Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt && git commit -m "$(cat <<'EOF'
Android: add deletedAt / scheduledPurgeAt to User model

Parse both Timestamp fields in getUserProfile so the navigation layer
can detect pending-deletion state after login.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Android — FirebaseService deletion methods

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`

- [ ] **Step 1: Add `requestAccountDeletion()` and `cancelAccountDeletion()`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt`, locate `updateUserName` (around line 413). Add the following methods immediately AFTER it:

```kotlin
    // ── Account deletion (soft delete + 30-day grace period) ──

    /**
     * Marca la cuenta actual como pendiente de eliminación:
     * setea deletedAt y scheduledPurgeAt en users/{uid} y borra los
     * deviceTokens del usuario para cortar notificaciones push.
     * NO borra el Auth user ni datos del usuario — eso lo hace la Cloud
     * Function purgeDeletedAccounts a los 30 días.
     */
    suspend fun requestAccountDeletion() {
        val uid = currentUid ?: throw Exception("No autenticado")

        // 1. Borrar device tokens del usuario (query + batch delete)
        val tokensSnap = db.collection("deviceTokens")
            .whereEqualTo("userId", uid)
            .get().await()
        if (!tokensSnap.isEmpty) {
            val batch = db.batch()
            tokensSnap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        // 2. Marcar users/{uid} con deletedAt y scheduledPurgeAt
        val nowMs = System.currentTimeMillis()
        val purgeMs = nowMs + 30L * 24 * 60 * 60 * 1000
        db.collection("users").document(uid).update(
            mapOf(
                "deletedAt" to com.google.firebase.Timestamp(java.util.Date(nowMs)),
                "scheduledPurgeAt" to com.google.firebase.Timestamp(java.util.Date(purgeMs)),
            )
        ).await()
    }

    /**
     * Cancela una eliminación pendiente limpiando deletedAt y scheduledPurgeAt.
     */
    suspend fun cancelAccountDeletion() {
        val uid = currentUid ?: throw Exception("No autenticado")
        db.collection("users").document(uid).update(
            mapOf(
                "deletedAt" to com.google.firebase.firestore.FieldValue.delete(),
                "scheduledPurgeAt" to com.google.firebase.firestore.FieldValue.delete(),
            )
        ).await()
    }
```

- [ ] **Step 2: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/data/FirebaseService.kt && git commit -m "$(cat <<'EOF'
Android: add requestAccountDeletion and cancelAccountDeletion

Mirrors the iOS FirebaseService API. requestAccountDeletion deletes
device tokens and sets the timestamps; cancelAccountDeletion clears
both fields using FieldValue.delete.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Android — AuthViewModel pending-deletion state

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt`

- [ ] **Step 1: Add a `pendingDeletionUser` state flow**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt`, locate the state declarations near the top of the class (after `_pendingSocialUser`, around line 42). Insert a new state flow:

```kotlin
    // Cuenta pendiente de eliminación detectada tras login. Mientras sea no-nulo,
    // la navegación muestra AccountDeletionRecoveryScreen en lugar de Main.
    private val _pendingDeletionUser = MutableStateFlow<User?>(null)
    val pendingDeletionUser: StateFlow<User?> = _pendingDeletionUser
```

- [ ] **Step 2: Add a helper that evaluates the login result**

Add this private helper method at the end of the class, just before the closing `}` (after `updateProfilePhoto`):

```kotlin
    /**
     * Ruta el resultado de login: si el usuario tiene deletedAt activo, no
     * marca la sesión como autenticada (para que NavHost no vaya a Main) y
     * expone el usuario via pendingDeletionUser para que el host muestre la
     * pantalla de recuperación.
     */
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

- [ ] **Step 3: Replace direct authentication assignments with `routePostLogin`**

In `AuthViewModel.kt`, replace the post-login state updates in four places:

**Place A — `init { ... }` block (around line 48-55):**

Replace:
```kotlin
    init {
        if (firebaseService.isAuthenticated) {
            viewModelScope.launch {
                val uid = firebaseService.currentUid ?: return@launch
                _currentUser.value = firebaseService.getUserProfile(uid)
            }
            refreshDeviceToken()
        }
    }
```

With:
```kotlin
    init {
        if (firebaseService.isAuthenticated) {
            viewModelScope.launch {
                val uid = firebaseService.currentUid ?: return@launch
                val user = firebaseService.getUserProfile(uid)
                if (user != null) {
                    routePostLogin(user)
                }
            }
        }
    }
```

**Place B — `login(...)` (around line 58-72):**

Replace the success branch inside the try block:
```kotlin
                val user = firebaseService.login(email, password)
                _currentUser.value = user
                _isAuthenticated.value = true
                refreshDeviceToken()
```

With:
```kotlin
                val user = firebaseService.login(email, password)
                routePostLogin(user)
```

**Place C — `register(...)` (around line 74-88):**

Replace:
```kotlin
                val user = firebaseService.register(email, password, name, role, phone)
                _currentUser.value = user
                _isAuthenticated.value = true
                refreshDeviceToken()
```

With:
```kotlin
                val user = firebaseService.register(email, password, name, role, phone)
                // Un registro recién creado nunca tiene deletedAt, pero pasamos por
                // routePostLogin para mantener un único punto de entrada.
                routePostLogin(user)
```

**Place D — `handleGoogleSignInResult(...)` (around line 110-136):**

Replace:
```kotlin
                if (isNewUser) {
                    _pendingSocialUser.value = user   // show role selection
                } else {
                    _currentUser.value = user
                    _isAuthenticated.value = true
                    refreshDeviceToken()
                }
```

With:
```kotlin
                if (isNewUser) {
                    _pendingSocialUser.value = user   // show role selection
                } else {
                    routePostLogin(user)
                }
```

**Place E — `finalizeSocialRegistration(...)` (around line 138-153):**

Replace:
```kotlin
                val user = firebaseService.finalizeSocialRegistration(pending.id, role, name, pending.email)
                _pendingSocialUser.value = null
                _currentUser.value = user
                _isAuthenticated.value = true
                refreshDeviceToken()
```

With:
```kotlin
                val user = firebaseService.finalizeSocialRegistration(pending.id, role, name, pending.email)
                _pendingSocialUser.value = null
                routePostLogin(user)
```

- [ ] **Step 4: Update `logout()` to clear the pending-deletion state**

Replace the existing `logout()`:

```kotlin
    fun logout() {
        firebaseService.logout()
        _isAuthenticated.value = false
        _currentUser.value = null
    }
```

With:

```kotlin
    fun logout() {
        firebaseService.logout()
        _isAuthenticated.value = false
        _currentUser.value = null
        _pendingDeletionUser.value = null
    }
```

- [ ] **Step 5: Add methods `requestAccountDeletion()` and `cancelAccountDeletion()`**

Add these methods to `AuthViewModel.kt`, at the bottom of the class (just before the closing `}`):

```kotlin
    // ── Account deletion ──

    /**
     * Solicita eliminación de cuenta. Tras el éxito, reemplaza el usuario
     * cargado por uno nuevo con deletedAt seteado, lo cual hace que
     * NavHost salte a AccountDeletionRecoveryScreen.
     */
    fun requestAccountDeletion(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firebaseService.requestAccountDeletion()
                val uid = firebaseService.currentUid ?: return@launch
                val refreshed = firebaseService.getUserProfile(uid) ?: return@launch
                routePostLogin(refreshed)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al eliminar cuenta")
            }
        }
    }

    /** Cancela una eliminación pendiente y reactiva la cuenta. */
    fun cancelAccountDeletion(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                firebaseService.cancelAccountDeletion()
                val uid = firebaseService.currentUid ?: return@launch
                val refreshed = firebaseService.getUserProfile(uid) ?: return@launch
                routePostLogin(refreshed)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error al recuperar cuenta")
            }
        }
    }
```

- [ ] **Step 6: Commit**

```bash
git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AuthViewModel.kt && git commit -m "$(cat <<'EOF'
Android: route post-login via routePostLogin; add pendingDeletionUser

All auth success paths now funnel through routePostLogin, which either
marks the user as authenticated (active account) or stashes them in
pendingDeletionUser (account in grace period). Adds requestAccountDeletion
and cancelAccountDeletion VM methods that refresh the user doc and
re-route.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Android — AccountDeletionRecoveryScreen composable

**Files:**
- Create: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AccountDeletionRecoveryScreen.kt`

- [ ] **Step 1: Create the recovery screen**

Create a new file at `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AccountDeletionRecoveryScreen.kt`:

```kotlin
package com.sozolab.zampa.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sozolab.zampa.data.model.User
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pantalla que se muestra cuando un usuario inicia sesión y tiene la cuenta
 * marcada como pendiente de eliminación. Le permite recuperar la cuenta o
 * cerrar sesión sin deshacer el borrado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDeletionRecoveryScreen(
    user: User,
    onRecover: (onError: (String) -> Unit) -> Unit,
    onLogout: () -> Unit,
) {
    var isRecovering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val purgeDateText = remember(user.scheduledPurgeAt) {
        val date = user.scheduledPurgeAt?.toDate()
        if (date != null) {
            SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
                .format(date)
        } else {
            "pronto"
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(72.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Cuenta pendiente\nde eliminación",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Tu cuenta se eliminará el",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = purgeDateText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Si quieres conservarla, pulsa Recuperar cuenta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (isRecovering) return@Button
                    isRecovering = true
                    onRecover { err ->
                        isRecovering = false
                        errorMessage = err
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRecovering
            ) {
                if (isRecovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Recuperar cuenta", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onLogout,
                enabled = !isRecovering
            ) {
                Text("Cerrar sesión")
            }

            Spacer(Modifier.height(32.dp))
        }

        // Toast/snackbar de error
        LaunchedEffect(errorMessage) {
            val msg = errorMessage
            if (msg != null) {
                snackbarHostState.showSnackbar(msg)
                errorMessage = null
            }
        }
    }
}
```

**Note:** this composable does NOT show the "Cuenta recuperada" snackbar explicitly — because as soon as `cancelAccountDeletion` succeeds, `AuthViewModel.pendingDeletionUser` becomes `null`, `isAuthenticated` becomes `true`, and `NavHost` automatically swaps to `MainScreen`. The visual transition IS the feedback. If you want an explicit snackbar, it must live in `MainScreen` keyed on a "just recovered" flag — out of scope for v1.

- [ ] **Step 2: Verify the file compiles in isolation**

Run:
```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. If you see unresolved reference errors for `User` or Compose symbols, double-check imports in the new file.

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/auth/AccountDeletionRecoveryScreen.kt && git commit -m "$(cat <<'EOF'
Android: add AccountDeletionRecoveryScreen composable

Shows purge date and two actions: Recuperar cuenta (calls VM) or
Cerrar sesión. Uses Material3 theme and locale-formatted Spanish date.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Android — Wire recovery screen into Navigation

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt`

- [ ] **Step 1: Add a route object and observe `pendingDeletionUser`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt`, locate the `Route` sealed class and add a new route:

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
    data object History : Route("history")
    data object PrivacyPolicy : Route("privacy_policy")
    data object Terms : Route("terms")
    data object MenuDetail : Route("menu_detail/{menuId}") {
        fun createRoute(menuId: String) = "menu_detail/$menuId"
    }
}
```

- [ ] **Step 2: Observe the new state flow and react with a LaunchedEffect**

In the same file, locate `ZampaNavHost`. Just AFTER the `isAuthenticated` declaration (around line 42), add the pendingDeletionUser observation and a `LaunchedEffect` that handles transitions:

Replace:
```kotlin
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()

    val startDestination = if (isAuthenticated) Route.Main.route else Route.Auth.route
```

With:
```kotlin
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val pendingDeletionUser by authViewModel.pendingDeletionUser.collectAsState()

    val startDestination = when {
        pendingDeletionUser != null -> Route.AccountDeletionRecovery.route
        isAuthenticated -> Route.Main.route
        else -> Route.Auth.route
    }

    // Reaccionar a cambios runtime:
    //  - Aparece pendingDeletionUser → ir a pantalla de recuperación
    //  - Se recupera la cuenta (pendingDeletion pasa a null, isAuthenticated true) → Main
    LaunchedEffect(pendingDeletionUser, isAuthenticated) {
        val current = navController.currentDestination?.route
        when {
            pendingDeletionUser != null && current != Route.AccountDeletionRecovery.route -> {
                navController.navigate(Route.AccountDeletionRecovery.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            pendingDeletionUser == null && isAuthenticated && current == Route.AccountDeletionRecovery.route -> {
                navController.navigate(Route.Main.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
```

- [ ] **Step 3: Add the `composable` entry for the recovery screen**

In the same file, inside the `NavHost { ... }` block, after the `composable(Route.Auth.route) { ... }` block (around line 67), add:

```kotlin
        composable(Route.AccountDeletionRecovery.route) {
            val pending = pendingDeletionUser
            if (pending != null) {
                AccountDeletionRecoveryScreen(
                    user = pending,
                    onRecover = { onError ->
                        authViewModel.cancelAccountDeletion(onError)
                    },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Route.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
```

- [ ] **Step 4: Verify the NavHost compiles**

Run:
```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. Common errors: missing import for `AccountDeletionRecoveryScreen` — add `import com.sozolab.zampa.ui.auth.AccountDeletionRecoveryScreen` at the top of `Navigation.kt`.

- [ ] **Step 5: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/navigation/Navigation.kt && git commit -m "$(cat <<'EOF'
Android: route to AccountDeletionRecoveryScreen on pending deletion

NavHost observes AuthViewModel.pendingDeletionUser. When non-null, the
start destination (and any runtime transition) lands on the recovery
screen. When it flips back to null with isAuthenticated=true, navigate
to Main.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Android — ProfileScreen delete button + confirmation dialog

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt`

- [ ] **Step 1: Add new parameters to `ProfileScreen`**

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt`, locate the `ProfileScreen` function signature (around line 32-47). Add two new parameters:

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
    onNavigateToHistory: () -> Unit = {},
    onRequestAccountDeletion: ((onError: (String) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

- [ ] **Step 2: Add state for the deletion dialog**

Near the other `remember { mutableStateOf(...) }` declarations at the top of the `ProfileScreen` body (around line 49-52), add:

```kotlin
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteTypedConfirmation by remember { mutableStateOf("") }
    var deleteIsSubmitting by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
```

- [ ] **Step 3: Add the dialog composable**

In the same file, find the block of `if (showEditNameDialog) { ... }` around line 133-161. AFTER that block, add the new dialog:

```kotlin
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!deleteIsSubmitting) {
                    showDeleteAccountDialog = false
                    deleteTypedConfirmation = ""
                    deleteErrorMessage = null
                }
            },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Column {
                    Text(
                        "Esta acción programará la eliminación definitiva de tu cuenta en 30 días. Durante ese tiempo podrás recuperarla iniciando sesión. Pasado el plazo, se borrarán para siempre:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("• Tu perfil y foto", style = MaterialTheme.typography.bodyMedium)
                    Text("• Tus favoritos", style = MaterialTheme.typography.bodyMedium)
                    Text("• Tu historial", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Para confirmar, escribe ELIMINAR:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteTypedConfirmation,
                        onValueChange = { deleteTypedConfirmation = it },
                        placeholder = { Text("ELIMINAR") },
                        singleLine = true,
                        enabled = !deleteIsSubmitting,
                        isError = deleteErrorMessage != null
                    )
                    val err = deleteErrorMessage
                    if (err != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteIsSubmitting) return@TextButton
                        val handler = onRequestAccountDeletion ?: return@TextButton
                        deleteIsSubmitting = true
                        deleteErrorMessage = null
                        handler { err ->
                            deleteIsSubmitting = false
                            deleteErrorMessage = err
                        }
                        // En éxito NO necesitamos cerrar el diálogo manualmente:
                        // el NavHost observa pendingDeletionUser y navega fuera de
                        // ProfileScreen, desmontando este dialog.
                    },
                    enabled = deleteTypedConfirmation == "ELIMINAR" && !deleteIsSubmitting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (deleteIsSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Eliminar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        deleteTypedConfirmation = ""
                        deleteErrorMessage = null
                    },
                    enabled = !deleteIsSubmitting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
```

- [ ] **Step 4: Add the "Eliminar cuenta" button below Logout**

Locate the logout button in the `LazyColumn` (around line 350-366):

```kotlin
        // Logout
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }
            Spacer(Modifier.height(32.dp))
        }
```

Replace it with:

```kotlin
        // Logout
        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar Sesión")
            }
        }

        // Delete account (solo clientes)
        if (!isMerchant && onRequestAccountDeletion != null) {
            item {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                    )
                ) {
                    Text("Eliminar cuenta")
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
```

- [ ] **Step 5: Pass `onRequestAccountDeletion` from `MainScreen`**

`MainScreen.kt` already instantiates `authViewModel` via `hiltViewModel()` at the top of the composable (line 35), so no plumbing through `Navigation.kt` is needed — the call site can reference `authViewModel` directly.

In `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt`, locate the `ProfileScreen` call (lines 140-156):

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
                    onNavigateToHistory = onNavigateToHistory,
                    modifier = Modifier.padding(paddingValues)
                )
            }
```

Replace it with:

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
                    onNavigateToHistory = onNavigateToHistory,
                    onRequestAccountDeletion = { onError -> authViewModel.requestAccountDeletion(onError) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
```

No changes to `Navigation.kt` are required for this step.

- [ ] **Step 6: Verify compilation**

Run:
```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. Common errors:
- `Unresolved reference: authViewModel` in Navigation.kt → authViewModel is already declared at the top of `ZampaNavHost`, just use it directly.
- `Too many arguments for public fun ProfileScreen(...)` → you forgot to update `ProfileScreen`'s signature in step 1.

- [ ] **Step 7: Commit**

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && git add Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/profile/ProfileScreen.kt Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt && git commit -m "$(cat <<'EOF'
Android: add Eliminar cuenta button and typed-confirmation dialog

ProfileScreen gains an onRequestAccountDeletion callback, visible only
for cliente role. Typed confirmation must equal ELIMINAR before the
destructive action is enabled. MainScreen wires it to
AuthViewModel.requestAccountDeletion, which re-routes the NavHost to
AccountDeletionRecoveryScreen on success.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Android — Build and manual end-to-end verification

**Files:** (no changes — verification only)

- [ ] **Step 1: Build the debug APK**

Run:
```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android" && ./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If the build fails, read the error log to identify which file needs fixing and return to the relevant task.

- [ ] **Step 2: Install on emulator/device and launch**

Run:
```bash
./gradlew installDebug
```

Then open the app from the launcher or via:
```bash
adb shell am start -n com.sozolab.zampa/.MainActivity
```

- [ ] **Step 3: Verify the button and confirmation dialog**

1. Log in with a test cliente account.
2. Go to the Profile tab.
3. Scroll to the bottom and verify "Eliminar cuenta" is visible below "Cerrar Sesión".
4. Log out and log in as a merchant — verify "Eliminar cuenta" is NOT visible.
5. Back as cliente, tap "Eliminar cuenta".
6. Verify the `AlertDialog` opens with the warning text and the confirmation text field.
7. Type `elimin` — verify the "Eliminar" button is disabled.
8. Clear and type `ELIMINAR` — verify it enables.
9. Tap "Eliminar".

- [ ] **Step 4: Verify Firestore state after confirming**

Open `https://console.firebase.google.com/project/eatout-70b8b/firestore/data`:

1. Find `users/{uid}` for the test user. Verify `deletedAt` and `scheduledPurgeAt` are set.
2. Query `deviceTokens` where `userId == {uid}` — should be empty.
3. Back in the Android app, verify it auto-navigated to the recovery screen.

- [ ] **Step 5: Verify recovery**

1. Tap "Recuperar cuenta".
2. Verify the NavHost transitions to MainScreen.
3. In Firebase Console, verify the user doc no longer has `deletedAt` / `scheduledPurgeAt`.

- [ ] **Step 6: Verify "Cerrar sesión" on recovery screen**

1. Trigger deletion again.
2. On the recovery screen, tap "Cerrar sesión".
3. Verify the app returns to the AuthScreen.
4. Log back in — verify the app returns to the recovery screen (because `deletedAt` is still set).
5. Recover the account to leave it clean for future tests.

- [ ] **Step 7: No commit (verification only)**

---

## Task 17: Optional — admin CLI helper `admin-delete-user.js`

**Files:**
- Create: `functions/admin-delete-user.js`

> **Skip this task if you don't need a support/debug helper.** This script is purely for forcing a purge of a specific uid without waiting for the 30-day grace period, useful for testing and support requests.

- [ ] **Step 1: Create the script**

Create `functions/admin-delete-user.js`:

```javascript
#!/usr/bin/env node
/**
 * Forzar la purga inmediata de una cuenta.
 *
 * Uso:
 *   node admin-delete-user.js <uid>
 *
 * Setea scheduledPurgeAt al momento actual, de modo que la próxima
 * ejecución de la Cloud Function purgeDeletedAccounts (o una llamada
 * manual con `firebase functions:shell`) la procese. NO borra nada
 * directamente — deja que la función haga el trabajo para que el
 * proceso sea idéntico al flujo normal y quede registrado en
 * purgedAccounts/{uid}.
 *
 * Requiere:
 *   - functions/.env con GOOGLE_APPLICATION_CREDENTIALS apuntando a
 *     una service account con permisos Firestore + Auth admin
 *   - O haber ejecutado `gcloud auth application-default login`
 */

const admin = require("firebase-admin");

async function main() {
    const uid = process.argv[2];
    if (!uid) {
        console.error("Usage: node admin-delete-user.js <uid>");
        process.exit(1);
    }

    admin.initializeApp();
    const db = admin.firestore();

    const userRef = db.collection("users").doc(uid);
    const snap = await userRef.get();
    if (!snap.exists) {
        console.error(`users/${uid} no existe.`);
        process.exit(2);
    }

    const now = admin.firestore.Timestamp.now();
    await userRef.update({
        deletedAt: now,
        scheduledPurgeAt: now,
    });

    console.log(`OK — users/${uid} marcado con scheduledPurgeAt=now.`);
    console.log("La próxima ejecución de purgeDeletedAccounts lo procesará.");
    console.log("Para disparar manualmente: firebase functions:shell → purgeDeletedAccounts()");
}

main().catch(e => {
    console.error("Error:", e);
    process.exit(3);
});
```

- [ ] **Step 2: Commit**

```bash
git add functions/admin-delete-user.js && git commit -m "$(cat <<'EOF'
Add admin-delete-user.js CLI helper

Forces scheduledPurgeAt=now on a given uid so the next scheduled run of
purgeDeletedAccounts processes it. Useful for testing and support.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 18: End-to-end purge verification

**Files:** (no changes — verification only)

- [ ] **Step 1: Mark a throwaway user for immediate purge**

Create a dedicated throwaway test user (NOT a user you want to keep) via the app — register a new cliente account named e.g. `purge-test@example.com`. Note its uid from Firebase Console → Authentication.

Then force its `scheduledPurgeAt` to now using the admin helper (or manually in Firestore Console):

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/functions" && node admin-delete-user.js <UID>
```

Expected: `OK — users/<uid> marcado con scheduledPurgeAt=now.`

- [ ] **Step 2: Trigger the Cloud Function manually**

Option A — via `firebase functions:shell`:
```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa" && firebase functions:shell --project eatout-70b8b
```

In the shell prompt:
```
> purgeDeletedAccounts()
```

Expected: the shell prints logs for each step (favorites, userHistory, notifications, deviceTokens, customers, storage, users, auth) and finally "purgado completamente".

Option B — wait for the next scheduled run (daily at 03:00 Europe/Madrid). Use this only if you can't run the shell.

- [ ] **Step 3: Verify all data is gone**

In Firebase Console:
1. `users/<uid>` — does not exist.
2. `customers/<uid>` — does not exist.
3. Authentication → search for the user — does not exist.
4. Storage → `users/<uid>/` — empty or missing.
5. `purgedAccounts/<uid>` — exists, with `stepsCompleted` containing all expected steps and `errors: []`.

- [ ] **Step 4: Spot-check a partial failure path**

Optional: repeat Task 17 on a non-existent uid to verify the function gracefully handles missing users without crashing. Mark a fake `users/non-existent-uid` with scheduledPurgeAt=now, trigger the function, and verify the function logs "auth_already_missing" or similar without throwing. Check `purgedAccounts/non-existent-uid` has an audit entry.

- [ ] **Step 5: No commit**

This is verification only. If any step fails, inspect Cloud Function logs at `https://console.firebase.google.com/project/eatout-70b8b/functions/logs` to diagnose.

---

## Final verification

- [ ] **Task 19: Review the whole feature against the spec**

Open `docs/superpowers/specs/2026-04-11-account-deletion-design.md` and walk through each section:

1. Section 2 (Data model) — verify `users/{uid}` accepts both fields; verify the purge function selects by `scheduledPurgeAt <= now`.
2. Section 3 (Client flow) — verify the button placement, the typed confirmation UX, and the recovery screen on both platforms.
3. Section 4 (Firestore rules) — verify a pending-deletion user cannot write `favorites` or `userHistory` via the app (this works because the user is either signed out or stuck on the recovery screen — rules are not hardened against API bypass, as documented).
4. Section 5 (Cloud Function) — verify the audit log contains the expected steps and that `admin/user-not-found` is treated as success.
5. Section 7 (Implementation order) — verify each numbered item has been completed.

If any gap is found, file it as a follow-up or go back to the relevant task.
