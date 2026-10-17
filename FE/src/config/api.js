import axios from 'axios';
import { storage } from '../utils/storage';
import { STORAGE_KEYS } from './constants';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  const token = storage.get(STORAGE_KEYS.user)?.token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res.data,
  (error) => {
    if (error.response?.status === 401 && storage.get(STORAGE_KEYS.user)) {
      storage.remove(STORAGE_KEYS.user);
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;
