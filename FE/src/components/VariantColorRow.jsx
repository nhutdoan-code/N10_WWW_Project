export default function VariantColorRow({ color, onChange, onRemove, canRemove }) {
    return (
        <div className="color-row">
            <label className="field">
                <span>Màu sắc</span>
                <input
                    value={color.mauSac}
                    onChange={(e) => onChange('mauSac', e.target.value)}
                    placeholder="Ví dụ: Titan đen"
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
                <select
                    value={color.trangThai || 'CON_HANG'}
                    onChange={(e) => onChange('trangThai', e.target.value)}
                >
                    <option value="CON_HANG">Còn hàng</option>
                    <option value="HET_HANG">Hết hàng</option>
                    <option value="NGUNG_KINH_DOANH">Ngừng kinh doanh</option>
                </select>
            </label>

            <button
                type="button"
                className="btn btn-danger color-remove"
                onClick={onRemove}
                disabled={!canRemove}
            >
                Xóa màu
            </button>

            {color.maPhienBan && color.sku && (
                <small className="muted color-sku">SKU: {color.sku}</small>
            )}
        </div>
    );
}