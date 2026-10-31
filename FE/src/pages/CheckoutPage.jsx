import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import AddressForm from '../components/AddressForm';
import Modal from '../components/Modal';
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
  const { data: addresses, reload: reloadAddresses } = useFetch(customerService.addresses);
  const [editingAddress, setEditingAddress] = useState(null);
  const [maDiaChi, setMaDiaChi] = useState('');
  const [payment, setPayment] = useState('COD');
  const [chon, setChon] = useState({ dh: undefined, vc: undefined });
  const [xemHet, setXemHet] = useState({ DON_HANG: false, VAN_CHUYEN: false });
  const [params] = useSearchParams();
  const location = useLocation();
  // Buy Now mới truyền variant qua location.state; query chỉ giữ để tương thích link cũ.
  const mua = location.state?.mua || params.get('mua');
  const sl = Number(location.state?.sl || params.get('sl')) || 1;
  const toast = useToast();
  const navigate = useNavigate();
  const { data: pv, loading: dangTinh, error: loiPv, reload } = useFetch(
    () => (maDiaChi ? orderService.preview(maDiaChi, mua || undefined, mua ? sl : undefined) : Promise.resolve(null)),
    [maDiaChi, mua, sl]
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

  const dong = mua ? pv?.items || [] : items.map((i) => ({ maSanPham: i.phienBan.maPhienBan, tenMay: `${i.sanPham.tenMay} - ${i.phienBan.mauSac} ${i.phienBan.boNho}GB`, soLuong: i.soLuong, donGia: i.phienBan.giaBan }));
  const tamTinh = pv ? Number(pv.tamTinh) : total;
  const phi = pv ? Number(pv.phiVanChuyen) : 0;
  const giamDh = vDh ? Number(vDh.soTienGiam) : 0;
  const giamVc = vVc ? Math.min(Number(vVc.soTienGiam), phi) : 0;
  const tong = tamTinh - giamDh + phi - giamVc;
  const thieuMienPhi = pv?.mienPhiTu && phi > 0 ? Number(pv.mienPhiTu) - tamTinh : 0;

  const addressSaved = async (saved) => {
    setEditingAddress(null);
    toast('Đã lưu địa chỉ giao hàng');
    setMaDiaChi(saved.maDiaChi);
    reloadAddresses();
    reload();
  };

  const placeOrder = async () => {
    try {
      const order = await orderService.checkout({ phuongThucTt: payment, maDiaChi, maKm: vDh?.maKm, maKmVanChuyen: vVc?.maKm, maSanPham: mua || undefined, soLuong: mua ? sl : undefined });
      if (!mua) reset();
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

  if (mua && loiPv) return <p className="muted">{errorMessage(loiPv)} <Link to="/">Quay lại mua sắm</Link></p>;
  if (!mua && items.length === 0) return <p className="muted">Giỏ hàng trống. <Link to="/">Tiếp tục mua sắm</Link></p>;

  return (
    <>
      <h2>Đặt hàng & thanh toán</h2>
      <div className="checkout-layout">
        <div>
          <div className="section">
            <h3>Địa chỉ giao hàng</h3>
            {addresses?.length === 0 && <p className="muted">Bạn chưa có địa chỉ giao hàng, hãy thêm địa chỉ để tiếp tục.</p>}
            {addresses?.map((a) => (
              <label key={a.maDiaChi} className="option">
                <input type="radio" checked={maDiaChi === a.maDiaChi} onChange={() => setMaDiaChi(a.maDiaChi)} />
                <span className="grow">
                  <b>{a.tenNguoiNhan}</b> · {a.soDienThoaiNhan}
                  <br />
                  <span className="muted">{a.diaChiNhanHang}, {a.tenPhuong}, {a.tenTinh}</span>
                </span>
                <button type="button" className="btn btn-ghost" onClick={(e) => { e.preventDefault(); setEditingAddress(a); }}>Sửa</button>
              </label>
            ))}
            <button type="button" className="btn" onClick={() => setEditingAddress({})}>+ Thêm địa chỉ mới</button>
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
            {dong.map((d) => (
              <div key={d.maPhienBan || d.maSanPham} className="row between" style={{ flexWrap: 'nowrap' }}>
                <span>{d.tenMay} × {d.soLuong}</span>
                <span>{formatPrice(d.donGia * d.soLuong)}</span>
              </div>
            ))}
            <div className="row between total"><span>Tạm tính</span><span>{formatPrice(tamTinh)}</span></div>
            <div className="row between"><span>Phí vận chuyển</span><span>{pv ? (phi > 0 ? formatPrice(phi) : 'Miễn phí') : '—'}</span></div>
            {giamVc > 0 && <div className="row between"><span>Giảm phí vận chuyển</span><span>- {formatPrice(giamVc)}</span></div>}
            {giamDh > 0 && <div className="row between"><span>Voucher giảm giá</span><span>- {formatPrice(giamDh)}</span></div>}
            <div className="row between total"><b>Tổng thanh toán</b><b className="price">{formatPrice(tong)}</b></div>
            {mua && <p className="muted">Mua ngay: giỏ hàng của bạn không bị thay đổi.</p>}
            {pv && <p className="muted">Giao đến {pv.khuVuc}, dự kiến {pv.thoiGianGiao}.</p>}
            {thieuMienPhi > 0 && <p className="muted">Mua thêm {formatPrice(thieuMienPhi)} để được miễn phí vận chuyển.</p>}
            <button className="btn btn-primary btn-lg" disabled={!maDiaChi || dangTinh} onClick={placeOrder}>Xác nhận đặt hàng</button>
            {!maDiaChi && <p className="muted">Hãy thêm địa chỉ giao hàng để tiếp tục.</p>}
          </div>
        </aside>
      </div>
      {editingAddress && (
        <Modal title={editingAddress.maDiaChi ? 'Sửa địa chỉ giao hàng' : 'Thêm địa chỉ giao hàng'} onClose={() => setEditingAddress(null)}>
          <AddressForm initial={editingAddress} onSaved={addressSaved} onCancel={() => setEditingAddress(null)} />
        </Modal>
      )}
    </>
  );
}
