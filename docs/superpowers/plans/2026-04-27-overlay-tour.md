# Overlay Tour Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a spotlight coach-mark tour (overlay + tooltip) that runs once after the first-login onboarding pager for both cliente and merchant users, guiding them through key UI features.

**Architecture:** A `TourManager` (iOS) / `TourViewModel` (Android) holds the tour state (active, currentStep, bounds registry). A full-screen overlay draws a dark mask with an even-odd cutout around the target element. A tooltip bubble with title, description, step counter, "Siguiente" and "Omitir" appears relative to the spotlight. Tour is gated by `hasSeenTour_{uid}` in UserDefaults / SharedPreferences (separate from onboarding flag).

**Tech Stack:** SwiftUI (iOS) · Jetpack Compose (Android) · UserDefaults / SharedPreferences · No new dependencies required.

---

## File Map

### New files
| Path | Responsibility |
|---|---|
| `Zampa-iOS/Zampa/Features/Tour/TourStep.swift` | TourTarget enum, TourStep model, client/merchant step lists, TourTargetModifier |
| `Zampa-iOS/Zampa/Features/Tour/TourManager.swift` | ObservableObject: state, next(), skip(), bounds registry, persistence |
| `Zampa-iOS/Zampa/Features/Tour/TourOverlayView.swift` | SpotlightShape, tooltip bubble, triangle arrow |
| `Zampa-Android/.../ui/tour/TourStep.kt` | TourTarget enum, TourStep data class, client/merchant step lists |
| `Zampa-Android/.../ui/tour/TourViewModel.kt` | ViewModel: TourState, start(), next(), skip(), bounds registry |
| `Zampa-Android/.../ui/tour/TourOverlay.kt` | Canvas spotlight + tooltip composable |

### Modified files
| Path | Change |
|---|---|
| `Zampa-iOS/Zampa/App/ContentView.swift` | Add `@StateObject tourManager`, inject as `.environmentObject`, add `.overlay` |
| `Zampa-iOS/Zampa/Features/Feed/MainTabView.swift` | Register tab bounds + call `tourManager.start()` on appear |
| `Zampa-iOS/Zampa/Features/Feed/FeedView.swift` | `.tourTarget` on filter button, map toggle, and first card |
| `Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift` | `.tourTarget` on create button and stats HStack |
| `Zampa-Android/.../ui/main/MainScreen.kt` | Add `TourViewModel`, bounds registration callbacks, `TourOverlay` in root Box |
| `Zampa-Android/.../ui/feed/FeedScreen.kt` | `onGloballyPositioned` on filter button, map toggle, first card |
| `Zampa-Android/.../ui/merchant/DashboardScreen.kt` | `onGloballyPositioned` on create button and stats Column |
| `Zampa-iOS/Zampa/Localization/*.json` (8 files) | 17 new tour string keys |
| `Zampa-Android/.../res/values*/strings.xml` (9 files) | 17 new tour string keys |

---

## Task 1: iOS Localization Strings

**Files:**
- Modify: `Zampa-iOS/Zampa/Localization/es.json`
- Modify: `Zampa-iOS/Zampa/Localization/ca.json`
- Modify: `Zampa-iOS/Zampa/Localization/eu.json`
- Modify: `Zampa-iOS/Zampa/Localization/gl.json`
- Modify: `Zampa-iOS/Zampa/Localization/en.json`
- Modify: `Zampa-iOS/Zampa/Localization/de.json`
- Modify: `Zampa-iOS/Zampa/Localization/fr.json`
- Modify: `Zampa-iOS/Zampa/Localization/it.json`

- [ ] **Step 1: Add tour strings to es.json**

Add before the closing `}` of the JSON file:

```json
  "tour_skip": "Omitir tour",
  "tour_next": "Siguiente",
  "tour_finish": "¡Listo!",
  "tour_feed_title": "Menús del día cerca de ti",
  "tour_feed_desc": "Aquí aparecen las ofertas de los restaurantes cercanos actualizadas cada día.",
  "tour_filters_title": "Filtros",
  "tour_filters_desc": "Filtra por precio, tipo de dieta y distancia.",
  "tour_map_title": "Mapa",
  "tour_map_desc": "Alterna entre lista de restaurantes y vista de mapa.",
  "tour_favorites_title": "Favoritos",
  "tour_favorites_desc": "Guarda tus favoritos y recibe notificaciones cuando publiquen.",
  "tour_merchant_dashboard_title": "Tu dashboard",
  "tour_merchant_dashboard_desc": "Desde aquí gestionas y publicas tus ofertas del día.",
  "tour_merchant_create_title": "Publicar menú",
  "tour_merchant_create_desc": "Toca + para crear una nueva oferta del día.",
  "tour_merchant_stats_title": "Estadísticas",
  "tour_merchant_stats_desc": "Consulta visitas e impresiones de tus ofertas directamente desde aquí."
```

- [ ] **Step 2: Add tour strings to ca.json**

```json
  "tour_skip": "Ometre tour",
  "tour_next": "Següent",
  "tour_finish": "Llest!",
  "tour_feed_title": "Menús del dia prop teu",
  "tour_feed_desc": "Aquí apareixen les ofertes dels restaurants propers actualitzades cada dia.",
  "tour_filters_title": "Filtres",
  "tour_filters_desc": "Filtra per preu, tipus de dieta i distància.",
  "tour_map_title": "Mapa",
  "tour_map_desc": "Alterna entre llista de restaurants i vista de mapa.",
  "tour_favorites_title": "Preferits",
  "tour_favorites_desc": "Guarda els teus preferits i rep notificacions quan publiquin.",
  "tour_merchant_dashboard_title": "El teu tauler",
  "tour_merchant_dashboard_desc": "Des d'aquí gestiones i publiques les teves ofertes del dia.",
  "tour_merchant_create_title": "Publicar menú",
  "tour_merchant_create_desc": "Toca + per crear una nova oferta del dia.",
  "tour_merchant_stats_title": "Estadístiques",
  "tour_merchant_stats_desc": "Consulta visites i impressions de les teves ofertes directament des d'aquí."
```

- [ ] **Step 3: Add tour strings to eu.json**

```json
  "tour_skip": "Saltatu",
  "tour_next": "Hurrengoa",
  "tour_finish": "Prest!",
  "tour_feed_title": "Eguneko menuak zure inguruan",
  "tour_feed_desc": "Hemen agertzen dira inguruko jatetxeetako eskaintzak egunero eguneratuta.",
  "tour_filters_title": "Iragazkiak",
  "tour_filters_desc": "Iragazi prezioaren, dieta motaren eta distantziaren arabera.",
  "tour_map_title": "Mapa",
  "tour_map_desc": "Txandakatu jatetxeen zerrenda eta mapa ikuspegiaren artean.",
  "tour_favorites_title": "Gogokoenak",
  "tour_favorites_desc": "Gorde gogokoenak eta jaso jakinarazpenak argitaratzen dutenean.",
  "tour_merchant_dashboard_title": "Zure panela",
  "tour_merchant_dashboard_desc": "Hemendik kudeatzen eta argitaratzen dituzu eguneko eskaintzak.",
  "tour_merchant_create_title": "Menua argitaratu",
  "tour_merchant_create_desc": "Sakatu + eguneko eskaintza berri bat sortzeko.",
  "tour_merchant_stats_title": "Estatistikak",
  "tour_merchant_stats_desc": "Kontsultatu zure eskaintzen bisitak eta inpresioak zuzenean hemendik."
```

- [ ] **Step 4: Add tour strings to gl.json**

