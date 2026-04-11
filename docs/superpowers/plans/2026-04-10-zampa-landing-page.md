# Zampa Landing Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single-page marketing landing page for Zampa at `/Users/onejensen/Documents/MIS WEBS/Zampa`, deployable to GitHub Pages at www.getZampa.com.

**Architecture:** Static HTML/CSS/JS site. One `index.html` with all sections, `styles.css` for styling, `main.js` for i18n switching and FAQ accordion. Translations stored as JSON files in `i18n/`. No framework, no build step.

**Tech Stack:** HTML5, CSS3 (custom properties, flexbox, grid), vanilla JavaScript (ES modules), GitHub Pages hosting.

---

## File Structure

```
/Users/onejensen/Documents/MIS WEBS/Zampa/
├── index.html          — All page sections, data-i18n attributes on translatable elements
├── styles.css          — All styles: reset, variables, layout, components, responsive
├── main.js             — i18n loader, language selector, FAQ accordion, smooth scroll
├── assets/
│   ├── logo.png        — Zampa logo (copied from app)
│   └── favicon.png     — 32x32 favicon
├── i18n/
│   ├── es.json         — Spanish (default)
│   ├── en.json         — English
│   ├── ca.json         — Catalan
│   ├── eu.json         — Basque
│   ├── gl.json         — Galician
│   ├── pt.json         — Portuguese
│   ├── de.json         — German
│   ├── fr.json         — French
│   └── it.json         — Italian
├── CNAME               — Custom domain for GitHub Pages
└── .nojekyll           — Disable Jekyll processing
```

---

### Task 1: Initialize repo and project scaffold

**Files:**
- Create: `/Users/onejensen/Documents/MIS WEBS/Zampa/index.html`
- Create: `/Users/onejensen/Documents/MIS WEBS/Zampa/styles.css`
- Create: `/Users/onejensen/Documents/MIS WEBS/Zampa/main.js`
- Create: `/Users/onejensen/Documents/MIS WEBS/Zampa/CNAME`
- Create: `/Users/onejensen/Documents/MIS WEBS/Zampa/.nojekyll`
- Create: `/Users/onejensen/Documents/MIS WEBS/Zampa/.gitignore`
- Copy: `logo_zampa.png` → `/Users/onejensen/Documents/MIS WEBS/Zampa/assets/logo.png`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p "/Users/onejensen/Documents/MIS WEBS/Zampa/assets/screenshots"
mkdir -p "/Users/onejensen/Documents/MIS WEBS/Zampa/i18n"
```

- [ ] **Step 2: Copy logo from app**

```bash
cp "/Users/onejensen/Documents/MIS APPS/Zampa/Zampa-Android/app/src/main/res/drawable/logo_zampa.png" "/Users/onejensen/Documents/MIS WEBS/Zampa/assets/logo.png"
```

- [ ] **Step 3: Create CNAME for GitHub Pages custom domain**

Write `CNAME`:
```
www.getzampa.com
```

- [ ] **Step 4: Create .nojekyll and .gitignore**

Write `.nojekyll` (empty file).

Write `.gitignore`:
```
.DS_Store
.vscode/
```

- [ ] **Step 5: Create minimal index.html skeleton**

Write `index.html` — a basic HTML5 page with:
- `<!DOCTYPE html>`, lang="es", charset UTF-8, viewport meta
- `<title>Zampa — Descubre los mejores menús del día</title>`
- Open Graph meta tags (og:title, og:description, og:type)
- Meta description
- Link to `styles.css`
- Empty `<body>` with a single `<h1>Zampa</h1>` placeholder
- Script tag loading `main.js` with `type="module"`

```html
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Zampa — Descubre los mejores menús del día</title>
  <meta name="description" content="Zampa conecta a comensales con los mejores restaurantes y sus menús del día. Disponible en App Store y Google Play.">
  <meta property="og:title" content="Zampa — Descubre los mejores menús del día">
  <meta property="og:description" content="Zampa conecta a comensales con los mejores restaurantes y sus menús del día.">
  <meta property="og:type" content="website">
  <meta property="og:url" content="https://www.getzampa.com">
  <link rel="icon" href="assets/favicon.png" type="image/png">
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <h1>Zampa</h1>
  <script type="module" src="main.js"></script>
