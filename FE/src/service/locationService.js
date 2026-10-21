import api from '../config/api';

export const locationService = {
  provinces: () => api.get('/locations/provinces'),
  wards: (maTinh) => api.get(`/locations/provinces/${maTinh}/wards`),
};
