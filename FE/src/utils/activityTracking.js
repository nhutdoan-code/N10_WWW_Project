import { trackingService } from '../service/trackingService';

const KEY = 'phonenest_visitor_id';
let memoryVisitorId;

function createVisitorId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

export function getVisitorId() {
  if (memoryVisitorId) return memoryVisitorId;
  try {
    const saved = localStorage.getItem(KEY);
    if (saved && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(saved)) {
      memoryVisitorId = saved;
      return saved;
    }
    memoryVisitorId = createVisitorId();
    localStorage.setItem(KEY, memoryVisitorId);
  } catch {
    memoryVisitorId = createVisitorId();
  }
  return memoryVisitorId;
}

export function resetVisitorId() {
  memoryVisitorId = createVisitorId();
  try {
    localStorage.setItem(KEY, memoryVisitorId);
  } catch {
    // Keep the new id in memory when browser storage is unavailable.
  }
}

export function recordActivity(event) {
  trackingService.record({ visitorId: getVisitorId(), ...event }).catch(() => {
    console.warn('PhoneNest activity tracking is temporarily unavailable.');
  });
}