</body>
</html>
```

- [ ] **Step 6: Create minimal styles.css with CSS variables**

Write `styles.css` with the brand tokens as custom properties, a basic reset, and system font stack:

```css
:root {
  --primary: #FAAF32;
  --primary-dark: #D18B16;
  --primary-light: #FFD182;
  --primary-surface: #FFF7E0;
  --secondary: #4CAF50;
  --secondary-light: #E8F5E9;
  --text: #1A1A1A;
  --muted: #6B6B6B;
  --white: #FFFFFF;
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 20px;
  --space-xs: 8px;
  --space-sm: 12px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;
  --space-xxl: 48px;
  --max-width: 1120px;
  --font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: var(--font);
  color: var(--text);
  background: var(--white);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

img { max-width: 100%; display: block; }
a { color: inherit; text-decoration: none; }
button { font: inherit; cursor: pointer; border: none; background: none; }
```

- [ ] **Step 7: Create empty main.js**

Write `main.js`:
```js
// Zampa Landing Page — main.js
// i18n, FAQ accordion, smooth scroll
```

- [ ] **Step 8: Init git and push**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git init
git remote add origin https://github.com/onejensen/ZAMPA.git
git add -A
git commit -m "chore: scaffold project with structure, logo, and brand tokens"
git branch -M main
git push -u origin main
```

- [ ] **Step 9: Verify page loads locally**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
python3 -m http.server 8080
```

Open `http://localhost:8080` — should show "Zampa" heading on a white page.

---

### Task 2: Build the sticky header

**Files:**
- Modify: `index.html` — add `<header>` markup
- Modify: `styles.css` — add header styles

- [ ] **Step 1: Add header HTML to index.html**

Replace the `<h1>Zampa</h1>` placeholder in `<body>` with:

```html
<header class="header" id="header">
  <div class="header__inner">
    <a href="#" class="header__logo">
      <img src="assets/logo.png" alt="Zampa" width="36" height="36">
      <span>Zampa</span>
    </a>
    <nav class="header__nav" id="nav">
      <a href="#customers" class="header__link" data-i18n="nav.customers">Clientes</a>
      <a href="#merchants" class="header__link" data-i18n="nav.merchants">Comercios</a>
      <a href="#faq" class="header__link" data-i18n="nav.faq">FAQ</a>
    </nav>
    <div class="header__right">
      <div class="lang-selector" id="lang-selector">
        <button class="lang-selector__btn" id="lang-btn" aria-label="Cambiar idioma">
          <span id="lang-current">ES</span>
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M3 5l3 3 3-3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
        </button>
        <ul class="lang-selector__list" id="lang-list" hidden>
          <li><button data-lang="es">Español</button></li>
          <li><button data-lang="ca">Català</button></li>
          <li><button data-lang="eu">Euskara</button></li>
          <li><button data-lang="gl">Galego</button></li>
          <li><button data-lang="en">English</button></li>
          <li><button data-lang="pt">Português</button></li>
          <li><button data-lang="de">Deutsch</button></li>
          <li><button data-lang="fr">Français</button></li>
          <li><button data-lang="it">Italiano</button></li>
        </ul>
      </div>
      <button class="header__burger" id="burger" aria-label="Menu">
        <span></span><span></span><span></span>
      </button>
    </div>
  </div>
</header>
```

- [ ] **Step 2: Add header styles to styles.css**

Append to `styles.css`:

```css
/* ── Header ── */
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.header__inner {
  max-width: var(--max-width);
  margin: 0 auto;
  padding: var(--space-md) var(--space-lg);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header__logo {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: 20px;
  font-weight: 800;
  color: var(--text);
}

.header__logo img {
  width: 36px;
  height: 36px;
}

.header__nav {
  display: flex;
  gap: var(--space-lg);
}

.header__link {
  font-size: 15px;
  font-weight: 500;
  color: var(--muted);
  transition: color 0.2s;
}

.header__link:hover { color: var(--text); }

.header__right {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

/* Language selector */
.lang-selector { position: relative; }

.lang-selector__btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  color: var(--muted);
  transition: background 0.2s;
}

.lang-selector__btn:hover { background: var(--primary-surface); }

.lang-selector__list {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: var(--white);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
  list-style: none;
  min-width: 150px;
  overflow: hidden;
}

.lang-selector__list button {
  width: 100%;
  text-align: left;
  padding: 10px 16px;
  font-size: 14px;
  color: var(--text);
  transition: background 0.15s;
}

.lang-selector__list button:hover { background: var(--primary-surface); }

/* Burger (mobile) */
.header__burger { display: none; flex-direction: column; gap: 5px; }
.header__burger span {
  display: block;
  width: 22px;
  height: 2px;
  background: var(--text);
  border-radius: 2px;
  transition: transform 0.3s;
}

@media (max-width: 768px) {
  .header__nav {
    display: none;
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    background: var(--white);
    flex-direction: column;
    padding: var(--space-md) var(--space-lg);
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  }
  .header__nav.open { display: flex; }
  .header__burger { display: flex; }
}
```

- [ ] **Step 3: Add burger toggle to main.js**

Replace `main.js` contents:

```js
// ── Mobile nav toggle ──
const burger = document.getElementById('burger');
const nav = document.getElementById('nav');
burger?.addEventListener('click', () => {
  nav.classList.toggle('open');
});

// Close nav on link click (mobile)
nav?.querySelectorAll('a').forEach(link => {
  link.addEventListener('click', () => nav.classList.remove('open'));
});

// ── Smooth scroll ──
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
  anchor.addEventListener('click', e => {
    e.preventDefault();
    const target = document.querySelector(anchor.getAttribute('href'));
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  });
});
```

- [ ] **Step 4: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add sticky header with nav, language selector, and mobile burger"
```

---

### Task 3: Build the hero section

**Files:**
- Modify: `index.html` — add hero markup after `</header>`
- Modify: `styles.css` — add hero styles

- [ ] **Step 1: Add hero HTML after header in index.html**

```html
<section class="hero" id="hero">
  <div class="hero__inner">
    <div class="hero__content">
      <h1 class="hero__title" data-i18n="hero.title">Descubre los mejores menús del día</h1>
      <p class="hero__subtitle" data-i18n="hero.subtitle">Zampa conecta a comensales con restaurantes que publican sus ofertas diarias. Encuentra platos cerca de ti, filtra por tus preferencias y disfruta.</p>
      <div class="hero__buttons">
        <a href="https://apps.apple.com/app/idTODO" class="store-btn" target="_blank" rel="noopener">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>
          <div>
            <small data-i18n="hero.appstore_label">Descárgalo en</small>
            <strong>App Store</strong>
          </div>
        </a>
        <a href="https://play.google.com/store/apps/details?id=com.sozolab.zampa" class="store-btn" target="_blank" rel="noopener">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M3.18 23.77c-.36-.17-.58-.55-.58-.97V1.2c0-.42.22-.8.58-.97l11.83 11.77L3.18 23.77zm1.57-24L16.72 10.8l-2.79 2.78L4.75-.23zm17.19 10.24c.35.2.56.56.56.97s-.21.78-.56.97l-3.22 1.86-3.09-3.07 3.09-3.07 3.22 1.34zM4.75 24.23l11.18-11.18 2.79 2.78L4.75 24.23z"/></svg>
          <div>
            <small data-i18n="hero.play_label">Disponible en</small>
            <strong>Google Play</strong>
          </div>
        </a>
      </div>
    </div>
  </div>
</section>
```

- [ ] **Step 2: Add hero styles to styles.css**

```css
/* ── Hero ── */
.hero {
  background: linear-gradient(135deg, var(--primary-surface) 0%, var(--white) 100%);
  padding: 80px var(--space-lg) 60px;
}

.hero__inner {
  max-width: var(--max-width);
  margin: 0 auto;
  text-align: center;
}

.hero__title {
  font-size: clamp(32px, 5vw, 52px);
  font-weight: 800;
  line-height: 1.15;
  color: var(--text);
  margin-bottom: var(--space-md);
}

.hero__subtitle {
  font-size: clamp(16px, 2.5vw, 19px);
  color: var(--muted);
  max-width: 600px;
  margin: 0 auto var(--space-xl);
  line-height: 1.6;
}

.hero__buttons {
  display: flex;
  gap: var(--space-md);
  justify-content: center;
  flex-wrap: wrap;
}

.store-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 14px 24px;
  background: var(--text);
  color: var(--white);
  border-radius: var(--radius-md);
  font-size: 15px;
  transition: transform 0.15s, box-shadow 0.15s;
}

