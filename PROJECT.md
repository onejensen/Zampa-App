# PROJECT.md – Zampa

## Visión del proyecto

Zampa es una app móvil nativa para **iOS** y **Android** que conecta a bares y restaurantes de **menú/plato del día** con clientes que quieren decidir rápidamente dónde comer o cenar cerca.  
El énfasis está en la **simplicidad para el comercio** (subir una foto de la pizarra/carta en segundos) y en la **rapidez para el cliente** (ver de un vistazo las opciones cercanas y filtrarlas).

La plataforma se apoya en **Firebase** para autenticación, base de datos, almacenamiento y notificaciones.

---

## Objetivos principales

- Permitir que los establecimientos publiquen sus **ofertas diarias** en menos de 1 minuto.
- Mostrar al cliente menús/platos del día por **proximidad**, tipo de cocina y zona.
- Ofrecer un modelo de **suscripción** (mensual/anual) para comercios con funcionalidades premium.
- Mantener una experiencia coherente entre iOS y Android, respetando patrones nativos de cada plataforma.

---

## Stack técnico

### Cliente móvil

- **iOS (nativo)**  
  - Lenguaje: Swift  
  - UI: SwiftUI  
  - Arquitectura: MVVM por módulo

- **Android (nativo)**  
  - Lenguaje: Kotlin  
  - UI: Jetpack Compose  
  - Arquitectura: MVVM por módulo

### Backend / infraestructura (Firebase)

- **Firebase Authentication**  
  - Autenticación por email/password (MVP).  
  - Usuario con rol: `COMERCIO` o `CLIENTE`.

- **Cloud Firestore** (modelo documento/colección)  
  - Colecciones principales: `users`, `businesses`, `customers`, `dailyOffers`, `favorites`, `subscriptions`, `metrics`, `deviceTokens`, `notifications`, `cuisineTypes`, `businessCuisine`.  
  - Reglas de seguridad para aislar datos según rol y propiedad.

- **Cloud Storage**  
  - Almacenamiento de fotos de ofertas diarias y fotos de portada de comercio.

- **Cloud Functions**  
  - Lógica de negocio que no debe residir en el cliente:
    - Actualizar métricas de vistas.  
    - Disparar notificaciones push a favoritos al publicar una nueva oferta.  
    - Gestión de estado de suscripciones (alta, expiración, etc.).

- **Firebase Cloud Messaging (FCM)**  
  - Envío de notificaciones push a dispositivos iOS y Android.

---

## Lado del comercio

### Publicación de oferta diaria

**Flujo principal:**

1. Comercio inicia sesión (usuario con rol `COMERCIO`).
2. Desde la pantalla “Publicar menú del día”, selecciona:
   - Una foto (obligatoria en MVP), tomada con la cámara o desde la galería.  
   - Precio total del menú.  
   - Descripción corta (opcional).
3. Envía la publicación:
   - La app sube la imagen a **Firebase Storage**.  
   - Crea un documento `dailyOffer` con referencia al comercio y URL de la foto.  
   - Si el comercio tiene suscripción activa, permite subir varias fotos en la misma oferta.

Restricciones:

- Plan básico: 1 foto por oferta diaria (o 1 oferta activa por día, definible por negocio).
- Plan con suscripción: varias fotos asociadas a la oferta diaria.

### Perfil público del comercio

Cada comercio tiene un perfil editable:

- Campos mínimos:
  - Nombre del establecimiento.  
  - Teléfono.  
  - Dirección en texto.  
  - Coordenadas `lat` / `lng` (obtenidas a partir de la dirección, usando Google Maps/Places desde el cliente o función).  
  - Horario (texto).  
  - Flag `acceptsReservations` (bool).  
  - Tipos de cocina (referencias a `cuisineTypes`).  
  - Foto de portada (URL en Storage).

Uso:

- Se muestra en la vista de detalle desde el lado cliente.  
- Alimenta las funciones de “Llamar” y “Cómo llegar”.  
- Es la base para favoritos y para el destacado en listados.

### Estadísticas de interacción

El comercio puede ver datos básicos:

- Nº de **vistas** de sus ofertas diarias.  
- Nº de **clientes que lo tienen en favoritos**.

Implementación orientativa:

- Firestore colección `metrics` con documentos agregados por oferta y/o por día.  
- Cloud Functions que incrementan contadores al registrar eventos (vista de oferta, nuevo favorito).

### Suscripciones (mensual/anual)

