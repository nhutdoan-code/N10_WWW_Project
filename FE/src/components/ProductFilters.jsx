import { RAM_OPTIONS, RATING_OPTIONS, STORAGE_OPTIONS } from '../config/constants';

export default function ProductFilters({ brands = [], values, onChange, onReset }) {
    return (
        <div className="filters">
            <h3>Bộ lọc</h3>

            <label className="filter-group">
                <span>Hãng</span>
                <select value={values.hang} onChange={(e) => onChange('hang', e.target.value)}>
                    <option value="">Tất cả hãng</option>
                    {brands.map((b) => (
                        <option key={b.maThuongHieu} value={b.maThuongHieu}>{b.tenThuongHieu}</option>
                    ))}
                </select>
            </label>

            <label className="filter-group">
                <span>RAM</span>
                <select value={values.ram} onChange={(e) => onChange('ram', e.target.value)}>
                    <option value="">Tất cả</option>
                    {RAM_OPTIONS.map((r) => <option key={r} value={r}>{r} GB</option>)}
                </select>
            </label>

            <label className="filter-group">
                <span>Bộ nhớ trong</span>
                <select value={values.bonho} onChange={(e) => onChange('bonho', e.target.value)}>
                    <option value="">Tất cả</option>
                    {STORAGE_OPTIONS.map((s) => (
                        <option key={s} value={s}>{s >= 1024 ? `${s / 1024} TB` : `${s} GB`}</option>
                    ))}
                </select>
            </label>

            <label className="filter-group">
                <span>Đánh giá</span>
                <select value={values.sao} onChange={(e) => onChange('sao', e.target.value)}>
                    <option value="">Tất cả</option>
                    {RATING_OPTIONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                </select>
            </label>

            <label className="filter-check">
                <input
                    type="checkbox"
                    checked={values.conhang}
                    onChange={(e) => onChange('conhang', e.target.checked ? '1' : '')}
                />
                Chỉ hiện sản phẩm còn hàng
            </label>

            <button type="button" className="btn" onClick={onReset}>Xóa bộ lọc</button>
        </div>
    );
}