export const CHAT_STORAGE_PREFIX = 'phonenest_chat';
export const GUEST_SESSION_KEY = 'phonenest_chat_guest_session_id';
const LEGACY_STORAGE_KEY = 'phonenest-chat';

export const DEFAULT_GREETING = {
  role: 'assistant',
  content:
    'Xin chào! Mình là trợ lý PhoneNest. Bạn cần tư vấn chọn điện thoại, hỏi phí giao hàng hay voucher? Cứ nhắn cho mình nhé.',
};

/**
 * Gets or creates a unique session identifier for guest chat.
 */
export function getGuestChatSessionId() {
  try {
    let sid = sessionStorage.getItem(GUEST_SESSION_KEY);
    if (!sid) {
      sid = 'guest_' + Math.random().toString(36).substring(2, 11) + '_' + Date.now();
      sessionStorage.setItem(GUEST_SESSION_KEY, sid);
    }
    return sid;
  } catch {
    return 'guest_fallback';
  }
}

/**
 * Resets the guest chat session and cleans up guest chat history in sessionStorage.
 * Does not remove authenticated user chat histories.
 */
export function resetGuestChatSession() {
  try {
    const currentGuestId = sessionStorage.getItem(GUEST_SESSION_KEY);
    if (currentGuestId) {
      sessionStorage.removeItem(`${CHAT_STORAGE_PREFIX}_guest_${currentGuestId}`);
      sessionStorage.removeItem(GUEST_SESSION_KEY);
    }
    // Clean up legacy shared key if it exists
    sessionStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    // Ignore storage errors
  }
}

/**
 * Resolves the isolated chat storage key for a user or guest.
 * Returns null if authentication is in an uninitialized state.
 */
export function getChatStorageKey(user, isInitialized = true) {
  if (!isInitialized) return null;

  // Authenticated user: identify strictly by unique user account ID (maTk)
  if (user && user.maTk) {
    return `${CHAT_STORAGE_PREFIX}_user_${user.maTk}`;
  }

  // Guest user with guest-session namespace
  const guestId = getGuestChatSessionId();
  return `${CHAT_STORAGE_PREFIX}_guest_${guestId}`;
}

/**
 * Loads conversation history for the given storage key.
 */
export function loadChatHistory(storageKey) {
  if (!storageKey) return [DEFAULT_GREETING];
  try {
    const raw = sessionStorage.getItem(storageKey);
    if (!raw) return [DEFAULT_GREETING];
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed;
    }
    return [DEFAULT_GREETING];
  } catch {
    return [DEFAULT_GREETING];
  }
}

/**
 * Saves conversation history for the given storage key.
 */
export function saveChatHistory(storageKey, messages) {
  if (!storageKey || !Array.isArray(messages)) return;
  try {
    sessionStorage.setItem(storageKey, JSON.stringify(messages));
  } catch {
    // Ignore quota or private mode storage errors
  }
}