```json
  "tour_skip": "Omitir tour",
  "tour_next": "Seguinte",
  "tour_finish": "Listo!",
  "tour_feed_title": "Menús do día preto de ti",
  "tour_feed_desc": "Aquí aparecen as ofertas dos restaurantes próximos actualizadas cada día.",
  "tour_filters_title": "Filtros",
  "tour_filters_desc": "Filtra por prezo, tipo de dieta e distancia.",
  "tour_map_title": "Mapa",
  "tour_map_desc": "Alterna entre lista de restaurantes e vista de mapa.",
  "tour_favorites_title": "Favoritos",
  "tour_favorites_desc": "Garda os teus favoritos e recibe notificacións cando publiquen.",
  "tour_merchant_dashboard_title": "O teu panel",
  "tour_merchant_dashboard_desc": "Desde aquí xestionas e publicas as túas ofertas do día.",
  "tour_merchant_create_title": "Publicar menú",
  "tour_merchant_create_desc": "Toca + para crear unha nova oferta do día.",
  "tour_merchant_stats_title": "Estatísticas",
  "tour_merchant_stats_desc": "Consulta visitas e impresións das túas ofertas directamente desde aquí."
```

- [ ] **Step 5: Add tour strings to en.json**

```json
  "tour_skip": "Skip tour",
  "tour_next": "Next",
  "tour_finish": "Done!",
  "tour_feed_title": "Daily menus near you",
  "tour_feed_desc": "Here you'll find offers from nearby restaurants updated every day.",
  "tour_filters_title": "Filters",
  "tour_filters_desc": "Filter by price, dietary type, and distance.",
  "tour_map_title": "Map",
  "tour_map_desc": "Toggle between restaurant list and map view.",
  "tour_favorites_title": "Favorites",
  "tour_favorites_desc": "Save your favorites and get notifications when they publish.",
  "tour_merchant_dashboard_title": "Your dashboard",
  "tour_merchant_dashboard_desc": "Manage and publish your daily offers from here.",
  "tour_merchant_create_title": "Publish menu",
  "tour_merchant_create_desc": "Tap + to create a new daily offer.",
  "tour_merchant_stats_title": "Statistics",
  "tour_merchant_stats_desc": "Check visits and impressions for your offers right here."
```

- [ ] **Step 6: Add tour strings to de.json**

```json
  "tour_skip": "Tour überspringen",
  "tour_next": "Weiter",
  "tour_finish": "Fertig!",
  "tour_feed_title": "Tagesmenüs in deiner Nähe",
  "tour_feed_desc": "Hier findest du täglich aktualisierte Angebote von Restaurants in deiner Nähe.",
  "tour_filters_title": "Filter",
  "tour_filters_desc": "Filtere nach Preis, Ernährungstyp und Entfernung.",
  "tour_map_title": "Karte",
  "tour_map_desc": "Wechsle zwischen Restaurantliste und Kartenansicht.",
  "tour_favorites_title": "Favoriten",
  "tour_favorites_desc": "Speichere Favoriten und erhalte Benachrichtigungen, wenn sie veröffentlichen.",
  "tour_merchant_dashboard_title": "Dein Dashboard",
  "tour_merchant_dashboard_desc": "Verwalte und veröffentliche deine Tagesangebote von hier aus.",
  "tour_merchant_create_title": "Menü veröffentlichen",
  "tour_merchant_create_desc": "Tippe auf +, um ein neues Tagesangebot zu erstellen.",
  "tour_merchant_stats_title": "Statistiken",
  "tour_merchant_stats_desc": "Sieh dir Besuche und Impressionen deiner Angebote direkt hier an."
```

- [ ] **Step 7: Add tour strings to fr.json**

```json
  "tour_skip": "Passer le tour",
  "tour_next": "Suivant",
  "tour_finish": "Terminé !",
  "tour_feed_title": "Menus du jour près de vous",
  "tour_feed_desc": "Ici apparaissent les offres des restaurants à proximité mises à jour chaque jour.",
  "tour_filters_title": "Filtres",
  "tour_filters_desc": "Filtrez par prix, type de régime et distance.",
  "tour_map_title": "Carte",
  "tour_map_desc": "Alternez entre la liste des restaurants et la vue carte.",
  "tour_favorites_title": "Favoris",
  "tour_favorites_desc": "Enregistrez vos favoris et recevez des notifications quand ils publient.",
  "tour_merchant_dashboard_title": "Votre tableau de bord",
  "tour_merchant_dashboard_desc": "Gérez et publiez vos offres du jour depuis ici.",
  "tour_merchant_create_title": "Publier un menu",
  "tour_merchant_create_desc": "Appuyez sur + pour créer une nouvelle offre du jour.",
  "tour_merchant_stats_title": "Statistiques",
  "tour_merchant_stats_desc": "Consultez les visites et impressions de vos offres directement ici."
```

- [ ] **Step 8: Add tour strings to it.json**

```json
  "tour_skip": "Salta il tour",
  "tour_next": "Avanti",
  "tour_finish": "Fatto!",
  "tour_feed_title": "Menu del giorno vicino a te",
  "tour_feed_desc": "Qui trovi le offerte dei ristoranti vicini aggiornate ogni giorno.",
  "tour_filters_title": "Filtri",
  "tour_filters_desc": "Filtra per prezzo, tipo di dieta e distanza.",
  "tour_map_title": "Mappa",
  "tour_map_desc": "Alterna tra lista di ristoranti e vista mappa.",
  "tour_favorites_title": "Preferiti",
  "tour_favorites_desc": "Salva i preferiti e ricevi notifiche quando pubblicano.",
  "tour_merchant_dashboard_title": "Il tuo dashboard",
  "tour_merchant_dashboard_desc": "Gestisci e pubblica le tue offerte del giorno da qui.",
  "tour_merchant_create_title": "Pubblica menu",
  "tour_merchant_create_desc": "Tocca + per creare una nuova offerta del giorno.",
  "tour_merchant_stats_title": "Statistiche",
  "tour_merchant_stats_desc": "Consulta visite e impressioni delle tue offerte direttamente da qui."
```

- [ ] **Step 9: Build iOS to confirm no JSON parse errors**

```bash
cd "Zampa-iOS" && xcodebuild -scheme Zampa -destination 'platform=iOS Simulator,name=iPhone 16 Pro Max' build 2>&1 | grep -E "error:|BUILD"
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 10: Commit**

```bash
git add "Zampa-iOS/Zampa/Localization/"
git commit -m "feat(tour): add iOS localization strings for overlay tour (8 languages)"
```

---

## Task 2: Android Localization Strings

**Files:**
- Modify: `Zampa-Android/app/src/main/res/values/strings.xml` (default/ES fallback)
- Modify: `Zampa-Android/app/src/main/res/values-es/strings.xml`
- Modify: `Zampa-Android/app/src/main/res/values-ca/strings.xml`
- Modify: `Zampa-Android/app/src/main/res/values-eu/strings.xml`
- Modify: `Zampa-Android/app/src/main/res/values-gl/strings.xml`
- Modify: `Zampa-Android/app/src/main/res/values-en/strings.xml`
- Modify: `Zampa-Android/app/src/main/res/values-de/strings.xml`
- Modify: `Zampa-Android/app/src/main/res/values-fr/strings.xml`
- Modify: `Zampa-Android/app/src/main/res/values-it/strings.xml`

- [ ] **Step 1: Add strings to values/strings.xml and values-es/strings.xml**

Add before `</resources>` in both files:

```xml
    <string name="tour_skip">Omitir tour</string>
    <string name="tour_next">Siguiente</string>
    <string name="tour_finish">¡Listo!</string>
    <string name="tour_feed_title">Menús del día cerca de ti</string>
    <string name="tour_feed_desc">Aquí aparecen las ofertas de los restaurantes cercanos actualizadas cada día.</string>
    <string name="tour_filters_title">Filtros</string>
    <string name="tour_filters_desc">Filtra por precio, tipo de dieta y distancia.</string>
    <string name="tour_map_title">Mapa</string>
    <string name="tour_map_desc">Alterna entre lista de restaurantes y vista de mapa.</string>
    <string name="tour_favorites_title">Favoritos</string>
    <string name="tour_favorites_desc">Guarda tus favoritos y recibe notificaciones cuando publiquen.</string>
    <string name="tour_merchant_dashboard_title">Tu dashboard</string>
    <string name="tour_merchant_dashboard_desc">Desde aquí gestionas y publicas tus ofertas del día.</string>
    <string name="tour_merchant_create_title">Publicar menú</string>
    <string name="tour_merchant_create_desc">Toca + para crear una nueva oferta del día.</string>
    <string name="tour_merchant_stats_title">Estadísticas</string>
    <string name="tour_merchant_stats_desc">Consulta visitas e impresiones de tus ofertas directamente desde aquí.</string>
