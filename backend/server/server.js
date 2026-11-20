import 'dotenv/config';
import crypto from 'node:crypto';
import http from 'node:http';
import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import { WebSocketServer } from 'ws';
import { createStore, validateUsername } from './db.js';

const PORT = Number(process.env.PORT || 8080);
const STUN_URL = process.env.STUN_URL || 'stun:stun.l.google.com:19302';
const TURN_URL = process.env.TURN_URL || '';
const TURN_STATIC_USER = process.env.TURN_USERNAME || '';
const TURN_STATIC_SECRET = process.env.TURN_PASSWORD || '';
const COTURN_SHARED_SECRET = process.env.COTURN_SHARED_SECRET || '';

const store = createStore();
const app = express();

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '64kb' }));

const authLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many requests. Please wait a minute.' },
});

const apiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 240,
  standardHeaders: true,
  legacyHeaders: false,
});

app.use('/api/v1/', apiLimiter);

// Connected WebSocket clients: Map<userId, Set<WebSocket>>
const onlineSockets = new Map();

function pushToUser(userId, payload) {
  const sockets = onlineSockets.get(userId);
  if (!sockets || sockets.size === 0) return false;
  const serialized = JSON.stringify(payload);
  let delivered = false;
  for (const ws of sockets) {
    if (ws.readyState === 1) {
      ws.send(serialized);
      delivered = true;
    }
  }
  return delivered;
}

async function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token) return res.status(401).json({ error: 'Missing device session token' });
  const user = await store.getUserByToken(token);
  if (!user) return res.status(401).json({ error: 'Invalid or expired device session' });
  req.user = user;
  next();
}

// Health check
app.get('/api/v1/health', (_req, res) => {
  res.json({
    ok: true,
    service: 'worldlink-backend',
    version: '1.0.0',
    turnConfigured: Boolean(TURN_URL),
    timestamp: new Date().toISOString(),
  });
});

// Username availability check
app.get('/api/v1/users/check-username', async (req, res) => {
  const raw = String(req.query.username || '').trim().replace(/^@/, '');
  if (!validateUsername(raw)) {
    return res.status(400).json({
      available: false,
      reason: 'Username must be 3–24 characters using letters, numbers, or underscores.',
    });
  }
  const available = await store.isUsernameAvailable(raw);
  res.json({ username: raw, available, reason: available ? null : 'Username is already taken.' });
});

// Register username-only identity (no phone, email, or password)
app.post('/api/v1/auth/register', authLimiter, async (req, res) => {
  try {
    const username = String(req.body?.username || '').trim().replace(/^@/, '');
    const displayName = String(req.body?.displayName || username).trim();
    const deviceLabel = String(req.body?.deviceLabel || 'Android Device').trim().slice(0, 80);

    if (!validateUsername(username)) {
      return res.status(400).json({
        error: 'Username must be 3–24 characters using letters, numbers, or underscores.',
      });
    }

    const result = await store.registerUser({ username, displayName, deviceLabel });
    res.status(201).json(result);
  } catch (err) {
    if (err.code === 'USERNAME_TAKEN') {
      return res.status(409).json({ error: 'Username is already taken.' });
    }
    console.error('Register error:', err);
    res.status(500).json({ error: 'Registration failed.' });
  }
});

// Recover existing @username on a new device using recovery key
app.post('/api/v1/auth/recover', authLimiter, async (req, res) => {
  const username = String(req.body?.username || '').trim().replace(/^@/, '');
  const recoveryKey = String(req.body?.recoveryKey || '').trim();
  const deviceLabel = String(req.body?.deviceLabel || 'Recovered Device').trim().slice(0, 80);

  if (!username || !recoveryKey) {
    return res.status(400).json({ error: 'Username and recovery key are required.' });
  }

  const recovered = await store.recoverUser({ username, recoveryKey, deviceLabel });
  if (!recovered) {
    return res.status(401).json({ error: 'Invalid username or recovery key.' });
  }
  res.json(recovered);
});

// Profile management
app.get('/api/v1/me', requireAuth, (req, res) => {
  res.json({ user: req.user });
});

app.patch('/api/v1/me/profile', requireAuth, async (req, res) => {
  const user = await store.updateProfile(req.user.id, {
    displayName: req.body?.displayName,
    bio: req.body?.bio,
  });
  res.json({ user });
});

