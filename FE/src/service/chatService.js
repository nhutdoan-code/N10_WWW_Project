import api from '../config/api';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const getVisitorId = () => {
  let id = localStorage.getItem('visitorId');
  if (!id || !UUID_REGEX.test(id)) {
    id = crypto.randomUUID();
    localStorage.setItem('visitorId', id);
  }
  return id;
};

export const chatService = {
  // Lịch sử hội thoại do backend lưu theo conversationId (null = bắt đầu hội thoại mới).
  send: (message, conversationId = null, visitorId, config = {}) =>
    api.post('/chat', {
      message,
      conversationId: conversationId || null,
      // Backend chỉ nhận visitorId dạng UUID v4; id phiên của tracking ("sess_...") không hợp lệ -> dùng UUID riêng.
      visitorId: visitorId && UUID_REGEX.test(visitorId) ? visitorId : getVisitorId(),
    }, config),

  getConversations: () => api.get('/chat/conversations'),

  getMessages: (conversationId) =>
    api.get(`/chat/conversations/${encodeURIComponent(conversationId)}/messages`),
};