```

- [ ] **Step 2: Add strings to values-ca/strings.xml**

```xml
    <string name="tour_skip">Ometre tour</string>
    <string name="tour_next">Següent</string>
    <string name="tour_finish">Llest!</string>
    <string name="tour_feed_title">Menús del dia prop teu</string>
    <string name="tour_feed_desc">Aquí apareixen les ofertes dels restaurants propers actualitzades cada dia.</string>
    <string name="tour_filters_title">Filtres</string>
    <string name="tour_filters_desc">Filtra per preu, tipus de dieta i distància.</string>
    <string name="tour_map_title">Mapa</string>
    <string name="tour_map_desc">Alterna entre llista de restaurants i vista de mapa.</string>
    <string name="tour_favorites_title">Preferits</string>
    <string name="tour_favorites_desc">Guarda els teus preferits i rep notificacions quan publiquin.</string>
    <string name="tour_merchant_dashboard_title">El teu tauler</string>
    <string name="tour_merchant_dashboard_desc">Des d\'aquí gestiones i publiques les teves ofertes del dia.</string>
    <string name="tour_merchant_create_title">Publicar menú</string>
    <string name="tour_merchant_create_desc">Toca + per crear una nova oferta del dia.</string>
    <string name="tour_merchant_stats_title">Estadístiques</string>
    <string name="tour_merchant_stats_desc">Consulta visites i impressions de les teves ofertes directament des d\'aquí.</string>
```

- [ ] **Step 3: Add strings to values-eu/strings.xml**

```xml
    <string name="tour_skip">Saltatu</string>
    <string name="tour_next">Hurrengoa</string>
    <string name="tour_finish">Prest!</string>
    <string name="tour_feed_title">Eguneko menuak zure inguruan</string>
    <string name="tour_feed_desc">Hemen agertzen dira inguruko jatetxeetako eskaintzak egunero eguneratuta.</string>
    <string name="tour_filters_title">Iragazkiak</string>
    <string name="tour_filters_desc">Iragazi prezioaren, dieta motaren eta distantziaren arabera.</string>
    <string name="tour_map_title">Mapa</string>
    <string name="tour_map_desc">Txandakatu jatetxeen zerrenda eta mapa ikuspegiaren artean.</string>
    <string name="tour_favorites_title">Gogokoenak</string>
    <string name="tour_favorites_desc">Gorde gogokoenak eta jaso jakinarazpenak argitaratzen dutenean.</string>
    <string name="tour_merchant_dashboard_title">Zure panela</string>
    <string name="tour_merchant_dashboard_desc">Hemendik kudeatzen eta argitaratzen dituzu eguneko eskaintzak.</string>
    <string name="tour_merchant_create_title">Menua argitaratu</string>
    <string name="tour_merchant_create_desc">Sakatu + eguneko eskaintza berri bat sortzeko.</string>
    <string name="tour_merchant_stats_title">Estatistikak</string>
    <string name="tour_merchant_stats_desc">Kontsultatu zure eskaintzen bisitak eta inpresioak zuzenean hemendik.</string>
```

- [ ] **Step 4: Add strings to values-gl/strings.xml**

```xml
    <string name="tour_skip">Omitir tour</string>
    <string name="tour_next">Seguinte</string>
    <string name="tour_finish">Listo!</string>
    <string name="tour_feed_title">Menús do día preto de ti</string>
    <string name="tour_feed_desc">Aquí aparecen as ofertas dos restaurantes próximos actualizadas cada día.</string>
    <string name="tour_filters_title">Filtros</string>
    <string name="tour_filters_desc">Filtra por prezo, tipo de dieta e distancia.</string>
    <string name="tour_map_title">Mapa</string>
    <string name="tour_map_desc">Alterna entre lista de restaurantes e vista de mapa.</string>
    <string name="tour_favorites_title">Favoritos</string>
    <string name="tour_favorites_desc">Garda os teus favoritos e recibe notificacións cando publiquen.</string>
    <string name="tour_merchant_dashboard_title">O teu panel</string>
    <string name="tour_merchant_dashboard_desc">Desde aquí xestionas e publicas as túas ofertas do día.</string>
    <string name="tour_merchant_create_title">Publicar menú</string>
    <string name="tour_merchant_create_desc">Toca + para crear unha nova oferta do día.</string>
    <string name="tour_merchant_stats_title">Estatísticas</string>
    <string name="tour_merchant_stats_desc">Consulta visitas e impresións das túas ofertas directamente desde aquí.</string>
```

- [ ] **Step 5: Add strings to values-en/strings.xml**

```xml
    <string name="tour_skip">Skip tour</string>
    <string name="tour_next">Next</string>
    <string name="tour_finish">Done!</string>
    <string name="tour_feed_title">Daily menus near you</string>
    <string name="tour_feed_desc">Here you\'ll find offers from nearby restaurants updated every day.</string>
    <string name="tour_filters_title">Filters</string>
    <string name="tour_filters_desc">Filter by price, dietary type, and distance.</string>
    <string name="tour_map_title">Map</string>
    <string name="tour_map_desc">Toggle between restaurant list and map view.</string>
    <string name="tour_favorites_title">Favorites</string>
    <string name="tour_favorites_desc">Save your favorites and get notifications when they publish.</string>
    <string name="tour_merchant_dashboard_title">Your dashboard</string>
    <string name="tour_merchant_dashboard_desc">Manage and publish your daily offers from here.</string>
    <string name="tour_merchant_create_title">Publish menu</string>
    <string name="tour_merchant_create_desc">Tap + to create a new daily offer.</string>
    <string name="tour_merchant_stats_title">Statistics</string>
    <string name="tour_merchant_stats_desc">Check visits and impressions for your offers right here.</string>
```

- [ ] **Step 6: Add strings to values-de/strings.xml**

```xml
    <string name="tour_skip">Tour überspringen</string>
    <string name="tour_next">Weiter</string>
    <string name="tour_finish">Fertig!</string>
    <string name="tour_feed_title">Tagesmenüs in deiner Nähe</string>
    <string name="tour_feed_desc">Hier findest du täglich aktualisierte Angebote von Restaurants in deiner Nähe.</string>
    <string name="tour_filters_title">Filter</string>
    <string name="tour_filters_desc">Filtere nach Preis, Ernährungstyp und Entfernung.</string>
    <string name="tour_map_title">Karte</string>
    <string name="tour_map_desc">Wechsle zwischen Restaurantliste und Kartenansicht.</string>
    <string name="tour_favorites_title">Favoriten</string>
    <string name="tour_favorites_desc">Speichere Favoriten und erhalte Benachrichtigungen, wenn sie veröffentlichen.</string>
    <string name="tour_merchant_dashboard_title">Dein Dashboard</string>
    <string name="tour_merchant_dashboard_desc">Verwalte und veröffentliche deine Tagesangebote von hier aus.</string>
    <string name="tour_merchant_create_title">Menü veröffentlichen</string>
    <string name="tour_merchant_create_desc">Tippe auf +, um ein neues Tagesangebot zu erstellen.</string>
    <string name="tour_merchant_stats_title">Statistiken</string>
    <string name="tour_merchant_stats_desc">Sieh dir Besuche und Impressionen deiner Angebote direkt hier an.</string>