.store-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

.store-btn small {
  display: block;
  font-size: 11px;
  opacity: 0.8;
  font-weight: 400;
}

.store-btn strong {
  display: block;
  font-size: 16px;
  font-weight: 700;
}
```

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add hero section with tagline and store buttons"
```

---

### Task 4: Build "How It Works — Customers" section

**Files:**
- Modify: `index.html` — add section after hero
- Modify: `styles.css` — add section and card styles

- [ ] **Step 1: Add customers section HTML after hero in index.html**

```html
<section class="section" id="customers">
  <div class="section__inner">
    <h2 class="section__title" data-i18n="customers.title">¿Cómo funciona para ti?</h2>
    <p class="section__subtitle" data-i18n="customers.subtitle">Encontrar tu próximo menú del día es así de fácil.</p>
    <div class="steps">
      <div class="step-card">
        <div class="step-card__icon">📱</div>
        <h3 class="step-card__title" data-i18n="customers.step1.title">Abre Zampa</h3>
        <p class="step-card__desc" data-i18n="customers.step1.desc">Explora las ofertas de restaurantes y bares cerca de ti, actualizadas cada día.</p>
      </div>
      <div class="step-card">
        <div class="step-card__icon">🔍</div>
        <h3 class="step-card__title" data-i18n="customers.step2.title">Filtra y elige</h3>
        <p class="step-card__desc" data-i18n="customers.step2.desc">Por precio, cercanía, tipo de cocina o preferencias dietéticas como vegetariano o sin gluten.</p>
      </div>
      <div class="step-card">
        <div class="step-card__icon">🍽️</div>
        <h3 class="step-card__title" data-i18n="customers.step3.title">Llama o ve directo</h3>
        <p class="step-card__desc" data-i18n="customers.step3.desc">Contacta al restaurante con un toque o consulta cómo llegar. Sin intermediarios.</p>
      </div>
    </div>
  </div>
</section>
```

