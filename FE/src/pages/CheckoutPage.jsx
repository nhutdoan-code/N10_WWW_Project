import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import AddressForm from '../components/AddressForm';
import Modal from '../components/Modal';
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
  const [dangDat, setDangDat] = useState(false);

  const dangDatRef = useRef(false);

  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const toast = useToast();

  const maPhienBanMua = location.state?.mua || params.get('mua');
  const slMua = Number(location.state?.sl || params.get('sl')) || 1;
  const muaNgay = Boolean(maPhienBanMua);

  const {
    data: pv,
    loading: dangTinh,
    error: loiPv,
    reload,
  } = useFetch(
    () => maDiaChi
      ? orderService.preview(maDiaChi, maPhienBanMua || undefined, muaNgay ? slMua : undefined)
      : Promise.resolve(null),
    [maDiaChi, maPhienBanMua, slMua]
  );

  useEffect(() => {
    if (!addresses?.length) return;
    setMaDiaChi((current) => current || (addresses.find((a) => a.macDinh) || addresses[0]).maDiaChi);
  }, [addresses]);

  const ds = (loai) => (pv?.vouchers || []).filter((v) => v.loai === loai);

  const tot = (list) => list.filter((v) => v.apDungDuoc).reduce(
    (best, v) => (!best || v.soTienGiam > best.soTienGiam ? v : best),
    null
  );

  const hieuLuc = (list, daChon) => {
    const ma = daChon === undefined ? tot(list)?.maKm : daChon;
    return list.find((v) => v.maKm === ma && v.apDungDuoc) || null;
  };

  const vDh = hieuLuc(ds('DON_HANG'), chon.dh);
  const vVc = hieuLuc(ds('VAN_CHUYEN'), chon.vc);

  const dong = muaNgay
    ? pv?.items || []
    : items.map((i) => ({
      maPhienBan: i.phienBan?.maPhienBan,
      tenMay: i.phienBan
        ? `${i.sanPham.tenMay} - RAM ${i.phienBan.ram}GB / ${i.phienBan.boNho}GB - ${i.phienBan.mauSac}`
        : i.sanPham.tenMay,
      soLuong: i.soLuong,
      donGia: i.phienBan?.giaBan ?? i.sanPham.giaBan,
    }));

  const tamTinh = pv ? Number(pv.tamTinh) : total;
  const phi = pv ? Number(pv.phiVanChuyen) : 0;
  const giamDh = vDh ? Number(vDh.soTienGiam) : 0;
  const giamVc = vVc ? Math.min(Number(vVc.soTienGiam), phi) : 0;
  const tong = tamTinh - giamDh + phi - giamVc;

  const thieuMienPhi = pv?.mienPhiTu && phi > 0
    ? Number(pv.mienPhiTu) - tamTinh
    : 0;

  const addressSaved = async (saved) => {
    setEditingAddress(null);
    toast('Đã lưu địa chỉ giao hàng');
    setMaDiaChi(saved.maDiaChi);
    await reloadAddresses();
    reload();
  };

  // Đặt hàng và chống gửi request nhiều lần
  const placeOrder = async () => {
    if (dangDatRef.current) return;

    if (!maDiaChi) {
      toast('Vui lòng chọn địa chỉ giao hàng.', 'error');
      return;
    }

    if (dangTinh || !pv) return;

    dangDatRef.current = true;
    setDangDat(true);

    try {
      const order = await orderService.checkout({
        phuongThucTt: payment,
        maDiaChi,
        maKm: vDh?.maKm,
        maKmVanChuyen: vVc?.maKm,
        maSanPham: maPhienBanMua || undefined,
        soLuong: muaNgay ? slMua : undefined,
      });

      if (!muaNgay) reset();

      if (payment === 'VI_DIEN_TU') {
        const momo = await orderService.createMomoPayment(order.maDonHang);
        window.location.assign(momo.payUrl);
        return;
      }

      navigate(`/orders/${order.maDonHang}`);
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      dangDatRef.current = false;
      setDangDat(false);
    }
  };

  const toggleXem = (loai) => setXemHet((prev) => ({ ...prev, [loai]: !prev[loai] }));

  return (
    <>
      <h2>Thanh toán</h2>
      <div className="cart-layout">
        <div className="section">
          <h3>Địa chỉ nhận hàng</h3>
          {addresses?.map((a) => (
            <label key={a.maDiaChi} className="option">
              <input
                type="radio"
                name="addr"
                checked={maDiaChi === a.maDiaChi}
                onChange={() => setMaDiaChi(a.maDiaChi)}
              />
              <div>
                <b>{a.tenNguoiNhan}</b> · {a.soDienThoaiNhan}
                {a.macDinh && <span className="tag">Mặc định</span>}
                <div className="muted">{a.diaChiChiTiet}, {a.tenPhuongXa}, {a.tenTinhThanh}</div>
              </div>
              <button
                type="button"
                className="link-btn"
                style={{ marginLeft: 'auto' }}
                onClick={(e) => { e.preventDefault(); setEditingAddress(a); }}
              >
                Sửa
              </button>
            </label>
          ))}
          <button type="button" className="btn" onClick={() => setEditingAddress({})}>
            + Thêm địa chỉ mới
          </button>

          <div className="section">
            <h3>Phương thức thanh toán</h3>
            {PAYMENT_METHODS.map((m) => (
              <label key={m.value} className="option">
                <input
                  type="radio"
                  name="payment"
                  checked={payment === m.value}
                  onChange={() => setPayment(m.value)}
                />
                {m.label}
              </label>
            ))}
          </div>

          <div className="section">
            <h3>Mã khuyến mãi</h3>
            {loiPv && <p className="error">{loiPv}</p>}

            <h4>Voucher đơn hàng</h4>
            {ds('DON_HANG').length === 0 && <p className="muted">Không có mã khả dụng.</p>}
            {(xemHet.DON_HANG ? ds('DON_HANG') : ds('DON_HANG').slice(0, 3)).map((v) => (
              <label key={v.maKm} className={`option ${!v.apDungDuoc ? 'disabled' : ''}`}>
                <input
                  type="radio"
                  name="voucher-dh"
                  disabled={!v.apDungDuoc}
                  checked={vDh?.maKm === v.maKm}
                  onChange={() => setChon((c) => ({ ...c, dh: v.maKm }))}
                />
                <div>
                  <b>{v.maKm}</b> — {v.tenKm}
                  <div className="muted">{v.dieuKien}</div>
                  {!v.apDungDuoc && <small className="error">{v.lyDo}</small>}
                </div>
              </label>
            ))}
            {ds('DON_HANG').length > 3 && (
              <button type="button" className="link-btn" onClick={() => toggleXem('DON_HANG')}>
                {xemHet.DON_HANG ? 'Thu gọn' : `Xem thêm (${ds('DON_HANG').length - 3} mã)`}
              </button>
            )}

            <h4>Voucher vận chuyển</h4>
            {ds('VAN_CHUYEN').length === 0 && <p className="muted">Không có mã khả dụng.</p>}
            {(xemHet.VAN_CHUYEN ? ds('VAN_CHUYEN') : ds('VAN_CHUYEN').slice(0, 3)).map((v) => (
              <label key={v.maKm} className={`option ${!v.apDungDuoc ? 'disabled' : ''}`}>
                <input
                  type="radio"
                  name="voucher-vc"
                  disabled={!v.apDungDuoc}
                  checked={vVc?.maKm === v.maKm}
                  onChange={() => setChon((c) => ({ ...c, vc: v.maKm }))}
                />
                <div>
                  <b>{v.maKm}</b> — {v.tenKm}
                  <div className="muted">{v.dieuKien}</div>
                  {!v.apDungDuoc && <small className="error">{v.lyDo}</small>}
                </div>
              </label>
            ))}
            {ds('VAN_CHUYEN').length > 3 && (
              <button type="button" className="link-btn" onClick={() => toggleXem('VAN_CHUYEN')}>
                {xemHet.VAN_CHUYEN ? 'Thu gọn' : `Xem thêm (${ds('VAN_CHUYEN').length - 3} mã)`}
              </button>
            )}
          </div>
        </div>

        <aside className="side">
          <div className="summary">
            <h3>Đơn hàng của bạn</h3>
            {dangTinh && <p className="muted">Đang tính phí và khuyến mãi...</p>}

            {dong.map((d) => (
              <div
                key={d.maPhienBan || d.maSanPham}
                className="row between"
                style={{ flexWrap: 'nowrap' }}
              >
                <span>{d.tenMay} × {d.soLuong}</span>
                <span>{formatPrice(Number(d.donGia) * Number(d.soLuong))}</span>
              </div>
            ))}

            <div className="row between total">
              <span>Tạm tính</span>
              <span>{formatPrice(tamTinh)}</span>
            </div>

            <div className="row between">
              <span>Phí vận chuyển</span>
              <span>{pv ? (phi > 0 ? formatPrice(phi) : 'Miễn phí') : '—'}</span>
            </div>

            {giamVc > 0 && (
              <div className="row between">
                <span>Giảm phí vận chuyển</span>
                <span>- {formatPrice(giamVc)}</span>
              </div>
            )}

            {giamDh > 0 && (
              <div className="row between">
                <span>Voucher giảm giá</span>
                <span>- {formatPrice(giamDh)}</span>
              </div>
            )}

            <div className="row between total">
              <b>Tổng thanh toán</b>
              <b className="price">{formatPrice(tong)}</b>
            </div>

            {muaNgay && (
              <p className="muted">Mua ngay: giỏ hàng của bạn không bị thay đổi.</p>
            )}

            {pv && (
              <p className="muted">
                Giao đến {pv.khuVuc}, dự kiến {pv.thoiGianGiao}.
              </p>
            )}

            {thieuMienPhi > 0 && (
              <p className="muted">
                Mua thêm {formatPrice(thieuMienPhi)} để được miễn phí vận chuyển.
              </p>
            )}

            <button
              type="button"
              className="btn btn-primary btn-lg"
              disabled={!maDiaChi || dangTinh || !pv || dangDat}
              onClick={placeOrder}
            >
              {dangDat ? 'Đang xử lý đơn hàng...' : 'Xác nhận đặt hàng'}
            </button>

            {!maDiaChi && (
              <p className="muted">Hãy thêm địa chỉ giao hàng để tiếp tục.</p>
            )}
          </div>
        </aside>
      </div>

      {editingAddress && (
        <Modal
          title={editingAddress.maDiaChi ? 'Sửa địa chỉ giao hàng' : 'Thêm địa chỉ giao hàng'}
          onClose={() => setEditingAddress(null)}
        >
          <AddressForm
            initial={editingAddress}
            onSaved={addressSaved}
            onCancel={() => setEditingAddress(null)}
          />
        </Modal>
      )}
    </>
  );
}
