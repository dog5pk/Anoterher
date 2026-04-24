# Anotether Deployment Guide

## Backend (Cloudflare Worker)

### Prerequisites

- Cloudflare account (free tier sufficient for v1)
- Node.js 18+
- `wrangler` CLI: `npm install -g wrangler`

### Setup

```bash
cd backend-worker
npm install
wrangler login
```

### Create KV Namespace

```bash
# Production
wrangler kv:namespace create ANOTETHER_KV
# Note the ID returned — paste it into wrangler.toml

# Preview (for local dev)
wrangler kv:namespace create ANOTETHER_KV --preview
```

### Configure wrangler.toml

Edit `wrangler.toml` and replace the KV namespace ID placeholders with the IDs from above.

### Deploy

```bash
# Deploy to production
wrangler deploy

# Local development (with KV simulation)
wrangler dev
```

### Environment Variables

No secrets needed in v1. The Worker uses only KV. If you add secrets later:

```bash
wrangler secret put MY_SECRET
```

### Verify Deployment

```bash
curl -X POST https://your-worker.workers.dev/session/create \
  -H "Content-Type: application/json" \
  -d '{"public_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}'
```

Expect: `{"token":"XXXXXX","expires_at":...,"role":"initiator"}`

### Custom Domain (optional)

In Cloudflare dashboard: Workers → your worker → Triggers → Custom Domains → Add `relay.anotether.app`

---

## Android App

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK API 34
- Device/emulator with API 26+ (Android 8.0 minimum)

### Configuration

Edit `android-app/app/src/main/kotlin/com/anotether/network/RelayClient.kt`:

```kotlin
const val BASE_URL = "https://your-worker.workers.dev"
```

For production, use your custom domain.

### Build

```bash
cd android-app
./gradlew assembleRelease
```

APK will be at: `app/build/outputs/apk/release/app-release.apk`

### Signing

```bash
keytool -genkey -v -keystore anotether.keystore \
  -alias anotether -keyalg RSA -keysize 2048 -validity 10000

# In android-app/app/build.gradle, configure signingConfigs
```

### Debug Build

```bash
./gradlew assembleDebug
# Or run directly from Android Studio
```

### Minimum Requirements

- Android 8.0 (API 26)
- ~5MB storage
- Network access (HTTPS only)

---

## Production Checklist

### Backend
- [ ] KV namespace created and IDs in wrangler.toml
- [ ] Rate limiting tested and working
- [ ] Worker deployed and responding
- [ ] Custom domain configured with Cloudflare proxy
- [ ] Verify no IP logging in Worker code

### Android
- [ ] BASE_URL pointing to production Worker
- [ ] App signed with release key
- [ ] ProGuard/R8 minification enabled
- [ ] Network security config enforcing HTTPS
- [ ] First-run privacy disclosure screen tested

### Operational
- [ ] Worker error alerting (Cloudflare dashboard)
- [ ] No analytics, no crash reporting that leaks identity

---

## Cloudflare Free Tier Limits (as of 2024)

- 100,000 Worker requests/day
- KV: 100,000 reads/day, 1,000 writes/day
- Sufficient for ~5,000 active sessions/day

When you exceed free limits, cost is negligible ($0.50/million requests).
