# Overlay Tour — Design Spec
**Date:** 2026-04-27  
**Status:** Approved

## Context

App reviewers flagged the absence of a dynamic walkthrough for new users. The existing onboarding is a full-screen pager (icon + title + description slides) shown once after first login. That pager stays as-is. This spec adds a **coach-mark tour** that runs immediately after the pager, overlaying the real UI with a spotlight and tooltip on each key feature.

---

## Approach: Spotlight + Overlay (Option A)

A semi-transparent dark overlay covers the full screen. A cutout (rounded rect) reveals the target element. A tooltip bubble with title, description, step counter, "Siguiente" button, and "Omitir tour" link is positioned relative to the spotlight.

---

## Tour Steps

### Cliente (4 steps)

| # | Target element | Title | Description |
|---|---|---|---|
| 1 | First feed card | Menús del día cerca de ti | Aquí aparecen las ofertas de los restaurantes cercanos actualizadas cada día. |
| 2 | Filter button (top-right in FeedView) | Filtros | Filtra por precio, tipo de dieta y distancia. |
| 3 | Map tab (tab bar) | Mapa | Explora los restaurantes cerca de ti en el mapa. |
| 4 | Favorites tab (tab bar) | Favoritos | Guarda tus favoritos y recibe notificaciones cuando publiquen. |

### Merchant (3 steps)

| # | Target element | Title | Description |
|---|---|---|---|
| 1 | Dashboard tab (tab bar) | Tu dashboard | Desde aquí gestionas y publicas tus ofertas del día. |
| 2 | Create menu FAB / + button | Publicar menú | Toca + para crear una nueva oferta del día. |
| 3 | Stats grid (inline StatCards in Dashboard) | Estadísticas | Consulta visitas e impresiones de tus ofertas directamente desde aquí. |

---

## State Machine

```
inactive → step(0) → step(1) → ... → step(n) → finished
              ↑ skip from any step → finished
```

- Tour starts after the onboarding pager's `onFinish` callback, once `MainTabView`/`MainScreen` is rendered.
- "Siguiente" on the last step shows "¡Listo!" and marks the tour finished.
- "Omitir tour" on any step marks the tour finished immediately.

---

## Persistence

