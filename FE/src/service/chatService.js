import api from '../config/api';

// Lấy hoặc tạo visitorId hợp lệ
const getVisitorId = () => {
  let visitorId = localStorage.getItem('visitorId');

  // Nếu chưa có hoặc không đúng UUID v4 thì tạo mới
  const uuidRegex =
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

  if (!visitorId || !uuidRegex.test(visitorId)) {
    visitorId = crypto.randomUUID();
    localStorage.setItem('visitorId', visitorId);
  }

  return visitorId;
};

export const chatService = {
  send: (message, history = [], visitorId) => {
    const id = getVisitorId();

    return api.post('/chat', {
      message,
      history,
      visitorId: id
    });
  }
};