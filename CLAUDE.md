# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zampa (formerly EatOut) is a two-sided marketplace mobile app where restaurants/businesses ("comercios") publish daily meal offers ("menús"), and customers ("clientes") browse a feed and favorite businesses. There is no REST backend — both platforms talk directly to Firebase (Auth, Firestore, Storage, Messaging).

The project spans apps, backend, and a marketing site:
- `Zampa-iOS/` — SwiftUI app
- `Zampa-Android/` — Jetpack Compose app
- `functions/` — Cloud Functions (notification triggers, menu expiration) and seed scripts
- `firebase/` — Firestore and Storage security rules
- **Landing page** — lives OUTSIDE this repo at `/Users/onejensen/Documents/MIS WEBS/Zampa/` (repo: `github.com/onejensen/ZAMPA`, domain: `www.getzampa.com`). Static HTML/CSS/JS, 9 languages, hosted on GitHub Pages. Uses Playfair Display + Inter fonts and the same brand tokens as the apps. Store download buttons currently covered by a "Coming Soon" construction-tape sign pending app launch.

## Building & Running

### iOS (`Zampa-iOS/`)

**Open in Xcode (required — do not use `xcodebuild` for first build):**
```bash
open "Zampa-iOS/Zampa.xcodeproj"
```

Firebase SPM packages must be resolved via Xcode GUI:
- `File -> Packages -> Reset Package Caches`, then `File -> Packages -> Resolve Package Versions`
- First build takes 5-10 minutes while Firebase compiles

**Clean DerivedData if packages break:**
```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Zampa-*
```

**Scheme:** `Zampa.xcscheme` (was `EatOut.xcscheme` before the rename). Build settings in `project.pbxproj` reference `Zampa/Info.plist` and `Zampa/Zampa.entitlements` — if you see "input file cannot be found" errors, those paths were likely reverted during a refactor.

### Android (`Zampa-Android/`)

Open in Android Studio or build via Gradle:
```bash
cd Zampa-Android
./gradlew assembleDebug
./gradlew installDebug
```

Run tests:
```bash
./gradlew test                    # unit tests
./gradlew connectedAndroidTest    # instrumented tests
```

## Architecture

### Shared Domain Concepts

- **User roles**: `CLIENTE` (consumer) and `COMERCIO` (merchant). Role is stored as a string in Firestore (`users/{uid}.role`).
- **Daily offers**: called "menus" in code — stored in the `dailyOffers` Firestore collection.
- **Merchant plan tiers**: `"free"` or `"pro"` — controls `isMerchantPro` flag on offers and `isPremium` in iOS `AppState`. Pro merchants trigger push notifications to followers when publishing offers.
- **Notification preferences**: stored in `users/{uid}.notificationPreferences` with keys `newMenuFromFavorites`, `promotions`, `general` (all default `true`).
- **Dietary preferences**: `DietaryPreferences` model with fields `isVegetarian`, `isVegan`, `isMeatFree`, `isFishFree`, `isGlutenFree`, `isLactoseFree`, `isNutFree`. "Sin X" fields exclude offers containing X from the feed. Stored locally (UserDefaults on iOS, SharedPreferences on Android).
- **User history**: actions `call`, `directions` tracked per-user in the `userHistory` collection. Favorites come from the `favorites` collection.
- **Language preference**: stored in `users/{uid}.languagePreference` (default `"auto"`). Supported: `es`, `ca`, `eu`, `gl`, `en`, `de`, `fr`, `it`. `"auto"` follows the system language with fallback to `es`. Cached locally (UserDefaults on iOS, SharedPreferences on Android).

### Firestore Collections

| Collection | Purpose |
|---|---|
| `users` | Auth profile + role + notificationPreferences + languagePreference |
| `businesses` | Merchant profiles (doc ID = Firebase Auth UID) |
| `customers` | Customer profiles |
| `dailyOffers` | Daily meal offers (public read) |
| `favorites` | Customer-to-business relationships (includes `notificationsEnabled` per favorite) |
| `metrics/{merchantId}/daily/{date}` | Impressions and click tracking |
| `deviceTokens` | FCM push tokens |
| `notifications` | In-app notifications |
| `subscriptions` | Merchant subscription records |
| `cuisineTypes` | Catalog of cuisine categories |
| `reports` | User-submitted offer reports |
| `userHistory` | Per-user action log (calls, directions). Doc ID = `{userId}_{businessId}_{action}` |

### iOS Architecture

