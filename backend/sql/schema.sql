-- WorldLink Production Database Schema (MySQL 8.0+ / MariaDB 10.6+)
-- Enforces globally unique @usernames with zero phone/email/password PII.

CREATE DATABASE IF NOT EXISTS worldlink
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE worldlink;

-- 1. Users (Username-based global identity)
CREATE TABLE IF NOT EXISTS users (
  id CHAR(36) NOT NULL,
  username VARCHAR(24) NOT NULL,
  username_lower VARCHAR(24) GENERATED ALWAYS AS (LOWER(username)) STORED,
  display_name VARCHAR(64) NOT NULL,
  bio VARCHAR(160) NOT NULL DEFAULT '',
  avatar_color VARCHAR(16) NOT NULL DEFAULT '#00E5FF',
  recovery_key_hash CHAR(64) NOT NULL,
  is_online TINYINT(1) NOT NULL DEFAULT 0,
  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username_lower (username_lower),
  KEY idx_users_display_name (display_name),
  KEY idx_users_online (is_online, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Device Sessions (Cryptographically random bearer tokens, stored as SHA-256 hashes)
CREATE TABLE IF NOT EXISTS device_sessions (
  id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  device_label VARCHAR(80) NOT NULL DEFAULT 'Android Device',
  token_hash CHAR(64) NOT NULL,
  last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_device_sessions_token_hash (token_hash),
  KEY idx_device_sessions_user (user_id),
  CONSTRAINT fk_device_sessions_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Contacts
CREATE TABLE IF NOT EXISTS contacts (
  id CHAR(36) NOT NULL,
  owner_user_id CHAR(36) NOT NULL,
  contact_user_id CHAR(36) NOT NULL,
  alias VARCHAR(64) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_contacts_pair (owner_user_id, contact_user_id),
  KEY idx_contacts_owner (owner_user_id),
  CONSTRAINT fk_contacts_owner
    FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_contacts_target
    FOREIGN KEY (contact_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Blocked Users
CREATE TABLE IF NOT EXISTS blocked_users (
  id CHAR(36) NOT NULL,
  blocker_user_id CHAR(36) NOT NULL,
  blocked_user_id CHAR(36) NOT NULL,
  reason VARCHAR(255) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_blocked_pair (blocker_user_id, blocked_user_id),
  KEY idx_blocked_blocker (blocker_user_id),
  CONSTRAINT fk_blocked_blocker
    FOREIGN KEY (blocker_user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_blocked_target
    FOREIGN KEY (blocked_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Abuse Reports
CREATE TABLE IF NOT EXISTS abuse_reports (
  id CHAR(36) NOT NULL,
  reporter_user_id CHAR(36) NOT NULL,
  reported_user_id CHAR(36) NOT NULL,
  category VARCHAR(48) NOT NULL,
  details VARCHAR(500) NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_reports_reported (reported_user_id),
  CONSTRAINT fk_reports_reporter
    FOREIGN KEY (reporter_user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_reports_reported
    FOREIGN KEY (reported_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Conversations (1-to-1 canonical pair: participant_low_id < participant_high_id)
CREATE TABLE IF NOT EXISTS conversations (
  id CHAR(36) NOT NULL,
  participant_low_id CHAR(36) NOT NULL,
  participant_high_id CHAR(36) NOT NULL,
  last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_conversations_pair (participant_low_id, participant_high_id),
  CONSTRAINT fk_conv_low FOREIGN KEY (participant_low_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_conv_high FOREIGN KEY (participant_high_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Messages
CREATE TABLE IF NOT EXISTS messages (
  id CHAR(36) NOT NULL,
  conversation_id CHAR(36) NOT NULL,
  sender_id CHAR(36) NOT NULL,
  recipient_id CHAR(36) NOT NULL,
  reply_to_message_id CHAR(36) NULL,
  body TEXT NOT NULL,
  status ENUM('SENT', 'DELIVERED', 'READ') NOT NULL DEFAULT 'SENT',
  deleted_for_all TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_messages_conv_created (conversation_id, created_at),
  KEY idx_messages_recipient_status (recipient_id, status),
  CONSTRAINT fk_messages_conv FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Calls (Voice, Video, Screen Share history)
CREATE TABLE IF NOT EXISTS calls (
  id CHAR(36) NOT NULL,
  caller_id CHAR(36) NOT NULL,
  callee_id CHAR(36) NOT NULL,
  call_type ENUM('AUDIO', 'VIDEO', 'SCREEN_SHARE') NOT NULL,
  status ENUM('RINGING', 'ACTIVE', 'ENDED', 'MISSED', 'DECLINED', 'FAILED') NOT NULL DEFAULT 'RINGING',
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP NULL DEFAULT NULL,
  duration_seconds INT UNSIGNED NOT NULL DEFAULT 0,
  network_quality VARCHAR(16) NOT NULL DEFAULT 'GOOD',
  PRIMARY KEY (id),
  KEY idx_calls_caller (caller_id, started_at),
  KEY idx_calls_callee (callee_id, started_at),
  CONSTRAINT fk_calls_caller FOREIGN KEY (caller_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_calls_callee FOREIGN KEY (callee_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
