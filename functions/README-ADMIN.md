# Zampa · Comandos de administración

Guía rápida para operaciones que se hacen desde la terminal. Todos los scripts
viven en `functions/` y requieren haber iniciado sesión con firebase-tools:

```bash
npx firebase-tools login
```

Todo se ejecuta desde:

```bash
cd "/Users/onejensen/Documents/MIS APPS/Zampa/functions"
```

---

## 🎁 Promoción global de gratuidad

Escribe `config/promo.freeUntil` en Firestore. Mientras esté activa, **todos
los merchants** saltan cualquier verificación de trial/suscripción y pueden
publicar ofertas.

```bash
# Ver estado actual
node set-promo.js

# Gratis durante 60 días desde hoy (para lanzamiento)
node set-promo.js 60

# Gratis durante 90 días
node set-promo.js 90

# Gratis hasta una fecha concreta (23:59 UTC)
node set-promo.js 2026-09-30

# Desactivar — cada merchant pasa a usar su trial/suscripción individual
node set-promo.js off
```

En el día del lanzamiento público:

```bash
node set-promo.js 60
```

Y se queda grabado "gratis hasta dentro de 60 días" para todos los merchants
que se registren durante ese periodo.

---

## 🌱 Seed de bots (datos de demo)

- **`seed-bots.js`** — 10 restaurantes Madrid (emails `bot1@..10@eatout-test.com`)
- **`seed-bots-extra.js`** — 55 restaurantes España (emails `bot11@..65@`)
- **`seed-bots-mallorca.js`** — 100 restaurantes Mallorca (emails `bot100@..199@`)

Todos son idempotentes: re-ejecutar el mismo día sobreescribe en vez de duplicar.

```bash
node seed-bots-mallorca.js      # ~2 min, crea 100 bots + sus ofertas
```

Contraseña de los bots: ver `.env` (`EATOUT_BOT_PASSWORD`).

---

## 🔄 Migrar merchants existentes

Si cambias el esquema de suscripción/promo, este script añade los campos que
falten sin romper nada:

```bash
node migrate-subscriptions.js          # dry-run (no escribe)
node migrate-subscriptions.js --apply  # aplica
```

---

## 🚀 Despliegue

```bash
# Solo rules (~5 seg)
firebase deploy --only firestore:rules

# Solo indexes (~20 seg)
firebase deploy --only firestore:indexes

# Solo functions (~2 min)
firebase deploy --only functions

# Ver funciones desplegadas
firebase functions:list

# Ver logs en tiempo real
firebase functions:log --only onMenuPublished
firebase functions:log --only purgeDeletedAccounts
```

---

## 🔔 Test de push notifications

Requiere tener la app instalada y el iPhone como follower del bot Pro:

```bash
node trigger-test-push.js
```

---

## 🗑️ Limpiar bots (si hace falta empezar de cero)

```bash
# Borra todas las ofertas creadas por bots
node delete-bot-offers.js

# ⚠️ Borrar usuarios Auth (acción más grave, usa admin-delete-user)
node admin-delete-user.js <uid>
```

---

## 📋 Checklist pre-launch

Estado actual en `~/.claude/projects/-Users-onejensen-Documents-MIS-APPS-Zampa/memory/launch_manual_todos.md`:

1. ✅ APNs Auth Key subida
2. ✅ `config/promo.freeUntil` configurable (este CLI)
3. ⏳ App Store Connect listing + screenshots
4. ⏳ Play Console listing + screenshots
5. ⏳ Landing: quitar "Coming Soon" cuando lances