```

- [ ] **Step 7: Add strings to values-fr/strings.xml**

```xml
    <string name="tour_skip">Passer le tour</string>
    <string name="tour_next">Suivant</string>
    <string name="tour_finish">Terminé !</string>
    <string name="tour_feed_title">Menus du jour près de vous</string>
    <string name="tour_feed_desc">Ici apparaissent les offres des restaurants à proximité mises à jour chaque jour.</string>
    <string name="tour_filters_title">Filtres</string>
    <string name="tour_filters_desc">Filtrez par prix, type de régime et distance.</string>
    <string name="tour_map_title">Carte</string>
    <string name="tour_map_desc">Alternez entre la liste des restaurants et la vue carte.</string>
    <string name="tour_favorites_title">Favoris</string>
    <string name="tour_favorites_desc">Enregistrez vos favoris et recevez des notifications quand ils publient.</string>
    <string name="tour_merchant_dashboard_title">Votre tableau de bord</string>
    <string name="tour_merchant_dashboard_desc">Gérez et publiez vos offres du jour depuis ici.</string>
    <string name="tour_merchant_create_title">Publier un menu</string>
    <string name="tour_merchant_create_desc">Appuyez sur + pour créer une nouvelle offre du jour.</string>
    <string name="tour_merchant_stats_title">Statistiques</string>
    <string name="tour_merchant_stats_desc">Consultez les visites et impressions de vos offres directement ici.</string>
```

- [ ] **Step 8: Add strings to values-it/strings.xml**

```xml
    <string name="tour_skip">Salta il tour</string>
    <string name="tour_next">Avanti</string>
    <string name="tour_finish">Fatto!</string>
    <string name="tour_feed_title">Menu del giorno vicino a te</string>
    <string name="tour_feed_desc">Qui trovi le offerte dei ristoranti vicini aggiornate ogni giorno.</string>
    <string name="tour_filters_title">Filtri</string>
    <string name="tour_filters_desc">Filtra per prezzo, tipo di dieta e distanza.</string>
    <string name="tour_map_title">Mappa</string>
    <string name="tour_map_desc">Alterna tra lista di ristoranti e vista mappa.</string>
    <string name="tour_favorites_title">Preferiti</string>
    <string name="tour_favorites_desc">Salva i preferiti e ricevi notifiche quando pubblicano.</string>
    <string name="tour_merchant_dashboard_title">Il tuo dashboard</string>
    <string name="tour_merchant_dashboard_desc">Gestisci e pubblica le tue offerte del giorno da qui.</string>
    <string name="tour_merchant_create_title">Pubblica menu</string>
    <string name="tour_merchant_create_desc">Tocca + per creare una nuova offerta del giorno.</string>
    <string name="tour_merchant_stats_title">Statistiche</string>
    <string name="tour_merchant_stats_desc">Consulta visite e impressioni delle tue offerte direttamente da qui.</string>
```

- [ ] **Step 9: Build Android to confirm no resource errors**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add "Zampa-Android/app/src/main/res/"
git commit -m "feat(tour): add Android localization strings for overlay tour (9 resource files)"
```

---

## Task 3: iOS TourStep + TourManager

**Files:**
- Create: `Zampa-iOS/Zampa/Features/Tour/TourStep.swift`
- Create: `Zampa-iOS/Zampa/Features/Tour/TourManager.swift`

- [ ] **Step 1: Create TourStep.swift**

Create `Zampa-iOS/Zampa/Features/Tour/TourStep.swift`:

```swift
import SwiftUI

enum TourTarget: String, Hashable {
    case feedCard
    case filterButton
    case mapToggle
    case favoritesTab
    case merchantDashboardTab
    case merchantCreateButton
    case merchantStatsGrid
}

struct TourStep {
    let target: TourTarget
    let titleKey: String
    let descKey: String

    static let clientSteps: [TourStep] = [
        TourStep(target: .feedCard,       titleKey: "tour_feed_title",     descKey: "tour_feed_desc"),
        TourStep(target: .filterButton,   titleKey: "tour_filters_title",  descKey: "tour_filters_desc"),
        TourStep(target: .mapToggle,      titleKey: "tour_map_title",      descKey: "tour_map_desc"),
        TourStep(target: .favoritesTab,   titleKey: "tour_favorites_title",descKey: "tour_favorites_desc"),
    ]

    static let merchantSteps: [TourStep] = [
        TourStep(target: .merchantDashboardTab,  titleKey: "tour_merchant_dashboard_title", descKey: "tour_merchant_dashboard_desc"),
        TourStep(target: .merchantCreateButton,  titleKey: "tour_merchant_create_title",    descKey: "tour_merchant_create_desc"),
        TourStep(target: .merchantStatsGrid,     titleKey: "tour_merchant_stats_title",     descKey: "tour_merchant_stats_desc"),
    ]
}

// MARK: - Preference key + modifier for registering element bounds

struct TourBoundsKey: PreferenceKey {
    typealias Value = [TourTarget: CGRect]
    static var defaultValue: [TourTarget: CGRect] = [:]
    static func reduce(value: inout Value, nextValue: () -> Value) {
        value.merge(nextValue(), uniquingKeysWith: { $1 })
    }
}

struct TourTargetModifier: ViewModifier {
    let target: TourTarget
    @EnvironmentObject var tourManager: TourManager

    func body(content: Content) -> some View {
        content
            .background(
                GeometryReader { geo in
                    Color.clear.preference(
                        key: TourBoundsKey.self,
                        value: [target: geo.frame(in: .global)]
                    )
                }
            )
            .onPreferenceChange(TourBoundsKey.self) { bounds in
                for (t, rect) in bounds {
                    tourManager.register(target: t, bounds: rect)
                }
            }
    }
}

extension View {
    func tourTarget(_ target: TourTarget) -> some View {
        modifier(TourTargetModifier(target: target))
    }

    @ViewBuilder
    func tourTarget(_ target: TourTarget, when condition: Bool) -> some View {
        if condition {
            modifier(TourTargetModifier(target: target))
        } else {
            self
        }
    }
}
```

- [ ] **Step 2: Create TourManager.swift**

Create `Zampa-iOS/Zampa/Features/Tour/TourManager.swift`:

```swift
import Foundation
import SwiftUI

@MainActor
final class TourManager: ObservableObject {
    @Published private(set) var isActive: Bool = false
    @Published private(set) var currentStepIndex: Int = 0
    @Published var targetBounds: [TourTarget: CGRect] = [:]

    private var steps: [TourStep] = []
    private var uid: String = ""

    // MARK: - Public API

    func start(for uid: String, isMerchant: Bool) {
        guard !UserDefaults.standard.bool(forKey: "hasSeenTour_\(uid)") else { return }
        self.uid = uid
        self.steps = isMerchant ? TourStep.merchantSteps : TourStep.clientSteps
        self.currentStepIndex = 0
        self.isActive = true
    }

    func next() {
        if currentStepIndex < steps.count - 1 {
            currentStepIndex += 1
        } else {
            finish()
        }
    }

    func skip() {
        finish()
    }

    func register(target: TourTarget, bounds: CGRect) {
        targetBounds[target] = bounds
    }

    // MARK: - Derived state

    var currentStep: TourStep? {
        guard isActive, currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }

    var isLastStep: Bool { currentStepIndex == steps.count - 1 }
    var totalSteps: Int { steps.count }

    var currentTargetBounds: CGRect? {
        guard let step = currentStep else { return nil }
        return targetBounds[step.target]
    }

    // MARK: - Private

    private func finish() {
        UserDefaults.standard.set(true, forKey: "hasSeenTour_\(uid)")
        isActive = false
    }
}
```

- [ ] **Step 3: Build to confirm compilation**

