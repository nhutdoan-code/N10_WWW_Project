import { createContext, useEffect, useState } from 'react';
import { STORAGE_KEYS } from '../config/constants';
import { cartService } from '../service/cartService';
import { productService } from '../service/productService';
import { isItemDiscontinued, isItemUnapproved } from '../utils/discontinuedProductRules';
import { refreshGuestItems } from '../utils/guestCart';
import { storage } from '../utils/storage';
import useAuth from '../hooks/useAuth';
import useToast from '../hooks/useToast';

export const CartContext = createContext(null);
const KEY = STORAGE_KEYS.guestCart;

export function CartProvider({ children }) {
  const { user, isCustomer } = useAuth();
  const toast = useToast();
  const [items, setItems] = useState([]);

  useEffect(() => {
    const guest = storage.get(KEY) || [];

    if (!isCustomer) {
      // Hiển thị ngay ở chế độ fail-closed, sau đó thay bằng dữ liệu mới từ Backend.
      setItems(guest.map((i) => ({ ...i, phienBan: { ...i.phienBan, duocPhepBan: false } })));
      let alive = true;
      refreshGuestItems(guest, productService.get).then((next) => {
        if (!alive) return;
        storage.set(KEY, next);
        setItems(next);
      });
      return () => { alive = false; };
    }

    let alive = true;
    const loadServerCart = () => cartService.get()
      .then((cart) => { if (alive) setItems(cart.items || []); })
      .catch(() => { if (alive) setItems([]); });

    if (!guest.length) {
      loadServerCart();
      return () => { alive = false; };
    }

    // Bỏ dòng hỏng (thiếu mã / số lượng không hợp lệ): một dòng lỗi sẽ làm Backend từ chối cả request.
    const dongHopLe = guest
      .map((i) => ({ maPhienBan: i?.phienBan?.maPhienBan, soLuong: Number(i?.soLuong) }))
      .filter((i) => i.maPhienBan && Number.isInteger(i.soLuong) && i.soLuong >= 1);
    if (!dongHopLe.length) {
      storage.remove(KEY);
      loadServerCart();
      return () => { alive = false; };
    }

    cartService.sync(dongHopLe)
      .then((cart) => {
        // Chỉ xóa giỏ khách khi Backend đã nhận đồng bộ; dòng bị giảm/bỏ được báo qua canhBao.
        storage.remove(KEY);
        if (!alive) return;
        setItems(cart.items || []);
        const canhBao = cart.canhBao || [];
        if (canhBao.length && toast) {
          toast(canhBao.length === 1 ? canhBao[0] : `${canhBao[0]} (và ${canhBao.length - 1} thông báo khác về giỏ hàng)`, 'error');
        }
      })
      // Đồng bộ lỗi: giữ giỏ khách trong localStorage để thử lại lần sau, hiển thị giỏ tài khoản.
      .catch(() => loadServerCart());
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    const current = items.find((i) => i.phienBan.maPhienBan === phienBan.maPhienBan);
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

  // Chỉ cộng các dòng còn mua được: giá của biến thể chưa duyệt/khóa bán không phải giá bán.
  const total = items
    .filter((i) => !isItemDiscontinued(i) && !isItemUnapproved(i))
    .reduce((sum, i) => sum + Number(i.phienBan?.giaBan ?? i.sanPham?.giaBan ?? 0) * i.soLuong, 0);
  return (
      <CartContext.Provider value={{ items, count, total, add, setQuantity, remove, clear, replace, reset }}>
        {children}
      </CartContext.Provider>
  );
}