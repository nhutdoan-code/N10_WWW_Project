import { trackingService, getSessionId } from '../service/trackingService';

export function getVisitorId() {
  return getSessionId();
}

export function resetVisitorId() {
  try {
    sessionStorage.removeItem('phonenest_session_id');
  } catch {
    // Ignore storage errors
  }
}

export function recordActivity(event) {
  return trackingService.record(event);
}
