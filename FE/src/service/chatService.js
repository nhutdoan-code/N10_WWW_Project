import api from '../config/api';

export const chatService = {
  send: (message, history, visitorId) => api.post('/chat', { message, history, visitorId }),
};