- **`AppState`** (`Core/AppState.swift`) — global `ObservableObject` injected as `@EnvironmentObject`. Holds auth state, current user, merchant profile, and premium status.
- **`ContentView`** — root router: shows loading spinner -> `AuthView` (unauthenticated) -> `MerchantProfileSetupView` (merchant without complete profile) -> `MainTabView` (authenticated).
- **`FirebaseService`** (`Services/FirebaseService.swift`) — singleton, all Firestore/Storage operations. Handles auth, offers, favorites, metrics, notifications, and subscriptions.
- **`APIClient`** (`Core/Networking/APIClient.swift`) — HTTP client with auto token refresh for a potential REST API. DEBUG points to `localhost:3000`, production to `https://api.eatout.com`. Firebase is the primary data source.
- **`KeychainManager`** — stores legacy REST API access/refresh tokens.
- **Services** (`Services/`) — `FavoriteService`, `MenuService`, `PushManager` wrap `FirebaseService` for specific domains. `PushManager` handles FCM token registration and re-registers the token on login (via `refreshTokenIfNeeded`).
- Feature views live in `Features/{Auth,Feed,Favorites,Merchant,Profile,Subscription}/`. Profile includes `HistoryView`, `DietaryPreferencesView`, `NotificationPreferencesView`, `CurrencyPreferenceView`, `LanguagePickerView`.
- Models in `Core/Models/`: `User`, `Merchant`, `Menu`, `Favorite`, `Subscription`, `CuisineType`, `AppNotification`, `NotificationPreferences`.
- **`LocalizationManager`** (`Core/LocalizationManager.swift`) — singleton `ObservableObject`. Loads JSON translation files from `Localization/{lang}.json` in the app bundle. Provides `t(_ key:)` for string lookup. Views use `@ObservedObject var localization = LocalizationManager.shared` and call `localization.t("key")`. Persists language choice to UserDefaults + Firebase.
- Design tokens centralized in `Core/DesignSystem.swift`. **Font: Sora** (Google Fonts, 4 weights: Regular/Medium/SemiBold/Bold in `Fonts/`).
- **`LocationManager`** (`Core/LocationManager.swift`) — `ObservableObject` with `@Published location`. Nested in `AppState`; views must use `.onReceive(appState.locationManager.$location)` to observe changes (SwiftUI nested `ObservableObject` limitation).

### Android Architecture

- **MVVM + Hilt DI**: `FirebaseService`, `LocationService`, and `LocalizationManager` are `@Singleton` provided via `di/AppModule.kt`.
- **Jetpack Compose Navigation**: `ZampaNavHost` (`ui/navigation/Navigation.kt`, package `com.sozolab.zampa`) owns all routes — `Auth`, `Main`, `MerchantSetup`, `LocationOnboarding`, `Stats`, `MenuDetail`, `NotificationPreferences`, `DietaryPreferences`, `History`, `Language`. Package is `com.sozolab.zampa` (legacy `com.sozolab.eatout` references may still appear in build artifacts and the Play Store URL).
- **`FirebaseService`** (`data/FirebaseService.kt`) — mirrors iOS service exactly: same collections, same logic, using Kotlin coroutines + `await()`.
- ViewModels: `AuthViewModel`, `MainViewModel`, `FeedViewModel`, `FavoritesViewModel`, `DashboardViewModel`, `StatsViewModel`, `SubscriptionViewModel`, `MerchantProfileSetupViewModel`.
- All models in `data/model/Models.kt`: `User`, `Merchant`, `Menu`, `Favorite`, `CuisineType`, `Subscription`, `NotificationPreferences`, `AppNotification`.
- **`LocalizationManager`** (`data/LocalizationManager.kt`) — `@Singleton`, manages locale switching via `context.createConfigurationContext()`. Screens use `stringResource(R.string.key)` with Android's standard resource system. Translations live in `res/values-{lang}/strings.xml` (8 locales).
- **Font: Sora** — `res/font/sora_*.ttf` (4 weights), referenced in `ui/theme/Type.kt` via `FontFamily`.
- Image loading via **Coil** (`coil-compose`). Location via `play-services-location`.
- Android SDK: `minSdk = 26`, `targetSdk = 35`, Java 17, Compose BOM `2025.05.00`, Firebase BOM `33.13.0`, Hilt `2.56`.

### Key Cross-Platform Invariants

When modifying Firestore data structures, both `FirebaseService.swift` and `FirebaseService.kt` must be updated together — they implement identical logic in parallel.