// Safe atomic username change
app.post('/api/v1/me/username', requireAuth, async (req, res) => {
  const newUsername = String(req.body?.username || '').trim().replace(/^@/, '');
  if (!validateUsername(newUsername)) {
    return res.status(400).json({
      error: 'Username must be 3–24 characters using letters, numbers, or underscores.',
    });
  }
  try {
    const user = await store.changeUsername(req.user.id, newUsername);
    res.json({ user });
  } catch (err) {
    if (err.code === 'USERNAME_TAKEN') {
      return res.status(409).json({ error: 'Username is already taken.' });
    }
    res.status(500).json({ error: 'Could not update username.' });
  }
});

app.delete('/api/v1/me', requireAuth, async (req, res) => {
  await store.deleteUser(req.user.id);
  const sockets = onlineSockets.get(req.user.id);
  if (sockets) {
    for (const ws of sockets) ws.close(1000, 'Account deleted');
    onlineSockets.delete(req.user.id);
  }
  res.json({ deleted: true });
});

// Global user search & discovery
app.get('/api/v1/users/search', requireAuth, async (req, res) => {
  const q = String(req.query.q || '');
  const users = await store.searchUsers(req.user.id, q);
  res.json({ users });
});

app.get('/api/v1/users/:username', requireAuth, async (req, res) => {
  const user = await store.getUserByUsername(req.params.username);
  if (!user) return res.status(404).json({ error: 'User not found.' });
  const blocked = await store.isBlockedBetween(req.user.id, user.id);
  if (blocked) return res.status(403).json({ error: 'User unavailable.' });
  res.json({ user });
});

// Contacts
app.get('/api/v1/contacts', requireAuth, async (req, res) => {
  const contacts = await store.listContacts(req.user.id);
  res.json({ contacts });
});

app.post('/api/v1/contacts', requireAuth, async (req, res) => {
  const targetUserId = String(req.body?.userId || '').trim();
  const alias = String(req.body?.alias || '').trim().slice(0, 64);
  if (!targetUserId) return res.status(400).json({ error: 'userId required.' });
  const peer = await store.addContact(req.user.id, targetUserId, alias);
  if (!peer) return res.status(404).json({ error: 'Target user not found.' });
  res.status(201).json({ contact: peer });
});

app.delete('/api/v1/contacts/:userId', requireAuth, async (req, res) => {
  await store.removeContact(req.user.id, req.params.userId);
  res.json({ removed: true });
});

// Block & Report
app.get('/api/v1/blocks', requireAuth, async (req, res) => {
  const blocked = await store.listBlocked(req.user.id);
  res.json({ blocked });
});

app.post('/api/v1/blocks', requireAuth, async (req, res) => {
  const blockedUserId = String(req.body?.userId || '').trim();
  const reason = String(req.body?.reason || '').trim();
  if (!blockedUserId) return res.status(400).json({ error: 'userId required.' });
  await store.blockUser(req.user.id, blockedUserId, reason);
  res.status(201).json({ blocked: true });
});

app.delete('/api/v1/blocks/:userId', requireAuth, async (req, res) => {
  await store.unblockUser(req.user.id, req.params.userId);
  res.json({ unblocked: true });
});

app.post('/api/v1/reports', requireAuth, async (req, res) => {
  const reportedUserId = String(req.body?.userId || '').trim();
  const category = String(req.body?.category || 'ABUSE').trim();
  const details = String(req.body?.details || '').trim();
  if (!reportedUserId) return res.status(400).json({ error: 'userId required.' });
  const report = await store.reportUser(req.user.id, reportedUserId, category, details);
  res.status(201).json({ reportId: report.id });
});

// Conversations & Persistent Messages
app.get('/api/v1/conversations', requireAuth, async (req, res) => {
  const conversations = await store.listConversations(req.user.id);
  res.json({ conversations });
});

app.get('/api/v1/conversations/:peerId/messages', requireAuth, async (req, res) => {
  const messages = await store.getMessages(req.user.id, req.params.peerId);
  const updated = await store.markConversationRead(req.user.id, req.params.peerId);
  if (updated > 0) {
    pushToUser(req.params.peerId, {
      type: 'chat.read',
      peerId: req.user.id,
      readAt: new Date().toISOString(),
    });
  }
  res.json({ messages });
});

