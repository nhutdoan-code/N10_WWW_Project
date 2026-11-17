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
  send: (message, conversationId = null) =>
      api.post('/chat', { message, conversationId, visitorId: getVisitorId() }),

  getConversations: () => api.get('/chat/conversations'),

  getMessages: (conversationId) =>
      api.get(`/chat/conversations/${encodeURIComponent(conversationId)}/messages`),
};