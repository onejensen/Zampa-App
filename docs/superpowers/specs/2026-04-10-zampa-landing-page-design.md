# Zampa Landing Page — Design Spec

**Date:** 2026-04-10
**Location:** `/Users/onejensen/Documents/MIS WEBS/Zampa`
**Domain:** www.getZampa.com
**Hosting:** GitHub Pages (repo: github.com/onejensen/ZAMPA.git)

---

## Overview

Single-page marketing landing page for Zampa. Explains the app concept to two audiences (customers and merchants), answers common questions, and links to App Store / Google Play. Supports 9 languages with automatic browser detection.

## Technology

- **Static HTML/CSS/JS** — no framework, no build step
- **Single `index.html`** with all sections
- **CSS** in a separate `styles.css` file
- **JS** in a separate `main.js` file — handles language switching and FAQ accordion
- **Translations** in `i18n/` folder — one JSON file per language (`es.json`, `en.json`, `ca.json`, `eu.json`, `gl.json`, `pt.json`, `de.json`, `fr.json`, `it.json`)
- **Assets** in `assets/` folder — logo, favicon, screenshots (placeholder initially)

## Brand Tokens (from app DesignSystem.swift)

| Token | Value | Usage |
|---|---|---|
| Primary | `#FAAF32` | CTAs, accents, highlights |
| Primary Dark | `#D18B16` | Hover states, borders |
| Primary Light | `#FFD182` | Subtle backgrounds |
| Primary Surface | `#FFF7E0` | Page background |
| Secondary (Green) | `#4CAF50` | Merchant-related accents |
| Text | `#1A1A1A` | Body text |
| Muted | `#6B6B6B` | Secondary text |
| Border Radius | `12px` (md), `16px` (lg) | Cards, buttons |
| Font | System font stack: `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif` | All text |

## Page Structure

### 1. Header (sticky)

- Logo (left) — `logo_zampa.png` from Android drawable
- Navigation links: Clientes · Comercios · FAQ
- Language selector (dropdown, right) — shows current language flag + code (e.g., "ES"), expands to full list
- Navigation links scroll to corresponding sections with smooth scroll

### 2. Hero Section

- Large heading: "Descubre los mejores menús del día"
- Subheading: brief one-liner explaining Zampa connects diners with daily restaurant deals
- Two CTA buttons: App Store + Google Play (dark background, white text, store icons)
- App mockup image placeholder (phone frame with screenshot, added later)

### 3. How It Works — Customers

- Section heading: "¿Cómo funciona para ti?"
- 3 steps displayed as cards in a horizontal row (stacked on mobile):
  1. **Abre Zampa** — Explora ofertas de restaurantes cerca de ti
  2. **Filtra y elige** — Por precio, cercanía, tipo de cocina o preferencias dietéticas
  3. **Llama o ve directo** — Contacta al restaurante o consulta cómo llegar
- Each card has: icon (emoji or SVG), title, short description (1-2 lines)

### 4. How It Works — Merchants

- Section heading: "¿Tienes un restaurante o bar?"
- 3 steps displayed as cards:
  1. **Regístrate gratis** — Crea tu perfil de negocio en minutos
  2. **Publica tu menú** — Sube tu oferta del día con fotos, precio y descripción
  3. **Atrae clientes** — Tus seguidores reciben notificaciones al publicar (Plan Pro)
- Each card has: icon, title, short description

### 5. Benefits (two-column comparison)

- Section heading: "¿Por qué Zampa?"
- Two columns side by side (stacked on mobile):

**Para Clientes:**
- Descubre ofertas nuevas cada día
- Filtra por dieta: vegetariano, sin gluten, sin lactosa...
- Guarda tus restaurantes favoritos
- Consulta distancia y precio de un vistazo
- Gratis, sin registro obligatorio para explorar

**Para Comercios:**
- Publica tu menú en menos de un minuto
- Plan gratuito con visibilidad completa
- Plan Pro: notificaciones push a tus seguidores
- Estadísticas de impresiones, llamadas y visitas
- Sin comisiones ni intermediarios

Each benefit is a bullet with a checkmark icon.

### 6. FAQ (accordion)

Expandable questions. Only one open at a time. Questions (all languages have equivalent translations):