app.post('/api/v1/messages', requireAuth, async (req, res) => {
  const recipientId = String(req.body?.recipientId || '').trim();
  const body = String(req.body?.body || '').trim();
  const clientMsgId = req.body?.id ? String(req.body.id) : undefined;
  const replyToMessageId = req.body?.replyToMessageId ? String(req.body.replyToMessageId) : null;

  if (!recipientId || !body) {
    return res.status(400).json({ error: 'recipientId and body are required.' });
  }
  if (await store.isBlockedBetween(req.user.id, recipientId)) {
    return res.status(403).json({ error: 'Cannot message this user.' });
  }

  const saved = await store.sendMessage({
    id: clientMsgId,
    senderId: req.user.id,
    recipientId,
    body: body.slice(0, 4000),
    replyToMessageId,
  });

  const payload = {
    id: saved.id,
    conversationId: saved.conversation_id,
    senderId: saved.sender_id,
    recipientId: saved.recipient_id,
    replyToMessageId: saved.reply_to_message_id,
    body: saved.body,
    status: saved.status,
    deletedForAll: false,
    createdAt: saved.created_at,
  };

  const delivered = pushToUser(recipientId, {
    type: 'chat.message',
    sender: req.user,
    message: payload,
  });

  if (delivered) {
    await store.updateMessageStatus(saved.id, 'DELIVERED');
    payload.status = 'DELIVERED';
    pushToUser(req.user.id, { type: 'chat.delivered', messageId: saved.id, peerId: recipientId });
  }

  res.status(201).json({ message: payload });
});

app.delete('/api/v1/messages/:id', requireAuth, async (req, res) => {
  const msg = await store.deleteMessage(req.user.id, req.params.id);
  if (!msg) return res.status(404).json({ error: 'Message not found.' });
  pushToUser(msg.recipient_id, { type: 'chat.delete', messageId: msg.id, peerId: req.user.id });
  res.json({ deleted: true });
});

app.delete('/api/v1/conversations/:peerId', requireAuth, async (req, res) => {
  await store.clearConversation(req.user.id, req.params.peerId);
  res.json({ cleared: true });
});

// Call History
app.get('/api/v1/calls', requireAuth, async (req, res) => {
  const calls = await store.listCalls(req.user.id);
  res.json({ calls });
});

app.post('/api/v1/calls', requireAuth, async (req, res) => {
  const { id, peerId, direction, callType, status, durationSeconds, networkQuality } = req.body || {};
  if (!peerId) return res.status(400).json({ error: 'peerId required.' });
  const callerId = direction === 'INCOMING' ? peerId : req.user.id;
  const calleeId = direction === 'INCOMING' ? req.user.id : peerId;
  const record = await store.saveCallRecord({
    id,
    callerId,
    calleeId,
    callType,
    status,
    durationSeconds,
    networkQuality,
  });
  res.status(201).json({ call: record });
});

// STUN / Ephemeral TURN Credentials
app.get('/api/v1/ice-servers', requireAuth, (req, res) => {
  const iceServers = [{ urls: [STUN_URL] }];

  if (TURN_URL) {
    if (COTURN_SHARED_SECRET) {
      // coturn use-auth-secret REST API ephemeral credentials (valid 24h)
      const ttl = 86400;
      const unixExpiry = Math.floor(Date.now() / 1000) + ttl;
      const username = `${unixExpiry}:${req.user.id}`;
      const credential = crypto
        .createHmac('sha1', COTURN_SHARED_SECRET)
        .update(username)
        .digest('base64');
      iceServers.push({ urls: [TURN_URL], username, credential });
    } else if (TURN_STATIC_USER && TURN_STATIC_SECRET) {
      iceServers.push({
        urls: [TURN_URL],
        username: TURN_STATIC_USER,
        credential: TURN_STATIC_SECRET,
      });
    }
  }

  res.json({
    iceServers,
    turnConfigured: Boolean(TURN_URL),
    note: TURN_URL
      ? 'STUN and TURN relay configured.'
      : 'Only STUN configured. Symmetric NATs may require deploying coturn.',
  });
});

