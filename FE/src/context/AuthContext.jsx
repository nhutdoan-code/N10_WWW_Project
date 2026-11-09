import { createContext, useState } from 'react';
import { STORAGE_KEYS } from '../config/constants';
import { authService } from '../service/authService';
import { storage } from '../utils/storage';
import { resetVisitorId } from '../utils/activityTracking';
import { resetGuestChatSession } from '../utils/chatStorage';

export const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => storage.get(STORAGE_KEYS.user));

  const login = async (taiKhoan, matKhau) => {
    const data = await authService.login(taiKhoan, matKhau);
    const previousUser = storage.get(STORAGE_KEYS.user);
    if (previousUser?.maTk && previousUser.maTk !== data.maTk) resetVisitorId();
    resetGuestChatSession();
    storage.set(STORAGE_KEYS.user, data);
    setUser(data);
    return data;
  };

  const logout = () => {
    storage.remove(STORAGE_KEYS.user);
    resetVisitorId();
    resetGuestChatSession();
    setUser(null);
  };

  const value = {
    user,
    login,
    logout,
    isAdmin: user?.role === 'ADMIN',
    isCustomer: user?.role === 'CUSTOMER',
    isInitialized: true,
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