1. **¿Es gratis usar Zampa?** — Sí, para los clientes es completamente gratis. Los comercios tienen un plan gratuito con todas las funcionalidades básicas y un Plan Pro con notificaciones push y estadísticas avanzadas.
2. **¿En qué ciudades está disponible?** — Zampa está disponible en toda España. Los restaurantes y bares de cualquier ciudad pueden registrarse y publicar sus ofertas.
3. **¿Cómo publico mi menú del día?** — Regístrate como comercio, completa tu perfil (dirección y teléfono) y ya puedes publicar tu primera oferta con foto, precio y descripción.
4. **¿Qué incluye el Plan Pro?** — Notificaciones push automáticas a tus seguidores cada vez que publicas una oferta, además de estadísticas detalladas de impresiones y clics.
5. **¿Necesito hacer reservas por la app?** — No. Zampa te conecta con el restaurante para que llames directamente o consultes cómo llegar. No hay intermediarios.
6. **¿Puedo filtrar por preferencias dietéticas?** — Sí. Puedes filtrar ofertas por vegetariano, vegano, sin gluten, sin lactosa, sin frutos secos, sin carne y sin pescado.
7. **¿Cómo contacto con soporte?** — Escríbenos a soporte@getzampa.com y te responderemos lo antes posible.

### 7. Footer

- Logo + "Zampa" text
- Store badges: App Store + Google Play
- Links: Política de privacidad · Términos de uso · Contacto (soporte@getzampa.com)
- Copyright: "© 2026 Sozo Labs. Todos los derechos reservados."

## Multilanguage Implementation

### Languages

| Code | Language | File |
|---|---|---|
| `es` | Español | `i18n/es.json` |
| `ca` | Català | `i18n/ca.json` |
| `eu` | Euskara | `i18n/eu.json` |
| `gl` | Galego | `i18n/gl.json` |
| `en` | English | `i18n/en.json` |
| `pt` | Português | `i18n/pt.json` |
| `de` | Deutsch | `i18n/de.json` |
| `fr` | Français | `i18n/fr.json` |
| `it` | Italiano | `i18n/it.json` |

### How it works

1. HTML elements that need translation get a `data-i18n="key"` attribute
2. On page load, `main.js` detects `navigator.language`, maps it to a supported language (fallback: `es`)
3. Loads the corresponding JSON file and replaces all `textContent` of `[data-i18n]` elements
4. Language choice saved to `localStorage`
5. Selector in header allows manual override — reloads strings without page refresh

### JSON structure (flat keys)

```json
{
  "hero.title": "Descubre los mejores menús del día",
  "hero.subtitle": "Zampa conecta a comensales con ...",
  "nav.customers": "Clientes",
  "nav.merchants": "Comercios",
  "nav.faq": "FAQ",
  "customers.title": "¿Cómo funciona para ti?",
  "customers.step1.title": "Abre Zampa",
  "customers.step1.desc": "Explora ofertas de restaurantes cerca de ti",
  ...
  "faq.q1": "¿Es gratis usar Zampa?",
  "faq.a1": "Sí, para los clientes es completamente gratis...",
  ...
  "footer.copyright": "© 2026 Sozo Labs. Todos los derechos reservados."
}
```

## Responsive Design

- **Desktop** (>1024px): Full-width sections, two-column benefits, horizontal step cards
- **Tablet** (768–1024px): Slightly narrower, steps may wrap to 2+1
- **Mobile** (<768px): Single column throughout, hamburger menu for nav, stacked cards and benefits

## File Structure

```
/Users/onejensen/Documents/MIS WEBS/Zampa/
├── index.html
├── styles.css
├── main.js
├── assets/
│   ├── logo.png          (copied from app)
│   ├── favicon.png
│   └── screenshots/      (placeholder, added later)
├── i18n/
│   ├── es.json
│   ├── ca.json
│   ├── eu.json
│   ├── gl.json
│   ├── en.json
│   ├── pt.json
│   ├── de.json
│   ├── fr.json
│   └── it.json
└── README.md
```

## Store Links

- **App Store:** `https://apps.apple.com/app/idTODO` (update when live)
- **Google Play:** `https://play.google.com/store/apps/details?id=com.sozolab.zampa`

## Out of Scope

- No backend, no database, no CMS
- No screenshots/mockups in v1 (placeholders, added later)
- No blog, no pricing page
- No cookie banner (no analytics/tracking in v1)
- No contact form (email link only)
