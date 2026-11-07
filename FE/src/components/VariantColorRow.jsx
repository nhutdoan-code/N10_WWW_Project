export default function VariantColorRow({ color, onChange, onRemove, canRemove }) {
  return (
    <div className="color-row">
      <label className="field">
        <span>Màu sắc</span>
        <input
          value={color.mauSac}
          onChange={(e) => onChange('mauSac', e.target.value)}
          placeholder="Ví dụ: Đen Marble"
        />
      </label>
      <label className="field">
        <span>Tồn kho</span>
        <input
          type="number"
          min="0"
          step="1"
          value={color.soLuongTon}
          onChange={(e) => onChange('soLuongTon', e.target.value)}
        />
      </label>
      <label className="field">
        <span>Trạng thái</span>
        <select value={color.trangThai || 'DANG_KINH_DOANH'} onChange={(e) => onChange('trangThai', e.target.value)}>
          <option value="DANG_KINH_DOANH">Đang kinh doanh</option>
          <option value="NGUNG_KINH_DOANH">Ngừng kinh doanh</option>
        </select>
      </label>
      <button type="button" className="btn btn-danger color-remove" onClick={onRemove} disabled={!canRemove}>
        Xóa màu
      </button>
      {color.maPhienBan && color.sku ? <small className="muted color-sku">SKU: {color.sku}</small> : null}
    </div>
  );
}
