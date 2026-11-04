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
        ? cartService.sync(guest.map((i) => ({
          maPhienBan: i.phienBan.maPhienBan,
          soLuong: i.soLuong,
        })))
        : cartService.get();

    request
        .then((cart) => {
          storage.remove(KEY);
          setItems(cart.items || []);
        })
        .catch(() => setItems([]));
  }, [user?.maTk, isCustomer]);

  const saveGuest = (next) => {
    storage.set(KEY, next);
    setItems(next);
  };

  // Thêm phiên bản
  const add = async (sp, phienBan, soLuong = 1) => {
    if (!phienBan) throw new Error('Vui lòng chọn phiên bản sản phẩm');

    const qty = Number(soLuong);
    if (!Number.isInteger(qty) || qty < 1) {
      throw new Error('Số lượng không hợp lệ');
    }

    if (isCustomer) {
      const cart = await cartService.add(phienBan.maPhienBan, qty);
      setItems(cart.items || []);
      return;
    }

    const current = items.find(
        (i) => i.phienBan.maPhienBan === phienBan.maPhienBan
    );

    const totalQty = (current?.soLuong || 0) + qty;

    if (totalQty > Number(phienBan.soLuongTon)) {
      throw new Error(`Chỉ còn ${phienBan.soLuongTon} sản phẩm trong kho`);
    }

    saveGuest(
        current
            ? items.map((i) =>
                i.phienBan.maPhienBan === phienBan.maPhienBan
                    ? { ...i, soLuong: totalQty }
                    : i
            )
            : [...items, { sanPham: sp, phienBan, soLuong: qty }]
    );
  };

  // Cập nhật số lượng
  const setQuantity = async (item, soLuong) => {
    const { phienBan } = item;
    const qty = Number(soLuong);

    if (!Number.isInteger(qty)) {
      throw new Error('Số lượng không hợp lệ');
    }

    if (qty <= 0) {
      await remove(item);
      return;
    }

    if (qty > Number(phienBan.soLuongTon)) {
      throw new Error(`Chỉ còn ${phienBan.soLuongTon} sản phẩm trong kho`);
    }

    if (isCustomer) {
      const cart = await cartService.update(phienBan.maPhienBan, qty);
      setItems(cart.items || []);
      return;
    }

    saveGuest(
        items.map((i) =>
            i.phienBan.maPhienBan === phienBan.maPhienBan
                ? { ...i, soLuong: qty }
                : i
        )
    );
  };

  // Xóa một phiên bản
  const remove = async (item) => {
    const maPhienBan = item.phienBan.maPhienBan;

    if (isCustomer) {
      const cart = await cartService.remove(maPhienBan);
      setItems(cart.items || []);
      return;
    }

    saveGuest(
        items.filter((i) => i.phienBan.maPhienBan !== maPhienBan)
    );
  };

  // Xóa toàn bộ giỏ hàng
  const clear = async () => {
    if (isCustomer) {
      const cart = await cartService.clear();
      setItems(cart.items || []);
    } else {
      saveGuest([]);
    }
  };

  const replace = (cart) => setItems(cart.items || []);
  const reset = () => setItems([]);

  const count = items.reduce((sum, i) => sum + i.soLuong, 0);

  const total = items.reduce((sum, i) => sum + Number(i.phienBan?.giaBan ?? i.sanPham?.giaBan ?? 0) * i.soLuong, 0);
  return (
      <CartContext.Provider value={{ items, count, total, add, setQuantity, remove, clear, replace, reset }}>
        {children}
      </CartContext.Provider>
  );
}