import { createContext, useState } from 'react';
import { STORAGE_KEYS } from '../config/constants';
import { storage } from '../utils/storage';

export const CompareContext = createContext(null);

const MAX = 3;

export function CompareProvider({ children }) {
  const [ids, setIds] = useState(() => storage.get(STORAGE_KEYS.compare) || []);

  const save = (next) => {
    storage.set(STORAGE_KEYS.compare, next);
    setIds(next);
  };

  const toggle = (ma) => {
    if (ids.includes(ma)) return save(ids.filter((i) => i !== ma));
    if (ids.length >= MAX) throw new Error(`Chỉ so sánh tối đa ${MAX} sản phẩm`);
    save([...ids, ma]);
  };

  return <CompareContext.Provider value={{ ids, toggle, clear: () => save([]) }}>{children}</CompareContext.Provider>;
}
