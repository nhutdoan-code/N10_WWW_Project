import { useState } from 'react';
import { categoryLabel, flattenTree } from '../utils/tree';
import FormField from './FormField';

export default function ProductForm({ product, categories, onSubmit, onCancel }) {
  const [form, setForm] = useState({
    maLoai: categories[0]?.maLoai || '',
    tenMay: '',
    giaBan: '',
    soLuongTon: 0,
    anhChinh: '',
    moTa: '',
    thongSoKyThuat: '',
    trangThai: 'CON_HANG',
    ...product,
  });
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  const submit = (e) => {
    e.preventDefault();
    onSubmit({ ...form, giaBan: Number(form.giaBan), soLuongTon: Number(form.soLuongTon) });
  };

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
      <FormField label="Link ảnh chính" value={form.anhChinh || ''} onChange={set('anhChinh')} />
      <label className="field">
        <span>Mô tả</span>
        <textarea rows="2" value={form.moTa || ''} onChange={set('moTa')} />
      </label>
      <label className="field">
        <span>Thông số kỹ thuật (ngăn cách bằng dấu |)</span>
        <textarea rows="3" value={form.thongSoKyThuat || ''} onChange={set('thongSoKyThuat')} />
      </label>
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
        <button className="btn btn-primary">Lưu</button>
      </div>
    </form>
  );
}
