import { ORDER_STATUS, ORDER_STEPS } from '../config/constants';

export default function OrderSteps({ trangThai }) {
  if (trangThai === 'DA_HUY') return <p className="error">Đơn hàng đã được hủy</p>;
  const buoc = ORDER_STEPS.indexOf(trangThai);
  return (
    <ol className="steps">
      {ORDER_STEPS.map((s, i) => (
        <li key={s} className={i < buoc ? 'done' : i === buoc ? 'done current' : ''}>{ORDER_STATUS[s]}</li>
      ))}
    </ol>
  );
}
