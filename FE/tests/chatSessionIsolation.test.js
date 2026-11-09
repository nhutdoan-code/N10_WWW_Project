import test from 'node:test';
import assert from 'node:assert/strict';
import {
  CHAT_STORAGE_PREFIX,
  GUEST_SESSION_KEY,
  DEFAULT_GREETING,
  getGuestChatSessionId,
  resetGuestChatSession,
  getChatStorageKey,
  loadChatHistory,
  saveChatHistory,
} from '../src/utils/chatStorage.js';

// Setup Mock sessionStorage for Node.js test environment
class MockSessionStorage {
  constructor() {
    this.store = new Map();
  }
  getItem(key) {
    return this.store.has(key) ? this.store.get(key) : null;
  }
  setItem(key, value) {
    this.store.set(key, String(value));
  }
  removeItem(key) {
    this.store.delete(key);
  }
  clear() {
    this.store.clear();
  }
}

globalThis.sessionStorage = new MockSessionStorage();

test.beforeEach(() => {
  globalThis.sessionStorage.clear();
});

test('A. Identify authenticated user by unique maTk field only', () => {
  const user = {
    maTk: 'TK001',
    hoTen: 'Nguyen Van A',
    email: 'a@example.com',
    role: 'CUSTOMER',
    token: 'jwt.token.secret',
  };

  const key = getChatStorageKey(user, true);
  assert.equal(key, `${CHAT_STORAGE_PREFIX}_user_TK001`);
  // Ensure sensitive token, display name, and role are NOT part of the storage key
  assert.equal(key.includes('jwt.token.secret'), false);
  assert.equal(key.includes('Nguyen Van A'), false);
  assert.equal(key.includes('CUSTOMER'), false);
});

test('B. Guest users use isolated guest-session namespace', () => {
  const guestKey1 = getChatStorageKey(null, true);
  assert.ok(guestKey1.startsWith(`${CHAT_STORAGE_PREFIX}_guest_`));

  // The session ID is stable for subsequent calls within the same guest session
  const guestKey2 = getChatStorageKey(null, true);
  assert.equal(guestKey1, guestKey2);

  // Rotating/resetting guest session creates a fresh namespace
  resetGuestChatSession();
  const guestKey3 = getChatStorageKey(null, true);
  assert.ok(guestKey3.startsWith(`${CHAT_STORAGE_PREFIX}_guest_`));
  assert.notEqual(guestKey1, guestKey3);
});

test('C. Auth state initialization guard prevents premature or leaking storage access', () => {
  // During async auth initialization, key must be null
  assert.equal(getChatStorageKey(null, false), null);
  assert.equal(getChatStorageKey({ maTk: 'TK001' }, false), null);
});

test('Scenario 1: User A chats, logs out, User B logs in — no history leakage', () => {
  const userA = { maTk: 'TK_USER_A', hoTen: 'User A' };
  const userB = { maTk: 'TK_USER_B', hoTen: 'User B' };

  const keyA = getChatStorageKey(userA, true);
  const userAMessages = [
    DEFAULT_GREETING,
    { role: 'user', content: 'User A private question about iPhone' },
    { role: 'assistant', content: 'User A private answer' },
  ];
  saveChatHistory(keyA, userAMessages);

  // User A logs out: guest session is reset
  resetGuestChatSession();

  // User B logs in
  const keyB = getChatStorageKey(userB, true);
  assert.notEqual(keyA, keyB);

  const userBMessages = loadChatHistory(keyB);
  // User B must only see the initial greeting, NEVER User A's conversation
  assert.deepEqual(userBMessages, [DEFAULT_GREETING]);
  assert.equal(
    userBMessages.some((m) => m.content.includes('User A private')),
    false
  );
});

test('Scenario 2: User B chats, logs out, User A logs back in — correct history restored', () => {
  const userA = { maTk: 'TK_USER_A', hoTen: 'User A' };
  const userB = { maTk: 'TK_USER_B', hoTen: 'User B' };

  const keyA = getChatStorageKey(userA, true);
  const userAMessages = [
    DEFAULT_GREETING,
    { role: 'user', content: 'User A message 1' },
    { role: 'assistant', content: 'User A reply 1' },
  ];
  saveChatHistory(keyA, userAMessages);

  // User B logs in and chats
  const keyB = getChatStorageKey(userB, true);
  const userBMessages = [
    DEFAULT_GREETING,
    { role: 'user', content: 'User B message 1' },
    { role: 'assistant', content: 'User B reply 1' },
  ];
  saveChatHistory(keyB, userBMessages);

  // User B logs out
  resetGuestChatSession();

  // User A logs back in
  const restoredKeyA = getChatStorageKey(userA, true);
  const restoredMessagesA = loadChatHistory(restoredKeyA);
  assert.deepEqual(restoredMessagesA, userAMessages);

  // Verify User B's history is also intact under User B's key
  const restoredMessagesB = loadChatHistory(keyB);
  assert.deepEqual(restoredMessagesB, userBMessages);
});

