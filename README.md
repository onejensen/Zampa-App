# 🍽️ Zampa — La App del Plato del Día

Zampa es una plataforma móvil nativa diseñada para conectar a restaurantes que ofrecen **menú o plato del día** con clientes locales que buscan opciones rápidas, deliciosas y cercanas.

La plataforma se centra en la **fricción cero**:
- **Comercios:** Toman una foto de su pizarra y la publican en segundos.
- **Clientes:** Descubren qué hay de comer a su alrededor basándose en su ubicación exacta.

---

## 📱 Ecosistema de Aplicaciones

Zampa es una solución multiplataforma que incluye:

- **Zampa iOS:** App nativa desarrollada en **Swift / SwiftUI** siguiendo las *Apple Human Interface Guidelines*.
- **Zampa Android:** App nativa desarrollada en **Kotlin / Jetpack Compose** siguiendo *Material Design 3*.
- **Zampa Landing Page:** Página web promocional para captación de usuarios y comercios.
- **Zampa Backend:** Infraestructura **Serverless** basada íntegramente en **Firebase**.

---

## ✨ Funcionalidades Principales

### Para el Comercio (Merchant)
- **Publicación Instantánea:** Sube el menú del día con una foto y descripción en menos de 1 minuto.
- **Perfil de Negocio:** Gestión de información de contacto, horario, ubicación y tipo de cocina.
- **Dashboard de Estadísticas:** Visualiza cuántas personas han visto tus platos y cuántos te tienen en favoritos.
- **Suscripciones Premium:** Modelo Pro que permite múltiples fotos por oferta, perfil destacado y notificaciones push automáticas a seguidores.

### Para el Cliente (Customer)
- **Descubrimiento por Proximidad:** Listado de ofertas cercanas ordenado por distancia (GPS).
- **Filtros Avanzados:** Busca por tipo de cocina (Italiana, Vegana, Mediterránea...) o nombre de restaurante.
- **Acciones Rápidas:** Llama al restaurante o abre la ruta en Mapas con un solo toque.
- **Favoritos:** Guarda tus sitios preferidos para recibir una alerta cuando publiquen algo nuevo.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **iOS App** | Swift, SwiftUI, MVVM Architecture |
| **Android App** | Kotlin, JetBrains Compose, Hilt (DI), Coroutines, MVVM |
| **Backend** | Firebase (Auth, Firestore, Storage, Cloud Messaging) |
| **Lógica de Negocio** | Cloud Functions (Node.js) |
| **Landing Page** | HTML5, Vanilla JS, CSS3 |

---

## 📂 Estructura del Repositorio

```bash
Zampa/
├── Zampa-iOS/          # Proyecto Xcode (iOS)
├── Zampa-Android/      # Proyecto Android Studio (Android)
├── Zampa-LandingPage/  # Landing Page del proyecto
├── firebase/            # Reglas de seguridad e índices de Firestore
├── functions/           # Cloud Functions (Backend logic)
├── design-system/       # Tokens de diseño compartidos
└── docs/                # Documentación técnica adicional (ERD, Backlog)
```

---

## 🚀 Guía de Inicio Rápido

### Requisitos Previos
- **macOS** (para desarrollo iOS).
- **Xcode 15+** y **Android Studio Koala+**.
- **Node.js 18+** (para las Cloud Functions).
- Una cuenta de **Firebase** con un proyecto configurado.

### Instalación

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/onejensen/Zampa.git
   cd Zampa
   ```

2. **Configuración de Firebase:**
   - Descarga `GoogleService-Info.plist` y colócalo en `Zampa-iOS/Zampa/`.
   - Descarga `google-services.json` y colócalo en `Zampa-Android/app/`.

3. **Backend:**
   ```bash
   cd functions
   npm install
   firebase deploy --only functions
   ```

4. **Ejecución:**
   - En iOS: Abre `Zampa-iOS/Zampa.xcodeproj` y pulsa `Cmd + R`.
   - En Android: Abre `Zampa-Android` en Android Studio y pulsa `Run`.

---

## 📝 Documentación Relacionada

Para detalles más profundos, consulta los siguientes archivos:
- [📖 PROJECT.md](./PROJECT.md): Visión detallada y roadmap.
- [📊 ERD.md](./ERD.md): Esquema de base de datos y colecciones de Firestore.
- [🤖 AGENT.md](./AGENT.md): Guías de desarrollo y principios de arquitectura para el equipo.

---

## ⚖️ Licencia

Copyright © 2024 Zampa. Todos los derechos reservados.
