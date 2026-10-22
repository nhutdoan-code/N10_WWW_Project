import { useState } from 'react';
import useToast from '../hooks/useToast';
import { adminService } from '../service/adminService';
import { errorMessage } from '../utils/format';
import { categoryLabel, flattenTree } from '../utils/tree';
import FormField from './FormField';

const NUM = ['kichThuocManHinh', 'tanSoQuet', 'ram', 'boNho', 'cameraTruoc', 'pin', 'congSuatSac', 'trongLuong'];
const EMPTY_SPEC = { loaiManHinh: '', chip: '', cameraSau: '', heDieuHanh: '', nfc: false, ho5G: false };

export default function ProductForm({ product, categories, onSubmit, onCancel }) {
  const toast = useToast();
  const [uploading, setUploading] = useState(false);
  const [form, setForm] = useState({
    maLoai: categories[0]?.maLoai || '',
    tenMay: '',
    giaBan: '',
    soLuongTon: 0,
    moTa: '',
    trangThai: 'CON_HANG',
    hinhAnhs: [],
    anhChinh: null,
    ...product,
    thongSo: { ...EMPTY_SPEC, ...product?.thongSo },
  });
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const setSpec = (key) => (e) => setForm({ ...form, thongSo: { ...form.thongSo, [key]: e.target.type === 'checkbox' ? e.target.checked : e.target.value } });

  const upload = async (e) => {
    const files = [...e.target.files];
    e.target.value = '';
    setUploading(true);
    try {
      const urls = [];
      for (const f of files) urls.push((await adminService.uploadImage(f)).url);
      setForm((f) => ({ ...f, hinhAnhs: [...f.hinhAnhs, ...urls], anhChinh: f.anhChinh || urls[0] }));
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
    setUploading(false);
  };

  const removeImage = (url) => {
    const rest = form.hinhAnhs.filter((u) => u !== url);
    setForm({ ...form, hinhAnhs: rest, anhChinh: form.anhChinh === url ? rest[0] || null : form.anhChinh });
  };

  const submit = (e) => {
    e.preventDefault();
    if (form.hinhAnhs.length === 0) return toast('Vui lòng tải lên ít nhất 1 ảnh sản phẩm', 'error');
    const thongSo = { ...form.thongSo };
    Object.keys(thongSo).forEach((k) => {
      if (thongSo[k] === '' || thongSo[k] == null) thongSo[k] = null;
      else if (NUM.includes(k)) thongSo[k] = Number(thongSo[k]);
    });
    onSubmit({ ...form, giaBan: Number(form.giaBan), soLuongTon: Number(form.soLuongTon), thongSo });
  };

  const spec = (label, key, props = {}) => (
    <FormField label={label} value={form.thongSo[key] ?? ''} onChange={setSpec(key)} {...props} />
  );

  return (
    <form className="form" onSubmit={submit}>
      <label className="field">
        <span>Hãng / Loại sản phẩm</span>
        <select value={form.maLoai} onChange={set('maLoai')}>
          {flattenTree(categories).map((c) => (
            <option key={c.maLoai} value={c.maLoai}>{categoryLabel(c)}</option>
          ))}
        </select>
      </label>
      <FormField label="Tên máy" value={form.tenMay} onChange={set('tenMay')} required />
      <div className="grid-2">
        <FormField label="Giá bán (₫)" type="number" min="1" value={form.giaBan} onChange={set('giaBan')} required />
        <FormField label="Số lượng tồn" type="number" min="0" value={form.soLuongTon} onChange={set('soLuongTon')} required />
      </div>
      <label className="field">
        <span>Mô tả</span>
        <textarea rows="2" value={form.moTa || ''} onChange={set('moTa')} />
      </label>

      <h4 className="form-title">Thông số kỹ thuật</h4>
      <div className="grid-3">
        {spec('Màn hình (inch)', 'kichThuocManHinh', { type: 'number', step: '0.01', min: '1', max: '9.99' })}
        {spec('Loại màn hình', 'loaiManHinh', { placeholder: 'OLED, AMOLED, IPS...' })}
        {spec('Tần số quét (Hz)', 'tanSoQuet', { type: 'number', min: '1' })}
      </div>
      <div className="grid-3">
        {spec('Chip xử lý', 'chip')}
        {spec('RAM (GB)', 'ram', { type: 'number', min: '1' })}
        {spec('Bộ nhớ trong (GB)', 'boNho', { type: 'number', min: '1' })}
      </div>
      <div className="grid-3">
        {spec('Camera sau', 'cameraSau', { placeholder: '48MP + 12MP + 12MP' })}
        {spec('Camera trước (MP)', 'cameraTruoc', { type: 'number', min: '1' })}
        {spec('Hệ điều hành', 'heDieuHanh', { placeholder: 'Android 14, iOS 17...' })}
      </div>
      <div className="grid-3">
        {spec('Pin (mAh)', 'pin', { type: 'number', min: '1' })}
        {spec('Công suất sạc (W)', 'congSuatSac', { type: 'number', min: '1' })}
        {spec('Trọng lượng (g)', 'trongLuong', { type: 'number', min: '1' })}
      </div>
      <div className="row">
        <label className="check"><input type="checkbox" checked={form.thongSo.nfc} onChange={setSpec('nfc')} />Hỗ trợ NFC</label>
        <label className="check"><input type="checkbox" checked={form.thongSo.ho5G} onChange={setSpec('ho5G')} />Hỗ trợ 5G</label>
      </div>

      <h4 className="form-title">Hình ảnh sản phẩm</h4>
      <div className="img-list">
        {form.hinhAnhs.map((url) => (
          <div key={url} className={`img-item ${form.anhChinh === url ? 'chinh' : ''}`}>
            <img src={url} alt="" />
            <button type="button" className="btn btn-ghost" onClick={() => setForm({ ...form, anhChinh: url })} disabled={form.anhChinh === url}>
              {form.anhChinh === url ? 'Ảnh chính' : 'Đặt làm ảnh chính'}
            </button>
            <button type="button" className="img-remove" aria-label="Xóa ảnh" onClick={() => removeImage(url)}>×</button>
          </div>
        ))}
        <label className="img-add">
          <input type="file" accept="image/png,image/jpeg,image/webp" multiple hidden onChange={upload} disabled={uploading} />
          {uploading ? 'Đang tải...' : '+ Tải ảnh lên'}
        </label>
      </div>
      <small className="muted">Chọn ảnh JPG, PNG hoặc WEBP từ máy tính (tối đa 5MB mỗi ảnh).</small>

      <label className="check">
        <input
          type="checkbox"
          checked={form.trangThai === 'NGUNG_KINH_DOANH'}
          onChange={(e) => setForm({ ...form, trangThai: e.target.checked ? 'NGUNG_KINH_DOANH' : 'CON_HANG' })}
        />
        Ngừng kinh doanh
      </label>
      <div className="row end">
        <button type="button" className="btn" onClick={onCancel}>Hủy</button>
        <button className="btn btn-primary" disabled={uploading}>Lưu</button>
      </div>
    </form>
  );
}