- [ ] **Step 2: Add section and step-card styles to styles.css**

```css
/* ── Sections (shared) ── */
.section {
  padding: 80px var(--space-lg);
}

.section:nth-child(even) { background: var(--primary-surface); }

.section__inner {
  max-width: var(--max-width);
  margin: 0 auto;
  text-align: center;
}

.section__title {
  font-size: clamp(26px, 4vw, 36px);
  font-weight: 800;
  margin-bottom: var(--space-sm);
}

.section__subtitle {
  font-size: 17px;
  color: var(--muted);
  margin-bottom: var(--space-xxl);
  max-width: 550px;
  margin-left: auto;
  margin-right: auto;
}

/* ── Step Cards ── */
.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-lg);
}

.step-card {
  background: var(--white);
  border-radius: var(--radius-lg);
  padding: var(--space-xl) var(--space-lg);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  transition: transform 0.2s, box-shadow 0.2s;
}

.step-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.1);
}

.step-card__icon {
  font-size: 40px;
  margin-bottom: var(--space-md);
}

.step-card__title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: var(--space-xs);
}

.step-card__desc {
  font-size: 15px;
  color: var(--muted);
  line-height: 1.5;
}

@media (max-width: 768px) {
  .steps { grid-template-columns: 1fr; }
}
```

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add 'How it works — Customers' section with step cards"
```

---

### Task 5: Build "How It Works — Merchants" section

**Files:**
- Modify: `index.html` — add section after customers
- (Styles already defined in Task 4 — reuses `.section`, `.steps`, `.step-card`)

- [ ] **Step 1: Add merchants section HTML after customers section**

```html
<section class="section" id="merchants">
  <div class="section__inner">
    <h2 class="section__title" data-i18n="merchants.title">¿Tienes un restaurante o bar?</h2>
    <p class="section__subtitle" data-i18n="merchants.subtitle">Publica tu menú del día y llega a nuevos clientes cada día.</p>
    <div class="steps">
      <div class="step-card">
        <div class="step-card__icon">✏️</div>
        <h3 class="step-card__title" data-i18n="merchants.step1.title">Regístrate gratis</h3>
        <p class="step-card__desc" data-i18n="merchants.step1.desc">Crea tu perfil de negocio en minutos. Solo necesitas dirección y teléfono.</p>
      </div>
      <div class="step-card">
        <div class="step-card__icon">📸</div>
        <h3 class="step-card__title" data-i18n="merchants.step2.title">Publica tu menú</h3>
        <p class="step-card__desc" data-i18n="merchants.step2.desc">Sube tu oferta del día con foto, precio y descripción. Se publica al instante.</p>
      </div>
      <div class="step-card">
        <div class="step-card__icon">📈</div>
        <h3 class="step-card__title" data-i18n="merchants.step3.title">Atrae clientes</h3>
        <p class="step-card__desc" data-i18n="merchants.step3.desc">Tus seguidores reciben una notificación cada vez que publicas. Consulta tus estadísticas de impacto.</p>
      </div>
    </div>
  </div>
