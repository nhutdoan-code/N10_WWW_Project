import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import VoucherCard from '../components/VoucherCard';
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
  const [chon, setChon] = useState({ dh: undefined, vc: undefined });
  const [xemHet, setXemHet] = useState({ DON_HANG: false, VAN_CHUYEN: false });
  const toast = useToast();
  const navigate = useNavigate();
  const { data: pv, loading: dangTinh, reload } = useFetch(
    () => (maDiaChi ? orderService.preview(maDiaChi) : Promise.resolve(null)),
    [maDiaChi]
  );

  useEffect(() => {
    if (addresses?.length) setMaDiaChi((addresses.find((a) => a.macDinh) || addresses[0]).maDiaChi);
  }, [addresses]);

  const ds = (loai) => (pv?.vouchers || []).filter((v) => v.loai === loai);
  const tot = (list) => list.filter((v) => v.apDungDuoc).reduce((b, v) => (!b || v.soTienGiam > b.soTienGiam ? v : b), null);
  const hieuLuc = (list, daChon) => {
    const ma = daChon === undefined ? tot(list)?.maKm : daChon;
    return list.find((v) => v.maKm === ma && v.apDungDuoc) || null;
  };
  const vDh = hieuLuc(ds('DON_HANG'), chon.dh);
  const vVc = hieuLuc(ds('VAN_CHUYEN'), chon.vc);

  const tamTinh = pv ? Number(pv.tamTinh) : total;
  const phi = pv ? Number(pv.phiVanChuyen) : 0;
  const giamDh = vDh ? Number(vDh.soTienGiam) : 0;
  const giamVc = vVc ? Math.min(Number(vVc.soTienGiam), phi) : 0;
  const tong = tamTinh - giamDh + phi - giamVc;
  const thieuMienPhi = pv?.mienPhiTu && phi > 0 ? Number(pv.mienPhiTu) - tamTinh : 0;

  const placeOrder = async () => {
    try {
      const order = await orderService.checkout({ phuongThucTt: payment, maDiaChi, maKm: vDh?.maKm, maKmVanChuyen: vVc?.maKm });
      reset();
      toast('Đặt hàng thành công!');
      navigate(`/orders/${order.maDonHang}`);
    } catch (e) {
      toast(errorMessage(e), 'error');
      reload();
    }
  };

  const voucherSection = (loai, tieuDe, daChon, khoa) => {
    const list = ds(loai);
    if (list.length === 0) return null;
    const hien = xemHet[loai] ? list : list.slice(0, 3);
    return (
      <div className="section">
        <h3>{tieuDe}</h3>
        <div className="voucher-list">
          {hien.map((v) => (
            <VoucherCard
              key={v.maKm}
              v={v}
              selected={daChon?.maKm === v.maKm}
              onSelect={() => setChon({ ...chon, [khoa]: daChon?.maKm === v.maKm ? null : v.maKm })}
            />
          ))}
        </div>
        {list.length > 3 && (
          <button type="button" className="btn btn-ghost" onClick={() => setXemHet({ ...xemHet, [loai]: !xemHet[loai] })}>
            {xemHet[loai] ? 'Thu gọn' : `Xem thêm ${list.length - 3} voucher`}
          </button>
        )}
      </div>
    );
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

          {voucherSection('DON_HANG', 'Voucher giảm giá', vDh, 'dh')}
          {voucherSection('VAN_CHUYEN', 'Voucher vận chuyển', vVc, 'vc')}
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
            <div className="row between"><span>Phí vận chuyển</span><span>{pv ? (phi > 0 ? formatPrice(phi) : 'Miễn phí') : '—'}</span></div>
            {giamVc > 0 && <div className="row between"><span>Giảm phí vận chuyển</span><span>- {formatPrice(giamVc)}</span></div>}
            {giamDh > 0 && <div className="row between"><span>Voucher giảm giá</span><span>- {formatPrice(giamDh)}</span></div>}
            <div className="row between total"><b>Tổng thanh toán</b><b className="price">{formatPrice(tong)}</b></div>
            {pv && <p className="muted">Giao đến {pv.khuVuc}, dự kiến {pv.thoiGianGiao}.</p>}
            {thieuMienPhi > 0 && <p className="muted">Mua thêm {formatPrice(thieuMienPhi)} để được miễn phí vận chuyển.</p>}
            <button className="btn btn-primary btn-lg" disabled={!maDiaChi || dangTinh} onClick={placeOrder}>Xác nhận đặt hàng</button>
            {!maDiaChi && <p className="muted">Hãy thêm địa chỉ giao hàng để tiếp tục.</p>}
          </div>
        </aside>
      </div>
    </>
  );
}
