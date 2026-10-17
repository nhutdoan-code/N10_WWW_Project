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
    <>
      <h2>Đặt hàng & thanh toán</h2>
      <div className="checkout-layout">
        <div>
          <div className="section">
            <h3>Địa chỉ giao hàng</h3>
            {addresses?.length === 0 && <p className="error">Bạn chưa có địa chỉ giao hàng. <Link to="/account/addresses">Thêm địa chỉ</Link></p>}
            {addresses?.map((a) => (
              <label key={a.maDiaChi} className="option">
                <input type="radio" checked={maDiaChi === a.maDiaChi} onChange={() => setMaDiaChi(a.maDiaChi)} />
                <span>
                  <b>{a.tenNguoiNhan}</b> · {a.soDienThoaiNhan}
                  <br />
                  <span className="muted">{a.diaChiNhanHang}{a.tinhThanhPho && `, ${a.tinhThanhPho}`}</span>
                </span>
              </label>
            ))}
          </div>

          <div className="section">
            <h3>Phương thức thanh toán</h3>
            {PAYMENT_METHODS.map((m) => (
              <label key={m.value} className="option">
                <input type="radio" checked={payment === m.value} onChange={() => setPayment(m.value)} /> {m.label}
              </label>
            ))}
          </div>

          <div className="section">
            <h3>Mã khuyến mãi</h3>
            <div className="row">
              <input placeholder="Nhập mã (vd: NEST10)" value={code} onChange={(e) => setCode(e.target.value)} />
              <button className="btn" onClick={applyPromo}>Áp dụng</button>
            </div>
            {promo && <p className="muted">Đã áp dụng: {promo.tenKm}</p>}
          </div>
        </div>

        <aside className="side">
          <div className="summary">
            <h3 style={{ margin: 0 }}>Đơn hàng của bạn</h3>
            {items.map(({ sanPham: sp, soLuong }) => (
              <div key={sp.maSanPham} className="row between" style={{ flexWrap: 'nowrap' }}>
                <span>{sp.tenMay} × {soLuong}</span>
                <span>{formatPrice(sp.giaBan * soLuong)}</span>
              </div>
            ))}
            <div className="row between total"><span>Tạm tính</span><span>{formatPrice(total)}</span></div>
            <div className="row between"><span>Giảm giá</span><span>- {formatPrice(discount)}</span></div>
            <div className="row between total"><b>Tổng thanh toán</b><b className="price">{formatPrice(total - discount)}</b></div>
            <button className="btn btn-primary btn-lg" disabled={!maDiaChi} onClick={placeOrder}>Xác nhận đặt hàng</button>
            {!maDiaChi && <p className="muted">Hãy thêm địa chỉ giao hàng để tiếp tục.</p>}
          </div>
        </aside>
      </div>
    </>
  );
}