</section>
```

- [ ] **Step 2: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add 'How it works — Merchants' section"
```

---

### Task 6: Build Benefits comparison section

**Files:**
- Modify: `index.html` — add benefits section after merchants
- Modify: `styles.css` — add benefits styles

- [ ] **Step 1: Add benefits HTML after merchants section**

```html
<section class="section" id="benefits">
  <div class="section__inner">
    <h2 class="section__title" data-i18n="benefits.title">¿Por qué Zampa?</h2>
    <div class="benefits">
      <div class="benefits__col">
        <h3 class="benefits__heading benefits__heading--client" data-i18n="benefits.clients_heading">Para Clientes</h3>
        <ul class="benefits__list">
          <li data-i18n="benefits.client1">Descubre ofertas nuevas cada día</li>
          <li data-i18n="benefits.client2">Filtra por dieta: vegetariano, sin gluten, sin lactosa…</li>
          <li data-i18n="benefits.client3">Guarda tus restaurantes favoritos</li>
          <li data-i18n="benefits.client4">Consulta distancia y precio de un vistazo</li>
          <li data-i18n="benefits.client5">Gratis, sin registro obligatorio para explorar</li>
        </ul>
      </div>
      <div class="benefits__col">
        <h3 class="benefits__heading benefits__heading--merchant" data-i18n="benefits.merchants_heading">Para Comercios</h3>
        <ul class="benefits__list">
          <li data-i18n="benefits.merchant1">Publica tu menú en menos de un minuto</li>
          <li data-i18n="benefits.merchant2">Plan gratuito con visibilidad completa</li>
          <li data-i18n="benefits.merchant3">Plan Pro: notificaciones push a tus seguidores</li>
          <li data-i18n="benefits.merchant4">Estadísticas de impresiones, llamadas y visitas</li>
          <li data-i18n="benefits.merchant5">Sin comisiones ni intermediarios</li>
        </ul>
      </div>
    </div>
  </div>
</section>
```

- [ ] **Step 2: Add benefits styles to styles.css**