All user-facing strings are externalized. When adding new strings: (1) add the key to `Zampa-iOS/Zampa/Localization/es.json` and all 7 other language JSON files, (2) add the key to `Zampa-Android/app/src/main/res/values/strings.xml` and all `values-{lang}/strings.xml` variants. iOS uses `localization.t("key")`, Android uses `stringResource(R.string.key)`.

Merchant profile is considered "complete" when `businesses/{id}` has both `address` and `phone` fields set (`isMerchantProfileComplete`).

Tracking (impressions, clicks, user history) fails silently and must never block user actions.

Push notifications require: (1) FCM API enabled in Google Cloud, (2) APNs Auth Key uploaded in Firebase Console for iOS, (3) `aps-environment` entitlement in iOS project, (4) device token registered in `deviceTokens` collection after user login.

### Firebase Project (legacy name)

The Firebase project is still called **`eatout-70b8b`** and CANNOT be renamed — it's fixed in Firebase. Expect these legacy references throughout the codebase (they work as-is, don't "fix" them):
- `GoogleService-Info.plist` / `google-services.json` — `PROJECT_ID: eatout-70b8b`, `STORAGE_BUCKET: eatout-70b8b.firebasestorage.app`
- URLs: `eatout-70b8b.web.app`, `eatout-70b8b.firebaseapp.com`
- Seed scripts use `EATOUT_PROJECT_ID`, `EATOUT_BOT_PASSWORD` env vars and `bot{N}@eatout-test.com` emails
- iOS associated domains in `Zampa.entitlements` use `applinks:eatout-70b8b.*`

### Google Sign-In gotchas

**iOS:** The URL scheme in `Info.plist` (`CFBundleURLSchemes`) MUST match the `REVERSED_CLIENT_ID` in `GoogleService-Info.plist` exactly. A mismatch causes a crash when returning from the Google auth flow. Current correct value: `com.googleusercontent.apps.840515033444-i7a2eqgaeufvf48ci1tf2bt036l0cft5`.

**Android:** Each signing certificate (debug + release) needs a `client_type: 1` OAuth client registered in Firebase Console with its SHA-1 fingerprint. Without it, sign-in fails silently. Both are registered for package `com.sozolab.zampa`:
- Debug SHA-1: `07:8A:81:4B:27:41:42:7A:5A:C9:19:7F:A5:AD:2C:AF:76:99:3F:97`
- Release SHA-1: `B6:1A:5A:59:A2:38:DF:43:CD:EF:D1:48:83:1D:26:D9:47:B5:5B:DB` (keystore regenerado 2026-04-16)

After adding fingerprints in Firebase Console, re-download `google-services.json` and replace `Zampa-Android/app/google-services.json`.

**Release keystore:** Lives at `~/zampa-release.keystore` (outside repo, PKCS12 format). Alias: `zampa`. Validity: 10000 days. Configure via `ZAMPA_KEYSTORE_PATH` / `ZAMPA_KEYSTORE_PASS` / `ZAMPA_KEY_PASS` env vars or `local.properties` (`build.gradle.kts` reads these). PKCS12 obliga a que `ZAMPA_KEYSTORE_PASS` y `ZAMPA_KEY_PASS` sean iguales. Credenciales en `~/zampa-keystore-creds.txt` (chmod 600) y en el Keychain de macOS. **Losing the keystore means never being able to update the app on Play Store.**

### Cloud Functions (`functions/`)

- **`onMenuPublished`** — Firestore trigger on `dailyOffers/{menuId}` creation. Sends push notifications to followers of Pro merchants. Checks per-favorite `notificationsEnabled` and per-user `notificationPreferences.newMenuFromFavorites`. Creates in-app notifications in `notifications` collection. Cleans up invalid FCM tokens. Deployed as Node.js 22, 2nd gen Cloud Functions.
- **`expireMenus`** — Scheduled hourly, deactivates expired offers in `dailyOffers`.
- **Seed scripts** — `seed-bots.js` (10 Madrid restaurants) and `seed-bots-extra.js` (55 restaurants across 12 Spanish cities). Run with `node seed-bots.js`. Login: `bot{N}@eatout-test.com` / `Test1234!`.
- **`create-test-offers.js`** — Creates test daily offers from Marc Fosh (Pro, bot57) and Ca'n Joan de s'Aigo (free, bot56) to test push notifications. Run with `node create-test-offers.js`.
