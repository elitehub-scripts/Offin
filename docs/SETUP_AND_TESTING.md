# WorldLink Setup, Infrastructure & Testing Guide

## 1. Free & Self-Hosted Infrastructure Options

WorldLink operates **without SIM cards, phone numbers, emails, passwords, ads, or subscriptions**. Cross-country communication over Wi-Fi/internet requires a reachable signaling server + STUN/TURN infrastructure:

1. **Backend & Database Hosting**:
   - **Oracle Cloud Always Free** (4 ARM OCPUs / 24 GB RAM VM): Ideal for running the full Docker Compose stack (Node.js signaling + MySQL 8 + Coturn TURN) at $0/month.
   - **Fly.io / Render / Railway** (Free/Hobby tiers) + **TiDB Serverless / PlanetScale / Aiven MySQL**: Suitable for REST + WebSocket signaling + MySQL.
   - **Self-hosted VPS / Home Lab** behind Caddy/Nginx TLS (`https://` + `wss://`).
   - *Note*: Free tiers do not provide unlimited relay bandwidth. Heavy video relaying through TURN consumes server egress; direct P2P via STUN uses minimal server bandwidth.
2. **STUN & TURN**:
   - **STUN**: `stun:stun.l.google.com:19302` (free public STUN for P2P NAT discovery).
   - **TURN**: Self-hosted **Coturn** (`backend/turn/turnserver.conf`) using `use-auth-secret` ephemeral HMAC credentials for strict/symmetric NAT traversal across countries.
3. **Offline Nearby Mode**:
   - Uses Android **Wi-Fi Direct (`WifiP2pManager`)**, **Local Wi-Fi mDNS (`NsdManager`)**, and **BLE Proximity Beacons** with direct TCP sockets.
   - Requires **zero internet** and **zero backend**, and is strictly bounded to local wireless range.

## 2. Building the Android APK

```bash
# Debug APK
gradle :app:assembleDebug

# Release APK (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars)
gradle :app:assembleRelease
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Running Automated Tests

### Android Unit & Robolectric CUJ Tests
```bash
gradle :app:testDebugUnitTest
```

### Backend Verification
```bash
cd backend/server
npm install
PORT=8080 DB_DRIVER=file node server.js
# Verify health:
curl http://127.0.0.1:8080/api/v1/health
```

## 4. Acceptance Test Checklist

1. **Zero-PII Registration**: Launch app -> enter unique `@username` (no phone/email/password) -> claim username -> recovery key generated.
2. **Global Uniqueness**: Attempt registering the same `@username` from another device -> backend returns `409 Conflict` and UI shows taken error.
3. **Cross-Country Wi-Fi Calls**: Connect two devices on different Wi-Fi networks (no SIM required) pointing to the deployed HTTPS/WSS backend + Coturn TURN -> place voice & video calls.
4. **Live Screen Sharing**: During a call (or via Screen tab), trigger `MediaProjection` -> accept Android system consent -> verify remote peer views shared screen and `Stop` ends capture immediately.
5. **Persistent Real-Time Chat**: Send text/emojis -> verify instant WebSocket delivery, read receipts, reply, copy, delete, and persistence across app restarts.
6. **Offline Nearby Mode**: Disable internet on two nearby Android devices -> open **Nearby** tab -> scan -> discover via LAN/Wi-Fi Direct/BLE -> exchange local messages; verify out-of-range warning when separated.
7. **Block & Report**: Block a user from Contacts profile sheet -> verify messages/calls are rejected by backend (`403`) and unblock works in Settings.
