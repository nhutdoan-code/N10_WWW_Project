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

function generateUUID() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

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
  const daDatThanhCongRef = useRef(false);
  const [idempotencyKey, setIdempotencyKey] = useState(() => {
    try {
      const stored = sessionStorage.getItem('phonenest_checkout_key');
      if (stored && /^[0-9a-fA-F-]{36}$/.test(stored)) {
        return stored;
      }
    } catch {
      // sessionStorage unavailable
    }
    return generateUUID();
  });
  const idempotencyKeyRef = useRef(idempotencyKey);
  idempotencyKeyRef.current = idempotencyKey;

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

  // Theo dõi sự thay đổi của payload: khi người dùng đổi lựa chọn, sinh key mới
  const currentPayloadSig = `${maDiaChi}|${payment}|${vDh?.maKm || ''}|${vVc?.maKm || ''}|${maPhienBanMua || ''}|${muaNgay ? slMua : ''}|${muaNgay ? '' : items.map((i) => `${i.phienBan?.maPhienBan}:${i.soLuong}`).join(',')}`;
  const lastPayloadSigRef = useRef(null);

  useEffect(() => {
    let storedSig = null;
    try {
      storedSig = sessionStorage.getItem('phonenest_checkout_sig');
    } catch {
      // ignore
    }

    if (lastPayloadSigRef.current === null) {
      lastPayloadSigRef.current = currentPayloadSig;
      if (storedSig && storedSig === currentPayloadSig) {
        return;
      }
      const newKey = generateUUID();
      setIdempotencyKey(newKey);
      idempotencyKeyRef.current = newKey;
      try {
        sessionStorage.setItem('phonenest_checkout_key', newKey);
        sessionStorage.setItem('phonenest_checkout_sig', currentPayloadSig);
      } catch {
        // ignore
      }
      return;
    }

    if (currentPayloadSig !== lastPayloadSigRef.current) {
      lastPayloadSigRef.current = currentPayloadSig;
      const newKey = generateUUID();
      setIdempotencyKey(newKey);
      idempotencyKeyRef.current = newKey;
      try {
        sessionStorage.setItem('phonenest_checkout_key', newKey);
        sessionStorage.setItem('phonenest_checkout_sig', currentPayloadSig);
      } catch {
        // ignore
      }
    }
  }, [currentPayloadSig]);

  const addressSaved = async (saved) => {
    setEditingAddress(null);
    toast('Đã lưu địa chỉ giao hàng');
    setMaDiaChi(saved.maDiaChi);
    await reloadAddresses();
    reload();
  };

  // Đặt hàng và chống gửi request nhiều lần (Idempotent Checkout)
  const placeOrder = async () => {
    if (dangDatRef.current || daDatThanhCongRef.current) return;

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
      }, idempotencyKeyRef.current);

      daDatThanhCongRef.current = true;
      try {
        sessionStorage.removeItem('phonenest_checkout_key');
        sessionStorage.removeItem('phonenest_checkout_sig');
      } catch {
        // ignore
      }
      if (!muaNgay) reset();

      if (payment === 'VI_DIEN_TU') {
        const momo = await orderService.createMomoPayment(order.maDonHang);
        window.location.assign(momo.payUrl);
        return;
      }

      navigate(`/orders/${order.maDonHang}`);
    } catch (err) {
      const msg = errorMessage(err);
      const code = err.response?.data?.code;
      const status = err.response?.status;

      if (code === 'IDEMPOTENCY_PAYLOAD_MISMATCH' || (!code && status === 409 && msg && msg.toLowerCase().includes('khác'))) {
        toast(msg || 'Thông tin đơn hàng đã thay đổi. Đang làm mới phiên đặt hàng...', 'error');
        const newKey = generateUUID();
        setIdempotencyKey(newKey);
        idempotencyKeyRef.current = newKey;
        try {
          sessionStorage.setItem('phonenest_checkout_key', newKey);
          sessionStorage.setItem('phonenest_checkout_sig', currentPayloadSig);
        } catch {
          // ignore
        }
      } else if (code === 'IDEMPOTENCY_IN_PROGRESS') {
        // Request trước đó vẫn đang được xử lý hoặc chờ đối soát: GIỮ NGUYÊN idempotency key, cảnh báo người dùng chờ
        toast(msg || 'Yêu cầu đặt hàng của bạn đang được xử lý hoặc đang chờ đối soát, vui lòng chờ trong giây lát.', 'warning');
      } else if (code === 'VOUCHER_USAGE_LIMIT_REACHED') {
        // Mã lỗi ổn định: Voucher đã hết lượt hoặc vượt quá giới hạn mỗi khách
        toast(msg || 'Mã khuyến mãi đã hết lượt sử dụng hoặc vượt quá giới hạn. Vui lòng chọn mã khác.', 'error');
        reload();
      } else if (status === 409) {
        // Lỗi 409 khác không xác định
        toast(msg || 'Có xung đột khi xử lý đơn hàng. Vui lòng thử lại.', 'error');
      } else {
        toast(errorMessage(err), 'error');
        reload();
      }
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

          <h3>Phương thức thanh toán</h3>
          {PAYMENT_METHODS.map((m) => (
            <label key={m.id} className="option">
              <input
                type="radio"
                name="pm"
                checked={payment === m.id}
                onChange={() => setPayment(m.id)}
              />
              <div>
                <b>{m.name}</b>
                <div className="muted">{m.desc}</div>
              </div>
            </label>
          ))}

          <h3>Voucher giảm giá</h3>
          <div className="voucher-group">
            <div className="title">Voucher đơn hàng</div>
            {ds('DON_HANG').slice(0, xemHet.DON_HANG ? undefined : 2).map((v) => (
              <label
                key={v.maKm}
                className={`option voucher ${!v.apDungDuoc ? 'disabled' : ''}`}
              >
                <input
                  type="radio"
                  name="vDh"
                  disabled={!v.apDungDuoc}
                  checked={vDh?.maKm === v.maKm}
                  onChange={() => setChon((p) => ({ ...p, dh: v.maKm }))}
                  onClick={() => {
                    if (vDh?.maKm === v.maKm) {
                      setChon((p) => ({ ...p, dh: null }));
                    }
                  }}
                />
                <div>
                  <b>{v.tenKm}</b>
                  <div className="muted">{v.dieuKien}</div>
                  {!v.apDungDuoc && <span className="reason">{v.lyDoKhongDung}</span>}
                </div>
                {v.apDungDuoc && (
                  <span className="discount">-{formatPrice(v.soTienGiam)}</span>
                )}
              </label>
            ))}
            {ds('DON_HANG').length > 2 && (
              <button
                type="button"
                className="link-btn"
                onClick={() => toggleXem('DON_HANG')}
              >
                {xemHet.DON_HANG ? 'Thu gọn' : `Xem thêm (${ds('DON_HANG').length - 2})`}
              </button>
            )}
          </div>

          <div className="voucher-group">
            <div className="title">Voucher vận chuyển</div>
            {ds('VAN_CHUYEN').slice(0, xemHet.VAN_CHUYEN ? undefined : 2).map((v) => (
              <label
                key={v.maKm}
                className={`option voucher ${!v.apDungDuoc ? 'disabled' : ''}`}
              >
                <input
                  type="radio"
                  name="vVc"
                  disabled={!v.apDungDuoc}
                  checked={vVc?.maKm === v.maKm}
                  onChange={() => setChon((p) => ({ ...p, vc: v.maKm }))}
                  onClick={() => {
                    if (vVc?.maKm === v.maKm) {
                      setChon((p) => ({ ...p, vc: null }));
                    }
                  }}
                />
                <div>
                  <b>{v.tenKm}</b>
                  <div className="muted">{v.dieuKien}</div>
                  {!v.apDungDuoc && <span className="reason">{v.lyDoKhongDung}</span>}
                </div>
                {v.apDungDuoc && (
                  <span className="discount">-{formatPrice(v.soTienGiam)}</span>
                )}
              </label>
            ))}
            {ds('VAN_CHUYEN').length > 2 && (
              <button
                type="button"
                className="link-btn"
                onClick={() => toggleXem('VAN_CHUYEN')}
              >
                {xemHet.VAN_CHUYEN ? 'Thu gọn' : `Xem thêm (${ds('VAN_CHUYEN').length - 2})`}
              </button>
            )}
          </div>
        </div>

        <aside className="summary">
          <h3>Đơn hàng của bạn ({dong.length} món)</h3>
          {dong.map((d, index) => (
            <div key={d.maPhienBan || index} className="row between item">
              <div>
                <b>{d.tenMay}</b>
                <div className="muted">Số lượng: {d.soLuong}</div>
              </div>
              <span>{formatPrice(Number(d.donGia) * d.soLuong)}</span>
            </div>
          ))}

          <div className="divider" />
          <div className="row between"><span>Tạm tính</span><span>{formatPrice(tamTinh)}</span></div>
          <div className="row between"><span>Phí vận chuyển</span><span>{formatPrice(phi)}</span></div>
          {vDh && (
            <div className="row between text-success">
              <span>Giảm giá đơn hàng ({vDh.maKm})</span>
              <span>-{formatPrice(giamDh)}</span>
            </div>
          )}
          {vVc && (
            <div className="row between text-success">
              <span>Giảm phí vận chuyển ({vVc.maKm})</span>
              <span>-{formatPrice(giamVc)}</span>
            </div>
          )}
          <div className="divider" />

          <div>
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
              disabled={!maDiaChi || dangTinh || !pv || dangDat || daDatThanhCongRef.current}
              onClick={placeOrder}
            >
              {dangDat ? 'Đang xử lý đơn hàng...' : (daDatThanhCongRef.current ? 'Đã đặt hàng' : 'Xác nhận đặt hàng')}
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