```css
/* ── Benefits ── */
.benefits {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-xl);
  text-align: left;
}

.benefits__col {
  background: var(--white);
  border-radius: var(--radius-lg);
  padding: var(--space-xl);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
}

.benefits__heading {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: var(--space-lg);
  padding-bottom: var(--space-sm);
  border-bottom: 3px solid;
}

.benefits__heading--client { border-color: var(--primary); }
.benefits__heading--merchant { border-color: var(--secondary); }

.benefits__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.benefits__list li {
  font-size: 15px;
  line-height: 1.5;
  padding-left: 28px;
  position: relative;
  color: var(--text);
}

.benefits__list li::before {
  content: "✓";
  position: absolute;
  left: 0;
  font-weight: 700;
  color: var(--primary);
}

@media (max-width: 768px) {
  .benefits { grid-template-columns: 1fr; }
}
```

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add benefits comparison section (clients vs merchants)"
```

---

### Task 7: Build FAQ accordion section

**Files:**
- Modify: `index.html` — add FAQ section after benefits
- Modify: `styles.css` — add FAQ styles
- Modify: `main.js` — add accordion logic

- [ ] **Step 1: Add FAQ HTML after benefits section**

```html
<section class="section" id="faq">
  <div class="section__inner">
    <h2 class="section__title" data-i18n="faq.title">Preguntas frecuentes</h2>
    <div class="faq">
      <details class="faq__item">
        <summary class="faq__question" data-i18n="faq.q1">¿Es gratis usar Zampa?</summary>
        <p class="faq__answer" data-i18n="faq.a1">Sí, para los clientes es completamente gratis. Los comercios tienen un plan gratuito con todas las funcionalidades básicas y un Plan Pro con notificaciones push y estadísticas avanzadas.</p>
      </details>
      <details class="faq__item">
        <summary class="faq__question" data-i18n="faq.q2">¿En qué ciudades está disponible?</summary>
        <p class="faq__answer" data-i18n="faq.a2">Zampa está disponible en toda España. Los restaurantes y bares de cualquier ciudad pueden registrarse y publicar sus ofertas.</p>
      </details>
      <details class="faq__item">
        <summary class="faq__question" data-i18n="faq.q3">¿Cómo publico mi menú del día?</summary>
        <p class="faq__answer" data-i18n="faq.a3">Regístrate como comercio, completa tu perfil (dirección y teléfono) y ya puedes publicar tu primera oferta con foto, precio y descripción.</p>
      </details>
      <details class="faq__item">
        <summary class="faq__question" data-i18n="faq.q4">¿Qué incluye el Plan Pro?</summary>
        <p class="faq__answer" data-i18n="faq.a4">Notificaciones push automáticas a tus seguidores cada vez que publicas una oferta, además de estadísticas detalladas de impresiones y clics.</p>
      </details>
      <details class="faq__item">
        <summary class="faq__question" data-i18n="faq.q5">¿Necesito hacer reservas por la app?</summary>
        <p class="faq__answer" data-i18n="faq.a5">No. Zampa te conecta con el restaurante para que llames directamente o consultes cómo llegar. No hay intermediarios.</p>
      </details>
      <details class="faq__item">
        <summary class="faq__question" data-i18n="faq.q6">¿Puedo filtrar por preferencias dietéticas?</summary>
        <p class="faq__answer" data-i18n="faq.a6">Sí. Puedes filtrar ofertas por vegetariano, vegano, sin gluten, sin lactosa, sin frutos secos, sin carne y sin pescado.</p>
      </details>
      <details class="faq__item">
        <summary class="faq__question" data-i18n="faq.q7">¿Cómo contacto con soporte?</summary>
        <p class="faq__answer" data-i18n="faq.a7">Escríbenos a soporte@getzampa.com y te responderemos lo antes posible.</p>
      </details>
    </div>
  </div>
</section>
```

- [ ] **Step 2: Add FAQ styles to styles.css**

```css
/* ── FAQ ── */
.faq {
  max-width: 700px;
  margin: 0 auto;
  text-align: left;
}

