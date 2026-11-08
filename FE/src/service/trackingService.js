import api from '../config/api';

const SESSION_KEY = 'phonenest_session_id';

export function getSessionId() {
  try {
    let sid = sessionStorage.getItem(SESSION_KEY);
    if (!sid) {
      sid = 'sess_' + Math.random().toString(36).substring(2, 15) + '_' + Date.now();
      sessionStorage.setItem(SESSION_KEY, sid);
    }
    return sid;
  } catch {
    return 'sess_fallback_' + Date.now();
  }
}

export const trackingService = {
  track: async ({ loaiHanhVi, maSanPham, maPhienBan, tuKhoa, nguonSuKien, metadata }) => {
    try {
      const payload = {
        sessionId: getSessionId(),
        loaiHanhVi,
        maSanPham: maSanPham || null,
        maPhienBan: maPhienBan || null,
        tuKhoa: tuKhoa || null,
        nguonSuKien: nguonSuKien || null,
        metadata: metadata ? (typeof metadata === 'object' ? JSON.stringify(metadata) : String(metadata)) : null,
      };
      return await api.post('/tracking/events', payload);
    } catch (err) {
      // Non-blocking: silent catch to ensure customer experience is never interrupted
      console.debug('Tracking event silent error:', err?.message);
      return null;
    }
  },

  // Backward compatibility for PR #17 callers
  record: (event) => {
    if (!event) return Promise.resolve(null);
    const loai = event.type || event.loaiHanhVi;
    return trackingService.track({
      loaiHanhVi: loai,
      maSanPham: event.productId || event.maSanPham,
      maPhienBan: event.maPhienBan,
      tuKhoa: event.searchTerm || event.tuKhoa,
      nguonSuKien: event.pageType || event.nguonSuKien,
      metadata: event.metadata,
    });
  },

  trackView: (maSanPham, nguonSuKien = 'PRODUCT_DETAIL') => {
    if (!maSanPham) return;
    return trackingService.track({
      loaiHanhVi: 'PRODUCT_VIEW',
      maSanPham,
      nguonSuKien,
    });
  },

  trackClick: (maSanPham, nguonSuKien = 'PRODUCT_CARD') => {
    if (!maSanPham) return;
    return trackingService.track({
      loaiHanhVi: 'PRODUCT_CLICK',
      maSanPham,
      nguonSuKien,
    });
  },

  trackSearch: (tuKhoa, nguonSuKien = 'SEARCH_BOX') => {
    if (!tuKhoa || !tuKhoa.trim()) return;
    return trackingService.track({
      loaiHanhVi: 'SEARCH',
      tuKhoa: tuKhoa.trim(),
      nguonSuKien,
    });
  },

  trackAddToCart: (maSanPham, maPhienBan, nguonSuKien = 'PRODUCT_DETAIL') => {
    return trackingService.track({
      loaiHanhVi: 'ADD_TO_CART',
      maSanPham,
      maPhienBan,
      nguonSuKien,
    });
  },

  trackRemoveFromCart: (maSanPham, maPhienBan, nguonSuKien = 'CART_PAGE') => {
    return trackingService.track({
      loaiHanhVi: 'REMOVE_FROM_CART',
      maSanPham,
      maPhienBan,
      nguonSuKien,
    });
  },

  trackRecommendationClick: (maSanPham, nguonSuKien = 'AI_CHATBOT') => {
    return trackingService.track({
      loaiHanhVi: 'RECOMMENDATION_CLICK',
      maSanPham,
      nguonSuKien,
    });
  },

  // Admin APIs
  getBehaviorStats: (params) => api.get('/admin/analytics/behavior', { params }),
  getUserPreferences: (maKh) => api.get(`/admin/analytics/preferences/${maKh}`),
};
