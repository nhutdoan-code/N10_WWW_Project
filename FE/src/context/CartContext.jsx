import { createContext, useEffect, useState } from 'react';
import { STORAGE_KEYS } from '../config/constants';
import { cartService } from '../service/cartService';
import { storage } from '../utils/storage';
import useAuth from '../hooks/useAuth';

export const CartContext = createContext(null);

const KEY = STORAGE_KEYS.guestCart;

export function CartProvider({ children }) {
  const { user, isCustomer } = useAuth();
  const [items, setItems] = useState([]);

  useEffect(() => {
    const guest = storage.get(KEY) || [];
    if (!isCustomer) {
      setItems(guest);
      return;
    }
    const request = guest.length
      ? cartService.sync(guest.map((i) => ({ maSanPham: i.sanPham.maSanPham, soLuong: i.soLuong })))
      : cartService.get();
    request
      .then((cart) => {
        storage.remove(KEY);
        setItems(cart.items);
      })
      .catch(() => setItems([]));
  }, [user?.maTk, isCustomer]);

  const saveGuest = (next) => {
    storage.set(KEY, next);
    setItems(next);
  };

  const add = async (sp, soLuong = 1) => {
    if (isCustomer) {
      setItems((await cartService.add(sp.maSanPham, soLuong)).items);
      return;
    }
    const current = items.find((i) => i.sanPham.maSanPham === sp.maSanPham);
    const total = (current?.soLuong || 0) + soLuong;
    if (total > sp.soLuongTon) throw new Error(`Chỉ còn ${sp.soLuongTon} sản phẩm trong kho`);
    saveGuest(
      current
        ? items.map((i) => (i === current ? { ...i, soLuong: total } : i))
        : [...items, { sanPham: sp, soLuong }]
    );
  };

  const setQuantity = async (sp, soLuong) => {
    if (isCustomer) {
      setItems((await cartService.update(sp.maSanPham, soLuong)).items);
      return;
    }
    if (soLuong > sp.soLuongTon) throw new Error(`Chỉ còn ${sp.soLuongTon} sản phẩm trong kho`);
    saveGuest(
      soLuong <= 0
        ? items.filter((i) => i.sanPham.maSanPham !== sp.maSanPham)
        : items.map((i) => (i.sanPham.maSanPham === sp.maSanPham ? { ...i, soLuong } : i))
    );
  };

  const remove = (sp) => setQuantity(sp, 0);

  const clear = async () => {
    if (isCustomer) setItems((await cartService.clear()).items);
    else saveGuest([]);
  };

  const replace = (cart) => setItems(cart.items);

  const reset = () => setItems([]);

  const count = items.reduce((sum, i) => sum + i.soLuong, 0);
  const total = items.reduce((sum, i) => sum + i.sanPham.giaBan * i.soLuong, 0);

  return (
    <CartContext.Provider value={{ items, count, total, add, setQuantity, remove, clear, replace, reset }}>{children}</CartContext.Provider>
  );
}