.faq__item {
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.faq__question {
  padding: var(--space-lg) 0;
  font-size: 17px;
  font-weight: 600;
  cursor: pointer;
  list-style: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.faq__question::-webkit-details-marker { display: none; }

.faq__question::after {
  content: "+";
  font-size: 22px;
  font-weight: 300;
  color: var(--muted);
  transition: transform 0.2s;
}

details[open] .faq__question::after {
  content: "−";
}

.faq__answer {
  padding: 0 0 var(--space-lg);
  font-size: 15px;
  color: var(--muted);
  line-height: 1.6;
}
```

- [ ] **Step 3: Add FAQ accordion logic to main.js (only one open at a time)**

Append to `main.js`:

```js
// ── FAQ accordion: only one open at a time ──
document.querySelectorAll('.faq__item').forEach(item => {
  item.addEventListener('toggle', () => {
    if (item.open) {
      document.querySelectorAll('.faq__item').forEach(other => {
        if (other !== item) other.removeAttribute('open');
      });
    }
  });
});
```

- [ ] **Step 4: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add FAQ accordion section"
```

---

### Task 8: Build footer

**Files:**
- Modify: `index.html` — add footer after FAQ
- Modify: `styles.css` — add footer styles

- [ ] **Step 1: Add footer HTML after FAQ section**

```html
<footer class="footer">
  <div class="footer__inner">
    <div class="footer__brand">
      <div class="footer__logo">
        <img src="assets/logo.png" alt="Zampa" width="32" height="32">
        <span>Zampa</span>
      </div>
      <p class="footer__tagline" data-i18n="footer.tagline">Descubre los mejores menús del día</p>
    </div>
    <div class="footer__stores">
      <a href="https://apps.apple.com/app/idTODO" class="store-btn store-btn--small" target="_blank" rel="noopener">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/></svg>
        <strong>App Store</strong>
      </a>
      <a href="https://play.google.com/store/apps/details?id=com.sozolab.zampa" class="store-btn store-btn--small" target="_blank" rel="noopener">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M3.18 23.77c-.36-.17-.58-.55-.58-.97V1.2c0-.42.22-.8.58-.97l11.83 11.77L3.18 23.77zm1.57-24L16.72 10.8l-2.79 2.78L4.75-.23zm17.19 10.24c.35.2.56.56.56.97s-.21.78-.56.97l-3.22 1.86-3.09-3.07 3.09-3.07 3.22 1.34zM4.75 24.23l11.18-11.18 2.79 2.78L4.75 24.23z"/></svg>
        <strong>Google Play</strong>
      </a>
    </div>
    <div class="footer__links">
      <a href="mailto:soporte@getzampa.com" data-i18n="footer.contact">Contacto</a>
      <a href="#" data-i18n="footer.privacy">Política de privacidad</a>
      <a href="#" data-i18n="footer.terms">Términos de uso</a>
    </div>
    <p class="footer__copy" data-i18n="footer.copyright">© 2026 Sozo Labs. Todos los derechos reservados.</p>
  </div>
</footer>
```

- [ ] **Step 2: Add footer styles to styles.css**

```css
/* ── Footer ── */
.footer {
  background: var(--text);
  color: var(--white);
  padding: 60px var(--space-lg);
}

.footer__inner {
  max-width: var(--max-width);
  margin: 0 auto;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-lg);
}

.footer__logo {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: 20px;
  font-weight: 800;
}

.footer__logo img { width: 32px; height: 32px; filter: brightness(0) invert(1); }

.footer__tagline {
  font-size: 14px;
  opacity: 0.6;
  margin-top: 4px;
}

.footer__stores {
  display: flex;
  gap: var(--space-sm);
}

.store-btn--small {
  padding: 10px 18px;
  font-size: 13px;
}

.store-btn--small strong { font-size: 14px; }

.footer__links {
  display: flex;
  gap: var(--space-lg);
  flex-wrap: wrap;
  justify-content: center;
}

.footer__links a {
  font-size: 14px;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.footer__links a:hover { opacity: 1; }

.footer__copy {
  font-size: 13px;
  opacity: 0.4;
}
```

- [ ] **Step 3: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add footer with store links, legal links, and branding"
```

---

### Task 9: Implement i18n system with all 9 languages

**Files:**
- Create: `i18n/es.json`
- Create: `i18n/en.json`
- Create: `i18n/ca.json`
- Create: `i18n/eu.json`
- Create: `i18n/gl.json`
- Create: `i18n/pt.json`
- Create: `i18n/de.json`
- Create: `i18n/fr.json`
- Create: `i18n/it.json`
- Modify: `main.js` — add i18n loading and language selector logic

- [ ] **Step 1: Create es.json (Spanish — source of truth)**

All keys used in the HTML `data-i18n` attributes, with Spanish values. Full file contents provided at implementation time — covers: `nav.*`, `hero.*`, `customers.*`, `merchants.*`, `benefits.*`, `faq.*`, `footer.*`.

- [ ] **Step 2: Create en.json (English)**

Same keys as `es.json`, translated to English.

- [ ] **Step 3: Create ca.json (Catalan)**

Same keys, translated to Catalan.

- [ ] **Step 4: Create eu.json (Basque)**

Same keys, translated to Basque.

- [ ] **Step 5: Create gl.json (Galician)**

Same keys, translated to Galician.

- [ ] **Step 6: Create pt.json (Portuguese)**

Same keys, translated to Portuguese.

- [ ] **Step 7: Create de.json (German)**

Same keys, translated to German.

- [ ] **Step 8: Create fr.json (French)**

Same keys, translated to French.

- [ ] **Step 9: Create it.json (Italian)**

Same keys, translated to Italian.

- [ ] **Step 10: Implement i18n loader and language selector in main.js**

Add to `main.js` (before the existing burger/smooth-scroll code):

```js
// ── i18n ──
const SUPPORTED_LANGS = ['es', 'ca', 'eu', 'gl', 'en', 'pt', 'de', 'fr', 'it'];
const LANG_LABELS = { es: 'ES', ca: 'CA', eu: 'EU', gl: 'GL', en: 'EN', pt: 'PT', de: 'DE', fr: 'FR', it: 'IT' };
let currentLang = 'es';
let translations = {};

function detectLanguage() {
  const saved = localStorage.getItem('zampa-lang');
  if (saved && SUPPORTED_LANGS.includes(saved)) return saved;
  const browser = navigator.language?.split('-')[0];
  return SUPPORTED_LANGS.includes(browser) ? browser : 'es';
}

async function loadLanguage(lang) {
  try {
    const res = await fetch(`i18n/${lang}.json`);
    translations = await res.json();
    currentLang = lang;
    localStorage.setItem('zampa-lang', lang);
    applyTranslations();
    document.getElementById('lang-current').textContent = LANG_LABELS[lang];
    document.documentElement.lang = lang;
  } catch (e) {
    console.warn(`Failed to load language: ${lang}`, e);
    if (lang !== 'es') loadLanguage('es');
  }
}

function applyTranslations() {
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.getAttribute('data-i18n');
    if (translations[key]) el.textContent = translations[key];
  });
}

