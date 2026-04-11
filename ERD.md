# ERD.md – Modelo de datos Zampa (Firebase)

Este documento describe el modelo de datos de Zampa para **Firebase**, pensando principalmente en **Cloud Firestore**.  
La estructura está orientada a colecciones y documentos, con referencias claras entre entidades.

---

## Colecciones principales

### 1. `users`

Representa a todos los usuarios autenticados (comercios y clientes).  
Relacionada con Firebase Authentication mediante `uid`.

**Documento tipo:**

- `id` (igual a `auth.uid`)
- `email`
- `role` (`"COMERCIO"` | `"CLIENTE"`)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

---

### 2. `businesses` (comercios)

Perfil de cada comercio.

**Campos:**

- `id` (doc id)
- `userId` (ref → `users/{id}`)
- `name`
- `phone`
- `addressText`
- `location` (GeoPoint `{ latitude, longitude }`)
- `openingHours` (string o estructura)
- `acceptsReservations` (bool)
- `coverPhotoUrl`
- `shortDescription`
- `isHighlighted` (bool, derivado de suscripción activa, opcional)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

---

### 3. `customers` (clientes)

Perfil básico del cliente (opcionalmente minimalista).

**Campos:**

- `id` (doc id)
- `userId` (ref → `users/{id}`)
- `displayName` (opcional)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

---

### 4. `cuisineTypes`

Catálogo de tipos de cocina.

**Campos:**

- `id` (doc id)
- `name` (ej. `"Mediterránea"`, `"Vegana"`)

---

### 5. `businessCuisine`

Relación muchos-a-muchos entre `businesses` y `cuisineTypes`.

**Campos:**

- `id` (doc id)
- `businessId` (ref → `businesses/{id}`)
- `cuisineTypeId` (ref → `cuisineTypes/{id}`)

*(Alternativa: guardar una lista de ids de cocina dentro del propio documento de `businesses`.)*

---

### 6. `dailyOffers`

Ofertas diarias de cada comercio.

**Campos:**

- `id` (doc id)
- `businessId` (ref → `businesses/{id}`)
- `date` (date / timestamp de día)
- `priceTotal` (number)
- `description` (string opcional)
- `isActive` (bool) – para desactivar una oferta sin borrarla
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

---

### 7. `offerPhotos`

Fotos asociadas a una oferta diaria.

**Campos:**

- `id` (doc id)
- `offerId` (ref → `dailyOffers/{id}`)
- `url` (string – Firebase Storage)
- `order` (number)
- `createdAt` (timestamp)

Reglas de negocio:

- Comercios en plan básico: 1 foto por oferta.
- Comercios con suscripción: varias fotos por oferta.

---

### 8. `favorites`

Relación cliente–comercio para favoritos.

**Campos:**

- `id` (doc id)
- `customerId` (ref → `customers/{id}`)
- `businessId` (ref → `businesses/{id}`)
- `createdAt` (timestamp)

Regla:

- Debe ser único por par `(customerId, businessId)` a nivel de negocio (control en el cliente o en Cloud Function).

---

### 9. `subscriptions`

Suscripciones de comercios para funcionalidades premium.

**Campos:**

- `id` (doc id)
- `businessId` (ref → `businesses/{id}`)
- `type` (`"MONTHLY"`, `"YEARLY"`, etc.)
- `status` (`"ACTIVE"`, `"PENDING"`, `"CANCELLED"`, `"EXPIRED"`)
- `startDate` (timestamp)
- `endDate` (timestamp)
- `createdAt` (timestamp)
- `updatedAt` (timestamp)

Uso:

- Determina si el comercio puede:
  - Tener perfil destacado.  
  - Subir múltiples fotos.  
  - Disparar notificaciones push a sus favoritos.

---

### 10. `metrics`

Métricas básicas de interacción.

**Opción 1 (por oferta y día):**

- `id` (doc id)
- `offerId` (ref → `dailyOffers/{id}`)
- `date` (date)
- `views` (number)
- `favoritesCount` (number, opcional – agregada)
- `updatedAt` (timestamp)

Las Cloud Functions incrementan `views` cada vez que un cliente abre la oferta, y se pueden agregar datos de favoritos.

*(Para un MVP, incluso se puede simplificar a un solo documento de métrica por oferta sin desglosar por día.)*

---

### 11. `deviceTokens`

Tokens de dispositivo para notificaciones push.

**Campos:**

- `id` (doc id)
- `userId` (ref → `users/{id}`)
- `platform` (`"IOS"` | `"ANDROID"`)
- `token` (string FCM)
- `createdAt` (timestamp)
- `lastUsedAt` (timestamp)

---

### 12. `notifications`

Histórico de notificaciones generadas por el sistema.

**Campos:**

- `id` (doc id)
- `userId` (ref → `users/{id}`) – receptor
- `businessId` (ref → `businesses/{id}`, opcional)
- `offerId` (ref → `dailyOffers/{id}`, opcional)
- `type` (`"NEW_OFFER_FAVORITE"`, etc.)
- `title`
- `body`
- `read` (bool)
- `createdAt` (timestamp)

Uso:

- Para registro interno y posible representación en un “centro de notificaciones” dentro de la app.

---

## Relaciones (vista conceptual)

- `users` 1–1 `businesses` (cuando `role = COMERCIO`).
- `users` 1–1 `customers` (cuando `role = CLIENTE`).
- `businesses` 1–N `dailyOffers`.
- `dailyOffers` 1–N `offerPhotos`.
- `businesses` N–N `cuisineTypes` mediante `businessCuisine` (o array de ids en `businesses`).
- `customers` N–N `businesses` mediante `favorites`.
- `businesses` 1–N `subscriptions` (sólo se considera una `status = ACTIVE` vigente).
- `dailyOffers` 1–N `metrics` (opcional, si se modela por día).
- `users` 1–N `deviceTokens`.
- `users` 1–N `notifications` (cada notificación apunta opcionalmente a un comercio y/o oferta).

---

## Notas específicas para Firebase

- **IDs**: es recomendable que los `id` de documento coincidan con `uid` de Auth en el caso de `users`, y usar IDs auto generados para el resto.
- **Timestamps**: usar siempre campos `createdAt`/`updatedAt` gestionados por servidor para ordenación y depuración.
- **Reglas de seguridad**:
  - Comercios sólo pueden escribir en sus propios documentos (`businesses`, `dailyOffers`, `offerPhotos`, etc.).
  - Clientes sólo pueden crear/leer sus propios `favorites`, `deviceTokens` y ver `businesses`/`dailyOffers` públicos.
- **Funciones Cloud**:
  - Trigger `onCreate` de `dailyOffers`:  
    - Actualizar métricas.  
    - Buscar `favorites` de ese comercio y enviar notificaciones push si hay suscripción activa.
  - Triggers en `favorites` para mantener contadores agregados si se desean.