```bash
cd "Zampa-iOS" && xcodebuild -scheme Zampa -destination 'platform=iOS Simulator,name=iPhone 16 Pro Max' build 2>&1 | grep -E "error:|BUILD"
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 4: Commit**

```bash
git add "Zampa-iOS/Zampa/Features/Tour/"
git commit -m "feat(tour): add iOS TourStep model and TourManager"
```

---

## Task 4: iOS TourOverlayView

**Files:**
- Create: `Zampa-iOS/Zampa/Features/Tour/TourOverlayView.swift`

- [ ] **Step 1: Create TourOverlayView.swift**

Create `Zampa-iOS/Zampa/Features/Tour/TourOverlayView.swift`:

```swift
import SwiftUI

struct TourOverlayView: View {
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared

    var body: some View {
        GeometryReader { geo in
            if let step = tourManager.currentStep,
               let targetRect = tourManager.currentTargetBounds {
                ZStack(alignment: .topLeading) {
                    SpotlightShape(cutout: targetRect.insetBy(dx: -6, dy: -6).with(cornerRadius: 12))
                        .fill(Color.black.opacity(0.78), style: FillStyle(eoFill: true))
                        .ignoresSafeArea()
                        .animation(.easeInOut(duration: 0.25), value: targetRect)

                    TourTooltipView(
                        titleKey: step.titleKey,
                        descKey: step.descKey,
                        stepIndex: tourManager.currentStepIndex,
                        totalSteps: tourManager.totalSteps,
                        isLast: tourManager.isLastStep,
                        targetRect: targetRect,
                        screenSize: geo.size
                    )
                }
            }
        }
        .ignoresSafeArea()
    }
}

// MARK: - Spotlight shape (dark mask with rounded-rect cutout)

struct SpotlightShape: Shape {
    var cutoutRect: CGRect
    var cornerRadius: CGFloat

    init(cutout: SpotlightCutout) {
        self.cutoutRect = cutout.rect
        self.cornerRadius = cutout.cornerRadius
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(rect)
        path.addRoundedRect(
            in: cutoutRect,
            cornerSize: CGSize(width: cornerRadius, height: cornerRadius)
        )
        return path
    }

    var animatableData: AnimatablePair<AnimatablePair<CGFloat, CGFloat>, AnimatablePair<CGFloat, CGFloat>> {
        get {
            AnimatablePair(
                AnimatablePair(cutoutRect.origin.x, cutoutRect.origin.y),
                AnimatablePair(cutoutRect.width, cutoutRect.height)
            )
        }
        set {
            cutoutRect.origin.x = newValue.first.first
            cutoutRect.origin.y = newValue.first.second
            cutoutRect.width    = newValue.second.first
            cutoutRect.height   = newValue.second.second
        }
    }
}

struct SpotlightCutout {
    let rect: CGRect
    let cornerRadius: CGFloat
}

extension CGRect {
    func with(cornerRadius: CGFloat) -> SpotlightCutout {
        SpotlightCutout(rect: self, cornerRadius: cornerRadius)
    }
}

// MARK: - Tooltip bubble

struct TourTooltipView: View {
    @EnvironmentObject var tourManager: TourManager
    @ObservedObject var localization = LocalizationManager.shared

    let titleKey: String
    let descKey: String
    let stepIndex: Int
    let totalSteps: Int
    let isLast: Bool
    let targetRect: CGRect
    let screenSize: CGSize

    private var showAbove: Bool {
        targetRect.midY > screenSize.height * 0.55
    }

    private var tooltipX: CGFloat {
        min(max(targetRect.midX - 130, 16), screenSize.width - 276)
    }

    private var tooltipY: CGFloat {
        showAbove
            ? targetRect.minY - 140
            : targetRect.maxY + 12
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if showAbove {
                tooltipCard
                arrowView(pointingDown: true)
                    .padding(.leading, arrowLeadingPadding)
            } else {
                arrowView(pointingDown: false)
                    .padding(.leading, arrowLeadingPadding)
                tooltipCard
            }
        }
        .frame(width: 260)
        .position(x: tooltipX + 130, y: tooltipY + (showAbove ? 60 : 60))
        .animation(.easeInOut(duration: 0.25), value: stepIndex)
    }

    private var arrowLeadingPadding: CGFloat {
        min(max(targetRect.midX - tooltipX - 8, 12), 230)
    }

    private var tooltipCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                Text(localization.t(titleKey))
                    .font(.custom("Sora-SemiBold", size: 14))
                    .foregroundColor(.black)
                Spacer()
                Text("\(stepIndex + 1) / \(totalSteps)")
                    .font(.custom("Sora-Regular", size: 11))
                    .foregroundColor(.gray)
            }
            Text(localization.t(descKey))
                .font(.custom("Sora-Regular", size: 12))
                .foregroundColor(Color(.systemGray))
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Button(localization.t("tour_skip")) { tourManager.skip() }
                    .font(.custom("Sora-Regular", size: 12))
                    .foregroundColor(.gray)
                Spacer()
                Button(isLast ? localization.t("tour_finish") : localization.t("tour_next")) {
                    tourManager.next()
                }
                .font(.custom("Sora-SemiBold", size: 13))
                .foregroundColor(.black)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(Color.appPrimary)
                .cornerRadius(8)
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.2), radius: 12, y: 4)
    }

    @ViewBuilder
    private func arrowView(pointingDown: Bool) -> some View {
        Triangle(pointingDown: pointingDown)
            .fill(Color.white)
            .frame(width: 16, height: 8)
    }
}

// MARK: - Triangle arrow shape

struct Triangle: Shape {
    var pointingDown: Bool

