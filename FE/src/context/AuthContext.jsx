import { createContext, useState } from 'react';
import { STORAGE_KEYS } from '../config/constants';
import { authService } from '../service/authService';
import { storage } from '../utils/storage';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => storage.get(STORAGE_KEYS.user));

  const login = async (taiKhoan, matKhau) => {
    const data = await authService.login(taiKhoan, matKhau);
    storage.set(STORAGE_KEYS.user, data);
    setUser(data);
    return data;
  };

  const logout = () => {
    storage.remove(STORAGE_KEYS.user);
    setUser(null);
  };

  const value = { user, login, logout, isAdmin: user?.role === 'ADMIN', isCustomer: user?.role === 'CUSTOMER' };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