// HTTP + WebSocket Server
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', async (ws, req) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    const token = url.searchParams.get('token') || '';
    const user = token ? await store.getUserByToken(token) : null;
    if (!user) {
      ws.close(4001, 'Unauthorized');
      return;
    }

    if (!onlineSockets.has(user.id)) {
      onlineSockets.set(user.id, new Set());
    }
    onlineSockets.get(user.id).add(ws);
    await store.setOnlineStatus(user.id, true);

    ws.isAlive = true;
    ws.on('pong', () => {
      ws.isAlive = true;
    });

    ws.send(JSON.stringify({ type: 'session.ready', user }));

    ws.on('message', async (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString('utf8'));
      } catch {
        return;
      }

      const targetId = msg.toUserId;
      if (targetId && (await store.isBlockedBetween(user.id, targetId))) {
        ws.send(JSON.stringify({ type: 'error', reason: 'BLOCKED', callId: msg.callId }));
        return;
      }

      switch (msg.type) {
        case 'ping':
          ws.send(JSON.stringify({ type: 'pong', ts: Date.now() }));
          break;

        case 'chat.typing':
          if (targetId) {
            pushToUser(targetId, {
              type: 'chat.typing',
              fromUserId: user.id,
              isTyping: Boolean(msg.isTyping),
            });
          }
          break;

        case 'chat.read':
          if (targetId) {
            await store.markConversationRead(user.id, targetId);
            pushToUser(targetId, { type: 'chat.read', peerId: user.id });
          }
          break;

        case 'call.invite': {
          if (!targetId) break;
          await store.saveCallRecord({
            id: msg.callId,
            callerId: user.id,
            calleeId: targetId,
            callType: msg.callType || 'AUDIO',
            status: 'RINGING',
          });
          const delivered = pushToUser(targetId, {
            type: 'call.invite',
            callId: msg.callId,
            callType: msg.callType || 'AUDIO',
            fromUser: user,
          });
          if (!delivered) {
            await store.saveCallRecord({
              id: msg.callId,
              callerId: user.id,
              calleeId: targetId,
              callType: msg.callType || 'AUDIO',
              status: 'MISSED',
            });
            ws.send(
              JSON.stringify({
                type: 'call.unavailable',
                callId: msg.callId,
                reason: 'Peer is currently offline',
              })
            );
          } else {
            ws.send(JSON.stringify({ type: 'call.ringing', callId: msg.callId }));
          }
          break;
        }

        case 'call.accept':
        case 'call.decline':
        case 'call.end':
        case 'call.screenshare':
        case 'webrtc.offer':
        case 'webrtc.answer':
        case 'webrtc.ice': {
          if (!targetId) break;
          if (msg.type === 'call.accept') {
            await store.saveCallRecord({
              id: msg.callId,
              callerId: targetId,
              calleeId: user.id,
              status: 'ACTIVE',
            });
          } else if (msg.type === 'call.decline') {
            await store.saveCallRecord({
              id: msg.callId,
              callerId: targetId,
              calleeId: user.id,
              status: 'DECLINED',
            });
          } else if (msg.type === 'call.end') {
            await store.saveCallRecord({
              id: msg.callId,
              callerId: user.id,
              calleeId: targetId,
              status: 'ENDED',
              durationSeconds: Number(msg.durationSeconds || 0),
              networkQuality: msg.networkQuality || 'GOOD',
            });
          }
          pushToUser(targetId, { ...msg, fromUserId: user.id, fromUsername: user.username });
          break;
        }
      }
    });

    ws.on('close', async () => {
      const set = onlineSockets.get(user.id);
      if (set) {
        set.delete(ws);
        if (set.size === 0) {
          onlineSockets.delete(user.id);
          await store.setOnlineStatus(user.id, false);
        }
      }
    });
  } catch (err) {
    console.error('WS connection error:', err);
    ws.close(1011, 'Internal server error');
  }
});

// Keepalive ping every 30s
const interval = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30000);

wss.on('close', () => clearInterval(interval));

server.listen(PORT, '0.0.0.0', () => {
  console.log(`WorldLink Backend & WebSocket Signaling listening on 0.0.0.0:${PORT}`);
});
