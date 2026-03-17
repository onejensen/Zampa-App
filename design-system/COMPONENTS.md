# EatOut – Shared Component Specs

## Tokens
All values defined in `tokens.json`. Each platform maps tokens to native types:
- **iOS**: `Color` extensions, `Font` extensions, `CGFloat` constants
- **Android**: `Color()`, `TextStyle`, `Dp` values

## Reusable Components

| Component | iOS | Android | Notes |
|-----------|-----|---------|-------|
| PrimaryButton | `AppDesign.ButtonStyle(isPrimary: true)` | `Button` + `MaterialTheme` | Full-width, rounded corners |
| SecondaryButton | `AppDesign.ButtonStyle(isPrimary: false)` | `OutlinedButton` | Outlined variant |
| Card | `.appCardStyle()` modifier | `Card` composable | Shadow, rounded corners |
| TextField | `CustomTextField` | `OutlinedTextField` | Icon + placeholder |
| CategoryPill | `CategoryPill` view | Custom `FilterChip` | Rounded pill selector |
| MenuCard | In FeedView | In FeedScreen | Photo + title + price + tags |
| ProfileAvatar | PhotosPicker + Circle | Icon placeholder | Photo upload support |

## States (must exist in both platforms)
- **Loading**: `ProgressView` (iOS) / `CircularProgressIndicator` (Android)
- **Empty**: Centered icon + message text
- **Error**: Alert/Snackbar with retry action
- **Offline**: Banner at top indicating no connectivity

## Navigation
- **Tabs**: Feed → Favoritos → Mis Menús (merchant only) → Perfil
- **Detail**: Push/navigate to `MenuDetailView` / `MenuDetailScreen`
- **Modals**: Subscription, Filters, Create Menu
