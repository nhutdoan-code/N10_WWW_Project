import { useState } from 'react';
import useToast from '../hooks/useToast';
import { adminService } from '../service/adminService';
import { errorMessage } from '../utils/format';
import { categoryLabel, flattenTree } from '../utils/tree';
import {
  flattenConfigurations,
  groupVariantsForForm,
  newColorRow,
  newConfiguration,
  validateConfigurations,
} from '../utils/variantForm';
import FormField from './FormField';
import ProductImage from './ProductImage';
import VariantConfigurationCard from './VariantConfigurationCard';

const NUM = ['kichThuocManHinh', 'tanSoQuet', 'ram', 'boNho', 'cameraTruoc', 'pin', 'congSuatSac', 'trongLuong'];
const EMPTY_SPEC = { loaiManHinh: '', chip: '', cameraSau: '', heDieuHanh: '', nfc: false, ho5G: false };

export default function ProductForm({ product, categories, brands = [], onSubmit, onCancel }) {
  const toast = useToast();
  const [uploading, setUploading] = useState(false);
  const [groupedInitial] = useState(() => groupVariantsForForm(product?.phienBans));
  const [configurations, setConfigurations] = useState(() =>
    groupedInitial.configurations.length > 0 ? groupedInitial.configurations : [newConfiguration()],
  );
  const [variantErrors, setVariantErrors] = useState([]);
  const [priceWarnings] = useState(groupedInitial.warnings);
  const [form, setForm] = useState({
    maLoai: categories[0]?.maLoai || '',
    maThuongHieu: '',
    tenMay: '',
    giaBan: '',
    soLuongTon: 0,
    moTa: '',
    trangThai: 'DANG_KINH_DOANH',
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
      setForm((f) => ({
        ...f,
        hinhAnhs: [...f.hinhAnhs, ...urls.map((url) => ({ duongDan: url }))],
        anhChinh: f.anhChinh || urls[0],
      }));
    } catch (err) {
      toast(errorMessage(err), 'error');
    }
    setUploading(false);
  };

  const removeImage = (anh) => {
    const rest = form.hinhAnhs.filter((a) => a !== anh);
    setForm({
      ...form,
      hinhAnhs: rest,
      anhChinh: form.anhChinh === anh.duongDan ? rest[0]?.duongDan || null : form.anhChinh,
    });
  };

  const updateConfig = (configKey, patch) => {
    setVariantErrors([]);
    setConfigurations((list) => list.map((c) => (c.key === configKey ? { ...c, ...patch } : c)));
  };

  const addConfiguration = () => {
    setVariantErrors([]);
    setConfigurations((list) => [...list, newConfiguration()]);
  };

  const removeConfiguration = (configKey) => {
    setVariantErrors([]);
    setConfigurations((list) => (list.length <= 1 ? list : list.filter((c) => c.key !== configKey)));
  };

  const addColor = (configKey) => {
    setVariantErrors([]);
    setConfigurations((list) =>
      list.map((c) => (c.key === configKey ? { ...c, colors: [...c.colors, newColorRow()] } : c)),
    );
  };

  const updateColor = (configKey, colorKey, patch) => {
    setVariantErrors([]);
    setConfigurations((list) =>
      list.map((c) =>
        c.key === configKey
          ? { ...c, colors: c.colors.map((color) => (color.key === colorKey ? { ...color, ...patch } : color)) }
          : c,
      ),
    );
  };

  const removeColor = (configKey, colorKey) => {
    setVariantErrors([]);
    setConfigurations((list) =>
      list.map((c) =>
        c.key === configKey && c.colors.length > 1
          ? { ...c, colors: c.colors.filter((color) => color.key !== colorKey) }
          : c,
      ),
    );
  };
  // Màu hiện có trong form
  const mauOptions = [...new Set(
      configurations.flatMap((c) => c.colors.map((x) => (x.mauSac || '').trim())).filter(Boolean),
  )];

  // Phiên bản đã lưu (có mã) để gán ảnh riêng
  const pbOptions = configurations.flatMap((c) =>
      c.colors
          .filter((x) => x.maPhienBan && (x.mauSac || '').trim())
          .map((x) => ({ ma: x.maPhienBan, label: `${c.ram}GB / ${c.boNho}GB - ${x.mauSac.trim()}` })),
  );

  const phamViAnh = (anh) => {
    if (anh.maPhienBan) return `pb:${anh.maPhienBan}`;
    if (anh.mauSac) return `mau:${anh.mauSac}`;
    return '';
  };

  const setPhamViAnh = (anh, value) => setForm((f) => ({
    ...f,
    hinhAnhs: f.hinhAnhs.map((a) => {
      if (a !== anh) return a;
      if (value.startsWith('pb:')) return { ...a, maPhienBan: value.slice(3), mauSac: null };
      if (value.startsWith('mau:')) return { ...a, maPhienBan: null, mauSac: value.slice(4) };
      return { ...a, maPhienBan: null, mauSac: null };
    }),
  }));

  const submit = (e) => {
    e.preventDefault();
    if (!form.maThuongHieu) return toast('Vui lòng chọn thương hiệu', 'error');
    if ((form.hinhAnhs || []).length === 0) return toast('Vui lòng tải lên ít nhất 1 ảnh sản phẩm', 'error');

    const errors = validateConfigurations(configurations);
    setVariantErrors(errors);
    if (errors.length > 0) {
      toast(errors[0], 'error');
      requestAnimationFrame(() => {
        document.getElementById('variant-errors')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      });
      return;
    }

    if (configurations.some((c) => c.priceConflict)) {
      const message = `${priceWarnings.join('\n')}\n\nTiếp tục lưu và áp dụng giá của phiên bản cho tất cả màu?`;
      if (!window.confirm(message)) return;
    }

    const phienBans = flattenConfigurations(configurations);

    if ((form.trangThai || 'DANG_KINH_DOANH') === 'DANG_KINH_DOANH'
      && !phienBans.some((p) => p.trangThai === 'DANG_KINH_DOANH')) {
      return toast('Sản phẩm đang kinh doanh phải có ít nhất một phiên bản đang kinh doanh', 'error');
    }

    const thongSo = { ...form.thongSo };
    Object.keys(thongSo).forEach((k) => {
      if (thongSo[k] === '' || thongSo[k] == null) thongSo[k] = null;
      else if (NUM.includes(k)) thongSo[k] = Number(thongSo[k]);
    });

    // Trường giá/tồn cấp sản phẩm chỉ để thoả DTO; backend luôn tính lại từ phiên bản.
    // Ưu tiên giá thấp nhất của phiên bản đang kinh doanh, không lấy variant ngừng bán.
    const activePrices = phienBans
      .filter((p) => p.trangThai === 'DANG_KINH_DOANH' && Number.isFinite(Number(p.giaBan)))
      .map((p) => Number(p.giaBan));
    const giaBanMin = activePrices.length ? Math.min(...activePrices) : 0;
    const tonActive = phienBans
      .filter((p) => p.trangThai === 'DANG_KINH_DOANH')
      .reduce((s, v) => s + (Number(v.soLuongTon) || 0), 0);

    // Chỉ gửi identity + metadata ảnh; backend reconcile theo maHinhAnh.
    const hinhAnhs = (form.hinhAnhs || []).map((anh) => ({
      maHinhAnh: anh.maHinhAnh || null,
      duongDan: anh.duongDan,
      gocChup: anh.gocChup ?? null,
      mauSac: anh.mauSac || null,
      maPhienBan: anh.maPhienBan || null,
    }));

    onSubmit({
      ...form,
      giaBan: giaBanMin,
      soLuongTon: tonActive,
      thongSo,
      phienBans,
      hinhAnhs,
      anhChinh: form.anhChinh || hinhAnhs[0]?.duongDan || null,
    });
  };

  const spec = (label, key, props = {}) => (
    <FormField label={label} value={form.thongSo[key] ?? ''} onChange={setSpec(key)} {...props} />
  );

  return (
    <form className="form" onSubmit={submit}>
      <label className="field">
        <span>Thương hiệu</span>
        <select value={form.maThuongHieu || ''} onChange={set('maThuongHieu')}>
          <option value="">-- Chọn thương hiệu --</option>
          {brands.map((b) => (
            <option key={b.maThuongHieu} value={b.maThuongHieu}>{b.tenThuongHieu}</option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Hãng / Loại sản phẩm</span>
        <select value={form.maLoai} onChange={set('maLoai')}>
          {flattenTree(categories).map((c) => (
            <option key={c.maLoai} value={c.maLoai}>{categoryLabel(c)}</option>
          ))}
        </select>
      </label>
      <FormField label="Tên máy" value={form.tenMay} onChange={set('tenMay')} required />
      <label className="field">
        <span>Mô tả</span>
        <textarea rows="8" value={form.moTa || ''} onChange={set('moTa')} />
      </label>

      <div className="variant-section-head">
        <h4 className="form-title">Phiên bản sản phẩm</h4>
        <small className="muted">Mỗi phiên bản là một cấu hình RAM / bộ nhớ; thêm nhiều màu trong cùng phiên bản.</small>
      </div>

      {variantErrors.length > 0 && (
        <div className="variant-errors" id="variant-errors" role="alert">
          <b>Vui lòng kiểm tra lại:</b>
          <ul>
            {variantErrors.map((message, index) => (
              <li key={`${index}-${message}`}>{message}</li>
            ))}
          </ul>
        </div>
      )}

      {priceWarnings.length > 0 && (
        <div className="variant-warning" role="status">
          <b>Cảnh báo dữ liệu giá cũ:</b>
          <ul>
            {priceWarnings.map((message) => <li key={message}>{message}</li>)}
          </ul>
        </div>
      )}

      <div className="variant-list">
        {configurations.map((config, index) => (
          <VariantConfigurationCard
            key={config.key}
            config={config}
            index={index}
            canRemove={configurations.length > 1}
            onChange={(patch) => updateConfig(config.key, patch)}
            onRemove={() => removeConfiguration(config.key)}
            onAddColor={() => addColor(config.key)}
            onUpdateColor={(colorKey, patch) => updateColor(config.key, colorKey, patch)}
            onRemoveColor={(colorKey) => removeColor(config.key, colorKey)}
          />
        ))}
      </div>
      <button type="button" className="btn" onClick={addConfiguration} style={{ alignSelf: 'flex-start' }}>
        + Thêm phiên bản
      </button>

      <h4 className="form-title">Thông số kỹ thuật</h4>
      <div className="grid-3">
        {spec('Màn hình (inch)', 'kichThuocManHinh', { type: 'number', step: '0.01', min: '1', max: '9.99' })}
        {spec('Loại màn hình', 'loaiManHinh', { placeholder: 'OLED, AMOLED, IPS...' })}
        {spec('Tần số quét (Hz)', 'tanSoQuet', { type: 'number', min: '1' })}
      </div>
      <div className="grid-3">
        {spec('Chip xử lý', 'chip')}
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
        {(form.hinhAnhs || []).map((anh) => {
          const url = anh.duongDan;
          return (
              <div key={anh.maHinhAnh || url} className={`img-item ${form.anhChinh === url ? 'chinh' : ''}`}>
                <ProductImage src={url} alt=""/>
                <button type="button" className="btn btn-ghost" onClick={() => setForm({...form, anhChinh: url})}
                        disabled={form.anhChinh === url}>
                  {form.anhChinh === url ? 'Ảnh chính' : 'Đặt làm ảnh chính'}
                </button>
                <select value={phamViAnh(anh)} onChange={(e) => setPhamViAnh(anh, e.target.value)}
                        aria-label="Phạm vi áp dụng của ảnh">
                  <option value="">Ảnh chung (toàn sản phẩm)</option>
                  {mauOptions.length > 0 && (
                      <optgroup label="Theo màu (mọi dung lượng)">
                        {mauOptions.map((m) => <option key={m} value={`mau:${m}`}>{m}</option>)}
                      </optgroup>
                  )}
                  {pbOptions.length > 0 && (
                      <optgroup label="Chỉ riêng một phiên bản">
                        {pbOptions.map((p) => <option key={p.ma} value={`pb:${p.ma}`}>{p.label}</option>)}
                      </optgroup>
                  )}
                </select>
                <button type="button" className="img-remove" aria-label="Xóa ảnh" onClick={() => removeImage(anh)}>×
                </button>
              </div>
          );
        })}
        <label className="img-add">
          <input type="file" accept="image/png,image/jpeg,image/webp" multiple hidden onChange={upload}
                 disabled={uploading}/>
          {uploading ? 'Đang tải...' : '+ Tải ảnh lên'}
        </label>
      </div>
      <small className="muted">Chọn ảnh JPG, PNG hoặc WEBP từ máy tính (tối đa 5MB mỗi ảnh).</small>

      <label className="check">
        <input
            type="checkbox"
            checked={form.trangThai === 'NGUNG_KINH_DOANH'}
            onChange={(e) => setForm({...form, trangThai: e.target.checked ? 'NGUNG_KINH_DOANH' : 'DANG_KINH_DOANH'})}
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
