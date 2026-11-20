import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import mysql from 'mysql2/promise';

const DB_DRIVER = process.env.DB_DRIVER || 'file'; // 'mysql' | 'file'
const DATA_FILE = process.env.DATA_FILE || path.resolve(process.cwd(), 'data', 'worldlink-store.json');

const USERNAME_REGEX = /^[A-Za-z0-9_]{3,24}$/;

export function validateUsername(username) {
  return typeof username === 'string' && USERNAME_REGEX.test(username);
}

export function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

export function randomId() {
  return crypto.randomUUID();
}

export function generateToken() {
  return crypto.randomBytes(32).toString('base64url');
}

export function generateRecoveryKey() {
  const bytes = crypto.randomBytes(16).toString('hex').toUpperCase();
  return `${bytes.slice(0, 4)}-${bytes.slice(4, 8)}-${bytes.slice(8, 12)}-${bytes.slice(12, 16)}-${bytes.slice(16, 20)}-${bytes.slice(20, 24)}`;
}

const AVATAR_COLORS = ['#00E5FF', '#10B981', '#38BDF8', '#A78BFA', '#F472B6', '#FBBF24'];

function pickAvatarColor(username) {
  let hash = 0;
  for (let i = 0; i < username.length; i++) {
    hash = (hash * 31 + username.charCodeAt(i)) >>> 0;
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function publicUser(user) {
  if (!user) return null;
  return {
    id: user.id,
    username: user.username,
    displayName: user.display_name || user.displayName,
    bio: user.bio || '',
    avatarColor: user.avatar_color || user.avatarColor || '#00E5FF',
    isOnline: Boolean(user.is_online ?? user.isOnline),
    lastSeenAt: user.last_seen_at || user.lastSeenAt,
    createdAt: user.created_at || user.createdAt,
  };
}

class FileStore {
  constructor(filePath) {
    this.filePath = filePath;
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    if (fs.existsSync(filePath)) {
      this.data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    } else {
      this.data = {
        users: [],
        sessions: [],
        contacts: [],
        blocked: [],
        reports: [],
        conversations: [],
        messages: [],
        calls: [],
      };
      this.persist();
    }
  }

  persist() {
    const tmp = `${this.filePath}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(this.data, null, 2), 'utf8');
    fs.renameSync(tmp, this.filePath);
  }

  async isUsernameAvailable(username) {
    const lower = username.toLowerCase();
    return !this.data.users.some((u) => u.username.toLowerCase() === lower);
  }

  async registerUser({ username, displayName, deviceLabel }) {
    const lower = username.toLowerCase();
    if (this.data.users.some((u) => u.username.toLowerCase() === lower)) {
      const err = new Error('Username already taken');
      err.code = 'USERNAME_TAKEN';
      throw err;
    }
    const now = new Date().toISOString();
    const recoveryKey = generateRecoveryKey();
    const token = generateToken();
    const user = {
      id: randomId(),
      username,
      display_name: displayName?.trim() || username,
      bio: 'Available on WorldLink',
      avatar_color: pickAvatarColor(username),
      recovery_key_hash: sha256(recoveryKey),
      is_online: true,
      last_seen_at: now,
      created_at: now,
      updated_at: now,
    };
    this.data.users.push(user);
    this.data.sessions.push({
      id: randomId(),
      user_id: user.id,
      device_label: deviceLabel || 'Android Device',
      token_hash: sha256(token),
      last_active_at: now,
      created_at: now,
    });
    this.persist();
    return { user: publicUser(user), token, recoveryKey };
  }

  async recoverUser({ username, recoveryKey, deviceLabel }) {
    const lower = username.toLowerCase();
    const user = this.data.users.find((u) => u.username.toLowerCase() === lower);
    if (!user || user.recovery_key_hash !== sha256(recoveryKey.trim().toUpperCase())) {
      return null;
    }
    const now = new Date().toISOString();
    const token = generateToken();
    this.data.sessions.push({
      id: randomId(),
      user_id: user.id,
      device_label: deviceLabel || 'Recovered Android Device',
      token_hash: sha256(token),
      last_active_at: now,
      created_at: now,
    });
    user.is_online = true;
    user.last_seen_at = now;
    this.persist();
    return { user: publicUser(user), token };
  }

  async getUserByToken(token) {
    const hash = sha256(token);
    const session = this.data.sessions.find((s) => s.token_hash === hash);
    if (!session) return null;
    const user = this.data.users.find((u) => u.id === session.user_id);
    if (!user) return null;
    session.last_active_at = new Date().toISOString();
    return publicUser(user);
  }

  async setOnlineStatus(userId, isOnline) {
    const user = this.data.users.find((u) => u.id === userId);
    if (!user) return;
    user.is_online = Boolean(isOnline);
    user.last_seen_at = new Date().toISOString();
    this.persist();
  }

  async updateProfile(userId, { displayName, bio }) {
    const user = this.data.users.find((u) => u.id === userId);
    if (!user) return null;
    if (typeof displayName === 'string' && displayName.trim()) {
      user.display_name = displayName.trim().slice(0, 64);
    }
    if (typeof bio === 'string') {
      user.bio = bio.trim().slice(0, 160);
    }
    user.updated_at = new Date().toISOString();
    this.persist();
    return publicUser(user);
  }

  async changeUsername(userId, newUsername) {
    const lower = newUsername.toLowerCase();
    const collision = this.data.users.find(
      (u) => u.id !== userId && u.username.toLowerCase() === lower
    );
    if (collision) {
      const err = new Error('Username already taken');
      err.code = 'USERNAME_TAKEN';
      throw err;
    }
    const user = this.data.users.find((u) => u.id === userId);
    if (!user) return null;
    user.username = newUsername;
    user.updated_at = new Date().toISOString();
    this.persist();
    return publicUser(user);
  }

  async deleteUser(userId) {
    this.data.users = this.data.users.filter((u) => u.id !== userId);
    this.data.sessions = this.data.sessions.filter((s) => s.user_id !== userId);
    this.data.contacts = this.data.contacts.filter(
      (c) => c.owner_user_id !== userId && c.contact_user_id !== userId
    );
    this.data.blocked = this.data.blocked.filter(
      (b) => b.blocker_user_id !== userId && b.blocked_user_id !== userId
    );
    this.data.messages = this.data.messages.filter(
      (m) => m.sender_id !== userId && m.recipient_id !== userId
    );
    this.data.conversations = this.data.conversations.filter(
      (c) => c.participant_low_id !== userId && c.participant_high_id !== userId
    );
    this.data.calls = this.data.calls.filter(
      (c) => c.caller_id !== userId && c.callee_id !== userId
    );
    this.persist();
  }

  async isBlockedBetween(userA, userB) {
    return this.data.blocked.some(
      (b) =>
        (b.blocker_user_id === userA && b.blocked_user_id === userB) ||
        (b.blocker_user_id === userB && b.blocked_user_id === userA)
    );
  }

  async searchUsers(requesterId, query) {
    const q = query.trim().replace(/^@/, '').toLowerCase();
    if (!q) return [];
    const blockedIds = new Set(
      this.data.blocked
        .filter((b) => b.blocker_user_id === requesterId || b.blocked_user_id === requesterId)
        .map((b) => (b.blocker_user_id === requesterId ? b.blocked_user_id : b.blocker_user_id))
    );
    return this.data.users
      .filter(
        (u) =>
          u.id !== requesterId &&
          !blockedIds.has(u.id) &&
          (u.username.toLowerCase().includes(q) || u.display_name.toLowerCase().includes(q))
      )
      .sort((a, b) => {
        const aExact = a.username.toLowerCase() === q ? 0 : 1;
        const bExact = b.username.toLowerCase() === q ? 0 : 1;
        return aExact - bExact;
      })
      .slice(0, 25)
      .map(publicUser);
  }

  async getUserById(userId) {
    return publicUser(this.data.users.find((u) => u.id === userId));
  }

  async getUserByUsername(username) {
    const lower = username.replace(/^@/, '').toLowerCase();
    return publicUser(this.data.users.find((u) => u.username.toLowerCase() === lower));
  }

  async listContacts(ownerId) {
    const rows = this.data.contacts.filter((c) => c.owner_user_id === ownerId);
    return rows
      .map((c) => {
        const peer = this.data.users.find((u) => u.id === c.contact_user_id);
        return peer ? { ...publicUser(peer), alias: c.alias || '', addedAt: c.created_at } : null;
      })
      .filter(Boolean);
  }

  async addContact(ownerId, targetUserId, alias = '') {
    if (ownerId === targetUserId) return null;
    const existing = this.data.contacts.find(
      (c) => c.owner_user_id === ownerId && c.contact_user_id === targetUserId
    );
    if (!existing) {
      this.data.contacts.push({
        id: randomId(),
        owner_user_id: ownerId,
        contact_user_id: targetUserId,
        alias,
        created_at: new Date().toISOString(),
      });
      this.persist();
    }
    return this.getUserById(targetUserId);
  }

  async removeContact(ownerId, targetUserId) {
    this.data.contacts = this.data.contacts.filter(
      (c) => !(c.owner_user_id === ownerId && c.contact_user_id === targetUserId)
    );
    this.persist();
  }

  async listBlocked(ownerId) {
    return this.data.blocked
      .filter((b) => b.blocker_user_id === ownerId)
      .map((b) => {
        const peer = this.data.users.find((u) => u.id === b.blocked_user_id);
        return peer ? { ...publicUser(peer), reason: b.reason, blockedAt: b.created_at } : null;
      })
      .filter(Boolean);
  }

  async blockUser(blockerId, blockedId, reason = '') {
    if (blockerId === blockedId) return;
    if (!this.data.blocked.some((b) => b.blocker_user_id === blockerId && b.blocked_user_id === blockedId)) {
      this.data.blocked.push({
        id: randomId(),
        blocker_user_id: blockerId,
        blocked_user_id: blockedId,
        reason: reason.slice(0, 255),
        created_at: new Date().toISOString(),
      });
      this.persist();
    }
  }

  async unblockUser(blockerId, blockedId) {
    this.data.blocked = this.data.blocked.filter(
      (b) => !(b.blocker_user_id === blockerId && b.blocked_user_id === blockedId)
    );
    this.persist();
  }

  async reportUser(reporterId, reportedId, category, details) {
    const report = {
      id: randomId(),
      reporter_user_id: reporterId,
      reported_user_id: reportedId,
      category: category.slice(0, 48),
      details: (details || '').slice(0, 500),
      created_at: new Date().toISOString(),
    };
    this.data.reports.push(report);
    this.persist();
    return report;
  }

  getOrCreateConversation(userA, userB) {
    const [low, high] = userA < userB ? [userA, userB] : [userB, userA];
    let conv = this.data.conversations.find(
      (c) => c.participant_low_id === low && c.participant_high_id === high
    );
    if (!conv) {
      const now = new Date().toISOString();
      conv = {
        id: randomId(),
        participant_low_id: low,
        participant_high_id: high,
        last_message_at: now,
        created_at: now,
      };
      this.data.conversations.push(conv);
    }
    return conv;
  }

  async sendMessage({ id, senderId, recipientId, body, replyToMessageId }) {
    const conv = this.getOrCreateConversation(senderId, recipientId);
    const now = new Date().toISOString();
    const msg = {
      id: id || randomId(),
      conversation_id: conv.id,
      sender_id: senderId,
      recipient_id: recipientId,
      reply_to_message_id: replyToMessageId || null,
      body,
      status: 'SENT',
      deleted_for_all: false,
      created_at: now,
      updated_at: now,
    };
    conv.last_message_at = now;
    this.data.messages.push(msg);
    this.persist();
    return msg;
  }

  async updateMessageStatus(messageId, status) {
    const msg = this.data.messages.find((m) => m.id === messageId);
    if (!msg) return null;
    msg.status = status;
    msg.updated_at = new Date().toISOString();
    this.persist();
    return msg;
  }

  async markConversationRead(readerId, peerId) {
    const now = new Date().toISOString();
    let updated = 0;
    for (const m of this.data.messages) {
      if (m.sender_id === peerId && m.recipient_id === readerId && m.status !== 'READ') {
        m.status = 'READ';
        m.updated_at = now;
        updated++;
      }
    }
    if (updated > 0) this.persist();
    return updated;
  }

  async deleteMessage(userId, messageId) {
    const msg = this.data.messages.find((m) => m.id === messageId && m.sender_id === userId);
    if (!msg) return null;
    msg.deleted_for_all = true;
    msg.body = '';
    msg.updated_at = new Date().toISOString();
    this.persist();
    return msg;
  }

  async clearConversation(userId, peerId) {
    const [low, high] = userId < peerId ? [userId, peerId] : [peerId, userId];
    const conv = this.data.conversations.find(
      (c) => c.participant_low_id === low && c.participant_high_id === high
    );
    if (!conv) return;
    this.data.messages = this.data.messages.filter((m) => m.conversation_id !== conv.id);
    this.persist();
  }

  async listConversations(userId) {
    const convs = this.data.conversations
      .filter((c) => c.participant_low_id === userId || c.participant_high_id === userId)
      .sort((a, b) => b.last_message_at.localeCompare(a.last_message_at));

    return convs
      .map((c) => {
        const peerId = c.participant_low_id === userId ? c.participant_high_id : c.participant_low_id;
        const peer = this.data.users.find((u) => u.id === peerId);
        if (!peer) return null;
        const msgs = this.data.messages
          .filter((m) => m.conversation_id === c.id)
          .sort((a, b) => a.created_at.localeCompare(b.created_at));
        const last = msgs[msgs.length - 1] || null;
        const unreadCount = msgs.filter(
          (m) => m.recipient_id === userId && m.status !== 'READ' && !m.deleted_for_all
        ).length;
        return {
          conversationId: c.id,
          peer: publicUser(peer),
          lastMessage: last
            ? {
                id: last.id,
                senderId: last.sender_id,
                body: last.deleted_for_all ? 'Message deleted' : last.body,
                status: last.status,
                createdAt: last.created_at,
              }
            : null,
          unreadCount,
          lastMessageAt: c.last_message_at,
        };
      })
      .filter(Boolean);
  }

  async getMessages(userId, peerId) {
    const [low, high] = userId < peerId ? [userId, peerId] : [peerId, userId];
    const conv = this.data.conversations.find(
      (c) => c.participant_low_id === low && c.participant_high_id === high
    );
    if (!conv) return [];
    return this.data.messages
      .filter((m) => m.conversation_id === conv.id)
      .sort((a, b) => a.created_at.localeCompare(b.created_at))
      .map((m) => ({
        id: m.id,
        conversationId: m.conversation_id,
        senderId: m.sender_id,
        recipientId: m.recipient_id,
        replyToMessageId: m.reply_to_message_id,
        body: m.deleted_for_all ? 'Message deleted' : m.body,
        status: m.status,
        deletedForAll: Boolean(m.deleted_for_all),
        createdAt: m.created_at,
      }));
  }

  async saveCallRecord({ id, callerId, calleeId, callType, status, durationSeconds, networkQuality }) {
    const now = new Date().toISOString();
    let call = this.data.calls.find((c) => c.id === id);
    if (!call) {
      call = {
        id: id || randomId(),
        caller_id: callerId,
        callee_id: calleeId,
        call_type: callType || 'AUDIO',
        status: status || 'ENDED',
        started_at: now,
        ended_at: status === 'RINGING' || status === 'ACTIVE' ? null : now,
        duration_seconds: durationSeconds || 0,
        network_quality: networkQuality || 'GOOD',
      };
      this.data.calls.push(call);
    } else {
      call.status = status || call.status;
      call.duration_seconds = durationSeconds ?? call.duration_seconds;
      call.network_quality = networkQuality || call.network_quality;
      if (status && status !== 'RINGING' && status !== 'ACTIVE') {
        call.ended_at = now;
      }
    }
    this.persist();
    return call;
  }

  async listCalls(userId) {
    return this.data.calls
      .filter((c) => c.caller_id === userId || c.callee_id === userId)
      .sort((a, b) => b.started_at.localeCompare(a.started_at))
      .slice(0, 100)
      .map((c) => {
        const peerId = c.caller_id === userId ? c.callee_id : c.caller_id;
        const peer = this.data.users.find((u) => u.id === peerId);
        return {
          id: c.id,
          direction: c.caller_id === userId ? 'OUTGOING' : 'INCOMING',
          peer: peer ? publicUser(peer) : { id: peerId, username: 'deleted_user', displayName: 'Deleted User' },
          callType: c.call_type,
          status: c.status,
          startedAt: c.started_at,
          endedAt: c.ended_at,
          durationSeconds: c.duration_seconds,
          networkQuality: c.network_quality,
        };
      });
  }
}

class MySqlStore {
  constructor() {
    this.pool = mysql.createPool({
      host: process.env.MYSQL_HOST || '127.0.0.1',
      port: Number(process.env.MYSQL_PORT || 3306),
      user: process.env.MYSQL_USER || 'worldlink',
      password: process.env.MYSQL_PASSWORD || 'worldlink_secret',
      database: process.env.MYSQL_DATABASE || 'worldlink',
      waitForConnections: true,
      connectionLimit: 10,
    });
  }

  async isUsernameAvailable(username) {
    const [rows] = await this.pool.execute(
      'SELECT 1 FROM users WHERE username_lower = LOWER(?) LIMIT 1',
      [username]
    );
    return rows.length === 0;
  }

  async registerUser({ username, displayName, deviceLabel }) {
    const id = randomId();
    const recoveryKey = generateRecoveryKey();
    const token = generateToken();
    const color = pickAvatarColor(username);
    const conn = await this.pool.getConnection();
    try {
      await conn.beginTransaction();
      await conn.execute(
        `INSERT INTO users (id, username, display_name, bio, avatar_color, recovery_key_hash, is_online)
         VALUES (?, ?, ?, 'Available on WorldLink', ?, ?, 1)`,
        [id, username, displayName?.trim() || username, color, sha256(recoveryKey)]
      );
      await conn.execute(
        `INSERT INTO device_sessions (id, user_id, device_label, token_hash) VALUES (?, ?, ?, ?)`,
        [randomId(), id, deviceLabel || 'Android Device', sha256(token)]
      );
      await conn.commit();
      const user = await this.getUserById(id);
      return { user, token, recoveryKey };
    } catch (err) {
      await conn.rollback();
      if (err.code === 'ER_DUP_ENTRY') {
        const dup = new Error('Username already taken');
        dup.code = 'USERNAME_TAKEN';
        throw dup;
      }
      throw err;
    } finally {
      conn.release();
    }
  }

  async recoverUser({ username, recoveryKey, deviceLabel }) {
    const [rows] = await this.pool.execute(
      'SELECT * FROM users WHERE username_lower = LOWER(?) LIMIT 1',
      [username]
    );
    const user = rows[0];
    if (!user || user.recovery_key_hash !== sha256(recoveryKey.trim().toUpperCase())) {
      return null;
    }
    const token = generateToken();
    await this.pool.execute(
      'INSERT INTO device_sessions (id, user_id, device_label, token_hash) VALUES (?, ?, ?, ?)',
      [randomId(), user.id, deviceLabel || 'Recovered Android Device', sha256(token)]
    );
    await this.setOnlineStatus(user.id, true);
    return { user: publicUser(user), token };
  }

  async getUserByToken(token) {
    const [rows] = await this.pool.execute(
      `SELECT u.* FROM device_sessions ds
       JOIN users u ON u.id = ds.user_id
       WHERE ds.token_hash = ? LIMIT 1`,
      [sha256(token)]
    );
    if (!rows[0]) return null;
    await this.pool.execute('UPDATE device_sessions SET last_active_at = NOW() WHERE token_hash = ?', [
      sha256(token),
    ]);
    return publicUser(rows[0]);
  }

  async setOnlineStatus(userId, isOnline) {
    await this.pool.execute('UPDATE users SET is_online = ?, last_seen_at = NOW() WHERE id = ?', [
      isOnline ? 1 : 0,
      userId,
    ]);
  }

  async updateProfile(userId, { displayName, bio }) {
    await this.pool.execute(
      `UPDATE users SET
         display_name = COALESCE(NULLIF(?, ''), display_name),
         bio = COALESCE(?, bio)
       WHERE id = ?`,
      [displayName?.trim()?.slice(0, 64) ?? null, bio?.trim()?.slice(0, 160) ?? null, userId]
    );
    return this.getUserById(userId);
  }

  async changeUsername(userId, newUsername) {
    try {
      await this.pool.execute('UPDATE users SET username = ? WHERE id = ?', [newUsername, userId]);
      return this.getUserById(userId);
    } catch (err) {
      if (err.code === 'ER_DUP_ENTRY') {
        const dup = new Error('Username already taken');
        dup.code = 'USERNAME_TAKEN';
        throw dup;
      }
      throw err;
    }
  }

  async deleteUser(userId) {
    await this.pool.execute('DELETE FROM users WHERE id = ?', [userId]);
  }

  async isBlockedBetween(userA, userB) {
    const [rows] = await this.pool.execute(
      `SELECT 1 FROM blocked_users
       WHERE (blocker_user_id = ? AND blocked_user_id = ?)
          OR (blocker_user_id = ? AND blocked_user_id = ?)
       LIMIT 1`,
      [userA, userB, userB, userA]
    );
    return rows.length > 0;
  }

  async searchUsers(requesterId, query) {
    const q = query.trim().replace(/^@/, '');
    if (!q) return [];
    const like = `%${q}%`;
    const [rows] = await this.pool.execute(
      `SELECT u.* FROM users u
       WHERE u.id != ?
         AND (u.username LIKE ? OR u.display_name LIKE ?)
         AND NOT EXISTS (
           SELECT 1 FROM blocked_users b
           WHERE (b.blocker_user_id = ? AND b.blocked_user_id = u.id)
              OR (b.blocker_user_id = u.id AND b.blocked_user_id = ?)
         )
       ORDER BY (u.username_lower = LOWER(?)) DESC, u.is_online DESC, u.username ASC
       LIMIT 25`,
      [requesterId, like, like, requesterId, requesterId, q]
    );
    return rows.map(publicUser);
  }

  async getUserById(userId) {
    const [rows] = await this.pool.execute('SELECT * FROM users WHERE id = ? LIMIT 1', [userId]);
    return publicUser(rows[0]);
  }

  async getUserByUsername(username) {
    const clean = username.replace(/^@/, '');
    const [rows] = await this.pool.execute(
      'SELECT * FROM users WHERE username_lower = LOWER(?) LIMIT 1',
      [clean]
    );
    return publicUser(rows[0]);
  }

  async listContacts(ownerId) {
    const [rows] = await this.pool.execute(
      `SELECT u.*, c.alias, c.created_at AS added_at
       FROM contacts c JOIN users u ON u.id = c.contact_user_id
       WHERE c.owner_user_id = ?
       ORDER BY u.is_online DESC, u.username ASC`,
      [ownerId]
    );
    return rows.map((r) => ({ ...publicUser(r), alias: r.alias || '', addedAt: r.added_at }));
  }

  async addContact(ownerId, targetUserId, alias = '') {
    if (ownerId === targetUserId) return null;
    await this.pool.execute(
      `INSERT IGNORE INTO contacts (id, owner_user_id, contact_user_id, alias) VALUES (?, ?, ?, ?)`,
      [randomId(), ownerId, targetUserId, alias || null]
    );
    return this.getUserById(targetUserId);
  }

  async removeContact(ownerId, targetUserId) {
    await this.pool.execute(
      'DELETE FROM contacts WHERE owner_user_id = ? AND contact_user_id = ?',
      [ownerId, targetUserId]
    );
  }

  async listBlocked(ownerId) {
    const [rows] = await this.pool.execute(
      `SELECT u.*, b.reason, b.created_at AS blocked_at
       FROM blocked_users b JOIN users u ON u.id = b.blocked_user_id
       WHERE b.blocker_user_id = ?`,
      [ownerId]
    );
    return rows.map((r) => ({ ...publicUser(r), reason: r.reason, blockedAt: r.blocked_at }));
  }

  async blockUser(blockerId, blockedId, reason = '') {
    if (blockerId === blockedId) return;
    await this.pool.execute(
      `INSERT IGNORE INTO blocked_users (id, blocker_user_id, blocked_user_id, reason) VALUES (?, ?, ?, ?)`,
      [randomId(), blockerId, blockedId, reason.slice(0, 255)]
    );
  }

  async unblockUser(blockerId, blockedId) {
    await this.pool.execute(
      'DELETE FROM blocked_users WHERE blocker_user_id = ? AND blocked_user_id = ?',
      [blockerId, blockedId]
    );
  }

  async reportUser(reporterId, reportedId, category, details) {
    const id = randomId();
    await this.pool.execute(
      `INSERT INTO abuse_reports (id, reporter_user_id, reported_user_id, category, details) VALUES (?, ?, ?, ?, ?)`,
      [id, reporterId, reportedId, category.slice(0, 48), (details || '').slice(0, 500)]
    );
    return { id };
  }

  async getOrCreateConversation(userA, userB) {
    const [low, high] = userA < userB ? [userA, userB] : [userB, userA];
    const [existing] = await this.pool.execute(
      'SELECT * FROM conversations WHERE participant_low_id = ? AND participant_high_id = ? LIMIT 1',
      [low, high]
    );
    if (existing[0]) return existing[0];
    const id = randomId();
    await this.pool.execute(
      `INSERT IGNORE INTO conversations (id, participant_low_id, participant_high_id) VALUES (?, ?, ?)`,
      [id, low, high]
    );
    const [created] = await this.pool.execute(
      'SELECT * FROM conversations WHERE participant_low_id = ? AND participant_high_id = ? LIMIT 1',
      [low, high]
    );
    return created[0];
  }

  async sendMessage({ id, senderId, recipientId, body, replyToMessageId }) {
    const conv = await this.getOrCreateConversation(senderId, recipientId);
    const msgId = id || randomId();
    await this.pool.execute(
      `INSERT INTO messages (id, conversation_id, sender_id, recipient_id, reply_to_message_id, body, status)
       VALUES (?, ?, ?, ?, ?, ?, 'SENT')`,
      [msgId, conv.id, senderId, recipientId, replyToMessageId || null, body]
    );
    await this.pool.execute('UPDATE conversations SET last_message_at = NOW() WHERE id = ?', [conv.id]);
    const [rows] = await this.pool.execute('SELECT * FROM messages WHERE id = ?', [msgId]);
    return rows[0];
  }

  async updateMessageStatus(messageId, status) {
    await this.pool.execute('UPDATE messages SET status = ? WHERE id = ?', [status, messageId]);
    const [rows] = await this.pool.execute('SELECT * FROM messages WHERE id = ?', [messageId]);
    return rows[0] || null;
  }

  async markConversationRead(readerId, peerId) {
    const [result] = await this.pool.execute(
      `UPDATE messages SET status = 'READ' WHERE sender_id = ? AND recipient_id = ? AND status != 'READ'`,
      [peerId, readerId]
    );
    return result.affectedRows;
  }

  async deleteMessage(userId, messageId) {
    await this.pool.execute(
      `UPDATE messages SET deleted_for_all = 1, body = '' WHERE id = ? AND sender_id = ?`,
      [messageId, userId]
    );
    const [rows] = await this.pool.execute('SELECT * FROM messages WHERE id = ?', [messageId]);
    return rows[0] || null;
  }

  async clearConversation(userId, peerId) {
    const [low, high] = userId < peerId ? [userId, peerId] : [peerId, userId];
    const [convs] = await this.pool.execute(
      'SELECT id FROM conversations WHERE participant_low_id = ? AND participant_high_id = ?',
      [low, high]
    );
    if (convs[0]) {
      await this.pool.execute('DELETE FROM messages WHERE conversation_id = ?', [convs[0].id]);
    }
  }

  async listConversations(userId) {
    const [convs] = await this.pool.execute(
      `SELECT * FROM conversations
       WHERE participant_low_id = ? OR participant_high_id = ?
       ORDER BY last_message_at DESC`,
      [userId, userId]
    );
    const results = [];
    for (const c of convs) {
      const peerId = c.participant_low_id === userId ? c.participant_high_id : c.participant_low_id;
      const peer = await this.getUserById(peerId);
      if (!peer) continue;
      const [lastRows] = await this.pool.execute(
        'SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 1',
        [c.id]
      );
      const [unreadRows] = await this.pool.execute(
        `SELECT COUNT(*) AS cnt FROM messages
         WHERE conversation_id = ? AND recipient_id = ? AND status != 'READ' AND deleted_for_all = 0`,
        [c.id, userId]
      );
      const last = lastRows[0];
      results.push({
        conversationId: c.id,
        peer,
        lastMessage: last
          ? {
              id: last.id,
              senderId: last.sender_id,
              body: last.deleted_for_all ? 'Message deleted' : last.body,
              status: last.status,
              createdAt: last.created_at,
            }
          : null,
        unreadCount: Number(unreadRows[0]?.cnt || 0),
        lastMessageAt: c.last_message_at,
      });
    }
    return results;
  }

  async getMessages(userId, peerId) {
    const [low, high] = userId < peerId ? [userId, peerId] : [peerId, userId];
    const [convs] = await this.pool.execute(
      'SELECT id FROM conversations WHERE participant_low_id = ? AND participant_high_id = ? LIMIT 1',
      [low, high]
    );
    if (!convs[0]) return [];
    const [rows] = await this.pool.execute(
      'SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC',
      [convs[0].id]
    );
    return rows.map((m) => ({
      id: m.id,
      conversationId: m.conversation_id,
      senderId: m.sender_id,
      recipientId: m.recipient_id,
      replyToMessageId: m.reply_to_message_id,
      body: m.deleted_for_all ? 'Message deleted' : m.body,
      status: m.status,
      deletedForAll: Boolean(m.deleted_for_all),
      createdAt: m.created_at,
    }));
  }

  async saveCallRecord({ id, callerId, calleeId, callType, status, durationSeconds, networkQuality }) {
    const callId = id || randomId();
    await this.pool.execute(
      `INSERT INTO calls (id, caller_id, callee_id, call_type, status, duration_seconds, network_quality)
       VALUES (?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         status = VALUES(status),
         duration_seconds = VALUES(duration_seconds),
         network_quality = VALUES(network_quality),
         ended_at = IF(VALUES(status) IN ('ENDED','MISSED','DECLINED','FAILED'), NOW(), ended_at)`,
      [callId, callerId, calleeId, callType || 'AUDIO', status || 'ENDED', durationSeconds || 0, networkQuality || 'GOOD']
    );
    return { id: callId };
  }

  async listCalls(userId) {
    const [rows] = await this.pool.execute(
      `SELECT * FROM calls WHERE caller_id = ? OR callee_id = ? ORDER BY started_at DESC LIMIT 100`,
      [userId, userId]
    );
    const out = [];
    for (const c of rows) {
      const peerId = c.caller_id === userId ? c.callee_id : c.caller_id;
      const peer = (await this.getUserById(peerId)) || {
        id: peerId,
        username: 'deleted_user',
        displayName: 'Deleted User',
      };
      out.push({
        id: c.id,
        direction: c.caller_id === userId ? 'OUTGOING' : 'INCOMING',
        peer,
        callType: c.call_type,
        status: c.status,
        startedAt: c.started_at,
        endedAt: c.ended_at,
        durationSeconds: c.duration_seconds,
        networkQuality: c.network_quality,
      });
    }
    return out;
  }
}

export function createStore() {
  if (DB_DRIVER === 'mysql') {
    console.log('[DB] Using MySQL/MariaDB persistence driver');
    return new MySqlStore();
  }
  console.log(`[DB] Using local persistent JSON store at ${DATA_FILE}`);
  return new FileStore(DATA_FILE);
}