// Language selector
const langBtn = document.getElementById('lang-btn');
const langList = document.getElementById('lang-list');

langBtn?.addEventListener('click', () => {
  langList.hidden = !langList.hidden;
});

langList?.addEventListener('click', e => {
  const btn = e.target.closest('[data-lang]');
  if (!btn) return;
  loadLanguage(btn.dataset.lang);
  langList.hidden = true;
});

// Close language list on outside click
document.addEventListener('click', e => {
  if (!e.target.closest('.lang-selector')) {
    langList && (langList.hidden = true);
  }
});

// Init
loadLanguage(detectLanguage());
```

- [ ] **Step 11: Commit**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: add i18n system with 9 languages"
```

---

### Task 10: Final polish and push

**Files:**
- Create: `assets/favicon.png` — generate a simple 32x32 favicon from the logo
- Verify all sections render correctly
- Push to GitHub

- [ ] **Step 1: Create favicon (use logo as base)**

```bash
sips -z 32 32 "/Users/onejensen/Documents/MIS WEBS/Zampa/assets/logo.png" --out "/Users/onejensen/Documents/MIS WEBS/Zampa/assets/favicon.png"
```

- [ ] **Step 2: Open locally and verify all sections**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
python3 -m http.server 8080
```

Open `http://localhost:8080` — verify:
- Header sticks on scroll, nav links scroll to sections, burger works on mobile
- Hero shows title, subtitle, store buttons
- Customers and Merchants sections show 3 cards each
- Benefits section shows two-column comparison
- FAQ accordion opens/closes (one at a time)
- Footer shows logo, store links, legal links
- Language selector switches all text
- Mobile responsive (resize browser to ~375px width)

- [ ] **Step 3: Final commit and push**

```bash
cd "/Users/onejensen/Documents/MIS WEBS/Zampa"
git add -A
git commit -m "feat: final polish, favicon, and all sections complete"
git push origin main
```

- [ ] **Step 4: Configure GitHub Pages**

Go to https://github.com/onejensen/ZAMPA/settings/pages:
- Source: Deploy from branch → `main` → `/ (root)`
- Custom domain: `www.getzampa.com`
- Enforce HTTPS: checked

Then configure DNS for `getzampa.com`:
- CNAME record: `www` → `onejensen.github.io`
- A records for apex domain:
  - `185.199.108.153`
  - `185.199.109.153`
  - `185.199.110.153`
  - `185.199.111.153`
