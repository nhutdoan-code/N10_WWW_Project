import api from '../config/api';

export const productService = {
  list: (params) => api.get('/products', { params }),
  get: (ma) => api.get(`/products/${ma}`),
  related: (ma) => api.get(`/products/${ma}/related`),
  reviews: (ma) => api.get(`/products/${ma}/reviews`),
  questions: (ma) => api.get(`/products/${ma}/questions`),
  categories: () => api.get('/categories'),
  brands: () => api.get('/brands'),
  search: (q) => api.get('/search', { params: { q } }),
};
