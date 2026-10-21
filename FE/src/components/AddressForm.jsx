import { useEffect, useState } from 'react';
import FormField from './FormField';
import useFetch from '../hooks/useFetch';
import useToast from '../hooks/useToast';
import { customerService } from '../service/customerService';
import { locationService } from '../service/locationService';
import { errorMessage } from '../utils/format';

const EMPTY = { tenNguoiNhan: '', soDienThoaiNhan: '', maTinh: '', maPhuong: '', diaChiNhanHang: '', macDinh: false };

export default function AddressForm({ initial, onSaved, onCancel }) {
  const [form, setForm] = useState({ ...EMPTY, ...initial });
  const [wards, setWards] = useState([]);
  const { data: provinces } = useFetch(locationService.provinces);
  const toast = useToast();

  useEffect(() => {
    if (!form.maTinh) return setWards([]);
    let alive = true;
    locationService.wards(form.maTinh).then((w) => alive && setWards(w));
    return () => { alive = false; };
  }, [form.maTinh]);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const sorted = (provinces || []).slice().sort((a, b) => a.ten.localeCompare(b.ten, 'vi'));

  const submit = async (e) => {
    e.preventDefault();
    try {
      onSaved(await customerService.saveAddress(form.maDiaChi, form));
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
  };

  return (
    <form className="form" onSubmit={submit}>
      <div className="grid-2">
        <FormField label="Tên người nhận" value={form.tenNguoiNhan} onChange={set('tenNguoiNhan')} required />
        <FormField label="Số điện thoại" value={form.soDienThoaiNhan} onChange={set('soDienThoaiNhan')} inputMode="tel" required />
      </div>
      <label className="field">
        <span>Tỉnh / Thành phố</span>
        <select value={form.maTinh} onChange={(e) => setForm({ ...form, maTinh: e.target.value, maPhuong: '' })} required>
          <option value="">Chọn tỉnh / thành phố</option>
          {sorted.map((t) => <option key={t.ma} value={t.ma}>{t.ten}</option>)}
        </select>
      </label>
      <label className="field">
        <span>Phường / Xã</span>
        <select value={form.maPhuong} onChange={set('maPhuong')} disabled={!form.maTinh} required>
          <option value="">{form.maTinh ? 'Chọn phường / xã' : 'Chọn tỉnh / thành phố trước'}</option>
          {wards.map((w) => <option key={w.ma} value={w.ma}>{w.ten}</option>)}
        </select>
      </label>
      <FormField label="Số nhà, tên đường" placeholder="vd: 12 Nguyễn Văn Bảo" value={form.diaChiNhanHang} onChange={set('diaChiNhanHang')} required />
      <label className="check">
        <input type="checkbox" checked={form.macDinh} onChange={(e) => setForm({ ...form, macDinh: e.target.checked })} /> Đặt làm địa chỉ mặc định
      </label>
      <div className="row end">
        {onCancel && <button type="button" className="btn" onClick={onCancel}>Hủy</button>}
        <button className="btn btn-primary">Lưu địa chỉ</button>
      </div>
    </form>
  );
}
