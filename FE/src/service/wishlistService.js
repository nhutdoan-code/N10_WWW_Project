import api from '../config/api';

export const wishlistService = {
  list: () => api.get('/customer/wishlist'),
  add: (ma) => api.post(`/customer/wishlist/${ma}`),
  remove: (ma) => api.delete(`/customer/wishlist/${ma}`),
};