test('Scenario 3: Page refresh restores correct user history', () => {
  const userA = { maTk: 'TK_USER_A', hoTen: 'User A' };
  const keyA = getChatStorageKey(userA, true);

  const messages = [
    DEFAULT_GREETING,
    { role: 'user', content: 'Searching for Xiaomi' },
    { role: 'assistant', content: 'Here are Xiaomi phones', products: [{ maSanPham: 'SP1', tenMay: 'Xiaomi 14' }] },
  ];
  saveChatHistory(keyA, messages);

  // Simulate page reload by re-resolving storage key and loading
  const reloadedKey = getChatStorageKey(userA, true);
  const reloadedMessages = loadChatHistory(reloadedKey);
  assert.deepEqual(reloadedMessages, messages);
  assert.equal(reloadedMessages[2].products[0].tenMay, 'Xiaomi 14');
});

test('Scenario 4: Guest chats then logs in — guest history does not mix into user account', () => {
  // Guest chats
  const guestKey = getChatStorageKey(null, true);
  const guestMessages = [
    DEFAULT_GREETING,
    { role: 'user', content: 'Guest message before login' },
    { role: 'assistant', content: 'Guest reply' },
  ];
  saveChatHistory(guestKey, guestMessages);

  // User logs in: login resets guest session
  resetGuestChatSession();
  const userA = { maTk: 'TK_USER_A', hoTen: 'User A' };
  const userKey = getChatStorageKey(userA, true);

  // User A gets fresh user history, NOT guest messages
  const userMessages = loadChatHistory(userKey);
  assert.deepEqual(userMessages, [DEFAULT_GREETING]);
  assert.equal(
    userMessages.some((m) => m.content.includes('Guest message')),
    false
  );
});

test('Scenario 5: Logout while AI response is pending — stale response protection', async () => {
  const userA = { maTk: 'TK_USER_A' };
  const initialSessionKey = getChatStorageKey(userA, true);

  const controller = new AbortController();
  let isAborted = false;

  // Simulate unmount hook behavior on session change
  const onSessionUnmount = () => {
    isAborted = true;
    controller.abort();
  };

  let simulatedApiResolved = false;
  let simulatedStaleSaved = false;

  // Simulate an async chat request
  const pendingChatPromise = new Promise((resolve, reject) => {
    controller.signal.addEventListener('abort', () => {
      const err = new Error('Request aborted');
      err.name = 'CanceledError';
      reject(err);
    });

    setTimeout(() => {
      resolve({ reply: 'Stale AI response for User A', products: [] });
    }, 50);
  });

  // User sends message
  const userAMessages = [
    DEFAULT_GREETING,
    { role: 'user', content: 'Question before logout' },
  ];
  saveChatHistory(initialSessionKey, userAMessages);

  // User logs out before response resolves!
  onSessionUnmount();
  resetGuestChatSession();

  // Guest mounts
  const guestKey = getChatStorageKey(null, true);

  // Handle request outcome
  try {
    const res = await pendingChatPromise;
    simulatedApiResolved = true;
    if (!isAborted) {
      saveChatHistory(initialSessionKey, [...userAMessages, { role: 'assistant', content: res.reply }]);
      simulatedStaleSaved = true;
    }
  } catch (err) {
    assert.equal(err.name, 'CanceledError');
  }

  // Verify:
  // 1. The request was cancelled
  assert.equal(isAborted, true);
  assert.equal(simulatedStaleSaved, false);

  // 2. Guest conversation has not received User A's response
  const guestMessages = loadChatHistory(guestKey);
  assert.deepEqual(guestMessages, [DEFAULT_GREETING]);

  // 3. If User B logs in, User B has clean conversation
  const userB = { maTk: 'TK_USER_B' };
  const userBKey = getChatStorageKey(userB, true);
  const userBMessages = loadChatHistory(userBKey);
  assert.deepEqual(userBMessages, [DEFAULT_GREETING]);
});

test('Scenario 6: Stored history survives corrupted JSON gracefully', () => {
  sessionStorage.setItem('phonenest_chat_user_CORRUPT', '{ invalid json ...');
  const messages = loadChatHistory('phonenest_chat_user_CORRUPT');
  assert.deepEqual(messages, [DEFAULT_GREETING]);
});
