import MoneyInput from './MoneyInput';
import VariantColorRow from './VariantColorRow';
import { RAM_OPTIONS, STORAGE_OPTIONS, configurationLabel } from '../utils/variantForm';
import { formatMoneyInput } from '../utils/money';

const withLegacyValue = (options, current) => {
  const value = Number(current);
  const list = [...options];
  if (Number.isInteger(value) && value > 0 && !list.includes(value)) list.push(value);
  return list.sort((a, b) => a - b);
};

const storageLabel = (value) => (Number(value) === 1024 ? '1024 GB (1 TB)' : `${value} GB`);

export default function VariantConfigurationCard({
  config,
  index,
  canRemove,
  onChange,
  onRemove,
  onAddColor,
  onUpdateColor,
  onRemoveColor,
}) {
  const hasSummary = config.ram !== '' && config.boNho !== '';

  return (
    <section className="variant-card">
      <div className="variant-card-head">
        <div>
          <div className="variant-card-title">
            Phiên bản {index + 1}
            {hasSummary ? ` — ${configurationLabel(config)}` : ''}
          </div>
          <small className="variant-card-sub">Giá bán và giá niêm yết áp dụng cho tất cả màu trong phiên bản này.</small>
        </div>
        <button type="button" className="btn btn-danger" onClick={onRemove} disabled={!canRemove}>
          Xóa phiên bản
        </button>
      </div>

      {config.priceConflict && config.originalPrices?.length > 1 && (
        <div className="variant-card-alert" role="status">
          Dữ liệu cũ của phiên bản này có nhiều mức giá: {config.originalPrices.map((p) => formatMoneyInput(p)).join(', ')}.
          Khi lưu, giá bên dưới sẽ được áp dụng cho mọi màu.
        </div>
      )}

      <div className="variant-card-grid">
        <label className="field">
          <span>RAM</span>
          <select value={config.ram} onChange={(e) => onChange({ ram: e.target.value })}>
            <option value="">-- Chọn RAM --</option>
            {withLegacyValue(RAM_OPTIONS, config.ram).map((value) => (
              <option key={value} value={String(value)}>{value} GB</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Bộ nhớ</span>
          <select value={config.boNho} onChange={(e) => onChange({ boNho: e.target.value })}>
            <option value="">-- Chọn bộ nhớ --</option>
            {withLegacyValue(STORAGE_OPTIONS, config.boNho).map((value) => (
              <option key={value} value={String(value)}>{storageLabel(value)}</option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Giá bán (₫)</span>
          <MoneyInput
            value={config.giaBan}
            onChange={(raw) => onChange({ giaBan: raw })}
            placeholder="9.990.000"
          />
        </label>
        <label className="field">
          <span>Giá niêm yết (₫)</span>
          <MoneyInput
            value={config.giaNiemYet}
            onChange={(raw) => onChange({ giaNiemYet: raw })}
            placeholder="10.990.000"
          />
        </label>
      </div>

      <div className="variant-colors">
        <div className="variant-colors-head">
          <b>Màu sắc</b>
          <button type="button" className="btn" onClick={onAddColor}>+ Thêm màu</button>
        </div>
        {config.colors.map((color) => (
          <VariantColorRow
            key={color.key}
            color={color}
            canRemove={config.colors.length > 1}
            onChange={(field, value) => onUpdateColor(color.key, { [field]: value })}
            onRemove={() => onRemoveColor(color.key)}
          />
        ))}
      </div>
    </section>
  );
}
