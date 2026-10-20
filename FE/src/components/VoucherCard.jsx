import Icon from './Icon';
import { formatDay, formatPrice } from '../utils/format';

export default function VoucherCard({ v, selected, onSelect }) {
  const ship = v.loai === 'VAN_CHUYEN';
  const mien = ship && Number(v.phanTramGiam) === 100;
  const dieuKien = Number(v.donToiThieu) > 0 ? `Đơn từ ${formatPrice(v.donToiThieu)}` : 'Mọi đơn hàng';
  const cls = ['voucher', ship ? 'ship' : '', selected ? 'selected' : '', v.apDungDuoc ? '' : 'disabled'].join(' ').trim();

  return (
    <button type="button" className={cls} disabled={!v.apDungDuoc} onClick={onSelect} aria-pressed={selected}>
      <span className="voucher-stub">
        <Icon name={ship ? 'truck' : 'tag'} size={22} />
        <b>{mien ? 'Miễn phí' : `-${Number(v.phanTramGiam)}%`}</b>
        <small>{ship ? 'vận chuyển' : 'đơn hàng'}</small>
      </span>
      <span className="voucher-body">
        <b>{v.tenKm}</b>
        <span className="muted">
          {v.soTienGiamToiDa ? `Giảm tối đa ${formatPrice(v.soTienGiamToiDa)} · ` : ''}{dieuKien}
        </span>
        {v.dieuKienApDung && <span className="muted">{v.dieuKienApDung}</span>}
        <span className="muted">Mã {v.maKm} · {formatDay(v.ngayBatDau)} - {formatDay(v.ngayKetThuc)}</span>
        {v.apDungDuoc
          ? <span className="saving">Tiết kiệm {formatPrice(v.soTienGiam)}</span>
          : <span className="reason">{v.lyDo}</span>}
      </span>
      <span className="voucher-check" aria-hidden="true">{selected ? '✓' : ''}</span>
    </button>
  );
}
