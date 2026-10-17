import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PAYMENT_METHODS } from '../config/constants';
import useCart from '../hooks/useCart';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { orderService } from '../service/orderService';
import { errorMessage, formatPrice } from '../utils/format';

export default function CheckoutPage() {
  const { items, total, reset } = useCart();
  const { data: addresses } = useFetch(customerService.addresses);
  const [maDiaChi, setMaDiaChi] = useState('');
  const [payment, setPayment] = useState('COD');
  const [code, setCode] = useState('');
  const [promo, setPromo] = useState(null);
  const toast = useToast();
  const navigate = useNavigate();

  useEffect(() => {
    if (addresses?.length) setMaDiaChi((addresses.find((a) => a.macDinh) || addresses[0]).maDiaChi);
  }, [addresses]);

  const discount = promo ? Math.min((total * promo.phanTramGiam) / 100, promo.soTienGiamToiDa ?? Infinity) : 0;

  const applyPromo = async () => {
    try {
      setPromo(await orderService.promotion(code.trim()));
      toast('Áp dụng mã khuyến mãi thành công');
    } catch (e) {
      setPromo(null);
      toast(errorMessage(e), 'error');
    }
  };

  const placeOrder = async () => {
    try {
      const order = await orderService.checkout({ phuongThucTt: payment, maDiaChi, maKm: promo?.maKm });
      reset();
      toast('Đặt hàng thành công!');
      navigate(`/orders/${order.maDonHang}`);
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  if (items.length === 0) return <p className="muted">Giỏ hàng trống. <Link to="/">Tiếp tục mua sắm</Link></p>;

  return (
    <div className="checkout">
      <h2>Đặt hàng & thanh toán</h2>
      <ul className="plain">
        {items.map(({ sanPham: sp, soLuong }) => (
          <li key={sp.maSanPham} className="row between">
            <span>{sp.tenMay} × {soLuong}</span>
            <span>{formatPrice(sp.giaBan * soLuong)}</span>
          </li>
        ))}
      </ul>

      <h3>Địa chỉ giao hàng</h3>
      {addresses?.length === 0 && <p className="error">Bạn chưa có địa chỉ giao hàng. <Link to="/account/addresses">Thêm địa chỉ</Link></p>}
      {addresses?.map((a) => (
        <label key={a.maDiaChi} className="check">
          <input type="radio" checked={maDiaChi === a.maDiaChi} onChange={() => setMaDiaChi(a.maDiaChi)} />
          <span>{a.tenNguoiNhan} · {a.soDienThoaiNhan} · {a.diaChiNhanHang}{a.tinhThanhPho && `, ${a.tinhThanhPho}`}</span>
        </label>
      ))}

      <h3>Mã khuyến mãi</h3>
      <div className="row">
        <input placeholder="Nhập mã (vd: NEST10)" value={code} onChange={(e) => setCode(e.target.value)} />
        <button className="btn" onClick={applyPromo}>Áp dụng</button>
        {promo && <span className="muted">{promo.tenKm}</span>}
      </div>

      <h3>Phương thức thanh toán</h3>
      {PAYMENT_METHODS.map((m) => (
        <label key={m.value} className="check">
          <input type="radio" checked={payment === m.value} onChange={() => setPayment(m.value)} /> {m.label}
        </label>
      ))}

      <div className="summary">
        <div className="row between"><span>Tạm tính</span><span>{formatPrice(total)}</span></div>
        <div className="row between"><span>Giảm giá</span><span>- {formatPrice(discount)}</span></div>
        <div className="row between"><b>Tổng thanh toán</b><b className="price">{formatPrice(total - discount)}</b></div>
      </div>
      <button className="btn btn-primary" disabled={!maDiaChi} onClick={placeOrder}>Xác nhận đặt hàng</button>
    </div>
  );
}
