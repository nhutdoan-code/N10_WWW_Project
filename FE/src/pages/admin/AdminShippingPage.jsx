import { useState } from 'react';
import FormField from '../../components/FormField';
import AdminFetchError from '../../components/AdminFetchError';
import MoneyInput from '../../components/MoneyInput';
import Modal from '../../components/Modal';
import { locationService } from '../../service/locationService';
import useFetch from '../../hooks/useFetch';
import useToast from '../../hooks/useToast';
import { adminService } from '../../service/adminService';
import { errorMessage, formatPrice } from '../../utils/format';

const VUNG = [
  { value: 0, label: 'Vùng 0 · Nội tỉnh TP.HCM' },
  { value: 1, label: 'Vùng 1 · Lân cận & Miền Nam' },
  { value: 2, label: 'Vùng 2 · Miền Trung & Tây Nguyên' },
  { value: 3, label: 'Vùng 3 · Miền Bắc' },
];

const EMPTY = {
  maTinh: '', vung: '', phiCoBan: '', phuPhiXa: 0, phuPhiDacKhu: 15000, mienPhiTu: '',
  soNgayGiaoToiThieu: '', soNgayGiaoToiDa: '',
};

const soHoacNull = (v) => (v === '' || v == null ? null : Number(v));
const phuPhi = (v) => (Number(v) > 0 ? `+${formatPrice(v)}` : '—');