- **Key:** `hasSeenTour_{uid}` — separate from `hasSeenOnboarding_{uid}`
- **iOS:** `UserDefaults.standard`
- **Android:** `SharedPreferences` (`eatout_prefs`)
- Set to `true` on finish or skip. Never shown again for that uid.
- Users upgrading from a version without the tour will see it on next launch (since the key won't exist).

---

## Files

### New files

| File | Purpose |
|---|---|
| `Zampa-iOS/Zampa/Features/Tour/TourStep.swift` | Model: step id, title key, description key, target identifier |
| `Zampa-iOS/Zampa/Features/Tour/TourManager.swift` | `@MainActor ObservableObject`: currentStepIndex, isActive, next(), skip(), persist |
| `Zampa-iOS/Zampa/Features/Tour/TourOverlayView.swift` | Full-screen SwiftUI overlay: spotlight Path + tooltip bubble |
| `Zampa-Android/…/ui/tour/TourStep.kt` | Data class: same fields |
| `Zampa-Android/…/ui/tour/TourViewModel.kt` | `ViewModel` with `StateFlow<TourState>`, next(), skip() |
| `Zampa-Android/…/ui/tour/TourOverlay.kt` | Composable: Canvas spotlight + tooltip Box |

### Modified files

| File | Change |
|---|---|
| `Zampa-iOS/…/App/ContentView.swift` | Inject `TourManager` as `@StateObject`; show `TourOverlayView` as `.overlay` on `MainTabView` |
| `Zampa-iOS/…/Features/Feed/FeedView.swift` | Add `.tourTarget(.feedCard)` on first card, `.tourTarget(.filterButton)` on filter button |
| `Zampa-iOS/…/Features/Main/MainTabView.swift` | Add `.tourTarget(.mapTab)` and `.tourTarget(.favoritesTab)` on tab items |
| `Zampa-Android/…/ui/main/MainScreen.kt` | Add `TourOverlay` inside the root `Box`; wire `TourViewModel`; pass `onBoundsChanged` callbacks |
| `Zampa-Android/…/ui/feed/FeedScreen.kt` | Add `onGloballyPositioned` modifier on first card and filter button |

---

## Spotlight Rendering

### iOS — SwiftUI Path with even-odd fill rule

```swift
Path { path in
    path.addRect(fullScreenRect)
    path.addRoundedRect(in: targetRect, cornerSize: CGSize(width: 12, height: 12))
}
.fill(Color.black.opacity(0.78), style: FillStyle(eoFill: true))
```

Target bounds are captured via `.anchorPreference(key: TourTargetKey.self, value: .bounds)` on each target element and read in `.overlayPreferenceValue` at the `ContentView` level.

### Android — Canvas with BlendMode.Clear

```kotlin
Canvas(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
) {
    drawRect(color = Color(0xC7000000))
    drawRoundRect(
        color = Color.Transparent,
        blendMode = BlendMode.Clear,
        topLeft = spotlight.offset,
        size = spotlight.size,
        cornerRadius = CornerRadius(12.dp.toPx())
    )
}
```

`CompositingStrategy.Offscreen` is mandatory for `BlendMode.Clear` to work. Target bounds arrive via `onGloballyPositioned` and are stored in `TourViewModel` as `Rect` in screen coordinates.

---

## Tooltip Anatomy

```
┌─────────────────────────────────┐
│ Title               paso X / N  │
│ Description text               │
│                                │
│ Omitir tour    [ Siguiente → ] │
└─────────────────────────────────┘
         ↑ arrow pointing to target
```

- Arrow points toward the spotlight (above or below tooltip depending on element position).
- Last step: "Siguiente →" becomes "¡Listo! ✓".
- Colors: background `#FFFFFF`, button `#FFAA1C` (brand primary), text `#111111`, secondary `#555555`.
- Font: Sora (existing brand font, 4 weights available on both platforms).

---

## Localization

17 new string keys added to all 8 language files:

| Key | ES |
|---|---|
| `tour_skip` | Omitir tour |
| `tour_next` | Siguiente |
| `tour_finish` | ¡Listo! |
| `tour_feed_title` | Menús del día cerca de ti |
| `tour_feed_desc` | Aquí aparecen las ofertas de los restaurantes cercanos actualizadas cada día. |
| `tour_filters_title` | Filtros |
| `tour_filters_desc` | Filtra por precio, tipo de dieta y distancia. |
| `tour_map_title` | Mapa |
| `tour_map_desc` | Explora los restaurantes cerca de ti en el mapa. |
| `tour_favorites_title` | Favoritos |
| `tour_favorites_desc` | Guarda tus favoritos y recibe notificaciones cuando publiquen. |
| `tour_merchant_dashboard_title` | Tu dashboard |
| `tour_merchant_dashboard_desc` | Desde aquí gestionas y publicas tus ofertas del día. |
| `tour_merchant_create_title` | Publicar menú |
| `tour_merchant_create_desc` | Toca + para crear una nueva oferta del día. |
| `tour_merchant_stats_title` | Estadísticas |
| `tour_merchant_stats_desc` | Consulta visitas e impresiones de tus ofertas directamente desde aquí. |

iOS: keys added to `Localization/{lang}.json` (8 files).  
Android: keys added to `res/values/strings.xml` + 7 `values-{lang}/strings.xml`.

---

## Edge Cases

- **Feed vacío en paso 1:** Si no hay cards en el feed, el spotlight apunta al área de contenido del feed (el `LazyColumn`/`ScrollView` completo) en lugar de la primera card. El tour no se bloquea.
- **TourManager como EnvironmentObject (iOS):** `TourManager` se crea como `@StateObject` en `ContentView` y se inyecta hacia abajo como `@EnvironmentObject` para que `FeedView` y `MainTabView` puedan registrar sus bounds sin acoplarse directamente.

## Out of Scope

- Re-triggering the tour manually from Settings (can be added post-launch).
- Animated transitions between steps (fade/slide) — plain appear/disappear is sufficient for v1.
- Tour for the merchant profile setup flow — that flow already has inline helper text.
