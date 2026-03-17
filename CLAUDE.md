# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EatOut is a two-sided marketplace mobile app where restaurants/businesses ("comercios") publish daily meal offers ("menús"), and customers ("clientes") browse a feed and favorite businesses. There is no REST backend — both platforms talk directly to Firebase (Auth, Firestore, Storage, Messaging).

Two separate native apps share the same Firebase project:
- `EatOut-iOS/` — SwiftUI app
- `EatOut-Android/` — Jetpack Compose app
- `firebase/` — Firestore and Storage security rules

## Building & Running

### iOS (`EatOut-iOS/`)

**Open in Xcode (required — do not use `xcodebuild` for first build):**
```bash
open "EatOut-iOS/EatOut.xcodeproj"
```

Firebase SPM packages must be resolved via Xcode GUI:
- `File -> Packages -> Reset Package Caches`, then `File -> Packages -> Resolve Package Versions`
- First build takes 5-10 minutes while Firebase compiles

**Clean DerivedData if packages break:**
```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/EatOut-*
```

### Android (`EatOut-Android/`)

Open in Android Studio or build via Gradle:
```bash
cd EatOut-Android
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
- **Merchant plan tiers**: `"free"` or `"pro"` — controls `isMerchantPro` flag on offers and `isPremium` in iOS `AppState`.

### Firestore Collections

| Collection | Purpose |
|---|---|
| `users` | Auth profile + role |
| `businesses` | Merchant profiles (doc ID = Firebase Auth UID) |
| `customers` | Customer profiles |
| `dailyOffers` | Daily meal offers (public read) |
| `favorites` | Customer-to-business relationships |
| `metrics/{merchantId}/daily/{date}` | Impressions and click tracking |
| `deviceTokens` | FCM push tokens |
| `notifications` | In-app notifications |
| `subscriptions` | Merchant subscription records |
| `cuisineTypes` | Catalog of cuisine categories |
| `reports` | User-submitted offer reports |

### iOS Architecture

- **`AppState`** (`Core/AppState.swift`) — global `ObservableObject` injected as `@EnvironmentObject`. Holds auth state, current user, merchant profile, and premium status.
- **`ContentView`** — root router: shows loading spinner -> `AuthView` (unauthenticated) -> `MerchantProfileSetupView` (merchant without complete profile) -> `MainTabView` (authenticated).
- **`FirebaseService`** (`Services/FirebaseService.swift`) — singleton, all Firestore/Storage operations. Handles auth, offers, favorites, metrics, notifications, and subscriptions.
- **`APIClient`** (`Core/Networking/APIClient.swift`) — HTTP client with auto token refresh for a potential REST API. DEBUG points to `localhost:3000`, production to `https://api.eatout.com`. Firebase is the primary data source.
- **`KeychainManager`** — stores legacy REST API access/refresh tokens.
- **Services** (`Services/`) — `FavoriteService`, `MenuService`, `PushManager` wrap `FirebaseService` for specific domains.
- Feature views live in `Features/{Auth,Feed,Favorites,Merchant,Profile,Subscription}/`.
- Models in `Core/Models/`: `User`, `Merchant`, `Menu`, `Favorite`, `Subscription`, `CuisineType`, `AppNotification`.
- Design tokens centralized in `Core/DesignSystem.swift`.

### Android Architecture

- **MVVM + Hilt DI**: `FirebaseService` and `LocationService` are `@Singleton` provided via `di/AppModule.kt`.
- **Jetpack Compose Navigation**: `EatOutNavHost` (`ui/navigation/Navigation.kt`) owns all routes — `Auth`, `Main`, `MerchantSetup`, `LocationOnboarding`, `Stats`, `MenuDetail`.
- **`FirebaseService`** (`data/FirebaseService.kt`) — mirrors iOS service exactly: same collections, same logic, using Kotlin coroutines + `await()`.
- ViewModels: `AuthViewModel`, `MainViewModel`, `FeedViewModel`, `FavoritesViewModel`, `DashboardViewModel`, `StatsViewModel`, `SubscriptionViewModel`, `MerchantProfileSetupViewModel`.
- All models in `data/model/Models.kt`: `User`, `Merchant`, `Menu`, `Favorite`, `CuisineType`, `Subscription`, `AppNotification`.
- Image loading via **Coil** (`coil-compose`). Location via `play-services-location`.
- Android SDK: `minSdk = 26`, `targetSdk = 35`, Java 17, Compose BOM `2025.05.00`, Firebase BOM `33.13.0`, Hilt `2.56`.

### Key Cross-Platform Invariants

When modifying Firestore data structures, both `FirebaseService.swift` and `FirebaseService.kt` must be updated together — they implement identical logic in parallel.

Merchant profile is considered "complete" when `businesses/{id}` has both `address` and `phone` fields set (`isMerchantProfileComplete`).

Tracking (impressions, clicks) fails silently and must never block user actions.
