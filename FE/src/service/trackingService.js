import api from '../config/api';

export const trackingService = {
  record: (event) => api.post('/tracking/events', event),
};
