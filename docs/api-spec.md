## EatOut – Referencia de datos Firebase

> El backend es **100% Firebase**. No existe servidor REST propio.
> Este documento describe colecciones Firestore, operaciones de Storage y Cloud Functions disponibles.

---

### Autenticación (Firebase Auth)

Las apps usan directamente el SDK de Firebase Auth. No hay endpoint propio.

| Operación | SDK call (iOS / Android) |
|---|---|
| Registro email/password | `Auth.auth().createUser(withEmail:password:)` / `auth.createUserWithEmailAndPassword(email, password)` |
| Login | `Auth.auth().signIn(withEmail:password:)` / `auth.signInWithEmailAndPassword(email, password)` |
| Logout | `Auth.auth().signOut()` / `auth.signOut()` |
| Sesión activa | `Auth.auth().currentUser` / `auth.currentUser` |

El **rol** del usuario (`COMERCIO` o `CLIENTE`) se almacena como Custom Claim y también en el documento `users/{uid}`.

---

### Colecciones Firestore

#### `users/{uid}`

```
{
  uid: string,
  email: string,
  name: string,
  phone: string?,
  role: "COMERCIO" | "CLIENTE",
  createdAt: Timestamp
}
```

#### `businesses/{businessId}`

```
{
  uid: string,               // mismo que users/{uid} del comercio
  name: string,
  phone: string,
  address: string,
  lat: number,
  lng: number,
  schedule: string,
  acceptsReservations: boolean,
  coverPhotoURL: string?,
  cuisineTypes: string[],    // referencias a cuisineTypes
  createdAt: Timestamp,
  updatedAt: Timestamp
}
```

#### `dailyOffers/{offerId}`

```
{
  businessId: string,
  title: string,
  description: string?,
  price: number,
  photoURLs: string[],       // URLs de Firebase Storage
  isActive: boolean,
  expiresAt: Timestamp,
  createdAt: Timestamp
}
```

#### `favorites/{favoriteId}`

```
{
  customerId: string,        // uid del cliente
  businessId: string,
  notificationsEnabled: boolean,
  createdAt: Timestamp
}
```

#### `subscriptions/{subscriptionId}`

```
{
  businessId: string,
  type: "MONTHLY" | "YEARLY",
  status: "ACTIVE" | "CANCELLED" | "EXPIRED",
  startDate: Timestamp,
  endDate: Timestamp,
  createdAt: Timestamp
}
```

#### `metrics/{metricId}`

```
{
  offerId: string,
  businessId: string,
  views: number,
  clicks: { call: number, directions: number, share: number },
  date: string              // "YYYY-MM-DD"
}
```

#### `deviceTokens/{tokenId}`

```
{
  userId: string,
  token: string,            // FCM token
  platform: "ios" | "android",
  updatedAt: Timestamp
}
```

#### `cuisineTypes/{typeId}`

```
{
  name: string,             // "tapas", "italiana", "japonesa", etc.
  slug: string
}
```

---

### Firebase Storage

Rutas de almacenamiento:

| Ruta | Descripción |
|---|---|
| `businesses/{businessId}/cover.jpg` | Foto de portada del comercio |
| `offers/{offerId}/{index}.jpg` | Fotos de una oferta diaria |

Las apps suben directamente con el SDK de Storage. Las URLs de descarga se guardan en los documentos Firestore correspondientes.

---

### Cloud Functions

Definidas en `functions/index.js`. Se despliegan con `firebase deploy --only functions`.

#### `expireMenus` (Scheduled – cada hora)

Busca documentos en `dailyOffers` donde `isActive == true` y `expiresAt < now` y los marca como `isActive: false`.

#### `onMenuPublished` (Firestore trigger – onCreate en `dailyOffers/{offerId}`)

Al crear una nueva oferta:
1. Busca documentos en `favorites` con `businessId` coincidente y `notificationsEnabled == true`.
2. Obtiene tokens FCM de los usuarios desde `deviceTokens`.
3. Envía notificación push con título "¡Nuevo menú publicado!" y deep link al detalle.

---

### Firestore Security Rules (orientativas)

```
// Solo el propietario puede escribir su negocio
match /businesses/{businessId} {
  allow read: if true;
  allow write: if request.auth.uid == resource.data.uid;
}

// Solo el cliente propietario puede ver/modificar sus favoritos
match /favorites/{favId} {
  allow read, write: if request.auth.uid == resource.data.customerId;
}

// Las ofertas las crea/edita el comercio propietario
match /dailyOffers/{offerId} {
  allow read: if true;
  allow write: if request.auth.uid == get(/databases/$(database)/documents/businesses/$(resource.data.businessId)).data.uid;
}
```

---

### Convenciones

- Todos los IDs son generados por Firestore (`auto-id`) salvo `users/{uid}` que usa el UID de Firebase Auth.
- Los timestamps usan `FieldValue.serverTimestamp()` para garantizar consistencia.
- Las apps nunca calculan métricas directamente; incrementan contadores via Cloud Functions o transacciones Firestore.