export default function AdminShippingPage() {
  const { data: list, loading, error, reload } = useFetch(adminService.shippingFees);
  const { data: provinces, loading: provincesLoading, error: provincesError, reload: reloadProvinces } = useFetch(locationService.provinces);
  const [editing, setEditing] = useState(null);
  const toast = useToast();

  const run = async (action, message) => {
    try {
      await action();
      toast(message);
      setEditing(null);
      reload();
    } catch (e) {
      toast(errorMessage(e), 'error');
    }
  };

  const set = (key) => (e) => setEditing({ ...editing, [key]: e.target.value });
  const submit = (e) => {
    e.preventDefault();
    run(
      () => adminService.saveShippingFee(editing.maPhi, {
        ...editing,
        vung: soHoacNull(editing.vung),
        phiCoBan: Number(editing.phiCoBan),
        phuPhiXa: soHoacNull(editing.phuPhiXa) ?? 0,
        phuPhiDacKhu: soHoacNull(editing.phuPhiDacKhu) ?? 0,
        mienPhiTu: soHoacNull(editing.mienPhiTu),
        soNgayGiaoToiThieu: soHoacNull(editing.soNgayGiaoToiThieu),
        soNgayGiaoToiDa: soHoacNull(editing.soNgayGiaoToiDa),
      }),
      'Đã lưu bảng phí'
    );
  };

  return (
    <>
      <div className="row between">
        <h2>Phí vận chuyển</h2>
        <button className="btn btn-primary" onClick={() => setEditing({ ...EMPTY })}>+ Thêm khu vực</button>
      </div>
      <p className="muted">
        Kho đặt tại TP.HCM. Phí giao hàng tính theo tỉnh / thành phố và loại địa bàn của địa chỉ nhận
        (phường = nội thành, xã = ngoại thành, đặc khu = huyện đảo, giao thêm 1–2 ngày). Đơn đạt ngưỡng “Miễn phí từ”
        được miễn toàn bộ phí. Đơn chưa đạt ngưỡng cộng thêm phí khai giá 0,5% giá trị hàng (tối đa 50.000 ₫) với đơn từ
        3.000.000 ₫ và phụ phí 15.000 ₫ khi thanh toán COD cho đơn trên 10.000.000 ₫. Voucher vận chuyển chỉ giảm phí giao hàng.
        Đơn đặt sau 16:00 được tính ngày giao từ hôm sau. Tỉnh chưa có trong danh sách dùng mức mặc định.
      </p>
      <AdminFetchError error={error} onRetry={reload} loading={loading} />
      {loading && <p className="muted">Đang tải bảng phí vận chuyển…</p>}
      <div className="table-wrap" style={{ overflowX: 'auto' }}>
        <table className="table">
          <thead>
            <tr>
              <th>Khu vực</th><th>Vùng</th><th>Phường</th><th>Xã</th><th>Đặc khu</th>
              <th>Miễn phí từ</th><th>Thời gian giao</th><th></th>
            </tr>
          </thead>
          <tbody>
            {list?.map((p) => (
              <tr key={p.maPhi}>
                <td><b>{p.tenKhuVuc}</b></td>
                <td>{p.tenVung}</td>
                <td>{formatPrice(p.phiCoBan)}</td>
                <td>{phuPhi(p.phuPhiXa)}</td>
                <td>{phuPhi(p.phuPhiDacKhu)}</td>
                <td>{p.mienPhiTu ? formatPrice(p.mienPhiTu) : 'Không áp dụng'}</td>
                <td>{p.thoiGianGiao}</td>
                <td><div className="row">
                  <button className="btn" onClick={() => setEditing({ ...p, vung: p.vung ?? '' })}>Sửa</button>
                  {p.maTinh && (
                    <button className="btn btn-danger" onClick={() => window.confirm(`Xóa mức phí của ${p.tenKhuVuc}?`) && run(() => adminService.deleteShippingFee(p.maPhi), 'Đã xóa khu vực')}>Xóa</button>
                  )}
                </div></td>
              </tr>
            ))}
            {!loading && !error && list?.length === 0 && (
              <tr><td colSpan={8} className="muted" style={{ textAlign: 'center', padding: '24px' }}>Chưa có mức phí vận chuyển.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {editing && (
        <Modal title={editing.maPhi ? 'Sửa phí vận chuyển' : 'Thêm khu vực'} onClose={() => setEditing(null)}>
          <form className="form" onSubmit={submit}>
            <AdminFetchError error={provincesError} onRetry={reloadProvinces} loading={provincesLoading} />
            {editing.maPhi ? (
              <FormField label="Khu vực" value={editing.tenKhuVuc} disabled />
            ) : (
              <label className="field">
                <span>Tỉnh / Thành phố</span>
                <select value={editing.maTinh} onChange={set('maTinh')} required>
                  <option value="">Chọn tỉnh / thành phố</option>
                  {(provinces || []).slice().sort((a, b) => a.ten.localeCompare(b.ten, 'vi')).map((t) => <option key={t.ma} value={t.ma}>{t.ten}</option>)}
                </select>
              </label>
            )}
            <label className="field">
              <span>Vùng giao hàng</span>
              <select value={editing.vung} onChange={set('vung')}>
                <option value="">Khu vực khác</option>
                {VUNG.map((v) => <option key={v.value} value={v.value}>{v.label}</option>)}
              </select>
            </label>
            <div className="grid-2">
              <label className="field">
                <span>Phí giao đến phường (₫)</span>
                <MoneyInput value={editing.phiCoBan} onChange={(raw) => setEditing({ ...editing, phiCoBan: raw })} placeholder="25.000" required />
              </label>
              <label className="field">
                <span>Miễn phí cho đơn từ (₫)</span>
                <MoneyInput value={editing.mienPhiTu ?? ''} onChange={(raw) => setEditing({ ...editing, mienPhiTu: raw })} placeholder="3.000.000" />
              </label>
            </div>
            <div className="grid-2">
              <label className="field">
                <span>Phụ phí xã ngoại thành (₫)</span>
                <MoneyInput value={editing.phuPhiXa ?? ''} onChange={(raw) => setEditing({ ...editing, phuPhiXa: raw })} placeholder="0" />
              </label>
              <label className="field">
                <span>Phụ phí đặc khu / đảo (₫)</span>
                <MoneyInput value={editing.phuPhiDacKhu ?? ''} onChange={(raw) => setEditing({ ...editing, phuPhiDacKhu: raw })} placeholder="15.000" />
              </label>
            </div>
            <div className="grid-2">
              <FormField label="Giao nhanh nhất (ngày, 0 = trong ngày)" type="number" min="0" max="30" value={editing.soNgayGiaoToiThieu ?? ''} onChange={set('soNgayGiaoToiThieu')} required />
              <FormField label="Giao chậm nhất (ngày)" type="number" min="0" max="30" value={editing.soNgayGiaoToiDa ?? ''} onChange={set('soNgayGiaoToiDa')} required />
            </div>
            <button className="btn btn-primary">Lưu</button>
          </form>
        </Modal>
      )}
    </>
  );
}
