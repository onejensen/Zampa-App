## Zampa – Arquitectura Aplicaciones Nativas

### Objetivos

- Desarrollar apps nativas para iOS y Android sin frameworks híbridos.
- Compartir especificación de dominio vía documentación, no código.
- Garantizar paridad funcional y coordinación entre plataformas.
- Todo el backend vive en **Firebase**: no hay servidor REST propio ni base de datos relacional.

---

### iOS

- **Lenguaje/Frameworks**: Swift 5.9, SwiftUI, Combine, async/await, MapKit/GoogleMaps SDK, UserNotifications.
- **Arquitectura**: Clean MVVM modular.
  - `App`: punto de entrada, configuración Firebase, routing inicial.
  - `Features`: módulos independientes (Auth, MerchantProfile, MenuPublisher, Feed, Favorites, Subscription, Analytics).
  - `Core`: modelos de dominio, utilidades, configuración de entorno.
  - `Services`: FirebaseService, AuthService, MerchantService, MenuService, FeedService, FavoritesService, SubscriptionService, ImageUploadService, LocationManager, PushManager.
- **Flujo App**:
  1. Launch → verifica estado Auth (Firebase) → decide entre auth flow o main tabs.
  2. Onboarding cliente: permisos ubicación + preferencias.
  3. Merchant: dashboard con pestañas (publicar, historial, stats, plan).
- **Persistencia local**:
  - Keychain para UID y tokens de sesión Firebase.
  - SwiftData/UserDefaults para cache ligera de preferencias y feed.
- **Firebase SDKs**:
  - `FirebaseAuth`: registro, login, sesión persistente.
  - `FirebaseFirestore`: lecturas/escrituras en tiempo real con listeners.
  - `FirebaseStorage`: upload de imágenes con URLs de descarga.
  - `FirebaseMessaging`: registro de device token FCM, recepción de push.
- **Multimedia**:
  - Photo picker + cámara nativa.
  - Upload directo a Firebase Storage con barra de progreso.
- **Push**:
  - APNs + FCM. `PushManager` registra token en colección `deviceTokens` y maneja deep links.
- **Testing**:
  - Unit tests de ViewModels y Services con mocks de Firebase.
  - Snapshot tests para pantallas clave.

---

### Android

- **Lenguaje/Frameworks**: Kotlin, Jetpack Compose, Coroutines/Flow, Navigation Compose, Hilt, Google Maps SDK, FCM.
- **Arquitectura**: Clean MVVM.
  - `ui/auth`, `ui/feed`, `ui/merchant`, `ui/favorites`, `ui/profile`, `ui/subscription`, `ui/stats`: ViewModels + Screens.
  - `data/model`: modelos de dominio y DTOs para Firestore.
  - `data/`: repositorios que encapsulan acceso a Firebase SDKs.
  - `di/`: módulos Hilt para inyección de Firebase instances.
- **Persistencia local**:
  - DataStore para preferencias y UID de sesión.
  - Room (opcional) para cache de feed/favoritos offline.
- **Firebase SDKs**:
  - `firebase-auth-ktx`: autenticación.
  - `firebase-firestore-ktx`: consultas con Flow y snapshots en tiempo real.
  - `firebase-storage-ktx`: upload de imágenes.
  - `firebase-messaging`: registro de token FCM, recepción de notificaciones.
- **Repositorios**:
  - `AuthRepository`, `MerchantRepository`, `MenuRepository`, `FeedRepository`, `FavoritesRepository`, `SubscriptionRepository` — todos sobre Firestore SDK.
  - `LocationService` con FusedLocationProvider.
  - `PushService` (FCM) + WorkManager para retry de registro de token.
- **UI Flow**:
  - Single-activity Compose con NavigationGraph.
  - Bottom nav (feed/favorites/perfil) para clientes; tabset diferente para merchants.
- **Testing**:
  - Unit tests (JUnit + Turbine para flows).
  - Compose UI tests para flows críticos.

---

### Backend & Infraestructura (Firebase)

- **Firebase Authentication**: gestión de identidad y roles (Custom Claims).
- **Cloud Firestore**: base de datos principal. Ver `ERD.md` y `docs/api-spec.md` para colecciones.
- **Cloud Storage**: almacenamiento de imágenes de ofertas y portadas.
- **Cloud Functions** (`functions/index.js`):
  - `expireMenus`: scheduler cada hora para desactivar menús vencidos.
  - `onMenuPublished`: trigger en nueva oferta → notificación push a favoritos con suscripción activa.
  - Futuras: actualización de métricas, gestión de estado de suscripciones.
- **Firebase Cloud Messaging (FCM)**: notificaciones push iOS y Android.
- **Pagos**: integración futura con Stripe mediante Cloud Functions (webhook → actualiza `subscriptions` en Firestore).

---

### Coordinación y Versionado

- `ERD.md` y `docs/api-spec.md` como contrato de datos entre apps y Firebase.
- Firestore Security Rules como capa de autorización (no lógica de negocio en cliente).
- CI/CD:
  - iOS: Xcode Cloud / Fastlane.
  - Android: Gradle + GitHub Actions.
  - Functions: `firebase deploy --only functions` con tests previos.
- Telemetría: Firebase Crashlytics + Analytics en ambas apps.

---

### Próximos Pasos Recomendados

1. Implementar capa de Services en iOS (AuthService, MenuService) usando Firebase SDK.
2. Implementar Repositories en Android sobre Firestore con Flow.
3. Desplegar y probar Cloud Functions en emulador local (`firebase emulators:start`).
4. Configurar Firestore Security Rules por rol y propiedad.
5. Integrar autenticación y flujo onboarding como primer sprint compartido.