    func path(in rect: CGRect) -> Path {
        var path = Path()
        if pointingDown {
            path.move(to: CGPoint(x: rect.minX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        } else {
            path.move(to: CGPoint(x: rect.midX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        }
        path.closeSubpath()
        return path
    }
}
```

- [ ] **Step 2: Build to confirm compilation**

```bash
cd "Zampa-iOS" && xcodebuild -scheme Zampa -destination 'platform=iOS Simulator,name=iPhone 16 Pro Max' build 2>&1 | grep -E "error:|BUILD"
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 3: Commit**

```bash
git add "Zampa-iOS/Zampa/Features/Tour/TourOverlayView.swift"
git commit -m "feat(tour): add iOS TourOverlayView with spotlight and tooltip"
```

---

## Task 5: iOS Integration (ContentView + MainTabView + FeedView + MerchantDashboardView)

**Files:**
- Modify: `Zampa-iOS/Zampa/App/ContentView.swift`
- Modify: `Zampa-iOS/Zampa/Features/Feed/MainTabView.swift`
- Modify: `Zampa-iOS/Zampa/Features/Feed/FeedView.swift`
- Modify: `Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift`

- [ ] **Step 1: Modify ContentView.swift**

Add `@StateObject private var tourManager = TourManager()` after the existing `@State` declarations, and add `.environmentObject(tourManager)` + `.overlay` to `MainTabView`:

In `ContentView.swift`, change:

```swift
    @State private var onboardingFinishedThisSession = false
```
to:
```swift
    @State private var onboardingFinishedThisSession = false
    @StateObject private var tourManager = TourManager()
```

Change the `MainTabView()` line from:
```swift
                    MainTabView()
```
to:
```swift
                    MainTabView()
                        .environmentObject(tourManager)
                        .overlay {
                            if tourManager.isActive {
                                TourOverlayView()
                                    .environmentObject(tourManager)
                                    .ignoresSafeArea()
                            }
                        }
```

- [ ] **Step 2: Modify MainTabView.swift**

Add `@EnvironmentObject var tourManager: TourManager` after the existing `@EnvironmentObject var appState: AppState` line:

```swift
    @EnvironmentObject var appState: AppState
    @EnvironmentObject var tourManager: TourManager
```

Add `onAppear` to the `TabView` to register tab bounds and start the tour. Replace the closing `.tint(.appPrimary)` block with:

```swift
        .tint(.appPrimary)
        .background(
            GeometryReader { geo in
                Color.clear.onAppear {
                    registerTabBounds(geo: geo)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                        if let uid = appState.currentUser?.id {
                            tourManager.start(
                                for: uid,
                                isMerchant: appState.currentUser?.role == .comercio
                            )
                        }
                    }
                }
            }
        )
```

Add the helper function inside `MainTabView`:

```swift
    private func registerTabBounds(geo: GeometryProxy) {
        let isMerchant = appState.currentUser?.role == .comercio
        let tabCount: CGFloat = isMerchant ? 4 : 3
        let tabWidth = geo.size.width / tabCount
        let safeBottom = geo.safeAreaInsets.bottom
        let tabBarTop = geo.size.height - 49 - safeBottom

        // Favorites tab is always at index 1
        tourManager.register(
            target: .favoritesTab,
            bounds: CGRect(x: tabWidth * 1, y: tabBarTop, width: tabWidth, height: 49)
        )
        // Dashboard tab is at index 2 for merchants
        if isMerchant {
            tourManager.register(
                target: .merchantDashboardTab,
                bounds: CGRect(x: tabWidth * 2, y: tabBarTop, width: tabWidth, height: 49)
            )
        }
    }
```

- [ ] **Step 3: Modify FeedView.swift — add .tourTarget on filter button**

Find the filter button block (around line 104):
```swift
                    Button(action: { showingFilters = true }) {
```

Add `.tourTarget(.filterButton)` after the closing brace of the button and before `.padding(.trailing, 20)`:
```swift
                    Button(action: { showingFilters = true }) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "slider.horizontal.3")
                                .padding(10)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color.appInputBackground))
                                .foregroundColor(.appTextPrimary)
                            if filtersActive {
                                Circle()
                                    .fill(Color.appPrimary)
                                    .frame(width: 8, height: 8)
                                    .offset(x: -2, y: 2)
                            }
                        }
                    }
                    .tourTarget(.filterButton)
                    .padding(.trailing, 20)
```

- [ ] **Step 4: Modify FeedView.swift — add .tourTarget on map toggle**

Find the map/list toggle button (the button that switches `viewMode`). Add `.tourTarget(.mapToggle)` after the button's closing brace. The button looks like:

```swift
                    Button(action: {
                        viewMode = (viewMode == .list) ? .map : .list
                    }) {
                        Image(systemName: viewMode == .list ? "map.fill" : "list.bullet")
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(
                                viewMode == .map ? Color.appPrimary : Color.appInputBackground
                            ))
                            .foregroundColor(viewMode == .map ? .white : .appTextPrimary)
                    }
                    .tourTarget(.mapToggle)
```

- [ ] **Step 5: Modify FeedView.swift — add .tourTarget on first card**

Find the `ForEach(sortedMenus)` block. Change:

```swift
                        LazyVStack(spacing: 0) {
                            ForEach(sortedMenus) { menu in
                                MenuCard(menu: menu, onMerchantLoaded: { id, merchant in
                                    merchantMap[id] = merchant
                                })
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .onAppear {
```

to:

```swift
                        LazyVStack(spacing: 0) {
                            ForEach(sortedMenus) { menu in
                                MenuCard(menu: menu, onMerchantLoaded: { id, merchant in
                                    merchantMap[id] = merchant
                                })
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .tourTarget(.feedCard, when: menu.id == sortedMenus.first?.id)
                                .onAppear {
```

- [ ] **Step 6: Modify MerchantDashboardView.swift — add .tourTarget on create button**

Find the create button at the top of the dashboard header (around line 47):

```swift
                    Button(action: { showingCreateMenu = true }) {
                        Image(systemName: "plus.circle.fill")
```

Add `.tourTarget(.merchantCreateButton)` to that button:

```swift
                    Button(action: { showingCreateMenu = true }) {
                        Image(systemName: "plus.circle.fill")
                            ...
                    }
                    .tourTarget(.merchantCreateButton)
```

- [ ] **Step 7: Modify MerchantDashboardView.swift — add .tourTarget on stats HStack**

Find the HStack containing the StatCard rows (around line 39):

```swift
                        StatCard(icon: "eye.fill", title: localization.t("merchant_views_today"), value: "\(todayImpressions)", color: .blue)
                        StatCard(icon: "hand.tap.fill", title: localization.t("merchant_clicks_today"), value: "\(todayClicks)", color: .appPrimary)
                        StatCard(icon: "heart.fill", title: localization.t("merchant_favorites"), value: "\(todayFavorites)", color: .red)
                        StatCard(icon: "fork.knife", title: localization.t("merchant_active_menus"), value: "\(menus.filter { $0.isToday }.count)", color: .green)
```

These are inside an HStack. Add `.tourTarget(.merchantStatsGrid)` to that HStack:

```swift
                        HStack {
                            StatCard(icon: "eye.fill", ...)
                            StatCard(icon: "hand.tap.fill", ...)
                            StatCard(icon: "heart.fill", ...)
                            StatCard(icon: "fork.knife", ...)
                        }
                        .tourTarget(.merchantStatsGrid)
```

Note: Read the actual surrounding code in `MerchantDashboardView.swift` around line 38-43 to see the exact HStack or VStack wrapping the StatCards, and apply `.tourTarget(.merchantStatsGrid)` to that container.

- [ ] **Step 8: Build and verify**

```bash
cd "Zampa-iOS" && xcodebuild -scheme Zampa -destination 'platform=iOS Simulator,name=iPhone 16 Pro Max' build 2>&1 | grep -E "error:|BUILD"
```
Expected: `** BUILD SUCCEEDED **`

- [ ] **Step 9: Manual smoke test on simulator**

1. Launch the app in iPhone 16 Pro Max simulator
2. Log in with a test account (or create one) — confirm the onboarding pager appears
3. Complete the pager → confirm the tour overlay appears with step 1 (feed card highlighted)
4. Tap "Siguiente" through all 4 steps — confirm each element gets spotlighted
5. Verify "¡Listo!" appears on step 4 and the overlay disappears
6. Log out and log back in → confirm the tour does NOT appear again

- [ ] **Step 10: Commit**

```bash
git add "Zampa-iOS/Zampa/App/ContentView.swift" \
        "Zampa-iOS/Zampa/Features/Feed/MainTabView.swift" \
        "Zampa-iOS/Zampa/Features/Feed/FeedView.swift" \
        "Zampa-iOS/Zampa/Features/Merchant/MerchantDashboardView.swift"
git commit -m "feat(tour): integrate overlay tour into iOS MainTabView, FeedView, and MerchantDashboardView"
```

---

## Task 6: Android TourStep + TourViewModel

**Files:**
- Create: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/tour/TourStep.kt`
- Create: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/tour/TourViewModel.kt`

- [ ] **Step 1: Create TourStep.kt**

```kotlin
package com.sozolab.zampa.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sozolab.zampa.R

enum class TourTarget {
    FEED_CARD, FILTER_BUTTON, MAP_TOGGLE, FAVORITES_TAB,
    MERCHANT_DASHBOARD_TAB, MERCHANT_CREATE_BUTTON, MERCHANT_STATS_GRID
}

data class TourBounds(val offset: Offset, val size: Size)

data class TourStep(
    val target: TourTarget,
    val titleRes: Int,
    val descRes: Int
) {
    companion object {
        val clientSteps = listOf(
            TourStep(TourTarget.FEED_CARD,       R.string.tour_feed_title,                R.string.tour_feed_desc),
            TourStep(TourTarget.FILTER_BUTTON,   R.string.tour_filters_title,             R.string.tour_filters_desc),
            TourStep(TourTarget.MAP_TOGGLE,      R.string.tour_map_title,                 R.string.tour_map_desc),
            TourStep(TourTarget.FAVORITES_TAB,   R.string.tour_favorites_title,           R.string.tour_favorites_desc),
        )

        val merchantSteps = listOf(
            TourStep(TourTarget.MERCHANT_DASHBOARD_TAB,  R.string.tour_merchant_dashboard_title, R.string.tour_merchant_dashboard_desc),
            TourStep(TourTarget.MERCHANT_CREATE_BUTTON,  R.string.tour_merchant_create_title,    R.string.tour_merchant_create_desc),
            TourStep(TourTarget.MERCHANT_STATS_GRID,     R.string.tour_merchant_stats_title,     R.string.tour_merchant_stats_desc),
        )
    }
}
```

- [ ] **Step 2: Create TourViewModel.kt**

```kotlin
package com.sozolab.zampa.ui.tour

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TourState(
    val isActive: Boolean = false,
    val currentStepIndex: Int = 0,
    val steps: List<TourStep> = emptyList(),
    val bounds: Map<TourTarget, TourBounds> = emptyMap()
) {
    val currentStep: TourStep? get() = steps.getOrNull(currentStepIndex)
    val isLastStep: Boolean get() = currentStepIndex == steps.size - 1
    val currentBounds: TourBounds? get() = currentStep?.let { bounds[it.target] }
}

@HiltViewModel
class TourViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(TourState())
    val state: StateFlow<TourState> = _state.asStateFlow()

    fun start(isMerchant: Boolean) {
        val steps = if (isMerchant) TourStep.merchantSteps else TourStep.clientSteps
        _state.update { it.copy(isActive = true, currentStepIndex = 0, steps = steps) }
    }

    fun next() {
        val s = _state.value
        if (s.currentStepIndex < s.steps.size - 1) {
            _state.update { it.copy(currentStepIndex = it.currentStepIndex + 1) }
        } else {
            _state.update { it.copy(isActive = false) }
        }
    }

    fun skip() {
        _state.update { it.copy(isActive = false) }
    }

    fun registerBounds(target: TourTarget, bounds: TourBounds) {
        _state.update { it.copy(bounds = it.bounds + (target to bounds)) }
    }
}
```

- [ ] **Step 3: Build to confirm compilation**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/tour/"
git commit -m "feat(tour): add Android TourStep model and TourViewModel"
```

---

## Task 7: Android TourOverlay Composable

**Files:**
- Create: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/tour/TourOverlay.kt`

- [ ] **Step 1: Create TourOverlay.kt**

```kotlin
package com.sozolab.zampa.ui.tour

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.sozolab.zampa.R
import com.sozolab.zampa.ui.theme.Primary

@Composable
fun TourOverlay(
    state: TourState,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    if (!state.isActive) return
    val step = state.currentStep ?: return
    val bounds = state.currentBounds ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        // Dark overlay with spotlight cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(color = Color(0xC7000000))
            drawSpotlight(bounds)
        }

        // Tooltip
        TourTooltip(
            step = step,
            stepIndex = state.currentStepIndex,
            totalSteps = state.steps.size,
            isLast = state.isLastStep,
            bounds = bounds,
            onNext = onNext,
            onSkip = onSkip
        )
    }
}

private fun DrawScope.drawSpotlight(bounds: TourBounds) {
    drawRoundRect(
        color = Color.Transparent,
        blendMode = BlendMode.Clear,
        topLeft = Offset(bounds.offset.x - 6f, bounds.offset.y - 6f),
        size = Size(bounds.size.width + 12f, bounds.size.height + 12f),
        cornerRadius = CornerRadius(12.dp.toPx())
    )
}

@Composable
private fun TourTooltip(
    step: TourStep,
    stepIndex: Int,
    totalSteps: Int,
    isLast: Boolean,
    bounds: TourBounds,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val screenWidthDp = configuration.screenWidthDp.dp

    val showAbove = bounds.offset.y + bounds.size.height > screenHeightPx * 0.55f

    val targetMidXDp: Dp = with(density) { (bounds.offset.x + bounds.size.width / 2).toDp() }
    val tooltipWidth = 260.dp
    val tooltipOffsetX = (targetMidXDp - tooltipWidth / 2).coerceIn(8.dp, screenWidthDp - tooltipWidth - 8.dp)
    val targetBottomDp: Dp = with(density) { (bounds.offset.y + bounds.size.height).toDp() }
    val targetTopDp: Dp = with(density) { bounds.offset.y.toDp() }
    val tooltipOffsetY = if (showAbove) targetTopDp - 148.dp else targetBottomDp + 8.dp

    Box(
        modifier = Modifier
            .offset(x = tooltipOffsetX, y = tooltipOffsetY)
            .width(tooltipWidth)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(step.titleRes),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Text(
                        "${stepIndex + 1} / $totalSteps",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Text(
                    stringResource(step.descRes),
                    fontSize = 12.sp,
                    color = Color(0xFF555555),
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onSkip) {
                        Text(
                            stringResource(R.string.tour_skip),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isLast) stringResource(R.string.tour_finish)
                                   else stringResource(R.string.tour_next),
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to confirm compilation**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/tour/TourOverlay.kt"
git commit -m "feat(tour): add Android TourOverlay composable"
```

---

## Task 8: Android Integration (MainScreen + FeedScreen + DashboardScreen)

**Files:**
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/FeedScreen.kt`
- Modify: `Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt`

- [ ] **Step 1: Add imports to MainScreen.kt**

Add these imports at the top of MainScreen.kt (after the existing imports):

```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.sozolab.zampa.ui.tour.TourBounds
import com.sozolab.zampa.ui.tour.TourOverlay
import com.sozolab.zampa.ui.tour.TourTarget
import com.sozolab.zampa.ui.tour.TourViewModel
```

- [ ] **Step 2: Add TourViewModel to MainScreen composable**

Inside the `MainScreen` composable, add after the existing `val mainViewModel: MainViewModel = hiltViewModel()` line:

```kotlin
    val tourViewModel: TourViewModel = hiltViewModel()
    val tourState by tourViewModel.state.collectAsState()
```

- [ ] **Step 3: Start the tour after onboarding**

Add a `LaunchedEffect` after the existing `LaunchedEffect(needsLocationPrompt)` block:

```kotlin
    LaunchedEffect(currentUser, showOnboarding) {
        if (currentUser != null && !showOnboarding) {
            val uid = currentUser!!.id
            if (!prefs.getBoolean("hasSeenTour_$uid", false)) {
                kotlinx.coroutines.delay(400)
                tourViewModel.start(isMerchant = isMerchant)
            }
        }
    }
```

- [ ] **Step 4: Add TourOverlay to the root Box**

Find the root `Box` in the main content section (after the `if (showOnboarding) { ... return }` block). The Box wraps the tab content and bottom navigation bar. Add `TourOverlay` at the end of this Box, before the closing brace:

```kotlin
        // At the end of the root Box, before the closing brace:
        if (tourState.isActive) {
            TourOverlay(
                state = tourState,
                onNext = {
                    if (tourState.isLastStep) {
                        currentUser?.id?.let { uid ->
                            prefs.edit().putBoolean("hasSeenTour_$uid", true).apply()
                        }
                    }
                    tourViewModel.next()
                },
                onSkip = {
                    currentUser?.id?.let { uid ->
                        prefs.edit().putBoolean("hasSeenTour_$uid", true).apply()
                    }
                    tourViewModel.skip()
                }
            )
        }
```

- [ ] **Step 5: Register tab bounds in the custom tab bar**

In the `tabs.forEach { tab ->` loop inside the custom bottom navigation `Row`, add `onGloballyPositioned` to the `Column` of the FAVORITES_TAB and MERCHANT_DASHBOARD_TAB:

```kotlin
            tabs.forEach { tab ->
                val selected = selectedTab == tab
                val tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(...)
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .then(
                            when (tab) {
                                Tab.FAVORITES -> Modifier.onGloballyPositioned { coords ->
                                    val pos = coords.positionInWindow()
                                    tourViewModel.registerBounds(
                                        TourTarget.FAVORITES_TAB,
                                        TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                    )
                                }
                                Tab.DASHBOARD -> Modifier.onGloballyPositioned { coords ->
                                    val pos = coords.positionInWindow()
                                    tourViewModel.registerBounds(
                                        TourTarget.MERCHANT_DASHBOARD_TAB,
                                        TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                    )
                                }
                                else -> Modifier
                            }
                        )
                ) { ... }
            }
```

- [ ] **Step 6: Pass tourViewModel to FeedScreen and DashboardScreen**

FeedScreen and DashboardScreen need access to `tourViewModel` to register their element bounds. Update the `when (selectedTab)` content section in `MainScreen`:

```kotlin
                Tab.FEED -> {
                    FeedScreen(
                        modifier = Modifier.haze(hazeState),
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToMerchant = onNavigateToMerchant,
                        onNavigateToProfile = { selectedTab = Tab.PROFILE },
                        tourViewModel = tourViewModel
                    )
                }
                Tab.DASHBOARD -> {
                    DashboardScreen(
                        modifier = Modifier.haze(hazeState),
                        tourViewModel = tourViewModel
                    )
                }
```

- [ ] **Step 7: Modify FeedScreen.kt — add tourViewModel parameter and register bounds**

Add `tourViewModel: TourViewModel? = null` parameter to `FeedScreen`:

```kotlin
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToMerchant: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    tourViewModel: TourViewModel? = null
)
```

**Map toggle (line ~220):** The `IconButton` for toggling `viewMode` has `modifier = Modifier.size(44.dp).background(...)`. Add `.onGloballyPositioned` to that modifier chain:

```kotlin
            IconButton(
                onClick = {
                    viewMode = if (viewMode == FeedViewMode.LIST) FeedViewMode.MAP else FeedViewMode.LIST
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (viewMode == FeedViewMode.MAP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp)
                    )
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tourViewModel?.registerBounds(
                            TourTarget.MAP_TOGGLE,
                            TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        )
                    }
            ) { ... }
```

**Filter button (line ~240):** The filter button is wrapped in a `Box(modifier = Modifier.padding(end = 20.dp))`. Add `.onGloballyPositioned` to that Box:

```kotlin
            Box(
                modifier = Modifier
                    .padding(end = 20.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tourViewModel?.registerBounds(
                            TourTarget.FILTER_BUTTON,
                            TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        )
                    }
            ) {
                IconButton(
                    onClick = { showFilterSheet = true },
                    ...
                ) { ... }
                if (activeFilters.isActive) { Badge(...) }
            }
```

**First card (line ~342):** In the `LazyColumn`, the `items(sortedMenus) { menu -> MenuCard(...) }` block wraps each card. Add a conditional `onGloballyPositioned` to the `MenuCard` modifier:

```kotlin
                    items(sortedMenus) { menu ->
                        MenuCard(
                            menu = menu,
                            merchantMap = merchantMap,
                            onCardClick = { onNavigateToDetail(menu.id) },
                            onMerchantClick = { onNavigateToMerchant(menu.businessId) },
                            modifier = Modifier.then(
                                if (menu == sortedMenus.firstOrNull())
                                    Modifier.onGloballyPositioned { coords ->
                                        val pos = coords.positionInWindow()
                                        tourViewModel?.registerBounds(
                                            TourTarget.FEED_CARD,
                                            TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                                        )
                                    }
                                else Modifier
                            )
                        )
                        ...
                    }
```

Note: If `MenuCard` does not currently accept a `modifier` parameter, add `modifier: Modifier = Modifier` to its signature and apply it to its root composable.

- [ ] **Step 8: Modify DashboardScreen.kt — add tourViewModel parameter and register bounds**

Add `tourViewModel: TourViewModel? = null` parameter to `DashboardScreen`:

```kotlin
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    tourViewModel: TourViewModel? = null
)
```

**Stats grid (line ~85):** The outer `Column(verticalArrangement = Arrangement.spacedBy(12.dp))` wraps two `Row`s of `StatCard`. Add `onGloballyPositioned` to it:

```kotlin
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        tourViewModel?.registerBounds(
                            TourTarget.MERCHANT_STATS_GRID,
                            TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                        )
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ... }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ... }
                }
            }