Modelo simple:

- Documento en `subscriptions` enlazado a `businessId`:
  - Tipo (`MONTHLY`, `YEARLY`…).  
  - Estado (`ACTIVE`, `CANCELLED`, `EXPIRED`…).  
  - Fecha inicio y fecha fin.

Comportamiento:

- Si la suscripción está activa:
  - El comercio puede subir más fotos por oferta.  
  - Su perfil aparece destacado en listados (orden o estilo visual).  
  - Se envían notificaciones push a los clientes que lo tienen en favoritos al publicar nueva oferta.

La lógica de estado de suscripción puede vivir en Firestore + Cloud Functions (por ejemplo, al confirmar pago externo).

---

## Lado del cliente

### Descubrir menús por proximidad

Pantalla de inicio del cliente:

- Lista de ofertas del día (`dailyOffers` activos) ordenadas por distancia a la ubicación actual.  
- Selector de rango de distancia (por ejemplo, 0.5 km a 10 km).  
- Cada tarjeta muestra:
  - Foto principal.  
  - Nombre del comercio.  
  - Distancia aproximada.  
  - Precio.  
  - Tipo de cocina.  
  - Etiqueta de “Destacado” si la suscripción del comercio está activa.

Implementación:

- La app obtiene ubicación (permiso de GPS).  
- Consulta Firestore filtrando por área aproximada (bounding box) + cálculo de distancia en el cliente o mediante Cloud Function si se necesita más precisión.  
- Soporte de estado “sin permisos de localización” con búsqueda manual.

### Filtros y búsqueda

- Filtro por **tipo de cocina**:
  - Lista de tipos obtenidos de `cuisineTypes`.  
  - Se filtran las ofertas por los comercios que tengan esos tipos asociados.

- Búsqueda por **zona**:
  - Búsqueda de dirección o ciudad (uso de Google Places en cliente).  
  - La ubicación seleccionada se usa como centro para ordenar ofertas por proximidad.

- Búsqueda por **nombre de comercio**:
  - Búsqueda en texto sobre campo `name` de los comercios.

### Interacciones rápidas

- **Llamada directa**:
  - Botón “Llamar” que lanza `tel:` con el número configurado en el perfil del comercio.

- **Cómo llegar**:
  - Botón “Cómo llegar” que abre Google Maps (u otra app de mapas) con `lat`/`lng` del comercio.

Estas acciones se realizan directamente en el cliente, usando los datos del documento de comercio.

### Favoritos

- El cliente puede marcar un comercio como favorito:
  - Se crea un documento en `favorites` con `customerId` y `businessId`.  
  - Se utiliza para mostrar una lista de favoritos y para notificaciones.

- Lista de favoritos:
  - Vista específica que consulta `favorites` del usuario y obtiene datos de cada comercio y su última oferta.

- Notificaciones:
  - Al publicar una oferta nueva y si el comercio tiene suscripción activa, una Cloud Function busca los `favorites` y envía notificación push a los `deviceTokens` de esos usuarios.

### Funcionalidades opcionales

- **Compartir en redes sociales**:
  - Desde la tarjeta o el detalle de la oferta/perfil, compartir texto + enlace (deep link o web) usando las hojas de compartir nativas de iOS/Android.

---

## Modelo de datos (alto nivel, versión Firebase)

Ver `ERD.md` para el detalle de colecciones y campos.

---

## Roadmap de producto

1. **MVP**  
   - Autenticación comercio/cliente.  
   - Perfil de comercio.  
   - Publicación de oferta diaria (1 foto).  
   - Listado por proximidad (cliente).  
   - Llamar y cómo llegar.  
   - Favoritos sin notificación.

2. **Suscripciones + estadísticas**  
   - Suscripción comercio.  
   - Destacar perfil y múltiples fotos.  
   - Métricas de vistas y favoritos.

3. **Notificaciones y extras**  
   - Notificaciones push a favoritos cuando hay nueva oferta.  
   - Compartir en redes sociales.  
   - Filtros avanzados, búsqueda por zona mejorada.

---

## Criterios generales de aceptación

Un feature está terminado cuando:

- Funciona en **iOS y Android** con comportamiento equivalente.  
- Usa correctamente Firebase (seguridad y estructura de datos).  
- Gestiona estados vacíos, errores y latencia (loaders, mensajes).  
- Ha sido probado en dispositivos reales o emuladores representativos con cuentas de comercio y cliente.
