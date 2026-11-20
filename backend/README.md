# WorldLink Backend & Signaling Server

Complete REST API, WebSocket WebRTC signaling service, MySQL/MariaDB schema, and self-hosted Coturn TURN configuration for **WorldLink**.

## Architecture

- **REST API (`/api/v1/...`)**: Express + Helmet + Rate Limiting + SHA-256 hashed Bearer device tokens + unique `@username` enforcement (`UNIQUE KEY uq_users_username_lower`).
- **WebSocket Signaling (`/ws?token=...`)**: Real-time presence, typing indicators, delivery/read receipts, call invitations (`call.invite`, `call.accept`, `call.decline`, `call.end`), WebRTC SDP offers/answers (`webrtc.offer`, `webrtc.answer`), ICE candidates (`webrtc.ice`), and screen-sharing state (`call.screenshare`).
- **Database**:
  - Production: MySQL 8.0+ / MariaDB 10.6+ (`backend/sql/schema.sql`)
  - Zero-setup local dev: Persistent atomic JSON store (`DB_DRIVER=file`)
- **NAT Traversal (STUN / TURN)**:
  - Configured via `STUN_URL`, `TURN_URL`, and `COTURN_SHARED_SECRET` (generates 24h ephemeral HMAC-SHA1 TURN credentials at `GET /api/v1/ice-servers`).

## Quick Start (Local Development)

```bash
cd backend/server
npm install
PORT=8080 DB_DRIVER=file node server.js
```

Point the Android app (via Onboarding -> **Server** or Settings -> **Server & ICE**) to:
- Android Emulator: `http://10.0.2.2:8080`
- Physical Android device on same Wi-Fi: `http://<YOUR_PC_LAN_IP>:8080`

## Production Self-Hosting (Docker Compose + MySQL + Coturn)

```bash
cd backend
# Edit backend/turn/turnserver.conf and backend/docker-compose.yml with your public IP & secret
docker compose up -d --build
```

Place Nginx or Caddy in front of port `8080` for automatic TLS (`https://` and `wss://`).

## REST API Reference

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/health` | No | Server health & TURN configuration status |
| `GET` | `/api/v1/users/check-username?username=` | No | Global uniqueness check (`[A-Za-z0-9_]{3,24}`) |
| `POST` | `/api/v1/auth/register` | No | Claims unique `@username`, returns session token + recovery key |
| `POST` | `/api/v1/auth/recover` | No | Recovers `@username` on a new device via recovery key |
| `GET` | `/api/v1/me` | Bearer | Current user profile |
| `PATCH` | `/api/v1/me/profile` | Bearer | Update display name and bio |
| `POST` | `/api/v1/me/username` | Bearer | Atomic unique username change |
| `DELETE` | `/api/v1/me` | Bearer | Permanently delete account and all associated data |
| `GET` | `/api/v1/users/search?q=` | Bearer | Search users by `@username` or display name (excludes blocked) |
| `GET` | `/api/v1/contacts` | Bearer | List saved contacts |
| `POST` | `/api/v1/contacts` | Bearer | Add contact by `userId` |
| `DELETE` | `/api/v1/contacts/:userId` | Bearer | Remove contact |
| `GET/POST/DELETE` | `/api/v1/blocks` | Bearer | List, block, or unblock users |
| `POST` | `/api/v1/reports` | Bearer | Submit abuse report |
| `GET` | `/api/v1/conversations` | Bearer | List 1:1 conversations with unread counts |
| `GET` | `/api/v1/conversations/:peerId/messages` | Bearer | Fetch message history and mark as `READ` |
| `POST` | `/api/v1/messages` | Bearer | Persist message and push over WebSocket |
| `DELETE` | `/api/v1/messages/:id` | Bearer | Delete message for everyone |
| `DELETE` | `/api/v1/conversations/:peerId` | Bearer | Clear conversation |
| `GET/POST` | `/api/v1/calls` | Bearer | Fetch or persist call history |
| `GET` | `/api/v1/ice-servers` | Bearer | Fetch STUN + ephemeral TURN ICE configuration |