```

**Create button (line ~124):** The `Button(onClick = { showCreateSheet = true }, modifier = Modifier.fillMaxWidth().height(60.dp)...)`. Add `.onGloballyPositioned` to its modifier chain:

```kotlin
            item {
                Button(
                    onClick = { showCreateSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(...)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            tourViewModel?.registerBounds(
                                TourTarget.MERCHANT_CREATE_BUTTON,
                                TourBounds(Offset(pos.x, pos.y), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
                            )
                        },
                    shape = RoundedCornerShape(16.dp),
                    ...
                ) { ... }
            }
```

- [ ] **Step 9: Build and verify**

```bash
cd "Zampa-Android" && ./gradlew assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Run existing tests**

```bash
cd "Zampa-Android" && ./gradlew test 2>&1 | tail -10
```
Expected: All 5 existing tests pass.

- [ ] **Step 11: Manual smoke test on emulator**

1. Launch on Pixel 6 emulator
2. Log in with a test account that has `hasSeenOnboarding_uid = true` but no `hasSeenTour_uid` key
3. Confirm the tour overlay appears with step 1 (feed card spotlight)
4. Tap through all steps, verify each element gets spotlighted
5. On last step, tap "¡Listo!" — confirm overlay disappears and `hasSeenTour_uid = true` is written
6. Restart app — confirm tour does NOT reappear

- [ ] **Step 12: Commit**

```bash
git add "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/main/MainScreen.kt" \
        "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/feed/FeedScreen.kt" \
        "Zampa-Android/app/src/main/java/com/sozolab/zampa/ui/merchant/DashboardScreen.kt"
git commit -m "feat(tour): integrate overlay tour into Android MainScreen, FeedScreen, and DashboardScreen"
```

---

## Task 9: Android TourStep Unit Tests

**Files:**
- Modify: `Zampa-Android/app/src/test/java/com/sozolab/zampa/ModelsTest.kt`

- [ ] **Step 1: Add TourStep tests to ModelsTest.kt**

Add after the last `@Test` function in `ModelsTest.kt`:

```kotlin
    @Test
    fun tourStep_clientSteps_hasFourSteps() {
        assertEquals(4, TourStep.clientSteps.size)
    }

    @Test
    fun tourStep_merchantSteps_hasThreeSteps() {
        assertEquals(3, TourStep.merchantSteps.size)
    }

    @Test
    fun tourStep_clientSteps_startsWithFeedCard() {
        assertEquals(TourTarget.FEED_CARD, TourStep.clientSteps.first().target)
    }

    @Test
    fun tourStep_clientSteps_endsWithFavoritesTab() {
        assertEquals(TourTarget.FAVORITES_TAB, TourStep.clientSteps.last().target)
    }

    @Test
    fun tourStep_merchantSteps_startsWithDashboardTab() {
        assertEquals(TourTarget.MERCHANT_DASHBOARD_TAB, TourStep.merchantSteps.first().target)
    }

    @Test
    fun tourStep_clientSteps_targetsAreDistinct() {
        val targets = TourStep.clientSteps.map { it.target }
        assertEquals(targets.size, targets.distinct().size)
    }

    @Test
    fun tourStep_merchantSteps_targetsAreDistinct() {
        val targets = TourStep.merchantSteps.map { it.target }
        assertEquals(targets.size, targets.distinct().size)
    }
```

Add the required import at the top of ModelsTest.kt:

```kotlin
import com.sozolab.zampa.ui.tour.TourStep
import com.sozolab.zampa.ui.tour.TourTarget
```

- [ ] **Step 2: Run tests to confirm all pass**

```bash
cd "Zampa-Android" && ./gradlew test 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL` with 12 tests passing (5 existing + 7 new).

- [ ] **Step 3: Commit**

```bash
git add "Zampa-Android/app/src/test/java/com/sozolab/zampa/ModelsTest.kt"
git commit -m "test(tour): add TourStep unit tests"
```
