## Zampa – Backlog MVP

Cada historia incluye objetivo, criterios de aceptación y dependencias destacadas.

> **Nota**: el backend es 100% Firebase. Las "dependencias" referencia SDKs y colecciones Firestore, no endpoints REST.

---

### 1. Registro cliente

- **Objetivo**: crear cuenta de cliente con email/contraseña.
- **Criterios**: validación email y password (>=8 chars), aceptación términos, auto-login tras registro, rol `CLIENTE` guardado en `users/{uid}`.
- **Dependencias**: `FirebaseAuth.createUser`, escritura inicial en `users/{uid}`.

### 2. Registro comercio

- **Objetivo**: alta de comercio.
- **Criterios**: mismo flujo que clientes pero con rol `COMERCIO`, mensaje si email ya existe, redirección a completar perfil tras registro.
- **Dependencias**: `FirebaseAuth.createUser`, escritura inicial en `users/{uid}` con `role: COMERCIO`.

### 3. Completar perfil comercio

- **Objetivo**: datos públicos (teléfono, dirección, horario, cocina, reservas).
- **Criterios**: autocomplete Google Places, mapa preview con lat/lng, validaciones de horario, guardado en `businesses/{uid}`.
- **Dependencias**: Google Places SDK, Firestore `businesses`.

### 4. Publicar menú diario

- **Objetivo**: subir menú del día con foto obligatoria.
- **Criterios**: 1 oferta activa/día en plan free, título/descripción/precio requeridos, upload foto a Firebase Storage, URL guardada en `dailyOffers`.
- **Dependencias**: Firebase Storage upload, Firestore `dailyOffers`, comprobación de suscripción activa en cliente.

### 5. Ver historial/menú actual (merchant)

- **Objetivo**: consultar y gestionar publicaciones propias.
- **Criterios**: lista cronológica filtrada por `businessId`, estados (vigente, expirado), permitir editar/eliminar activos.
- **Dependencias**: Firestore `dailyOffers` query por `businessId`, update/delete de documentos.

### 6. Feed cercano cliente

- **Objetivo**: mostrar menús según distancia.
- **Criterios**: permisos ubicación, slider radio min/max, cards con foto/precio/distancia, paginación cursor.
- **Dependencias**: Location services, Firestore `dailyOffers` con filtro `isActive: true` + bounding box por coordenadas.

### 7. Filtros feed

- **Objetivo**: refinar búsquedas en el feed.
- **Criterios**: filtro por cocina (join con `businesses.cuisineTypes`), precio, zona manual; persistencia temporal en sesión; botón reset.
- **Dependencias**: colección `cuisineTypes`, lógica de filtro en cliente sobre resultados Firestore.

### 8. Detalle oferta

- **Objetivo**: ver info completa + acciones rápidas.
- **Criterios**: galería fotos, descripción, datos del local, botones llamar/direcciones/compartir, toggle favorito.
- **Dependencias**: Firestore `dailyOffers/{offerId}` + `businesses/{businessId}`, deep links Maps.

### 9. Favoritos

- **Objetivo**: guardar comercios y acceder a sus menús.
- **Criterios**: toggle en feed/detalle, sección dedicada con última oferta, opción activar/desactivar notificaciones por local.
- **Dependencias**: Firestore `favorites` (create/delete), query de última oferta activa por `businessId`.

### 10. Notificaciones push seguidores

- **Objetivo**: avisar cuando un comercio favorito publica.
- **Criterios**: app registra token FCM en `deviceTokens` al login, Cloud Function `onMenuPublished` envía push al publicar, deep link a detalle de oferta, usuarios pueden desactivar por local.
- **Dependencias**: APNs/FCM, Cloud Function `onMenuPublished` (ya implementada en `functions/index.js`), colección `deviceTokens`.

### 11. Estadísticas básicas comercio

- **Objetivo**: ver impresiones, favoritos y clics.
- **Criterios**: selector rango día/semana/mes, métricas en tarjetas, datos frescos.
- **Dependencias**: Firestore `metrics` aggregados por `businessId` y `date`, Cloud Function para incrementar contadores.

### 12. Tracking acciones rápidas

- **Objetivo**: registrar clics call/directions/share.
- **Criterios**: antes de ejecutar la acción se incrementa el contador en `metrics/{metricId}`, fallo silencioso sin bloquear la acción.
- **Dependencias**: Firestore `metrics`, `FieldValue.increment()`.

### 13. Suscripción Premium

- **Objetivo**: monetizar con planes Pro.
- **Criterios**: pantalla comparación planes, pago via Stripe (mensual/anual), confirmación de pago actualiza `subscriptions/{businessId}` en Firestore, estado plan visible en perfil.
- **Dependencias**: Stripe SDK, Cloud Function webhook Stripe → escribe en `subscriptions`.

### 14. Destacar perfil / múltiples fotos

- **Objetivo**: beneficios plan Pro visibles.
- **Criterios**: badge "Destacado" en tarjetas del feed si suscripción activa, límite de fotos controlado en cliente según estado de `subscriptions`.
- **Dependencias**: Firestore `subscriptions`, lógica de plan en cliente.

### 15. Caducidad automática menús

- **Objetivo**: limpiar feed diariamente.
- **Criterios**: Cloud Function expira menús cuyo `expiresAt` ha pasado, feed solo muestra `isActive: true`, merchants ven estado `expired` en historial.
- **Dependencias**: Cloud Function `expireMenus` (ya implementada en `functions/index.js`).

### 16. Moderación básica (MVP+)

- **Objetivo**: manejar contenido reportado.
- **Criterios**: usuarios pueden reportar una oferta, admin ve cola en consola Firebase o app interna, puede desactivar oferta, notificar al merchant.
- **Dependencias**: colección `reports` en Firestore, Cloud Function opcional para notificaciones al merchant.

---

### Próximos pasos

1. Estimar esfuerzo/story points y priorizar (MVP vs post-MVP).
2. Alinear estructura de Firestore y Security Rules antes de cada sprint.
3. Generar épicas en la herramienta de gestión preferida (Jira/Notion).
